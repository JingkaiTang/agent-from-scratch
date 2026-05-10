# demo06 — Skill 支持实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 demo05 基础上新增 `demo06-skill-support` 模块，实现 Claude Code 风格的 Skills 机制——元数据预加载 + LLM 自主 `load_skill` 按需加载。

**Architecture:** 拷贝 demo05 作为基线（包名改为 `demo06`），在其上新增一个独立的 `skill` 包（`SkillMeta` / `SkillLoader` / `SkillRegistry` / `LoadSkillTool`）、一个 `AgentCallback.onSkillLoad` 事件、一个右侧 `SkillSidebar` UI 面板，以及在 `AgentService` 中完成 Skills 子系统的组装。skill 文件放在 `~/.afs/skills/<name>/SKILL.md`，首次启动从 classpath `resources/skills/` 拷贝 3 个内置示例。

**Tech Stack:** Java 25 · Maven · JavaFX 21 · Jackson（已有）· OkHttp（已有）· SLF4J + Logback（已有）—— 不引入任何新依赖。

**测试策略：** 遵循设计文档 §13 —— 延续 demo 系列传统，手动验证为主，不引 JUnit。每个任务末尾有"编译 / 手动验证 / commit"三步收尾。

**关联文档：**
- Spec：`docs/superpowers/specs/2026-05-10-demo06-skill-support-design.md`
- 基线代码：`demo05-long-term-memory/src/main/java/com/github/agent/demo05/`

---

## 文件结构

### 将创建的文件

```
demo06-skill-support/
├── pom.xml                                                     # 模块 pom（基本拷贝自 demo05，仅改 artifactId / mainClass）
├── README.md                                                   # 手动验证清单 + 使用说明
└── src/main/
    ├── java/com/github/agent/demo06/
    │   ├── Demo06App.java                                      # 入口（拷贝自 Demo05App，改 title）
    │   ├── Launcher.java                                       # 拷贝自 demo05 Launcher
    │   ├── skill/                                              # ⭐ 全新包
    │   │   ├── SkillMeta.java
    │   │   ├── SkillLoader.java
    │   │   ├── SkillRegistry.java
    │   │   └── LoadSkillTool.java
    │   ├── ui/SkillSidebar.java                                # ⭐ 新建
    │   ├── ui/SkillCell.java                                   # ⭐ 新建
    │   ├── core/                                               # 基本复用，AgentCallback.java 追加 onSkillLoad
    │   ├── agent/                                              # AgentService/AgentConfig 小改
    │   ├── ui/                                                 # ChatWindow/MessageBubble 小改
    │   └── llm/ memory/ longmem/ session/ tool/ model/ config/  # 完全复用（仅包名改）
    └── resources/
        ├── logback.xml / style.css / icon*.png                 # 复用
        └── skills/                                             # ⭐ 全新目录
            ├── code-review/SKILL.md
            ├── translate-zh-en/SKILL.md
            └── summarize-long-text/SKILL.md
```

### 将修改的文件

- `pom.xml`（根）：`<modules>` 追加 `demo06-skill-support`
- `README.md`（根）：学习路线表把 demo06 标为 ✅

---

## Phase 0 —— 模块脚手架（基于 demo05 克隆）

### Task 0.1: 创建 demo06 模块目录并克隆 demo05 源码

**Files:**
- Create: `demo06-skill-support/pom.xml`
- Create: `demo06-skill-support/src/...`（拷贝自 demo05）

- [ ] **Step 1: 创建模块目录并拷贝 demo05 所有源码与资源**

Run:

```bash
cp -r demo05-long-term-memory demo06-skill-support
# 清理 build 产物
rm -rf demo06-skill-support/target
# 删除 demo05 特有的 README（后续会用 demo06 自己的）
rm -f demo06-skill-support/README.md
```

Expected: 目录已创建，`demo06-skill-support/src/main/java/com/github/agent/demo05/` 存在（待重命名）。

- [ ] **Step 2: 把所有 Java 源码包名 demo05 → demo06**

Run:

```bash
# 重命名目录：com/github/agent/demo05 → com/github/agent/demo06
mv demo06-skill-support/src/main/java/com/github/agent/demo05 \
   demo06-skill-support/src/main/java/com/github/agent/demo06
# 把所有 .java 文件内的 "com.github.agent.demo05" 字符串替换成 "com.github.agent.demo06"
find demo06-skill-support/src/main/java -name "*.java" -exec \
    sed -i '' 's|com\.github\.agent\.demo05|com.github.agent.demo06|g' {} +
```

Expected: `grep -rn "demo05" demo06-skill-support/src/main/java/` 无输出。

- [ ] **Step 3: 把主类 Demo05App.java 重命名为 Demo06App.java 并改类名**

Run:

```bash
mv demo06-skill-support/src/main/java/com/github/agent/demo06/Demo05App.java \
   demo06-skill-support/src/main/java/com/github/agent/demo06/Demo06App.java
sed -i '' 's|Demo05App|Demo06App|g' \
   demo06-skill-support/src/main/java/com/github/agent/demo06/Demo06App.java \
   demo06-skill-support/src/main/java/com/github/agent/demo06/Launcher.java
# 改窗口标题（从"长期记忆"改为"Skills"）
sed -i '' 's|Agent From Scratch - 长期记忆|Agent From Scratch - Skills|g' \
   demo06-skill-support/src/main/java/com/github/agent/demo06/Demo06App.java
sed -i '' 's|从零手搓 AI Agent —— 长期记忆|从零手搓 AI Agent —— Skills|g' \
   demo06-skill-support/src/main/java/com/github/agent/demo06/ui/ChatWindow.java
```

Expected: `grep -rn "Demo05App" demo06-skill-support/` 无输出。

- [ ] **Step 4: 更新 demo06 模块 pom.xml 的 artifactId/name 和 mainClass**

Edit `demo06-skill-support/pom.xml`:

将：
```xml
<artifactId>demo05-long-term-memory</artifactId>
<name>Demo 05 - Long-Term Memory</name>
<description>为 Agent 增加跨会话的长期记忆：...</description>
```
改为：
```xml
<artifactId>demo06-skill-support</artifactId>
<name>Demo 06 - Skill Support</name>
<description>在 demo05 基础上引入 Claude Code 风格的 Skills 机制：元数据预加载 + LLM 自主按需加载 skill 完整指令</description>
```

将两处 `com.github.agent.demo05.Launcher` / `com.github.agent.demo05.Demo05App` 改为 `com.github.agent.demo06.Launcher` / `com.github.agent.demo06.Demo06App`。

- [ ] **Step 5: 把 demo06 注册到根 pom 的 modules 列表**

Edit `pom.xml`（根），在 `<modules>` 节点追加：

```xml
<module>demo06-skill-support</module>
```

完整块：

