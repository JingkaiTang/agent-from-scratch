package com.github.agent.demo05;

import com.github.agent.demo05.agent.AgentConfig;
import com.github.agent.demo05.agent.AgentService;
import com.github.agent.demo05.ui.ChatWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo05 入口 —— JavaFX Application 启动类。
 * <p>
 * 在 Demo04（短期记忆管理）基础上新增跨会话长期记忆功能：
 * 对话结束后自动提取关键信息写入 memories.md，新对话时自动检索相关知识注入上下文。
 * <ul>
 *   <li>Agent 配置与服务 → {@link AgentConfig} + {@link AgentService}</li>
 *   <li>GUI 聊天窗口 → {@link ChatWindow}</li>
 * </ul>
 * <p>
 * 运行方式:
 * <pre>
 * export OPENAI_API_KEY=你的key
 * export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
 * export OPENAI_MODEL=gpt-4o                          # 可选
 *
 * cd demo05-long-term-memory
 * mvn compile exec:java
 * </pre>
 */
public class Demo05App extends Application {

    private static final Logger log = LoggerFactory.getLogger(Demo05App.class);

    private AgentService agentService;

    @Override
    public void start(Stage primaryStage) {
        // ========== 1. 初始化 Agent 层 ==========
        AgentConfig config = new AgentConfig();
        agentService = new AgentService(config);
        boolean agentReady = agentService.initialize();

        // ========== 2. 构建 GUI 层 ==========
        ChatWindow chatWindow = new ChatWindow(agentService);

        // 根据 Agent 初始化结果加载会话或显示错误
        if (agentReady) {
            chatWindow.loadCurrentSession();
        } else {
            chatWindow.showApiKeyError();
        }

        // ========== 3. 配置窗口并显示 ==========
        Scene scene = new Scene(chatWindow.getRoot(), 1060, 650);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        primaryStage.setTitle("Agent From Scratch - 长期记忆");
        // 设置窗口图标（多尺寸适配）
        primaryStage.getIcons().addAll(
                new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.png")),
                new javafx.scene.image.Image(getClass().getResourceAsStream("/icon_128.png")),
                new javafx.scene.image.Image(getClass().getResourceAsStream("/icon_64.png")),
                new javafx.scene.image.Image(getClass().getResourceAsStream("/icon_32.png"))
        );
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(400);
        primaryStage.show();

        // 聚焦到输入框
        chatWindow.focusInput();

        // 窗口关闭时清理资源
        primaryStage.setOnCloseRequest(e -> {
            agentService.shutdown();
            Platform.exit();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
