package com.fx.eshop_chatmind.service;

import com.fx.eshop_chatmind.agent.tools.Tool;

import java.util.List;

public interface ToolFacadeService {
    List<Tool> getAllTools();

    List<Tool> getOptionalTools();

    List<Tool> getFixedTools();
}
