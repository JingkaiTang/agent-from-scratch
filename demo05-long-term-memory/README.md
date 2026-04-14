# Demo 03 - Session Management（会话管理）

在 demo02 GUI Agent 的基础上，增加**多会话管理**能力——支持创建、切换、删除多个独立对话，并将会话数据持久化到本地文件系统。

## 学习目标

通过本 Demo，你将学习和理解：

| 概念 | 说明 |
|------|------|
| **会话建模** | 如何将"对话"抽象为 `Session` 实体，包含 ID、标题、时间戳、消息列表 |
| **持久化存储** | 不依赖数据库，用 JSON 文件实现简单的本地持久化（每个会话一个文件） |
| **多会话管理** | SessionManager 如何协调内存状态与持久化存储，处理创建/切换/删除的边界情况 |
| **分层解耦** | 会话管理层（session）如何与 Agent 核心循环（core）、GUI 层（ui）解耦集成 |
| **GUI 扩展** | 在现有聊天界面上扩展侧边栏，实现会话列表的交互 |

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                      GUI 层 (ui)                         │
│  ┌──────────────┐    ┌──────────────────────────────┐   │
│  │SessionSidebar│◄──►│        ChatWindow             │   │
│  │ · 会话列表    │    │ · 消息展示区                   │   │
│  │ · 新建按钮    │    │ · 输入区域                     │   │
│  │ · 右键删除    │    │ · 流式输出                     │   │
│  └──────┬───────┘    └──────────┬───────────────────┘   │
│         │                       │                        │
└─────────┼───────────────────────┼────────────────────────┘
          │  点击/切换/删除        │  发送消息/接收回复
          ▼                       ▼
┌─────────────────────────────────────────────────────────┐
│                  服务层 (agent)                           │
│              ┌──────────────┐                            │
│              │ AgentService │                            │
│              │ · 异步调用    │                            │
│              │ · 会话操作    │                            │
│              └──┬────────┬──┘                            │
│                 │        │                               │
└─────────────────┼────────┼───────────────────────────────┘
                  │        │
        ┌─────────┘        └─────────┐
        ▼                            ▼
┌───────────────┐          ┌──────────────────┐
│   core 层      │          │   session 层      │
│ ┌───────────┐ │          │ ┌──────────────┐ │
│ │ AgentLoop │ │          │ │SessionManager│ │
│ │ · ReAct   │ │◄────────►│ │ · 内存管理    │ │
│ │ · 工具调用 │ │  上下文   │ │ · CRUD 操作  │ │
│ │ · 流式输出 │ │  切换     │ └──────┬───────┘ │
│ └───────────┘ │          │        │          │
└───────────────┘          │ ┌──────▼───────┐ │
                           │ │ SessionStore │ │
                           │ │ · JSON 读写   │ │
                           │ │ · 文件管理    │ │
                           │ └──────┬───────┘ │
                           └────────┼──────────┘
                                    │
                                    ▼
                           ~/.afs/
                             sessions/
                               ├── {uuid1}.json
                               ├── {uuid2}.json
                               └── ...
```

## 核心类说明

### session 包（⭐ 本 Demo 新增）

| 类 | 职责 |
|----|------|
| `Session` | 会话数据模型——包含 ID(UUID)、标题、创建/更新时间、消息列表，支持 Jackson 序列化 |
| `SessionStore` | 持久化存储层——负责将 Session 序列化为 JSON 文件，以及从文件系统加载恢复 |
| `SessionManager` | 会话管理服务——协调内存中的会话状态与持久化存储，提供创建/切换/删除/追加消息等操作 |
| `SessionChangeListener` | 会话变更监听器接口——通知 GUI 层刷新侧边栏 |

### 改造的类

| 类 | 改造内容 |
|----|---------|
| `AgentLoop` | 新增 `setConversationHistory()` / `resetConversation()` 方法，支持外部切换对话上下文 |
| `AgentService` | 集成 SessionManager，新增 `createSession()` / `switchSession()` / `deleteSession()` 等方法 |
| `ChatWindow` | 集成 SessionSidebar 侧边栏，支持会话切换时重新渲染历史消息 |

### ui 包（新增组件）

| 类 | 职责 |
|----|------|
| `SessionSidebar` | 侧边栏 GUI 组件——展示会话列表、新建按钮、右键删除菜单，支持点击切换 |

## 项目结构

```
demo03-session-management/
├── pom.xml
└── src/main/java/com/github/agent/demo03/
    ├── Demo03App.java              # JavaFX Application 入口
    ├── Launcher.java               # 启动器（绕过 JavaFX 模块检查）
    ├── agent/
    │   ├── AgentConfig.java        # Agent 配置（环境变量）
    │   └── AgentService.java       # ⭐ Agent 服务层（集成 SessionManager）
    ├── core/
    │   ├── AgentCallback.java      # 回调接口
    │   └── AgentLoop.java          # ⭐ Agent 核心循环（支持上下文切换）
    ├── llm/
    │   └── LLMClient.java          # LLM API 调用封装
    ├── model/
    │   └── ChatMessage.java        # 消息模型
    ├── session/                    # ⭐ 新增：会话管理包
    │   ├── Session.java            # 会话数据模型
    │   ├── SessionChangeListener.java  # 会话变更监听器
    │   ├── SessionManager.java     # 会话管理服务
    │   └── SessionStore.java       # 持久化存储层
    ├── tool/
    │   ├── Tool.java               # 工具接口
    │   ├── ToolRegistry.java       # 工具注册表
    │   └── impl/
    │       └── ExecTool.java       # Shell 命令执行工具
    └── ui/
        ├── ChatWindow.java         # ⭐ 聊天窗口（集成侧边栏）
        ├── MessageBubble.java      # 消息气泡组件
        └── SessionSidebar.java     # ⭐ 新增：会话列表侧边栏
```

## 快速开始

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo03-session-management
mvn exec:java
```

## 数据存储

会话数据以 JSON 文件形式存储在用户主目录下（`~/.afs/` 是 Agent From Scratch 的主目录）：

```
~/.afs/sessions/
├── a1b2c3d4-e5f6-7890-abcd-ef1234567890.json
├── f9e8d7c6-b5a4-3210-fedc-ba0987654321.json
└── ...
```

每个 JSON 文件包含一个完整的会话数据：

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "title": "帮我看看当前目录有哪些文件",
  "createdAt": "2026-04-06T15:00:00",
  "updatedAt": "2026-04-06T15:05:30",
  "messages": [
    { "role": "system", "content": "你是一个能操作电脑的 AI 助手..." },
    { "role": "user", "content": "帮我看看当前目录有哪些文件" },
    { "role": "assistant", "content": null, "toolCalls": [...] },
    { "role": "tool", "content": "file1.txt\nfile2.java\n...", "toolCallId": "call_xxx" },
    { "role": "assistant", "content": "当前目录下有以下文件：..." }
  ]
}
```

## 与 demo02 的差异

| 特性 | demo02 | demo03 |
|------|--------|--------|
| 会话数量 | 单一会话 | 多会话管理 |
| 数据持久化 | 无（内存中，关闭即丢失） | JSON 文件持久化 |
| 会话切换 | 不支持 | 侧边栏点击切换 |
| 历史恢复 | 不支持 | 启动时自动加载 |
| GUI 布局 | 纯聊天窗口 | 左侧侧边栏 + 右侧聊天窗口 |
