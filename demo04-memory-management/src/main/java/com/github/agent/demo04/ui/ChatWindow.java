package com.github.agent.demo04.ui;

import com.github.agent.demo04.agent.AgentService;
import com.github.agent.demo04.core.AgentCallback;
import com.github.agent.demo04.model.ChatMessage;
import com.github.agent.demo04.session.Session;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 聊天窗口 —— 纯 GUI 组件，负责界面布局和用户交互。
 * <p>
 * 在 Demo03 基础上新增记忆压缩气泡的展示：当 Agent 触发上下文压缩时，
 * 在对话流中插入紫色的记忆压缩信息气泡，展示摘要内容和 token 对比。
 * <p>
 * 职责：
 * <ul>
 *   <li>构建聊天界面的所有 UI 组件（侧边栏、标题栏、消息区域、输入区域）</li>
 *   <li>处理用户输入事件（发送消息、快捷键）</li>
 *   <li>管理消息气泡的展示和状态切换（思考中/空闲）</li>
 *   <li>支持 SSE 流式响应的逐 token 实时展示</li>
 *   <li>集成 {@link SessionSidebar}，支持多会话的创建、切换和删除</li>
 *   <li>会话切换时重新渲染历史消息到聊天区域</li>
 * </ul>
 * <p>
 * 通过 {@link AgentService} 与 Agent 层和会话管理层通信，不直接依赖 AgentLoop、SessionManager 等底层组件。
 */
public class ChatWindow {

    // ---- UI 组件 ----
    private final BorderPane root;
    private VBox messageContainer;
    private ScrollPane scrollPane;
    private TextArea inputField;
    private Button sendButton;
    private MessageBubble thinkingBubble;

    /** 会话列表侧边栏 */
    private SessionSidebar sessionSidebar;

    /** 当前正在流式输出的消息气泡（SSE 流式响应时使用） */
    private MessageBubble streamingBubble;

    // ---- Agent 服务 ----
    private final AgentService agentService;

    /** Agent 是否正在处理中 */
    private volatile boolean processing = false;

    public ChatWindow(AgentService agentService) {
        this.agentService = agentService;
        this.root = new BorderPane();
        buildUI();
        bindEvents();
        bindSidebarEvents();
        bindAgentCallback();
    }

    /**
     * 获取根布局节点，供 Scene 使用。
     */
    public BorderPane getRoot() {
        return root;
    }

    /**
     * 加载当前活跃会话 —— 应用启动时调用。
     * <p>
     * 如果当前会话有历史消息，则渲染历史消息到聊天区域；
     * 如果是空白会话，则显示欢迎消息。
     * 同时刷新侧边栏的会话列表。
     */
    public void loadCurrentSession() {
        // 刷新侧边栏
        refreshSidebar();

        // 加载当前会话的历史消息
        Session currentSession = agentService.getCurrentSession();
        if (currentSession != null && !currentSession.getMessages().isEmpty()) {
            renderSessionMessages(currentSession);
        } else {
            // 空白会话，显示欢迎消息
            showWelcomeMessage();
        }
    }

    /**
     * 显示 API Key 未配置的错误提示，并禁用输入。
     */
    public void showApiKeyError() {
        addMessage(MessageBubble.Type.ERROR,
                "❌ 未设置 OPENAI_API_KEY 环境变量！\n\n" +
                "请在终端中设置后重新启动：\n" +
                "export OPENAI_API_KEY=sk-xxxxx\n" +
                "export OPENAI_BASE_URL=https://api.openai.com/v1  # 可选\n" +
                "export OPENAI_MODEL=gpt-4o  # 可选");
        inputField.setDisable(true);
        sendButton.setDisable(true);
    }

    /**
     * 聚焦到输入框。
     */
    public void focusInput() {
        inputField.requestFocus();
    }

    // ==================== 私有方法 ====================

