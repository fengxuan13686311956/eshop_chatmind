package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.entity.ChunkBgeM3;

import java.util.List;
import java.util.Map;

public interface KeywordSearchService {

    /**
     * 基于 BM25 + jieba 分词的关键词检索
     *
     * @param kbId  知识库 ID
     * @param query 查询文本
     * @param topK  返回结果数量
     * @return 按 BM25 得分降序排列的 chunks 和对应得分
     */
    List<Map.Entry<ChunkBgeM3, Double>> keywordSearch(String kbId, String query, int topK);
}
