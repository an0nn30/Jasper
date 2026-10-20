package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.IdleTerminalSession;
import dev.jasper.terminal.session.TerminalSession;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.SwingUtilities;

/**
 * Jasper's windows for the moves of parent tabs spec 5, headless: a window manager over stand-in frames whose windows
 * share one tab registry and one terminal registry, which records what it reports. Made and used on the EDT, as the
 * windows are; {@link #launch()} runs off it.
 */
final class MovesFixture implements AutoCloseable {
    final Contributions model = new Contributions();
    final TerminalRegistry terminals = new TerminalRegistry();
    /** What the terminal registry reported, in order. */
    final List<TerminalEvent> facts = new ArrayList<>();
    final WindowManager manager;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();

    MovesFixture() {
        terminals.onEvent(facts::add);
        manager = new WindowManager(model, new WindowManager.Hooks(this::compose, MovesFixture::frame, window -> { },
            window -> { }, window -> { }, state -> { }, window -> { }, () -> { }));
    }

    private WindowContent compose(WindowManager.Opening opening) {
        var launcher = new ShellLauncher(pending::add, directory -> IdleTerminalSession.start(), "idle-test-session");
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        var content = DesktopTestSupport.track(new WindowContent(opening.role(), launcher, opening.directory(), opening.empty(),
            directory -> manager.openTerminalWindow(directory), () -> { }, opening.emptied(), ThemeTestSupport.controller(true),
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, WorkspaceChrome.STANDARD));
        content.connectContributions(model);
        content.connectTerminals(terminals, () -> { });
        return content;
    }

    /** A frame that shows nothing and opens where the platform puts it. */
    private static WindowShell frame(JasperWindow window) {
        return new WindowShell(() -> { }, () -> { }, () -> { }, () -> { }, () -> null,
            component -> SwingUtilities.isDescendingFrom(component, window.content()));
    }

    /** The main window, opened with one terminal tab when none is open. */
    MainWindow main() { return manager.ensureMain("cold", null).orElseThrow(); }

    /** Starts every shell asked for so far; off the EDT. */
    void launch() { while (!pending.isEmpty()) pending.remove().run(); }

    /** What the terminal registry reported of tabs opening, closing and moving between windows, in order. */
    List<TerminalEvent> tabFacts() {
        return facts.stream().filter(fact -> fact instanceof TerminalEvent.TabMoved || fact instanceof TerminalEvent.TabOpened
            || fact instanceof TerminalEvent.TabClosed).toList();
    }

    /** One {@code TAB_MOVED} for each of {@code tabs}, in order, from window {@code from} to window {@code to}. */
    static List<TerminalEvent> moved(List<Tab> tabs, UUID from, UUID to) {
        return tabs.stream().map(tab -> (TerminalEvent) new TerminalEvent.TabMoved(tab.id().value(), from, to)).toList();
    }

    /** A terminal tab's session. */
    static TerminalSession session(Tab tab) { return ((TerminalContent) tab.content()).pane().session(); }

    /** Closes every window, as Quit does. */
    @Override public void close() { manager.closeAll(); }
}
