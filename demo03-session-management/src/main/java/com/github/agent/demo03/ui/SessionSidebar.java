package com.github.agent.demo03.ui;

import com.github.agent.demo03.session.Session;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * 会话列表侧边栏 —— 在聊天窗口左侧展示所有会话，支持创建、切换和删除会话。
 * <p>
 * 功能：
 * <ul>
 *   <li>顶部"+ 新建会话"按钮，点击创建新的空白会话</li>
 *   <li>会话列表按最后更新时间倒序排列，每项显示标题和更新时间</li>
 *   <li>当前活跃会话以高亮背景色标识</li>
 *   <li>左键点击会话项触发切换回调</li>
 *   <li>右键点击会话项弹出上下文菜单，包含"删除会话"选项</li>
 * </ul>
 * <p>
 * 通过回调函数（{@link Consumer}）与外部通信，不直接依赖 AgentService。
 */
public class SessionSidebar extends VBox {

    /** 侧边栏固定宽度 */
    private static final double SIDEBAR_WIDTH = 240;

    /** 时间格式化器 —— 用于显示会话的最后更新时间 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 会话列表容器 */
    private final VBox sessionListContainer;

    // ---- 回调函数 ----
    /** 新建会话回调 */
    private Runnable onNewSession;
    /** 切换会话回调（参数为目标会话 ID） */
    private Consumer<String> onSwitchSession;
    /** 删除会话回调（参数为目标会话 ID） */
    private Consumer<String> onDeleteSession;

    public SessionSidebar() {
        // 设置侧边栏整体样式
        setPrefWidth(SIDEBAR_WIDTH);
        setMinWidth(SIDEBAR_WIDTH);
        setMaxWidth(SIDEBAR_WIDTH);
        setStyle("""
                -fx-background-color: #F5F5F5;
                -fx-border-color: #E0E0E0;
                -fx-border-width: 0 1 0 0;
                """);

        // --- 顶部：标题 + 新建按钮 ---
        Label titleLabel = new Label("💬 会话列表");
        titleLabel.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-text-fill: #333;
                """);

        Button newSessionBtn = new Button("+ 新建会话");
        newSessionBtn.setMaxWidth(Double.MAX_VALUE);
        newSessionBtn.setStyle("""
                -fx-font-size: 13px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
                -fx-background-color: #007AFF;
                -fx-background-radius: 8;
                -fx-padding: 8 16;
                -fx-cursor: hand;
                -fx-background-insets: 0;
                -fx-effect: null;
                """);
        newSessionBtn.setOnAction(e -> {
            if (onNewSession != null) {
                onNewSession.run();
            }
        });

        VBox header = new VBox(8, titleLabel, newSessionBtn);
        header.setPadding(new Insets(16, 12, 12, 12));
        header.setStyle("-fx-border-color: #E0E0E0; -fx-border-width: 0 0 1 0;");

        // --- 中部：会话列表（可滚动） ---
        sessionListContainer = new VBox(2);
        sessionListContainer.setPadding(new Insets(8, 6, 8, 6));

        ScrollPane scrollPane = new ScrollPane(sessionListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #F5F5F5; -fx-background-color: #F5F5F5; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(header, scrollPane);
    }

    // ========== 回调设置方法 ==========

    /**
     * 设置新建会话回调。
     *
     * @param onNewSession 点击"新建会话"按钮时触发的回调
     */
    public void setOnNewSession(Runnable onNewSession) {
        this.onNewSession = onNewSession;
    }

    /**
     * 设置切换会话回调。
     *
     * @param onSwitchSession 点击会话项时触发的回调，参数为目标会话 ID
     */
    public void setOnSwitchSession(Consumer<String> onSwitchSession) {
        this.onSwitchSession = onSwitchSession;
    }

    /**
     * 设置删除会话回调。
     *
     * @param onDeleteSession 右键删除会话时触发的回调，参数为目标会话 ID
     */
    public void setOnDeleteSession(Consumer<String> onDeleteSession) {
        this.onDeleteSession = onDeleteSession;
    }

    // ========== 刷新方法 ==========

    /**
     * 刷新会话列表 —— 根据最新的会话数据重新渲染所有会话项。
     * <p>
     * 必须在 JavaFX Application Thread 上调用。
     *
     * @param sessions        所有会话列表（应已按 updatedAt 倒序排列）
     * @param activeSessionId 当前活跃会话的 ID（用于高亮标识）
     */
    public void refresh(List<Session> sessions, String activeSessionId) {
        sessionListContainer.getChildren().clear();

        for (Session session : sessions) {
            boolean isActive = session.getId().equals(activeSessionId);
            HBox sessionItem = createSessionItem(session, isActive);
            sessionListContainer.getChildren().add(sessionItem);
        }
    }

    // ========== 私有方法 ==========

    /**
     * 创建单个会话列表项。
     * <p>
     * 每个列表项包含：
     * <ul>
     *   <li>会话标题（单行，超出截断）</li>
     *   <li>最后更新时间（小字灰色）</li>
     *   <li>左键点击 → 触发切换回调</li>
     *   <li>右键点击 → 弹出"删除会话"上下文菜单</li>
     *   <li>活跃会话以蓝色左边框 + 浅蓝背景高亮</li>
     * </ul>
     *
     * @param session  会话数据
     * @param isActive 是否为当前活跃会话
     * @return 会话列表项节点
     */
    private HBox createSessionItem(Session session, boolean isActive) {
        // 会话标题
        Label titleLabel = new Label(session.getTitle());
        titleLabel.setMaxWidth(180);
        titleLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: #333;
                -fx-font-weight: %s;
                """.formatted(isActive ? "bold" : "normal"));
        // 超出宽度时显示省略号
        titleLabel.setEllipsisString("...");

