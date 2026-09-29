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

/** Actual title-bar components at Retina scale, without a JFrame, native controls or a shell. */
public final class TitleBarPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/title-bar").toAbsolutePath();
        Files.createDirectories(output);
        try {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()));
                var model = new dev.jasper.app.contributions.Contributions();
                owner.connectContributions(model);
                model.addAction("preview.ssh", "Connect to host…", null, java.util.List.of(), java.util.Optional.empty(), event -> {});
                model.addToolbar(new dev.jasper.app.contributions.ToolbarEntry.Dropdown(
                    dev.jasper.app.platform.AppIcons.named("SERVER"), "Sessions", java.util.List.of("preview.ssh")));
                var root = new JRootPane();
                try (var bar = WindowContent.installTitleBar(root, owner, true, title -> {})) {
                    for (Theme theme : java.util.List.of(Theme.DARK, Theme.LIGHT)) {
                        owner.selectTheme(theme);
                        while (owner.tabStrip().getTabCount() > 1) owner.closeTab(owner.currentTab());
                        owner.currentTab().rename("zsh");
                        render(bar, output, theme, "single", 830);
                        owner.newTab(HOME); owner.currentTab().rename("zsh");
                        render(bar, output, theme, "shell-tabs", 830);
                        owner.currentTab().rename("Reviewing the notification implementation");
                        owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));
                        owner.currentTab().rename("tmux");
                        owner.chrome().search().setContext("Claude Code — Reviewing the notification implementation", "Claude Code — Reviewing the notification implementation", dev.jasper.app.platform.AppIcons.named("TERMINAL"));
                        render(bar, output, theme, "tmux-tabs", 830);
                        render(bar, output, theme, "narrow", 450);
                        owner.chrome().search().setContext("staging-01:/srv/app : make test", "staging-01:/srv/app : make test",
                            dev.jasper.app.platform.AppIcons.named("SERVER"));
                        render(bar, output, theme, "remote", 830);
                        render(bar, output, theme, "remote-narrow", 450);
                        owner.update();
                    }
                }
            });
        } finally { closeOwners(); }
        System.out.println(output);
    }

    private static void render(MacTitleBar bar, Path output, Theme theme, String name, int width) {
        int height = bar.getPreferredSize().height;
        WindowTabsTest.layout(bar, width, height);
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); bar.printAll(graphics); }
        finally { graphics.dispose(); }
        try { ImageIO.write(image, "png", output.resolve(theme.name().toLowerCase(Locale.ROOT) + "-" + name + ".png").toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
