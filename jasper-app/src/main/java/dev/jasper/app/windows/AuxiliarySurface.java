package dev.jasper.app.windows;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Everything about an auxiliary window that does not need a native frame: its content holder, title,
 * closing guards and lifetime. The native shell is created on first show. EDT only.
 */
public final class AuxiliarySurface {
    /** The native side, as plain functions so tests can stand in for a frame. */
    public record Shell(Runnable show, Runnable toFront, Runnable dispose, Consumer<String> title, Supplier<Rectangle> bounds) { }

    /** Whether this is a top-level window or a dialog over another window. */
    public enum Kind { WINDOW, DIALOG }

    private static final System.Logger LOG = System.getLogger(AuxiliarySurface.class.getName());
    private final String id;
    private final Kind kind;
    private final boolean modal;
    private final Dimension preferredSize;
    private final UUID ownerWindow;
    private final AuxiliarySurface ownerSurface;
    private final Function<AuxiliarySurface, Shell> shells;
    private final JPanel holder = new JPanel(new BorderLayout());
    private final List<BooleanSupplier> guards = new ArrayList<>();
    private final List<Runnable> closedListeners = new ArrayList<>();
    private String title;
    private Shell shell;
    private boolean shown;
    private boolean closed;
    private Rectangle lastBounds;

    AuxiliarySurface(String id, String title, Kind kind, boolean modal, Dimension preferredSize, UUID ownerWindow,
                     AuxiliarySurface ownerSurface, Function<AuxiliarySurface, Shell> shells) {
        this.id = id; this.kind = kind; this.modal = modal; this.preferredSize = new Dimension(preferredSize);
        this.ownerWindow = ownerWindow; this.ownerSurface = ownerSurface; this.shells = shells;
        this.title = requireTitle(title);
    }

    private static String requireTitle(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("A window needs a title");
        return value;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public boolean modal() { return modal; }
    public Dimension preferredSize() { return new Dimension(preferredSize); }
    public Optional<UUID> ownerWindow() { return Optional.ofNullable(ownerWindow); }
    public Optional<AuxiliarySurface> ownerSurface() { return Optional.ofNullable(ownerSurface); }
    /** The stable content pane the native shell adopts; {@link #setContent} swaps its child. */
    public JComponent holder() { return holder; }
    public String title() { return title; }
    public boolean shown() { return shown && !closed; }
    public boolean closed() { return closed; }
    Optional<Rectangle> lastBounds() { return Optional.ofNullable(lastBounds); }

    public void setContent(JComponent content) {
        holder.removeAll();
        if (content != null) holder.add(content, BorderLayout.CENTER);
        holder.revalidate(); holder.repaint();
    }

    public void setTitle(String value) {
        title = requireTitle(value);
        if (shell != null && !closed) shell.title().accept(value);
    }

    /** Creates the native shell on first use. For a modal dialog this returns after the dialog closed. */
    public void show() {
        if (closed) return;
        if (shell == null) shell = shells.apply(this);
        shown = true;
        shell.show().run();
    }

    public void toFront() {
        if (!closed && shell != null) shell.toFront().run();
    }

    /** A user's attempt to close: every guard is consulted; one that throws cannot trap the window open. */
    public boolean requestClose() {
        if (closed) return true;
        for (BooleanSupplier guard : List.copyOf(guards)) {
            try { if (!guard.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closing guard failed", failure); }
        }
        close();
        return true;
    }

    /** Closes at once, without consulting guards. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        if (shell != null) {
            try { lastBounds = shell.bounds().get(); } catch (RuntimeException ignored) { lastBounds = null; }
            shell.dispose().run();
        }
        guards.clear();
        for (Runnable listener : List.copyOf(closedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closed listener failed", failure); }
        }
        closedListeners.clear();
    }

    public Subscription onClosing(BooleanSupplier guard) {
        guards.add(java.util.Objects.requireNonNull(guard));
        return new Subscription(() -> guards.remove(guard));
    }

    public Subscription onClosed(Runnable listener) {
        closedListeners.add(java.util.Objects.requireNonNull(listener));
        return new Subscription(() -> closedListeners.remove(listener));
    }
}
