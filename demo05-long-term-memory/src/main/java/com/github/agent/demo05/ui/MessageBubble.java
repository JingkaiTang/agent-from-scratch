package com.github.agent.demo05.ui;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * 消息气泡组件 —— 用于在聊天区域渲染不同类型的消息。
 * <p>
 * 支持七种消息类型：
 * - 用户消息（右对齐，蓝色背景，白色文字）
 * - Agent 回复（左对齐，灰色背景）
 * - 工具调用信息（左对齐，橙色边框）
 * - 工具执行结果（左对齐，绿色边框）
 * - 错误信息（左对齐，红色边框）
 * - 状态提示（居中，灰色斜体）
 * - ⭐ 记忆压缩（居中，紫色边框，展示上下文压缩信息）
 * <p>
 * 特性：
 * - 消息文字可选择复制（使用只读 TextArea）
 * - 每条消息带有"复制"按钮，可一键复制完整文本
 * - 通过 CSS 样式类控制 TextArea 内部文字颜色（解决 -fx-text-fill 对 TextArea 不生效的问题）
 */
public class MessageBubble extends HBox {

    public enum Type {
        USER,           // 用户消息
        AGENT,          // Agent 回复
        TOOL_CALL,      // 工具调用
        TOOL_RESULT,    // 工具结果
        ERROR,          // 错误信息
        STATUS,         // 状态提示（如"思考中..."）
        MEMORY          // ⭐ 记忆压缩（上下文压缩通知）
    }

    /** 消息内容区域（用于流式更新） */
    private TextArea messageArea;
    /** 当前完整内容（用于流式追加和复制） */
    private String currentContent;
    /** 复制按钮（流式完成后更新复制内容） */
    private Button copyButton;
    /** 消息类型 */
    private Type type;

    public MessageBubble(Type type, String content) {
        super();
        this.type = type;
        this.currentContent = content;
        setPadding(new Insets(4, 12, 4, 12));

        VBox bubble = new VBox(4);
        bubble.setPadding(new Insets(10, 14, 10, 14));
        bubble.setMaxWidth(520);

        // 角色标签
        Label roleLabel = new Label(getRoleText(type));
        roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #555;");

        // 消息内容 —— 使用只读 TextArea，支持文字选择复制
        messageArea = new TextArea(content);
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setMaxWidth(490);

        // 根据内容行数自适应高度
        int lineCount = content.split("\n").length;
        int estimatedHeight = Math.max(36, Math.min(lineCount * 22 + 16, 300));
        messageArea.setPrefHeight(estimatedHeight);
        messageArea.setMinHeight(Region.USE_PREF_SIZE);
        messageArea.setMaxHeight(300);

        // 根据类型设置气泡样式、TextArea CSS 样式类和对齐方式
        switch (type) {
            case USER -> {
                bubble.setStyle("""
                        -fx-background-color: #007AFF;
                        -fx-background-radius: 16 16 4 16;
                        """);
                messageArea.getStyleClass().add("msg-user");
                messageArea.setStyle("-fx-font-size: 14px;");
                roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: rgba(255,255,255,0.8);");
                setAlignment(Pos.CENTER_RIGHT);
            }
            case AGENT -> {
                bubble.setStyle("""
                        -fx-background-color: #E9ECEF;
                        -fx-background-radius: 16 16 16 4;
                        """);
                messageArea.getStyleClass().add("msg-agent");
                messageArea.setStyle("-fx-font-size: 14px;");
                setAlignment(Pos.CENTER_LEFT);
            }
            case TOOL_CALL -> {
                bubble.setStyle("""
                        -fx-background-color: #FFF3CD;
                        -fx-background-radius: 12;
                        -fx-border-color: #FFCA2C;
                        -fx-border-radius: 12;
                        -fx-border-width: 1;
                        """);
                messageArea.getStyleClass().add("msg-tool-call");
                messageArea.setStyle("-fx-font-size: 13px;");
                roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #664D03;");
                setAlignment(Pos.CENTER_LEFT);
            }
            case TOOL_RESULT -> {
                bubble.setStyle("""
                        -fx-background-color: #D1E7DD;
                        -fx-background-radius: 12;
                        -fx-border-color: #479F76;
                        -fx-border-radius: 12;
                        -fx-border-width: 1;
                        """);
                messageArea.getStyleClass().add("msg-tool-result");
                messageArea.setStyle("-fx-font-size: 13px;");
                roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0A3622;");
                setAlignment(Pos.CENTER_LEFT);
            }
            case ERROR -> {
                bubble.setStyle("""
                        -fx-background-color: #F8D7DA;
                        -fx-background-radius: 12;
                        -fx-border-color: #DC3545;
                        -fx-border-radius: 12;
                        -fx-border-width: 1;
                        """);
                messageArea.getStyleClass().add("msg-error");
                messageArea.setStyle("-fx-font-size: 13px;");
                roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #58151C;");
                setAlignment(Pos.CENTER_LEFT);
            }
            case STATUS -> {
                bubble.setStyle("""
                        -fx-background-color: #F0F0F0;
                        -fx-background-radius: 12;
                        """);
                messageArea.getStyleClass().add("msg-status");
                messageArea.setStyle("-fx-font-size: 13px;");
                roleLabel.setVisible(false);
                roleLabel.setManaged(false);
                setAlignment(Pos.CENTER);
            }
            case MEMORY -> {
                bubble.setStyle("""
                        -fx-background-color: #F3E8FF;
                        -fx-background-radius: 12;
                        -fx-border-color: #9333EA;
                        -fx-border-radius: 12;
                        -fx-border-width: 1;
                        """);
                bubble.setMaxWidth(600);
                messageArea.setMaxWidth(570);
                messageArea.getStyleClass().add("msg-memory");
                messageArea.setStyle("-fx-font-size: 12px;");
                roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #6B21A8;");
                setAlignment(Pos.CENTER);
            }
        }

        // 构建顶部栏：角色标签 + 弹性空间 + 复制按钮
        if (type != Type.STATUS && type != Type.MEMORY) {
            copyButton = createCopyButton(content, type);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox topBar = new HBox(4, roleLabel, spacer, copyButton);
            topBar.setAlignment(Pos.CENTER_LEFT);
            bubble.getChildren().addAll(topBar, messageArea);
        } else {
            bubble.getChildren().addAll(roleLabel, messageArea);
        }

        getChildren().add(bubble);
    }

