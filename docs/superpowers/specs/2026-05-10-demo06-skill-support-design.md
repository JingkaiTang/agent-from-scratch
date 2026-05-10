# demo06 — Skill 支持设计文档

- **状态**：Draft，待用户审阅
- **日期**：2026-05-10
- **基线**：demo05-long-term-memory
- **核心命题**：在 Agent 中引入 Claude Code 风格的 Skills 机制——元数据预加载 + LLM 自主按需加载完整指令

---

## 1. 背景与动机

`agent-from-scratch` 是一个层累式教学 demo 系列，每一个 demo 在前一个的基础上引入**一个**核心概念：

| 模块 | 主题 | 核心概念 |
|------|------|---------|
| demo00 | Basic Agent | ReAct 循环、Tool Use、LLM 调用 |
| demo01 | Computer Use | Shell 命令执行、安全机制 |
| demo02 | GUI Agent | JavaFX 界面、SSE 流式响应 |
| demo03 | Session Management | 会话建模、JSON 持久化 |
| demo04 | Memory Management | 短期记忆、滑动窗口压缩 |
| demo05 | Long-Term Memory | 跨会话长期记忆、Markdown 持久化 |
| **demo06** | **Skills** | **元数据索引、按需加载、自描述指令包** |

demo06 引入 **Skills 机制**——磁盘上每一个 skill 是一个带 YAML frontmatter 的 Markdown 文件；启动时 Agent 扫描可用 skill 并把"菜单"（name + description）注入 system prompt；LLM 在判断需要时通过内置工具 `load_skill(name)` 加载完整正文，正文作为对话历史一部分参与后续推理。

这与 Tool 机制的关键区别：
- **Tool** 是"做事的能力"——execute 输入 → 输出
- **Skill** 是"做事的指令包"——加载后改变 LLM 的行为风格和工作流程

与 demo04/05 记忆机制的关键区别：
- **短期记忆**（demo04）：自动压缩对话历史，对 LLM 透明
- **长期记忆**（demo05）：跨会话知识库，启动时自动检索注入
- **Skill**（demo06）：用户编写的"指令包"，由 LLM 自主决策何时加载

## 2. 设计原则

1. **元数据 vs 正文分离**：system prompt 只承载轻量的 skill 菜单，正文按需才加载。这是 Skills 机制的核心教学点。
2. **零新机制**：复用已有 Tool / AgentCallback / SessionManager / MemoryManager 管线，把 `load_skill` 实现为一个普通 Tool。
3. **YAGNI**：不做 skill 启用/禁用、版本管理、参数化、热加载等附加功能；这些会模糊主线。
4. **错误降级**：Skill 子系统的任何错误都不让 Agent 启动失败或运行崩溃；统一为日志 warn 或返回错误字符串给 LLM。
5. **对话历史一致性**：已加载的 skill 正文就是普通的 tool 消息，不引入特殊状态字段；自然地参与会话切换、记忆压缩。

## 3. 架构总览

```
┌─────────────────────────── GUI 层 ────────────────────────────┐
│  SessionSidebar  │   ChatWindow（+ 加载气泡） │ SkillSidebar  │
│   （左，复用）    │   （中央，小改）           │  （右，新增） │
└──────────────────┴────────────────────────────┴───────────────┘
                              ▲
                              │ AgentCallback（+ onSkillLoad）
┌─────────────────────────── 服务层 ────────────────────────────┐
│                    AgentService（小改）                        │
│  组装：LLM + ToolRegistry + MemoryMgr + LongTermMemoryMgr      │
│                            + SkillRegistry  ← 新增             │
└────────────────────────────────────────────────────────────────┘
                              ▲
┌────────────────────── 核心层（AgentLoop） ────────────────────┐
│   ReAct 循环（不改）—— System Prompt 被追加 Skill 菜单         │
│   工具清单：ExecTool  +  LoadSkillTool（新增）                 │
└────────────────────────────────────────────────────────────────┘
                              ▲
┌────────────────────── Skills 子系统（新增） ──────────────────┐
│   SkillLoader        SkillRegistry        LoadSkillTool        │
│   （首启拷贝+扫描）  （缓存 Meta+读正文） （implements Tool）  │
└────────────────────────────────────────────────────────────────┘
                              ▲
                ┌─ ~/.afs/skills/<name>/SKILL.md ─┐
                │  frontmatter: name, description │
                │  body: Markdown 指令正文         │
                └─────────────────────────────────┘
```

