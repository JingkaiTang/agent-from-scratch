# 实施计划

- [ ] 1. 创建 demo02-gui-agent Maven 子模块
   - 在项目根目录创建 `demo02-gui-agent/pom.xml`，继承 parent POM，添加 JavaFX 依赖（javafx-controls）以及现有的 okhttp、jackson、slf4j、logback 依赖
   - 配置 `exec-maven-plugin`，指定主类为 `com.github.agent.demo02.Demo02App`
   - 配置 `maven-compiler-plugin`，添加 JavaFX 模块参数（`--add-modules javafx.controls`）
   - 在根 `pom.xml` 的 `<modules>` 中添加 `demo02-gui-agent` 子模块
   - 创建标准目录结构 `src/main/java/com/github/agent/demo02/` 和 `src/main/resources/`
   - _需求：5.1、5.2、5.3、5.4_

- [ ] 2. 复制并适配 Agent 核心组件
   - 将 demo01 中的核心类复制到 demo02 的包路径下，并修改包名为 `com.github.agent.demo02`：
     - `model/ChatMessage.java` — 消息模型
     - `llm/LLMClient.java` — LLM 客户端
     - `tool/Tool.java` — 工具接口
     - `tool/ToolRegistry.java` — 工具注册中心
     - `tool/impl/ExecTool.java` — exec 工具实现
   - 确保所有 import 路径更新为 demo02 的包名
   - _需求：2.1、2.3_

- [ ] 3. 改造 AgentLoop 支持回调通知
   - 复制 `core/AgentLoop.java` 到 demo02 包下
   - 定义 `AgentCallback` 接口，包含以下回调方法：
     - `onToolCall(String toolName, String argsJson)` — 工具调用时触发
     - `onToolResult(String toolName, String result)` — 工具执行结果返回时触发
     - `onError(String errorMessage)` — 异常发生时触发
   - 修改 `AgentLoop.run()` 方法，在工具调用和结果返回时调用对应的回调方法，使 GUI 能实时获取中间过程信息
   - _需求：3.1、3.2、3.3、4.3_

- [ ] 4. 实现 JavaFX 聊天窗口主界面
   - 创建 `Demo02App.java`（继承 `javafx.application.Application`），作为 JavaFX 应用入口
   - 使用纯 Java 代码构建 UI 布局（不使用 FXML）：
     - 顶部标题栏：显示应用名称 "Agent GUI"
     - 中部消息展示区域：使用 `ScrollPane` + `VBox` 展示聊天消息列表
     - 底部输入区域：`TextField`（输入框）+ `Button`（发送按钮），水平排列
   - 设置窗口默认大小（约 800x600）、标题、基础样式
   - _需求：1.1_

- [ ] 5. 实现消息气泡组件与样式
   - 创建 `MessageBubble` 组件类，用于渲染不同类型的消息气泡：
     - 用户消息：右对齐，蓝色背景
     - Agent 回复：左对齐，灰色背景
     - 工具调用信息：左对齐，橙色/黄色背景，显示工具名称和参数
     - 工具执行结果：左对齐，绿色背景，显示执行结果
     - 错误信息：左对齐，红色背景
   - 使用 CSS 内联样式或独立 CSS 文件美化气泡外观（圆角、阴影、间距等）
   - _需求：1.3、3.1、3.2、4.3_

- [ ] 6. 实现消息发送与 Agent 后台调用
   - 在 `Demo02App` 的 `start()` 方法中初始化 Agent 组件：读取环境变量创建 `LLMClient`、注册工具、创建 `AgentLoop`
   - 如果 `OPENAI_API_KEY` 未设置，在聊天区域显示红色错误提示消息
   - 实现发送按钮点击事件和 Enter 键事件：
     - 校验输入非空
     - 将用户消息添加到聊天区域（用户气泡）
     - 清空输入框
     - 在新线程（`Thread` 或 `ExecutorService`）中调用 `AgentLoop.run()`
     - 通过 `AgentCallback` 回调 + `Platform.runLater()` 将工具调用信息、工具结果实时更新到聊天区域
     - Agent 最终回复通过 `Platform.runLater()` 添加到聊天区域（Agent 气泡）
   - _需求：1.2、1.5、2.1、2.2、2.3、2.4、2.5、3.1、3.2、3.3_

- [ ] 7. 实现交互状态反馈
   - Agent 处理中：
     - 禁用发送按钮和输入框
     - 在聊天区域底部显示"🤔 思考中..."的状态提示（可使用简单的 Label 或动画）
   - Agent 处理完成：
     - 移除"思考中"提示
     - 重新启用发送按钮和输入框
     - 自动聚焦到输入框
   - 异常处理：
     - 在聊天区域显示红色错误气泡
     - 恢复输入状态
   - _需求：4.1、4.2、4.3_

- [ ] 8. 实现自动滚动与细节优化
   - 每次添加新消息后，自动将 `ScrollPane` 滚动到底部
   - 添加 logback.xml 配置文件
   - 验证完整流程：启动应用 → 发送消息 → 查看工具调用过程 → 收到最终回复
   - _需求：1.4、5.3_