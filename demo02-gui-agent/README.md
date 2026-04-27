# Demo 02 - GUI Agent（图形界面 Agent）

在 demo01 命令行 Agent 的基础上，用 **JavaFX** 构建图形化聊天界面，并引入 **SSE 流式响应**实现逐 token 输出效果。

## 学习目标

| 概念 | 说明 |
|------|------|
| **JavaFX GUI** | 用 JavaFX 构建聊天界面，包含消息气泡、输入框、发送按钮 |
| **SSE 流式响应** | 通过 `stream=true` 调用 LLM，逐 token 推送到 GUI |
| **回调机制** | `AgentCallback` 接口解耦 Agent 核心与 GUI 层 |
| **异步架构** | `AgentService` 封装后台线程池，避免 UI 阻塞 |
| **分层设计** | GUI（ui）→ 服务层（agent）→ 核心层（core）三层分离 |

## 架构设计

```
┌─────────────────────────────────────────────┐
│              GUI 层 (ui)                     │
│    ┌────────────────────────────────┐       │
│    │         ChatWindow             │       │
│    │  · 消息气泡区（可滚动）         │       │
│    │  · 流式输出实时展示             │       │
│    │  · 输入框 + 发送按钮            │       │
│    │  · "思考中..." 状态提示         │       │
│    └──────────┬─────────────────────┘       │
│               │                              │
└───────────────┼──────────────────────────────┘
                │  发送消息/接收回调
                ▼
┌─────────────────────────────────────────────┐
│            服务层 (agent)                     │
│    ┌────────────────────────────────┐       │
│    │        AgentService            │       │
│    │  · 后台线程池（异步执行）       │       │
│    │  · AgentCallback 回调通知      │       │
│    └──────────┬─────────────────────┘       │
│               │                              │
└───────────────┼──────────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────────┐
│            核心层 (core)                     │
│    ┌──────────┐     ┌──────────────┐       │
│    │AgentLoop │────►│  LLMClient   │       │
│    │ · ReAct  │     │ · chat()     │       │
│    │ · 工具   │     │ · chatStream │       │
│    └──────────┘     └──────────────┘       │
└─────────────────────────────────────────────┘
```

## 与 demo01 的差异

| 特性 | demo01 | demo02 |
|------|--------|--------|
| 交互方式 | 命令行 REPL | JavaFX 图形界面 |
| LLM 调用 | 同步（chat） | SSE 流式（chatStream） |
| 输出方式 | 一次性打印 | 逐 token 实时展示 |
| 架构 | 单线程直调 | 异步 + 回调 + 服务层 |
| 消息展示 | 纯文本 | 彩色气泡（用户/Agent/工具/错误） |

## 项目结构

```
demo02-gui-agent/
├── pom.xml
└── src/main/java/com/github/agent/demo02/
    ├── Demo02App.java              # JavaFX Application 入口
    ├── Launcher.java               # 启动器（绕过 JavaFX 模块检查）
    ├── agent/
    │   ├── AgentConfig.java        # Agent 配置（环境变量 + 系统提示词）
    │   └── AgentService.java       # ⭐ 服务层（异步调用 + 回调）
    ├── core/
    │   ├── AgentCallback.java      # ⭐ 回调接口（工具/流式/错误通知）
    │   └── AgentLoop.java          # Agent 核心循环（支持 SSE 流式）
    ├── llm/
    │   └── LLMClient.java          # ⭐ LLM 客户端（新增 chatStream 方法）
    ├── model/
    │   └── ChatMessage.java        # 消息模型
    ├── tool/
    │   ├── Tool.java               # 工具接口
    │   ├── ToolRegistry.java       # 工具注册表
    │   └── impl/
    │       └── ExecTool.java       # Shell 命令执行工具
    └── ui/
        ├── ChatWindow.java         # ⭐ 聊天窗口（消息展示 + 输入）
        └── MessageBubble.java      # ⭐ 消息气泡组件（6 种类型）
```

## 核心类说明

### ui 包（⭐ 本 Demo 新增）

| 类 | 职责 |
|----|------|
| `ChatWindow` | 聊天窗口主组件——消息区域、输入框、发送按钮，处理流式输出展示 |
| `MessageBubble` | 消息气泡——支持 USER/AGENT/TOOL_CALL/TOOL_RESULT/ERROR/STATUS 六种类型 |

### 新增/改造的类

| 类 | 说明 |
|----|------|
| `AgentCallback` | ⭐ 回调接口——onToolCall/onToolResult/onStreamStart/onStreamToken/onStreamComplete/onError |
| `AgentService` | ⭐ 服务层——封装 AgentLoop 的异步调用，通过回调通知 GUI |
| `LLMClient` | ⭐ 新增 `chatStream()` 方法——SSE 流式调用，逐 token 回调 |
| `AgentLoop` | 改造为使用 chatStream，支持流式 token 回调 |

## 快速开始

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo02-gui-agent
mvn exec:java
```

## 消息气泡类型

| 类型 | 颜色 | 说明 |
|------|------|------|
| USER | 蓝色（右对齐） | 用户发送的消息 |
| AGENT | 灰色（左对齐） | Agent 的回复（支持流式逐 token 展示） |
| TOOL_CALL | 橙色边框 | 工具调用信息 |
| TOOL_RESULT | 绿色边框 | 工具执行结果 |
| ERROR | 红色边框 | 错误信息 |
| STATUS | 灰色居中 | 状态提示（如"思考中..."） |