**核心心智模型（一句话）：**

> Agent 启动 → 扫 `~/.afs/skills/` → Skill 菜单注入 system prompt → LLM 看到相关请求时主动调 `load_skill(name)` → 工具返回正文 → 正文作为 tool 消息留在 history，后续推理按正文指令执行。

## 4. Skill 文件格式

每个 skill 占用一个目录 `~/.afs/skills/<name>/`，目录内必须有 `SKILL.md`：

```markdown
---
name: code-review
description: 审查代码质量、找 bug、给改进建议。用户贴出代码并希望被审查时触发。
---

# Code Review Skill
...（正文 Markdown）...
```

**frontmatter 解析规则（手写解析器，不引依赖）：**
- 文件首行必须是 `---`
- 至下一个 `---` 行之间为 frontmatter
- 每行 `key: value` 格式；空格分隔
- 仅识别 `name` 和 `description` 两个字段，其它忽略
- `name` 必填，作为唯一标识；按惯例与目录名一致（不强制校验）
- `description` 必填，作为 LLM 判断是否加载的依据
- 解析失败的文件被跳过并 warn

## 5. Skills 子系统详细设计

新增包 `com.github.agent.demo06.skill`，包含四个类。

### 5.1 `SkillMeta`（不可变 POJO）

```java
public final class SkillMeta {
    private final String name;          // frontmatter.name，唯一标识
    private final String description;   // frontmatter.description
    private final Path filePath;        // SKILL.md 绝对路径
    // 构造方法 + 三个 getter；hashCode/equals 按 name
}
```

**不持有正文**——元数据 vs 正文分离是设计核心；正文每次按需从磁盘读。

### 5.2 `SkillLoader`

职责：
1. **首次运行**：检测 `~/.afs/skills/` 是否存在；不存在则创建并从 classpath `resources/skills/` 拷贝内置示例（3 个）。
2. **每次启动**：扫描 `~/.afs/skills/` 下每个子目录的 `SKILL.md`，解析 frontmatter，返回 `List<SkillMeta>`。
3. 解析失败的 skill：日志 warn 并跳过，不让启动崩溃。

关键方法：

```java
public List<SkillMeta> loadAll();           // 首启拷贝 + 扫描 + 解析
private void bootstrapDefaults();            // 内部：首次拷贝
private SkillMeta parseFrontmatter(Path);    // 内部：解析单个 SKILL.md
```

### 5.3 `SkillRegistry`

进程内缓存 + Prompt 菜单生成。

```java
public class SkillRegistry {
    private final Map<String, SkillMeta> byName;

    public void register(List<SkillMeta> metas);
    public List<SkillMeta> list();                  // 供 SkillSidebar 遍历
    public SkillMeta get(String name);              // 供 LoadSkillTool 查询
    public String readBody(String name) throws IOException;  // 按需读 SKILL.md 正文
    public String toPromptMenu();                   // 生成注入 system prompt 的菜单文本
}
```

`toPromptMenu()` 输出（如果有 skill）：

```
你可以按需加载以下 skill 来获得专门的指令和工作流程：

- code-review: 审查代码质量、找 bug、给改进建议。贴代码时触发。
- translate-zh-en: 中英互译，保持专业术语一致。
- summarize-long-text: 对长文本做结构化摘要，输出 TL;DR + 要点。

当你判断某个 skill 与当前任务相关时，调用工具 load_skill(name) 加载它的完整指令。
加载后请严格按指令执行。不相关时无需加载，直接回复即可。
```

如果 `byName` 为空：返回单行"当前无可用 skill。"或返回空串（不追加到 system prompt）。

### 5.4 `LoadSkillTool`

