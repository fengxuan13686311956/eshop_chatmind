package com.fx.eshop_chatmind.model.response;

import com.fx.eshop_chatmind.model.vo.AgentVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetAgentsResponse {
    private AgentVO[] agents;
}
