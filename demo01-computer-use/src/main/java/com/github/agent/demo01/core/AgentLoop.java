package com.github.agent.demo01.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agent.demo01.llm.LLMClient;
import com.github.agent.demo01.model.ChatMessage;
import com.github.agent.demo01.tool.Tool;
import com.github.agent.demo01.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Agent 核心 —— ReAct 循环。
 * <p>
 * 这是整个 demo 的心脏。工作流程：
 * <pre>
 * while (未完成 && 未超过最大步数) {
 *     1. 把当前消息列表发给 LLM
 *     2. 如果 LLM 返回 tool_calls → 执行工具 → 把结果追加到消息列表
 *     3. 如果 LLM 返回纯文本 → 任务完成，退出循环
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

    /** 对话历史，跨多次 run() 调用保持上下文 */
    private final List<ChatMessage> conversationHistory = new ArrayList<>();

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, String systemPrompt, int maxSteps) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.maxSteps = maxSteps;

        // 初始化时添加 system prompt
        conversationHistory.add(ChatMessage.system(systemPrompt));
    }

    /**
     * 执行一次完整的 Agent 任务。
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
                // 1. 调用 LLM
                ChatMessage assistantMsg = llmClient.chat(conversationHistory, toolsSchema);
                conversationHistory.add(assistantMsg);

                // 2. 检查是否有工具调用
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    // 有工具调用 → 逐个执行
                    for (ChatMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
                        String toolName = toolCall.getFunction().getName();
                        String argsJson = toolCall.getFunction().getArguments();
                        log.info("🔧 调用工具: {}({})", toolName, argsJson);

                        String result = executeTool(toolName, argsJson);
                        log.info("📋 工具结果: {}", result);

                        // 把工具执行结果追加到对话历史
                        conversationHistory.add(ChatMessage.toolResult(toolCall.getId(), result));
                    }
                } else {
                    // 没有工具调用 → 认为任务完成
                    String finalAnswer = assistantMsg.getContent();
                    log.info("✅ Agent 完成，最终回复:\n{}", finalAnswer);
                    return finalAnswer;
                }

            } catch (Exception e) {
                log.error("Step {} 执行异常: {}", step, e.getMessage(), e);
                return "Agent 执行出错: " + e.getMessage();
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
