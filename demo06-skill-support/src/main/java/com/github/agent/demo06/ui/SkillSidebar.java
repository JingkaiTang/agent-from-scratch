package com.github.agent.demo06.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agent.demo06.agent.AgentService;
import com.github.agent.demo06.model.ChatMessage;
import com.github.agent.demo06.session.Session;
import com.github.agent.demo06.session.SessionChangeListener;
import com.github.agent.demo06.skill.SkillMeta;
import com.github.agent.demo06.skill.SkillRegistry;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 聊天窗口右侧的 Skills 面板。
 * <p>
 * 职责：
 * <ul>
 *   <li>启动时遍历 {@link SkillRegistry} 为每个 skill 构建一个 {@link SkillCell}</li>
 *   <li>监听会话切换事件：重算当前会话的"已加载 skill 集合"并刷新所有徽章</li>
 *   <li>被 {@code ChatWindow.onSkillLoad} 回调转发通知：把对应 cell 的徽章改为 ☑</li>
 * </ul>
 * <p>
 * 已加载集合不持久化——纯函数式从当前会话的 {@link ChatMessage} 历史推导，
 * 贯彻设计文档中"skill = 对话历史的一部分"的心智模型。
 */
public class SkillSidebar extends VBox {

    private static final Logger log = LoggerFactory.getLogger(SkillSidebar.class);

    /** 侧边栏固定宽度 */
    private static final double SIDEBAR_WIDTH = 220;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentService agentService;

    /** 所有 skill cell 的索引（by skill name） */
    private final Map<String, SkillCell> cellByName = new HashMap<>();

    /** 当前会话已加载的 skill 集合（刷新徽章用） */
    private Set<String> loadedSkills = new HashSet<>();

    public SkillSidebar(AgentService agentService) {
        this.agentService = agentService;
        buildUI();
        bindEvents();
        // 初始加载：根据当前会话计算已加载集合
        Session current = agentService.getCurrentSession();
        if (current != null) {
            refreshLoadedFromSession(current);
        }
    }

    private void buildUI() {
        setPrefWidth(SIDEBAR_WIDTH);
        setMinWidth(SIDEBAR_WIDTH);
        setMaxWidth(SIDEBAR_WIDTH);
        setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 0 1;");

        // 标题
        Label title = new Label("🧩 Skills");
        title.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-text-fill: #333;
                -fx-padding: 16 12 12 12;
                """);

        // 列表容器
        VBox listBox = new VBox();
        listBox.setStyle("-fx-background-color: #FFFFFF;");

        SkillRegistry registry = agentService.getSkillRegistry();
        if (registry.list().isEmpty()) {
            Label emptyLabel = new Label("(当前无可用 skill)");
            emptyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9CA3AF; -fx-padding: 20 12;");
            listBox.getChildren().add(emptyLabel);
        } else {
            for (SkillMeta meta : registry.list()) {
                SkillCell cell = new SkillCell(meta);
                cellByName.put(meta.getName(), cell);
                listBox.getChildren().add(cell);
            }
        }

        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; -fx-border-width: 0;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(title, scrollPane);
    }

    private void bindEvents() {
        // 注册到 SessionChangeListener：会话切换时重算已加载集合
        agentService.addSessionChangeListener(new SessionChangeListener() {
            @Override
            public void onSessionListChanged() {
                // 列表变化（创建/删除/排序）时不必重算徽章——徽章只跟当前会话有关
            }

            @Override
            public void onActiveSessionChanged(Session session) {
                refreshLoadedFromSession(session);
            }
        });
    }

    /**
     * 对外：当 LLM 通过 load_skill 成功加载某个 skill 时，
     * 由 ChatWindow 在 callback.onSkillLoad 里转发给本方法。
     * <p>
     * 必须在 JavaFX Application Thread 上调用。
     */
    public void onSkillLoadEvent(String skillName) {
        loadedSkills.add(skillName);
        SkillCell cell = cellByName.get(skillName);
        if (cell != null) {
            cell.setLoaded(true);
        }
    }

    /**
     * 扫描当前会话的历史，计算已加载集合并刷新所有徽章。
     */
    private void refreshLoadedFromSession(Session session) {
        Set<String> loaded = session == null
                ? new HashSet<>()
                : computeLoadedSkills(session.getMessages());
        Platform.runLater(() -> {
            this.loadedSkills = loaded;
            cellByName.forEach((n, cell) -> cell.setLoaded(loaded.contains(n)));
        });
    }

    /**
     * 从对话历史里推导"已成功加载过的 skill 名集合"。
     * <p>
     * 判定规则：
     * <ol>
     *   <li>遍历 assistant 消息中的 toolCalls，筛出 function.name == "load_skill" 的调用</li>
     *   <li>从 arguments JSON 中提取 name 字段</li>
     *   <li>通过 tool_call_id 配对其后的 tool 消息</li>
     *   <li>tool 消息内容不以 "错误：" 开头者视为成功加载</li>
     * </ol>
     */
    static Set<String> computeLoadedSkills(List<ChatMessage> history) {
        Set<String> loaded = new HashSet<>();
        if (history == null) return loaded;

        // 先收集所有 load_skill 的 tool_call: id → requested skill name
        Map<String, String> pendingByCallId = new HashMap<>();
        for (ChatMessage msg : history) {
            if (!"assistant".equals(msg.getRole())) continue;
            if (msg.getToolCalls() == null) continue;
            for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                if (tc.getFunction() == null) continue;
                if (!"load_skill".equals(tc.getFunction().getName())) continue;
                String args = tc.getFunction().getArguments();
                String skillName = extractSkillNameFromArgs(args);
                if (skillName != null) {
                    pendingByCallId.put(tc.getId(), skillName);
                }
            }
        }

        // 再遍历 tool 消息，按 tool_call_id 配对，判断是否成功
        for (ChatMessage msg : history) {
            if (!"tool".equals(msg.getRole())) continue;
            String callId = msg.getToolCallId();
            if (!pendingByCallId.containsKey(callId)) continue;
            String content = msg.getContent();
            if (content != null && !content.startsWith("错误：")) {
                loaded.add(pendingByCallId.get(callId));
            }
        }
        return loaded;
    }

    private static String extractSkillNameFromArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return null;
        try {
            Map<String, Object> map = MAPPER.readValue(argsJson, new TypeReference<>() {});
            Object v = map.get("name");
            return (v instanceof String s && !s.isBlank()) ? s.trim() : null;
        } catch (Exception e) {
            log.debug("解析 load_skill arguments 失败：{}", e.getMessage());
            return null;
        }
    }
}
