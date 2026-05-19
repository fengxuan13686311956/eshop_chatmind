package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.model.dto.ChatMessageDTO;
import com.fx.eshop_chatmind.model.request.CreateChatMessageRequest;
import com.fx.eshop_chatmind.model.request.UpdateChatMessageRequest;
import com.fx.eshop_chatmind.model.response.CreateChatMessageResponse;
import com.fx.eshop_chatmind.model.response.GetChatMessagesResponse;

import java.util.List;

public interface ChatMessageFacadeService {
    GetChatMessagesResponse getChatMessagesBySessionId(String sessionId);

    List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit);

    CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request);

    CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO);

    CreateChatMessageResponse agentCreateChatMessage(CreateChatMessageRequest request);

    CreateChatMessageResponse appendChatMessage(String chatMessageId, String appendContent);

    void deleteChatMessage(String chatMessageId);

    void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request);
}
