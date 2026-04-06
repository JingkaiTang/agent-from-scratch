package com.github.agent.demo03.agent;

import com.github.agent.demo03.core.AgentCallback;
import com.github.agent.demo03.core.AgentLoop;
import com.github.agent.demo03.llm.LLMClient;
import com.github.agent.demo03.model.ChatMessage;
import com.github.agent.demo03.session.Session;
import com.github.agent.demo03.session.SessionChangeListener;
import com.github.agent.demo03.session.SessionManager;
import com.github.agent.demo03.session.SessionStore;
import com.github.agent.demo03.tool.ToolRegistry;
import com.github.agent.demo03.tool.impl.ExecTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Agent 服务层 —— 封装 Agent 的初始化、生命周期管理、会话管理和异步调用。
 * <p>
 * 职责：
 * <ul>
 *   <li>根据 {@link AgentConfig} 初始化 LLMClient、ToolRegistry、AgentLoop</li>
 *   <li>初始化 {@link SessionManager}，管理多会话的创建、切换、删除</li>
 *   <li>协调 SessionManager 与 AgentLoop 之间的交互（会话切换时重建上下文）</li>
 *   <li>提供异步的 {@link #sendMessage(String, Consumer, Consumer)} 方法供 GUI 调用</li>
 *   <li>管理后台线程池的生命周期</li>
 * </ul>
 * <p>
 * 与 GUI 完全解耦，仅通过回调接口和 Consumer 函数式接口进行通信。
 * 是 SessionManager 与 AgentLoop 之间的桥梁 —— SessionManager 不直接操作 AgentLoop。
 */
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentConfig config;
    private AgentLoop agentLoop;

    /** 会话管理器 —— 管理多会话的生命周期和持久化 */
    private SessionManager sessionManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-worker");
        t.setDaemon(true);
        return t;
    });

    public AgentService(AgentConfig config) {
        this.config = config;
    }

    /**
     * 初始化 Agent 组件：创建 LLMClient、注册工具、创建 AgentLoop，并初始化会话管理器。
     *
     * @return true 表示初始化成功
     */
    public boolean initialize() {
        if (!config.isApiKeyConfigured()) {
            log.error("未设置 OPENAI_API_KEY 环境变量");
            return false;
        }

        log.info("模型: {} @ {}", config.getModel(), config.getBaseUrl());

        // 初始化 LLM 客户端
        LLMClient llmClient = new LLMClient(config.getApiKey(), config.getBaseUrl(), config.getModel());

        // 注册工具
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new ExecTool());
        log.info("已注册工具: {}", toolRegistry.all().stream().map(t -> t.name()).toList());

        // 创建 Agent
        agentLoop = new AgentLoop(llmClient, toolRegistry, AgentConfig.SYSTEM_PROMPT, config.getMaxSteps());

        // 初始化会话管理器（加载历史会话）
        SessionStore store = new SessionStore();
        sessionManager = new SessionManager(store);
        sessionManager.init();

        // 用当前活跃会话的消息列表恢复 AgentLoop 的对话上下文
        Session currentSession = sessionManager.getCurrentSession();
        if (currentSession != null && !currentSession.getMessages().isEmpty()) {
            agentLoop.setConversationHistory(currentSession.getMessages());
            log.info("已从会话 '{}' 恢复对话上下文（{} 条消息）",
                    currentSession.getTitle(), currentSession.getMessages().size());
        }

        return true;
    }

    /**
     * 设置 Agent 回调监听器，用于实时获取工具调用等中间过程。
     */
    public void setCallback(AgentCallback callback) {
        if (agentLoop != null) {
            agentLoop.setCallback(callback);
        }
    }

    // ==================== 会话管理方法 ====================

    /**
     * 创建一个新的空白会话，并重置 AgentLoop 的对话上下文。
     *
     * @return 新创建的会话
     */
    public Session createSession() {
        Session session = sessionManager.createSession();
        // 重置 AgentLoop 上下文（新会话从干净状态开始）
        agentLoop.resetConversation();
        log.info("已创建新会话并重置 Agent 上下文: {}", session.getId());
        return session;
    }

    /**
     * 切换到指定会话，并用目标会话的消息列表重建 AgentLoop 的对话上下文。
     *
     * @param sessionId 目标会话 ID
     * @return 切换后的目标会话；如果 ID 不存在则返回 null
     */
    public Session switchSession(String sessionId) {
        Session target = sessionManager.switchSession(sessionId);
        if (target == null) {
            return null;
        }

        // 用目标会话的消息列表重建 AgentLoop 上下文
        if (target.getMessages().isEmpty()) {
            // 空白会话 → 重置上下文
            agentLoop.resetConversation();
            log.info("切换到空白会话，已重置 Agent 上下文");
        } else {
            // 有历史消息 → 替换上下文
            agentLoop.setConversationHistory(target.getMessages());
            log.info("切换到会话 '{}'，已恢复 {} 条历史消息到 Agent 上下文",
                    target.getTitle(), target.getMessages().size());
        }

        return target;
    }

    /**
     * 删除指定会话。
     * <p>
     * 如果删除的是当前活跃会话，SessionManager 会自动切换到其他会话，
     * 此方法会同步更新 AgentLoop 的对话上下文。
     *
     * @param sessionId 要删除的会话 ID
     */
    public void deleteSession(String sessionId) {
        // 记录删除前的活跃会话 ID，用于判断是否需要切换上下文
        String previousActiveId = sessionManager.getActiveSessionId();

        sessionManager.deleteSession(sessionId);

        // 如果删除的是之前的活跃会话，需要同步更新 AgentLoop 上下文
        if (sessionId.equals(previousActiveId)) {
            Session newActive = sessionManager.getCurrentSession();
            if (newActive != null && !newActive.getMessages().isEmpty()) {
                agentLoop.setConversationHistory(newActive.getMessages());
                log.info("删除活跃会话后，已切换到会话 '{}' 并恢复上下文", newActive.getTitle());
            } else {
                agentLoop.resetConversation();
                log.info("删除活跃会话后，已重置 Agent 上下文");
            }
        }
    }

    /**
     * 获取会话管理器 —— 供 GUI 层查询会话列表、注册监听器等。
     *
     * @return 会话管理器实例
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * 获取所有会话（按最后更新时间倒序排列）。
     * <p>
     * 便捷方法，等价于 {@code getSessionManager().getAllSessionsSorted()}。
     *
     * @return 排序后的会话列表
     */
    public List<Session> getAllSessionsSorted() {
        return sessionManager.getAllSessionsSorted();
    }

    /**
     * 获取当前活跃会话。
     * <p>
     * 便捷方法，等价于 {@code getSessionManager().getCurrentSession()}。
     *
     * @return 当前活跃会话
     */
    public Session getCurrentSession() {
        return sessionManager.getCurrentSession();
    }

    /**
     * 添加会话变更监听器。
     * <p>
     * 便捷方法，等价于 {@code getSessionManager().addListener(listener)}。
     *
     * @param listener 监听器
     */
    public void addSessionChangeListener(SessionChangeListener listener) {
        sessionManager.addListener(listener);
    }

    // ==================== 消息发送 ====================

    /**
     * 异步发送消息给 Agent。
     * <p>
     * 发送流程：
     * <ol>
     *   <li>将用户消息追加到当前活跃会话（触发持久化和标题自动生成）</li>
     *   <li>通过 AgentLoop 异步执行任务</li>
     *   <li>Agent 回复后，将 assistant 消息同步追加到当前活跃会话</li>
     * </ol>
     *
     * @param message   用户消息
     * @param onSuccess 成功回调（接收 Agent 最终回复）
     * @param onError   异常回调（接收错误信息）
     */
    public void sendMessage(String message, Consumer<String> onSuccess, Consumer<String> onError) {
        // 将用户消息追加到当前活跃会话（持久化 + 标题生成）
        sessionManager.appendMessage(ChatMessage.user(message));

        // 记录发送前 AgentLoop 对话历史的消息数量，用于后续同步新增消息
        int historyBefore = agentLoop.getConversationHistory().size();

        executor.submit(() -> {
            try {
                String response = agentLoop.run(message);

                // Agent 执行完成后，将 AgentLoop 中新增的消息同步到当前活跃会话
                // 新增的消息包括：assistant 消息（含 tool_calls）、tool 结果消息、最终 assistant 回复
                syncNewMessagesToSession(historyBefore);

                onSuccess.accept(response);
            } catch (Exception e) {
                log.error("Agent 调用异常: {}", e.getMessage(), e);

                // 即使异常，也尝试同步已产生的消息
                syncNewMessagesToSession(historyBefore);

                onError.accept("Agent 调用异常: " + e.getMessage());
            }
        });
    }

    /**
     * 将 AgentLoop 中新增的消息同步到当前活跃会话。
     * <p>
     * AgentLoop.run() 执行过程中会向 conversationHistory 追加多条消息
     * （assistant 消息、tool 结果消息等），这些消息需要同步到 Session 中进行持久化。
     * <p>
     * 注意：用户消息已在 sendMessage() 开头通过 sessionManager.appendMessage() 追加，
     * 但 AgentLoop.run() 内部也会追加一条 user 消息（同一条），因此同步时需要跳过
     * AgentLoop 新增的第一条 user 消息，避免重复。
     *
     * @param historyBefore Agent 执行前的对话历史消息数量
     */
    private void syncNewMessagesToSession(int historyBefore) {
        try {
            List<ChatMessage> currentHistory = agentLoop.getConversationHistory();
            if (currentHistory.size() > historyBefore) {
                // 获取新增的消息（从 historyBefore 开始的所有消息）
                List<ChatMessage> newMessages = currentHistory.subList(historyBefore, currentHistory.size());

                for (ChatMessage msg : newMessages) {
                    // 跳过 user 消息（已在 sendMessage 开头追加过）
                    if ("user".equals(msg.getRole())) {
                        continue;
                    }
                    // 将 assistant、tool 等消息同步到会话
                    sessionManager.appendMessage(msg);
                }
                log.debug("已同步 {} 条新消息到当前会话", newMessages.size() - 1);
            }
        } catch (Exception e) {
            log.error("同步消息到会话失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 关闭 Agent 服务，释放线程池资源。
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
