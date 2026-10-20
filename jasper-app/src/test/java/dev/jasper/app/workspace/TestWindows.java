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
import java.util.function.Function;

/**
 * A window manager over stand-in frames, as {@code WindowManagerTest}'s, for the saved workspace's tests (workspace tabs
 * spec 13): each window's content is connected to one model and one terminal registry, and shells are idle sessions
 * launched when the test runs {@link #launch()}. EDT only.
 */
final class TestWindows {
    final Queue<Runnable> pending = new ArrayDeque<>();
    /** Where each shell started, in order. */
    final Queue<Path> started = new ArrayDeque<>();
    /** What the stand-in frames did and what the application heard, in order. */
    final Queue<String> events = new ArrayDeque<>();
    final Contributions model = new Contributions();
    final TerminalRegistry terminals = new TerminalRegistry();
    final WindowManager manager;
    private final ShellLauncher launcher;

    /** Windows whose shells are idle sessions. */
    TestWindows() { this(directory -> IdleTerminalSession.start()); }

    /** Windows whose shells {@code shells} starts in the directory it is given. */
    TestWindows(Function<Path, dev.jasper.terminal.session.TerminalSession> shells) {
        launcher = new ShellLauncher(pending::add, directory -> { started.add(directory); return shells.apply(directory); }, "idle-test-session");
        manager = new WindowManager(model, new WindowManager.Hooks(this::compose, this::frame,
            window -> events.add("opened " + name(window)), window -> events.add("activated " + name(window)),
            window -> events.add("closed " + name(window)), state -> { }, window -> { }, () -> { }));
    }

    static String name(JasperWindow window) { return window.isMain() ? "main" : "child"; }

    private WindowContent compose(WindowManager.Opening opening) {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
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
