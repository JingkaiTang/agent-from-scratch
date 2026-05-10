package com.github.agent.demo06.skill;

import com.github.agent.demo06.core.AgentCallback;
import com.github.agent.demo06.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内置工具 {@code load_skill} —— 让 LLM 按需加载某个 skill 的完整指令到对话上下文。
 * <p>
 * 工作流程：
 * <ol>
 *   <li>LLM 在 system prompt 的"Skills 菜单"里看到 skill 清单</li>
 *   <li>判断某个 skill 相关时，调用 load_skill(name)</li>
 *   <li>本工具从 {@link SkillRegistry} 读出正文返回给 LLM</li>
 *   <li>正文作为 tool 消息留在 conversationHistory 里，LLM 后续推理按正文指令执行</li>
 * </ol>
 * <p>
 * 回调注入：构造时无 callback；由 {@code AgentService.setCallback()} 时回填，
 * 因为 AgentService 的初始化顺序是 [Tool 注册 → AgentLoop → setCallback]。
 */
public class LoadSkillTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(LoadSkillTool.class);

    private final SkillRegistry registry;

    /** 由 AgentService.setCallback 时回填；可能为 null */
    private AgentCallback callback;

    public LoadSkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    public void setCallback(AgentCallback callback) {
        this.callback = callback;
    }

    @Override
    public String name() {
        return "load_skill";
    }

    @Override
    public String description() {
        return "加载指定 skill 的完整指令到对话上下文。调用前你已经在 system prompt 的 Skills 菜单里看过 skill 清单；"
             + "当某个 skill 与当前任务相关时用它；不相关时不要调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> nameProp = new LinkedHashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "要加载的 skill 名（来自 system prompt 中的 Skills 菜单）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", nameProp);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("name"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Object rawName = arguments == null ? null : arguments.get("name");
        if (!(rawName instanceof String nameStr) || nameStr.isBlank()) {
            return "错误：缺少必需参数 'name'。";
        }
        String skillName = nameStr.trim();

        SkillMeta meta = registry.get(skillName);
        if (meta == null) {
            String available = registry.list().stream()
                    .map(SkillMeta::getName)
                    .collect(Collectors.joining(", "));
            String listSuffix = available.isEmpty() ? "（当前无可用 skill）" : available;
            return "错误：未找到名为 '" + skillName + "' 的 skill。\n可用的 skill 有：" + listSuffix + "。";
        }

        try {
            String body = registry.readBody(skillName);
            log.info("✅ 加载 skill: {} ({} 字符)", skillName, body.length());
            if (callback != null) {
                try {
                    callback.onSkillLoad(skillName, meta.getDescription());
                } catch (Exception cbErr) {
                    log.warn("onSkillLoad 回调异常（忽略）：{}", cbErr.getMessage());
                }
            }
            return body;
        } catch (IOException e) {
            return "错误：读取 skill '" + skillName + "' 失败：" + e.getMessage();
        }
    }
}
