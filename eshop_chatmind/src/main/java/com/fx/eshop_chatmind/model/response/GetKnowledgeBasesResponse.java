package com.fx.eshop_chatmind.model.response;

import com.fx.eshop_chatmind.model.vo.KnowledgeBaseVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetKnowledgeBasesResponse {
    private KnowledgeBaseVO[] knowledgeBases;
}

