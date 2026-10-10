package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.platform.MacTitleBar;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual title-bar components at Retina scale, without a JFrame, native controls or a shell. */
public final class TitleBarPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/title-bar").toAbsolutePath();
        Files.createDirectories(output);
        try {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()));
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 80, 100, 130));
                var model = new dev.jasper.app.contributions.Contributions();
                owner.connectContributions(model);
                model.addAction("preview.ssh", "Connect to host…", null, java.util.List.of(), java.util.Optional.empty(), event -> {});
                model.addToolbar(new dev.jasper.app.contributions.ToolbarEntry.Dropdown(
                    dev.jasper.app.platform.AppIcons.named("SERVER"), "Sessions", java.util.List.of("preview.ssh")));
                var root = new JRootPane();
                try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    for (Theme theme : java.util.List.of(Theme.DARK, dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT)) {
                        owner.selectTheme(theme);
                        while (owner.tabStrip().getTabCount() > 1) owner.closeTab(owner.currentTab());
                        owner.currentTab().rename("zsh");
                        render(root, owner, bar, output, theme, "single", 830);
                        owner.newTab(HOME); owner.currentTab().rename("zsh");
                        render(root, owner, bar, output, theme, "shell-tabs", 830);
                        owner.currentTab().rename("Reviewing the notification implementation");
                        owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));
                        owner.currentTab().rename("tmux");
                        owner.chrome().search().setContext("Claude Code — Reviewing the notification implementation", "Claude Code — Reviewing the notification implementation", dev.jasper.app.platform.AppIcons.named("TERMINAL"));
                        render(root, owner, bar, output, theme, "tmux-tabs", 830);
                        render(root, owner, bar, output, theme, "narrow", 450);
                        owner.chrome().search().setContext("staging-01:/srv/app : make test", "staging-01:/srv/app : make test",
                            dev.jasper.app.platform.AppIcons.named("SERVER"));
                        render(root, owner, bar, output, theme, "remote", 830);
                        render(root, owner, bar, output, theme, "remote-narrow", 450);
                        owner.update();
                    }
                }
            });
        } finally { closeOwners(); }
        System.out.println(output);
    }

    private static void render(JRootPane root, WindowContent owner, MacTitleBar bar, Path output, Theme theme, String name, int logicalWidth) {
        int width = com.formdev.flatlaf.util.UIScale.scale(logicalWidth);
        int height = bar.getPreferredSize().height;
        WindowTabsTest.layout(root, width, com.formdev.flatlaf.util.UIScale.scale(620));
        assertThat(bar.getWidth()).isEqualTo(width);
        assertThat(owner.tabStrip().getTabCount()).as("actual tab owner for %s", name).isEqualTo(name.equals("single") ? 1 : 2);
        assertThat(owner.windowTabs().isVisible()).isEqualTo(!name.equals("single"));
        if (owner.windowTabs().isVisible()) {
            assertThat(owner.windowTabs().getWidth()).isEqualTo(width);
            assertThat(owner.windowTabs().getHeight()).isPositive();
        }
        if (name.equals("tmux-tabs")) assertThat(WindowTabsTest.named(owner.windowTabs(), "select:Reviewing the notification implementation")).isNotNull();
        var field = owner.chrome().search();
        assertThat(root.getWidth()).as("containing root width for %s", name).isEqualTo(width);
        assertThat(field.getWidth()).as("visible context control for %s", name).isPositive();
        assertThat(field.getHeight()).isPositive();
        assertThat(field.getX()).isGreaterThanOrEqualTo(0);
        assertThat(field.getX() + field.getWidth()).isLessThanOrEqualTo(width);
        assertThat(field.collapsed()).as("only narrow captures collapse context").isEqualTo(logicalWidth < 500);
        if (name.equals("tmux-tabs")) assertThat(field.contextText()).contains("Claude Code");
        if (name.startsWith("remote")) assertThat(field.contextText()).contains("staging-01");
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); bar.printAll(graphics); }
        finally { graphics.dispose(); }
        try { ImageIO.write(image, "png", output.resolve(theme.name().toLowerCase(Locale.ROOT) + "-" + name + ".png").toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        // Tabs are owned below the titlebar. Preserve their actual region separately from the strip.
        var tabs = owner.windowTabs();
        if (!tabs.isVisible()) return; // One tab intentionally has no strip in the current workspace.
        var tabImage = new BufferedImage(width * 2, tabs.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
        var tabGraphics = tabImage.createGraphics();
        try { tabGraphics.scale(2, 2); tabs.printAll(tabGraphics); }
        finally { tabGraphics.dispose(); }
        try { ImageIO.write(tabImage, "png", output.resolve(theme.name().toLowerCase(Locale.ROOT) + "-" + name + "-window-tabs.png").toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
