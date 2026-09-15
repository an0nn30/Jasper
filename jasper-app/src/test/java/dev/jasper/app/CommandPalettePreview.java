package dev.jasper.app;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.terminal.TerminalSession;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Opt-in actual-component command-palette renders; creates no native window. */
public final class CommandPalettePreview {
    private static final int LARGE_WIDTH = 900;
    private static final int LARGE_HEIGHT = 600;
    private static final int NARROW_WIDTH = 360;
    private static final int NARROW_HEIGHT = 500;

    private CommandPalettePreview() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        Integer expectedScale = expectedUiScale(args);
        try (var fixture = Fixture.create()) {
            if (expectedScale == null) {
                fixture.renderMatrix(output);
            } else {
                fixture.verifyUiScale(output, expectedScale);
            }
        }
    }

    private static Integer expectedUiScale(String[] args) {
        for (int i = 1; i < args.length; i++) {
            String prefix = "--expect-ui-scale=";
            if (args[i].startsWith(prefix)) return Integer.parseInt(args[i].substring(prefix.length()));
        }
        return null;
    }

    static void capture(JComponent root, Path file, int width, int height, int scale) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            root.setSize(width, height);
            MockUiTest.layoutTree(root);
            root.dispatchEvent(new ComponentEvent(root, ComponentEvent.COMPONENT_RESIZED));
            root.getRootPane().getLayeredPane().dispatchEvent(
                new ComponentEvent(root.getRootPane().getLayeredPane(), ComponentEvent.COMPONENT_RESIZED));
            MockUiTest.layoutTree(root);
            var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                graphics.scale(scale, scale);
                root.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            try {
                ImageIO.write(image, "png", file.toFile());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    private enum Scenario {
        RECENTS("recents", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        PANE_QUERY("pane-query", "pane", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        NO_MATCH("no-match", "quasar never", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID),
        LONG_LABELS("long-labels-narrow", "preview fixture", NARROW_WIDTH, NARROW_HEIGHT, PaletteScope.COMMANDS_ID),
        HISTORY_RECENT("history-recent", "", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.HISTORY_ID),
        HISTORY_QUERY("history-query", "git", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.HISTORY_ID),
        SCOPE_PICKER("scope-picker", ">", LARGE_WIDTH, LARGE_HEIGHT, PaletteScope.COMMANDS_ID);

        final String slug;
        final String query;
        final int width;
        final int height;
        final String scope;

        Scenario(String slug, String query, int width, int height, String scope) {
            this.slug = slug;
            this.query = query;
            this.width = width;
            this.height = height;
            this.scope = scope;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Queue<Runnable> pending;
        private final List<TerminalSession> sessions;
        private final ThemeController themes;
        private final CommandHistory history;
        private final ShellHistoryIndex shellHistory;
        private final WindowContent owner;
        private final JRootPane root;
        private final MacTitleBar titleBar;

        private Fixture(Queue<Runnable> pending, List<TerminalSession> sessions,
                        ThemeController themes, CommandHistory history, ShellHistoryIndex shellHistory,
                        WindowContent owner, JRootPane root, MacTitleBar titleBar) {
            this.pending = pending;
            this.sessions = sessions;
            this.themes = themes;
            this.history = history;
            this.shellHistory = shellHistory;
            this.owner = owner;
            this.root = root;
            this.titleBar = titleBar;
        }

        /** Worker and delivery run inline so the fixture's synthetic entries are recorded before the palette opens. */
        private static java.util.concurrent.ExecutorService inlineWorker() {
            return new java.util.concurrent.AbstractExecutorService() {
                @Override public void execute(Runnable task) { task.run(); }
                @Override public void shutdown() {}
                @Override public List<Runnable> shutdownNow() { return List.of(); }
                @Override public boolean isShutdown() { return false; }
                @Override public boolean isTerminated() { return false; }
                @Override public boolean awaitTermination(long t, TimeUnit u) { return true; }
            };
        }

        static Fixture create() throws Exception {
            Fixture[] result = new Fixture[1];
            SwingUtilities.invokeAndWait(() -> {
                Queue<Runnable> pending = new ArrayDeque<>();
                List<TerminalSession> sessions = new ArrayList<>();
                var launcher = new ShellLauncher(pending::add, directory -> {
                    TerminalSession session = DesktopTestSupport.shell(directory);
                    sessions.add(session);
                    return session;
                }, "sh");
                var themes = new ThemeController();
                var history = new CommandHistory();
                var index = new ShellHistoryIndex(List.of(), inlineWorker(), Runnable::run);
                String[] commands = {"git status", "git commit -m \"Tidy palette scopes\"", "./gradlew check",
                    "ls -la", "cd ~/projects/moray", "rg TODO jasper-app/src", "cargo build --release",
                    "docker compose up -d", "kubectl get pods -n jasper", "make test", "python3 -m http.server 8000",
                    "tail -f /var/log/system.log", "brew upgrade", "ssh build@ci.example.com", "npm run dev"};
                for (int i = 0; i < commands.length; i++)
                    index.record(new ShellHistoryEntry(commands[i], 1_700_000_000L + i, java.util.Set.of(i % 3 == 0 ? "bash" : "zsh"),
                        i == 12 ? java.nio.file.Path.of("/Users/preview/projects/moray") : null, null));
                var owner = new WindowContent(launcher, DesktopTestSupport.HOME, path -> {}, () -> {}, () -> {},
                    themes, KeyBindings.defaults(true), System::nanoTime, history, true, index);
                var root = new JRootPane();
                var titleBar = MacTitleBar.install(root, owner, true, title -> {});
                owner.installRootBindings(root);
                var fixture = new Fixture(pending, sessions, themes, history, index, owner, root, titleBar);
                fixture.addLongLabelCommands();
                history.record("find");
                history.record("split_right");
                history.record("new_tab");
                result[0] = fixture;
            });

            Fixture fixture = result[0];
            while (!fixture.pending.isEmpty()) fixture.pending.remove().run();
            DesktopTestSupport.until(() -> fixture.owner.currentPane().view() != null);
            SwingUtilities.invokeAndWait(() -> {
                fixture.owner.update();
                fixture.owner.commandPalette().toggle();
            });
            return fixture;
        }

        private void addLongLabelCommands() {
            List<String> labels = List.of(
                "Preview Fixture: Connect to the production session chooser with an unusually long destination label",
                "Preview Fixture: Open a deeply nested workspace configuration directory",
                "Preview Fixture: Restore the selected pane after a delayed launch",
                "Preview Fixture: Compare keyboard shortcuts across platforms",
                "Preview Fixture: Reopen the most recently closed development tab");
            for (int i = 0; i < labels.size(); i++) {
                var action = new AbstractAction(labels.get(i)) {
                    @Override public void actionPerformed(ActionEvent event) {}
                };
                action.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("meta shift " + (i + 1)));
                owner.commands().register(new Command("preview.fixture." + (i + 1), action,
                    List.of("preview", "fixture", "long label")));
            }
        }

        void renderMatrix(Path output) throws Exception {
            var manifest = new StringBuilder("# Command palette render manifest\n\n")
                .append("Swing UI scale: ").append(UIScale.getUserScaleFactor()).append("\n")
                .append("Pixel output scales: 1x, 2x\n\n");
            for (Scenario scenario : Scenario.values()) {
                for (BuiltinTheme theme : BuiltinTheme.values()) {
                    configure(scenario, theme);
                    assertScenario(scenario);
                    for (int pixelScale : List.of(1, 2)) {
                        String name = scenario.slug + "-" + themeSlug(theme) + "-"
                            + scenario.width + "x" + scenario.height + "-" + pixelScale + "x.png";
                        Path file = output.resolve(name);
                        capture(root, file, scenario.width, scenario.height, pixelScale);
                        manifest.append(name).append('\n');
                        System.out.println(file);
                    }
                }
            }
            Files.writeString(output.resolve("render-manifest.txt"), manifest.toString());
        }

        private void configure(Scenario scenario, BuiltinTheme theme) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                themes.configure(theme.appearance());
                if (!scenario.scope.equals(owner.commandPalette().activeScopeId())) owner.commandPalette().open(scenario.scope);
                owner.commandPalette().component().queryField().setText(scenario.query);
            });
        }

        private void assertScenario(Scenario scenario) throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                var palette = owner.commandPalette().component();
                int count = palette.resultList().getModel().getSize();
                int expected = switch (scenario) {
                    case RECENTS -> 3;
                    case PANE_QUERY, LONG_LABELS -> 5;
                    case NO_MATCH -> 0;
                    case HISTORY_RECENT -> 5;
                    case HISTORY_QUERY, SCOPE_PICKER -> 2;
                };
                if (count != expected) {
                    throw new AssertionError(scenario.slug + " expected " + expected + " rows, got " + count);
                }
                if (scenario == Scenario.PANE_QUERY) {
                    for (int i = 0; i < count; i++) {
                        String id = palette.resultList().getModel().getElementAt(i).id();
                        if (id.startsWith("preview.")) throw new AssertionError("Pane query used synthetic command " + id);
                    }
                }
                if (scenario == Scenario.HISTORY_RECENT) {
                    // Fifteen entries are recorded; the hard cap keeps only the newest five.
                    for (int i = 0; i < count; i++) {
                        PaletteRow row = palette.resultList().getModel().getElementAt(i);
                        if (row.tag() == null) throw new AssertionError("history-recent row missing tag: " + row.id());
                    }
                }
                if (scenario == Scenario.HISTORY_QUERY) {
                    String title = palette.resultList().getModel().getElementAt(0).title();
                    if (!title.startsWith("git")) throw new AssertionError("history-query first title does not start with git: " + title);
                }
            });
        }

        void verifyUiScale(Path output, int expectedScale) throws Exception {
            configure(Scenario.LONG_LABELS, BuiltinTheme.DARK);
            SwingUtilities.invokeAndWait(() -> {
                int actualUnit = UIScale.scale(1);
                var palette = owner.commandPalette().component();
                Dimension preferred = palette.getPreferredSize();
                int inputHeight = palette.queryField().getParent().getPreferredSize().height;
                int rowHeight = palette.resultList().getFixedCellHeight();
                if (actualUnit != expectedScale) {
                    throw new AssertionError("Expected UIScale " + expectedScale + "x, got " + actualUnit + "x");
                }
                if (preferred.width != 560 * expectedScale) {
                    throw new AssertionError("Palette width scaled more or less than once: " + preferred.width);
                }
                if (inputHeight != 56 * expectedScale) {
                    throw new AssertionError("Input height scaled more or less than once: " + inputHeight);
                }
                if (rowHeight != 40 * expectedScale) {
                    throw new AssertionError("Row height scaled more or less than once: " + rowHeight);
                }
            });
            int width = LARGE_WIDTH * expectedScale;
            int height = LARGE_HEIGHT * expectedScale;
            Path image = output.resolve("actual-ui-scale-" + expectedScale + "x.png");
            capture(root, image, width, height, 1);
            int[] bounds = new int[4];
            SwingUtilities.invokeAndWait(() -> {
                var palette = owner.commandPalette().component();
                bounds[0] = palette.getX();
                bounds[1] = palette.getY();
                bounds[2] = palette.getWidth();
                bounds[3] = palette.getHeight();
                if (palette.getWidth() != 560 * expectedScale) {
                    throw new AssertionError("Laid-out palette width scaled more or less than once: " + palette.getWidth());
                }
            });
            String report = "actual UIScale=" + expectedScale + "x\n"
                + "root=" + width + "x" + height + "\n"
                + "palette=" + bounds[0] + "," + bounds[1] + " " + bounds[2] + "x" + bounds[3] + "\n"
                + "preferredWidth=" + (560 * expectedScale) + "\n"
                + "inputHeight=" + (56 * expectedScale) + "\n"
                + "rowHeight=" + (40 * expectedScale) + "\n";
            Files.writeString(output.resolve("actual-ui-scale-" + expectedScale + "x.txt"), report);
            System.out.println(image);
            System.out.print(report);
        }

        private static String themeSlug(BuiltinTheme theme) {
            return switch (theme) {
                case DARK -> "dark";
                case LIGHT -> "light";
            };
        }

        @Override public void close() {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    if (titleBar != null) titleBar.close();
                    owner.close();
                    history.close();
                    shellHistory.close();
                });
                for (TerminalSession session : sessions) session.exitFuture().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while closing preview fixture", interrupted);
            } catch (Exception failure) {
                throw new IllegalStateException("Could not close preview fixture", failure);
            }
        }
    }
}
