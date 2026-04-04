package com.github.agent.demo01.tool.impl;

import com.github.agent.demo01.tool.Tool;
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
 * 仅凭这一个工具，Agent 就能通过 Shell 命令完成：
 * - 文件读写（cat、echo >、tee）
 * - 目录操作（ls、mkdir、cd）
 * - 进程管理（ps、kill）
 * - 系统信息（uname、df、top）
 * - 以及任何其他 Shell 能做的事情
 * <p>
 * 安全机制：
 * 1. 危险命令拦截（硬拦截，不可绕过）
 * 2. 交互式确认（执行前需用户按 y 确认，可通过环境变量跳过）
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
     * 即使在自动确认模式下也不可绕过。
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // 删除根目录
            Pattern.compile("rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|(-[a-zA-Z]*\\s+)*)/(\\s|$)"),
            // 格式化磁盘
            Pattern.compile("mkfs"),
            // dd 写入磁盘
            Pattern.compile("dd\\s+if="),
            // Fork 炸弹
            Pattern.compile(":\\(\\)\\s*\\{"),
            // chmod 777 根目录
            Pattern.compile("chmod\\s+(-[a-zA-Z]*\\s+)*777\\s+/"),
            // 写入磁盘设备
            Pattern.compile(">\\s*/dev/sd[a-z]"),
            // 清空磁盘设备
            Pattern.compile(">\\s*/dev/nvme"),
            // 删除整个 home 目录
            Pattern.compile("rm\\s+(-[a-zA-Z]*f[a-zA-Z]*\\s+|(-[a-zA-Z]*\\s+)*)~"),
            // 危险的 wget/curl 管道到 sh
            Pattern.compile("(wget|curl).*\\|\\s*(ba)?sh")
    );

    /** 用于读取用户确认输入的 Scanner（从 System.in 读取） */
    private final Scanner confirmScanner;

    public ExecTool() {
        this.confirmScanner = new Scanner(System.in);
    }

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

        // ========== 第一道防线：危险命令拦截（不可绕过） ==========
        String dangerCheck = checkDangerousCommand(command);
        if (dangerCheck != null) {
            return dangerCheck;
        }

        // ========== 第二道防线：交互式确认 ==========
        if (!confirmExecution(command)) {
            return "⛔ 用户取消执行命令: " + command;
        }

        // ========== 执行命令 ==========
        return executeCommand(command);
    }

    /**
     * 检查命令是否匹配危险模式。
     *
     * @return 如果危险返回警告信息，否则返回 null
     */
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

    /**
     * 交互式确认机制。
     * 如果环境变量 AGENT_AUTO_CONFIRM=true，则跳过确认。
     *
     * @return true 表示用户确认执行，false 表示取消
     */
    private boolean confirmExecution(String command) {
        // 检查自动确认模式
        String autoConfirm = System.getenv("AGENT_AUTO_CONFIRM");
        if ("true".equalsIgnoreCase(autoConfirm)) {
            log.info("🔄 自动确认模式，跳过确认: {}", command);
            return true;
        }

        // 交互式确认
        System.out.println("\n⚠️  即将执行命令: " + command);
        System.out.print("确认执行？(y/N) > ");
        System.out.flush();

        try {
            String input = confirmScanner.nextLine().trim();
            return "y".equalsIgnoreCase(input);
        } catch (Exception e) {
            log.warn("读取用户确认输入失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 实际执行 Shell 命令。
     * 使用 ProcessBuilder 启动进程，捕获 stdout 和 stderr，支持超时控制和输出截断。
     */
    private String executeCommand(String command) {
        try {
            // 构建进程：macOS/Linux 用 /bin/sh -c，Windows 用 cmd /c
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }

            // 设置工作目录为当前目录
            pb.directory(new java.io.File(System.getProperty("user.dir")));

            log.info("▶️ 执行命令: {}", command);
            Process process = pb.start();

            // 读取 stdout 和 stderr（使用独立线程避免阻塞）
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdout), "stdout-reader");
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderr), "stderr-reader");
            stdoutThread.start();
            stderrThread.start();

            // 等待进程完成（带超时）
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                // 超时 → 强制终止
                process.destroyForcibly();
                stdoutThread.interrupt();
                stderrThread.interrupt();
                log.warn("⏰ 命令执行超时 ({}秒): {}", TIMEOUT_SECONDS, command);
                return "⏰ 命令执行超时（" + TIMEOUT_SECONDS + " 秒），已强制终止。\n命令: " + command;
            }

            // 等待输出读取线程完成
            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();

            // 组合结果
            return formatResult(exitCode, stdout.toString(), stderr.toString());

        } catch (Exception e) {
            log.error("命令执行异常: {}", e.getMessage(), e);
            return "❌ 命令执行失败: " + e.getMessage();
        }
    }

    /**
     * 从输入流中读取内容到 StringBuilder。
     */
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
            // 忽略流读取异常（进程被终止时可能发生）
        }
    }

    /**
     * 格式化命令执行结果。
     * 包含 exit code、stdout、stderr，并在输出过长时截断。
     */
    private String formatResult(int exitCode, String stdout, String stderr) {
        StringBuilder result = new StringBuilder();

        // 退出码
        result.append("[退出码: ").append(exitCode).append("]\n");

        // stdout
        if (!stdout.isBlank()) {
            String truncatedStdout = truncateIfNeeded(stdout);
            result.append(truncatedStdout);
        }

        // stderr
        if (!stderr.isBlank()) {
            if (!stdout.isBlank()) {
                result.append("\n");
            }
            String truncatedStderr = truncateIfNeeded(stderr);
            result.append("[stderr]\n").append(truncatedStderr);
        }

        // 如果 stdout 和 stderr 都为空
        if (stdout.isBlank() && stderr.isBlank()) {
            result.append("(无输出)");
        }

        return result.toString();
    }

    /**
     * 如果文本超过最大长度，截断并附加提示。
     */
    private String truncateIfNeeded(String text) {
        if (text.length() > MAX_OUTPUT_LENGTH) {
            return text.substring(0, MAX_OUTPUT_LENGTH) + "\n...[输出已截断，共 " + text.length() + " 字符]";
        }
        return text;
    }
}
