package com.fx.eshop_chatmind.service.impl;

import com.fx.eshop_chatmind.mapper.ChunkBgeM3Mapper;
import com.fx.eshop_chatmind.model.entity.ChunkBgeM3;
import com.fx.eshop_chatmind.service.KeywordSearchService;
import com.fx.eshop_chatmind.service.RagService;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagServiceImpl implements RagService {

    // 封装本地的模型调用
    private final WebClient webClient;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final KeywordSearchService keywordSearchService;

    public RagServiceImpl(WebClient.Builder builder,
                          ChunkBgeM3Mapper chunkBgeM3Mapper,
                          KeywordSearchService keywordSearchService) {
        this.webClient = builder.baseUrl("http://localhost:11434").build();
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
        this.keywordSearchService = keywordSearchService;
    }

    @Data
    private static class EmbeddingResponse {
        private float[] embedding;
    }

    private float[] doEmbed(String text) {
        EmbeddingResponse resp = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(Map.of(
                        "model", "bge-m3",
                        "prompt", text
                ))
                .retrieve()
                .bodyToMono(EmbeddingResponse.class)
                .block();
        Assert.notNull(resp, "Embedding response cannot be null");
        return resp.getEmbedding();
    }

    @Override
    public float[] embed(String text) {
        return doEmbed(text);
    }

    @Override
    public List<String> similaritySearch(String kbId, String title) {
        String queryEmbedding = toPgVector(doEmbed(title));
        List<ChunkBgeM3> chunks = chunkBgeM3Mapper.similaritySearch(kbId, queryEmbedding, 3);
        return chunks.stream().map(ChunkBgeM3::getContent).toList();
    }

    @Override
    public List<String> hybridSearch(String kbId, String query) {
        // 1. 向量语义检索 (top 5)
        String queryEmbedding = toPgVector(doEmbed(query));
        List<ChunkBgeM3> vectorChunks = chunkBgeM3Mapper.similaritySearch(kbId, queryEmbedding, 5);

        // 2. BM25 关键词检索 (top 5)
        List<Map.Entry<ChunkBgeM3, Double>> bm25Results = keywordSearchService.keywordSearch(kbId, query, 5);

        // 3. Reciprocal Rank Fusion (RRF) 融合排序
        final int K = 60;
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, String> chunkContentMap = new LinkedHashMap<>();

        // 向量结果贡献 RRF 分
        for (int i = 0; i < vectorChunks.size(); i++) {
            ChunkBgeM3 chunk = vectorChunks.get(i);
            double rrf = 1.0 / (K + i + 1);
            rrfScores.merge(chunk.getId(), rrf, Double::sum);
            chunkContentMap.put(chunk.getId(), chunk.getContent());
        }

        // BM25 结果贡献 RRF 分
        for (int i = 0; i < bm25Results.size(); i++) {
            ChunkBgeM3 chunk = bm25Results.get(i).getKey();
            double rrf = 1.0 / (K + i + 1);
            rrfScores.merge(chunk.getId(), rrf, Double::sum);
            chunkContentMap.putIfAbsent(chunk.getId(), chunk.getContent());
        }

        // 按 RRF 得分降序排列，取 top 5
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .map(e -> chunkContentMap.get(e.getKey()))
                .toList();
    }

    private String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(v[i]);
            if (i < v.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
