package com.fx.eshop_chatmind.controller;

import com.fx.eshop_chatmind.model.common.ApiResponse;
import com.fx.eshop_chatmind.model.request.CreateKnowledgeBaseRequest;
import com.fx.eshop_chatmind.model.request.UpdateKnowledgeBaseRequest;
import com.fx.eshop_chatmind.model.response.CreateKnowledgeBaseResponse;
import com.fx.eshop_chatmind.model.response.GetKnowledgeBasesResponse;
import com.fx.eshop_chatmind.service.KnowledgeBaseFacadeService;
import com.fx.eshop_chatmind.service.KnowledgeBaseSyncService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseFacadeService knowledgeBaseFacadeService;
    private final KnowledgeBaseSyncService knowledgeBaseSyncService;

    // 查询所有知识库
    @GetMapping("/knowledge-bases")
    public ApiResponse<GetKnowledgeBasesResponse> getKnowledgeBases() {
        return ApiResponse.success(knowledgeBaseFacadeService.getKnowledgeBases());
    }

    // 创建知识库
    @PostMapping("/knowledge-bases")
    public ApiResponse<CreateKnowledgeBaseResponse> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        return ApiResponse.success(knowledgeBaseFacadeService.createKnowledgeBase(request));
    }

    // 删除知识库
    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}")
    public ApiResponse<Void> deleteKnowledgeBase(@PathVariable String knowledgeBaseId) {
        knowledgeBaseFacadeService.deleteKnowledgeBase(knowledgeBaseId);
        return ApiResponse.success();
    }

    // 更新知识库
    @PatchMapping("/knowledge-bases/{knowledgeBaseId}")
    public ApiResponse<Void> updateKnowledgeBase(@PathVariable String knowledgeBaseId, @RequestBody UpdateKnowledgeBaseRequest request) {
        knowledgeBaseFacadeService.updateKnowledgeBase(knowledgeBaseId, request);
        return ApiResponse.success();
    }

    // 同步知识库（基于内容Hash检测变更，自动更新向量库）
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/sync")
    public ApiResponse<KnowledgeBaseSyncService.SyncResult> syncKnowledgeBase(@PathVariable String knowledgeBaseId) {
        KnowledgeBaseSyncService.SyncResult result = knowledgeBaseSyncService.syncKnowledgeBase(knowledgeBaseId);
        return ApiResponse.success(result);
    }
}
