package com.fx.eshop_chatmind.controller;

import com.fx.eshop_chatmind.model.common.ApiResponse;
import com.fx.eshop_chatmind.model.request.CreateAgentRequest;
import com.fx.eshop_chatmind.model.request.UpdateAgentRequest;
import com.fx.eshop_chatmind.model.response.CreateAgentResponse;
import com.fx.eshop_chatmind.model.response.GetAgentsResponse;
import com.fx.eshop_chatmind.service.AgentFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class AgentController {

    private final AgentFacadeService agentFacadeService;

    // 查询 agents
    @GetMapping("/agents")
    public ApiResponse<GetAgentsResponse> getAgents() {
        return ApiResponse.success(agentFacadeService.getAgents());
    }

    // 创建 agent
    @PostMapping("/agents")
    public ApiResponse<CreateAgentResponse> createAgent(@RequestBody CreateAgentRequest request) {
        return ApiResponse.success(agentFacadeService.createAgent(request));
    }

    // 删除 agent
    @DeleteMapping("/agents/{agentId}")
    public ApiResponse<Void> deleteAgent(@PathVariable String agentId) {
        agentFacadeService.deleteAgent(agentId);
        return ApiResponse.success();
    }

    // 更新 agent
    @PatchMapping("/agents/{agentId}")
    public ApiResponse<Void> updateAgent(@PathVariable String agentId, @RequestBody UpdateAgentRequest request) {
        agentFacadeService.updateAgent(agentId, request);
        return ApiResponse.success();
    }
}