        // 最后更新时间
        String timeText = formatTime(session.getUpdatedAt());
        Label timeLabel = new Label(timeText);
        timeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        // 标题 + 时间纵向排列
        VBox textBox = new VBox(2, titleLabel, timeLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        // 整行容器
        HBox item = new HBox(textBox);
        item.setPadding(new Insets(10, 10, 10, 12));
        item.setAlignment(Pos.CENTER_LEFT);

        // 根据是否活跃设置不同样式
        if (isActive) {
            item.setStyle("""
                    -fx-background-color: #E3F2FD;
                    -fx-background-radius: 8;
                    -fx-border-color: #007AFF;
                    -fx-border-width: 0 0 0 3;
                    -fx-border-radius: 8;
                    -fx-cursor: hand;
                    """);
        } else {
            item.setStyle("""
                    -fx-background-color: transparent;
                    -fx-background-radius: 8;
                    -fx-cursor: hand;
                    """);
            // 鼠标悬停效果
            item.setOnMouseEntered(e -> item.setStyle("""
                    -fx-background-color: #EBEBEB;
                    -fx-background-radius: 8;
                    -fx-cursor: hand;
                    """));
            item.setOnMouseExited(e -> item.setStyle("""
                    -fx-background-color: transparent;
                    -fx-background-radius: 8;
                    -fx-cursor: hand;
                    """));
        }

        // 左键点击 → 切换会话
        item.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onSwitchSession != null) {
                onSwitchSession.accept(session.getId());
            }
        });

        // 右键点击 → 弹出上下文菜单（删除会话）
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("🗑 删除会话");
        deleteItem.setOnAction(e -> {
            if (onDeleteSession != null) {
                onDeleteSession.accept(session.getId());
            }
        });
        contextMenu.getItems().add(deleteItem);

        item.setOnContextMenuRequested(e -> {
            contextMenu.show(item, e.getScreenX(), e.getScreenY());
            e.consume();
        });

        return item;
    }

    /**
     * 格式化时间显示 —— 根据时间距离当前的远近，使用不同的格式。
     * <p>
     * 规则：
     * <ul>
     *   <li>今天的消息：显示 "HH:mm"</li>
     *   <li>今年的消息：显示 "MM-dd HH:mm"</li>
     *   <li>更早的消息：显示 "yyyy-MM-dd"</li>
     * </ul>
     *
     * @param dateTime 要格式化的时间
     * @return 格式化后的时间字符串
     */
    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        LocalDateTime now = LocalDateTime.now();
        if (dateTime.toLocalDate().equals(now.toLocalDate())) {
            // 今天 → 只显示时间
            return dateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else if (dateTime.getYear() == now.getYear()) {
            // 今年 → 显示月日+时间
            return dateTime.format(TIME_FORMATTER);
        } else {
            // 更早 → 显示完整日期
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    }
}
