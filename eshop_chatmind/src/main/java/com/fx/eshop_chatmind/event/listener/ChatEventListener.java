package com.fx.eshop_chatmind.event.listener;

import com.fx.eshop_chatmind.agent.eshop_chatmind;
import com.fx.eshop_chatmind.agent.eshop_chatmindFactory;
import com.fx.eshop_chatmind.event.ChatEvent;
import lombok.AllArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ChatEventListener {

    private final eshop_chatmindFactory eshop_chatmindFactory;

    @Async
    @EventListener
    public void handle(ChatEvent event) {
        // 创建一个 Agent 实例处理聊天事件
        eshop_chatmind eshop_chatmind = eshop_chatmindFactory.create(event.getAgentId(), event.getSessionId());
        eshop_chatmind.run();
    }
}
