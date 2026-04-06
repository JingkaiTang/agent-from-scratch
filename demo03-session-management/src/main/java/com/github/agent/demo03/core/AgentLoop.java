package com.github.agent.demo03.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agent.demo03.llm.LLMClient;
import com.github.agent.demo03.model.ChatMessage;
import com.github.agent.demo03.tool.Tool;
import com.github.agent.demo03.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent 核心 —— ReAct 循环（支持 SSE 流式响应 + 回调通知）。
 * <p>
 * 使用 LLMClient 的 SSE 流式接口，实现逐 token 推送到 GUI 的效果，
 * 同时保持对 tool_calls 的完整支持。
 * <pre>
 * while (未完成 && 未超过最大步数) {
 *     1. 把当前消息列表以 SSE 流式方式发给 LLM
 *     2. 如果 LLM 返回 tool_calls → 执行工具 → 回调通知 → 把结果追加到消息列表
 *     3. 如果 LLM 返回纯文本（流式逐 token 推送）→ 任务完成，退出循环
 * }
 * </pre>
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final int maxSteps;

    /** 可选的回调，用于通知 GUI 中间过程 */
    private AgentCallback callback;

    /** 对话历史，跨多次 run() 调用保持上下文（支持会话切换时替换） */
    private List<ChatMessage> conversationHistory = new ArrayList<>();

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, String systemPrompt, int maxSteps) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.maxSteps = maxSteps;

        // 初始化时添加 system prompt
        conversationHistory.add(ChatMessage.system(systemPrompt));
    }

    /**
     * 设置回调监听器，用于 GUI 实时获取中间过程。
     */
    public void setCallback(AgentCallback callback) {
        this.callback = callback;
    }

    /**
     * 替换当前对话历史 —— 用于会话切换时加载目标会话的上下文。
     * <p>
     * 会将传入的消息列表复制一份作为新的对话历史，避免外部修改影响内部状态。
     * 如果传入的列表中没有 system 消息，会自动在头部插入 system prompt。
     *
     * @param history 目标会话的消息列表
     */
    public void setConversationHistory(List<ChatMessage> history) {
        conversationHistory = new ArrayList<>(history);
        // 确保对话历史以 system prompt 开头
        if (conversationHistory.isEmpty() || !"system".equals(conversationHistory.get(0).getRole())) {
            conversationHistory.add(0, ChatMessage.system(systemPrompt));
        }
        log.info("对话历史已替换，当前消息数: {}", conversationHistory.size());
    }

    /**
     * 重置对话历史 —— 清空所有消息，仅保留 system prompt。
     * <p>
     * 用于切换到新的空白会话时，确保 Agent 从干净的上下文开始。
     */
    public void resetConversation() {
        conversationHistory.clear();
        conversationHistory.add(ChatMessage.system(systemPrompt));
        log.info("对话历史已重置（仅保留 system prompt）");
    }

    /**
     * 获取当前对话历史的只读副本。
     * <p>
     * 返回的是一份拷贝，外部修改不会影响 AgentLoop 内部状态。
     * 用于会话管理层同步对话记录。
     *
     * @return 对话历史的不可变副本
     */
    public List<ChatMessage> getConversationHistory() {
        return Collections.unmodifiableList(new ArrayList<>(conversationHistory));
    }

    /**
     * 执行一次完整的 Agent 任务（使用 SSE 流式响应）。
     *
     * @param userTask 用户的任务描述
     * @return Agent 的最终回复
     */
    public String run(String userTask) {
        log.info("========== Agent 启动 ==========");
        log.info("任务: {}", userTask);

        // 追加用户消息到对话历史（保持上下文连续性）
        conversationHistory.add(ChatMessage.user(userTask));

        // 获取工具 schema
        List<Map<String, Object>> toolsSchema = toolRegistry.toOpenAIToolsSchema();

        for (int step = 1; step <= maxSteps; step++) {
            log.info("---------- Step {}/{} ----------", step, maxSteps);

            try {
                // 标记是否为纯文本回复（非 tool_calls），用于控制流式回调
                boolean[] isTextReply = {false};

                // 1. 以 SSE 流式方式调用 LLM
                ChatMessage assistantMsg = llmClient.chatStream(conversationHistory, toolsSchema, token -> {
                    // 流式 token 回调 —— 仅在纯文本回复时推送到 GUI
                    if (!isTextReply[0]) {
                        // 首个 token 到达，通知 GUI 开始流式输出
                        isTextReply[0] = true;
                        if (callback != null) {
                            callback.onStreamStart();
                        }
                    }
                    if (callback != null) {
                        callback.onStreamToken(token);
                    }
                });

                conversationHistory.add(assistantMsg);

                // 2. 检查是否有工具调用
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    // 有工具调用 → 逐个执行
                    for (ChatMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
                        String toolName = toolCall.getFunction().getName();
                        String argsJson = toolCall.getFunction().getArguments();
                        log.info("🔧 调用工具: {}({})", toolName, argsJson);

                        // 回调通知：工具调用
                        if (callback != null) {
                            callback.onToolCall(toolName, argsJson);
                        }

                        String result = executeTool(toolName, argsJson);
                        log.info("📋 工具结果: {}", result);

                        // 回调通知：工具结果
                        if (callback != null) {
                            callback.onToolResult(toolName, result);
                        }

                        // 把工具执行结果追加到对话历史
                        conversationHistory.add(ChatMessage.toolResult(toolCall.getId(), result));
                    }
                } else {
                    // 没有工具调用 → 认为任务完成
                    String finalAnswer = assistantMsg.getContent();
                    log.info("✅ Agent 完成，最终回复:\n{}", finalAnswer);

                    // 通知流式输出完成
                    if (callback != null) {
                        callback.onStreamComplete(finalAnswer);
                    }

                    return finalAnswer;
                }

            } catch (Exception e) {
                log.error("Step {} 执行异常: {}", step, e.getMessage(), e);
                String errorMsg = "Agent 执行出错: " + e.getMessage();

                // 回调通知：异常
                if (callback != null) {
                    callback.onError(errorMsg);
                }

                return errorMsg;
            }
        }

        log.warn("⚠️ 达到最大步数 {} 限制", maxSteps);
        return "达到最大步数限制，Agent 未能完成任务。";
    }

    private String executeTool(String toolName, String argsJson) {
        Tool tool = toolRegistry.get(toolName);
        if (tool == null) {
            return "错误: 未知工具 '" + toolName + "'";
        }
        try {
            Map<String, Object> args = MAPPER.readValue(argsJson, new TypeReference<>() {});
            return tool.execute(args);
        } catch (Exception e) {
            return "工具执行失败: " + e.getMessage();
        }
    }
}