```xml
<modules>
    <module>demo00-basic-agent</module>
    <module>demo01-computer-use</module>
    <module>demo02-gui-agent</module>
    <module>demo03-session-management</module>
    <module>demo04-memory-management</module>
    <module>demo05-long-term-memory</module>
    <module>demo06-skill-support</module>
</modules>
```

- [ ] **Step 6: 编译验证**

Run: `mvn -pl demo06-skill-support -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add demo06-skill-support/ pom.xml
git commit -m "$(cat <<'EOF'
feat(demo06): 初始化 demo06 模块，克隆自 demo05

仅做包名 demo05 → demo06、主类 Demo06App 重命名、pom 字段调整，
业务逻辑与 demo05 完全一致。为后续 Skills 机制的增量改造做基线。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 1 —— Skills 子系统核心

### Task 1.1: SkillMeta（不可变 POJO）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillMeta.java`

- [ ] **Step 1: 新建 SkillMeta.java**

写入内容：

```java
package com.github.agent.demo06.skill;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Skill 元数据（不可变 POJO）。
 * <p>
 * 对应 SKILL.md 文件的 YAML frontmatter 解析结果。
 * 刻意不持有 Markdown 正文 —— 正文每次通过 {@link SkillRegistry#readBody(String)}
 * 按需从磁盘读取，体现 Skills 机制"元数据 vs 正文分离"的核心教学点。
 */
public final class SkillMeta {

    /** skill 的唯一标识，来自 frontmatter.name；按惯例与目录名一致（不强制校验） */
    private final String name;

    /** skill 的简短描述，来自 frontmatter.description；LLM 判断是否加载的依据 */
    private final String description;

    /** SKILL.md 的绝对路径，用于按需读取正文 */
    private final Path filePath;

    public SkillMeta(String name, String description, Path filePath) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Path getFilePath() { return filePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillMeta other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() {
        return "SkillMeta{name='" + name + "', description='" + description + "', filePath=" + filePath + "}";
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillMeta.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 SkillMeta —— skill 元数据不可变 POJO

仅持有 name/description/filePath，不持有正文；
正文按需读取，体现"元数据 vs 正文分离"核心教学点。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.2: SkillLoader（首启拷贝 + 扫描解析）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillLoader.java`

- [ ] **Step 1: 新建 SkillLoader.java**

写入内容：

```java
package com.github.agent.demo06.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 扫描 {@code ~/.afs/skills/} 加载所有 skill 的元数据。
 * <p>
 * 首次运行（目录不存在）时，自动从 classpath {@code /skills/} 拷贝内置示例。
 * 每个 skill 占一个子目录，内含 {@code SKILL.md} 文件，文件首部为 YAML frontmatter
 * （仅识别 {@code name} 和 {@code description} 两个字段）。
 * <p>
 * 解析失败的 skill 会被跳过并 warn 日志，不让启动崩溃。
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    /** skill 根目录：~/.afs/skills/ */
    private static final Path SKILLS_DIR = Path.of(
            System.getProperty("user.home"), ".afs", "skills");

    /** 内置示例 skill 的名字（对应 classpath 下 /skills/<name>/SKILL.md） */
    private static final List<String> BUILT_IN_SKILLS = List.of(
            "code-review", "translate-zh-en", "summarize-long-text");

    /**
     * 首启拷贝 + 扫描 + 解析，返回所有合法 skill 的元数据列表。
     */
    public List<SkillMeta> loadAll() {
        bootstrapDefaults();

        List<SkillMeta> result = new ArrayList<>();
        if (!Files.isDirectory(SKILLS_DIR)) {
            log.warn("skills 目录不存在或不可访问：{}", SKILLS_DIR);
            return result;
        }

        try (Stream<Path> entries = Files.list(SKILLS_DIR)) {
            for (Path entry : entries.sorted().toList()) {
                if (!Files.isDirectory(entry)) continue;
                Path skillMd = entry.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    log.warn("跳过 {}：未找到 SKILL.md", entry.getFileName());
                    continue;
                }
                try {
                    SkillMeta meta = parseFrontmatter(skillMd);
                    if (meta == null) continue;
                    result.add(meta);
                } catch (Exception e) {
                    log.warn("解析 skill '{}' 失败：{}", entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("扫描 skills 目录失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 首次启动：如果 {@code ~/.afs/skills/} 不存在，创建目录并从 classpath 拷贝内置示例。
     * 如果目录已存在（即使为空），不做任何事，避免覆盖用户的"我故意删光"决定。
     */
    private void bootstrapDefaults() {
        if (Files.exists(SKILLS_DIR)) {
            return;
        }
        try {
            Files.createDirectories(SKILLS_DIR);
            log.info("首次启动，已创建 skills 目录：{}", SKILLS_DIR);
            for (String name : BUILT_IN_SKILLS) {
                copyBuiltIn(name);
            }
        } catch (IOException e) {
            log.error("创建 skills 目录或拷贝内置示例失败（启动继续）：{}", e.getMessage());
        }
    }

    /** 从 classpath 拷贝 /skills/&lt;name&gt;/SKILL.md 到 ~/.afs/skills/&lt;name&gt;/SKILL.md */
    private void copyBuiltIn(String skillName) {
        String resource = "/skills/" + skillName + "/SKILL.md";
        try (InputStream in = SkillLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("classpath 内置 skill 资源不存在：{}", resource);
                return;
            }
            Path target = SKILLS_DIR.resolve(skillName).resolve("SKILL.md");
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("已拷贝内置 skill: {} → {}", skillName, target);
        } catch (IOException e) {
            log.warn("拷贝内置 skill '{}' 失败：{}", skillName, e.getMessage());
        }
    }

    /**
     * 解析单个 SKILL.md 的 YAML frontmatter。
     * <p>
     * 格式要求：
     * <pre>
     * ---
     * name: xxx
     * description: xxx
     * ---
     *
     * # 正文 Markdown...
     * </pre>
     * 仅识别 name 和 description 两个字段，其它忽略。缺失必填字段则返回 null 并 warn。
     */
    SkillMeta parseFrontmatter(Path skillMd) throws IOException {
        String name = null;
        String description = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(skillMd), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.trim().equals("---")) {
                log.warn("跳过 {}：首行不是 '---'", skillMd);
                return null;
            }
            boolean closed = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    closed = true;
                    break;
                }
                int colonIdx = line.indexOf(':');
                if (colonIdx < 0) continue;
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (key.equals("name")) name = value;
                else if (key.equals("description")) description = value;
            }
            if (!closed) {
                log.warn("跳过 {}：frontmatter 未闭合（找不到第二个 '---'）", skillMd);
                return null;
            }
        }
        if (name == null || name.isBlank()) {
            log.warn("跳过 {}：frontmatter 缺少必填字段 'name'", skillMd);
            return null;
        }
        if (description == null || description.isBlank()) {
            log.warn("跳过 {}：frontmatter 缺少必填字段 'description'", skillMd);
            return null;
        }
        return new SkillMeta(name, description, skillMd);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillLoader.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 SkillLoader —— 首启拷贝 + 扫描 + frontmatter 解析

首次启动时自动从 classpath /skills/ 拷贝 3 个内置示例到 ~/.afs/skills/。
每次启动扫描 ~/.afs/skills/<name>/SKILL.md，解析 YAML frontmatter（name/description）。
解析失败的 skill 跳过并 warn，不影响启动。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.3: SkillRegistry（缓存 + 正文读取 + 菜单生成）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillRegistry.java`

