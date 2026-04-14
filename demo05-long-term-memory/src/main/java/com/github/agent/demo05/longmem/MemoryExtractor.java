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
 * 记忆提取器 —— 调用 LLM 从对话历史中提取值得长期记住的信息。
 * <p>
 * 提取流程：
 * <ol>
 *   <li>将完整对话历史格式化为可读文本</li>
 *   <li>通过 LLM 判断对话中是否包含值得记录的知识/事实/偏好</li>
 *   <li>LLM 以 JSON 数组格式返回提取出的记忆条目</li>
 * </ol>
 * <p>
 * 什么值得记住？
 * <ul>
 *   <li>用户的个人信息（姓名、职业、偏好）</li>
 *   <li>用户教给 Agent 的知识点</li>
 *   <li>重要的项目信息、技术决策</li>
 *   <li>需要跨会话记住的事实</li>
 * </ul>
 * <p>
 * 什么不记？闲聊、问候、临时性操作指令。
 */
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 记忆提取提示词 */
    private static final String EXTRACT_PROMPT = """
            你是一个记忆提取助手。请分析以下对话，提取值得长期记住的信息。

            值得记录的信息包括：
            - 用户的个人信息（姓名、职业、偏好、习惯）
            - 用户教给 Agent 的知识或事实
            - 重要的项目信息、技术选型、环境配置
            - 用户明确表示希望被记住的事情

            不要记录的信息：
            - 闲聊、问候、临时指令（如"帮我查一下天气"）
            - 工具调用的具体执行过程
            - 已经是常识的信息

            请以 JSON 字符串数组格式返回提取出的记忆条目，每条是一句简洁的陈述。
            如果没有值得记录的内容，返回空数组 []。

            示例返回：
            ["用户名叫小明，是一名 Java 开发者", "用户的项目使用 Maven 构建"]

            注意：只返回 JSON 数组，不要包含其他文字。

            ## 对话内容：
            """;

    private final LLMClient llmClient;

    public MemoryExtractor(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 从对话历史中提取值得长期记住的信息。
     *
     * @param conversationHistory 完整的对话历史（包含 system prompt）
     * @return 提取出的记忆条目列表；无值得记录的内容时返回空列表
     */
    public List<String> extract(List<ChatMessage> conversationHistory) {
        try {
            // 1. 将对话历史格式化为可读文本
            String conversationText = formatConversation(conversationHistory);

            // 2. 构造提取请求
            List<ChatMessage> messages = List.of(
                    ChatMessage.system("你是一个记忆提取助手，只返回 JSON 数组，不返回其他内容。"),
                    ChatMessage.user(EXTRACT_PROMPT + conversationText)
            );

            // 3. 调用 LLM（非流式，不需要 tools）
            ChatMessage response = llmClient.chat(messages, Collections.emptyList());
            String content = response.getContent();

            if (content == null || content.isBlank()) {
                return Collections.emptyList();
            }

            // 4. 解析 JSON 数组
            return parseEntries(content.strip());

        } catch (Exception e) {
            log.error("提取记忆失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将对话历史格式化为人类可读的文本。
     * <p>
     * 跳过 system 消息和纯 tool_calls 消息，只保留有实际内容的交互。
     */
    private String formatConversation(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            String role = switch (msg.getRole()) {
                case "user" -> "用户";
                case "assistant" -> "Agent";
                case "tool" -> "工具结果";
                case "system" -> null; // 跳过 system 消息
                default -> msg.getRole();
            };

            if (role == null) continue;

            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                sb.append(role).append(": ").append(msg.getContent()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的 JSON 字符串数组。
     * <p>
     * 容错处理：如果 LLM 返回了 Markdown 代码块包裹的 JSON，自动去除。
     */
    private List<String> parseEntries(String jsonText) {
        try {
            // 去除可能的 Markdown 代码块包裹
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceAll("```json\\s*", "")
                        .replaceAll("```\\s*", "")
                        .strip();
            }

            List<String> entries = MAPPER.readValue(jsonText, new TypeReference<List<String>>() {});
            log.info("LLM 提取了 {} 条记忆", entries.size());
            return entries;

        } catch (Exception e) {
            log.warn("解析 LLM 返回的记忆条目失败: {} | 原始内容: {}", e.getMessage(), jsonText);
            return Collections.emptyList();
        }
    }
}
