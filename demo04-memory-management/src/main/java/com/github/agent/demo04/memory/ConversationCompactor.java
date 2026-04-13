package com.github.agent.demo04.memory;

import com.github.agent.demo04.llm.LLMClient;
import com.github.agent.demo04.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话压缩器 —— 将过长的对话历史压缩为"摘要 + 最近对话"的形式。
 * <p>
 * 压缩流程：
 * <ol>
 *   <li>将消息列表分为三部分：system prompt、旧消息（待压缩）、最近 N 轮对话（保留）</li>
 *   <li>将旧消息格式化为文本，拼接摘要提示词，调用 LLM 生成摘要</li>
 *   <li>组装新的消息列表：[system prompt, 摘要消息(system), 最近 N 轮对话]</li>
 * </ol>
 * <p>
 * "轮"的定义：从一条 user 消息开始，到下一条 user 消息之前的所有消息为一轮。
 * 一轮可能包含：user → assistant(tool_calls) → tool(result) → assistant(reply)。
 */
public class ConversationCompactor {

    private static final Logger log = LoggerFactory.getLogger(ConversationCompactor.class);

    private final LLMClient llmClient;
    private final TokenCounter tokenCounter;

    public ConversationCompactor(LLMClient llmClient, TokenCounter tokenCounter) {
        this.llmClient = llmClient;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 压缩对话历史。
     * <p>
     * 保留 system prompt 和最近 keepRecentRounds 轮对话，
     * 将中间的旧消息用 LLM 压缩为一条摘要。
     *
     * @param messages         完整的对话历史（包含 system prompt）
     * @param keepRecentRounds 保留最近的对话轮数
     * @return 压缩结果，包含新的消息列表和压缩统计信息
     */
    public CompactionResult compact(List<ChatMessage> messages, int keepRecentRounds) {
        int tokensBefore = tokenCounter.estimateTokens(messages);

        // 1. 提取 system prompt（第一条消息）
        ChatMessage systemMsg = messages.get(0);
        List<ChatMessage> nonSystemMessages = messages.subList(1, messages.size());

        // 2. 找到最近 N 轮的起始位置
        int recentStartIndex = findRecentRoundsStartIndex(nonSystemMessages, keepRecentRounds);

        // 3. 分割为旧消息和最近消息
        List<ChatMessage> oldMessages = nonSystemMessages.subList(0, recentStartIndex);
        List<ChatMessage> recentMessages = nonSystemMessages.subList(recentStartIndex, nonSystemMessages.size());

        if (oldMessages.isEmpty()) {
            log.info("没有可以压缩的旧消息，跳过压缩");
            return null;
        }

        log.info("开始压缩: 总消息 {} 条, 旧消息 {} 条, 保留最近 {} 条",
                messages.size(), oldMessages.size(), recentMessages.size());

        // 4. 将旧消息格式化为文本
        String oldConversationText = formatMessagesAsText(oldMessages);

        // 5. 调用 LLM 生成摘要
        String summary = generateSummary(oldConversationText);
        if (summary == null) {
            log.error("LLM 摘要生成失败，跳过压缩");
            return null;
        }

        // 6. 组装新的消息列表
        List<ChatMessage> compactedHistory = new ArrayList<>();
        compactedHistory.add(systemMsg);
        // 摘要作为 system 消息插入（让 Agent 知道这是之前对话的总结）
        compactedHistory.add(ChatMessage.system(
                "[以下是之前对话的摘要，请在后续回复中参考这些信息]\n\n" + summary));
        compactedHistory.addAll(recentMessages);

        int tokensAfter = tokenCounter.estimateTokens(compactedHistory);

        CompactionResult result = new CompactionResult(
                compactedHistory, summary, tokensBefore, tokensAfter, oldMessages.size());

        log.info("压缩完成: {}", result);

        return result;
    }

    /**
     * 从消息列表末尾倒数，找到最近 N 轮对话的起始索引。
     * <p>
     * "轮"以 user 消息为分界：每遇到一条 role=user 的消息，轮数 +1。
     *
     * @param messages         非 system 的消息列表
     * @param keepRecentRounds 要保留的轮数
     * @return 最近 N 轮对话在列表中的起始索引
     */
    private int findRecentRoundsStartIndex(List<ChatMessage> messages, int keepRecentRounds) {
        int roundCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                roundCount++;
                if (roundCount >= keepRecentRounds) {
                    return i;
                }
            }
        }
        // 如果轮数不够，返回 0（保留所有消息，不压缩）
        return 0;
    }

    /**
     * 将消息列表格式化为可读的文本（用于发给 LLM 做摘要）。
     */
    private String formatMessagesAsText(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role = switch (msg.getRole()) {
                case "user" -> "用户";
                case "assistant" -> "Agent";
                case "tool" -> "工具结果";
                default -> msg.getRole();
            };

            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                sb.append(role).append(": ").append(msg.getContent()).append("\n\n");
            }

            // 包含工具调用信息
            if (msg.getToolCalls() != null) {
                for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                    sb.append("Agent 调用工具: ").append(tc.getFunction().getName())
                            .append("(").append(tc.getFunction().getArguments()).append(")\n\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 调用 LLM 生成摘要。
     * <p>
     * 使用非流式调用（摘要不需要实时展示），使用 MemoryConfig 中的摘要提示词。
     *
     * @param conversationText 格式化后的对话文本
     * @return 摘要文本；失败时返回 null
     */
    private String generateSummary(String conversationText) {
        try {
            String prompt = MemoryConfig.SUMMARY_PROMPT + conversationText;

            // 构造一个简单的对话：只有 system + user，不带 tools
            List<ChatMessage> summaryMessages = List.of(
                    ChatMessage.system("你是一个专业的对话摘要助手，擅长提取关键信息并生成简洁准确的摘要。"),
                    ChatMessage.user(prompt)
            );

            ChatMessage response = llmClient.chat(summaryMessages, Collections.emptyList());
            String summary = response.getContent();

            log.debug("LLM 生成摘要: {}", summary);
            return summary;

        } catch (Exception e) {
            log.error("调用 LLM 生成摘要失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
