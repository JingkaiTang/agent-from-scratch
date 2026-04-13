package com.github.agent.demo04.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用配置加载器 —— 从 {@code ~/.afs/config.json} 读取配置。
 * <p>
 * 配置文件不存在时自动创建默认配置；文件损坏时使用默认值并记录警告。
 * <p>
 * 当前管理的配置项：
 * <ul>
 *   <li>{@code memory.maxContextTokens} —— 上下文 token 上限（默认 8000）</li>
 *   <li>{@code memory.compactionThreshold} —— 压缩触发阈值（默认 0.8）</li>
 *   <li>{@code memory.keepRecentRounds} —— 保留最近对话轮数（默认 3）</li>
 * </ul>
 * <p>
 * 优先级：{@code ~/.afs/config.json} > 代码默认值
 */
public class AppConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(AppConfigLoader.class);

    /** 配置文件路径：~/.afs/config.json */
    private static final Path CONFIG_PATH = Path.of(
            System.getProperty("user.home"), ".afs", "config.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ========== 默认值 ==========
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 8000;
    private static final double DEFAULT_COMPACTION_THRESHOLD = 0.8;
    private static final int DEFAULT_KEEP_RECENT_ROUNDS = 3;

    // ========== 加载后的配置值 ==========
    private int maxContextTokens = DEFAULT_MAX_CONTEXT_TOKENS;
    private double compactionThreshold = DEFAULT_COMPACTION_THRESHOLD;
    private int keepRecentRounds = DEFAULT_KEEP_RECENT_ROUNDS;

    /**
     * 从 {@code ~/.afs/config.json} 加载配置。
     * <p>
     * 如果配置文件不存在，自动创建默认配置文件；
     * 如果文件存在但解析失败，使用默认值并记录警告。
     *
     * @return 当前实例（支持链式调用）
     */
    public AppConfigLoader load() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                createDefaultConfig();
                log.info("配置文件不存在，已创建默认配置: {}", CONFIG_PATH);
                return this;
            }

            JsonNode root = MAPPER.readTree(CONFIG_PATH.toFile());
            JsonNode memoryNode = root.path("memory");

            if (!memoryNode.isMissingNode()) {
                if (memoryNode.has("maxContextTokens")) {
                    maxContextTokens = memoryNode.get("maxContextTokens").asInt(DEFAULT_MAX_CONTEXT_TOKENS);
                }
                if (memoryNode.has("compactionThreshold")) {
                    compactionThreshold = memoryNode.get("compactionThreshold").asDouble(DEFAULT_COMPACTION_THRESHOLD);
                }
                if (memoryNode.has("keepRecentRounds")) {
                    keepRecentRounds = memoryNode.get("keepRecentRounds").asInt(DEFAULT_KEEP_RECENT_ROUNDS);
                }
            }

            log.info("配置已加载: maxContextTokens={}, compactionThreshold={}, keepRecentRounds={}",
                    maxContextTokens, compactionThreshold, keepRecentRounds);

        } catch (Exception e) {
            log.warn("加载配置文件失败，使用默认值: {} - {}", CONFIG_PATH, e.getMessage());
        }

        return this;
    }

    /**
     * 创建默认配置文件。
     */
    private void createDefaultConfig() throws IOException {
        // 确保目录存在
        Files.createDirectories(CONFIG_PATH.getParent());

        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode memoryNode = root.putObject("memory");
        memoryNode.put("maxContextTokens", DEFAULT_MAX_CONTEXT_TOKENS);
        memoryNode.put("compactionThreshold", DEFAULT_COMPACTION_THRESHOLD);
        memoryNode.put("keepRecentRounds", DEFAULT_KEEP_RECENT_ROUNDS);

        MAPPER.writeValue(CONFIG_PATH.toFile(), root);
    }

    // ========== Getter ==========

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public double getCompactionThreshold() {
        return compactionThreshold;
    }

    public int getKeepRecentRounds() {
        return keepRecentRounds;
    }
}
