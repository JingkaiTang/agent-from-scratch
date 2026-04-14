package com.github.agent.demo05.longmem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agent.demo05.llm.LLMClient;
import com.github.agent.demo05.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 记忆检索器 —— 根据用户消息检索相关的长期记忆。
 * <p>
 * 检索流程（两步）：
 * <ol>
 *   <li><b>关键词提取</b>：调用 LLM 从用户消息中提取搜索关键词</li>
 *   <li><b>grep 匹配</b>：用关键词逐行搜索 memories.md，返回命中的记忆条目</li>
 * </ol>
 * <p>
 * 为什么用 LLM 提取关键词而不是简单分词？
 * <ul>
 *   <li>LLM 能理解语义，把"你记得我是谁吗？"映射为关键词 ["姓名", "用户"]</li>
 *   <li>简单分词会产生大量无意义词（"的""了""吗"），命中噪音太多</li>
 *   <li>多一次 LLM 调用的成本，换来检索质量的大幅提升</li>
 * </ul>
 */
public class MemoryRetriever {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetriever.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 关键词提取提示词 */
    private static final String KEYWORD_PROMPT = """
            你是一个关键词提取助手。请从用户的消息中提取用于搜索记忆库的关键词。

            规则：
            1. 提取实体名词、专有名词、关键概念（如人名、项目名、技术名词）
            2. 如果用户在问"你记得..."相关的问题，推测他想让你回忆什么，提取对应的关键词
            3. 每个关键词 2-4 个字，不要太长也不要太短
            4. 返回 3-6 个最相关的关键词

            以 JSON 字符串数组格式返回，只返回 JSON，不要其他文字。

            示例：
            用户消息："你还记得我的名字吗？我之前跟你说过"
            返回：["名字", "姓名", "用户", "叫"]

            用户消息："帮我回忆一下我的项目用的什么构建工具"
            返回：["项目", "构建工具", "Maven", "Gradle"]

            用户消息：
            """;

    private final LLMClient llmClient;
    private final MemoryStore memoryStore;

    public MemoryRetriever(LLMClient llmClient, MemoryStore memoryStore) {
        this.llmClient = llmClient;
        this.memoryStore = memoryStore;
    }

    /**
     * 根据用户消息检索相关的长期记忆。
     * <p>
     * 流程：LLM 提取关键词 → grep memories.md → 返回命中条目。
     *
     * @param userMessage 用户的最新消息
     * @return 命中的记忆条目列表；无命中返回空列表
     */
    public List<String> retrieve(String userMessage) {
        try {
            // 1. LLM 提取关键词
            List<String> keywords = extractKeywords(userMessage);
            if (keywords.isEmpty()) {
                log.debug("未提取到关键词，跳过记忆检索");
                return Collections.emptyList();
            }

            log.info("提取关键词: {}", keywords);

            // 2. grep 搜索记忆文件
            List<String> matches = memoryStore.search(keywords);
            log.info("记忆检索: 关键词 {} → 命中 {} 条", keywords, matches.size());

            return matches;

        } catch (Exception e) {
            log.error("记忆检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 调用 LLM 从用户消息中提取搜索关键词。
     *
     * @param userMessage 用户消息
     * @return 关键词列表；失败返回空列表
     */
    private List<String> extractKeywords(String userMessage) {
        try {
            List<ChatMessage> messages = List.of(
                    ChatMessage.system("你是一个关键词提取助手，只返回 JSON 数组，不返回其他内容。"),
                    ChatMessage.user(KEYWORD_PROMPT + userMessage)
            );

            ChatMessage response = llmClient.chat(messages, Collections.emptyList());
            String content = response.getContent();

            if (content == null || content.isBlank()) {
                return Collections.emptyList();
            }

            return parseKeywords(content.strip());

        } catch (Exception e) {
            log.warn("提取关键词失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 LLM 返回的关键词 JSON 数组。
     */
    private List<String> parseKeywords(String jsonText) {
        try {
            // 去除可能的 Markdown 代码块包裹
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .strip();
            }

            return MAPPER.readValue(jsonText, new TypeReference<List<String>>() {});

        } catch (Exception e) {
            log.warn("解析关键词 JSON 失败: {} | 原始内容: {}", e.getMessage(), jsonText);
            return Collections.emptyList();
        }
    }
}
