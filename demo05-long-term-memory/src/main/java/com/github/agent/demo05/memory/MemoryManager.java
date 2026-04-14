package com.github.agent.demo05.memory;

import com.github.agent.demo05.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆管理器 —— 短期记忆管理的核心协调者。
 * <p>
 * 职责：
 * <ul>
 *   <li>在每次 LLM 调用前检查当前上下文的 token 用量</li>
 *   <li>当 token 用量超过阈值时，触发对话压缩</li>
 *   <li>协调 {@link TokenCounter} 和 {@link ConversationCompactor} 完成压缩</li>
 * </ul>
 * <p>
 * 使用方式：
 * <pre>
 * // 在 AgentLoop 的每次 LLM 调用前
 * CompactionResult result = memoryManager.checkAndCompact(conversationHistory);
 * if (result != null) {
 *     conversationHistory = new ArrayList<>(result.getCompactedHistory());
 *     // 通知 GUI 展示压缩事件
 * }
 * </pre>
 */
public class MemoryManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryManager.class);

    private final MemoryConfig config;
    private final TokenCounter tokenCounter;
    private final ConversationCompactor compactor;

    public MemoryManager(MemoryConfig config, TokenCounter tokenCounter, ConversationCompactor compactor) {
        this.config = config;
        this.tokenCounter = tokenCounter;
        this.compactor = compactor;
        log.info("记忆管理器已初始化: {}", config);
    }

    /**
     * 检查当前对话历史是否需要压缩，需要则执行压缩。
     * <p>
     * 检查逻辑：
     * <ol>
     *   <li>估算当前消息列表的 token 总量</li>
     *   <li>与触发阈值（maxContextTokens * compactionThreshold）比较</li>
     *   <li>超过阈值则调用 ConversationCompactor 执行压缩</li>
     * </ol>
     *
     * @param conversationHistory 当前的完整对话历史（包含 system prompt）
     * @return 压缩结果；如果不需要压缩或压缩失败则返回 null
     */
    public CompactionResult checkAndCompact(List<ChatMessage> conversationHistory) {
        int currentTokens = tokenCounter.estimateTokens(conversationHistory);
        int triggerTokens = config.getCompactionTriggerTokens();

        log.debug("Token 检查: 当前 {} / 阈值 {} (上限 {})",
                currentTokens, triggerTokens, config.getMaxContextTokens());

        if (currentTokens < triggerTokens) {
            return null; // 未超过阈值，无需压缩
        }

        log.info("Token 用量 {} 超过阈值 {}，触发上下文压缩", currentTokens, triggerTokens);

        // 执行压缩
        CompactionResult result = compactor.compact(conversationHistory, config.getKeepRecentRounds());

        if (result != null) {
            log.info("上下文压缩完成: {} → {} tokens (节省 {}%), 移除 {} 条消息",
                    result.getTokensBefore(), result.getTokensAfter(),
                    (int) (result.getCompressionRatio() * 100), result.getRemovedMessageCount());
        }

        return result;
    }

    /**
     * 获取当前对话历史的 token 估算值（供外部查询，如 GUI 展示）。
     *
     * @param conversationHistory 对话历史
     * @return token 估算值
     */
    public int estimateTokens(List<ChatMessage> conversationHistory) {
        return tokenCounter.estimateTokens(conversationHistory);
    }

    /**
     * 获取记忆管理配置。
     */
    public MemoryConfig getConfig() {
        return config;
    }
}
