package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.AnimationSettings;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.plugins.WorkspacePanelsFixture;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.session.TerminalSession;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;

/**
 * Actual workspace chrome at the Desktop references' logical sizes and device scale 2: the real WindowContent with its
 * macOS title-bar header, equal-width tabs, tool window stripes, dock and status bar, plus the staged Vault, Remote and
 * File Server plugins for the rails and the integrated panels. Shells are controlled /bin/sh children printing a fixture
 * prompt; no login shell, JFrame, native control or connection is used. Mock text, hosts and paths are fixture data.
 * Arguments: output directory, then an optional scenario-name filter.
 */
public final class WorkspaceSurfacePreview {
    /** Fixture-only window configuration of the reference captures (34 px title row, 28 px tabs and status). */
    private static final int TITLE_HEIGHT = 34, TAB_HEIGHT = 28, STATUS_HEIGHT = 28;
    /** Fixture-only terminal font of the reference captures: about 8.1 x 15.4 logical cells (90 x 40 and 93 x 50 grids). */
    private static final float TERMINAL_SIZE = 13.5f, TERMINAL_LINE_HEIGHT = .88f;
    private static final int SIDE_WIDTH = 260;
    private static final List<TerminalSession> SESSIONS = new ArrayList<>();

    private WorkspaceSurfacePreview() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Files.createDirectories(Path.of(args[0]).toAbsolutePath());
        String filter = args.length > 1 ? args[1] : "";
        Path home = Path.of(System.getProperty("user.home"));
        Path screenshots = Files.createDirectories(home.resolve("Desktop/Screenshots"));
        try {
            var manifest = new StringBuilder("# Workspace surface captures\nprovenance=actual WindowContent + MacTitleBar header "
                + "(no native window controls) + staged Vault/Remote/File Server; controlled /bin/sh prompt; headless\n"
                + "uiScale=" + UIScale.getUserScaleFactor() + "\n\n");
            if (wanted(filter, "tabs")) plain(output, screenshots, true, manifest);
            if (wanted(filter, "rails") || wanted(filter, "integrated")) integrated(output, home, screenshots, true, manifest, filter);
            Files.writeString(output.resolve("manifest.txt"), manifest);
        } finally {
            DesktopTestSupport.closeOwners();
            for (TerminalSession session : SESSIONS) session.exitFuture().get(5, TimeUnit.SECONDS);
        }
        System.out.println(output);
    }

    private static boolean wanted(String filter, String name) { return filter.isEmpty() || name.contains(filter) || filter.contains(name); }

    /** One actual window with its title bar installed; the caller owns closing. */
    private record Window(WindowContent owner, JRootPane root, MacTitleBar bar, ThemeController themes) { }

    private static Window window(Path screenshots, boolean dark, int prompts) throws Exception {
        Queue<Runnable> pending = new ArrayDeque<>();
        Window[] result = new Window[1];
        edt(() -> {
            var launcher = new ShellLauncher(pending::add, directory -> shell(screenshots, prompts), "zsh");
            var themes = ThemeTestSupport.controller(true);
            themes.configure(dark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            var owner = new WindowContent(launcher, screenshots, path -> { }, () -> { }, themes,
                KeyBindings.defaults(true), new CommandHistory(), true);
            owner.applyConfiguration(fixtureConfiguration(), true);
            owner.setAnimations(new AnimationSettings(false, 80, 100, 130));
            var root = new JRootPane();
            var bar = WindowContent.installTitleBar(root, owner, true, title -> { });
            owner.installRootBindings(root);
            owner.connectConfiguration(() -> { }, () -> { }, () -> { });
            result[0] = new Window(owner, root, bar, themes);
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> result[0].owner().currentPane().view() != null);
        edt(() -> result[0].owner().newTab(screenshots));
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> result[0].owner().terminalTabs().stream().allMatch(tab -> tab.pane() != null
            && tab.pane().view() != null && tab.pane().directory().equals(screenshots)));
        Thread.sleep(300);
        edt(() -> result[0].owner().update());
        return result[0];
    }

    /** Fixture-only prompt shaped like the reference's two-line prompt (with OSC 133 prompt marks, as integrated zsh emits);
     * truecolor, so any theme shows the same ink. */
    private static TerminalSession shell(Path directory, int prompts) {
        String frame = "\\033[38;2;110;117;126m", path = "\\033[38;2;45;143;191m", leaf = "\\033[1;38;2;2;175;255m";
        String first = frame + "\\342\\225\\255\\342\\224\\200 \\033[38;2;206;208;214m\\342\\227\\217 \\033[38;2;45;143;191m\\342\\226\\240 "
            + path + "~/Desktop/" + leaf + "Screenshots\\033[0m";
        String right = "\\0337\\033[999C\\033[1D" + frame + "\\342\\224\\200\\342\\225\\256\\033[0m\\0338";
        String rightEnd = "\\0337\\033[999C\\033[1D" + frame + "\\342\\224\\200\\342\\225\\257\\033[0m\\0338";
        // Redrawn on every resize, so the right frame always meets the grid's last column; scrollback is cleared too.
        var script = new StringBuilder("p() { printf '\\033[H\\033[2J\\033[3J\\033]133;A\\007");
        for (int i = 0; i < prompts; i++) script.append(first).append(i == prompts - 1 ? right : "").append("\\r\\n");
        script.append(rightEnd).append(frame).append("\\342\\225\\260\\342\\224\\200\\033[38;2;95;215;0m\\342\\235\\257\\033[0m ")
            .append("\\033]7;file://localhost").append(directory.toString().replace("'", ""))
            .append("\\007\\033]133;B\\007'; }; trap p WINCH; p; while true; do read -r answer; done");
        try {
            TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder().command(command(script.toString()))
                .environment(System.getenv()).workingDirectory(directory).grid(new GridSize(80, 24)).scrollback(100).build());
            synchronized (SESSIONS) { SESSIONS.add(session); }
            return session;
        } catch (Exception failure) { throw new CompletionException(failure); }
    }

    /** The reference user's saved values as a real snapshot; every other setting keeps its default. Fixture data only. */
    private static dev.jasper.app.config.ConfigSnapshot fixtureConfiguration() {
        var values = dev.jasper.app.settings.SettingValues.builder(dev.jasper.app.config.CoreSettings.SCHEMA)
            .put(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT, TAB_HEIGHT, List.of())
            .put(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR_HEIGHT, TITLE_HEIGHT, List.of())
            .put(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR_HEIGHT, STATUS_HEIGHT, List.of())
            .put(dev.jasper.app.config.CoreSettings.TERMINAL_FONT_SIZE, TERMINAL_SIZE, List.of())
            .put(dev.jasper.app.config.CoreSettings.TERMINAL_FONT_LINE_HEIGHT, TERMINAL_LINE_HEIGHT, List.of())
            .build();
        return dev.jasper.app.config.ConfigSnapshot.from(values, java.util.Map.of(), java.util.Map.of());
    }

    /** zsh without startup files (-f) when present, so the pane's job reads as the reference's shell; never a login shell. */
    private static List<String> command(String script) {
        return Files.isExecutable(Path.of("/bin/zsh")) ? List.of("/bin/zsh", "-f", "-c", script) : List.of("/bin/sh", "-c", script);
    }

    private static void plain(Path output, Path screenshots, boolean dark, StringBuilder manifest) throws Exception {
        var window = window(screenshots, dark, 1);
        try {
            edt(() -> window.owner().setStatusVisible(false));
            capture(window, output.resolve("tabs/dark-791x712.png"), 791, 712, 2, manifest);
            capture(window, output.resolve("tabs/dark-791x712-1x.png"), 791, 712, 1, manifest);
            capture(window, output.resolve("tabs/dark-narrow-460x320.png"), 460, 320, 2, manifest);
            // Enlarged user UI font: chrome text grows with it and must not clip in the 34 px title row or the tabs.
            edt(() -> window.themes().configure(dark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system", 20f)));
            capture(window, output.resolve("tabs/dark-font20-791x712.png"), 791, 712, 2, manifest);
            edt(() -> window.themes().configure(dark, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new dev.jasper.app.config.UiFontConfig("system", 13f)));
            for (boolean themeDark : new boolean[]{true, false}) {
                edt(() -> window.themes().configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
                capture(window, output.resolve("tabs/" + slug(themeDark) + "-791x712.png"), 791, 712, 2, manifest);
            }
            capture(window, output.resolve("tabs/light-791x712-1x.png"), 791, 712, 1, manifest);
        } finally { close(window); }
        // A long working directory and tab title clip inside the command centre and the equal-width slots.
        Path deep = Files.createDirectories(screenshots.resolve("with/a/deliberately/long/working/directory/for/clipping"));
        var longWindow = window(deep, dark, 1);
        try {
            edt(() -> { longWindow.owner().setStatusVisible(true);
                longWindow.owner().currentTerminal().rename("A deliberately long terminal tab title that must clip inside its equal-width slot"); });
            capture(longWindow, output.resolve("tabs/dark-long-titles-791x712.png"), 791, 712, 2, manifest);
            capture(longWindow, output.resolve("tabs/dark-long-titles-narrow-520x360.png"), 520, 360, 2, manifest);
        } finally { close(longWindow); }
    }

    private static void integrated(Path output, Path home, Path screenshots, boolean dark, StringBuilder manifest, String filter) throws Exception {
        var window = window(screenshots, dark, 2);
        ResolvedThemeHolder holder = new ResolvedThemeHolder();
        edt(() -> holder.value = window.themes().current());
        Path pluginHome = Files.createTempDirectory(Files.createDirectories(home.resolve("plugin-homes")), "run-");
        try (var panels = new WorkspacePanelsFixture(pluginHome, holder.value)) {
            edt(() -> {
                window.owner().connectTerminals(panels.registry(), () -> { });
                window.owner().connectContributions(panels.contributions());
                window.owner().setConfigurationState(new ConfigService.State(dev.jasper.app.config.ConfigSnapshot.defaults(), List.of(), home.resolve(".config/jasper/config.toml"), true));
                // The app's Notifications tool window registration (application.NotificationsFeature); the panel is never shown here.
                panels.contributions().addPanel("jasper.notifications", "Notifications", dev.jasper.app.platform.AppIcons.semantic("bell"),
                    Slot.RIGHT_TOP, Optional.empty(), site -> new javax.swing.JPanel());
            });
            WorkspacePanelsFixture.settle();
            arrange(window.owner(), panels, false);
            if (wanted(filter, "rails")) {
                capture(window, output.resolve("rails/dark-791x712.png"), 791, 712, 2, manifest);
                capture(window, output.resolve("rails/dark-791x712-1x.png"), 791, 712, 1, manifest);
                edt(() -> window.owner().setRailVisible(false));
                capture(window, output.resolve("rails/dark-hidden-rails-791x712.png"), 791, 712, 2, manifest);
                edt(() -> window.owner().setRailVisible(true));
            }
            if (wanted(filter, "integrated")) {
                panels.populate();
                arrange(window.owner(), panels, true);
                until(() -> panels.select(window.owner().id(), WorkspacePanelsFixture.SELECTED));
                WorkspacePanelsFixture.settle();
                capture(window, output.resolve("integrated/dark-1342x858.png"), 1342, 858, 2, manifest);
                capture(window, output.resolve("integrated/dark-1342x858-1x.png"), 1342, 858, 1, manifest);
                edt(() -> window.owner().setRailVisible(false));
                capture(window, output.resolve("integrated/dark-hidden-rails-1342x858.png"), 1342, 858, 2, manifest);
                edt(() -> window.owner().setRailVisible(true));
                edt(() -> {
                    window.owner().dock().hide(WorkspacePanelsFixture.SFTP);
                    window.owner().dock().hide(WorkspacePanelsFixture.FILE_SERVERS);
                    window.owner().dock().hide(WorkspacePanelsFixture.TUNNELS);
                    window.owner().area().sync();
                });
                capture(window, output.resolve("integrated/dark-collapsed-1342x858.png"), 1342, 858, 2, manifest);
                arrange(window.owner(), panels, true);
                edt(window.owner()::toggleZenMode);
                capture(window, output.resolve("integrated/dark-zen-1342x858.png"), 1342, 858, 2, manifest);
                edt(window.owner()::toggleZenMode);
                for (boolean themeDark : new boolean[]{true, false}) {
                    edt(() -> window.themes().configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
                    edt(() -> holder.value = window.themes().current());
                    panels.theme(holder.value);
                    WorkspacePanelsFixture.settle();
                    capture(window, output.resolve("integrated/" + slug(themeDark) + "-1342x858.png"), 1342, 858, 2, manifest);
                }
            }
        } finally {
            close(window);
            try (var paths = Files.walk(pluginHome)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }

    private static final class ResolvedThemeHolder { dev.jasper.app.appearance.ResolvedTheme value; }

    /** Docks the reference arrangement: Sessions over SFTP on the left, File Servers over Tunnels on the right. */
    private static void arrange(WindowContent owner, WorkspacePanelsFixture panels, boolean show) throws Exception {
        String[][] slots = {{WorkspacePanelsFixture.SESSIONS, "LEFT_TOP"}, {WorkspacePanelsFixture.SFTP, "LEFT_BOTTOM"},
            {"jasper.notifications", "RIGHT_TOP"}, {WorkspacePanelsFixture.FILE_SERVERS, "RIGHT_TOP"},
            {WorkspacePanelsFixture.TUNNELS, "RIGHT_BOTTOM"}};
        edt(() -> {
            var dock = owner.dock();
            for (String[] entry : slots) {
                Slot slot = Slot.valueOf(entry[1]);
                if (dock.slot(entry[0]).isEmpty()) continue;
                dock.move(entry[0], slot, Integer.MAX_VALUE);
            }
            for (String[] entry : slots) {
                if (dock.slot(entry[0]).isEmpty()) continue;
                if (show && !entry[0].equals("jasper.notifications")) dock.show(entry[0]); else dock.hide(entry[0]);
            }
            dock.resize(Side.LEFT, UIScale.scale(SIDE_WIDTH));
            dock.resize(Side.RIGHT, UIScale.scale(SIDE_WIDTH));
            dock.setRatio(Side.LEFT, .5);
            dock.setRatio(Side.RIGHT, .5);
            owner.area().sync();
        });
        WorkspacePanelsFixture.settle();
    }

    private static String slug(boolean dark) { return dark ? "dark" : "light"; }

    private static void close(Window window) throws Exception {
        edt(() -> { if (window.bar() != null) window.bar().close(); window.owner().close(); window.themes().close(); });
    }

    /** Lays the real root out at a logical size, then prints it at {@code scale} with its measured geometry beside it. */
    private static void capture(Window window, Path file, int width, int height, int scale, StringBuilder manifest) throws Exception {
        Files.createDirectories(file.getParent());
        for (int pass = 0; pass < 2; pass++) edt(() -> {
            window.root().setSize(width, height);
            for (int i = 0; i < 4; i++) LayoutTestSupport.layoutTree(window.root());
            window.owner().update();
        });
        // Headless views are never displayable, so they never publish their grid; ask each to fit its laid-out size,
        // as a shown view does after a resize, then let the fixture prompt redraw for the new width.
        edt(() -> {
            for (TerminalContent tab : window.owner().terminalTabs()) for (TerminalPane pane : java.util.List.of(tab.pane())) {
                if (pane.view() == null || pane.view().getWidth() <= 0) continue;
                try {
                    var fit = pane.view().getClass().getDeclaredMethod("resizeSessionToFit");
                    fit.setAccessible(true);
                    fit.invoke(pane.view());
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }
        });
        Thread.sleep(500);
        edt(() -> { window.owner().update(); for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(window.root()); });
        edt(() -> {
            for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(window.root());
            var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try { graphics.scale(scale, scale); window.root().printAll(graphics); }
            finally { graphics.dispose(); }
            var details = new StringBuilder();
            describe(window.root(), window.root(), details);
            try {
                ImageIO.write(image, "png", file.toFile());
                Files.writeString(file.resolveSibling(file.getFileName() + ".txt"), details);
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
            manifest.append(file.getFileName()).append(' ').append(width).append('x').append(height).append(" @").append(scale).append("x\n");
            System.out.println(file);
        });
    }

    private static void describe(Component component, JComponent root, StringBuilder text) {
        if (!component.isVisible() || component.getWidth() <= 0) return;
        String name = component.getName();
        String label = component instanceof AbstractButton button ? button.getAccessibleContext().getAccessibleName()
            : component instanceof JLabel l ? l.getText() : null;
        if (name != null || label != null && !label.isBlank()) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
            text.append(name).append(" | ").append(label).append(" | ").append(component.getClass().getSimpleName()).append(' ')
                .append(bounds.x).append(',').append(bounds.y).append(' ').append(bounds.width).append('x').append(bounds.height).append('\n');
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) describe(child, root, text);
    }
}
