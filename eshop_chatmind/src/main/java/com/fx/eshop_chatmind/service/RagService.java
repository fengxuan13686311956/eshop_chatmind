package com.fx.eshop_chatmind.service;

import java.util.List;

public interface RagService {
    float[] embed(String text);

    /**
     * 纯语义检索（向量相似度）
     */
    List<String> similaritySearch(String kbId, String title);

    /**
     * 混合检索：向量语义检索 + BM25 关键词检索，融合排序后返回。
     * 向量检索保证语义相关性，BM25 保证关键词命中率。
     */
    List<String> hybridSearch(String kbId, String query);
}
