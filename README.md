# Agent From Scratch

从零手搓 AI Agent —— 用 Java 学习和验证 Agent 核心原理。

## 项目结构

```
agent-from-scratch/
├── pom.xml                          # Parent POM（多模块管理）
├── demo00-basic-agent/              # Demo 00：最基础的 Agent
│   ├── pom.xml
│   └── src/main/java/com/github/agent/demo00/
│       ├── Demo00Main.java          # 入口
│       ├── core/
│       │   └── AgentLoop.java       # ⭐ Agent 核心循环（ReAct）
│       ├── llm/
│       │   └── LLMClient.java       # LLM API 调用封装
│       ├── model/
│       │   └── ChatMessage.java     # 消息模型
│       └── tool/
│           ├── Tool.java            # 工具接口
│           ├── ToolRegistry.java    # 工具注册表
│           └── impl/
│               ├── CalculatorTool.java   # 计算器
│               ├── CurrentTimeTool.java  # 当前时间
│               └── WeatherTool.java      # 天气查询（模拟）
├── demo01-computer-use/             # Demo 01：让 Agent 操作电脑
│   ├── pom.xml
│   └── src/main/java/com/github/agent/demo01/
│       ├── Demo01Main.java          # 入口
│       ├── core/
│       │   └── AgentLoop.java       # Agent 核心循环（复用）
│       ├── llm/
│       │   └── LLMClient.java       # LLM API 调用封装（复用）
│       ├── model/
│       │   └── ChatMessage.java     # 消息模型（复用）
│       └── tool/
│           ├── Tool.java            # 工具接口（复用）
│           ├── ToolRegistry.java    # 工具注册表（复用）
│           └── impl/
│               └── ExecTool.java    # ⭐ Shell 命令执行工具
└── (后续模块...)
```

## 快速开始

### 环境要求

- Java 25+
- Maven 3.8+
- 一个 OpenAI 兼容的 API Key

### 运行 Demo 00

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选，支持 DeepSeek/通义等兼容 API
export OPENAI_MODEL=gpt-4o                          # 可选，默认 gpt-4o

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo00-basic-agent
mvn exec:java
```

### 运行 Demo 01 — Computer Use

```bash
# 设置环境变量（同上）
export OPENAI_API_KEY=sk-xxxxx
export AGENT_AUTO_CONFIRM=true   # 可选，跳过命令执行确认

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo01-computer-use
mvn exec:java
```

### 演示效果

```
You > 现在几点了？顺便帮我查一下上海天气，如果下雨的话算一下 365 * 24 是多少小时

🔧 调用工具: get_current_time()
📋 工具结果: 当前时间: 2026年03月20日 15:03:28 (星期五)

🔧 调用工具: get_weather({"city": "上海"})
📋 工具结果: 上海天气: 多云转小雨，气温 18°C...

🔧 调用工具: calculator({"expression": "365 * 24"})
📋 工具结果: 8760

Agent > 现在是 2026年3月20日 15:03，上海今天多云转小雨...365天共有 8760 小时。
```

## 学习路线（计划中的模块）

| 模块 | 主题 | 核心概念 |
|------|------|---------|
| **demo00** ✅ | Basic Agent | ReAct 循环、Tool Use、LLM 调用 |
| **demo01** ✅ | Computer Use | Shell 命令执行、安全机制、Agent 操作电脑 |
| demo02 | Planning | Plan-and-Execute、任务分解 |
| demo03 | Reflection | 自我反思、质量评估、迭代改进 |
| demo04 | Multi-Agent | 多智能体协作、消息传递 |
| demo05 | RAG Agent | 检索增强生成、知识库集成 |
| demo06 | Streaming | 流式输出、实时反馈 |

## 技术栈

- **HTTP**: OkHttp 4
- **JSON**: Jackson
- **日志**: SLF4J + Logback
- **API**: OpenAI Chat Completion（兼容格式）
- **无框架依赖** —— 纯手搓，看清每一行代码在干什么
