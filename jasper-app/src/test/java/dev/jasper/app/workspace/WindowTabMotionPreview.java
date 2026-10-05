package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.swing.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Actual headless Swing frames at deterministic transition times; no native window or shell launch. */
public final class WindowTabMotionPreview {
    private WindowTabMotionPreview() {}
    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments.length == 0 ? "build/render-preview/tab-motion" : arguments[0]);
        Files.createDirectories(output);
        try {
            edt(() -> {
                for (String appearance : new String[]{"dark", "light", "glass"}) {
                    var owner = content(launcher(new ArrayDeque<>()));
                    owner.selectTheme(appearance.equals("light") ? dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT : Theme.DARK);
                    if (appearance.equals("glass")) owner.applyBackgroundOpacity(.55);
                    var model = new Contributions(); owner.connectContributions(model);
                    owner.closeTab(owner.currentTab());
                    var first = model.content().open("preview", "first", "tmux", owner.id(), terminalText());
                    first.select();
                    var clock = new AtomicLong();
                    var strip = new WindowTabs(owner, clock::get);
                    var original = owner.windowTabs(); var column = original.getParent();
                    column.remove(original); column.add(strip, BorderLayout.NORTH);
                    var root = new JRootPane(); root.setContentPane(owner);
                    WindowContent.installTitleBar(root, owner, true, title -> {});
                    owner.installRootBindings(root);
                    strip.refresh(); root.setSize(800, 420); LayoutTestSupport.layoutTree(root);
                    render(root, strip, output, appearance + "-single");
                    var second = model.content().open("preview", "second", "amp", owner.id(), terminalText());
                    strip.refresh(); frames(root, strip, clock, output, appearance + "-reveal");
                    var third = model.content().open("preview", "third", "pve-root", owner.id(), terminalText());
                    strip.refresh(); frames(root, strip, clock, output, appearance + "-open");
                    third.requestClose(); strip.refresh(); frames(root, strip, clock, output, appearance + "-close");
                    first.select(); strip.refresh(); frames(root, strip, clock, output, appearance + "-switch");
                    second.requestClose(); strip.refresh(); render(root, strip, output, appearance + "-single-again");
                    strip.close();
                }
            });
        } finally { closeOwners(); }
        System.out.println(output.toAbsolutePath());
    }
    private static JComponent terminalText() {
        var text = new JTextArea("~/projects/jasper on codex/alacritty-tabs\n\n$ ./gradlew check\n\nBUILD SUCCESSFUL\n\nTerminal workspace ready.\n\n$ ");
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        text.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14)); text.setEditable(false);
        return text;
    }
    private static void frames(JRootPane root, WindowTabs strip, AtomicLong clock, Path output, String prefix) {
        long start = clock.get();
        for (int frame = 0; frame <= 60; frame++) {
            clock.set(start + frame * 1_000_000_000L / 60); strip.animate(clock.get());
            render(root, strip, output, prefix + String.format("-%03d", frame));
        }
    }
    private static void render(JRootPane root, WindowTabs strip, Path output, String name) {
        LayoutTestSupport.layoutTree(root);
        var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); root.printAll(g); } finally { g.dispose(); }
        Point position = SwingUtilities.convertPoint(strip, 0, 0, root);
        try {
            ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
            if (strip.isVisible() && strip.getHeight() > 0) ImageIO.write(image.getSubimage(position.x * 2, position.y * 2, strip.getWidth() * 2, strip.getHeight() * 2),
                "png", output.resolve(name + "-strip.png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