    /**
     * 显示欢迎消息（新会话时使用）。
     */
    private void showWelcomeMessage() {
        addMessage(MessageBubble.Type.AGENT,
                "你好！欢迎使用 Agent From Scratch 🖥️\n\n" +
                "我可以通过 Shell 命令帮你操作电脑，试试说：\n" +
                "• \"帮我看看当前目录有哪些文件\"\n" +
                "• \"查看系统磁盘使用情况\"\n" +
                "• \"创建一个 hello.txt 文件，内容为 Hello World\"");
    }

    /**
     * 构建 UI 布局。
     */
    private void buildUI() {
        root.setStyle("-fx-background-color: #FFFFFF;");

        // --- 左侧：会话列表侧边栏 ---
        sessionSidebar = new SessionSidebar();
        root.setLeft(sessionSidebar);

        // --- 顶部标题栏 ---
        Label titleLabel = new Label("🤖 Agent From Scratch");
        titleLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: #333;
                -fx-padding: 16 20 12 20;
                """);
        Label subtitleLabel = new Label("从零手搓 AI Agent —— 记忆管理");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999; -fx-padding: 0 20 12 20;");
        VBox header = new VBox(titleLabel, subtitleLabel);
        header.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 1 0;");
        root.setTop(header);

        // --- 中部消息展示区域 ---
        messageContainer = new VBox(8);
        messageContainer.setPadding(new Insets(16));
        messageContainer.setStyle("-fx-background-color: #FFFFFF;");

        scrollPane = new ScrollPane(messageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; -fx-border-width: 0;");

        // 自动滚动：当内容高度变化时滚动到底部
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            scrollPane.setVvalue(1.0);
        });

        root.setCenter(scrollPane);

        // --- 底部输入区域（多行编辑，Ctrl+Enter 发送，Enter 换行）---
        inputField = new TextArea();
        inputField.setPromptText("输入消息... (Ctrl+Enter 发送)");
        inputField.setWrapText(true);
        inputField.setPrefRowCount(3);
        inputField.setMaxHeight(120);
        inputField.getStyleClass().add("input-area");
        inputField.setStyle("""
                -fx-font-size: 14px;
                -fx-padding: 8 12;
                """);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        sendButton = new Button("发送");
        sendButton.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-text-fill: #FFFFFF;
                -fx-background-color: #007AFF;
                -fx-background-radius: 20;
                -fx-padding: 10 24;
                -fx-cursor: hand;
                -fx-background-insets: 0;
                -fx-effect: null;
                """);

