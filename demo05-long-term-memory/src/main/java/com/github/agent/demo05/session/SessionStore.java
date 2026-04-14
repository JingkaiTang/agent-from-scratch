package com.github.agent.demo05.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话持久化存储层 —— 将会话数据以 JSON 文件形式存储到本地文件系统。
 * <p>
 * 存储策略：
 * <ul>
 *   <li>每个会话对应一个独立的 JSON 文件，文件名为 {@code {sessionId}.json}</li>
 *   <li>存储目录为用户主目录下的 {@code .afs/sessions/}（afs = Agent From Scratch）</li>
 *   <li>使用 Jackson 进行 JSON 序列化/反序列化</li>
 *   <li>配置 {@link JavaTimeModule} 以支持 {@code LocalDateTime} 的正确序列化</li>
 * </ul>
 * <p>
 * 容错机制：
 * <ul>
 *   <li>存储目录不存在时自动创建</li>
 *   <li>加载时遇到损坏文件记录警告日志并跳过，不影响其他会话</li>
 * </ul>
 */
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    /** 存储目录：~/.afs/sessions/（afs = Agent From Scratch） */
    private final Path storageDir;

    /** Jackson 序列化器，配置了 JavaTimeModule 以支持 LocalDateTime */
    private final ObjectMapper objectMapper;

    public SessionStore() {
        // 存储目录为用户主目录下的 .afs/sessions/（~/.afs/ 是 Agent From Scratch 的主目录）
        this.storageDir = Path.of(System.getProperty("user.home"), ".afs", "sessions");

        // 配置 Jackson ObjectMapper
        this.objectMapper = new ObjectMapper();
        // 注册 JavaTimeModule，支持 LocalDateTime 序列化/反序列化
        objectMapper.registerModule(new JavaTimeModule());
        // 禁用将日期写为时间戳，改为 ISO-8601 格式字符串（更易读）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 格式化输出，便于调试和学习
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // 确保存储目录存在
        ensureDirectoryExists();
    }

    /**
     * 保存会话到本地文件系统。
     * <p>
     * 将 Session 对象序列化为 JSON，写入 {@code {sessionId}.json} 文件。
     * 如果文件已存在则覆盖（更新）。
     *
     * @param session 要保存的会话
     */
    public void save(Session session) {
        Path filePath = storageDir.resolve(session.getId() + ".json");
        try {
            objectMapper.writeValue(filePath.toFile(), session);
            log.debug("会话已保存: {} -> {}", session.getId(), filePath);
        } catch (IOException e) {
            log.error("保存会话失败: {} -> {}", session.getId(), filePath, e);
        }
    }

    /**
     * 从本地存储目录加载所有已保存的会话。
     * <p>
     * 扫描存储目录下所有 {@code .json} 文件，逐个反序列化为 Session 对象。
     * 遇到损坏或格式错误的文件时，记录警告日志并跳过，不影响其他会话的加载。
     *
     * @return 加载成功的会话列表（可能为空列表）
     */
    public List<Session> loadAll() {
        List<Session> sessions = new ArrayList<>();

        // 如果目录不存在或为空，直接返回空列表
        if (!Files.exists(storageDir) || !Files.isDirectory(storageDir)) {
            log.info("存储目录不存在，返回空会话列表: {}", storageDir);
            return sessions;
        }

        // 扫描目录下所有 .json 文件
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "*.json")) {
            for (Path filePath : stream) {
                try {
                    Session session = objectMapper.readValue(filePath.toFile(), Session.class);
                    sessions.add(session);
                    log.debug("加载会话成功: {} ({})", session.getTitle(), session.getId());
                } catch (Exception e) {
                    // 单个文件损坏不影响其他会话的加载
                    log.warn("加载会话文件失败，已跳过: {} - {}", filePath.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("扫描存储目录失败: {}", storageDir, e);
        }

        log.info("从本地加载了 {} 个会话", sessions.size());
        return sessions;
    }

    /**
     * 删除指定会话的本地存储文件。
     *
     * @param sessionId 要删除的会话 ID
     */
    public void delete(String sessionId) {
        Path filePath = storageDir.resolve(sessionId + ".json");
        try {
            if (Files.deleteIfExists(filePath)) {
                log.info("会话文件已删除: {}", filePath);
            } else {
                log.warn("会话文件不存在，无需删除: {}", filePath);
            }
        } catch (IOException e) {
            log.error("删除会话文件失败: {}", filePath, e);
        }
    }

    /**
     * 确保存储目录存在，不存在则自动创建（包括父目录）。
     */
    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
                log.info("已创建存储目录: {}", storageDir);
            }
        } catch (IOException e) {
            log.error("创建存储目录失败: {}", storageDir, e);
        }
    }
}
