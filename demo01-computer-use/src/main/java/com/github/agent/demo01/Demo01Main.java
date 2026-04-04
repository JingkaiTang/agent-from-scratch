package com.github.agent.demo01;

import com.github.agent.demo01.core.AgentLoop;
import com.github.agent.demo01.llm.LLMClient;
import com.github.agent.demo01.tool.ToolRegistry;
import com.github.agent.demo01.tool.impl.ExecTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Demo 01 入口 —— Computer Use 演示。
 * <p>
 * 仅凭一个 exec 工具，让 Agent 成为"电脑操作员"。
 * <p>
 * 运行方式:
 * <pre>
 * # 设置环境变量
 * export OPENAI_API_KEY=你的key
 * export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选，默认 OpenAI
 * export OPENAI_MODEL=gpt-4o                          # 可选，默认 gpt-4o
 * export AGENT_AUTO_CONFIRM=true                      # 可选，跳过命令确认
 *
 * # 运行
 * cd demo01-computer-use
 * mvn compile exec:java
 * </pre>
 */
public class Demo01Main {

    private static final Logger log = LoggerFactory.getLogger(Demo01Main.class);

    /**
     * Computer Use 场景专用 System Prompt。
     * 引导 LLM 合理使用 exec 工具来完成各种操作系统任务。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个能操作电脑的 AI 助手。你可以通过 `exec` 工具执行 Shell 命令来完成用户的任务。
            
            ## 你的能力
            通过 exec 工具，你可以执行任意 Shell 命令，包括但不限于：
            - **文件操作**: 查看文件(cat)、创建文件(echo > / tee)、编辑文件(sed)、删除文件(rm)、复制/移动(cp/mv)
            - **目录操作**: 列出目录(ls -la)、创建目录(mkdir -p)、查看目录树(find / tree)
            - **系统信息**: 操作系统信息(uname -a)、磁盘使用(df -h)、内存使用(free -h)、CPU信息
            - **进程管理**: 查看进程(ps aux)、查找进程(pgrep)
            - **网络操作**: 网络连通性(ping)、下载文件(curl/wget)、查看端口(netstat/lsof)
            - **开发工具**: 运行脚本(python/node/bash)、编译代码(javac/gcc)、包管理(pip/npm/brew)
            
            ## 执行规则
            1. 分析用户的请求，思考需要执行什么命令
            2. 如果任务需要多个步骤，按逻辑顺序依次执行命令
            3. 根据命令输出结果，判断是否需要进一步操作
            4. 最终整合所有信息，给用户一个清晰的回复
            
            ## 安全意识
            - 执行命令前先思考：这个命令是否安全？是否会造成不可逆的影响？
            - 对于删除、修改等操作，优先使用安全的方式（如先备份再修改）
            - 避免执行可能导致系统不稳定的命令
            - 如果不确定命令的影响，先用只读命令查看情况，再决定下一步
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

        // 3. 注册工具 —— 仅一个 exec 工具
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new ExecTool());

        log.info("已注册工具: {}", toolRegistry.all().stream().map(t -> t.name()).toList());

        // 提示自动确认模式状态
        String autoConfirm = System.getenv("AGENT_AUTO_CONFIRM");
        if ("true".equalsIgnoreCase(autoConfirm)) {
            log.info("⚡ 自动确认模式已开启（AGENT_AUTO_CONFIRM=true）");
        } else {
            log.info("🔒 安全模式：每次执行命令前需要确认（设置 AGENT_AUTO_CONFIRM=true 可跳过）");
        }

        // 4. 创建 Agent
        AgentLoop agent = new AgentLoop(llmClient, toolRegistry, SYSTEM_PROMPT, 10);

        // 5. 交互式循环
        System.out.println("\n🖥️  Computer Use Agent 已就绪！");
        System.out.println("💡 我可以通过 Shell 命令帮你操作电脑，试试说：");
        System.out.println("   - \"帮我看看当前目录有哪些文件\"");
        System.out.println("   - \"查看系统磁盘使用情况\"");
        System.out.println("   - \"创建一个 hello.txt 文件，内容为 Hello World\"");
        System.out.println("输入 quit 退出\n");

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
