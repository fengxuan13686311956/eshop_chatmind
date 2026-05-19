package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.request.CreateKnowledgeBaseRequest;
import com.fx.eshop_chatmind.model.request.UpdateKnowledgeBaseRequest;
import com.fx.eshop_chatmind.model.response.CreateKnowledgeBaseResponse;
import com.fx.eshop_chatmind.model.response.GetKnowledgeBasesResponse;

public interface KnowledgeBaseFacadeService {
    GetKnowledgeBasesResponse getKnowledgeBases();

    CreateKnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request);

    void deleteKnowledgeBase(String knowledgeBaseId);

    void updateKnowledgeBase(String knowledgeBaseId, UpdateKnowledgeBaseRequest request);
}

