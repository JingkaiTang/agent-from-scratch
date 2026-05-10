package com.github.agent.demo06.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 扫描 {@code ~/.afs/skills/} 加载所有 skill 的元数据。
 * <p>
 * 首次运行（目录不存在）时，自动从 classpath {@code /skills/} 拷贝内置示例。
 * 每个 skill 占一个子目录，内含 {@code SKILL.md} 文件，文件首部为 YAML frontmatter
 * （仅识别 {@code name} 和 {@code description} 两个字段）。
 * <p>
 * 解析失败的 skill 会被跳过并 warn 日志，不让启动崩溃。
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    /** skill 根目录：~/.afs/skills/ */
    private static final Path SKILLS_DIR = Path.of(
            System.getProperty("user.home"), ".afs", "skills");

    /** 内置示例 skill 的名字（对应 classpath 下 /skills/<name>/SKILL.md） */
    private static final List<String> BUILT_IN_SKILLS = List.of(
            "code-review", "translate-zh-en", "summarize-long-text");

    /**
     * 首启拷贝 + 扫描 + 解析，返回所有合法 skill 的元数据列表。
     */
    public List<SkillMeta> loadAll() {
        bootstrapDefaults();

        List<SkillMeta> result = new ArrayList<>();
        if (!Files.isDirectory(SKILLS_DIR)) {
            log.warn("skills 目录不存在或不可访问：{}", SKILLS_DIR);
            return result;
        }

        try (Stream<Path> entries = Files.list(SKILLS_DIR)) {
            for (Path entry : entries.sorted().toList()) {
                if (!Files.isDirectory(entry)) continue;
                Path skillMd = entry.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    log.warn("跳过 {}：未找到 SKILL.md", entry.getFileName());
                    continue;
                }
                try {
                    SkillMeta meta = parseFrontmatter(skillMd);
                    if (meta == null) continue;
                    result.add(meta);
                } catch (Exception e) {
                    log.warn("解析 skill '{}' 失败：{}", entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("扫描 skills 目录失败：{}", e.getMessage());
        }

        return result;
    }

    /**
     * 首次启动：如果 {@code ~/.afs/skills/} 不存在，创建目录并从 classpath 拷贝内置示例。
     * 如果目录已存在（即使为空），不做任何事，避免覆盖用户的"我故意删光"决定。
     */
    private void bootstrapDefaults() {
        if (Files.exists(SKILLS_DIR)) {
            return;
        }
        try {
            Files.createDirectories(SKILLS_DIR);
            log.info("首次启动，已创建 skills 目录：{}", SKILLS_DIR);
            for (String name : BUILT_IN_SKILLS) {
                copyBuiltIn(name);
            }
        } catch (IOException e) {
            log.error("创建 skills 目录或拷贝内置示例失败（启动继续）：{}", e.getMessage());
        }
    }

    /** 从 classpath 拷贝 /skills/&lt;name&gt;/SKILL.md 到 ~/.afs/skills/&lt;name&gt;/SKILL.md */
    private void copyBuiltIn(String skillName) {
        String resource = "/skills/" + skillName + "/SKILL.md";
        try (InputStream in = SkillLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("classpath 内置 skill 资源不存在：{}", resource);
                return;
            }
            Path target = SKILLS_DIR.resolve(skillName).resolve("SKILL.md");
            Files.createDirectories(target.getParent());
            // bootstrapDefaults 仅在 SKILLS_DIR 不存在时调用，所以 target 一定是新建——无需 REPLACE_EXISTING
            Files.copy(in, target);
            log.info("已拷贝内置 skill: {} → {}", skillName, target);
        } catch (IOException e) {
            log.warn("拷贝内置 skill '{}' 失败：{}", skillName, e.getMessage());
        }
    }

    /**
     * 解析单个 SKILL.md 的 YAML frontmatter。
     * <p>
     * 格式要求：
     * <pre>
     * ---
     * name: xxx
     * description: xxx
     * ---
     *
     * # 正文 Markdown...
     * </pre>
     * 仅识别 name 和 description 两个字段，其它忽略。缺失必填字段则返回 null 并 warn。
     */
    private SkillMeta parseFrontmatter(Path skillMd) throws IOException {
        String name = null;
        String description = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(skillMd), StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.trim().equals("---")) {
                log.warn("跳过 {}：首行不是 '---'", skillMd);
                return null;
            }
            boolean closed = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equals("---")) {
                    closed = true;
                    break;
                }
                int colonIdx = line.indexOf(':');
                if (colonIdx < 0) continue;
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (key.equals("name")) name = value;
                else if (key.equals("description")) description = value;
            }
            if (!closed) {
                log.warn("跳过 {}：frontmatter 未闭合（找不到第二个 '---'）", skillMd);
                return null;
            }
        }
        if (name == null || name.isBlank()) {
            log.warn("跳过 {}：frontmatter 缺少必填字段 'name'", skillMd);
            return null;
        }
        if (description == null || description.isBlank()) {
            log.warn("跳过 {}：frontmatter 缺少必填字段 'description'", skillMd);
            return null;
        }
        return new SkillMeta(name, description, skillMd);
    }
}
