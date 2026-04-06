# 实施计划

- [ ] 1. 搭建 demo03 模块骨架并复制 demo02 基础代码
   - 创建 `demo03-session-management` Maven 子模块目录结构，包名为 `com.github.agent.demo03`
   - 编写 `demo03-session-management/pom.xml`，继承父 POM，依赖与 demo02 一致（JavaFX、OkHttp、Jackson、SLF4J/Logback）
   - 修改父 `pom.xml`，在 `<modules>` 中添加 `<module>demo03-session-management</module>`
   - 将 demo02 的所有 Java 源文件复制到 demo03 对应包下（`model`、`core`、`llm`、`tool`、`agent`、`ui` 包），修改包名为 `com.github.agent.demo03`
   - 复制 `logback.xml` 等资源文件
   - 创建入口类 `Demo03App.java` 和 `Launcher.java`，确保模块可独立编译运行
   - _需求：6.3_

- [ ] 2. 创建 Session 数据模型
   - 在 `com.github.agent.demo03.session` 包下创建 `Session` 类
   - 包含字段：`id`（String，UUID 格式）、`title`（String）、`createdAt`（LocalDateTime）、`updatedAt`（LocalDateTime）、`messages`（`List<ChatMessage>`）
   - 构造方法中自动生成 UUID 和设置创建时间
   - 提供 `addMessage(ChatMessage)` 方法，追加消息时自动更新 `updatedAt`
   - 提供 `autoGenerateTitle()` 逻辑：当 title 为空且有第一条 user 消息时，取前 20 个字符作为标题
   - 添加 Jackson 序列化/反序列化所需的注解（`@JsonProperty`、`@JsonIgnoreProperties` 等）和无参构造方法
   - 编写中文 Javadoc 注释
   - _需求：1.1、1.2、1.3、1.4、6.1_

- [ ] 3. 实现 SessionStore 持久化存储层
   - 在 `com.github.agent.demo03.session` 包下创建 `SessionStore` 类
   - 存储目录为 `~/.agent-from-scratch/sessions/`，在构造方法中检查并自动创建目录
   - 实现 `save(Session)` 方法：将 Session 序列化为 JSON 写入 `{sessionId}.json` 文件
   - 实现 `loadAll()` 方法：扫描目录下所有 `.json` 文件，逐个反序列化为 Session 对象；遇到损坏文件记录警告日志并跳过
   - 实现 `delete(String sessionId)` 方法：删除对应的 JSON 文件
   - 使用 Jackson ObjectMapper 进行 JSON 序列化/反序列化，配置 `JavaTimeModule` 支持 `LocalDateTime`
   - 编写中文 Javadoc 注释
   - _需求：2.1、2.2、2.3、2.4、2.5、6.1_

- [ ] 4. 实现 SessionManager 会话管理服务
   - 在 `com.github.agent.demo03.session` 包下创建 `SessionManager` 类
   - 持有 `SessionStore` 引用和内存中的会话列表 `Map<String, Session>`，以及当前活跃会话 ID
   - 实现 `init()` 方法：调用 `SessionStore.loadAll()` 加载所有会话到内存；如果没有任何会话则自动创建一个空白会话；自动选中最近更新的会话
   - 实现 `createSession()` 方法：创建新空白会话，加入内存 Map，持久化，返回新会话
   - 实现 `switchSession(String sessionId)` 方法：切换当前活跃会话 ID，返回目标 Session
   - 实现 `deleteSession(String sessionId)` 方法：从内存和存储中删除会话；如果删除的是当前活跃会话，自动切换到最近的其他会话或创建新会话
   - 实现 `appendMessage(ChatMessage)` 方法：向当前活跃会话追加消息，自动生成标题，触发持久化
   - 实现 `getCurrentSession()` 和 `getAllSessionsSorted()` 方法
   - 提供会话变更监听器接口 `SessionChangeListener`，通知 GUI 刷新
   - 编写中文 Javadoc 注释
   - _需求：3.1、3.2、3.3、3.4、3.5、3.6、6.1、6.2_

- [ ] 5. 改造 AgentLoop 支持会话上下文切换
   - 在 `AgentLoop` 中新增 `setConversationHistory(List<ChatMessage>)` 方法，允许外部替换对话历史
   - 新增 `resetConversation()` 方法，清空对话历史并重新添加 system prompt
   - 新增 `getConversationHistory()` 方法，返回当前对话历史的只读副本
   - 确保切换会话时 AgentLoop 的上下文能正确重建
   - 编写中文 Javadoc 注释
   - _需求：5.1、5.2、5.3、5.4_

