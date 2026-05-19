package com.fx.eshop_chatmind.service.impl;

import com.fx.eshop_chatmind.mapper.ChunkBgeM3Mapper;
import com.fx.eshop_chatmind.model.entity.ChunkBgeM3;
import com.fx.eshop_chatmind.service.KeywordSearchService;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class KeywordSearchServiceImpl implements KeywordSearchService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final JiebaSegmenter segmenter;

    public KeywordSearchServiceImpl(ChunkBgeM3Mapper chunkBgeM3Mapper) {
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
        this.segmenter = new JiebaSegmenter();
    }

    @Override
    public List<Map.Entry<ChunkBgeM3, Double>> keywordSearch(String kbId, String query, int topK) {
        List<ChunkBgeM3> allChunks = chunkBgeM3Mapper.selectByKbId(kbId);
        if (allChunks.isEmpty()) {
            return List.of();
        }

        // tokenize query
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        // tokenize all documents
        List<List<String>> docTokens = new ArrayList<>();
        int totalLen = 0;
        for (ChunkBgeM3 chunk : allChunks) {
            List<String> tokens = tokenize(chunk.getContent());
            docTokens.add(tokens);
            totalLen += tokens.size();
        }
        double avgdl = (double) totalLen / allChunks.size();

        // compute IDF for query terms
        int N = allChunks.size();
        Map<String, Double> idf = new HashMap<>();
        for (String term : queryTokens) {
            int n = 0;
            for (List<String> tokens : docTokens) {
                if (tokens.contains(term)) {
                    n++;
                }
            }
            idf.put(term, Math.log((N - n + 0.5) / (n + 0.5) + 1.0));
        }

        // compute BM25 scores
        List<Map.Entry<ChunkBgeM3, Double>> results = new ArrayList<>();
        for (int i = 0; i < allChunks.size(); i++) {
            ChunkBgeM3 chunk = allChunks.get(i);
            List<String> tokens = docTokens.get(i);
            int dl = tokens.size();
            if (dl == 0) continue;

            // term frequency map
            Map<String, Integer> tfMap = new HashMap<>();
            for (String t : tokens) {
                tfMap.merge(t, 1, Integer::sum);
            }

            double score = 0.0;
            for (String term : queryTokens) {
                int f = tfMap.getOrDefault(term, 0);
                if (f == 0) continue;
                double idfVal = idf.get(term);
                score += idfVal * (f * (K1 + 1)) / (f + K1 * (1 - B + B * dl / avgdl));
            }

            if (score > 0) {
                results.add(new AbstractMap.SimpleEntry<>(chunk, score));
            }
        }

        // sort by score descending, take topK
        results.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            List<SegToken> tokens = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH);
            return tokens.stream()
                    .map(t -> t.word.trim().toLowerCase())
                    .filter(w -> w.length() > 0)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("jieba tokenize failed: {}", e.getMessage());
            return List.of();
        }
    }
}
