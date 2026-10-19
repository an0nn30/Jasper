package dev.jasper.app.workspace;

import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import static dev.jasper.app.workspace.DesktopTestSupport.*;

/** Actual Swing workspace and terminal renders; no native window or user's login shell. */
public final class AlacrittyTabsPreview {
    private AlacrittyTabsPreview() {}

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "build/render-preview/alacritty-tabs" : args[0]);
        Files.createDirectories(output);
        var pending = new ArrayDeque<Runnable>();
        var sessions = new ArrayList<TerminalSession>();
        WindowContent[] owners = new WindowContent[1];
        JRootPane[] roots = new JRootPane[1];
        try {
            edt(() -> {
                var launcher = new ShellLauncher(pending::add, directory -> {
                    try {
                        String script = "printf '\\033[36m~/projects/jasper\\033[0m on \\033[32mcodex/alacritty-tabs\\033[0m\\r\\n"
                            + "$ ./gradlew check\\r\\n\\r\\n"
                            + "\\033[32mBUILD SUCCESSFUL\\033[0m\\r\\n"
                            + "Terminal workspace ready.\\r\\n\\r\\n$ \\033]2;fixture\\007'; read answer";
                        var session = TerminalSession.start(SessionLaunchOptions.builder()
                            .command(List.of("/bin/sh", "-c", script)).environment(System.getenv())
                            .workingDirectory(HOME).grid(new GridSize(100, 30)).scrollback(100).build());
                        sessions.add(session); return session;
                    } catch (Exception failure) { throw new CompletionException(failure); }
                }, "sh");
                var owner = content(launcher); owners[0] = owner;
                owner.setAnimations(new dev.jasper.app.config.AnimationSettings(false, 150, 120, 130));
                owner.newTab(HOME);
                roots[0] = new JRootPane();
                WindowContent.installTitleBar(roots[0], owner, true, title -> {});
                owner.installRootBindings(roots[0]);
            });
            while (!pending.isEmpty()) pending.remove().run();
            until(() -> owners[0].terminalTabs().stream().allMatch(tab -> tab.focusedPane().view() != null)
                && sessions.stream().allMatch(session -> session.title().equals("fixture")));
            edt(() -> {
                var owner = owners[0]; var root = roots[0];
                String[] titles = {"zsh", "amp"};
                for (int i = 0; i < titles.length; i++) owner.terminalTabs().get(i).rename(titles[i]);
                owner.selectVariant(true); render(root, owner, output, "dark-two", 1040);
                owner.selectVariant(false);
                render(root, owner, output, "light-two", 1040);
                var contributions = new dev.jasper.app.contributions.Contributions();
                owner.connectContributions(contributions);
                var document = contributions.tabs().open("preview.editor", "config", "config.yml", owner.id(), new javax.swing.JTextArea("key: value"));
                document.presentation("config.yml", "", dev.jasper.app.platform.AppIcons.named("FILE"), false, "config.yml", "");
                for (boolean themeDark : new boolean[]{true, false}) {
                    owner.selectVariant(themeDark);
                    String name = themeDark ? "dark" : "light";
                    owner.selectTab(owner.terminalTabs().get(0)); render(root, owner, output, name + "-first", 1040);
                    owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, name + "-middle", 1040);
                    document.select(); render(root, owner, output, name + "-document", 1040);
                    owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, name + "-overflow", 350);
                }
                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-cobalt/dark").orElseThrow());
                owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, "light-chrome-dark-workspace", 1040);
                owner.applyBackgroundOpacity(.5); render(root, owner, output, "glass", 1040);
                owner.applyBackgroundOpacity(1); owner.selectVariant(true);
                var fourth = contributions.tabs().open("preview.editor", "fourth", "README.md", owner.id(), new javax.swing.JTextArea());
                owner.selectTab(owner.terminalTabs().getFirst()); render(root, owner, output, "dark-four", 1040);
                fourth.requestClose(); document.requestClose(); owner.closeTab(owner.terminalTabs().get(1));
                render(root, owner, output, "dark-single", 1040);
            });
        } finally { closeOwners(); }
        System.out.println(output.toAbsolutePath());
    }

    private static void render(JRootPane root, WindowContent owner, Path output, String name, int width) {
        owner.update(); root.setSize(width, 480); LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root);
        var image = new BufferedImage(width * 2, 960, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.scale(2, 2); root.printAll(g); } finally { g.dispose(); }
        try {
            ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
            var strip = owner.windowTabs();
            var at = javax.swing.SwingUtilities.convertPoint(strip, 0, 0, root);
            if (strip.isVisible()) ImageIO.write(image.getSubimage(at.x * 2, at.y * 2, strip.getWidth() * 2, strip.getHeight() * 2),
                "png", output.resolve(name + "-strip.png").toFile());
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