```java
public class LoadSkillTool implements Tool {
    private final SkillRegistry registry;
    private AgentCallback callback;  // 由 AgentService 在 setCallback 时回填

    public String name() { return "load_skill"; }
    public String description() {
        return "加载指定 skill 的完整指令到对话上下文。"
             + "调用前你已经在 system prompt 的菜单里看过 skill 清单。";
    }
    public Map<String, Object> parameters() {
        // OpenAI function calling JSON schema:
        // { "type": "object", "properties": { "name": { "type": "string", "description": "..." } },
        //   "required": ["name"] }
    }

    public String execute(Map<String, Object> args) {
        String name = (String) args.get("name");
        if (name == null || name.isBlank()) {
            return "错误：缺少必需参数 'name'。";
        }
        SkillMeta meta = registry.get(name);
        if (meta == null) {
            String available = registry.list().stream()
                .map(SkillMeta::getName).collect(Collectors.joining(", "));
            return "错误：未找到名为 '" + name + "' 的 skill。\n可用的 skill 有：" + available + "。";
        }
        try {
            String body = registry.readBody(name);
            if (callback != null) callback.onSkillLoad(name, meta.getDescription());
            return body;
        } catch (IOException e) {
            return "错误：读取 skill '" + name + "' 失败：" + e.getMessage();
        }
    }

    public void setCallback(AgentCallback cb) { this.callback = cb; }
}
```

参数 schema 仅 `name: string`（必填），最简化以降低 LLM 出错概率。

## 6. AgentCallback 扩展

在 demo05 `AgentCallback` 接口尾部追加一个 default 方法：

```java
/**
 * 当 LLM 通过 load_skill 工具加载某个 skill 后触发。
 *
 * @param skillName   被加载的 skill 名
 * @param description skill 的简短描述（来自 frontmatter）
 */
default void onSkillLoad(String skillName, String description) {}
```

风格与现有 `onMemoryCompaction` / `onLongTermMemoryQuery` 完全对齐。

## 7. AgentService 集成

在 `initialize()` 流程中插入两段：

```java
// 1. 加载 skills
SkillLoader skillLoader = new SkillLoader();
List<SkillMeta> metas = skillLoader.loadAll();
SkillRegistry skillRegistry = new SkillRegistry();
skillRegistry.register(metas);

// 2. 注册 LoadSkillTool
ToolRegistry toolRegistry = new ToolRegistry();
toolRegistry.register(new ExecTool());
LoadSkillTool loadSkillTool = new LoadSkillTool(skillRegistry);
toolRegistry.register(loadSkillTool);
this.loadSkillTool = loadSkillTool;
this.skillRegistry = skillRegistry;

// 3. 拼接最终 system prompt（基础 + 菜单）
String finalSystemPrompt = AgentConfig.SYSTEM_PROMPT
                         + "\n\n" + skillRegistry.toPromptMenu();
agentLoop = new AgentLoop(llmClient, toolRegistry, finalSystemPrompt, config.getMaxSteps());
```

`setCallback()` 同步给 `LoadSkillTool`：

```java
public void setCallback(AgentCallback callback) {
    if (agentLoop != null) agentLoop.setCallback(callback);
    if (loadSkillTool != null) loadSkillTool.setCallback(callback);
}
```

新增 getter 给 GUI：

```java
public SkillRegistry getSkillRegistry() { return skillRegistry; }
```

## 8. System Prompt 组装

最终注入 `AgentLoop` 的 system prompt 形如：

```
[demo05 原 SYSTEM_PROMPT 不变]

[skillRegistry.toPromptMenu() 返回的菜单段]
```

**为什么菜单拼到主 system prompt 而不是新增独立 system 消息？**
- Skill 菜单整个进程生命周期内不变，拼到主 prompt 最自洽，conversationHistory 最干净。
- demo05 的"长期记忆动态注入"用的是新 system 消息（每轮重新查），那是因为它每轮都变化；Skill 菜单不需要这种动态性。

## 9. 已加载 skill 集合的计算

GUI 侧需要展示"当前会话已加载了哪些 skill"。该集合**不持久化、纯函数式**从当前会话的 `conversationHistory` 推导：

```java
public static Set<String> computeLoadedSkills(List<ChatMessage> history) {
    // 思路：遍历 assistant 消息的 toolCalls，找 function.name == "load_skill" 的调用
    //       提取 arguments.name 字段
    //       配对其后的 tool 消息（同 tool_call_id）—— 内容不以"错误：" 开头者视为成功
    //       收集成功加载的 skill 名集合
    Set<String> loaded = new HashSet<>();
    // 实现细节略
    return loaded;
}
```

会话切换、`onSkillLoad` 触发时调用一次，刷新 `SkillSidebar` 徽章。

