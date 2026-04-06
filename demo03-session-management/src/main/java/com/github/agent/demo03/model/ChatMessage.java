package com.github.agent.demo03.model;

import java.util.List;
import java.util.Map;

/**
 * 统一消息模型，对齐 OpenAI Chat Completion API 的消息格式。
 * <p>
 * role 取值: system / user / assistant / tool
 */
public class ChatMessage {

    private String role;
    private String content;

    // ---- function calling 相关 ----
    /** assistant 消息中可能携带的工具调用列表 */
    private List<ToolCall> toolCalls;
    /** tool 消息需要带上对应的 tool_call_id */
    private String toolCallId;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // ---------- 便捷工厂方法 ----------

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage toolResult(String toolCallId, String content) {
        ChatMessage msg = new ChatMessage("tool", content);
        msg.setToolCallId(toolCallId);
        return msg;
    }

    // ---------- getter / setter ----------

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(role).append("] ");
        if (content != null) sb.append(content);
        if (toolCalls != null) sb.append(" toolCalls=").append(toolCalls);
        if (toolCallId != null) sb.append(" (toolCallId=").append(toolCallId).append(")");
        return sb.toString();
    }

    // ========== 内嵌类：ToolCall ==========

    public static class ToolCall {
        private String id;
        private String type; // "function"
        private FunctionCall function;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public FunctionCall getFunction() { return function; }
        public void setFunction(FunctionCall function) { this.function = function; }

        @Override
        public String toString() {
            return "ToolCall{id='" + id + "', function=" + function + "}";
        }
    }

    public static class FunctionCall {
        private String name;
        private String arguments; // JSON string

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }

        @Override
        public String toString() {
            return name + "(" + arguments + ")";
        }
    }
}
