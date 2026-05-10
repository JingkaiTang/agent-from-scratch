package com.github.agent.demo06.ui;

import com.github.agent.demo06.skill.SkillMeta;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * SkillSidebar 内的单个 skill 视图 cell。
 * <p>
 * 展示内容：
 * <ul>
 *   <li>左上角徽章（□ 未加载 / ☑ 已加载）</li>
 *   <li>skill name（加粗）</li>
 *   <li>description（灰色小字，自动换行）</li>
 * </ul>
 * 不响应点击——Skills 机制强调"由 LLM 自主决定加载"，用户不能手动触发加载。
 */
public class SkillCell extends VBox {

    /** 描述文字最大宽度：侧边栏 220 - 左右 padding 各 12 = 196 */
    private static final double DESC_MAX_WIDTH = SkillSidebar.SIDEBAR_WIDTH - 24;

    private final Label badgeLabel;
    private final String skillName;

    public SkillCell(SkillMeta meta) {
        this.skillName = meta.getName();

        setPadding(new Insets(10, 12, 10, 12));
        setSpacing(4);
        setStyle("""
                -fx-background-color: #FFFFFF;
                -fx-border-color: #E5E7EB;
                -fx-border-width: 0 0 1 0;
                """);

        // 顶行：徽章 + name
        badgeLabel = new Label("□");
        badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");

        Label nameLabel = new Label(meta.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1F2937;");

        HBox topRow = new HBox(6, badgeLabel, nameLabel);

        // 描述行
        Label descLabel = new Label(meta.getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");
        descLabel.setMaxWidth(DESC_MAX_WIDTH);

        getChildren().addAll(topRow, descLabel);
    }

    public String getSkillName() {
        return skillName;
    }

    /**
     * 切换徽章显示（必须在 JavaFX Application Thread 上调用）。
     *
     * @param loaded 是否已加载
     */
    public void setLoaded(boolean loaded) {
        if (loaded) {
            badgeLabel.setText("☑");
            badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #0891B2; -fx-font-weight: bold;");
        } else {
            badgeLabel.setText("□");
            badgeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #9CA3AF; -fx-font-weight: bold;");
        }
    }
}
