package com.github.agent.demo06.ui;

import com.github.agent.demo06.skill.SkillMeta;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * SkillSidebar 内的单个 skill 视图 cell。
 * <p>
 * 展示内容：
 * <ul>
 *   <li>skill name（加粗，左对齐）</li>
 *   <li>description（灰色小字，自动换行）</li>
 *   <li>加载状态：未加载 = 纯白背景；已加载 = 左侧 3px 青色色条 + 右上角"已加载"药丸标签</li>
 * </ul>
 * 不响应点击 / 没有 checkbox 形态——Skills 机制强调"由 LLM 自主决定加载"，
 * 状态展示刻意回避任何"开关 / 启用"的视觉暗示。
 */
public class SkillCell extends VBox {

    /** 描述文字最大宽度：侧边栏 220 - 左右 padding 各 12 = 196 */
    private static final double DESC_MAX_WIDTH = SkillSidebar.SIDEBAR_WIDTH - 24;

    /** 未加载状态的背景样式（纯白 + 底边分隔线） */
    private static final String STYLE_UNLOADED = """
            -fx-background-color: #FFFFFF;
            -fx-border-color: #E5E7EB;
            -fx-border-width: 0 0 1 0;
            -fx-padding: 10 12 10 12;
            """;

    /** 已加载状态的背景样式（左侧 3px 青色色条 + 浅青色底） */
    private static final String STYLE_LOADED = """
            -fx-background-color: #ECFEFF;
            -fx-border-color: #0891B2 #E5E7EB #E5E7EB #E5E7EB;
            -fx-border-width: 0 0 1 0;
            -fx-padding: 10 12 10 9;
            -fx-border-style: hidden hidden solid hidden;
            """;

    private final String skillName;
    private final Label loadedTag;
    private final Region leftBar;

    public SkillCell(SkillMeta meta) {
        this.skillName = meta.getName();

        setSpacing(4);
        setStyle(STYLE_UNLOADED);

        // 左侧色条（已加载时显示）
        leftBar = new Region();
        leftBar.setPrefWidth(3);
        leftBar.setMinWidth(3);
        leftBar.setMaxWidth(3);
        leftBar.setVisible(false);
        leftBar.setManaged(false);
        leftBar.setStyle("-fx-background-color: #0891B2;");

        // skill name
        Label nameLabel = new Label(meta.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1F2937;");

        // 已加载药丸标签（默认隐藏）
        loadedTag = new Label("已加载");
        loadedTag.setStyle("""
                -fx-font-size: 10px;
                -fx-font-weight: bold;
                -fx-text-fill: #155E75;
                -fx-background-color: #CFFAFE;
                -fx-background-radius: 8;
                -fx-padding: 1 6 1 6;
                """);
        loadedTag.setVisible(false);
        loadedTag.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(6, nameLabel, spacer, loadedTag);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // 描述行
        Label descLabel = new Label(meta.getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");
        descLabel.setMaxWidth(DESC_MAX_WIDTH);

        // 把内容包在 HBox 里，左侧给色条留位
        VBox content = new VBox(4, topRow, descLabel);
        HBox.setHgrow(content, Priority.ALWAYS);
        HBox row = new HBox(0, leftBar, content);
        // 让色条在已加载状态贴边显示，未加载状态只移除色条本身
        row.setPadding(new Insets(0, 0, 0, 0));

        getChildren().add(row);
    }

    public String getSkillName() {
        return skillName;
    }

    /**
     * 切换加载状态显示（必须在 JavaFX Application Thread 上调用）。
     * <p>
     * 已加载：左侧 3px 青色色条 + 右上角"已加载"药丸 + 浅青色背景。
     * 未加载：纯白背景，无标记。
     *
     * @param loaded 是否已加载
     */
    public void setLoaded(boolean loaded) {
        leftBar.setVisible(loaded);
        leftBar.setManaged(loaded);
        loadedTag.setVisible(loaded);
        loadedTag.setManaged(loaded);
        setStyle(loaded ? STYLE_LOADED : STYLE_UNLOADED);
    }
}
