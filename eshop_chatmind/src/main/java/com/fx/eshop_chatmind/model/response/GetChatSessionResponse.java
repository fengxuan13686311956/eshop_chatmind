package com.fx.eshop_chatmind.model.response;

import com.fx.eshop_chatmind.model.vo.ChatSessionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class GetChatSessionResponse {
    private ChatSessionVO chatSession;
}
