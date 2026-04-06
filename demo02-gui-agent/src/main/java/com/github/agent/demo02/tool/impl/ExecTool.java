package com.github.agent.demo02.tool.impl;

import com.github.agent.demo02.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Shell 命令执行工具 —— 让 Agent 能操作电脑的核心能力。
 * <p>
 * 安全机制：
 * 1. 危险命令拦截（硬拦截，不可绕过）
 * 2. 交互式确认（GUI 模式下默认自动确认）
 * 3. 超时控制（默认 30 秒）
 * 4. 输出截断（默认 10000 字符）
 */
public class ExecTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ExecTool.class);

    /** 命令执行超时时间（秒） */
    private static final int TIMEOUT_SECONDS = 30;

    /** 输出最大长度（字符数） */
    private static final int MAX_OUTPUT_LENGTH = 10000;

    /** 是否为 Windows 操作系统 */
    private static final boolean IS_WINDOWS = System.getProperty("os.name")
            .toLowerCase().contains("win");

    /**
     * 危险命令模式列表 —— 匹配到任何一个都会被拒绝执行。
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|(-[a-zA-Z]*\\s+)*)/(\\s|$)"),
            Pattern.compile("mkfs"),
            Pattern.compile("dd\\s+if="),
            Pattern.compile(":\\(\\)\\s*\\{"),
            Pattern.compile("chmod\\s+(-[a-zA-Z]*\\s+)*777\\s+/"),
            Pattern.compile(">\\s*/dev/sd[a-z]"),
            Pattern.compile(">\\s*/dev/nvme"),
            Pattern.compile("rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|(-[a-zA-Z]*\\s+)*)~"),
            Pattern.compile("(wget|curl).*\\|\\s*(ba)?sh")
    );

    @Override
    public String name() {
        return "exec";
    }

    @Override
    public String description() {
        return "在操作系统上执行 Shell 命令。可以用来完成任何操作系统任务，" +
                "例如：查看文件(cat)、列出目录(ls)、创建文件(echo > / tee)、" +
                "创建目录(mkdir)、查看进程(ps)、查看磁盘(df)等。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "command", Map.of(
                        "type", "string",
                        "description", "要执行的 Shell 命令，例如 'ls -la' 或 'cat /etc/hosts'"
                )
        ));
        schema.put("required", List.of("command"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        if (command == null || command.isBlank()) {
            return "错误: 请提供要执行的命令";
        }

        // 危险命令拦截
        String dangerCheck = checkDangerousCommand(command);
        if (dangerCheck != null) {
            return dangerCheck;
        }

        // GUI 模式下默认自动确认，不再需要 Scanner 交互
        return executeCommand(command);
    }

    private String checkDangerousCommand(String command) {
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                log.warn("🚫 危险命令被拦截: {}", command);
                return "🚫 安全警告: 该命令被识别为危险操作，已拒绝执行。\n" +
                        "命令: " + command + "\n" +
                        "匹配的危险模式: " + pattern.pattern();
            }
        }
        return null;
    }

    private String executeCommand(String command) {
        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }

            pb.directory(new java.io.File(System.getProperty("user.dir")));

            log.info("▶️ 执行命令: {}", command);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdout), "stdout-reader");
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderr), "stderr-reader");
            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                stdoutThread.interrupt();
                stderrThread.interrupt();
                log.warn("⏰ 命令执行超时 ({}秒): {}", TIMEOUT_SECONDS, command);
                return "⏰ 命令执行超时（" + TIMEOUT_SECONDS + " 秒），已强制终止。\n命令: " + command;
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();
            return formatResult(exitCode, stdout.toString(), stderr.toString());

        } catch (Exception e) {
            log.error("命令执行异常: {}", e.getMessage(), e);
            return "❌ 命令执行失败: " + e.getMessage();
        }
    }

    private void readStream(java.io.InputStream inputStream, StringBuilder output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append("\n");
                }
                output.append(line);
            }
        } catch (Exception e) {
            // 忽略流读取异常
        }
    }

    private String formatResult(int exitCode, String stdout, String stderr) {
        StringBuilder result = new StringBuilder();
        result.append("[退出码: ").append(exitCode).append("]\n");

        if (!stdout.isBlank()) {
            result.append(truncateIfNeeded(stdout));
        }

        if (!stderr.isBlank()) {
            if (!stdout.isBlank()) {
                result.append("\n");
            }
            result.append("[stderr]\n").append(truncateIfNeeded(stderr));
        }

        if (stdout.isBlank() && stderr.isBlank()) {
            result.append("(无输出)");
        }

        return result.toString();
    }

    private String truncateIfNeeded(String text) {
        if (text.length() > MAX_OUTPUT_LENGTH) {
            return text.substring(0, MAX_OUTPUT_LENGTH) + "\n...[输出已截断，共 " + text.length() + " 字符]";
        }
        return text;
    }
}
