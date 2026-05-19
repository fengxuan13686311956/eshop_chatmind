package com.fx.eshop_chatmind.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateAgentResponse {
    private String agentId;
}