## 10. GUI 设计

### 10.1 整体布局

`ChatWindow` 的 `BorderPane` 增加 `right` 槽位：

```
┌───────────────┬──────────────────────────────────┬───────────────┐
│ SessionSidebar│  标题栏 + 消息流 + 输入区         │ SkillSidebar  │
│  （左 240px） │   （中央自适应）                  │  （右 220px） │
└───────────────┴──────────────────────────────────┴───────────────┘
```

Scene 默认宽度从 demo05 的值上调 ~220px。

### 10.2 SkillSidebar 组件

```java
public class SkillSidebar {
    private final VBox root;
    private final SkillRegistry registry;
    private final AgentService agentService;
    private final Map<String, SkillCell> cellByName = new HashMap<>();
    private Set<String> loadedSkills = Set.of();

    public SkillSidebar(AgentService agentService);
    public VBox getRoot();
    public void onSkillLoadEvent(String name);    // 由 ChatWindow 转发
    private void onSessionChanged(Session newSession);
}
```

`SkillCell` 渲染：徽章（□/☑）+ 加粗 skill name + 灰色小字 description。**不响应点击**——强调"由 LLM 自主决定加载"是 Skills 机制的精髓。

事件订阅：
- 启动：从 `registry.list()` 构建 cell
- `agentService.addSessionChangeListener(this::onSessionChanged)`：会话切换时重算 `loadedSkills` 并刷新徽章
- `onSkillLoadEvent(name)`：由 ChatWindow 在 `onSkillLoad` 回调里手动转发

### 10.3 ChatWindow 改动

**(a) 新增"已加载技能"气泡：**
- `MessageBubble` 新增静态工厂 `skillLoaded(name, description)`
- 配色：蓝绿色（区别于紫色压缩气泡、橙色工具气泡）
- 文案：

```
🧩 已加载技能：code-review
   审查代码质量、找 bug、给改进建议
```

**(b) `bindAgentCallback()` 新增事件处理：**

```java
@Override
public void onSkillLoad(String skillName, String description) {
    Platform.runLater(() -> {
        MessageBubble bubble = MessageBubble.skillLoaded(skillName, description);
        messageContainer.getChildren().add(bubble.getRoot());
        scrollToBottom();
        skillSidebar.onSkillLoadEvent(skillName);  // 转发给侧边栏
    });
}
```

ChatWindow 作为单一 callback 入口，事件分发给 SkillSidebar。这保留了 demo05 "AgentService 仅接受一个 callback" 的简洁性。

## 11. 示例 Skill 内容

三个内置示例放在 `demo06-skill-support/src/main/resources/skills/`，首次启动时拷贝到 `~/.afs/skills/`。

### 11.1 `code-review/SKILL.md`

```markdown
---
name: code-review
description: 审查代码质量、找 bug、给改进建议。用户贴出代码并希望被审查时触发。
---

# Code Review Skill

当用户分享代码并希望你做 code review 时，按以下结构回复：

## 1. 总体评价（一句话）
对代码整体质量给出一句话判断（优秀 / 良好 / 一般 / 有重大问题）。

## 2. 发现的问题
按严重程度从高到低列出，每个问题包含：
- **位置**：第 N 行 / 函数名
- **问题**：具体描述
- **建议**：如何修复

类型分类：
- 🔴 Bug / 安全风险
- 🟡 设计 / 可维护性问题
- 🟢 风格 / 命名 / 注释

## 3. 改进建议（如果有）
不是 bug 但能让代码更好的建议。

## 4. 亮点（如果有）
代码中值得肯定的地方。

## 注意
- 如果代码很短、问题很少，可以省略某些章节
- 如果代码看起来不完整，先告知用户并请求补充
- 不要过度挑剔无关紧要的风格问题
```

### 11.2 `translate-zh-en/SKILL.md`

