package com.github.agent.demo00;

import com.github.agent.demo00.core.AgentLoop;
import com.github.agent.demo00.llm.LLMClient;
import com.github.agent.demo00.tool.ToolRegistry;
import com.github.agent.demo00.tool.impl.CalculatorTool;
import com.github.agent.demo00.tool.impl.CurrentTimeTool;
import com.github.agent.demo00.tool.impl.WeatherTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Demo 00 入口 —— 最基础的 Agent 演示。
 * <p>
 * 运行方式:
 * <pre>
 * # 设置环境变量
 * export OPENAI_API_KEY=你的key
 * export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选，默认 OpenAI
 * export OPENAI_MODEL=gpt-4o                          # 可选，默认 gpt-4o
 *
 * # 运行
 * cd demo00-basic-agent
 * mvn compile exec:java
 * </pre>
 */
public class Demo00Main {

    private static final Logger log = LoggerFactory.getLogger(Demo00Main.class);

    private static final String SYSTEM_PROMPT = """
            你是一个有帮助的 AI 助手。你可以使用提供的工具来帮助用户完成任务。
            
            执行规则:
            1. 分析用户的请求，判断需要使用哪些工具
            2. 如果需要多个工具，按逻辑顺序依次调用
            3. 根据工具返回的结果，整合信息回复用户
            4. 如果不需要工具就能回答，直接回答即可
            """;

    public static void main(String[] args) {
        // 1. 读取配置
        String apiKey = getEnv("OPENAI_API_KEY", null);
        String baseUrl = getEnv("OPENAI_BASE_URL", "https://api.openai.com/v1");
        String model = getEnv("OPENAI_MODEL", "gpt-4o");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("❌ 请设置环境变量 OPENAI_API_KEY");
            System.err.println("   export OPENAI_API_KEY=sk-xxxxx");
            System.exit(1);
        }

        log.info("模型: {} @ {}", model, baseUrl);

        // 2. 初始化 LLM 客户端
        LLMClient llmClient = new LLMClient(apiKey, baseUrl, model);

        // 3. 注册工具
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new CalculatorTool());
        toolRegistry.register(new CurrentTimeTool());
        toolRegistry.register(new WeatherTool());

        log.info("已注册工具: {}", toolRegistry.all().stream().map(t -> t.name()).toList());

        // 4. 创建 Agent
        AgentLoop agent = new AgentLoop(llmClient, toolRegistry, SYSTEM_PROMPT, 10);

        // 5. 交互式循环
        System.out.println("\n🤖 Agent 已就绪！输入任务开始对话（输入 quit 退出）\n");
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("You > ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                System.out.println("再见！");
                break;
            }

            String response = agent.run(input);
            System.out.println("\nAgent > " + response + "\n");
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
