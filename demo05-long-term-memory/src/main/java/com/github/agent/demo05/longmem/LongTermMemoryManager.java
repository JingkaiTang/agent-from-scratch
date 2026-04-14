package com.github.agent.demo05.longmem;

import com.github.agent.demo05.llm.LLMClient;
import com.github.agent.demo05.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 长期记忆管理器 —— 统一门面，协调记忆的提取、存储和检索。
 * <p>
 * 对外暴露两个核心操作：
 * <ul>
 *   <li>{@link #retrieve(String)} —— 在 AgentLoop.run() 开始时调用，检索相关记忆注入上下文</li>
 *   <li>{@link #ingest(List)} —— 在每轮对话结束后调用，提取新知识写入 memories.md</li>
 * </ul>
 * <p>
 * 内部协调三个组件：
 * <ul>
 *   <li>{@link MemoryStore} —— 读写 memories.md 文件</li>
 *   <li>{@link MemoryRetriever} —— LLM 提取关键词 + grep 检索</li>
 *   <li>{@link MemoryExtractor} —— LLM 从对话中提取记忆条目</li>
 * </ul>
 * <p>
 * 使用 Facade 模式 —— 外部调用者（AgentLoop、AgentService）只需与本类交互，
 * 不需要关心 Store/Retriever/Extractor 的细节。
 */
public class LongTermMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryManager.class);

    private final MemoryStore store;
    private final MemoryRetriever retriever;
    private final MemoryExtractor extractor;
    private final boolean enabled;

    /**
     * 创建长期记忆管理器。
     *
     * @param llmClient LLM 客户端（用于关键词提取和记忆提取）
     * @param enabled   是否启用长期记忆
     */
    public LongTermMemoryManager(LLMClient llmClient, boolean enabled) {
        this.enabled = enabled;
        this.store = new MemoryStore();
        this.retriever = new MemoryRetriever(llmClient, store);
        this.extractor = new MemoryExtractor(llmClient);
    }

    /**
     * 初始化 —— 确保 memories.md 文件存在。
     */
    public void init() {
        if (enabled) {
            store.init();
            log.info("长期记忆管理器已初始化");
        }
    }

    /**
     * 检索与用户消息相关的长期记忆。
     * <p>
     * 在 AgentLoop.run() 开头调用。检索到的记忆将被拼接为文本，
     * 由 AgentLoop 以 system 消息的形式注入到对话上下文中。
     *
     * @param userMessage 用户的最新消息
     * @return 相关记忆的拼接文本；无相关记忆或未启用时返回 null
     */
    public String retrieve(String userMessage) {
        if (!enabled) {
            return null;
        }

        List<String> matches = retriever.retrieve(userMessage);
        if (matches.isEmpty()) {
            return null;
        }

        // 拼接为可读的文本
        StringBuilder sb = new StringBuilder();
        for (String match : matches) {
            sb.append("- ").append(match).append("\n");
        }

        String result = sb.toString().strip();
        log.info("检索到 {} 条相关长期记忆", matches.size());
        return result;
    }

    /**
     * 从对话历史中提取知识并写入 memories.md。
     * <p>
     * 在 AgentService 中，每轮对话结束后异步调用。
     * LLM 会判断对话中是否有值得长期记住的信息，有则追加到文件。
     *
     * @param conversationHistory 完整的对话历史
     * @return 新写入的记忆条目数量；无新记忆返回 0
     */
    public int ingest(List<ChatMessage> conversationHistory) {
        if (!enabled) {
            return 0;
        }

        // 1. LLM 提取记忆条目
        List<String> entries = extractor.extract(conversationHistory);
        if (entries.isEmpty()) {
            log.debug("本轮对话无值得记录的长期记忆");
            return 0;
        }

        // 2. 追加写入 memories.md
        store.append(entries);
        log.info("已写入 {} 条长期记忆", entries.size());

        return entries.size();
    }

    /**
     * 是否启用长期记忆。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 获取记忆存储层（用于调试或高级操作）。
     */
    public MemoryStore getStore() {
        return store;
    }
}
