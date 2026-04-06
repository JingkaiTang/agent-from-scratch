package com.github.agent.demo03.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.agent.demo03.model.ChatMessage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 会话数据模型 —— 每个会话拥有独立的对话历史和元数据。
 * <p>
 * 核心字段：
 * <ul>
 *   <li>{@code id} —— 会话唯一标识（UUID 格式）</li>
 *   <li>{@code title} —— 会话标题（自动从首条用户消息生成，也可手动修改）</li>
 *   <li>{@code createdAt} —— 创建时间</li>
 *   <li>{@code updatedAt} —— 最后更新时间（每次追加消息时自动刷新）</li>
 *   <li>{@code messages} —— 对话消息列表（包含 system/user/assistant/tool 消息）</li>
 * </ul>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>构造时自动生成 UUID 和设置创建时间</li>
 *   <li>追加消息时自动更新 {@code updatedAt}</li>
 *   <li>首条用户消息到达时自动生成标题（取前 20 个字符）</li>
 *   <li>支持 Jackson JSON 序列化/反序列化（用于持久化存储）</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Session {

    /** 会话标题自动生成时的最大字符数 */
    private static final int TITLE_MAX_LENGTH = 20;

    /** 会话唯一标识（UUID 格式） */
    @JsonProperty("id")
    private String id;

    /** 会话标题 */
    @JsonProperty("title")
    private String title;

    /** 创建时间 */
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    /** 对话消息列表 */
    @JsonProperty("messages")
    private List<ChatMessage> messages;

    // ========== 构造方法 ==========

    /**
     * 无参构造方法 —— Jackson 反序列化时使用。
     */
    public Session() {
        this.messages = new ArrayList<>();
    }

    /**
     * 创建一个新的空白会话。
     * <p>
     * 自动生成唯一的 UUID 作为会话 ID，并设置创建时间和更新时间为当前时间。
     *
     * @return 新创建的空白会话
     */
    public static Session create() {
        Session session = new Session();
        session.id = UUID.randomUUID().toString();
        session.title = "新会话";
        session.createdAt = LocalDateTime.now();
        session.updatedAt = LocalDateTime.now();
        return session;
    }

    // ========== 业务方法 ==========

    /**
     * 向会话中追加一条消息，并自动更新最后更新时间。
     * <p>
     * 如果这是第一条用户消息且标题尚未自定义，则自动生成标题。
     *
     * @param message 要追加的消息
     */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        // 每次追加消息都刷新更新时间
        updatedAt = LocalDateTime.now();
        // 尝试自动生成标题
        autoGenerateTitle();
    }

    /**
     * 自动生成会话标题 —— 当标题为默认值且存在用户消息时，
     * 取第一条用户消息内容的前 {@value #TITLE_MAX_LENGTH} 个字符作为标题。
     */
    private void autoGenerateTitle() {
        // 仅当标题为默认值时才自动生成
        if (!"新会话".equals(title)) {
            return;
        }
        // 查找第一条用户消息
        for (ChatMessage msg : messages) {
            if ("user".equals(msg.getRole()) && msg.getContent() != null && !msg.getContent().isBlank()) {
                String content = msg.getContent().trim();
                // 取前 TITLE_MAX_LENGTH 个字符，超出部分用省略号
                if (content.length() > TITLE_MAX_LENGTH) {
                    title = content.substring(0, TITLE_MAX_LENGTH) + "...";
                } else {
                    title = content;
                }
                break;
            }
        }
    }

    // ========== getter / setter ==========

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public String toString() {
        return "Session{id='" + id + "', title='" + title + "', messages=" + messages.size() +
                ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + "}";
    }
}
