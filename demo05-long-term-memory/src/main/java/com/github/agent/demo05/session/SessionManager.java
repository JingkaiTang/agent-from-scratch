package com.github.agent.demo05.session;

import com.github.agent.demo05.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 会话管理服务 —— 统一管理所有会话的生命周期，是会话管理的核心协调者。
 * <p>
 * 职责：
 * <ul>
 *   <li>维护内存中的会话列表（{@code Map<String, Session>}）和当前活跃会话 ID</li>
 *   <li>提供创建、切换、删除会话的业务方法</li>
 *   <li>向当前活跃会话追加消息，并自动触发持久化</li>
 *   <li>通过 {@link SessionChangeListener} 通知 GUI 层刷新</li>
 * </ul>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>与 AgentLoop 解耦 —— SessionManager 不直接操作 AgentLoop，
 *       而是由上层的 AgentService 负责协调两者</li>
 *   <li>持久化由 {@link SessionStore} 负责，SessionManager 在适当时机调用</li>
 *   <li>使用观察者模式（{@link SessionChangeListener}）通知 GUI 变更</li>
 * </ul>
 */
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** 持久化存储层 */
    private final SessionStore store;

    /** 内存中的会话列表，key 为会话 ID */
    private final Map<String, Session> sessions = new LinkedHashMap<>();

    /** 当前活跃会话的 ID */
    private String activeSessionId;

    /** 会话变更监听器列表（支持多个监听器，线程安全） */
    private final List<SessionChangeListener> listeners = new CopyOnWriteArrayList<>();

    public SessionManager(SessionStore store) {
        this.store = store;
    }

    // ========== 初始化 ==========

    /**
     * 初始化会话管理器 —— 从本地存储加载所有历史会话。
     * <p>
     * 如果没有任何已保存的会话，则自动创建一个空白会话。
     * 自动选中最近更新的会话作为当前活跃会话。
     */
    public void init() {
        // 1. 从本地存储加载所有会话
        List<Session> loaded = store.loadAll();
        for (Session session : loaded) {
            sessions.put(session.getId(), session);
        }
        log.info("已加载 {} 个历史会话", sessions.size());

        // 2. 如果没有任何会话，自动创建一个空白会话
        if (sessions.isEmpty()) {
            Session newSession = Session.create();
            sessions.put(newSession.getId(), newSession);
            store.save(newSession);
            log.info("无历史会话，已自动创建空白会话: {}", newSession.getId());
        }

        // 3. 选中最近更新的会话作为当前活跃会话
        activeSessionId = findMostRecentSessionId();
        log.info("当前活跃会话: {} ({})", getCurrentSession().getTitle(), activeSessionId);
    }

    // ========== 会话管理操作 ==========

    /**
     * 创建一个新的空白会话，并自动切换到该会话。
     *
     * @return 新创建的会话
     */
    public Session createSession() {
        Session session = Session.create();
        sessions.put(session.getId(), session);
        store.save(session);

        // 切换到新会话
        activeSessionId = session.getId();
        log.info("创建新会话: {} ({})", session.getTitle(), session.getId());

        // 通知监听器
        notifySessionListChanged();
        notifyActiveSessionChanged(session);

        return session;
    }

    /**
     * 切换当前活跃会话。
     *
     * @param sessionId 目标会话 ID
     * @return 切换后的目标会话；如果 ID 不存在则返回 null
     */
    public Session switchSession(String sessionId) {
        Session target = sessions.get(sessionId);
        if (target == null) {
            log.warn("切换失败，会话不存在: {}", sessionId);
            return null;
        }

        // 如果已经是当前会话，无需切换
        if (sessionId.equals(activeSessionId)) {
            log.debug("已经是当前活跃会话，无需切换: {}", sessionId);
            return target;
        }

        activeSessionId = sessionId;
        log.info("切换到会话: {} ({})", target.getTitle(), sessionId);

        // 通知监听器
        notifyActiveSessionChanged(target);

        return target;
    }

    /**
     * 删除指定会话。
     * <p>
     * 如果删除的是当前活跃会话，自动切换到最近的其他会话；
     * 如果没有其他会话，则自动创建一个新的空白会话。
     *
     * @param sessionId 要删除的会话 ID
     */
    public void deleteSession(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed == null) {
            log.warn("删除失败，会话不存在: {}", sessionId);
            return;
        }

        // 从本地存储中删除
        store.delete(sessionId);
        log.info("已删除会话: {} ({})", removed.getTitle(), sessionId);

        // 如果删除的是当前活跃会话，需要切换
        if (sessionId.equals(activeSessionId)) {
            if (sessions.isEmpty()) {
                // 没有其他会话了，自动创建一个新的空白会话
                Session newSession = Session.create();
                sessions.put(newSession.getId(), newSession);
                store.save(newSession);
                activeSessionId = newSession.getId();
                log.info("所有会话已删除，自动创建新会话: {}", newSession.getId());
            } else {
                // 切换到最近更新的会话
                activeSessionId = findMostRecentSessionId();
                log.info("自动切换到最近会话: {}", activeSessionId);
            }
            // 通知活跃会话变更
            notifyActiveSessionChanged(getCurrentSession());
        }

        // 通知会话列表变更
        notifySessionListChanged();
    }

    // ========== 消息操作 ==========

    /**
     * 向当前活跃会话追加一条消息。
     * <p>
     * 追加后自动触发：
     * <ul>
     *   <li>会话标题自动生成（如果是首条用户消息）</li>
     *   <li>更新时间刷新</li>
     *   <li>持久化到本地存储</li>
     *   <li>通知 GUI 刷新会话列表（标题和排序可能变化）</li>
     * </ul>
     *
     * @param message 要追加的消息
     */
    public void appendMessage(ChatMessage message) {
        Session current = getCurrentSession();
        if (current == null) {
            log.error("无法追加消息，当前没有活跃会话");
            return;
        }

        // 记录追加前的标题，用于判断标题是否发生变化
        String oldTitle = current.getTitle();

        // 追加消息（Session 内部会自动更新 updatedAt 和尝试生成标题）
        current.addMessage(message);

        // 持久化
        store.save(current);

        // 如果标题发生了变化，通知 GUI 刷新列表
        if (!oldTitle.equals(current.getTitle())) {
            notifySessionListChanged();
        }
    }

    // ========== 查询方法 ==========

    /**
     * 获取当前活跃会话。
     *
     * @return 当前活跃会话，如果没有则返回 null
     */
    public Session getCurrentSession() {
        return sessions.get(activeSessionId);
    }

    /**
     * 获取当前活跃会话的 ID。
     *
     * @return 当前活跃会话 ID
     */
    public String getActiveSessionId() {
        return activeSessionId;
    }

    /**
     * 获取所有会话，按最后更新时间倒序排列（最新的在最前面）。
     *
     * @return 排序后的会话列表
     */
    public List<Session> getAllSessionsSorted() {
        List<Session> sorted = new ArrayList<>(sessions.values());
        sorted.sort(Comparator.comparing(Session::getUpdatedAt, Comparator.reverseOrder()));
        return sorted;
    }

    // ========== 监听器管理 ==========

    /**
     * 添加会话变更监听器。
     *
     * @param listener 监听器
     */
    public void addListener(SessionChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除会话变更监听器。
     *
     * @param listener 监听器
     */
    public void removeListener(SessionChangeListener listener) {
        listeners.remove(listener);
    }

    // ========== 私有方法 ==========

    /**
     * 查找最近更新的会话 ID。
     *
     * @return 最近更新的会话 ID；如果没有会话则返回 null
     */
    private String findMostRecentSessionId() {
        return sessions.values().stream()
                .max(Comparator.comparing(Session::getUpdatedAt))
                .map(Session::getId)
                .orElse(null);
    }

    /**
     * 通知所有监听器：会话列表发生变化。
     */
    private void notifySessionListChanged() {
        for (SessionChangeListener listener : listeners) {
            try {
                listener.onSessionListChanged();
            } catch (Exception e) {
                log.error("通知监听器 onSessionListChanged 异常", e);
            }
        }
    }

    /**
     * 通知所有监听器：当前活跃会话发生变化。
     *
     * @param session 新的活跃会话
     */
    private void notifyActiveSessionChanged(Session session) {
        for (SessionChangeListener listener : listeners) {
            try {
                listener.onActiveSessionChanged(session);
            } catch (Exception e) {
                log.error("通知监听器 onActiveSessionChanged 异常", e);
            }
        }
    }
}
