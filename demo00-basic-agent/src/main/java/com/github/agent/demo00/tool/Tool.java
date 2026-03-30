package com.github.agent.demo00.tool;

import java.util.Map;

/**
 * 工具接口 —— Agent 能调用的每一个"技能"都实现此接口。
 */
public interface Tool {

    /** 工具名称（唯一标识，会传给 LLM） */
    String name();

    /** 工具描述（告诉 LLM 这个工具干什么用） */
    String description();

    /**
     * 工具参数的 JSON Schema（简化版）。
     * 返回一个 Map 直接作为 parameters 字段塞进 function 定义。
     * <p>
     * 示例:
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "expression": { "type": "string", "description": "数学表达式" }
     *   },
     *   "required": ["expression"]
     * }
     * </pre>
     */
    Map<String, Object> parameters();

    /**
     * 执行工具，传入 LLM 给的参数 JSON 解析后的 Map，返回文本结果。
     */
    String execute(Map<String, Object> arguments);
}
