package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A window manager over stand-in frames, as {@code WindowManagerTest}'s, for the saved workspace's tests (workspace tabs
 * spec 13): each window's content is connected to one model and one terminal registry, and shells are idle sessions
 * launched when the test runs {@link #launch()}. EDT only, but {@link #launch()}, which runs off it.
 */
final class TestWindows {
    /** The shells' launches the windows asked for: the EDT adds them, {@link #launch()} runs them off it. */
    final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    /** Where each shell started, in order. */
    final Queue<Path> started = new ArrayDeque<>();
    /** What the stand-in frames did and what the application heard, in order. */
    final Queue<String> events = new ArrayDeque<>();
    final Contributions model = new Contributions();
    final TerminalRegistry terminals = new TerminalRegistry();
    final WindowManager manager;
    /** Runs on the EDT as each shell's launch is asked for, inside its pane's start: a test makes a start throw with it. */
    Runnable launching = () -> { };
    /** Hears each window's content once it is composed, before its window restores or shows: a test arms a failure. */
    Consumer<WindowContent> composed = content -> { };
    private final ShellLauncher launcher;

    /** Windows whose shells are idle sessions. */
    TestWindows() { this(directory -> IdleTerminalSession.start()); }

    /** Windows whose shells {@code shells} starts in the directory it is given. */
    TestWindows(Function<Path, dev.jasper.terminal.session.TerminalSession> shells) {
        launcher = new ShellLauncher(task -> { launching.run(); pending.add(task); },
            directory -> { started.add(directory); return shells.apply(directory); }, "idle-test-session");
        manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> events.add("closed " + name(window)), state -> { }, window -> events.add("closing " + name(window)), () -> { }));
    }

    /** Each window's name, {@code "w0"}, {@code "w1"}, …: its index in {@code windows()} when this first named it. */
    private final java.util.Map<JasperWindow, String> names = new java.util.IdentityHashMap<>();

    String name(JasperWindow window) {
        return names.computeIfAbsent(window, named -> {
            int index = manager.windows().indexOf(named);
            return "w" + (index < 0 ? names.size() : index);
        });
    }

    /** @return every window after the first, in opening order */
    java.util.List<JasperWindow> others() { var all = manager.windows(); return all.isEmpty() ? java.util.List.of() : all.subList(1, all.size()); }

    /** @return the first window in opening order */
    JasperWindow first() { return manager.windows().getFirst(); }

    private WindowContent compose(WindowManager.Opening opening) {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(launcher, opening.directory(), opening.empty(),
            directory -> manager.openWindow(directory), () -> { }, ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        // Its own tool windows, as the opening says (full windows spec 6.1).
        content.connectContributions(model, new dev.jasper.app.persistence.ToolWindowState(opening.tools(), ""));
        content.connectTerminals(terminals, () -> { });
        composed.accept(content);
        return content;
    }

    private WindowShell frame(JasperWindow window) {
        String name = name(window);
        return new WindowShell(() -> events.add("show " + name), () -> events.add("front " + name), () -> events.add("dispose " + name),
            () -> events.add("discard " + name), () -> null, component -> false);
    }

    /**
     * Runs the shells' launches the windows asked for so far, once the EDT has run what it was given before: a restored
     * tab starts at the end of its EDT turn (workspace plan 6a Rulings 31). Off the EDT.
     */
    void launch() throws Exception {
        DesktopTestSupport.edt(() -> { });
        while (!pending.isEmpty()) pending.remove().run();
    }
}