- [ ] **Step 1: 新建 SkillRegistry.java**

写入内容：

```java
package com.github.agent.demo06.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills 进程内缓存 + system prompt 菜单生成 + 正文按需读取。
 * <p>
 * 启动时由 {@link SkillLoader#loadAll()} 填充；运行时对外提供三件事：
 * <ul>
 *   <li>{@link #list()} —— 供 UI 遍历所有 skill</li>
 *   <li>{@link #get(String)} / {@link #readBody(String)} —— 供 LoadSkillTool 查找并按需读正文</li>
 *   <li>{@link #toPromptMenu()} —— 生成注入 system prompt 的菜单文本</li>
 * </ul>
 */
public class SkillRegistry {

    /** 按注册顺序保留（LinkedHashMap） */
    private final Map<String, SkillMeta> byName = new LinkedHashMap<>();

    /**
     * 注册一批 skill。重名后注册者覆盖前者（由 SkillLoader 决定扫描顺序）。
     */
    public void register(List<SkillMeta> metas) {
        for (SkillMeta m : metas) {
            if (byName.containsKey(m.getName())) {
                // 重名警告由调用方或日志里体现；这里允许覆盖以保持确定性
            }
            byName.put(m.getName(), m);
        }
    }

    public List<SkillMeta> list() {
        return new ArrayList<>(byName.values());
    }

    public SkillMeta get(String name) {
        return byName.get(name);
    }

    /**
     * 按需读取指定 skill 的 SKILL.md 完整内容（包括 frontmatter 部分）。
     * 调用方（如 LoadSkillTool）负责异常处理。
     */
    public String readBody(String name) throws IOException {
        SkillMeta meta = byName.get(name);
        if (meta == null) {
            throw new IOException("skill not found: " + name);
        }
        return Files.readString(meta.getFilePath(), StandardCharsets.UTF_8);
    }

    /**
     * 生成注入 system prompt 尾部的 skill 菜单文本。
     * <p>
     * 如果当前没有任何可用 skill，返回空串（调用方判断后决定是否追加）。
     */
    public String toPromptMenu() {
        if (byName.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你可以按需加载以下 skill 来获得专门的指令和工作流程：\n\n");
        for (SkillMeta m : byName.values()) {
            sb.append("- ").append(m.getName()).append(": ").append(m.getDescription()).append("\n");
        }
        sb.append("\n");
        sb.append("当你判断某个 skill 与当前任务相关时，调用工具 load_skill(name) 加载它的完整指令。\n");
        sb.append("加载后请严格按指令执行。不相关时无需加载，直接回复即可。");
        return sb.toString();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/skill/SkillRegistry.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 SkillRegistry —— 缓存元数据 + 生成 Prompt 菜单 + 按需读正文

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.4: AgentCallback 新增 onSkillLoad 事件

**Files:**
- Modify: `demo06-skill-support/src/main/java/com/github/agent/demo06/core/AgentCallback.java`

- [ ] **Step 1: 在 AgentCallback 接口尾部追加 default 方法**

Edit `demo06-skill-support/src/main/java/com/github/agent/demo06/core/AgentCallback.java`，在最后一个 `}` 前插入：

```java

    // ========== Skills 回调 ==========

    /**
     * 当 LLM 通过 load_skill 工具加载某个 skill 后触发。
     * <p>
     * 此回调用于 GUI 在对话流中插入"已加载技能"气泡、
     * 以及刷新右侧 SkillSidebar 中对应 skill 的"已加载"徽章。
     *
     * @param skillName   被加载的 skill 名
     * @param description skill 的简短描述（来自 frontmatter，便于气泡展示）
     */
    default void onSkillLoad(String skillName, String description) {}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/core/AgentCallback.java
git commit -m "$(cat <<'EOF'
feat(demo06): AgentCallback 追加 onSkillLoad 事件（default 空实现）

风格对齐 onMemoryCompaction / onLongTermMemoryQuery。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.5: LoadSkillTool（内置工具）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/skill/LoadSkillTool.java`

- [ ] **Step 1: 新建 LoadSkillTool.java**

写入内容：

```java
package com.github.agent.demo06.skill;

import com.github.agent.demo06.core.AgentCallback;
import com.github.agent.demo06.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内置工具 {@code load_skill} —— 让 LLM 按需加载某个 skill 的完整指令到对话上下文。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>LLM 在 system prompt 的"Skills 菜单"里看到 skill 清单</li>
 *   <li>判断某个 skill 相关时，调用 load_skill(name)</li>
 *   <li>本工具从 {@link SkillRegistry} 读出正文返回给 LLM</li>
 *   <li>正文作为 tool 消息留在 conversationHistory 里，LLM 后续推理按正文指令执行</li>
 * </ol>
 * <p>
 * 回调注入：构造时无 callback；由 {@code AgentService.setCallback()} 时回填，
 * 因为 AgentService 的初始化顺序是 [Tool 注册 → AgentLoop → setCallback]。
 */
