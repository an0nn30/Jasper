package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import java.awt.Component;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One of Jasper's windows (full windows spec 5.1): every window is the same kind, its {@link WindowContent} with all of
 * Jasper's chrome around its workspace, which its last tab leaves empty rather than closed. Only the {@link WindowManager}
 * makes one, in one place, which {@code verifyApplicationArchitecture} checks; it tracks and closes every one, and the
 * window's native frame drives it through {@link #events()}. EDT only.
 */
public final class JasperWindow {
    /**
     * What a window's native frame tells it.
     *
     * @param closing the title bar's close button, Alt+F4 or the Window menu's Close
     * @param activated the frame came to the front ({@code true}) or lost it
     * @param state the frame is showing, and whether it is minimized
     */
    public record Events(Runnable closing, Consumer<Boolean> activated, BiConsumer<Boolean, Boolean> state) {
        /** A frame nobody listens to, as the warm-up rehearsal's. */
        public static final Events NONE = new Events(() -> { }, active -> { }, (showing, iconified) -> { });

        /** Requires every callback. */
        public Events {
            Objects.requireNonNull(closing, "closing"); Objects.requireNonNull(activated, "activated"); Objects.requireNonNull(state, "state");
        }
    }

    /**
     * Where a window is (workspace tabs spec 13): what {@code workspace.toml} keeps, and where a restored window's frame
     * puts it.
     *
     * @param bounds its normal bounds, kept while it is maximized or minimized; empty until its frame has any
     * @param extendedState its maximized bits only ({@code Frame.MAXIMIZED_BOTH}'s 2 and 4)
     */
    public record Placement(Optional<UiState.Bounds> bounds, int extendedState) {
        /** A window not placed yet: its frame puts it where the platform puts a new window. */
        public static final Placement NONE = new Placement(Optional.empty(), 0);

        /** Requires the bounds; only the maximized bits are kept. */
        public Placement {
            Objects.requireNonNull(bounds, "bounds");
            if ((extendedState & ~6) != 0) throw new IllegalArgumentException("Only the maximized bits are kept: " + extendedState);
        }
    }

    private final WindowManager manager;
    private final WindowContent content;
    private WindowShell shell;
    private boolean closed;
    private Placement placement = Placement.NONE;

    JasperWindow(WindowManager manager, WindowContent content) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.content = Objects.requireNonNull(content, "content");
        content.onCloseWindow = this::requestClose;
    }

    /** @return the window's content: its workspace, actions and chrome */
    public WindowContent content() { return content; }

    /** @return the window's id, its content's */
    public UUID id() { return content.id(); }

    /** @return whether the window has closed */
    public boolean closed() { return closed; }

    /** @return where the window is, as its frame last said; where a restored window's frame puts it */
    public Placement placement() { return placement; }

    /**
     * Its frame says where the window is now: after it shows, moves, resizes or is maximized; a new place is saved
     * (workspace tabs spec 13). Ignored once it has closed, and when the window is there already.
     *
     * @param value the window's normal bounds and maximized bits
     */
    public void placed(Placement value) {
        WindowManager.requireEdt();
        if (closed) return;
        Objects.requireNonNull(value, "value");
        // Showing it, or a move or resize that ends where it was, asks for no save.
        if (value.equals(placement)) return;
        placement = value;
        manager.changed();
    }

    /** Brings the window to the front, restoring it when it is minimized, and focuses its active tab. */
    public void toFront() {
        if (closed || shell == null) return;
        shell.toFront().run();
        content.focusSelected();
    }

    /** @return the native window, for parenting dialogs; null before the frame exists and in a headless test */
    public java.awt.Window nativeWindow() { return shell == null ? null : shell.window().get(); }

    /**
     * Whether {@code component} is this window's frame or sits inside it.
     *
     * @param component a component, or null
     * @return true for this window's own components
     */
    public boolean owns(Component component) { return shell != null && component != null && shell.owns().test(component); }

    /**
     * Closes the window as its title bar's close button does (full windows spec 5.3): after its tabs' guards; a refusal
     * leaves the window as it is.
     */
    public void requestClose() {
        WindowManager.requireEdt();
        if (!closed) closeRequested();
    }

    /** @return what this window's native frame tells it */
    public Events events() { return new Events(this::requestClose, this::activated, this::stateChanged); }

    /** The close a request makes: see {@link #requestClose()}. */
    private void closeRequested() { if (content.mayClose()) close(); }

    /**
     * Restores the window as it was saved (workspace tabs spec 13; tabs and panes spec 12): where it was, its tabs and
     * its items. Before its frame exists, which takes the saved place from {@link #placement()}.
     *
     * @return how many items came back
     */
    int restore(WorkspaceFile.WindowRecord record) {
        placement = new Placement(record.bounds(), record.extendedState());
        return content.restore(record);
    }

    /** The frame the manager built for this window. */
    void shell(WindowShell value) { shell = Objects.requireNonNull(value, "value"); }

    WindowShell shell() { return shell; }

    private void activated(boolean active) {
        WindowManager.requireEdt();
        if (closed) return;
        content.setActive(active);
        if (active) manager.activated(this);
    }

    private void stateChanged(boolean showing, boolean iconified) {
        if (closed) return;
        content.buddyWindowState(showing, iconified);
        manager.stateChanged(this, showing, iconified);
    }

    /**
     * Closes the content, which closes the tabs it still holds, then the frame; the manager forgets the window, which
     * saves the windows left (workspace tabs spec 13). The manager's closing hook hears of it first, while its content is
     * whole. Every step runs whatever an earlier one throws; the first failure is rethrown at the end, the others
     * suppressed under it.
     */
    void close() {
        if (closed) return;
        closed = true;
        Throwable failure = step(() -> manager.closing(this), null);
        failure = step(content::close, failure);
        if (shell != null) failure = step(shell.dispose(), failure);
        failure = step(() -> manager.closed(this), failure);
        WindowManager.rethrow(failure);
    }

    /** As {@link #close()}, for a window that failed to open: neither its bounds nor its dock layout are remembered. */
    void discard() {
        if (closed) return;
        closed = true;
        content.forgetLayout();
        Throwable failure = step(content::close, null);
        if (shell != null) failure = step(shell.discard(), failure);
        failure = step(() -> manager.closed(this), failure);
        WindowManager.rethrow(failure);
    }

    /** Runs one step of a close, keeping its failure with those before it. */
    private static Throwable step(Runnable run, Throwable failure) {
        try {
            run.run();
        } catch (RuntimeException | Error thrown) {
            failure = WindowManager.keep(failure, thrown);
        }
        return failure;
    }
}
