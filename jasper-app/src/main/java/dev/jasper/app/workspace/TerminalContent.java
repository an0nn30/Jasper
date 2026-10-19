package dev.jasper.app.workspace;

import dev.jasper.app.contributions.TabContent;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.terminals.SessionRequest;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A terminal tab's content (workspace tabs spec 9.2): one {@link TerminalPane}, so one session, and the tab's own name.
 * Its window wires it through a {@code TabHost}; moving it to another window rewires it and never touches the session.
 * EDT only.
 */
public final class TerminalContent extends JPanel implements TabContent, AutoCloseable {
    private final TabId id;
    private final TerminalPane pane;
    private final TabState state = new TabState();
    private boolean closed;
    private Icon terminalIcon;
    /** The pane changed: its title, directory, state or size. */
    Runnable onChanged = () -> { };
    /** The pane asked to close (its shell exited under a closing policy, its provider ended, a failed launch). */
    Runnable onEmpty = () -> { };
    BiConsumer<String, Throwable> onError = (message, cause) -> { };
    /** Whether this is the only tab its window holds; a failed launch then stays to show why. */
    BooleanSupplier lastInWindow = () -> false;
    /** The pane's view is ready. */
    Consumer<TerminalPane> configure = ready -> { };

    /**
     * A tab whose shell starts in {@code directory}, where null means where a new shell starts; a request makes the pane
     * a provided session; {@code profile} names a local shell's profile, empty for the user's default; {@code program}
     * runs instead of the shell.
     */
    TerminalContent(TabId id, Path directory, ShellLauncher launcher, SessionRequest requestOrNull, Optional<String> profile,
                    Optional<List<String>> program) {
        super(new BorderLayout());
        this.id = Objects.requireNonNull(id, "id");
        pane = new TerminalPane(directory, launcher, requestOrNull, profile, program);
        pane.onChanged = () -> onChanged.run();
        pane.onClose = () -> onEmpty.run();
        pane.onFailure = (message, cause) -> {
            onError.accept(message, cause);
            // The window's only tab stays, showing why it failed: closing it would close the window before anyone read the error.
            if (!lastInWindow.getAsBoolean()) onEmpty.run();
        };
        pane.onReady = view -> configure.accept(pane);
        add(pane, BorderLayout.CENTER);
    }

    TabId id() { return id; }
    /** @return the tab's one pane */
    public TerminalPane pane() { return pane; }
    void start() { pane.start(); }
    /** The tab's title: the name the user gave it, else the shell's title, else its folder's name, else "Terminal". */
    String title() { return state.title(pane.tabTitle(), pane.directory()); }
    /** Names the tab; blank goes back to the automatic title. */
    void rename(String name) { state.rename(name); onChanged.run(); }
    /** The name the user gave the tab, kept apart from the shell's title (workspace plan 6 saves it). */
    Optional<String> renamed() { return state.renamed(); }

    /** The provided session's icon, or the host terminal icon for a local shell. */
    Icon icon() {
        Icon provided = pane.providedIcon();
        if (provided != null) return provided;
        if (terminalIcon == null) terminalIcon = AppIcons.named("TERMINAL");
        return terminalIcon;
    }

    void focusTerminal() { if (!closed) pane.focusTerminal(); }
    /** Undimmed while this is its window's active tab in an active window (workspace plan 3a Rulings 8). */
    void setActive(boolean active) { pane.setActive(active); }

    /** Below 1 the tab paints nothing behind its pane, which fills its padding at that opacity. */
    void setBackgroundOpacity(float opacity) {
        setOpaque(opacity >= 1f);
        pane.setBackgroundOpacity(opacity);
        repaint();
    }

    boolean closed() { return closed; }

    @Override public JComponent component() { return this; }
    @Override public void dispose() { close(); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        pane.close();
        removeAll();
        onChanged = () -> { }; onEmpty = () -> { }; onError = (message, cause) -> { };
        lastInWindow = () -> false; configure = ready -> { };
    }
}
