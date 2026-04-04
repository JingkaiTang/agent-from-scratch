# 实施计划

- [ ] 1. 升级项目 Java 版本到 25
   - 修改父 POM `pom.xml` 中 `maven.compiler.source` 和 `maven.compiler.target` 从 `17` 改为 `25`
   - 修改父 POM `pluginManagement` 中 `maven-compiler-plugin` 的 `<source>` 和 `<target>` 从 `17` 改为 `25`
   - 确认 demo00 子模块 POM 无需额外修改（继承父 POM 配置）
   - _需求：2.6_

- [ ] 2. 创建 demo01 模块骨架
   - 在父 POM 的 `<modules>` 中添加 `<module>demo01-computer-use</module>`
   - 创建 `demo01-computer-use/pom.xml`，参照 demo00 的 POM 结构，`artifactId` 为 `demo01-computer-use`，继承父 POM
   - 创建 Maven 标准目录结构 `src/main/java/com/github/agent/demo01/` 及子包 `core/`、`llm/`、`model/`、`tool/`、`tool/impl/`
   - 创建 `src/main/resources/` 目录，复制 demo00 的 `logback.xml` 配置
   - _需求：2.1、2.5_

- [ ] 3. 复制 demo00 核心类到 demo01
   - 复制 `core/AgentLoop.java` 到 demo01 的 `core/` 包下，修改包名为 `com.github.agent.demo01.core`
   - 复制 `llm/LLMClient.java` 到 demo01 的 `llm/` 包下，修改包名为 `com.github.agent.demo01.llm`
   - 复制 `model/ChatMessage.java` 到 demo01 的 `model/` 包下，修改包名为 `com.github.agent.demo01.model`
   - 复制 `tool/Tool.java` 和 `tool/ToolRegistry.java` 到 demo01 的 `tool/` 包下，修改包名为 `com.github.agent.demo01.tool`
   - 修正所有复制文件中的 import 语句，将 `demo00` 替换为 `demo01`
   - _需求：2.5_

- [ ] 4. 实现 ExecTool 核心逻辑
   - 在 `tool/impl/` 下创建 `ExecTool.java`，实现 `Tool` 接口
   - 工具名称定义为 `exec`，描述说明其功能为执行 Shell 命令
   - 定义工具参数 schema：`command`（string，必填，要执行的 Shell 命令）
   - 使用 `ProcessBuilder` 执行命令，macOS/Linux 使用 `/bin/sh -c`，Windows 使用 `cmd /c`（通过 `System.getProperty("os.name")` 判断）
   - 捕获 stdout 和 stderr 输出流，组合为结果返回（包含 exit code）
   - 实现 30 秒超时控制（`Process.waitFor(timeout, TimeUnit)`），超时则 `destroyForcibly()` 并返回超时提示
   - 实现输出截断：超过 10000 字符时截断并附加 `...[输出已截断]` 提示
   - 所有异常统一捕获，返回友好错误信息，不抛出异常
   - _需求：1.1、1.2、1.3、1.4、1.6、1.7、3.4_

- [ ] 5. 实现危险命令拦截机制
   - 在 `ExecTool` 中定义危险命令模式列表（正则表达式），包括但不限于：`rm\s+-rf\s+/`、`mkfs`、`dd\s+if=`、`:\(\)\{.*\|.*&\s*\};:`、`chmod\s+-R\s+777\s+/`、`>\s*/dev/sda` 等
   - 在执行命令前进行危险模式匹配检查，命中则直接拒绝执行并返回安全警告
   - 危险命令拦截优先级最高，即使在自动确认模式下也不可绕过
   - _需求：1.5、3.3_

- [ ] 6. 实现交互式命令确认机制
   - 在 `ExecTool.execute()` 中，执行命令前在控制台打印 `⚠️ 即将执行命令: <command>`，并提示用户输入 `y` 确认
   - 读取用户输入，仅当输入为 `y` 或 `Y` 时才继续执行，否则返回"用户取消执行"
   - 检查环境变量 `AGENT_AUTO_CONFIRM`，若为 `true` 则跳过确认直接执行
   - 确认机制在危险命令检查之后执行（即先检查危险命令，再请求确认）
   - _需求：3.1、3.2_

- [ ] 7. 编写 Computer Use 场景的 System Prompt
   - 在 `Demo01Main` 或独立资源文件中定义 System Prompt
   - Prompt 内容需引导 LLM：你是一个能操作电脑的 AI 助手，可以通过 `exec` 工具执行 Shell 命令来完成用户的任务
   - Prompt 中给出常用操作示例提示：文件读写（`cat`、`echo >`）、目录操作（`ls`、`mkdir`、`cd`）、进程管理（`ps`、`kill`）、系统信息（`uname`、`df`、`top`）等
   - Prompt 中强调安全意识：执行前思考命令是否安全，避免破坏性操作
   - _需求：2.4_

- [ ] 8. 编写 Demo01Main 入口类
   - 创建 `Demo01Main.java`，参照 demo00 的 `Demo00Main.java` 结构
   - 初始化 `ToolRegistry`，仅注册 `ExecTool` 一个工具
   - 初始化 `LLMClient` 和 `AgentLoop`，使用步骤 7 中定义的 System Prompt
   - 实现交互式命令行循环（Scanner 读取用户输入，调用 AgentLoop 处理）
   - 在 demo01 的 `pom.xml` 中配置 `exec-maven-plugin`，指定 `mainClass` 为 `com.github.agent.demo01.Demo01Main`
   - _需求：2.2、2.3_

- [ ] 9. 端到端验证与调试
   - 使用 `jvm` 命令切换到 Java 25 环境，执行 `mvn clean compile` 验证整个项目（含 demo00 和 demo01）编译通过
   - 在 demo01 目录下执行 `mvn exec:java` 启动 Agent，测试以下场景：
     - 基础命令：`帮我看看当前目录有哪些文件`（验证 `ls` 执行）
     - 文件读取：`读取 pom.xml 的内容`（验证 `cat` 执行）
     - 文件写入：`创建一个 hello.txt 文件，内容为 Hello World`（验证 `echo >` 执行）
     - 危险命令拦截：`删除根目录所有文件`（验证安全拦截）
     - 交互式确认：验证执行命令前的确认提示正常工作
   - _需求：1.1 ~ 1.7、2.2、2.3、3.1 ~ 3.4_