- [ ] 6. 改造 AgentService 集成 SessionManager
   - 在 `AgentService` 中引入 `SessionManager` 依赖
   - 在 `initialize()` 方法中初始化 `SessionManager`（调用 `init()` 加载历史会话）
   - 修改 `sendMessage()` 方法：发送前将用户消息追加到当前会话（`sessionManager.appendMessage()`）；Agent 回复后将 assistant 消息也追加到当前会话
   - 新增 `createSession()` 方法：创建新会话并重置 AgentLoop 上下文
   - 新增 `switchSession(String sessionId)` 方法：切换会话并用目标会话的消息列表重建 AgentLoop 上下文
   - 新增 `deleteSession(String sessionId)` 方法：删除会话并处理上下文切换
   - 暴露 `SessionManager` 的查询方法供 GUI 调用（`getAllSessionsSorted()`、`getCurrentSession()` 等）
   - _需求：5.1、5.2、5.3、5.4、3.1、3.2、3.3、3.4_

- [ ] 7. 实现 GUI 侧边栏会话列表组件
   - 在 `com.github.agent.demo03.ui` 包下创建 `SessionSidebar` 类（继承 VBox）
   - 顶部放置"+ 新建会话"按钮，样式与整体 UI 风格一致
   - 使用 `ListView` 或自定义 VBox 展示会话列表项，每项显示会话标题和最后更新时间
   - 会话列表按 `updatedAt` 倒序排列
   - 当前活跃会话以高亮背景色标识
   - 点击会话项触发切换回调
   - 右键点击会话项弹出 `ContextMenu`，包含"删除会话"菜单项
   - 提供 `refresh(List<Session>, String activeSessionId)` 方法供外部刷新列表
   - 编写中文 Javadoc 注释
   - _需求：4.1、4.2、4.3、4.4、4.5、4.6、6.1_

- [ ] 8. 改造 ChatWindow 集成侧边栏和会话切换
   - 修改 `ChatWindow` 的 `buildUI()` 方法：在 `BorderPane` 的 left 区域放置 `SessionSidebar`
   - 绑定侧边栏事件：点击"新建会话"→ 调用 `AgentService.createSession()` → 清空聊天区域 → 刷新侧边栏
   - 绑定侧边栏事件：点击会话项 → 调用 `AgentService.switchSession()` → 重新渲染目标会话的历史消息到聊天区域 → 刷新侧边栏高亮
   - 绑定侧边栏事件：右键删除会话 → 调用 `AgentService.deleteSession()` → 刷新侧边栏和聊天区域
   - 新增 `renderSessionMessages(Session)` 方法：清空 `messageContainer` 并根据会话消息列表重建所有消息气泡
   - 修改 `handleSend()` 方法：发送消息后刷新侧边栏（更新标题和排序）
   - 应用启动时加载当前会话的历史消息并刷新侧边栏
   - _需求：4.1、4.2、4.3、4.4、4.5、4.6、3.1、3.2、3.6_

- [ ] 9. 改造 AgentCallback 回调同步消息到会话
   - 修改 `ChatWindow` 中的 `bindAgentCallback()` 方法，在 `onToolCall`、`onToolResult` 回调中，将工具调用和结果消息同步追加到当前活跃会话（通过 `SessionManager.appendMessage()`）
   - 在 `onStreamComplete` 回调中，将 Agent 最终回复追加到当前活跃会话
   - 确保每次消息追加后触发持久化（由 SessionManager 内部处理）
   - 每次消息追加后刷新侧边栏的会话标题和排序
   - _需求：5.3、2.1、1.4_

- [ ] 10. 编写 README 文档并验证整体功能
   - 在 `demo03-session-management` 目录下创建 `README.md`，说明本 Demo 的学习目标、架构设计、核心类说明、运行方式
   - 绘制简要的架构图（文本形式），展示 Session / SessionManager / SessionStore / AgentLoop / ChatWindow 之间的关系
   - 更新项目根目录 `README.md`，在学习路线中添加 demo03 的说明
   - _需求：6.1、6.2、6.3_
