package com.github.agent.demo02.agent;

import com.github.agent.demo02.core.AgentCallback;
import com.github.agent.demo02.core.AgentLoop;
import com.github.agent.demo02.llm.LLMClient;
import com.github.agent.demo02.tool.ToolRegistry;
import com.github.agent.demo02.tool.impl.ExecTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Agent 服务层 —— 封装 Agent 的初始化、生命周期管理和异步调用。
 * <p>
 * 职责：
 * <ul>
 *   <li>根据 {@link AgentConfig} 初始化 LLMClient、ToolRegistry、AgentLoop</li>
 *   <li>提供异步的 {@link #sendMessage(String, Consumer, Consumer)} 方法供 GUI 调用</li>
 *   <li>管理后台线程池的生命周期</li>
 * </ul>
 * <p>
 * 与 GUI 完全解耦，仅通过回调接口和 Consumer 函数式接口进行通信。
 */
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentConfig config;
    private AgentLoop agentLoop;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "agent-worker");
        t.setDaemon(true);
        return t;
    });

    public AgentService(AgentConfig config) {
        this.config = config;
    }

    /**
     * 初始化 Agent 组件：创建 LLMClient、注册工具、创建 AgentLoop。
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

    /**
     * 异步发送消息给 Agent。
     *
     * @param message   用户消息
     * @param onSuccess 成功回调（接收 Agent 最终回复）
     * @param onError   异常回调（接收错误信息）
     */
    public void sendMessage(String message, Consumer<String> onSuccess, Consumer<String> onError) {
        executor.submit(() -> {
            try {
                String response = agentLoop.run(message);
                onSuccess.accept(response);
            } catch (Exception e) {
                log.error("Agent 调用异常: {}", e.getMessage(), e);
                onError.accept("Agent 调用异常: " + e.getMessage());
            }
        });
    }

    /**
     * 关闭 Agent 服务，释放线程池资源。
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
