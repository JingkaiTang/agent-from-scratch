package com.github.agent.demo02.ui;

import com.github.agent.demo02.agent.AgentService;
import com.github.agent.demo02.core.AgentCallback;
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
 * 职责：
 * <ul>
 *   <li>构建聊天界面的所有 UI 组件（标题栏、消息区域、输入区域）</li>
 *   <li>处理用户输入事件（发送消息、快捷键）</li>
 *   <li>管理消息气泡的展示和状态切换（思考中/空闲）</li>
 *   <li>支持 SSE 流式响应的逐 token 实时展示</li>
 * </ul>
 * <p>
 * 通过 {@link AgentService} 与 Agent 层通信，不直接依赖 AgentLoop、LLMClient 等底层组件。
 */
public class ChatWindow {

    // ---- UI 组件 ----
    private final BorderPane root;
    private VBox messageContainer;
    private ScrollPane scrollPane;
    private TextArea inputField;
    private Button sendButton;
    private MessageBubble thinkingBubble;

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
        bindAgentCallback();
    }

    /**
     * 获取根布局节点，供 Scene 使用。
     */
    public BorderPane getRoot() {
        return root;
    }

    /**
     * 初始化完成后显示欢迎消息。
     */
    public void showWelcomeMessage() {
        addMessage(MessageBubble.Type.AGENT,
                "你好！欢迎使用 Agent From Scratch 🖥️\n\n" +
                "我可以通过 Shell 命令帮你操作电脑，试试说：\n" +
                "• \"帮我看看当前目录有哪些文件\"\n" +
                "• \"查看系统磁盘使用情况\"\n" +
                "• \"创建一个 hello.txt 文件，内容为 Hello World\"");
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
     * 构建 UI 布局。
     */
    private void buildUI() {
        root.setStyle("-fx-background-color: #FFFFFF;");

        // --- 顶部标题栏 ---
        Label titleLabel = new Label("🤖 Agent From Scratch");
        titleLabel.setStyle("""
                -fx-font-size: 20px;
                -fx-font-weight: bold;
                -fx-text-fill: #333;
                -fx-padding: 16 20 12 20;
                """);
        Label subtitleLabel = new Label("从零手搓 AI Agent —— GUI 聊天界面");
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
                -fx-text-fill: white;
                -fx-background-color: #007AFF;
                -fx-background-radius: 20;
                -fx-padding: 10 24;
                -fx-cursor: hand;
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
     * 绑定 Agent 回调 —— 将 Agent 中间过程和流式输出更新到 UI。
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
                // 成功回调 —— 流式文本已通过回调展示，这里只需结束处理状态
                response -> Platform.runLater(() -> {
                    setProcessing(false);
                }),
                // 异常回调
                errorMsg -> Platform.runLater(() -> {
                    addMessage(MessageBubble.Type.ERROR, errorMsg);
                    setProcessing(false);
                })
        );
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
