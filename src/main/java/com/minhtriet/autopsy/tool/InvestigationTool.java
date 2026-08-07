package com.minhtriet.autopsy.tool;

import com.anthropic.models.messages.Tool;

import java.util.Map;

public interface InvestigationTool {
    String name();
    Tool definition();
    String execute(Map<String, Object> args);
}
