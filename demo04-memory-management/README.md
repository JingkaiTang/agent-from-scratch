# Demo 04 - Memory Management（短期记忆管理）

在 demo03（会话管理）基础上新增**短期记忆管理**功能——当对话上下文过长时，自动触发滑动窗口 + LLM 摘要压缩，并在 GUI 中展示压缩过程。

## 学习目标

| 概念 | 说明 |
|------|------|
| **Token 估算** | 手搓 token 计数器，理解中英文 token 化的差异 |
| **滑动窗口** | 保留最近 N 轮对话，裁剪早期消息 |
| **LLM 摘要压缩** | 用 LLM 将被裁掉的旧消息压缩为一条摘要，保留关键信息 |
| **配置持久化** | 从 `~/.afs/config.json` 加载记忆管理参数 |
| **可观察性** | 在 GUI 对话流中插入紫色气泡展示压缩事件 |

## 核心机制

```
用户发消息 → AgentLoop.run()
  → 每次调 LLM 前，MemoryManager 检查 token 用量
  → 超过阈值（默认 80%）:
      1. 保留 system prompt + 最近 3 轮对话（滑动窗口）
      2. 被裁掉的旧消息用 LLM 压缩为摘要
      3. 用 [system prompt, 摘要, 最近 3 轮] 替换原始历史
      4. 回调通知 GUI → 插入紫色记忆压缩气泡
  → 正常调用 LLM
```

## 架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                      GUI 层 (ui)                              │
│  ┌──────────────┐    ┌───────────────────────────────────┐   │
│  │SessionSidebar│◄──►│           ChatWindow               │   │
│  └──────────────┘    │  · 消息气泡（USER/AGENT/TOOL/...） │   │
│                      │  · ⭐ 记忆压缩气泡（MEMORY 类型）   │   │
│                      └───────────────┬───────────────────┘   │
└──────────────────────────────────────┼───────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │            AgentService              │
                    │  · 初始化 MemoryManager 并注入       │
                    └──────┬────────────────────┬──────────┘
                           │                    │
              ┌────────────┘                    └────────────┐
              ▼                                              ▼
┌──────────────────────┐                    ┌────────────────────┐
│     core/AgentLoop   │                    │   memory 包 ⭐      │
│  · ReAct 循环        │◄──── 每次调 LLM ──►│  ┌──────────────┐  │
│  · 流式输出          │      前检查压缩    │  │MemoryManager │  │
│  · 工具调用          │                    │  │ · 检查 token  │  │
└──────────────────────┘                    │  │ · 触发压缩    │  │
                                            │  └──────┬───────┘  │
                                            │         │          │
                                            │  ┌──────▼───────┐  │
                                            │  │TokenCounter   │  │
                                            │  │ · 中文~1.5t/字│  │
                                            │  │ · 英文~0.5t/字│  │
                                            │  └──────────────┘  │
                                            │  ┌──────────────┐  │
                                            │  │Conversation   │  │
                                            │  │Compactor      │  │
                                            │  │ · LLM 生成摘要│  │
                                            │  └──────────────┘  │
                                            └────────────────────┘
```

## 与 demo03 的差异

| 特性 | demo03 | demo04 |
|------|--------|--------|
| 上下文管理 | 无限制增长 | 自动压缩（滑动窗口 + 摘要） |
| Token 感知 | 无 | TokenCounter 实时估算 |
| 配置管理 | 仅环境变量 | `~/.afs/config.json` 持久化 |
| GUI 气泡类型 | 6 种 | 7 种（新增 MEMORY 紫色气泡） |
| reasoning 支持 | 无 | 透传 reasoning_content（兼容 qwen3.5 thinking 模式） |

## 项目结构

```
demo04-memory-management/
├── pom.xml
└── src/main/java/com/github/agent/demo04/
    ├── Demo04App.java              # JavaFX Application 入口
    ├── Launcher.java               # 启动器
    ├── agent/
    │   ├── AgentConfig.java        # Agent 配置
    │   └── AgentService.java       # ⭐ 集成 MemoryManager
    ├── config/
    │   └── AppConfigLoader.java    # ⭐ 读取 ~/.afs/config.json
    ├── core/
    │   ├── AgentCallback.java      # ⭐ 新增 onMemoryCompaction 回调
    │   └── AgentLoop.java          # ⭐ 每次 LLM 调用前检查压缩
    ├── llm/
    │   └── LLMClient.java          # ⭐ 支持 reasoning_content 透传
    ├── memory/                     # ⭐ 新增：记忆管理包
    │   ├── MemoryConfig.java       # 记忆管理配置
    │   ├── TokenCounter.java       # Token 估算器
    │   ├── CompactionResult.java   # 压缩结果
    │   ├── ConversationCompactor.java  # 对话压缩器
    │   └── MemoryManager.java      # 记忆管理器
    ├── model/
    │   └── ChatMessage.java        # ⭐ 新增 reasoningContent 字段
    ├── session/                    # 复用 demo03
    ├── tool/                       # 复用
    └── ui/
        ├── ChatWindow.java         # ⭐ 展示记忆压缩气泡
        ├── MessageBubble.java      # ⭐ 新增 MEMORY 类型（紫色居中）
        └── SessionSidebar.java     # 复用
```

## 核心类说明

### memory 包（⭐ 本 Demo 新增）

| 类 | 职责 |
|----|------|
| `TokenCounter` | 手搓 token 估算器——中文 ~1.5 token/字，英文 ~0.5 token/字，精度 ±20% |
| `MemoryConfig` | 配置——maxContextTokens(8000)、compactionThreshold(0.8)、keepRecentRounds(3) |
| `CompactionResult` | 不可变压缩结果——摘要文本、前后 token 对比、移除消息数、压缩率 |
| `ConversationCompactor` | 对话压缩器——分割旧消息 → 调 LLM 生成摘要 → 组装新消息列表 |
| `MemoryManager` | 核心协调者——检查 token 用量 → 超过阈值则调用 Compactor 压缩 |

### config 包（⭐ 本 Demo 新增）

| 类 | 职责 |
|----|------|
| `AppConfigLoader` | 从 `~/.afs/config.json` 加载配置，文件不存在时自动创建默认配置 |

## 快速开始

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo04-memory-management
mvn exec:java
```

## 配置文件

记忆管理参数存储在 `~/.afs/config.json`（首次运行自动创建）：

```json
{
  "memory": {
    "maxContextTokens": 8000,
    "compactionThreshold": 0.8,
    "keepRecentRounds": 3
  }
}
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `maxContextTokens` | 8000 | 上下文 token 上限（教学用途故意设小，生产中根据模型能力设置） |
| `compactionThreshold` | 0.8 | 达到上限的 80% 时触发压缩，留 20% buffer |
| `keepRecentRounds` | 3 | 压缩时保留最近 3 轮对话（1 轮 = user + assistant + 可能的 tool 消息） |

## 消息气泡类型

| 类型 | 颜色 | 说明 |
|------|------|------|
| USER | 蓝色（右对齐） | 用户消息 |
| AGENT | 灰色（左对齐） | Agent 回复 |
| TOOL_CALL | 橙色边框 | 工具调用 |
| TOOL_RESULT | 绿色边框 | 工具结果 |
| ERROR | 红色边框 | 错误信息 |
| STATUS | 灰色居中 | 状态提示 |
| **MEMORY** | **紫色边框（居中）** | **⭐ 记忆压缩通知（摘要 + token 对比）** |