        HBox inputBar = new HBox(10, inputField, sendButton);
        inputBar.setPadding(new Insets(12, 16, 16, 16));
        inputBar.setAlignment(Pos.CENTER);
        inputBar.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 1 0 0 0;");
        root.setBottom(inputBar);
    }

    /**
     * 绑定 UI 事件。
     */
    private void bindEvents() {
        sendButton.setOnAction(e -> handleSend());
        // Ctrl+Enter（macOS 下 Meta+Enter 也支持）发送消息，普通 Enter 换行
        inputField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isMetaDown())) {
                e.consume();
                handleSend();
            }
        });
    }

    /**
     * 绑定侧边栏事件 —— 新建会话、切换会话、删除会话。
     */
    private void bindSidebarEvents() {
        // 新建会话 → 创建新会话 → 清空聊天区域 → 显示欢迎消息 → 刷新侧边栏
        sessionSidebar.setOnNewSession(() -> {
            if (processing) return; // Agent 处理中不允许切换
            agentService.createSession();
            messageContainer.getChildren().clear();
            showWelcomeMessage();
            refreshSidebar();
            inputField.requestFocus();
        });

        // 切换会话 → 切换 Agent 上下文 → 重新渲染历史消息 → 刷新侧边栏高亮
        sessionSidebar.setOnSwitchSession(sessionId -> {
            if (processing) return; // Agent 处理中不允许切换
            Session target = agentService.switchSession(sessionId);
            if (target != null) {
                messageContainer.getChildren().clear();
                if (target.getMessages().isEmpty()) {
                    showWelcomeMessage();
                } else {
                    renderSessionMessages(target);
                }
                refreshSidebar();
                inputField.requestFocus();
            }
        });

        // 删除会话 → 删除并切换 → 重新渲染当前会话 → 刷新侧边栏
        sessionSidebar.setOnDeleteSession(sessionId -> {
            if (processing) return; // Agent 处理中不允许删除
            agentService.deleteSession(sessionId);
            // 删除后 AgentService 已自动切换到新的活跃会话
            Session current = agentService.getCurrentSession();
            messageContainer.getChildren().clear();
            if (current != null && !current.getMessages().isEmpty()) {
                renderSessionMessages(current);
            } else {
                showWelcomeMessage();
            }
            refreshSidebar();
            inputField.requestFocus();
        });
    }

    /**
     * 绑定 Agent 回调 —— 将 Agent 中间过程和流式输出更新到 UI，并同步刷新侧边栏。
     * <p>
     * 回调中的消息展示职责：
     * <ul>
     *   <li>{@code onToolCall} / {@code onToolResult} → 在聊天区域展示工具调用和结果气泡</li>
     *   <li>{@code onStreamStart} / {@code onStreamToken} / {@code onStreamComplete} → 流式展示 Agent 回复</li>
     *   <li>{@code onMemoryCompaction} → 展示上下文压缩信息气泡</li>
     *   <li>{@code onError} → 展示错误消息</li>
     * </ul>
     * <p>
     * 注意：消息到会话的持久化同步由 {@link AgentService#sendMessage} 在 Agent 执行完成后批量处理，
     * 回调中仅负责 UI 展示和侧边栏刷新，不直接操作 SessionManager。
     */
    private void bindAgentCallback() {
        agentService.setCallback(new AgentCallback() {
            @Override
            public void onToolCall(String toolName, String argsJson) {
                Platform.runLater(() ->
                        addMessage(MessageBubble.Type.TOOL_CALL, toolName + "(" + argsJson + ")")
                );
            }

            @Override
            public void onToolResult(String toolName, String result) {
                Platform.runLater(() ->
                        addMessage(MessageBubble.Type.TOOL_RESULT, result)
                );
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() ->
                        addMessage(MessageBubble.Type.ERROR, errorMessage)
                );
            }

            @Override
            public void onStreamStart() {
                Platform.runLater(() -> {
                    // 创建一个空的 Agent 消息气泡，用于后续逐 token 追加
                    streamingBubble = new MessageBubble(MessageBubble.Type.AGENT, "");
                    // 插入到"思考中"提示前面
                    if (thinkingBubble != null && messageContainer.getChildren().contains(thinkingBubble)) {
                        int idx = messageContainer.getChildren().indexOf(thinkingBubble);
                        messageContainer.getChildren().add(idx, streamingBubble);
                    } else {
                        messageContainer.getChildren().add(streamingBubble);
                    }
                });
            }

            @Override
            public void onStreamToken(String token) {
                Platform.runLater(() -> {
                    if (streamingBubble != null) {
                        streamingBubble.appendContent(token);
                    }
                });
            }

            @Override
            public void onStreamComplete(String fullContent) {
                Platform.runLater(() -> {
                    if (streamingBubble != null) {
                        streamingBubble.finalizeContent(fullContent);
                        streamingBubble = null;
                    }
                    // 流式输出完成后刷新侧边栏（会话标题和排序可能已变化）
                    refreshSidebar();
                });
            }

            @Override
            public void onMemoryCompaction(String summary, int tokensBefore, int tokensAfter, int removedCount) {
                Platform.runLater(() -> {
                    String info = String.format(
                            "上下文压缩: %d → %d tokens (节省 %d%%)\n移除 %d 条旧消息\n\n摘要:\n%s",
                            tokensBefore, tokensAfter,
                            (int) ((1.0 - (double) tokensAfter / tokensBefore) * 100),
                            removedCount, summary);
                    addMessage(MessageBubble.Type.MEMORY, info);
                });
            }
        });
    }

    /**
     * 处理发送消息。
     */
    private void handleSend() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || processing) {
            return;
        }

        // 1. 显示用户消息
        addMessage(MessageBubble.Type.USER, text);
        inputField.clear();

        // 2. 进入处理状态
        setProcessing(true);

        // 3. 通过 AgentService 异步调用 Agent
        //    流式响应的文本内容已通过 onStreamStart/onStreamToken/onStreamComplete 回调展示，
        //    这里的 onSuccess 仅用于结束处理状态
        agentService.sendMessage(text,
                // 成功回调 —— 流式文本已通过回调展示，这里只需结束处理状态并刷新侧边栏
                response -> Platform.runLater(() -> {
                    setProcessing(false);
                    // 发送消息后刷新侧边栏（标题和排序可能变化）
                    refreshSidebar();
                }),
                // 异常回调
                errorMsg -> Platform.runLater(() -> {
                    addMessage(MessageBubble.Type.ERROR, errorMsg);
                    setProcessing(false);
                    refreshSidebar();
                })
        );
    }

    /**
     * 渲染会话的历史消息到聊天区域。
     * <p>
     * 根据 {@link ChatMessage} 的 role 字段，将消息映射为对应类型的 {@link MessageBubble}：
     * <ul>
     *   <li>{@code user} → USER 气泡</li>
     *   <li>{@code assistant} → AGENT 气泡（仅渲染有文本内容的消息，跳过纯 tool_calls 消息）</li>
     *   <li>{@code tool} → TOOL_RESULT 气泡</li>
     *   <li>{@code system} → 跳过（不在 UI 中展示）</li>
     * </ul>
     *
     * @param session 要渲染的会话
     */
    private void renderSessionMessages(Session session) {
        for (ChatMessage msg : session.getMessages()) {
            switch (msg.getRole()) {
                case "user" -> addMessage(MessageBubble.Type.USER, msg.getContent());
                case "assistant" -> {
                    // assistant 消息可能只有 tool_calls 没有文本内容，跳过这类消息
                    if (msg.getContent() != null && !msg.getContent().isBlank()) {
                        addMessage(MessageBubble.Type.AGENT, msg.getContent());
                    }
                    // 如果有 tool_calls，渲染工具调用信息
                    if (msg.getToolCalls() != null) {
                        for (ChatMessage.ToolCall toolCall : msg.getToolCalls()) {
                            String callInfo = toolCall.getFunction().getName() + "(" + toolCall.getFunction().getArguments() + ")";
                            addMessage(MessageBubble.Type.TOOL_CALL, callInfo);
                        }
                    }
                }
                case "tool" -> addMessage(MessageBubble.Type.TOOL_RESULT, msg.getContent());
                // system 消息不在 UI 中展示
            }
        }
    }

    /**
     * 刷新侧边栏 —— 用最新的会话列表和活跃会话 ID 更新侧边栏显示。
     */
    private void refreshSidebar() {
        Session current = agentService.getCurrentSession();
        String activeId = current != null ? current.getId() : "";
        sessionSidebar.refresh(agentService.getAllSessionsSorted(), activeId);
    }

    /**
     * 设置处理状态：显示/隐藏"思考中"提示，启用/禁用输入控件。
     */
    private void setProcessing(boolean isProcessing) {
        this.processing = isProcessing;
        inputField.setDisable(isProcessing);
        sendButton.setDisable(isProcessing);

        if (isProcessing) {
            // 显示"思考中..."提示
            thinkingBubble = new MessageBubble(MessageBubble.Type.STATUS, "🤔 思考中...");
            messageContainer.getChildren().add(thinkingBubble);
        } else {
            // 移除"思考中..."提示
            if (thinkingBubble != null) {
                messageContainer.getChildren().remove(thinkingBubble);
                thinkingBubble = null;
            }
            // 重新聚焦到输入框
            inputField.requestFocus();
        }
    }

    /**
     * 向聊天区域添加一条消息气泡。
     */
    private void addMessage(MessageBubble.Type type, String content) {
        MessageBubble bubble = new MessageBubble(type, content);
        // 如果有"思考中"提示，插入到它前面
        if (thinkingBubble != null && messageContainer.getChildren().contains(thinkingBubble)) {
            int idx = messageContainer.getChildren().indexOf(thinkingBubble);
            messageContainer.getChildren().add(idx, bubble);
        } else {
            messageContainer.getChildren().add(bubble);
        }
    }
}
