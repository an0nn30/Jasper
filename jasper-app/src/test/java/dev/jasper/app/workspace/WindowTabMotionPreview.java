package dev.jasper.app.workspace;

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

/**
 * Actual headless Swing frames of a window's tab strip at deterministic transition times; no native window or shell
 * launch. The window's own strip runs on the system clock, so a strip over the same tabs on the preview's clock stands
 * in its place in each frame.
 */
public final class WindowTabMotionPreview {
    private WindowTabMotionPreview() {}
    public static void main(String[] arguments) throws Exception {
        Path output = Path.of(arguments.length == 0 ? "build/render-preview/tab-motion" : arguments[0]);
        Files.createDirectories(output);
        try {
            edt(() -> {
                for (String appearance : new String[]{"dark", "light", "glass"}) {
                    var owner = content(launcher(new ArrayDeque<>()));
                    owner.selectVariant(!appearance.equals("light"));
                    if (appearance.equals("glass")) owner.applyBackgroundOpacity(.55);
                    var model = new Contributions(); owner.connectContributions(model);
                    owner.closeTab(owner.currentTerminal());
                    var first = model.tabs().open("preview", "first", "tmux", owner.id(), terminalText());
                    first.select();
                    var root = new JRootPane();
                    WindowContent.installTitleBar(root, owner, true, title -> {});
                    owner.installRootBindings(root);
                    root.setSize(800, 420); LayoutTestSupport.layoutTree(root);
                    var clock = new AtomicLong();
                    var strip = new WindowTabs(owner.parentTabs().bar(), clock::get);
                    WorkspaceTheme.apply(strip, owner.theme());
                    strip.setSize(owner.parentTabs().getWidth(), strip.getPreferredSize().height);
                    strip.refresh();
                    render(root, owner, strip, output, appearance + "-single");
                    var second = model.tabs().open("preview", "second", "amp", owner.id(), terminalText());
                    strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-reveal");
                    var third = model.tabs().open("preview", "third", "pve-root", owner.id(), terminalText());
                    strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-open");
                    third.requestClose(); strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-close");
                    first.select(); strip.refresh(); frames(root, owner, strip, clock, output, appearance + "-switch");
                    second.requestClose(); strip.refresh(); render(root, owner, strip, output, appearance + "-single-again");
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
    private static void frames(JRootPane root, WindowContent owner, WindowTabs strip, AtomicLong clock, Path output, String prefix) {
        long start = clock.get();
        for (int frame = 0; frame <= 60; frame++) {
            clock.set(start + frame * 1_000_000_000L / 60); strip.animate(clock.get());
            render(root, owner, strip, output, prefix + String.format("-%03d", frame));
        }
    }
    /** The window with the preview's strip painted where the window's own strip shows; the window alone while it hides. */
    private static void render(JRootPane root, WindowContent owner, WindowTabs strip, Path output, String name) {
        LayoutTestSupport.layoutTree(root);
        WindowTabs own = owner.windowTabs();
        Rectangle at = own.isVisible() && own.getHeight() > 0 ? SwingUtilities.convertRectangle(own.getParent(), own.getBounds(), root) : null;
        var image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(2, 2);
            if (at != null) own.setVisible(false);
            root.printAll(g);
            if (at != null) {
                own.setVisible(true);
                var inStrip = (Graphics2D) g.create(at.x, at.y, at.width, at.height);
                try { strip.printAll(inStrip); } finally { inStrip.dispose(); }
            }
        } finally { g.dispose(); }
        try {
            ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
            if (at != null) ImageIO.write(image.getSubimage(at.x * 2, at.y * 2, at.width * 2, at.height * 2),
                "png", output.resolve(name + "-strip.png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
