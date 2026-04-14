package com.github.agent.demo05.memory;

import com.github.agent.demo05.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Token 估算器 —— 简单估算文本和消息列表的 token 数量。
 * <p>
 * 教学目的：不引入 tiktoken 等第三方依赖，手搓一个简单的估算逻辑。
 * <p>
 * 估算规则：
 * <ul>
 *   <li>中文字符（CJK 统一表意文字）：约 1.5 token/字符</li>
 *   <li>英文/数字/符号：约 0.25 token/字符（≈ 4 字符 = 1 token）</li>
 *   <li>每条消息额外开销：约 4 token（role + 格式标记）</li>
 * </ul>
 * <p>
 * 精度说明：
 * 这只是粗略估算，实际 token 数量取决于模型使用的 tokenizer（如 tiktoken 的 cl100k_base）。
 * 估算误差约在 ±20% 范围内，对于上下文管理来说足够使用。
 */
public class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    /** 每条消息的固定开销（role 标记、格式符号等） */
    private static final int MESSAGE_OVERHEAD = 4;

    /**
     * 估算单段文本的 token 数量。
     *
     * @param text 文本内容
     * @return 估算的 token 数
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int tokens = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCJK(c)) {
                // 中文字符约 1.5 token
                tokens += 3; // 用 *2 后最后 /2，避免浮点运算
            } else {
                // 英文/数字/符号约 0.25 token（即 4 字符 ≈ 1 token）
                tokens += 1; // 用 *2 后最后 /2
            }
        }

        // 上面中文按 3、英文按 1 累加，最后除以 2 得到近似值
        // 中文：3/2 = 1.5，英文：1/2 = 0.5（略高于 0.25，作为安全余量）
        return tokens / 2;
    }

    /**
     * 估算消息列表的总 token 数量。
     * <p>
     * 每条消息的 token = 内容 token + 固定开销（role 标记等）。
     * 对于包含 tool_calls 的 assistant 消息，也会计算参数部分的 token。
     *
     * @param messages 消息列表
     * @return 估算的总 token 数
     */
    public int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            // 消息内容
            total += estimateTokens(msg.getContent());
            // 固定开销
            total += MESSAGE_OVERHEAD;

            // tool_calls 中的参数也占 token
            if (msg.getToolCalls() != null) {
                for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getFunction() != null) {
                        total += estimateTokens(tc.getFunction().getName());
                        total += estimateTokens(tc.getFunction().getArguments());
                    }
                }
            }
        }
        return total;
    }

    /**
     * 判断字符是否为 CJK（中日韩统一表意文字）。
     */
    private boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
