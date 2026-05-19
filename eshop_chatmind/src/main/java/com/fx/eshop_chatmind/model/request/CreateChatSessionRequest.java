package com.fx.eshop_chatmind.model.request;

import lombok.Data;

@Data
public class CreateChatSessionRequest {
    private String agentId;
    private String title;
}
