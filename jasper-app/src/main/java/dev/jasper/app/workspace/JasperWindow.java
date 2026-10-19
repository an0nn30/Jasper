package dev.jasper.app.workspace;

import java.awt.Component;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One of Jasper's windows (workspace tabs spec 6): the {@link MainWindow} or a {@link ChildWindow}, around its
 * {@link WindowContent}. The {@link WindowManager} makes, tracks and closes every one; the window's native frame drives it
 * through {@link #events()}. EDT only.
 */
public abstract sealed class JasperWindow permits MainWindow, ChildWindow {
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

    private final WindowManager manager;
    private final WindowContent content;
    private WindowShell shell;
    private boolean closed;

    JasperWindow(WindowManager manager, WindowContent content) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.content = Objects.requireNonNull(content, "content");
    }

    /** @return the window's content: its workspace and actions, and the main window's chrome */
    public final WindowContent content() { return content; }

    /** @return the window's id, its content's */
    public final UUID id() { return content.id(); }

    /** @return whether this is the main window or a child window */
    public abstract WindowRole role();

    /** @return whether this is the main window */
    public final boolean isMain() { return role() == WindowRole.MAIN; }

    /** @return whether the window has closed */
    public final boolean closed() { return closed; }

    /** Brings the window to the front, restoring it when it is minimized, and focuses its active tab. */
    public final void toFront() {
        if (closed || shell == null) return;
        shell.toFront().run();
        content.focusSelected();
    }

    /** @return the native window, for parenting dialogs; null before the frame exists and in a headless test */
    public final java.awt.Window nativeWindow() { return shell == null ? null : shell.window().get(); }

    /**
     * Whether {@code component} is this window's frame or sits inside it.
     *
     * @param component a component, or null
     * @return true for this window's own components
     */
    public final boolean owns(Component component) { return shell != null && component != null && shell.owns().test(component); }

    /**
     * Closes the window as its title bar's close button does (workspace tabs spec 6.4): a child window after its tabs'
     * guards; the main window after every window's tabs' guards, with every child window.
     */
    public final void requestClose() { if (!closed) closeRequested(); }

    /** @return what this window's native frame tells it */
    public final Events events() { return new Events(this::requestClose, this::activated, this::stateChanged); }

    /** The close this window's kind makes on a request: see {@link #requestClose()}. */
    abstract void closeRequested();

    final WindowManager manager() { return manager; }

    /** The frame the manager built for this window. */
    final void shell(WindowShell value) { shell = Objects.requireNonNull(value, "value"); }

    final WindowShell shell() { return shell; }

    private void activated(boolean active) {
        if (closed) return;
        content.setActive(active);
        if (active) manager.activated(this);
    }

    private void stateChanged(boolean showing, boolean iconified) {
        if (!closed) manager.stateChanged(this, showing, iconified);
    }

    /**
     * Closes the content, which closes the tabs it still holds, then the frame; the manager forgets the window. The
     * manager's closing hook hears of it first, while its content is whole (plan 6 saves the layout there).
     */
    final void close() {
        if (closed) return;
        closed = true;
        try {
            try {
                manager.closing(this);
            } finally {
                content.close();
            }
        } finally {
            try {
                if (shell != null) shell.dispose().run();
            } finally {
                manager.closed(this);
            }
        }
    }

    /** As {@link #close()}, for a window that failed to open: neither its bounds nor its dock layout are remembered. */
    final void discard() {
        if (closed) return;
        closed = true;
        try {
            content.forgetLayout();
            content.close();
        } finally {
            try {
                if (shell != null) shell.discard().run();
            } finally {
                manager.closed(this);
            }
        }
    }
}
