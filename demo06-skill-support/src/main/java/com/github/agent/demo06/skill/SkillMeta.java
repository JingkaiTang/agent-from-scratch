package com.github.agent.demo06.skill;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Skill 元数据（不可变 POJO）。
 * <p>
 * 对应 SKILL.md 文件的 YAML frontmatter 解析结果。
 * 刻意不持有 Markdown 正文 —— 正文每次通过 {@link SkillRegistry#readBody(String)}
 * 按需从磁盘读取，体现 Skills 机制"元数据 vs 正文分离"的核心教学点。
 */
public final class SkillMeta {

    /** skill 的唯一标识，来自 frontmatter.name；按惯例与目录名一致（不强制校验） */
    private final String name;

    /** skill 的简短描述，来自 frontmatter.description；LLM 判断是否加载的依据 */
    private final String description;

    /** SKILL.md 的绝对路径，用于按需读取正文 */
    private final Path filePath;

    public SkillMeta(String name, String description, Path filePath) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Path getFilePath() { return filePath; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillMeta other)) return false;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() {
        return "SkillMeta{name='" + name + "', description='" + description + "', filePath=" + filePath + "}";
    }
}
