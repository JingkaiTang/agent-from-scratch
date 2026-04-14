package com.github.agent.demo05.agent;

/**
 * Agent 配置 —— 集中管理 Agent 的系统提示词和运行参数。
 * <p>
 * LLM 连接信息从环境变量中读取，记忆管理配置由 {@link com.github.agent.demo05.config.AppConfigLoader} 从
 * {@code ~/.afs/config.json} 加载。
 */
public class AgentConfig {

    /** 系统提示词 —— 定义 Agent 的角色和能力 */
    public static final String SYSTEM_PROMPT = """
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

            ## 长期记忆
            你拥有一个跨会话的长期记忆系统。
            - 在每次对话开始时，系统会自动检索与你的问题相关的历史知识并注入上下文
            - 如果上下文中包含"从长期记忆中检索到的相关信息"标记的内容，请优先参考这些信息
            - 你不需要主动管理长期记忆，系统会自动在对话结束后提取重要信息
            """;

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxSteps;

    public AgentConfig() {
        this.apiKey = getEnv("OPENAI_API_KEY", null);
        this.baseUrl = getEnv("OPENAI_BASE_URL", "https://api.openai.com/v1");
        this.model = getEnv("OPENAI_MODEL", "gpt-4o");
        this.maxSteps = Integer.parseInt(getEnv("AGENT_MAX_STEPS", "20"));
    }

    /**
     * 检查 API Key 是否已配置。
     */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
