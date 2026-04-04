# 需求文档

## 引言

本模块 `demo01-computer-use` 是 `agent-from-scratch` 教学项目的第二个 Demo，主题为 **Computer Use（电脑操作）**。

在 demo00 中，Agent 已经具备了基础的 ReAct 循环和工具调用能力，但所有工具都是"信息查询类"的（计算器、时间、天气），Agent 无法对外部环境产生实际影响。demo01 的目标是让 Agent 具备**操作电脑的能力**——仅通过一个 Shell 命令执行工具 `exec`，Agent 就可以真正地与操作系统交互，完成诸如"帮我创建一个项目目录结构"、"查看当前磁盘使用情况"、"读取某个配置文件并修改"等实际任务。

**设计理念：** 只提供一个 `exec` 工具，而非拆分为多个专用工具（read_file、write_file、list_directory 等）。这样做的好处是：
- **极简设计**：一个工具覆盖所有操作系统交互场景
- **展示 LLM 的涌现能力**：LLM 足够聪明，能自行组合 `cat`、`echo`、`ls`、`mkdir` 等 Shell 命令来完成各种任务
- **教学价值**：让学习者理解"工具只是 Agent 的手，LLM 才是 Agent 的脑"

本模块的核心教学目标：
- 学习如何实现**有副作用的工具**（与 demo00 的纯查询工具形成对比）
- 理解 Agent 安全性设计：命令确认机制、危险命令拦截、超时控制
- 体验仅凭一个 `exec` 工具，Agent 就能成为"电脑操作员"的能力

本模块复用 demo00 的核心架构（AgentLoop、LLMClient、ChatMessage 等），通过复制代码的方式保持各 demo 独立可运行，同时新增 `exec` 工具实现。

**技术要求：** 本模块使用 **Java 25**，需同步将父 POM 和 demo00 的 Java 版本从 17 升级到 25。

## 需求

### 需求 1：Shell 命令执行工具（exec）

**用户故事：** 作为一名学习者，我希望 Agent 能通过一个 `exec` 工具执行任意 Shell 命令，以便 Agent 可以与操作系统交互完成所有实际任务（查看文件、创建目录、编辑配置、安装软件、运行脚本等）。

#### 验收标准

1. WHEN 用户要求 Agent 执行一个操作系统任务 THEN 系统 SHALL 通过 `exec` 工具调用 Java `ProcessBuilder` 执行对应的 Shell 命令，并返回命令的标准输出（stdout）内容。
2. WHEN 命令执行产生错误输出（stderr） THEN 系统 SHALL 将 stderr 内容一并返回，并明确标注为错误输出。
3. WHEN 命令执行超过预设的超时时间（默认 30 秒） THEN 系统 SHALL 强制终止该进程，并返回超时错误提示。
4. WHEN 命令的输出内容超过预设的最大长度（默认 10000 字符） THEN 系统 SHALL 截断输出并附加截断提示，避免过长输出消耗过多 Token。
5. WHEN 用户提交的命令包含危险操作关键词（如 `rm -rf /`、`mkfs`、`dd if=`、`:(){ :|:& };:` 等） THEN 系统 SHALL 拒绝执行并返回安全警告信息。
6. IF 操作系统为 macOS/Linux THEN 系统 SHALL 使用 `/bin/sh -c` 执行命令；IF 操作系统为 Windows THEN 系统 SHALL 使用 `cmd /c` 执行命令。
7. WHEN 命令执行完成 THEN 系统 SHALL 返回退出码（exit code）、stdout 和 stderr 的组合结果。

### 需求 2：模块结构与入口

**用户故事：** 作为一名学习者，我希望 demo01 模块是独立可运行的，并且代码结构与 demo00 保持一致，以便我能快速上手并对比学习。

#### 验收标准

1. WHEN 构建项目时 THEN 系统 SHALL 将 demo01 作为 `demo01-computer-use` 子模块纳入父 POM 管理。
2. WHEN 运行 demo01 THEN 系统 SHALL 提供 `Demo01Main` 入口类，支持通过 `mvn exec:java` 启动交互式命令行。
3. WHEN Agent 启动时 THEN 系统 SHALL 仅注册 `exec` 这一个工具。
4. WHEN Agent 启动时 THEN 系统 SHALL 使用专门为 Computer Use 场景优化的 System Prompt，引导 LLM 合理使用 `exec` 工具来完成各种操作系统任务（包括文件读写、目录浏览、进程管理等）。
5. WHEN demo01 模块构建时 THEN 系统 SHALL 复用 demo00 的核心类（AgentLoop、LLMClient、ChatMessage、Tool 接口等），通过复制代码到 demo01 包下的方式保持模块独立性。
6. WHEN 项目构建时 THEN 系统 SHALL 将整个项目的 Java 版本（父 POM 及所有子模块）从 Java 17 升级到 **Java 25**，包括 `maven.compiler.source`、`maven.compiler.target` 及 `maven-compiler-plugin` 的配置。

### 需求 3：安全性设计

**用户故事：** 作为一名学习者，我希望了解 Agent 操作电脑时的安全性考量，以便我能理解在生产环境中如何安全地使用 Agent。

#### 验收标准

1. WHEN Agent 准备执行 Shell 命令时 THEN 系统 SHALL 在控制台打印即将执行的命令，并要求用户输入 `y` 确认后才执行（交互式确认机制）。
2. IF 环境变量 `AGENT_AUTO_CONFIRM=true` THEN 系统 SHALL 跳过确认步骤，自动执行命令（方便演示和测试）。
3. WHEN 命令包含预定义的危险模式 THEN 系统 SHALL 即使在自动确认模式下也拒绝执行。
4. WHEN 工具执行过程中发生异常 THEN 系统 SHALL 捕获异常并返回友好的错误信息，不应导致 Agent 循环崩溃。
