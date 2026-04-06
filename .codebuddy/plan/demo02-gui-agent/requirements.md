# 需求文档

## 引言

Demo 02 — GUI Agent 是 `agent-from-scratch` 项目的一个新模块，旨在为 Agent 提供一个基于 JavaFX 的图形化用户界面（GUI）调用入口。

当前项目中的 demo00 和 demo01 均采用命令行（CLI）交互方式，用户通过终端输入文本与 Agent 对话。Demo 02 将复用现有的 Agent 核心能力（AgentLoop、LLMClient、Tool 体系），在此基础上构建一个类似聊天窗口的 JavaFX 桌面应用，提供更直观、更友好的交互体验。

本模块的核心目标是：
- 提供一个美观、现代的聊天式 GUI 界面
- 复用现有 Agent 核心组件（AgentLoop、LLMClient、ToolRegistry 等）
- 支持实时展示 Agent 的思考过程和工具调用信息
- 保持项目"从零手搓"的学习风格，不引入额外的 UI 框架

## 需求

### 需求 1：基础聊天窗口界面

**用户故事：** 作为一名学习者，我希望有一个图形化的聊天窗口界面，以便更直观地与 Agent 进行对话交互，而不必依赖命令行终端。

#### 验收标准

1. WHEN 用户启动 Demo02 应用 THEN 系统 SHALL 显示一个 JavaFX 聊天窗口，包含消息展示区域、输入框和发送按钮
2. WHEN 用户在输入框中输入文本并点击发送按钮（或按下 Enter 键） THEN 系统 SHALL 将用户消息显示在聊天区域，并发送给 Agent 处理
3. WHEN Agent 返回回复 THEN 系统 SHALL 将 Agent 的回复以不同于用户消息的样式显示在聊天区域
4. WHEN 聊天消息超出可视区域 THEN 系统 SHALL 自动滚动到最新消息
5. WHEN 输入框为空时用户点击发送 THEN 系统 SHALL 忽略该操作，不发送空消息

### 需求 2：Agent 后端集成

**用户故事：** 作为一名学习者，我希望 GUI 界面能复用现有的 Agent 核心组件（AgentLoop、LLMClient、ToolRegistry），以便理解 GUI 只是 Agent 的一个调用入口，核心逻辑不变。

#### 验收标准

1. WHEN 应用启动 THEN 系统 SHALL 读取环境变量（OPENAI_API_KEY、OPENAI_BASE_URL、OPENAI_MODEL）初始化 LLMClient
2. WHEN 应用启动 AND 未设置 OPENAI_API_KEY THEN 系统 SHALL 在界面上显示错误提示，引导用户设置环境变量
3. WHEN 用户发送消息 THEN 系统 SHALL 通过 AgentLoop 处理用户输入，并将结果返回到 GUI 展示
4. WHEN Agent 正在处理请求 THEN 系统 SHALL 在后台线程中执行 Agent 调用，避免阻塞 JavaFX UI 线程
5. WHEN Agent 处理完成 THEN 系统 SHALL 通过 Platform.runLater() 将结果更新到 UI 线程

### 需求 3：工具调用过程可视化

**用户故事：** 作为一名学习者，我希望在 GUI 中能看到 Agent 调用工具的过程信息，以便更好地理解 Agent 的 ReAct 循环工作原理。

#### 验收标准

1. WHEN Agent 调用工具 THEN 系统 SHALL 在聊天区域以特殊样式（如不同颜色或图标）显示工具调用信息，包括工具名称和参数
2. WHEN 工具执行完成返回结果 THEN 系统 SHALL 在聊天区域显示工具执行结果
3. WHEN Agent 经历多步 ReAct 循环 THEN 系统 SHALL 按顺序展示每一步的工具调用和结果，让用户能跟踪完整的推理过程

### 需求 4：交互状态反馈

**用户故事：** 作为一名学习者，我希望界面能清晰地反馈当前 Agent 的工作状态，以便我知道 Agent 是否正在处理我的请求。

#### 验收标准

1. WHEN Agent 正在处理用户请求 THEN 系统 SHALL 显示加载指示器（如"思考中..."的动画或文字提示），并禁用发送按钮
2. WHEN Agent 处理完成 THEN 系统 SHALL 隐藏加载指示器，并重新启用发送按钮和输入框
3. WHEN Agent 处理过程中发生异常 THEN 系统 SHALL 在聊天区域显示错误信息，并恢复输入状态，允许用户重新发送

### 需求 5：Maven 模块集成

**用户故事：** 作为一名学习者，我希望 demo02 作为独立的 Maven 子模块存在，遵循项目现有的模块结构规范，以便保持项目的一致性和可维护性。

#### 验收标准

1. WHEN 项目构建 THEN 系统 SHALL 将 demo02-gui-agent 作为 parent POM 的子模块进行编译
2. WHEN demo02 模块构建 THEN 系统 SHALL 包含 JavaFX 相关依赖（javafx-controls、javafx-fxml 等）以及现有的 okhttp、jackson、slf4j 依赖
3. WHEN 用户执行 `cd demo02-gui-agent && mvn compile exec:java` THEN 系统 SHALL 能够正常启动 GUI 应用
4. WHEN demo02 模块结构 THEN 系统 SHALL 遵循现有包命名规范 `com.github.agent.demo02`