    /**
     * 创建复制按钮，点击后将消息内容复制到系统剪贴板，并短暂显示"✅ 已复制"反馈。
     */
    private Button createCopyButton(String content, Type type) {
        Button copyBtn = new Button("📋 复制");

        // 根据消息类型调整按钮文字颜色
        String textColor = switch (type) {
            case USER -> "rgba(255,255,255,0.7)";
            case AGENT -> "#666";
            case TOOL_CALL -> "#997404";
            case TOOL_RESULT -> "#146C43";
            case ERROR -> "#842029";
            default -> "#888";
        };

        copyBtn.setStyle(String.format("""
                -fx-font-size: 11px;
                -fx-text-fill: %s;
                -fx-background-color: transparent;
                -fx-border-width: 0;
                -fx-cursor: hand;
                -fx-padding: 2 6;
                """, textColor));

        copyBtn.setOnAction(e -> {
            // 复制到系统剪贴板
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(content);
            Clipboard.getSystemClipboard().setContent(clipboardContent);

            // 短暂显示"已复制"反馈
            String originalText = copyBtn.getText();
            copyBtn.setText("✅ 已复制");
            copyBtn.setDisable(true);

            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
            pause.setOnFinished(ev -> {
                copyBtn.setText(originalText);
                copyBtn.setDisable(false);
            });
            pause.play();
        });

        return copyBtn;
    }

    // ========== 流式更新支持 ==========

    /**
     * 追加文本内容（用于 SSE 流式输出，逐 token 追加）。
     * <p>
     * 必须在 JavaFX Application Thread 上调用。
     *
     * @param token 要追加的文本片段
     */
    public void appendContent(String token) {
        currentContent += token;
        messageArea.setText(currentContent);
        // 动态调整高度
        int lineCount = currentContent.split("\n").length;
        int estimatedHeight = Math.max(36, Math.min(lineCount * 22 + 16, 300));
        messageArea.setPrefHeight(estimatedHeight);
        // 滚动到底部
        messageArea.positionCaret(currentContent.length());
    }

    /**
     * 流式输出完成后，更新复制按钮的内容为完整文本。
     * <p>
     * 必须在 JavaFX Application Thread 上调用。
     *
     * @param fullContent 完整的消息文本
     */
    public void finalizeContent(String fullContent) {
        this.currentContent = fullContent;
        messageArea.setText(fullContent);
        // 最终调整高度
        int lineCount = fullContent.split("\n").length;
        int estimatedHeight = Math.max(36, Math.min(lineCount * 22 + 16, 300));
        messageArea.setPrefHeight(estimatedHeight);
        // 更新复制按钮绑定的内容
        if (copyButton != null) {
            copyButton.setOnAction(e -> {
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(fullContent);
                Clipboard.getSystemClipboard().setContent(clipboardContent);

                String originalText = copyButton.getText();
                copyButton.setText("✅ 已复制");
                copyButton.setDisable(true);

                PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                pause.setOnFinished(ev -> {
                    copyButton.setText(originalText);
                    copyButton.setDisable(false);
                });
                pause.play();
            });
        }
    }

    /**
     * 获取当前消息的完整内容。
     */
    public String getCurrentContent() {
        return currentContent;
    }

    private String getRoleText(Type type) {
        return switch (type) {
            case USER -> "You";
            case AGENT -> "🤖 Agent";
            case TOOL_CALL -> "🔧 工具调用";
            case TOOL_RESULT -> "📋 工具结果";
            case ERROR -> "❌ 错误";
            case MEMORY -> "🧠 记忆压缩";
            case STATUS -> "";
        };
    }
}