```markdown
---
name: translate-zh-en
description: 中英互译。用户希望翻译文本时触发，自动识别源语言并译为另一语言。
---

# 中英互译 Skill

## 工作步骤
1. 自动识别源语言（中文 → 译为英文；英文 → 译为中文）
2. 直接给出翻译结果，不要附加"以下是翻译"等冗余前缀
3. 翻译后另起一段，简短说明：
   - 关键术语的处理理由（如有）
   - 任何意译/直译的取舍

## 风格要求
- **保留专业术语**：技术术语保持原文或使用业界标准译法（例：API、SDK、prompt、token 等英文术语在中译时通常保留）
- **保持语气**：原文正式则译文正式，原文口语化则译文口语化
- **保留格式**：Markdown 标记、代码块、列表结构原样保留

## 注意
- 如果原文有歧义，先指出歧义点再给出最合理的译法
- 如果原文过短（< 5 字）且无上下文，请确认用户希望翻译的方向
```

### 11.3 `summarize-long-text/SKILL.md`

```markdown
---
name: summarize-long-text
description: 对长文本做结构化摘要，输出 TL;DR + 要点。用户希望"总结/概括/摘要"长文时触发。
---

# 长文摘要 Skill

按以下结构输出摘要：

## TL;DR
**一句话**说清整篇文章在讲什么。

## 核心要点
3-7 个要点，每点一句话。按重要性排序。

## 关键数据/引用（如果原文有）
- 重要数字、统计、引述
- 保留原文用词，不要改写

## 个人评估（可选）
仅当用户明确要求时才提供你对内容的看法。默认只摘要不评论。

## 长度规则
- 原文 < 1000 字：要点 3 个左右
- 原文 1000-5000 字：要点 5 个左右
- 原文 > 5000 字：要点 7 个左右，可以再加"按章节分述"

## 注意
- 不要在摘要里编造原文没有的信息
- 如果原文有事实错误，可以在最后用 ⚠️ 标注，但不要修改摘要内容
```

## 12. 错误处理与边界情况

| 场景 | 行为 |
|---|---|
| `~/.afs/skills/` 不存在 | `bootstrapDefaults()` 创建目录 + 拷贝内置示例 |
| `~/.afs/skills/` 存在但为空 | 不重新拷贝；日志 warn；菜单段降级或省略 |
| SKILL.md frontmatter 缺 `name` 或 `description` | 日志 warn 并跳过此 skill，其它正常加载 |
| frontmatter 不闭合（找不到第二个 `---`） | 同上，跳过 |
| 两个 skill 重名 | 后扫描的覆盖前者；日志 warn 提示重名 |
| `load_skill` 缺 `name` 参数 | 返回 "错误：缺少必需参数 'name'。" |
| `load_skill` 调用不存在的 skill | 返回友好错误 + 可用清单 |
| `load_skill` 读文件 IOException | 返回 "错误：读取 skill '<name>' 失败：<msg>" |
| 重复加载同一 skill | 老实再返回正文，不特殊处理（依赖记忆压缩兜底） |
| 内置示例拷贝时 IO 失败 | 日志 error，启动继续 |

**核心原则：** Skills 子系统的任何错误都不能让 Agent 启动失败或运行崩溃。

## 13. 测试策略

延续 demo00-05 的"手动验证为主、不引 JUnit"传统。在 `demo06-skill-support/README.md` 中给出以下手动验证清单：

1. **首次启动**：删除 `~/.afs/skills/`，启动 demo06 → 验证目录被自动创建、3 个示例被拷贝
2. **菜单注入**：启动日志输出 "已发现 3 个 skill"；和 LLM 对话验证 LLM 知道有这些 skill
3. **加载触发**：发送 "帮我 review 这段代码：```python\ndef add(a,b): return a-b\n```" → 观察聊天流出现 `load_skill` 工具调用气泡 + "🧩 已加载技能：code-review" 气泡 + 右侧 SkillSidebar 中 code-review 徽章变 ☑ + LLM 后续回复结构符合 SKILL.md
4. **不触发**：发送 "今天天气怎么样" → 验证 LLM 不会乱加载 skill
5. **会话切换徽章刷新**：在会话 A 加载 code-review，新建会话 B → 切到 B 验证徽章全部回到未加载；切回 A 验证 ☑ 恢复
6. **错误处理**：手动改一个 SKILL.md 删掉 `name:` 行 → 重启验证日志 warn、其它 skill 正常、菜单里少了这一项
7. **不存在的 skill**：诱导 LLM 调 `load_skill("nonexistent")` → 验证返回错误消息且包含可用清单
8. **重复加载**：连续两次 review → 验证第二次也老实返回正文

## 14. 模块结构

