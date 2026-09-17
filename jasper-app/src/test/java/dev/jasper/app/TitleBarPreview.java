package dev.jasper.app;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.DesktopTestSupport.*;

/** Actual title-bar components at Retina scale, without a JFrame, native controls or a shell. */
public final class TitleBarPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/title-bar").toAbsolutePath();
        Files.createDirectories(output);
        try {
            edt(() -> {
                var owner = content(launcher(new ArrayDeque<>()));
                var root = new JRootPane();
                try (var bar = MacTitleBar.install(root, owner, true, title -> {})) {
                    for (BuiltinTheme theme : BuiltinTheme.values()) {
                        owner.selectTheme(theme);
                        while (owner.tabStrip().getTabCount() > 1) owner.closeTab(owner.currentTab());
                        owner.currentTab().rename("~ (-zsh)");
                        render(bar, output, theme, "single", 830);
                        owner.newTab(HOME); owner.currentTab().rename("~ (-zsh)");
                        render(bar, output, theme, "shell-tabs", 830);
                        owner.currentTab().rename("Reviewing the notification implementation (tmux)");
                        owner.selectTab((TerminalTab) owner.tabStrip().getComponentAt(0));
                        owner.currentTab().rename("~ (tmux)");
                        render(bar, output, theme, "tmux-tabs", 830);
                        render(bar, output, theme, "narrow", 450);
                    }
                }
            });
        } finally { closeOwners(); }
        System.out.println(output);
    }

    private static void render(MacTitleBar bar, Path output, BuiltinTheme theme, String name, int width) {
        WindowTabsTest.layout(bar, width, 38);
        var image = new BufferedImage(width * 2, 76, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); bar.printAll(graphics); }
        finally { graphics.dispose(); }
        try { ImageIO.write(image, "png", output.resolve(theme.name().toLowerCase(Locale.ROOT) + "-" + name + ".png").toFile()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
