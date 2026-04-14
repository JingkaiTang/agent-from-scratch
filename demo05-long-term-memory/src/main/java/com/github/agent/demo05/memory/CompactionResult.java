package com.github.agent.demo05.memory;

import com.github.agent.demo05.model.ChatMessage;

import java.util.List;

/**
 * 上下文压缩结果 —— 记录一次压缩操作的所有信息。
 * <p>
 * 包含：
 * <ul>
 *   <li>压缩后的消息列表（system prompt + 摘要消息 + 最近 N 轮对话）</li>
 *   <li>LLM 生成的摘要文本</li>
 *   <li>压缩前后的 token 数对比</li>
 *   <li>被移除的消息数量</li>
 * </ul>
 * <p>
 * 此对象为不可变对象，所有字段在构造时设置，创建后不可修改。
 */
public class CompactionResult {

    /** 压缩后的完整消息列表 */
    private final List<ChatMessage> compactedHistory;

    /** LLM 生成的摘要文本 */
    private final String summary;

    /** 压缩前的 token 估算数 */
    private final int tokensBefore;

    /** 压缩后的 token 估算数 */
    private final int tokensAfter;

    /** 被移除（压缩）的消息数量 */
    private final int removedMessageCount;

    public CompactionResult(List<ChatMessage> compactedHistory, String summary,
                            int tokensBefore, int tokensAfter, int removedMessageCount) {
        this.compactedHistory = List.copyOf(compactedHistory); // 不可变副本
        this.summary = summary;
        this.tokensBefore = tokensBefore;
        this.tokensAfter = tokensAfter;
        this.removedMessageCount = removedMessageCount;
    }

    public List<ChatMessage> getCompactedHistory() {
        return compactedHistory;
    }

    public String getSummary() {
        return summary;
    }

    public int getTokensBefore() {
        return tokensBefore;
    }

    public int getTokensAfter() {
        return tokensAfter;
    }

    public int getRemovedMessageCount() {
        return removedMessageCount;
    }

    /**
     * 计算压缩率（百分比），表示节省了多少 token。
     *
     * @return 压缩率，如 0.65 表示节省了 65% 的 token
     */
    public double getCompressionRatio() {
        if (tokensBefore == 0) return 0;
        return 1.0 - ((double) tokensAfter / tokensBefore);
    }

    @Override
    public String toString() {
        return String.format("CompactionResult{tokens: %d → %d (节省 %.0f%%), 移除 %d 条消息}",
                tokensBefore, tokensAfter, getCompressionRatio() * 100, removedMessageCount);
    }
}
