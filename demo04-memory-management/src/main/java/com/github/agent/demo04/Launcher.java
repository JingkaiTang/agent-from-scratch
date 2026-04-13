package com.github.agent.demo04;

/**
 * 启动器 —— 绕过 JavaFX 模块系统检查。
 * <p>
 * JavaFX 11+ 要求主类继承 Application 时必须通过模块路径启动，
 * 否则会报 "缺少 JavaFX 运行时组件" 错误。
 * <p>
 * 解决方案：用一个普通类（不继承 Application）作为入口，
 * 间接调用 Demo04App.main()，即可绕过此限制。
 * <p>
 * 使用方式：
 * <pre>
 * mvn compile exec:java                    # 通过 Launcher 启动
 * mvn javafx:run                           # 通过 javafx-maven-plugin 启动（也可以）
 * </pre>
 */
public class Launcher {
    public static void main(String[] args) {
        // 启用 macOS Retina (HiDPI) 高清渲染
        System.setProperty("prism.allowhidpi", "true");
        // 使用 GPU 加速渲染管线（macOS 默认 Metal/OpenGL）
        System.setProperty("prism.order", "es2,sw");

        // macOS Dock 图标设置（Java 9+ Taskbar API）
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                java.io.InputStream iconStream = Launcher.class.getResourceAsStream("/icon.png");
                if (iconStream != null) {
                    taskbar.setIconImage(javax.imageio.ImageIO.read(iconStream));
                    iconStream.close();
                }
            }
        } catch (Exception e) {
            // 非 macOS 或不支持 Taskbar，忽略
        }

        Demo04App.main(args);
    }
}
