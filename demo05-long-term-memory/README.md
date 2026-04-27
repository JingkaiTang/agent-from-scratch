# Demo 05 - Long-Term Memory（长期记忆）

在 demo04（短期记忆管理）基础上新增**跨会话长期记忆**——Agent 能自动从对话中提取值得记住的信息，持久化到 `~/.afs/memories.md`，并在后续对话中检索相关记忆注入上下文。

## 学习目标

| 概念 | 说明 |
|------|------|
| **记忆提取** | 用 LLM 从对话中提取值得长期记住的事实（用户偏好、个人信息、技术决策等） |
| **记忆检索** | LLM 提取语义关键词 + grep 搜索 memories.md，两步检索 |
| **Markdown 持久化** | 用人类可读的 Markdown 文件存储记忆，追加写入，按时间分段 |
| **Facade 模式** | `LongTermMemoryManager` 作为统一门面，协调提取、检索、存储三个组件 |
| **短期 + 长期记忆协作** | 短期记忆管理上下文长度，长期记忆提供跨会话知识 |

## 核心机制

### 记忆检索（每次对话开始时）

```
用户发消息 → AgentLoop.run()
  → LongTermMemoryManager.retrieve(userMessage)
      1. MemoryRetriever 调用 LLM 从用户消息中提取语义关键词
         "你记得我叫什么吗？" → ["姓名", "用户", "名字"]
      2. MemoryStore.search() 用关键词 grep 搜索 memories.md
      3. 匹配到的记忆条目注入 system prompt
  → 正常执行 ReAct 循环
```

### 记忆写入（每轮对话结束后）

```
Agent 回复完成 → AgentService.sendMessage() 后续处理
  → LongTermMemoryManager.ingest(conversationHistory)
      1. MemoryExtractor 调用 LLM 分析最近对话
         提取值得记住的信息（用户名、偏好、技术栈等）
      2. MemoryStore.append() 追加写入 memories.md
  → 回调通知 GUI 展示写入条目数
```

## 架构设计

```
┌───────────────────────────────────────────────────────────┐
│                     AgentLoop.run()                        │
│                                                           │
│  -1. ⭐ 长期记忆：检索相关知识，注入 system message        │
│   0. 短期记忆：检查 token，超阈值压缩                      │
│   1. SSE 流式调用 LLM                                     │
│   2. tool_calls → 执行工具 → 追加结果                      │
│   3. 纯文本回复 → 流式推送 → 完成                          │
└───────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼                               ▼
┌───────────────────┐         ┌───────────────────────┐
│  memory 包         │         │  longmem 包 ⭐         │
│  短期记忆管理      │         │  ┌─────────────────┐  │
│  · MemoryManager  │         │  │LongTermMemory    │  │
│  · TokenCounter   │         │  │Manager (Facade)  │  │
│  · Compactor      │         │  └──┬──────┬──────┬─┘  │
└───────────────────┘         │     │      │      │    │
                              │     ▼      ▼      ▼    │
                              │  Extract Retrieve Store │
                              │  (LLM)  (LLM+grep)(MD) │
                              └───────────────────────┘
                                          │
                                          ▼
                                  ~/.afs/memories.md
```

## 与 demo04 的差异

| 特性 | demo04 | demo05 |
|------|--------|--------|
| 短期记忆 | ✅ 滑动窗口 + LLM 摘要 | ✅ 继承 |
| 长期记忆 | 无 | ⭐ 跨会话记忆提取 + 检索 |
| 记忆存储 | 无 | `~/.afs/memories.md`（Markdown 追加写入） |
| 上下文注入 | 仅压缩摘要 | 压缩摘要 + 长期记忆知识 |
| 新增 GUI 气泡 | MEMORY（紫色） | MEMORY + LONG_TERM_QUERY + LONG_TERM_INGEST |

## 项目结构

```
demo05-long-term-memory/
├── pom.xml
└── src/main/java/com/github/agent/demo05/
    ├── Demo05App.java
    ├── Launcher.java
    ├── agent/
    │   ├── AgentConfig.java
    │   └── AgentService.java       # ⭐ 集成 LongTermMemoryManager
    ├── config/
    │   └── AppConfigLoader.java
    ├── core/
    │   ├── AgentCallback.java      # ⭐ 新增长期记忆回调
    │   └── AgentLoop.java          # ⭐ 对话前检索长期记忆
    ├── llm/
    │   └── LLMClient.java
    ├── longmem/                    # ⭐ 新增：长期记忆包
    │   ├── LongTermMemoryManager.java  # 门面（Facade）
    │   ├── MemoryExtractor.java    # LLM 提取记忆
    │   ├── MemoryRetriever.java    # LLM 关键词 + grep 检索
    │   └── MemoryStore.java        # Markdown 文件读写
    ├── memory/                     # 继承 demo04 短期记忆
    │   ├── MemoryConfig.java
    │   ├── TokenCounter.java
    │   ├── CompactionResult.java
    │   ├── ConversationCompactor.java
    │   └── MemoryManager.java
    ├── model/
    │   └── ChatMessage.java
    ├── session/
    ├── tool/
    └── ui/
        ├── ChatWindow.java         # ⭐ 展示长期记忆气泡
        ├── MessageBubble.java      # ⭐ 新增记忆相关气泡类型
        └── SessionSidebar.java
```

## 核心类说明

### longmem 包（⭐ 本 Demo 新增）

| 类 | 职责 |
|----|------|
| `LongTermMemoryManager` | 门面（Facade）——统一协调 retrieve() 和 ingest() 两个核心操作 |
| `MemoryExtractor` | LLM 记忆提取——分析对话，提取用户个人信息、偏好、技术决策等值得记住的事实 |
| `MemoryRetriever` | 两步检索——① LLM 从用户消息中提取语义关键词 ② grep 搜索 memories.md |
| `MemoryStore` | Markdown 持久化——追加写入 `~/.afs/memories.md`，按时间戳分段 |

### 新增回调

| 回调方法 | 触发时机 |
|---------|---------|
| `onLongTermMemoryQuery(knowledge, matchCount)` | 从长期记忆检索到相关知识并注入上下文时 |
| `onLongTermMemoryIngest(entryCount)` | 从对话中提取知识并写入长期记忆时 |

## 快速开始

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选

# 编译 & 运行
cd agent-from-scratch
mvn compile
cd demo05-long-term-memory
mvn exec:java
```

## 数据存储

长期记忆以 Markdown 格式存储在 `~/.afs/memories.md`（追加写入，人类可读）：

```markdown
# 长期记忆
> 此文件由 Agent 自动维护，记录跨会话的重要信息。

## 2026-04-14 10:30
- 用户名叫小明，是一名 Java 开发者
- 用户偏好使用中文回复

## 2026-04-14 11:15
- 用户的项目使用 Maven 构建，Java 25
- 用户在做一个 AI Agent 教学项目叫 agent-from-scratch
```

## 为什么用 LLM 提取关键词而不是简单分词？

简单分词（如按空格/标点切分）会产生大量噪音（停用词、语气词等），且无法理解语义。例如：

| 用户消息 | 简单分词 | LLM 提取 |
|---------|---------|---------|
| "你记得我叫什么吗？" | ["你", "记得", "我", "叫", "什么", "吗"] | ["姓名", "用户", "名字"] |
| "之前那个 Maven 项目怎么样了？" | ["之前", "那个", "Maven", "项目", "怎么样", "了"] | ["Maven", "项目", "构建"] |

LLM 理解语义后提取的关键词更精准，虽然多一次 LLM 调用，但检索质量大幅提升。
