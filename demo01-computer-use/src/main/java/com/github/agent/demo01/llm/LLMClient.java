package com.github.agent.demo01.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.agent.demo01.model.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * LLM 客户端 —— 封装对 OpenAI 兼容 API 的调用。
 * <p>
 * 支持:
 * - 普通 chat completion
 * - function calling (tool_calls)
 * <p>
 * 兼容 OpenAI / DeepSeek / 通义千问 等任何 OpenAI 格式的 API。
 */
public class LLMClient {

    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final OkHttpClient httpClient;

    /**
     * @param apiKey  API Key
     * @param baseUrl API 基础地址，例如 "https://api.openai.com/v1"
     * @param model   模型名称，例如 "gpt-4o"
     */
    public LLMClient(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用 Chat Completion API（带 function calling 支持）。
     *
     * @param messages   对话消息列表
     * @param toolsSchema OpenAI 格式的 tools 定义列表，可为 null
     * @return assistant 回复的 ChatMessage（可能包含 toolCalls）
     */
    public ChatMessage chat(List<ChatMessage> messages, List<Map<String, Object>> toolsSchema) throws IOException {
        // 构建请求 JSON
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);

        // messages 数组
        ArrayNode messagesNode = MAPPER.createArrayNode();
        for (ChatMessage msg : messages) {
            messagesNode.add(messageToJson(msg));
        }
        requestBody.set("messages", messagesNode);

        // tools 数组（可选）
        if (toolsSchema != null && !toolsSchema.isEmpty()) {
            requestBody.set("tools", MAPPER.valueToTree(toolsSchema));
        }

        String jsonBody = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
        log.debug(">>> LLM Request:\n{}", jsonBody);

        // 发送 HTTP 请求
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("LLM API error " + response.code() + ": " + body);
            }
            log.debug("<<< LLM Response:\n{}", body);

            // 解析响应
            JsonNode root = MAPPER.readTree(body);
            JsonNode choiceMessage = root.path("choices").get(0).path("message");
            return parseAssistantMessage(choiceMessage);
        }
    }

    // ---------- 内部方法 ----------

    private ObjectNode messageToJson(ChatMessage msg) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", msg.getRole());

        if (msg.getContent() != null) {
            node.put("content", msg.getContent());
        }

        // assistant 消息携带 tool_calls
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            ArrayNode toolCallsNode = MAPPER.createArrayNode();
            for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                ObjectNode tcNode = MAPPER.createObjectNode();
                tcNode.put("id", tc.getId());
                tcNode.put("type", "function");
                ObjectNode fnNode = MAPPER.createObjectNode();
                fnNode.put("name", tc.getFunction().getName());
                fnNode.put("arguments", tc.getFunction().getArguments());
                tcNode.set("function", fnNode);
                toolCallsNode.add(tcNode);
            }
            node.set("tool_calls", toolCallsNode);
        }

        // tool 消息需要 tool_call_id
        if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
            node.put("tool_call_id", msg.getToolCallId());
        }

        return node;
    }

    private ChatMessage parseAssistantMessage(JsonNode messageNode) {
        ChatMessage msg = new ChatMessage();
        msg.setRole("assistant");

        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
            msg.setContent(messageNode.get("content").asText());
        }

        // 解析 tool_calls
        if (messageNode.has("tool_calls")) {
            List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
            for (JsonNode tcNode : messageNode.get("tool_calls")) {
                ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
                tc.setId(tcNode.get("id").asText());
                tc.setType("function");

                ChatMessage.FunctionCall fn = new ChatMessage.FunctionCall();
                fn.setName(tcNode.path("function").get("name").asText());
                fn.setArguments(tcNode.path("function").get("arguments").asText());
                tc.setFunction(fn);

                toolCalls.add(tc);
            }
            msg.setToolCalls(toolCalls);
        }

        return msg;
    }
}
