package com.github.agent.demo00.tool;

import java.util.*;

/**
 * 工具注册表 —— 管理所有可用工具，提供查找和 schema 生成能力。
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public Tool get(String name) {
        return tools.get(name);
    }

    public Collection<Tool> all() {
        return tools.values();
    }

    /**
     * 生成 OpenAI function calling 格式的 tools 列表。
     * 每个元素形如:
     * <pre>
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": { ... }
     *   }
     * }
     * </pre>
     */
    public List<Map<String, Object>> toOpenAIToolsSchema() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Tool tool : tools.values()) {
            Map<String, Object> functionDef = new LinkedHashMap<>();
            functionDef.put("name", tool.name());
            functionDef.put("description", tool.description());
            functionDef.put("parameters", tool.parameters());

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "function");
            entry.put("function", functionDef);
            result.add(entry);
        }
        return result;
    }
}
