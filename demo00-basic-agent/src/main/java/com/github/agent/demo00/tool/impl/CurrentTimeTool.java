package com.github.agent.demo00.tool.impl;

import com.github.agent.demo00.tool.Tool;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 获取当前时间的工具 —— 演示无参数工具。
 */
public class CurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "get_current_time";
    }

    @Override
    public String description() {
        return "获取当前的日期和时间。无需参数。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss (EEEE)");
        return "当前时间: " + now.format(formatter);
    }
}
