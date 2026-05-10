# Demo 06 — Skills

> 在 demo05 基础上引入 **Claude Code 风格的 Skills 机制**：
> 元数据预加载 + LLM 自主按需加载 skill 完整指令。

## 核心命题

**Skill ≠ Tool**：
- Tool 是 Agent 做事的能力（`exec("ls")` 列目录）
- Skill 是 Agent 做事的指令包（"code-review 用什么结构回复"）

Skill 以 Markdown 文件形式存放在 `~/.afs/skills/<name>/SKILL.md`。
启动时 Agent 只把每个 skill 的 name + description 注入 system prompt（"菜单"）；
LLM 判断相关时自主调用内置工具 `load_skill(name)` 加载完整正文。

## 三个内置示例

首次启动时自动从模块 resources 拷贝到 `~/.afs/skills/`：

| Skill | 用途 |
|---|---|
| code-review | 结构化代码审查（总体评价 / 问题 / 建议 / 亮点） |
| translate-zh-en | 中英互译，保持专业术语一致 |
| summarize-long-text | 长文摘要：TL;DR + 要点 |

## 运行

```bash
export OPENAI_API_KEY=sk-xxxxx
export OPENAI_BASE_URL=https://api.openai.com/v1   # 可选
export OPENAI_MODEL=gpt-4o                          # 可选

cd demo06-skill-support
mvn compile exec:java
```

## GUI 布局

```
┌─────────────┬──────────────────────────┬─────────────┐
│ 会话侧边栏  │ 聊天区（含 🧩 加载气泡）│ Skills 面板 │
│  （左）     │  （中央）                │  （右）     │
└─────────────┴──────────────────────────┴─────────────┘
```

右侧 Skills 面板展示所有 skill 及"已加载 □ / ☑"徽章（按会话作用域）。

## 用户自定义 skill

在 `~/.afs/skills/` 下新建目录，放入 `SKILL.md`：

```markdown
---
name: my-skill
description: 这是什么 skill，什么时候触发（LLM 根据此描述判断）
---

# 我的 Skill

正文：告诉 LLM 做这件事时应该遵循的指令、结构、风格……
```

重启 demo06 即可生效。

## 手动验证清单

1. **首次启动**：`rm -rf ~/.afs/skills` 后启动 → 查看日志有 "已拷贝内置 skill: ..." 与 "已发现 3 个 skill"
2. **菜单注入**：发送 "你有哪些 skill？" → LLM 会列出三个
3. **触发加载**：发送代码 review 请求，如 `帮我 review 这段代码：def add(a,b): return a-b` → 观察：
   - 🧩 已加载技能气泡（取代了原本的"工具调用 + 工具结果"双气泡）
   - 右侧 code-review 徽章变 ☑
   - LLM 回复符合 SKILL.md 中"总体评价 / 问题 / 建议 / 亮点"结构
4. **不触发**：发送 "今天天气怎么样" → LLM 不会乱加载 skill
5. **会话切换徽章刷新**：在会话 A 加载过后，新建会话 B → ☑ 消失；切回 A → ☑ 恢复
6. **错误处理**：编辑一个 SKILL.md 删掉 `name:` 行 → 重启 → 日志 warn、菜单里少了这一项
7. **不存在的 skill**：通过对话让 LLM 调用不存在的 skill → 工具返回"错误：未找到 ... 可用的 skill 有：..."
8. **重复加载**：一次会话中发两次 code review 请求 → 两次都老实加载，不特殊去重

## 与前置 demo 的关系

| Demo | 关注点 | 与 Skill 的区别 |
|---|---|---|
| demo04 短期记忆 | 对话压缩，LLM 透明 | 机制在后台，不影响 LLM 决策风格 |
| demo05 长期记忆 | 跨会话知识检索，自动注入 | 知识是"事实"，skill 是"做事方式" |
| **demo06 Skills** | **LLM 自主决定加载哪些指令包** | **改变 LLM 的行为风格和工作流程** |

## 扩展方向（本 demo 不做）

- Skill 启用/禁用开关
- Skill 参数化
- Skill 目录下的辅助资源（脚本、附加 markdown）
- Skill 热加载（修改 SKILL.md 无需重启）
- Skill 之间的依赖与组合
