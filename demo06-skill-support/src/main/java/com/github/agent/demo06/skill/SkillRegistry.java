package com.github.agent.demo06.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills 进程内缓存 + system prompt 菜单生成 + 正文按需读取。
 * <p>
 * 启动时由 {@link SkillLoader#loadAll()} 填充；运行时对外提供三件事：
 * <ul>
 *   <li>{@link #list()} —— 供 UI 遍历所有 skill</li>
 *   <li>{@link #get(String)} / {@link #readBody(String)} —— 供 LoadSkillTool 查找并按需读正文</li>
 *   <li>{@link #toPromptMenu()} —— 生成注入 system prompt 的菜单文本</li>
 * </ul>
 */
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    /** 按注册顺序保留（LinkedHashMap） */
    private final Map<String, SkillMeta> byName = new LinkedHashMap<>();

    /**
     * 注册一批 skill。重名时后注册者覆盖前者（由 SkillLoader 决定扫描顺序），同时 warn 提示。
     */
    public void register(List<SkillMeta> metas) {
        for (SkillMeta m : metas) {
            if (byName.containsKey(m.getName())) {
                log.warn("skill '{}' 重名，后注册者覆盖前者", m.getName());
            }
            byName.put(m.getName(), m);
        }
    }

    public List<SkillMeta> list() {
        return new ArrayList<>(byName.values());
    }

    public SkillMeta get(String name) {
        return byName.get(name);
    }

    /**
     * 按需读取指定 skill 的 SKILL.md 完整内容（包括 frontmatter 部分）。
     * 调用方（如 LoadSkillTool）负责异常处理。
     */
    public String readBody(String name) throws IOException {
        SkillMeta meta = byName.get(name);
        if (meta == null) {
            throw new IOException("skill not found: " + name);
        }
        return Files.readString(meta.getFilePath(), StandardCharsets.UTF_8);
    }

    /**
     * 生成注入 system prompt 尾部的 skill 菜单文本。
     * <p>
     * 如果当前没有任何可用 skill，返回空串（调用方判断后决定是否追加）。
     */
    public String toPromptMenu() {
        if (byName.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你可以按需加载以下 skill 来获得专门的指令和工作流程：\n\n");
        for (SkillMeta m : byName.values()) {
            sb.append("- ").append(m.getName()).append(": ").append(m.getDescription()).append("\n");
        }
        sb.append("\n");
        sb.append("当你判断某个 skill 与当前任务相关时，调用工具 load_skill(name) 加载它的完整指令。\n");
        sb.append("加载后请严格按指令执行。不相关时无需加载，直接回复即可。");
        return sb.toString();
    }
}