public class LoadSkillTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);

    private final SkillRegistry registry;

    /** 由 AgentService.setCallback 时回填；可能为 null */
    private AgentCallback callback;

    public LoadSkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    public void setCallback(AgentCallback callback) {
        this.callback = callback;
    }

    @Override
    public String name() {
        return "load_skill";
    }

    @Override
    public String description() {
        return "加载指定 skill 的完整指令到对话上下文。调用前你已经在 system prompt 的 Skills 菜单里看过 skill 清单；"
             + "当某个 skill 与当前任务相关时用它；不相关时不要调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "要加载的 skill 名（来自 system prompt 中的 Skills 菜单）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", nameProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("name"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object rawName = arguments == null ? null : arguments.get("name");
        if (!(rawName instanceof String nameStr) || nameStr.isBlank()) {
            return "错误：缺少必需参数 'name'。";
        }
        String skillName = nameStr.trim();

        SkillMeta meta = registry.get(skillName);
        if (meta == null) {
            String available = registry.list().stream()
                    .map(SkillMeta::getName)
                    .collect(Collectors.joining(", "));
            String listSuffix = available.isEmpty() ? "（当前无可用 skill）" : available;
            return "错误：未找到名为 '" + skillName + "' 的 skill。\n可用的 skill 有：" + listSuffix + "。";
        }

        try {
            String body = registry.readBody(skillName);
            log.info("✅ 加载 skill: {} ({} 字符)", skillName, body.length());
            if (callback != null) {
                try {
                    callback.onSkillLoad(skillName, meta.getDescription());
                } catch (Exception cbErr) {
                    log.warn("onSkillLoad 回调异常（忽略）：{}", cbErr.getMessage());
                }
            }
            return body;
        } catch (IOException e) {
            return "错误：读取 skill '" + skillName + "' 失败：" + e.getMessage();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/skill/LoadSkillTool.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 LoadSkillTool —— 作为 Tool 注册到 ToolRegistry

LLM 通过此工具按需加载 skill 正文到对话上下文。
错误（未知 skill / IO 失败）统一返回字符串给 LLM 而不抛异常，
保持 demo05 一贯的"Tool 永不崩溃"哲学。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2 —— 集成到 AgentService

### Task 2.1: AgentService 初始化 Skills 子系统 + 拼接 System Prompt

**Files:**
- Modify: `demo06-skill-support/src/main/java/com/github/agent/demo06/agent/AgentService.java`

- [ ] **Step 1: 在 import 区新增 skill 包 import**

在 `AgentService.java` 的 import 区（与其它 `com.github.agent.demo06.*` imports 相邻）添加：

```java
import com.github.agent.demo06.skill.LoadSkillTool;
import com.github.agent.demo06.skill.SkillLoader;
import com.github.agent.demo06.skill.SkillMeta;
import com.github.agent.demo06.skill.SkillRegistry;
import java.util.stream.Collectors;
```

- [ ] **Step 2: 新增两个字段**

在 `AgentService` 类体内（已有 `longTermMemoryManager` 字段附近）添加：

```java
/** Skill 注册表 —— 启动时扫描 ~/.afs/skills/ 填充；供 GUI 与 LoadSkillTool 查询 */
private SkillRegistry skillRegistry;

/** load_skill 工具引用 —— setCallback 时回填 callback */
private LoadSkillTool loadSkillTool;
```

- [ ] **Step 3: 修改 initialize() —— 在 ToolRegistry 注册与 AgentLoop 构造之间插入 Skills 初始化**

定位到 `initialize()` 方法中：

```java
// 注册工具
ToolRegistry toolRegistry = new ToolRegistry();
toolRegistry.register(new ExecTool());
log.info("已注册工具: {}", toolRegistry.all().stream().map(t -> t.name()).toList());

// 创建 Agent
agentLoop = new AgentLoop(llmClient, toolRegistry, AgentConfig.SYSTEM_PROMPT, config.getMaxSteps());
```

替换为：

```java
// ⭐ 初始化 Skills 子系统
SkillLoader skillLoader = new SkillLoader();
List<SkillMeta> skillMetas = skillLoader.loadAll();
this.skillRegistry = new SkillRegistry();
this.skillRegistry.register(skillMetas);
log.info("已发现 {} 个 skill: {}", skillMetas.size(),
        skillMetas.stream().map(SkillMeta::getName).collect(Collectors.toList()));

// 注册工具（ExecTool + LoadSkillTool）
ToolRegistry toolRegistry = new ToolRegistry();
toolRegistry.register(new ExecTool());
this.loadSkillTool = new LoadSkillTool(this.skillRegistry);
toolRegistry.register(this.loadSkillTool);
log.info("已注册工具: {}", toolRegistry.all().stream().map(t -> t.name()).toList());

// 拼接最终 system prompt（基础 prompt + Skills 菜单）
String skillMenu = this.skillRegistry.toPromptMenu();
String finalSystemPrompt = skillMenu.isEmpty()
        ? AgentConfig.SYSTEM_PROMPT
        : AgentConfig.SYSTEM_PROMPT + "\n\n" + skillMenu;

// 创建 Agent
agentLoop = new AgentLoop(llmClient, toolRegistry, finalSystemPrompt, config.getMaxSteps());
```

- [ ] **Step 4: 修改 setCallback() 同步给 LoadSkillTool**

定位到：

```java
public void setCallback(AgentCallback callback) {
    if (agentLoop != null) {
        agentLoop.setCallback(callback);
    }
}
```

改为：

```java
public void setCallback(AgentCallback callback) {
    if (agentLoop != null) {
        agentLoop.setCallback(callback);
    }
    if (loadSkillTool != null) {
        loadSkillTool.setCallback(callback);
    }
}
```

- [ ] **Step 5: 新增 getter 供 GUI 查询**

在 `AgentService` 类尾部（`shutdown()` 方法前）插入：

```java
/**
 * 获取 Skill 注册表 —— 供右侧 SkillSidebar 遍历所有 skill 渲染列表。
 *
 * @return 注册表实例；若尚未初始化（不应发生）则返回 null
 */
public SkillRegistry getSkillRegistry() {
    return skillRegistry;
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: 手动烟雾测试（首启拷贝）**

```bash
# 备份已有 skills 目录（如果存在）
[ -d ~/.afs/skills ] && mv ~/.afs/skills ~/.afs/skills.bak || true

# 运行（看到菜单打印日志即可退出，不必完整对话）
cd demo06-skill-support
mvn exec:java 2>&1 | head -80
# Ctrl+C 退出 GUI 或直接关窗口
```

Expected 日志片段：

```
已拷贝内置 skill: code-review → /Users/.../.afs/skills/code-review/SKILL.md
已拷贝内置 skill: translate-zh-en → /Users/.../.afs/skills/translate-zh-en/SKILL.md
已拷贝内置 skill: summarize-long-text → /Users/.../.afs/skills/summarize-long-text/SKILL.md
已发现 3 个 skill: [code-review, translate-zh-en, summarize-long-text]
已注册工具: [exec, load_skill]
```

> 注意：此时 Phase 4 还没写内置 SKILL.md 资源，Step 7 可能打印"classpath 内置 skill 资源不存在"warn。这**正常**，不阻塞；Phase 4 写完后重跑即可完整验证。如果想先跑过，可以**跳过 Step 7 等 Phase 4 完成后再回来验证**。

```bash
# 恢复原 skills 目录
[ -d ~/.afs/skills.bak ] && { rm -rf ~/.afs/skills; mv ~/.afs/skills.bak ~/.afs/skills; } || true
```

- [ ] **Step 8: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/agent/AgentService.java
git commit -m "$(cat <<'EOF'
feat(demo06): AgentService 集成 Skills 子系统

- initialize() 扫描 ~/.afs/skills/ 构建 SkillRegistry
- LoadSkillTool 与 ExecTool 一起注册到 ToolRegistry
- system prompt = 原 SYSTEM_PROMPT + Skills 菜单
- setCallback 同步给 LoadSkillTool（延迟注入 callback）
- 新增 getSkillRegistry() 供 GUI 使用

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3 —— GUI 层

### Task 3.1: MessageBubble 新增 SKILL_LOAD 类型（蓝绿色气泡）

**Files:**
- Modify: `demo06-skill-support/src/main/java/com/github/agent/demo06/ui/MessageBubble.java`

- [ ] **Step 1: 在 Type 枚举中追加 SKILL_LOAD**

定位到：

```java
public enum Type {
    USER,
    AGENT,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR,
    STATUS,
    MEMORY
}
```

改为：

```java
public enum Type {
    USER,
    AGENT,
    TOOL_CALL,
    TOOL_RESULT,
    ERROR,
    STATUS,
    MEMORY,
    SKILL_LOAD       // ⭐ demo06 新增：已加载 skill 气泡
}
```

- [ ] **Step 2: 在 switch(type) 分支里追加 SKILL_LOAD 样式**

定位到 `case MEMORY -> { ... }` 之后、`}` 闭合 switch 之前，添加：

```java
case SKILL_LOAD -> {
    bubble.setStyle("""
            -fx-background-color: #CFFAFE;
            -fx-background-radius: 12;
            -fx-border-color: #0891B2;
            -fx-border-radius: 12;
            -fx-border-width: 1;
            """);
    bubble.setMaxWidth(600);
    messageArea.setMaxWidth(570);
    messageArea.getStyleClass().add("msg-skill-load");
    messageArea.setStyle("-fx-font-size: 12px;");
    roleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #155E75;");
    setAlignment(Pos.CENTER);
}
```

- [ ] **Step 3: 让 SKILL_LOAD 和 MEMORY / STATUS 一样不显示复制按钮**

定位到：

```java
if (type != Type.STATUS && type != Type.MEMORY) {
    copyButton = createCopyButton(content, type);
    ...
}
```

改为：

```java
if (type != Type.STATUS && type != Type.MEMORY && type != Type.SKILL_LOAD) {
    copyButton = createCopyButton(content, type);
    ...
}
```

- [ ] **Step 4: getRoleText() 新增 SKILL_LOAD 分支**

定位到：

```java
private String getRoleText(Type type) {
    return switch (type) {
        case USER -> "You";
        case AGENT -> "🤖 Agent";
        case TOOL_CALL -> "🔧 工具调用";
        case TOOL_RESULT -> "📋 工具结果";
        case ERROR -> "❌ 错误";
        case MEMORY -> "🧠 记忆压缩";
        case STATUS -> "";
    };
}
```

改为：

```java
private String getRoleText(Type type) {
    return switch (type) {
        case USER -> "You";
        case AGENT -> "🤖 Agent";
        case TOOL_CALL -> "🔧 工具调用";
        case TOOL_RESULT -> "📋 工具结果";
        case ERROR -> "❌ 错误";
        case MEMORY -> "🧠 记忆压缩";
        case SKILL_LOAD -> "🧩 已加载技能";
        case STATUS -> "";
    };
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/ui/MessageBubble.java
git commit -m "$(cat <<'EOF'
feat(demo06): MessageBubble 新增 SKILL_LOAD 类型（蓝绿色气泡）

样式风格对齐 MEMORY 类型：居中、无复制按钮、较小字号。
角色标签 "🧩 已加载技能"。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.2: 新增 SkillCell 组件（侧边栏中单个 skill 的视图）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/ui/SkillCell.java`

- [ ] **Step 1: 新建 SkillCell.java**

写入内容：

```java
package com.github.agent.demo06.ui;

import com.github.agent.demo06.skill.SkillMeta;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * SkillSidebar 内的单个 skill 视图 cell。
 * <p>
 * 展示内容：
 * <ul>
 *   <li>左上角徽章（□ 未加载 / ☑ 已加载）</li>
 *   <li>skill name（加粗）</li>
 *   <li>description（灰色小字，自动换行）</li>
 * </ul>
 * 不响应点击——Skills 机制强调"由 LLM 自主决定加载"，用户不能手动触发加载。
 */
public class SkillCell extends VBox {

    private final Label badgeLabel;
    private final String skillName;

    public SkillCell(SkillMeta meta) {
        this.skillName = meta.getName();

        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(4);
        setStyle("""
                -fx-background-color: #FFFFFF;
                -fx-border-color: #E5E7EB;
                -fx-border-width: 0 0 1 0;
                """);

        // 顶行：徽章 + name
        badgeLabel = new Label("□");
        badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");

        Label nameLabel = new Label(meta.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1F2937;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(6, badgeLabel, nameLabel, spacer);

        // 描述行
        Label descLabel = new Label(meta.getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");
        descLabel.setMaxWidth(200);

        getChildren().addAll(topRow, descLabel);
    }

    public String getSkillName() {
        return skillName;
    }

    /**
     * 切换徽章显示（必须在 JavaFX Application Thread 上调用）。
     *
     * @param loaded 是否已加载
     */
    public void setLoaded(boolean loaded) {
        if (loaded) {
            badgeLabel.setText("☑");
            badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #0891B2; -fx-font-weight: bold;");
        } else {
            badgeLabel.setText("□");
            badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/ui/SkillCell.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 SkillCell —— SkillSidebar 内部的单个 skill 视图

徽章 □/☑ 切换加载状态；不响应点击（加载由 LLM 自主决定）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.3: 新增 SkillSidebar 组件（右侧面板）

**Files:**
- Create: `demo06-skill-support/src/main/java/com/github/agent/demo06/ui/SkillSidebar.java`

- [ ] **Step 1: 新建 SkillSidebar.java**

写入内容：

```java
package com.github.agent.demo06.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agent.demo06.agent.AgentService;
import com.github.agent.demo06.model.ChatMessage;
import com.github.agent.demo06.session.Session;
import com.github.agent.demo06.skill.SkillMeta;
import com.github.agent.demo06.skill.SkillRegistry;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聊天窗口右侧的 Skills 面板。
 * <p>
 * 职责：
 * <ul>
 *   <li>启动时遍历 {@link SkillRegistry} 为每个 skill 构建一个 {@link SkillCell}</li>
 *   <li>监听会话切换事件：重算当前会话的"已加载 skill 集合"并刷新所有徽章</li>
 *   <li>被 {@code ChatWindow.onSkillLoad} 回调转发通知：把对应 cell 的徽章改为 ☑</li>
 * </ul>
 * <p>
 * 已加载集合不持久化——纯函数式从当前会话的 {@link ChatMessage} 历史推导，
 * 贯彻设计文档中"skill = 对话历史的一部分"的心智模型。
 */
public class SkillSidebar extends VBox {

    private static final Logger log = LoggerFactory.getLogger(SkillSidebar.class);

    /** 侧边栏固定宽度 */
    private static final double SIDEBAR_WIDTH = 220;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentService agentService;

    /** 所有 skill cell 的索引（by skill name） */
    private final Map<String, SkillCell> cellByName = new HashMap<>();

    /** 当前会话已加载的 skill 集合（刷新徽章用） */
    private Set<String> loadedSkills = new HashSet<>();

    public SkillSidebar(AgentService agentService) {
        this.agentService = agentService;
        buildUI();
        bindEvents();
        // 初始加载：根据当前会话计算已加载集合
        Session current = agentService.getCurrentSession();
        if (current != null) {
            refreshLoadedFromSession(current);
        }
    }

    private void buildUI() {
        setPrefWidth(SIDEBAR_WIDTH);
        setMinWidth(SIDEBAR_WIDTH);
        setMaxWidth(SIDEBAR_WIDTH);
        setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 0 1;");

        // 标题
        Label title = new Label("🧩 Skills");
        title.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-text-fill: #333;
                -fx-padding: 16 12 12 12;
                """);

        // 列表容器
        VBox listBox = new VBox();
        listBox.setStyle("-fx-background-color: #FFFFFF;");

        SkillRegistry registry = agentService.getSkillRegistry();
        if (registry == null || registry.list().isEmpty()) {
            Label emptyLabel = new Label("(当前无可用 skill)");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF; -fx-padding: 20 12;");
            listBox.getChildren().add(emptyLabel);
        } else {
            for (SkillMeta meta : registry.list()) {
                SkillCell cell = new SkillCell(meta);
                cellByName.put(meta.getName(), cell);
                listBox.getChildren().add(cell);
            }
        }

        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(title, scrollPane);
    }

    private void bindEvents() {
        // 会话切换时重算已加载集合 + 刷新徽章
        agentService.addSessionChangeListener(new com.github.agent.demo06.session.SessionChangeListener() {
            @Override
            public void onSessionSwitched(Session newSession) {
                refreshLoadedFromSession(newSession);
            }

            @Override
            public void onSessionCreated(Session session) {
                refreshLoadedFromSession(session);
            }

            @Override
            public void onSessionDeleted(String sessionId) {
                Session current = agentService.getCurrentSession();
                if (current != null) {
                    refreshLoadedFromSession(current);
                }
            }

            @Override
            public void onSessionUpdated(Session session) {
                // 对标题更新/消息追加等不必重算（加载由 onSkillLoadEvent 驱动）
            }
        });
    }

    /**
     * 对外：当 LLM 通过 load_skill 成功加载某个 skill 时，
     * 由 ChatWindow 在 callback.onSkillLoad 里转发给本方法。
     * <p>
     * 必须在 JavaFX Application Thread 上调用。
     */
    public void onSkillLoadEvent(String skillName) {
        loadedSkills.add(skillName);
        SkillCell cell = cellByName.get(skillName);
        if (cell != null) {
            cell.setLoaded(true);
        }
    }

    /**
     * 扫描当前会话的历史，计算已加载集合并刷新所有徽章。
     */
    private void refreshLoadedFromSession(Session session) {
        Set<String> loaded = session == null
                ? new HashSet<>()
                : computeLoadedSkills(session.getMessages());
        Platform.runLater(() -> {
            this.loadedSkills = loaded;
            cellByName.forEach((n, cell) -> cell.setLoaded(loaded.contains(n)));
        });
    }

    /**
     * 从对话历史里推导"已成功加载过的 skill 名集合"。
     * <p>
     * 判定规则：
     * <ol>
     *   <li>遍历 assistant 消息中的 toolCalls，筛出 function.name == "load_skill" 的调用</li>
     *   <li>从 arguments JSON 中提取 name 字段</li>
     *   <li>通过 tool_call_id 配对其后的 tool 消息</li>
     *   <li>tool 消息内容不以 "错误：" 开头者视为成功加载</li>
     * </ol>
     */
    static Set<String> computeLoadedSkills(List<ChatMessage> history) {
        Set<String> loaded = new HashSet<>();
        if (history == null) return loaded;

        // 先收集所有 load_skill 的 tool_call: id → requested skill name
        Map<String, String> pendingByCallId = new HashMap<>();
        for (ChatMessage msg : history) {
            if (!"assistant".equals(msg.getRole())) continue;
            if (msg.getToolCalls() == null) continue;
            for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                if (tc.getFunction() == null) continue;
                if (!"load_skill".equals(tc.getFunction().getName())) continue;
                String args = tc.getFunction().getArguments();
                String skillName = extractSkillNameFromArgs(args);
                if (skillName != null) {
                    pendingByCallId.put(tc.getId(), skillName);
                }
            }
        }

        // 再遍历 tool 消息，按 tool_call_id 配对，判断是否成功
        for (ChatMessage msg : history) {
            if (!"tool".equals(msg.getRole())) continue;
            String callId = msg.getToolCallId();
            if (!pendingByCallId.containsKey(callId)) continue;
            String content = msg.getContent();
            if (content != null && !content.startsWith("错误：")) {
                loaded.add(pendingByCallId.get(callId));
            }
        }
        return loaded;
    }

    private static String extractSkillNameFromArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return null;
        try {
            Map<String, Object> map = MAPPER.readValue(argsJson, new TypeReference<>() {});
            Object v = map.get("name");
            return (v instanceof String s && !s.isBlank()) ? s.trim() : null;
        } catch (Exception e) {
            log.debug("解析 load_skill arguments 失败：{}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

> 如果失败提示 `SessionChangeListener` 的方法签名不匹配，先查看其实际接口：
> `cat demo06-skill-support/src/main/java/com/github/agent/demo06/session/SessionChangeListener.java` —— 把上面 `bindEvents()` 里的 `onSessionSwitched/Created/Deleted/Updated` 方法名调整为该接口真实声明的名字。demo05 目前的接口预期提供这四个方法（之一或等价方法），按实际签名匹配即可。

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/ui/SkillSidebar.java
git commit -m "$(cat <<'EOF'
feat(demo06): 新增 SkillSidebar —— 右侧 Skills 面板

- 启动时为每个 skill 构建 SkillCell
- 监听 SessionChangeListener 会话切换，重算已加载集合刷新徽章
- 对外 onSkillLoadEvent 接收 ChatWindow 转发的加载事件
- 已加载集合纯函数式从 conversationHistory 推导

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.4: ChatWindow 集成 SkillSidebar + onSkillLoad 回调

**Files:**
- Modify: `demo06-skill-support/src/main/java/com/github/agent/demo06/ui/ChatWindow.java`

- [ ] **Step 1: 新增 import 与字段**

在 `ChatWindow.java` 顶部 import 区追加：

```java
// 无需新 import，SkillSidebar 在同包内
```

在类字段区（`private SessionSidebar sessionSidebar;` 附近）追加：

```java
/** 右侧 Skills 面板 */
private SkillSidebar skillSidebar;
```

- [ ] **Step 2: 在 buildUI() 末尾创建并挂载 SkillSidebar**

定位到 `buildUI()` 方法末尾（`root.setBottom(inputBar);` 之后），追加：

```java
// --- 右侧：Skills 面板 ---
skillSidebar = new SkillSidebar(agentService);
root.setRight(skillSidebar);
```

- [ ] **Step 3: 在 bindAgentCallback() 中新增 onSkillLoad 处理**

定位到 `bindAgentCallback()` 里的 `new AgentCallback() { ... }` 内部，在最后一个 `onLongTermMemoryIngest` 或 `onLongTermMemoryQuery` 实现之后（闭合 `}` 之前）追加：

```java
@Override
public void onSkillLoad(String skillName, String description) {
    Platform.runLater(() -> {
        String info = "🧩 已加载技能：" + skillName + "\n" + description;
        addMessage(MessageBubble.Type.SKILL_LOAD, info);
        if (skillSidebar != null) {
            skillSidebar.onSkillLoadEvent(skillName);
        }
    });
}
```

- [ ] **Step 4: renderSessionMessages() 新增 skill 加载气泡还原**

定位到 `renderSessionMessages()` 方法（约 424 行），它目前按 role 渲染消息。需要在 `"tool"` 分支里判断这是不是一个 `load_skill` 的结果消息，如果是则渲染为 SKILL_LOAD 气泡。

当前代码形如：

```java
case "tool" -> addMessage(MessageBubble.Type.TOOL_RESULT, msg.getContent());
```

改为：

```java
case "tool" -> {
    // 判断这条 tool 消息是否是 load_skill 的成功返回；若是则渲染为 SKILL_LOAD 气泡
    String loadedSkill = findLoadSkillCallName(session, msg.getToolCallId());
    boolean isSuccess = msg.getContent() != null && !msg.getContent().startsWith("错误：");
    if (loadedSkill != null && isSuccess) {
        addMessage(MessageBubble.Type.SKILL_LOAD,
                "🧩 已加载技能：" + loadedSkill);
    } else {
        addMessage(MessageBubble.Type.TOOL_RESULT, msg.getContent());
    }
}
```

然后在 `ChatWindow` 类末尾新增一个静态 helper 方法：

```java
/**
 * 根据 tool_call_id 从 session 历史中找出对应 load_skill 调用的 skill 名。
 * 找不到则返回 null（意味着该 tool 消息不是 load_skill 的结果）。
 */
private static String findLoadSkillCallName(Session session, String toolCallId) {
    if (toolCallId == null) return null;
    for (ChatMessage m : session.getMessages()) {
        if (!"assistant".equals(m.getRole())) continue;
        if (m.getToolCalls() == null) continue;
        for (ChatMessage.ToolCall tc : m.getToolCalls()) {
            if (!toolCallId.equals(tc.getId())) continue;
            if (tc.getFunction() == null) continue;
            if (!"load_skill".equals(tc.getFunction().getName())) continue;
            // 从 arguments 里取 name
            String args = tc.getFunction().getArguments();
            if (args == null) return null;
            try {
                java.util.Map<String, Object> map = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(args, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                Object v = map.get("name");
                return (v instanceof String s && !s.isBlank()) ? s.trim() : null;
            } catch (Exception e) {
                return null;
            }
        }
    }
    return null;
}
```

（类体顶部或相关 import 处确保已经 import `ChatMessage`——demo05 原版已经 import 过。如果 `com.github.agent.demo06.model.ChatMessage` 还没 import，补上。）

- [ ] **Step 5: 同样修改 renderSessionMessages() 中对 assistant 的 toolCall 处理**

定位到：

```java
case "assistant" -> {
    if (msg.getContent() != null && !msg.getContent().isBlank()) {
        addMessage(MessageBubble.Type.AGENT, msg.getContent());
    }
    if (msg.getToolCalls() != null) {
        for (ChatMessage.ToolCall call : msg.getToolCalls()) {
            String callInfo = call.getFunction().getName() + "(" + call.getFunction().getArguments() + ")";
            addMessage(MessageBubble.Type.TOOL_CALL, callInfo);
        }
    }
}
```

**不需要特别改动**——`load_skill` 的调用正常以 `TOOL_CALL` 气泡展示即可，其后配对的 tool 消息才需要区分为 SKILL_LOAD 气泡（Step 4 已处理）。

- [ ] **Step 6: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/ui/ChatWindow.java
git commit -m "$(cat <<'EOF'
feat(demo06): ChatWindow 集成 SkillSidebar + onSkillLoad 回调

- 右侧挂载 SkillSidebar
- onSkillLoad 回调插入 SKILL_LOAD 气泡 + 转发给 SkillSidebar 刷新徽章
- renderSessionMessages 在历史回放时也把 load_skill 的成功 tool 消息
  渲染为 SKILL_LOAD 气泡（通过 tool_call_id 配对 load_skill 调用）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.5: Scene 宽度调整（容纳右侧边栏）

**Files:**
- Modify: `demo06-skill-support/src/main/java/com/github/agent/demo06/Demo06App.java`

- [ ] **Step 1: 把 Scene 宽度从 1060 改为 1280**

定位到：

```java
Scene scene = new Scene(chatWindow.getRoot(), 1060, 650);
```

改为：

```java
Scene scene = new Scene(chatWindow.getRoot(), 1280, 650);
```

同时把最小宽度也提上来：

```java
primaryStage.setMinWidth(700);
```

改为：

```java
primaryStage.setMinWidth(900);
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo06-skill-support compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add demo06-skill-support/src/main/java/com/github/agent/demo06/Demo06App.java
git commit -m "$(cat <<'EOF'
chore(demo06): Scene 宽度从 1060 → 1280 容纳右侧 SkillSidebar

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 4 —— 内置示例 SKILL.md 资源

### Task 4.1: code-review/SKILL.md

**Files:**
- Create: `demo06-skill-support/src/main/resources/skills/code-review/SKILL.md`

- [ ] **Step 1: 创建目录并写入内容**

Run: `mkdir -p demo06-skill-support/src/main/resources/skills/code-review`

然后写入 `demo06-skill-support/src/main/resources/skills/code-review/SKILL.md`：

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

- [ ] **Step 2: Commit**

```bash
git add demo06-skill-support/src/main/resources/skills/code-review/SKILL.md
git commit -m "$(cat <<'EOF'
feat(demo06): 新增内置 skill —— code-review

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.2: translate-zh-en/SKILL.md

**Files:**
- Create: `demo06-skill-support/src/main/resources/skills/translate-zh-en/SKILL.md`

- [ ] **Step 1: 创建目录并写入内容**

Run: `mkdir -p demo06-skill-support/src/main/resources/skills/translate-zh-en`

写入 `demo06-skill-support/src/main/resources/skills/translate-zh-en/SKILL.md`：

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

- [ ] **Step 2: Commit**

```bash
git add demo06-skill-support/src/main/resources/skills/translate-zh-en/SKILL.md
git commit -m "$(cat <<'EOF'
feat(demo06): 新增内置 skill —— translate-zh-en

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4.3: summarize-long-text/SKILL.md

**Files:**
- Create: `demo06-skill-support/src/main/resources/skills/summarize-long-text/SKILL.md`

- [ ] **Step 1: 创建目录并写入内容**

Run: `mkdir -p demo06-skill-support/src/main/resources/skills/summarize-long-text`

写入 `demo06-skill-support/src/main/resources/skills/summarize-long-text/SKILL.md`：

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

- [ ] **Step 2: Commit**

```bash
git add demo06-skill-support/src/main/resources/skills/summarize-long-text/SKILL.md
git commit -m "$(cat <<'EOF'
feat(demo06): 新增内置 skill —— summarize-long-text

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5 —— README、端到端手动验证、打磨

### Task 5.1: 编写 demo06 模块 README

**Files:**
- Create: `demo06-skill-support/README.md`

- [ ] **Step 1: 写入 README**

写入 `demo06-skill-support/README.md`：

```markdown
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
   - 🔧 工具调用气泡：`load_skill({"name":"code-review"})`
   - 🧩 已加载技能气泡
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
```

- [ ] **Step 2: Commit**

```bash
git add demo06-skill-support/README.md
git commit -m "$(cat <<'EOF'
docs(demo06): 新增模块 README —— 核心命题、用法、验证清单

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5.2: 更新根 README 学习路线

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 把 demo06 从"规划中"改为 ✅**

在 `README.md` 中定位学习路线表格中的 demo06 行：

```markdown
| demo06 | RAG Agent | 检索增强生成、知识库集成 |
```

改为：

```markdown
| **demo06** ✅ | Skills | 元数据索引、按需加载、自描述指令包 |
```

（原"RAG Agent"主题移到 demo07+ 规划中，或让用户自行决定。为保险起见可将原 demo06 的 RAG 行移到 demo07 下，或保留为文档注释。此处采取"覆盖"策略。）

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "$(cat <<'EOF'
docs: 更新学习路线 —— demo06 标记为完成，主题改为 Skills

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5.3: 端到端手动验证（跑 demo）

- [ ] **Step 1: 重编译整个项目**

Run:

```bash
mvn -pl demo06-skill-support -am clean compile
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: 清理旧的 skills 目录（模拟首次启动）**

Run:

```bash
[ -d ~/.afs/skills ] && mv ~/.afs/skills ~/.afs/skills.bak.$(date +%s) || true
```

- [ ] **Step 3: 启动 demo06**

Run:

```bash
export OPENAI_API_KEY="你的 key"
cd demo06-skill-support
mvn exec:java
```

- [ ] **Step 4: 按 demo06-skill-support/README.md 手动验证清单 1-8 逐项验证**

用户验证每一项都符合预期。任一项失败则回到对应 Task 修复。

- [ ] **Step 5: 关闭 GUI，确认日志无异常栈**

回看日志，确认无 `ERROR` 级别报错（只允许故意制造错误场景里的预期错误返回字符串）。

- [ ] **Step 6: 如果曾备份用户原来的 skills，提示用户是否需要还原**

```bash
ls ~/.afs/skills.bak.* 2>/dev/null
```

告知用户清理/还原策略。

---

## 自检清单（计划作者复盘）

### Spec 覆盖对照

| Spec 章节 | 覆盖于 | 状态 |
|---|---|---|
| §3 架构总览 | Phase 0-3 整体 | ✅ |
| §4 Skill 文件格式 | Task 1.2 parseFrontmatter + Phase 4 三个 SKILL.md | ✅ |
| §5.1 SkillMeta | Task 1.1 | ✅ |
| §5.2 SkillLoader | Task 1.2 | ✅ |
| §5.3 SkillRegistry | Task 1.3 | ✅ |
| §5.4 LoadSkillTool | Task 1.5 | ✅ |
| §6 AgentCallback.onSkillLoad | Task 1.4 | ✅ |
| §7 AgentService 集成 | Task 2.1 | ✅ |
| §8 System Prompt 组装 | Task 2.1 Step 3（拼接逻辑） | ✅ |
| §9 已加载集合计算 | Task 3.3 SkillSidebar.computeLoadedSkills | ✅ |
| §10.1 布局 | Task 3.4 右侧挂载 + Task 3.5 Scene 宽度 | ✅ |
| §10.2 SkillSidebar | Task 3.2 + 3.3 | ✅ |
| §10.3 ChatWindow 改动 | Task 3.1 + 3.4 | ✅ |
| §11 三个示例 skill | Task 4.1 / 4.2 / 4.3 | ✅ |
| §12 错误处理与边界 | 分散在 Task 1.2 / 1.5（逻辑）+ Task 5.3（验证） | ✅ |
| §13 测试策略 | Task 5.3 手动验证 | ✅ |
| §14 模块结构 | Phase 0 + Phase 1-5 合计 | ✅ |
| §15 根 README 更新 | Task 5.2 | ✅ |
| §16 范围之外 | Task 5.1 README 扩展方向小节明确提及 | ✅ |

### 占位符扫描
- 无 TBD / TODO / "handle edge cases"（error 行为在 Task 1.2 / 1.5 里已显式列出字符串）
- 每一段代码步骤都给出完整可复制代码

### 类型一致性
- `SkillMeta.getName/getDescription/getFilePath` 在 SkillRegistry / LoadSkillTool / SkillCell / SkillSidebar 中使用一致
- `AgentCallback.onSkillLoad(String, String)` 签名在定义（Task 1.4）、调用（Task 1.5 LoadSkillTool）、实现（Task 3.4 ChatWindow）三处一致
- `SkillSidebar.onSkillLoadEvent(String)` 在定义（Task 3.3）和调用（Task 3.4）签名一致

### 风险点注记

1. **SessionChangeListener 方法签名**：Task 3.3 Step 2 提醒——如果接口实际方法签名和计划里的不同，以实际接口为准微调。
2. **JavaFX 模块检查**：demo05 用 `Launcher` 类绕过 JavaFX 模块检查；demo06 已继承该策略（Task 0.1 Step 3 改类名保留此机制）。
3. **首次启动目录权限**：若 `~/.afs/` 所在磁盘只读或权限问题，`SkillLoader.bootstrapDefaults` 会 log.error 并继续——预期行为，不阻塞启动（设计 §12）。

---

## Execution Handoff

计划已写入 `docs/superpowers/plans/2026-05-10-demo06-skill-support.md`。

两种执行方式：

1. **Subagent-Driven（推荐）** —— 每个 Task 派发独立子 agent，主 agent 在 Task 间做审查，迭代快速
2. **Inline Execution** —— 在当前会话中分批执行（每个 Phase 一批），中间点暂停供你 review

你希望以哪种方式执行？
