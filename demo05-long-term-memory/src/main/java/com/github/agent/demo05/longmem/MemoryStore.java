package com.github.agent.demo05.longmem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 长期记忆存储层 —— 封装 memories.md 文件的读写操作。
 * <p>
 * 存储路径：{@code ~/.afs/memories.md}
 * <p>
 * 文件格式：
 * <pre>
 * ## 2026-04-14 10:30
 * - 用户名叫小明，是一名 Java 开发者
 * - 用户偏好使用中文回复
 *
 * ## 2026-04-14 11:15
 * - 用户的项目使用 Maven 构建，Java 25
 * </pre>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>单文件存储，简单透明</li>
 *   <li>写入操作为追加模式（append-only），不会修改已有内容</li>
 *   <li>检索操作为逐行 grep 匹配，返回包含关键词的行</li>
 * </ul>
 */
public class MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(MemoryStore.class);

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 记忆文件路径：~/.afs/memories.md */
    private final Path memoriesPath;

    public MemoryStore() {
        this.memoriesPath = Path.of(
                System.getProperty("user.home"), ".afs", "memories.md");
    }

    /**
     * 初始化 —— 确保目录和文件存在。
     * <p>
     * 如果 memories.md 不存在，创建一个带标题的空文件。
     */
    public void init() {
        try {
            Files.createDirectories(memoriesPath.getParent());
            if (!Files.exists(memoriesPath)) {
                Files.writeString(memoriesPath,
                        "# 长期记忆\n\n> 此文件由 Agent 自动维护，记录跨会话的重要信息。\n\n",
                        StandardCharsets.UTF_8);
                log.info("已创建记忆文件: {}", memoriesPath);
            } else {
                log.info("记忆文件已存在: {}", memoriesPath);
            }
        } catch (IOException e) {
            log.error("初始化记忆文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 追加记忆条目到文件末尾。
     * <p>
     * 自动添加时间戳段落头，每个条目以 "- " 开头，一行一条。
     *
     * @param entries 记忆条目列表（每条为一行文本，不需要加 "- " 前缀）
     */
    public void append(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(LocalDateTime.now().format(TIMESTAMP_FMT)).append("\n");
        for (String entry : entries) {
            sb.append("- ").append(entry.strip()).append("\n");
        }
        sb.append("\n");

        try {
            Files.writeString(memoriesPath, sb.toString(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            log.info("已追加 {} 条记忆到 {}", entries.size(), memoriesPath);
        } catch (IOException e) {
            log.error("追加记忆失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据关键词列表搜索记忆文件，返回包含任意关键词的行。
     * <p>
     * 实现方式：逐行读取 memories.md，对每一行做关键词包含匹配（不区分大小写）。
     * 只匹配以 "- " 开头的记忆条目行，跳过标题行和空行。
     *
     * @param keywords 关键词列表
     * @return 命中的记忆条目（去掉 "- " 前缀）；无命中返回空列表
     */
    public List<String> search(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            if (!Files.exists(memoriesPath)) {
                return Collections.emptyList();
            }

            List<String> lines = Files.readAllLines(memoriesPath, StandardCharsets.UTF_8);
            List<String> matches = new ArrayList<>();

            // 将关键词转为小写，用于不区分大小写匹配
            List<String> lowerKeywords = keywords.stream()
                    .map(String::toLowerCase)
                    .toList();

            for (String line : lines) {
                // 只匹配记忆条目行（以 "- " 开头）
                if (!line.startsWith("- ")) {
                    continue;
                }

                String lowerLine = line.toLowerCase();
                for (String keyword : lowerKeywords) {
                    if (lowerLine.contains(keyword)) {
                        // 去掉 "- " 前缀
                        matches.add(line.substring(2).strip());
                        break; // 一行匹配一个关键词即可
                    }
                }
            }

            log.debug("搜索关键词 {} → 命中 {} 条记忆", keywords, matches.size());
            return matches;

        } catch (IOException e) {
            log.error("搜索记忆文件失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 读取记忆文件的全部内容（用于调试或展示）。
     *
     * @return 文件全部内容；文件不存在返回空字符串
     */
    public String readAll() {
        try {
            if (!Files.exists(memoriesPath)) {
                return "";
            }
            return Files.readString(memoriesPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取记忆文件失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 获取记忆文件路径。
     */
    public Path getMemoriesPath() {
        return memoriesPath;
    }
}
