package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.request.CreateAgentRequest;
import com.fx.eshop_chatmind.model.request.UpdateAgentRequest;
import com.fx.eshop_chatmind.model.response.CreateAgentResponse;
import com.fx.eshop_chatmind.model.response.GetAgentsResponse;

public interface AgentFacadeService {
    GetAgentsResponse getAgents();

    CreateAgentResponse createAgent(CreateAgentRequest request);

    void deleteAgent(String agentId);

    void updateAgent(String agentId, UpdateAgentRequest request);
}
