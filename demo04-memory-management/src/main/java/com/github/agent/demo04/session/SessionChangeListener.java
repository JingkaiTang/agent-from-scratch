package com.github.agent.demo04.session;

/**
 * 会话变更监听器 —— 当会话列表或当前活跃会话发生变化时，通知 GUI 层刷新。
 * <p>
 * 这是一个观察者模式的回调接口，由 {@link SessionManager} 在以下场景触发：
 * <ul>
 *   <li>创建新会话</li>
 *   <li>切换当前活跃会话</li>
 *   <li>删除会话</li>
 *   <li>会话标题或排序发生变化（如新消息导致标题自动生成）</li>
 * </ul>
 */
public interface SessionChangeListener {

    /**
     * 会话列表发生变化时回调（新增、删除、排序变化等）。
     * <p>
     * GUI 层应在此回调中刷新侧边栏的会话列表。
     */
    void onSessionListChanged();

    /**
     * 当前活跃会话切换时回调。
     * <p>
     * GUI 层应在此回调中切换聊天区域的内容，展示新会话的对话历史。
     *
     * @param session 切换后的当前活跃会话
     */
    void onActiveSessionChanged(Session session);
}
