package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.request.CreateChatSessionRequest;
import com.fx.eshop_chatmind.model.request.UpdateChatSessionRequest;
import com.fx.eshop_chatmind.model.response.CreateChatSessionResponse;
import com.fx.eshop_chatmind.model.response.GetChatSessionResponse;
import com.fx.eshop_chatmind.model.response.GetChatSessionsResponse;

public interface ChatSessionFacadeService {
    GetChatSessionsResponse getChatSessions();

    GetChatSessionResponse getChatSession(String chatSessionId);

    GetChatSessionsResponse getChatSessionsByAgentId(String agentId);

    CreateChatSessionResponse createChatSession(CreateChatSessionRequest request);

    void deleteChatSession(String chatSessionId);

    void updateChatSession(String chatSessionId, UpdateChatSessionRequest request);
}
