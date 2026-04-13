package com.github.agent.demo04.core;

/**
 * Agent 回调接口 —— 用于 GUI 实时获取 Agent 执行过程中的中间信息。
 * <p>
 * 在 AgentLoop 的 ReAct 循环中，每当发生工具调用、工具返回结果、
 * 流式文本输出或异常时，都会通过此接口通知外部（如 GUI 界面）。
 */
public interface AgentCallback {

    /**
     * 当 Agent 调用工具时触发。
     *
     * @param toolName 工具名称
     * @param argsJson 工具参数（JSON 字符串）
     */
    void onToolCall(String toolName, String argsJson);

    /**
     * 当工具执行完成返回结果时触发。
     *
     * @param toolName 工具名称
     * @param result   工具执行结果
     */
    void onToolResult(String toolName, String result);

    /**
     * 当 Agent 执行过程中发生异常时触发。
     *
     * @param errorMessage 错误信息
     */
    void onError(String errorMessage);

    // ========== 流式响应回调（SSE） ==========

    /**
     * 当 LLM 开始流式输出文本时触发（用于 GUI 创建空的消息气泡）。
     */
    default void onStreamStart() {}

    /**
     * 当 LLM 流式输出一个文本片段（token/chunk）时触发。
     *
     * @param token 本次收到的文本片段
     */
    default void onStreamToken(String token) {}

    /**
     * 当 LLM 流式输出完成时触发。
     *
     * @param fullContent 完整的输出文本
     */
    default void onStreamComplete(String fullContent) {}

    // ========== 记忆管理回调 ==========

    /**
     * 当上下文压缩发生时触发。
     * <p>
     * 在 AgentLoop 的 ReAct 循环中，每次调用 LLM 前会检查 token 用量，
     * 超过阈值时自动触发压缩，并通过此回调通知 GUI 展示压缩信息。
     *
     * @param summary      LLM 生成的摘要文本
     * @param tokensBefore 压缩前的 token 估算数
     * @param tokensAfter  压缩后的 token 估算数
     * @param removedCount 被移除的消息数量
     */
    default void onMemoryCompaction(String summary, int tokensBefore, int tokensAfter, int removedCount) {}
}
