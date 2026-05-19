package com.fx.eshop_chatmind.agent.tools;

import com.fx.eshop_chatmind.service.RagService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeTools implements Tool {

    private final RagService ragService;

    public KnowledgeTools(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String getName() {
        return "KnowledgeTool";
    }

    @Override
    public String getDescription() {
        return "用于从知识库执行混合检索（RAG）。结合向量语义检索和 BM25 关键词检索，融合排序后返回最相关的内容片段。";
    }

    @Override
    public ToolType getType() {
        return ToolType.FIXED;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "KnowledgeTool",
            description = "从指定知识库中执行混合检索（向量语义+BM25关键词）。参数为知识库 ID（kbsId）和查询文本（query），返回与查询最相关的知识片段。当需要查找事实性信息或最新数据时，应在回答前优先调用此工具。"
    )
    public String knowledgeQuery(String kbsId, String query) {
        List<String> strings = ragService.hybridSearch(kbsId, query);
        return String.join("\n", strings);
    }
}
