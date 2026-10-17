package dev.jasper.app.windows;

import dev.jasper.app.lifecycle.Subscription;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;
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
    public record Shell(Runnable show, Runnable toFront, Runnable dispose, Consumer<String> title, Supplier<Rectangle> bounds,
                        BiFunction<String, Optional<Path>, Optional<Path>> chooseFile) { }

    /** Whether this is a top-level window or a dialog over another window. */
    public enum Kind { WINDOW, DIALOG, OVERLAY }

    private static final System.Logger LOG = System.getLogger(AuxiliarySurface.class.getName());
    private final String id;
    private final Kind kind;
    private final boolean modal;
    private final Dimension preferredSize;
    private final UUID ownerWindow;
    private final AuxiliarySurface ownerSurface;
    private final Function<AuxiliarySurface, Shell> shells;
    /** Its invalidation (any content revalidating) is how a fitted dialog notices its kit view's size changing. */
    private final JPanel holder = new JPanel(new BorderLayout()) {
        @Override public void invalidate() {
            super.invalidate();
            contentChanged();
        }
    };
    private final List<BooleanSupplier> guards = new ArrayList<>();
    private final List<BooleanSupplier> closeCommands = new ArrayList<>();
    private final List<Runnable> closedListeners = new ArrayList<>();
    private final List<Runnable> activatedListeners = new ArrayList<>();
    private final JPanel titleActions = new JPanel(new BorderLayout());
    private Runnable titleActionsChanged = () -> { };
    /** Stable title slot, always outside the body holder. */
    public JComponent titleActions() { return titleActions; }
    public void setTitleActions(JComponent content) {
        if (closed) return;
        titleActions.removeAll();
        if (closed) return;
        // Kit content in the title slot takes the shared title-strip presentation (menu indicators, control surface).
        if (content instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()) view.titleStrip(true);
        if (content != null) titleActions.add(content, BorderLayout.CENTER);
        titleActions.setVisible(content != null); titleActionsChanged();
    }
    public void titleActionsChanged() { titleActions.revalidate(); titleActions.repaint(); titleActionsChanged.run(); }
    void onTitleActionsChanged(Runnable handler) { titleActionsChanged = handler; }
    private String title;
    private Shell shell;
    private JComponent anchorOwner;
    void anchorOwner(JComponent anchor){anchorOwner=anchor;}
    Optional<JComponent> anchorOwner(){return Optional.ofNullable(anchorOwner);}
    private boolean shown;
    private boolean closed;
    private Rectangle lastBounds;
    private boolean contentSize, attachedSheet;
    public boolean contentSize() { return contentSize; }
    public void contentSize(boolean value) { contentSize = value; }
    public boolean attachedSheet() { return attachedSheet; }
    public void attachedSheet(boolean value) {
        if (value && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        attachedSheet = value;
    }
    private boolean classic, hostTitled, nativeDialog;
    /** Whether a modal overlay or attached sheet of this surface is drawn in its window as a classic dialog (spec 10.8). */
    public boolean classic() { return classic; }
    void classic(boolean value) { classic = value; }
    /**
     * Whether a title bar the system draws already shows this dialog's title, so its FULL form draws none (spec 10.8): a
     * dialog's, or a modal overlay's that opens as a native dialog; never an attached sheet's, which has no title bar.
     */
    public boolean hostTitled() { return hostTitled && !attachedSheet && (kind != Kind.OVERLAY || nativeDialog); }
    void hostTitled(boolean value) { hostTitled = value; }
    /** Whether this modal overlay opens as a native modal dialog, its owner having no showing window (spec 10.8). */
    boolean nativeDialog() { return nativeDialog; }
    void nativeDialog(boolean value) { nativeDialog = value; }
    /** The shell's host flags for a kit view set as content after the shell exists (spec 10.8); none by default. */
    private java.util.function.Consumer<dev.jasper.ui.swing.KitView> laterContent = view -> { };
    /** The shell marks content set from now on with {@code marks}, as it marked the content it found. */
    void laterContent(java.util.function.Consumer<dev.jasper.ui.swing.KitView> marks) { laterContent = Objects.requireNonNull(marks); }
    private dev.jasper.ui.model.Nodes.Hue swatch = dev.jasper.ui.model.Nodes.Hue.NONE;
    /** The hue of the square drawn before this surface's title wherever Jasper draws it; NONE draws none. Set before the first show. */
    public void swatch(dev.jasper.ui.model.Nodes.Hue value) { swatch = Objects.requireNonNull(value); }
    public dev.jasper.ui.model.Nodes.Hue swatch() { return swatch; }
    private java.util.function.IntSupplier integratedTitle;
    /**
     * The content draws the window's title row itself, {@code height} pixels tall (Settings: its sidebar and header):
     * the native shell then shows no title strip and centres the window controls on that row. Set before the first show.
     */
    public void integratedTitle(java.util.function.IntSupplier height) { integratedTitle = Objects.requireNonNull(height); }
    public Optional<java.util.function.IntSupplier> integratedTitle() { return Optional.ofNullable(integratedTitle); }
    private boolean fitContent;
    private DialogFit fit;
    private AttachedSheet attachment;
    void attachWith(AttachedSheet value) { attachment = value; if (closed) value.close(); }

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
        if (closed) return;
        holder.removeAll();
        if (!closed && content != null) {
            if (attachedSheet && content instanceof dev.jasper.ui.swing.KitView view) view.paintsSurface(false);
            holder.add(content, BorderLayout.CENTER);
            // Content replaced later keeps the shell's treatment (a classic dialog's strip and band).
            if (content instanceof dev.jasper.ui.swing.KitView kit && !kit.isClosed()) laterContent.accept(kit);
        }
        holder.revalidate(); holder.repaint();
    }

    /**
     * Whether the content is a mounted kit view, whose preferred-size changes a host-packed dialog follows ({@link
     * DialogFit}); raw Swing content keeps its own sizing.
     */
    public void fitContent(boolean value) {
        fitContent = value;
        contentChanged();
    }

    /**
     * The mounted kit view changed (its descriptor was updated, or its layout was invalidated); a fitted dialog re-packs
     * when the preferred size changed.
     */
    public void contentChanged() { if (closed) return; if (attachment != null) attachment.changed(); else if (fitContent && fit != null) fit.contentChanged(); }

    /** Whether the content is a mounted kit view (see {@link #fitContent}). */
    public boolean fitsContent() { return fitContent; }

    /** The native shell's fitting rule for a dialog it packs itself; none for remembered bounds. Closed with the surface. */
    public void fitWith(DialogFit value) {
        fit = Objects.requireNonNull(value);
        if (closed) value.close();
    }

    public void setTitle(String value) {
        title = requireTitle(value);
        if (shell != null && !closed) shell.title().accept(value);
    }

    /** Creates the native shell on first use. For a modal dialog this returns after the dialog closed. */
    public void show() {
        if (closed) return;
        try {
            if (attachedSheet && ownerSurface != null && !ownerSurface.shown())
                throw new IllegalArgumentException("Attached sheet needs a shown owner");
            if (shell == null) {
                Shell created = Objects.requireNonNull(shells.apply(this));
                if (closed) { created.dispose().run(); return; }
                shell = created;
            }
            if (ownerSurface != null && (ownerSurface.closed() || attachedSheet && !ownerSurface.shown())) { close(); return; }
            shown = true;
            shell.show().run();
        } catch (RuntimeException | Error failure) {
            shown = false;
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Opens a native existing-file picker owned by this shown surface. */
    public Optional<Path> chooseFile(String title, Optional<Path> initialPath) {
        requireTitle(title);
        Objects.requireNonNull(initialPath, "initialPath");
        if (!shown()) throw new IllegalStateException("File selection needs a shown, open window");
        Optional<Path> selected = shell.chooseFile().apply(title, initialPath);
        return closed ? Optional.empty() : selected.map(path -> path.toAbsolutePath().normalize());
    }

    public void toFront() {
        if (!closed && shell != null) shell.toFront().run();
    }

    /**
     * Asks every closing guard without closing: quit and restart use this. One that throws cannot trap the window open.
     *
     * @return whether the window may close
     */
    public boolean mayClose() {
        if (closed) return true;
        for (BooleanSupplier guard : List.copyOf(guards)) {
            try { if (!guard.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closing guard failed", failure); }
        }
        return true;
    }

    /** A user's attempt to close: every guard is consulted; one that throws cannot trap the window open. */
    public boolean requestClose() {
        if (closed) return true;
        if (kind == Kind.OVERLAY) return false;
        if (!mayClose()) return false;
        close();
        return true;
    }

    /**
     * The window's Close Window command: handlers newest first; the first that returns true has handled it and
     * the window stays open. Otherwise this is a user's close. One that throws has not handled it.
     *
     * @return true when the window closed
     */
    public boolean closeCommand() {
        if (closed) return true;
        List<BooleanSupplier> handlers = new ArrayList<>(closeCommands);
        java.util.Collections.reverse(handlers);
        for (BooleanSupplier handler : handlers) {
            try { if (handler.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window close command handler failed", failure); }
        }
        return requestClose();
    }

    public Subscription onCloseCommand(BooleanSupplier handler) {
        closeCommands.add(java.util.Objects.requireNonNull(handler));
        return new Subscription(() -> closeCommands.remove(handler));
    }

    /** Closes at once, without consulting guards. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        if (fit != null) fit.close();
        if (attachment != null) attachment.close();
        if (shell != null) {
            try { lastBounds = shell.bounds().get(); } catch (RuntimeException ignored) { lastBounds = null; }
            shell.dispose().run();
        }
        guards.clear();
        closeCommands.clear();
        for (Runnable listener : List.copyOf(closedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closed listener failed", failure); }
        }
        closedListeners.clear();
        activatedListeners.clear();
    }

    public Subscription onClosing(BooleanSupplier guard) {
        guards.add(java.util.Objects.requireNonNull(guard));
        return new Subscription(() -> guards.remove(guard));
    }

    public Subscription onClosed(Runnable listener) {
        java.util.Objects.requireNonNull(listener);
        if (closed) { listener.run(); return new Subscription(() -> { }); }
        closedListeners.add(listener);
        return new Subscription(() -> closedListeners.remove(listener));
    }

    /** The native window gained focus. Called by the shell; does nothing once closed. */
    public void notifyActivated() {
        if (closed) return;
        for (Runnable listener : List.copyOf(activatedListeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window activation listener failed", failure); }
        }
    }

    public Subscription onActivated(Runnable listener) {
        activatedListeners.add(java.util.Objects.requireNonNull(listener));
        return new Subscription(() -> activatedListeners.remove(listener));
    }
}
