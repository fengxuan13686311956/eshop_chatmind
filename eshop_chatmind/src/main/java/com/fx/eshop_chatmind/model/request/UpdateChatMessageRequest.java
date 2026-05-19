package com.fx.eshop_chatmind.model.request;

import com.fx.eshop_chatmind.model.dto.ChatMessageDTO;
import lombok.Data;

@Data
public class UpdateChatMessageRequest {
    private String content;
    private ChatMessageDTO.MetaData metadata;
}

