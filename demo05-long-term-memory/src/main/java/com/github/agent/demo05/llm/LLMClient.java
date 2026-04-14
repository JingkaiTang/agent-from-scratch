package com.github.agent.demo05.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.agent.demo05.model.ChatMessage;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM 客户端 —— 封装对 OpenAI 兼容 API 的调用。
 * <p>
 * 支持:
 * - 普通 chat completion（同步）
 * - SSE 流式 chat completion（逐 token 回调）
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
                .readTimeout(300, TimeUnit.SECONDS)  // SSE 流式响应需要更长的读取超时
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用 Chat Completion API（非流式，带 function calling 支持）。
     *
     * @param messages    对话消息列表
     * @param toolsSchema OpenAI 格式的 tools 定义列表，可为 null
     * @return assistant 回复的 ChatMessage（可能包含 toolCalls）
     */
    public ChatMessage chat(List<ChatMessage> messages, List<Map<String, Object>> toolsSchema) throws IOException {
        ObjectNode requestBody = buildRequestBody(messages, toolsSchema, false);
        String jsonBody = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
        log.debug(">>> LLM Request:\n{}", jsonBody);

        Request request = buildHttpRequest(jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("LLM API error " + response.code() + ": " + body);
            }
            log.debug("<<< LLM Response:\n{}", body);

            JsonNode root = MAPPER.readTree(body);
            JsonNode choiceMessage = root.path("choices").get(0).path("message");
            return parseAssistantMessage(choiceMessage);
        }
    }

    /**
     * 调用 Chat Completion API（SSE 流式响应）。
     * <p>
     * 使用 stream=true 发起请求，逐行解析 SSE 事件流。
     * 对于纯文本回复，通过 onToken 回调逐 token 推送；
     * 对于 tool_calls，在流结束后一次性组装返回。
     *
     * @param messages    对话消息列表
     * @param toolsSchema OpenAI 格式的 tools 定义列表，可为 null
     * @param onToken     每收到一个文本 token 时的回调（可为 null）
     * @return assistant 回复的 ChatMessage（完整内容，可能包含 toolCalls）
     */
    public ChatMessage chatStream(List<ChatMessage> messages, List<Map<String, Object>> toolsSchema,
                                   Consumer<String> onToken) throws IOException {
        ObjectNode requestBody = buildRequestBody(messages, toolsSchema, true);
        String jsonBody = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
        log.debug(">>> LLM Stream Request:\n{}", jsonBody);

        Request request = buildHttpRequest(jsonBody);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("LLM API error " + response.code() + ": " + errorBody);
            }

            if (response.body() == null) {
                throw new IOException("LLM API 返回空响应体");
            }

            return parseSSEStream(response.body(), onToken);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构建请求 JSON Body。
     */
    private ObjectNode buildRequestBody(List<ChatMessage> messages, List<Map<String, Object>> toolsSchema,
                                         boolean stream) {
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

        // 流式模式
        if (stream) {
            requestBody.put("stream", true);
        }

        return requestBody;
    }

    /**
     * 构建 HTTP 请求。
     */
    private Request buildHttpRequest(String jsonBody) {
        return new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();
    }

    /**
     * 解析 SSE 事件流，组装完整的 ChatMessage。
     * <p>
     * SSE 格式：
     * <pre>
     * data: {"choices":[{"delta":{"content":"Hello"}}]}
     * data: {"choices":[{"delta":{"content":" World"}}]}
     * data: [DONE]
     * </pre>
     * <p>
     * 对于 tool_calls，delta 中会分多次传递：
     * <pre>
     * data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_xxx","function":{"name":"exec","arguments":""}}]}}]}
     * data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"co"}}]}}]}
     * data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"mmand\""}}]}}]}
     * </pre>
     */
    private ChatMessage parseSSEStream(ResponseBody responseBody, Consumer<String> onToken) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        // tool_calls 增量组装：index -> {id, name, argumentsBuilder}
        Map<Integer, ToolCallAccumulator> toolCallMap = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseBody.byteStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过空行和非 data 行
                if (line.isEmpty() || !line.startsWith("data: ")) {
                    continue;
                }

                String data = line.substring(6).trim(); // 去掉 "data: " 前缀

                // 流结束标志
                if ("[DONE]".equals(data)) {
                    log.debug("<<< SSE Stream [DONE]");
                    break;
                }

                try {
                    JsonNode chunk = MAPPER.readTree(data);
                    JsonNode delta = chunk.path("choices").get(0).path("delta");

                    // 1. 处理文本内容增量
                    if (delta.has("content") && !delta.get("content").isNull()) {
                        String token = delta.get("content").asText();
                        contentBuilder.append(token);
                        if (onToken != null) {
                            onToken.accept(token);
                        }
                    }

                    // 1.5 处理 reasoning_content 增量（thinking 模式，如 qwen3.5）
                    if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                        reasoningBuilder.append(delta.get("reasoning_content").asText());
                    }

                    // 2. 处理 tool_calls 增量
                    if (delta.has("tool_calls")) {
                        for (JsonNode tcDelta : delta.get("tool_calls")) {
                            int index = tcDelta.get("index").asInt();
                            ToolCallAccumulator acc = toolCallMap.computeIfAbsent(index,
                                    k -> new ToolCallAccumulator());

                            // 首次出现时设置 id 和 function name
                            if (tcDelta.has("id")) {
                                acc.id = tcDelta.get("id").asText();
                            }
                            if (tcDelta.has("function")) {
                                JsonNode fn = tcDelta.get("function");
                                if (fn.has("name")) {
                                    acc.name = fn.get("name").asText();
                                }
                                if (fn.has("arguments")) {
                                    acc.arguments.append(fn.get("arguments").asText());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析 SSE chunk 失败: {}, 原始数据: {}", e.getMessage(), data);
                }
            }
        }

        // 组装最终的 ChatMessage
        ChatMessage msg = new ChatMessage();
        msg.setRole("assistant");

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            msg.setContent(content);
        }

        // 设置 reasoning_content（thinking 模式）
        String reasoning = reasoningBuilder.toString();
        if (!reasoning.isEmpty()) {
            msg.setReasoningContent(reasoning);
        }

        // 组装 tool_calls
        if (!toolCallMap.isEmpty()) {
            List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallAccumulator acc : toolCallMap.values()) {
                ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
                tc.setId(acc.id);
                tc.setType("function");

                ChatMessage.FunctionCall fn = new ChatMessage.FunctionCall();
                fn.setName(acc.name);
                fn.setArguments(acc.arguments.toString());
                tc.setFunction(fn);

                toolCalls.add(tc);
            }
            msg.setToolCalls(toolCalls);
            log.debug("<<< SSE Stream 完成，包含 {} 个 tool_calls", toolCalls.size());
        } else {
            log.debug("<<< SSE Stream 完成，文本长度: {}", content.length());
        }

        return msg;
    }

    /**
     * tool_calls 增量累加器 —— 用于在 SSE 流中逐步组装完整的 tool_call。
     */
    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private ObjectNode messageToJson(ChatMessage msg) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role", msg.getRole());

        if (msg.getContent() != null) {
            node.put("content", msg.getContent());
        }

        // assistant 消息回传 reasoning_content（thinking 模式必需，需始终包含）
        if ("assistant".equals(msg.getRole())) {
            node.put("reasoning_content", msg.getReasoningContent() != null ? msg.getReasoningContent() : "");
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

        // 解析 reasoning_content（thinking 模式）
        if (messageNode.has("reasoning_content") && !messageNode.get("reasoning_content").isNull()) {
            msg.setReasoningContent(messageNode.get("reasoning_content").asText());
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
