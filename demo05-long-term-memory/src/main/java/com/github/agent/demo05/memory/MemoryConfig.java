package com.github.agent.demo05.memory;

/**
 * 记忆管理配置 —— 控制上下文压缩的行为参数。
 * <p>
 * 所有参数通过 {@code ~/.afs/config.json} 配置，由
 * {@link com.github.agent.demo05.config.AppConfigLoader} 加载。
 * <p>
 * 参数说明：
 * <ul>
 *   <li>{@code maxContextTokens} —— 上下文 token 上限。超过此值的 80%（由 threshold 控制）时触发压缩。
 *       默认 8000，教学演示用途，实际生产中应根据模型能力设置（如 GPT-4o 支持 128K）。</li>
 *   <li>{@code compactionThreshold} —— 压缩触发阈值，范围 0.0~1.0。
 *       当 token 用量达到 maxContextTokens * threshold 时触发压缩。
 *       默认 0.8（即 80%），留出 20% buffer 给 LLM 回复和压缩摘要本身。</li>
 *   <li>{@code keepRecentRounds} —— 压缩时保留最近的对话轮数。
 *       一轮 = 一次 user 消息 + 对应的 assistant 回复（可能包含 tool_calls 和 tool 结果）。
 *       默认 3 轮，保证近期对话的连贯性。</li>
 * </ul>
 */
public class MemoryConfig {

    private final int maxContextTokens;
    private final double compactionThreshold;
    private final int keepRecentRounds;

    /**
     * 摘要提示词 —— 用于指导 LLM 将旧对话压缩为简洁摘要。
     * <p>
     * 要求摘要保留：关键事实、用户偏好、重要决策和结论。
     */
    public static final String SUMMARY_PROMPT = """
            请将以下对话历史压缩为一段简洁的摘要。要求：
            1. 保留所有关键事实和信息（如用户提到的名字、数字、文件路径等）
            2. 保留用户表达的偏好和需求
            3. 保留重要的决策和结论
            4. 保留工具调用的关键结果
            5. 用第三人称描述（"用户曾..."、"Agent 帮助用户..."）
            6. 控制在 200 字以内

            对话历史：
            """;

    public MemoryConfig(int maxContextTokens, double compactionThreshold, int keepRecentRounds) {
        this.maxContextTokens = maxContextTokens;
        this.compactionThreshold = compactionThreshold;
        this.keepRecentRounds = keepRecentRounds;
    }

    /**
     * 使用默认值创建配置。
     */
    public static MemoryConfig defaults() {
        return new MemoryConfig(8000, 0.8, 3);
    }

    /**
     * 计算触发压缩的 token 阈值。
     *
     * @return maxContextTokens * compactionThreshold
     */
    public int getCompactionTriggerTokens() {
        return (int) (maxContextTokens * compactionThreshold);
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public double getCompactionThreshold() {
        return compactionThreshold;
    }

    public int getKeepRecentRounds() {
        return keepRecentRounds;
    }

    @Override
    public String toString() {
        return "MemoryConfig{maxContextTokens=" + maxContextTokens +
                ", compactionThreshold=" + compactionThreshold +
                ", keepRecentRounds=" + keepRecentRounds +
                ", triggerAt=" + getCompactionTriggerTokens() + "}";
    }
}