```
demo06-skill-support/
├── pom.xml
├── README.md                                    # 含手动验证清单
└── src/main/
    ├── java/com/github/agent/demo06/
    │   ├── Demo06App.java                        # 入口（仿 demo05）
    │   ├── Launcher.java                          # 仿 demo05
    │   ├── skill/                                # ⭐ 新增包
    │   │   ├── SkillMeta.java
    │   │   ├── SkillLoader.java
    │   │   ├── SkillRegistry.java
    │   │   └── LoadSkillTool.java
    │   ├── ui/
    │   │   ├── ChatWindow.java                   # 小改：onSkillLoad + 加 right 槽位
    │   │   ├── SessionSidebar.java               # 复用
    │   │   ├── SkillSidebar.java                 # ⭐ 新增
    │   │   ├── SkillCell.java                    # ⭐ 新增（SkillSidebar 内的 cell 组件）
    │   │   └── MessageBubble.java                # 小改：新增 skillLoaded 工厂方法 + 蓝绿色样式
    │   ├── core/
    │   │   ├── AgentLoop.java                    # 复用（不改）
    │   │   └── AgentCallback.java                # 小改：追加 onSkillLoad default 方法
    │   ├── agent/
    │   │   ├── AgentService.java                 # 小改：initialize + setCallback + getter
    │   │   └── AgentConfig.java                  # 复用（SYSTEM_PROMPT 不改，菜单运行时拼接）
    │   ├── llm/                                   # 全部复用
    │   ├── memory/                                # 全部复用
    │   ├── longmem/                               # 全部复用
    │   ├── session/                               # 全部复用
    │   ├── tool/                                  # 全部复用（Tool 接口、ToolRegistry、ExecTool）
    │   ├── model/                                 # 全部复用
    │   └── config/                                # 全部复用
    └── resources/
        ├── logback.xml / icon*.png / style.css   # 复用
        └── skills/                                # ⭐ 新增：内置示例 skill
            ├── code-review/SKILL.md
            ├── translate-zh-en/SKILL.md
            └── summarize-long-text/SKILL.md
```

根 `pom.xml` 的 `<modules>` 中追加 `<module>demo06-skill-support</module>`。

## 15. 根 README 更新

`agent-from-scratch/README.md` 学习路线表中：
- demo06 状态从规划改为 ✅
- 主题：Skills（按需加载技能）
- 核心概念：元数据索引、按需加载、自描述指令包

## 16. 范围之外（明确不做）

为保持教学焦点，以下功能**不在 demo06 范围**：

- Skill 的启用/禁用开关
- Skill 参数化（带参数的 skill）
- Skill 版本管理
- 热加载（修改 SKILL.md 后无需重启）
- Skill 引用资源文件（脚本、附加 markdown）
- Skill 之间的依赖/组合
- Skill 的 LLM 自动生成

这些可以在 demo07+ 或 README "扩展方向" 小节中提及，但本次实现不做。

## 17. 决策记录（澄清问答）

| # | 问题 | 决策 |
|---|---|---|
| Q1 | Skill 的语义 | Claude Code 风格（元数据索引 + 按需加载） |
| Q2 | 发现与加载策略 | 元数据预加载 + LLM 自主调 `load_skill` |
| Q3 | 文件组织形式 | 单文件 SKILL.md（YAML frontmatter） |
| Q4 | 示例 skill | code-review / translate-zh-en / summarize-long-text |
| Q5 | 基线 | demo05（层累式扩展） |
| Q6 | `load_skill` 实现 | 内置 Tool，与 ExecTool 并列 |
| Q7 | 加载后生命周期 | 作为普通 tool 消息留 history，不特殊处理 |
| Q8 | GUI 呈现 | 双侧边栏 + 对话流加载气泡 |
| Q9/Q10 | 文件位置与首启 | `~/.afs/skills/`，首启自动从 classpath 拷贝示例 |
| Q11 | 加载状态作用域 | 按会话（从 history 推导） |
| Q12 | 重复加载 | 老实返回，不特殊处理 |
| Q13 | 不存在的 skill | 友好错误 + 可用清单 |
| Q14 | 发现时机 | 启动时扫一次缓存；改 skill 需重启 |

## 18. 后续步骤

设计审阅通过后，进入 writing-plans 阶段，产出按文件粒度、可分步执行的实现计划。
