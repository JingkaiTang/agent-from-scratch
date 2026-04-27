# Demo 01 - Computer Use（让 Agent 操作电脑）

在 demo00 基础 Agent 的基础上，将工具从"计算器/天气查询"替换为 **Shell 命令执行工具**，让 Agent 具备操作电脑的能力。

## 学习目标

| 概念 | 说明 |
|------|------|
| **Shell 执行工具** | 如何将 `Runtime.exec()` 封装为 Agent 可调用的工具 |
| **安全确认机制** | 执行命令前交互式确认，或通过环境变量跳过确认 |
| **多轮对话上下文** | AgentLoop 保持 conversationHistory 跨多次 run() 调用 |
| **日志持久化** | 日志输出到 `~/.afs/logs/` 目录，按日期滚动 |

## 与 demo00 的差异

| 特性 | demo00 | demo01 |
|------|--------|--------|
| 工具集 | 计算器/时间/天气（模拟） | Shell 命令执行（真实） |
| 交互方式 | 命令行 REPL | 命令行 REPL |
| 安全机制 | 无 | 命令执行前确认提示 |
| 多轮对话 | 单次任务 | 保持上下文连续对话 |

## 项目结构

```
demo01-computer-use/
├── pom.xml
└── src/main/java/com/github/agent/demo01/
    ├── Demo01Main.java          # 入口（命令行 REPL）
    ├── core/
    │   └── AgentLoop.java       # Agent 核心循环（多轮上下文）
    ├── llm/
    │   └── LLMClient.java       # LLM API 调用封装
    ├── model/
    │   └── ChatMessage.java     # 消息模型
    └── tool/
        ├── Tool.java            # 工具接口
        ├── ToolRegistry.java    # 工具注册表
        └── impl/
            └── ExecTool.java    # ⭐ Shell 命令执行工具
```

## 核心类说明

| 类 | 职责 |
|----|------|
| `ExecTool` | ⭐ 封装 `ProcessBuilder` 执行 Shell 命令，支持超时控制和输出捕获 |
| `AgentLoop` | 复用 demo00 的 ReAct 循环，新增 conversationHistory 持久化 |
| `Demo01Main` | 命令行 REPL 入口，支持多轮对话 |

## 快速开始

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选
export AGENT_AUTO_CONFIRM=true                      # 可选，跳过命令执行确认

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo01-computer-use
mvn exec:java
```

## 演示效果

```
You > 帮我看看当前目录有哪些文件

🔧 调用工具: exec({"command": "ls -la"})
📋 工具结果: total 48 ...

Agent > 当前目录下有以下文件和目录：...

You > 创建一个 hello.txt，内容为 Hello World

🔧 调用工具: exec({"command": "echo 'Hello World' > hello.txt"})
📋 工具结果: (执行成功)

Agent > 已创建 hello.txt 文件，内容为 "Hello World"。
```
