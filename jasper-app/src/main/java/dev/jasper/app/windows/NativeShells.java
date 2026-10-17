package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.files.ChooserModel;
import dev.jasper.app.files.ChooserPrefs;
import dev.jasper.app.files.ChooserRequest;
import dev.jasper.app.files.ChooserSource;
import dev.jasper.app.files.FileChooserPanel;
import dev.jasper.app.files.LocalBrowser;
import dev.jasper.app.files.LocalSource;
import dev.jasper.app.files.Location;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.MacTitleBar;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The native boundary for auxiliary windows: frame or dialog, application icon, the unified macOS title
 * bar, a menu bar so the screen menu does not vanish, theme tracking and remembered bounds. A modal dialog floats above
 * Jasper's windows while Jasper is active ({@link FloatWhileActive}). It also shows Jasper's file
 * chooser in place of the platform's file dialogs. Everything
 * testable lives in {@link AuxiliarySurface}. EDT only; never constructed in headless tests.
 */
public final class NativeShells {
    private static final String CHOOSER_BOUNDS = "file-chooser";
    private final ThemeController themes;
    private final UiState state;
    private final Function<UUID, Window> terminalWindows;
    private final Runnable newWindow;
    private final Runnable quit;
    private final Map<AuxiliarySurface, Window> natives = new HashMap<>();
    /** Classic dialogs drawn inside their owner's window (spec 10.8): they have no native window of their own. */
    private final java.util.Set<AuxiliarySurface> drawnInWindow = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final ShellChrome shell;

    /**
     * Binds the shells to the application.
     *
     * @param themes the application's look
     * @param state remembered window bounds
     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit) {
        this(themes, state, terminalWindows, newWindow, quit, ShellChrome.standard());
    }

    /**
     * As above, under the look's shells (Enterprise presentation spec 10.1).
     *
     * @param themes the application's look
     * @param state remembered window bounds
     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     * @param shell the look's title bar, menu row and appearance
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit, ShellChrome shell) {
        this.shell = Objects.requireNonNull(shell);
        this.themes = Objects.requireNonNull(themes); this.state = Objects.requireNonNull(state);
        this.terminalWindows = Objects.requireNonNull(terminalWindows);
        this.newWindow = Objects.requireNonNull(newWindow); this.quit = Objects.requireNonNull(quit);
    }

    /**
     * The look's shells these windows are made under.
     *
     * @return the shells' chrome
     */
    public ShellChrome chrome() { return shell; }

    /**
     * Builds the native window for a surface.
     *
     * @param surface the headless core
     * @return the functions the core drives
     */
    public AuxiliarySurface.Shell create(AuxiliarySurface surface) {
        if (surface.classic()) {
            Window owner = surface.kind() == AuxiliarySurface.Kind.WINDOW ? null : owner(surface);
            boolean showing = owner instanceof javax.swing.RootPaneContainer && onScreen(owner);
            switch (classicRoute(surface, showing)) {
                case IN_WINDOW -> { return inWindow(surface, classicPlacement(surface, showing).orElseThrow()); }
                // A classic dialog whose owner has no showing window opens natively (spec 10.8): an overlay as a modal
                // dialog, an attached sheet as a centred one, since it has no showing window to hang from.
                case MODAL_DIALOG -> { nativeFallback(surface); return dialog(surface); }
                case AS_REQUESTED -> nativeFallback(surface);
            }
        }
        return switch (surface.kind()) {
            case WINDOW -> frame(surface);
            case DIALOG -> dialog(surface);
            case OVERLAY -> overlay(surface);
        };
    }

    /** A classic dialog's host flags (spec 10.8): the strip titles it and its footer is the band. */
    static final java.util.function.Consumer<dev.jasper.ui.swing.KitView> CLASSIC_FLAGS = view -> { view.hostTitled(true); view.dialogBand(true); };

    /**
     * A classic dialog with no showing window opens natively (spec 10.8). A modal overlay becomes a native modal dialog:
     * its system title bar titles it and it holds what it owns. An attached sheet, with no showing window to hang from,
     * becomes a centred native dialog: its attachment is cleared, which {@code dialog} would otherwise refuse, and its kit
     * views paint their surface again.
     */
    static void nativeFallback(AuxiliarySurface surface) {
        if (surface.kind() == AuxiliarySurface.Kind.OVERLAY && surface.modal()) surface.nativeDialog(true);
        if (surface.kind() != AuxiliarySurface.Kind.DIALOG || !surface.attachedSheet()) return;
        surface.attachedSheet(false);
        for (var child : surface.holder().getComponents())
            if (child instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()) view.paintsSurface(true);
    }

    /** Where {@link #create} puts a surface the look marks classic (spec 10.8). */
    enum ClassicRoute {
        /** In its owner's showing window, as a classic dialog. */
        IN_WINDOW,
        /** A modal overlay whose owner has no showing window: a native modal dialog ({@link #nativeFallback}). */
        MODAL_DIALOG,
        /** Its own presentation, an attached sheet first losing its attachment ({@link #nativeFallback}). */
        AS_REQUESTED
    }

    /** The route of a surface whose owner window is showing or not; a surface the look does not mark is as requested. */
    static ClassicRoute classicRoute(AuxiliarySurface surface, boolean ownerShowing) {
        if (classicPlacement(surface, ownerShowing).isPresent()) return ClassicRoute.IN_WINDOW;
        if (surface.classic() && surface.kind() == AuxiliarySurface.Kind.OVERLAY && surface.modal()) return ClassicRoute.MODAL_DIALOG;
        return ClassicRoute.AS_REQUESTED;
    }

    /** Where a marked modal overlay or attached sheet sits in its owner's showing window, or empty when it is not in one. */
    static Optional<dev.jasper.ui.swing.ClassicDialog.Placement> classicPlacement(AuxiliarySurface surface, boolean ownerShowing) {
        if (!surface.classic() || !surface.modal() || !ownerShowing) return Optional.empty();
        return switch (surface.kind()) {
            case OVERLAY -> Optional.of(dev.jasper.ui.swing.ClassicDialog.Placement.CENTERED);
            case DIALOG -> surface.attachedSheet() ? Optional.of(dev.jasper.ui.swing.ClassicDialog.Placement.SHEET) : Optional.empty();
            case WINDOW -> Optional.empty();
        };
    }

    /**
     * A classic dialog in the owner's window (spec 10.8): the content's form loses its own title and gains the footer
     * band, the card is centred on the window or joined under its title bar, and a modal show blocks until it closes.
     */
    private AuxiliarySurface.Shell inWindow(AuxiliarySurface surface, dev.jasper.ui.swing.ClassicDialog.Placement placement) {
        Window owner = owner(surface);
        var container = (javax.swing.RootPaneContainer) owner;
        SwingUtilities.updateComponentTreeUI(surface.holder());
        for (var child : surface.holder().getComponents())
            if (child instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()) CLASSIC_FLAGS.accept(view);
        // AuxiliarySurface.setContent marks content set later the same way.
        surface.laterContent(CLASSIC_FLAGS);
        var dialog = new dev.jasper.ui.swing.ClassicDialog(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), surface.holder(),
            surface.title(), surface.swatch(), placement);
        dialog.windowTitleHeight(owner.getInsets().top);
        var titleBar = followTitleBar(owner, dialog);
        owner.addComponentListener(titleBar);
        var overlay = WindowOverlay.classic(container.getRootPane(), dialog);
        surface.holder().getAccessibleContext().setAccessibleName(surface.title());
        var close = new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { surface.close(); }
        };
        owner.addWindowListener(close);
        // Surfaces it owns hang from its owner's window, as an overlay's do.
        drawnInWindow.add(surface);
        Runnable show = () -> {
            overlay.show();
            if (surface.closed()) return;
            var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            var closed = surface.onClosed(loop::exit);
            try { if (!surface.closed()) loop.enter(); } finally { closed.close(); }
        };
        return new AuxiliarySurface.Shell(show, overlay::focus, () -> {
                drawnInWindow.remove(surface); owner.removeWindowListener(close); owner.removeComponentListener(titleBar); overlay.close();
            },
            title -> { dialog.setTitle(title); surface.holder().getAccessibleContext().setAccessibleName(title); }, overlay::cardBounds,
            (title, initial) -> chooseFile(TopModal.over(owner), title, initial, Optional.empty()));
    }

    /**
     * Keeps a centred classic dialog centred on its whole window as the window's title bar (its top inset) changes under
     * it, as macOS full screen hides it: added to the owner window, it re-reads the inset on every resize.
     */
    static ComponentListener followTitleBar(java.awt.Container window, dev.jasper.ui.swing.ClassicDialog dialog) {
        return new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { dialog.windowTitleHeight(window.getInsets().top); }
        };
    }

    /**
     * The kit's modal overlay inside the owner's window: a terminal window, an auxiliary window or a dialog. A modal
     * one (a dialog presented as an overlay) blocks {@code show} until it closes, as a modal dialog does, and lets
     * Escape reach its content; a progress overlay returns at once and keeps Escape from the window.
     */
    private AuxiliarySurface.Shell overlay(AuxiliarySurface surface) {
        Window owner = owner(surface);
        if (!(owner instanceof javax.swing.RootPaneContainer container))
            throw new IllegalStateException("An overlay needs an open window");
        SwingUtilities.updateComponentTreeUI(surface.holder());
        var overlay = new WindowOverlay(container.getRootPane(), surface.holder(),
            themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), surface.modal());
        surface.holder().getAccessibleContext().setAccessibleName(surface.title());
        var close = new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { surface.close(); }
        };
        owner.addWindowListener(close);
        Runnable show = () -> {
            overlay.show();
            if (!surface.modal() || surface.closed()) return;
            var loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            var closed = surface.onClosed(loop::exit);
            try { if (!surface.closed()) loop.enter(); } finally { closed.close(); }
        };
        return new AuxiliarySurface.Shell(show, overlay::focus,
            () -> { owner.removeWindowListener(close); overlay.close(); },
            title -> surface.holder().getAccessibleContext().setAccessibleName(title), overlay::cardBounds,
            (title, initial) -> chooseFile(TopModal.over(owner), title, initial, Optional.empty()));
    }

    /** Bounded shell resources exist before any native show can reenter plugin callbacks. */
    private final class ShellResources implements AutoCloseable {
        final AuxiliarySurface surface; final Window window;
        ShellLayout layout; Subscription theme; FloatWhileActive floating; AttachedSheet attachment;
        Runnable unwatch = () -> {}; boolean closed;
        ShellResources(AuxiliarySurface surface, Window window) { this.surface = surface; this.window = window; natives.put(surface, window); }
        @Override public void close() {
            if (closed) return; closed = true;
            unwatch.run();
            if (attachment != null) attachment.close();
            if (floating != null) floating.close();
            if (theme != null) theme.close();
            if (layout != null) layout.close();
            natives.remove(surface, window); window.dispose();
        }
    }

    private AuxiliarySurface.Shell frame(AuxiliarySurface surface) {
        var frame = new JFrame(surface.title());
        var resources = new ShellResources(surface, frame);
        try {
            frame.setIconImages(ApplicationIcon.images());
            resources.layout = installChrome(frame.getRootPane(), surface, shell.unifiedTitle());
            MacTitleBar bar = resources.layout.bar();
            // Under in-window menus (spec 10.1) the minimal File menu is a row of this frame, so Close Window keeps its key;
            // a classic look gives it its terminal windows' row.
            JMenuBar menus = menuBar(surface);
            if (bar != null) bar.setMenuBar(menus); else { frame.setJMenuBar(menus); shell.styleMenus(menus); }
            shell.followTheme(frame.getRootPane(), bar, themes.current().chrome().dark());
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
                @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
                @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
            });
            if (bar != null) bar.attach(frame);
            frame.pack();
            var saved = state.window(surface.id()).map(value -> new Rectangle(value.x(), value.y(), value.width(), value.height())).filter(NativeShells::onSomeScreen);
            if (saved.isPresent()) frame.setBounds(saved.get());
            else { frame.setSize(initialSize(surface, frame.getSize(), surface.holder().getSize(), usableBounds(frame))); frame.setLocationByPlatform(true); }
            resources.theme = themes.subscribe((resolved, chromeChanged) -> {
                if (chromeChanged) SwingUtilities.updateComponentTreeUI(frame);
                if (bar != null) bar.setLight(!resolved.chrome().dark()); else shell.styleMenus(menus);
                shell.followTheme(frame.getRootPane(), bar, resolved.chrome().dark());
                resources.layout.body().changed();
            });
            return new AuxiliarySurface.Shell(() -> { if (!surface.closed()) frame.setVisible(true); }, () -> {
                if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
                frame.toFront(); frame.requestFocus();
            }, resources::close, title -> { frame.setTitle(title); if (bar != null) bar.setTitle(title); }, frame::getBounds,
                (title, initial) -> chooseFile(TopModal.over(frame), title, initial, Optional.empty()));
        } catch (RuntimeException | Error failure) { resources.close(); throw failure; }
    }

    /** Content default adds the measured actual chrome delta once; remembered bounds bypass this path. */
    static java.awt.Dimension initialSize(AuxiliarySurface surface, java.awt.Dimension packed, java.awt.Dimension body, Rectangle usable) {
        var requested = surface.preferredSize();
        if (surface.contentSize()) {
            requested.width += Math.max(0, packed.width - body.width);
            requested.height += Math.max(0, packed.height - body.height);
        }
        return new java.awt.Dimension(Math.min(requested.width, usable.width), Math.min(requested.height, usable.height));
    }

    private Window owner(AuxiliarySurface surface) {
        var placed = windowed(surface, drawnInWindow::contains);
        if (placed.ownerSurface().isPresent()) return natives.get(placed.ownerSurface().orElseThrow());
        return placed.anchorOwner().map(SwingUtilities::getWindowAncestor).orElseGet(() -> placed.ownerWindow().map(terminalWindows).orElse(null));
    }

    /**
     * The surface whose owner holds {@code surface}'s window: the nearest one up the owner chain whose owner surface, if
     * any, has a native window. An overlay or a classic dialog drawn in its owner's window ({@code drawnInWindow}) has
     * none, so what it owns hangs from its own owner's window; an overlay that opened as a native dialog has its own.
     */
    static AuxiliarySurface windowed(AuxiliarySurface surface, java.util.function.Predicate<AuxiliarySurface> drawnInWindow) {
        var at = surface;
        for (var parent = at.ownerSurface(); parent.isPresent(); parent = at.ownerSurface()) {
            var owner = parent.orElseThrow();
            boolean windowless = owner.kind() == AuxiliarySurface.Kind.OVERLAY && !owner.nativeDialog() || drawnInWindow.test(owner);
            if (!windowless) break;
            at = owner;
        }
        return at;
    }

    /**
     * Whether a classic dialog can be drawn in {@code window} for the user to see: it is showing, and neither it nor a
     * window it hangs from is minimised (a minimised frame still reports that it is showing).
     */
    private static boolean onScreen(Window window) {
        if (!window.isShowing()) return false;
        for (Window at = window; at != null; at = at.getOwner())
            if (at instanceof java.awt.Frame frame && (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) return false;
        return true;
    }
    private boolean liveOwner(AuxiliarySurface surface, Window expected) {
        return !surface.closed() && expected != null && expected.isShowing() && owner(surface) == expected
            && surface.ownerSurface().map(AuxiliarySurface::shown).orElse(true)
            && surface.anchorOwner().map(javax.swing.JComponent::isShowing).orElse(true);
    }
    private static dev.jasper.app.platform.WindowBody body(Window owner) {
        if (!(owner instanceof javax.swing.RootPaneContainer container)) throw new IllegalArgumentException("Attached sheet needs a native root");
        return dev.jasper.app.platform.WindowBody.find(container.getRootPane()).filter(value -> value.boundsInRoot().isPresent())
            .orElseThrow(() -> new IllegalArgumentException("Attached sheet needs a registered measured owner body"));
    }
    /**
     * Whether the user may resize this dialog's native window: only one that remembers its bounds. A dialog sized to its
     * content (every plugin dialog, a confirmation) and an attached sheet are fixed-size; their content still re-packs them.
     */
    static boolean resizable(AuxiliarySurface surface) { return !surface.attachedSheet() && !surface.id().isEmpty(); }

    private AuxiliarySurface.Shell dialog(AuxiliarySurface surface) {
        Window owner = owner(surface);
        boolean attached = surface.attachedSheet(), remembered = !surface.id().isEmpty();
        if (attached && !liveOwner(surface, owner)) throw new IllegalArgumentException("Attached sheet needs a live shown owner");
        var ownerBody = attached ? body(owner) : null;
        Dialog.ModalityType modality = !surface.modal() ? Dialog.ModalityType.MODELESS
            : owner == null ? Dialog.ModalityType.APPLICATION_MODAL : Dialog.ModalityType.DOCUMENT_MODAL;
        var dialog = new JDialog(owner, surface.title(), modality);
        var resources = new ShellResources(surface, dialog);
        try {
            if (attached) {
                dialog.setUndecorated(true);
                if (dialog.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                    dialog.setBackground(new java.awt.Color(0, 0, 0, 0)); dialog.getRootPane().setOpaque(false);
                    dialog.getLayeredPane().setOpaque(false); surface.holder().setOpaque(false);
                }
            }
            dialog.setResizable(resizable(surface));
            dialog.getAccessibleContext().setAccessibleName(surface.title());
            dialog.setIconImages(ApplicationIcon.images());
            resources.layout = installChrome(dialog.getRootPane(), surface, shell.unifiedTitle());
            MacTitleBar bar = resources.layout.bar();
            shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());
            // Spec 10.8: the title bar the system draws titles the dialog, so a FULL form does not repeat it.
            if (bar == null && surface.hostTitled())
                for (var child : surface.holder().getComponents())
                    if (child instanceof dev.jasper.ui.swing.KitView view && !view.isClosed()
                            && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Form form && form.kind() == dev.jasper.ui.model.Nodes.FormKind.FULL)
                        view.hostTitled(true);
            // Content set later takes the same flag (AuxiliarySurface.setContent).
            if (bar == null && surface.hostTitled())
                surface.laterContent(view -> {
                    if (view.spec().root() instanceof dev.jasper.ui.model.Nodes.Form form && form.kind() == dev.jasper.ui.model.Nodes.FormKind.FULL)
                        view.hostTitled(true);
                });
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
                @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
                @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
            });
            if (bar != null) bar.attach(dialog);
            dialog.pack();
            if (remembered) {
                dialog.setSize(surface.preferredSize()); dialog.setLocationRelativeTo(owner);
                state.window(surface.id()).map(saved -> new Rectangle(saved.x(), saved.y(), saved.width(), saved.height())).filter(NativeShells::onSomeScreen).ifPresent(dialog::setBounds);
            } else if (!attached) dialog.setLocationRelativeTo(owner);
            resources.theme = themes.subscribe((resolved, chromeChanged) -> {
                if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
                if (bar != null) bar.setLight(!resolved.chrome().dark());
                shell.followTheme(dialog.getRootPane(), bar, resolved.chrome().dark());
                if (resources.attachment != null) resources.attachment.changed();
            });
            resources.floating = surface.modal() ? FloatWhileActive.attach(dialog) : null;
            DialogFit fit = remembered || attached ? null : new DialogFit(surface.holder(), fitTarget(dialog));
            if (fit != null) surface.fitWith(fit);
            boolean[] watching = {false};
            if (attached && (!liveOwner(surface, owner) || body(owner) != ownerBody)) throw new IllegalArgumentException("Sheet owner changed during construction");
            return new AuxiliarySurface.Shell(() -> {
                if (surface.closed()) return;
                if (attached) {
                    if (!liveOwner(surface, owner) || body(owner) != ownerBody) throw new IllegalArgumentException("Sheet owner is no longer live");
                    if (resources.attachment == null) {
                        resources.attachment = attachSheet(surface, ownerBody, new AttachedSheet.Target(
                            () -> liveOwner(surface, owner), () -> ownerBody.root().getLocationOnScreen(), () -> usableBounds(owner), dialog::setBounds));
                        resources.unwatch = watchOwner(owner, resources.attachment);
                    }
                    resources.attachment.fitNow();
                } else if (!remembered) {
                    dialog.pack(); dialog.setLocationRelativeTo(owner); fit.packed();
                    if (!watching[0]) {
                        watching[0] = true;
                        dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
                            @Override public void componentResized(java.awt.event.ComponentEvent event) { fit.resized(dialog.getSize()); }
                        });
                    }
                }
                if (surface.closed()) return;
                if (resources.floating != null) resources.floating.update();
                dialog.setVisible(true);
            }, dialog::toFront, resources::close,
                title -> { dialog.setTitle(title); dialog.getAccessibleContext().setAccessibleName(title); if (bar != null) bar.setTitle(title); }, dialog::getBounds,
                (title, initial) -> chooseFile(TopModal.over(dialog), title, initial, Optional.empty()));
        } catch (RuntimeException | Error failure) { resources.close(); throw failure; }
    }
    private static Runnable watchOwner(Window owner, AttachedSheet attachment) {
        var geometry = new java.awt.event.ComponentAdapter() {
            @Override public void componentMoved(java.awt.event.ComponentEvent event) { attachment.changed(); }
            @Override public void componentResized(java.awt.event.ComponentEvent event) { attachment.changed(); }
            @Override public void componentHidden(java.awt.event.ComponentEvent event) { attachment.changed(); }
        };
        java.beans.PropertyChangeListener display = event -> attachment.changed();
        owner.addComponentListener(geometry); owner.addPropertyChangeListener("graphicsConfiguration", display);
        return () -> { owner.removeComponentListener(geometry); owner.removePropertyChangeListener("graphicsConfiguration", display); };
    }
    private static Rectangle usableBounds(Window window) {
        var configuration = window.getGraphicsConfiguration(); Rectangle screen = configuration.getBounds();
        var insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(screen.x + insets.left, screen.y + insets.top, screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
    }

    /** Used by the native shell and headless integration tests with the same registered owner boundary. */
    static AttachedSheet attachSheet(AuxiliarySurface surface, dev.jasper.app.platform.WindowBody body, AttachedSheet.Target target) {
        var attachment = new AttachedSheet(surface, body, target);
        surface.attachWith(attachment);
        return attachment;
    }

    record ShellLayout(MacTitleBar bar, dev.jasper.app.platform.WindowBody body, AuxiliarySurface surface) implements AutoCloseable {
        @Override public void close() { surface.onTitleActionsChanged(() -> {}); body.close(); if (bar != null) bar.close(); }
    }
    /** The one production chrome/body registration path for auxiliary windows and immediate dialog owners. */
    static ShellLayout installChrome(JRootPane root, AuxiliarySurface surface, boolean supported) {
        MacTitleBar bar;
        if (surface.attachedSheet()) { root.setOpaque(false);root.getLayeredPane().setOpaque(false);surface.holder().setOpaque(false);root.setContentPane(surface.holder()); bar = null; }
        else bar = installTitleBar(root, surface, supported);
        var body = dev.jasper.app.platform.WindowBody.install(root, surface.holder(),
            () -> new Rectangle(0, 0, surface.holder().getWidth(), surface.holder().getHeight()));
        return new ShellLayout(bar, body, surface);
    }

    /** The native side of {@link DialogFit}: the dialog's own size, pack size and its screen's usable bounds. */
    private static DialogFit.Target fitTarget(JDialog dialog) {
        return new DialogFit.Target() {
            @Override public java.awt.Dimension size() { return dialog.getSize(); }
            @Override public java.awt.Dimension packedSize() { return dialog.getPreferredSize(); }
            @Override public Rectangle usableBounds() {
                var configuration = dialog.getGraphicsConfiguration();
                Rectangle screen = configuration.getBounds();
                var insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
                return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                    screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
            }
            @Override public java.awt.Point location() { return dialog.getLocation(); }
            @Override public void setBounds(Rectangle bounds) { dialog.setBounds(bounds); }
        };
    }

    /** Shared, headless-testable title setup for both kinds of SDK/application auxiliary surface. */
    static MacTitleBar installTitleBar(JRootPane root, AuxiliarySurface surface, boolean supported) {
        MacTitleBar bar = MacTitleBar.install(root, surface.holder(), () -> { }, supported);
        surface.titleActions().setOpaque(false);
        surface.titleActions().setVisible(surface.titleActions().getComponentCount() > 0);
        if (bar == null && surface.kind() == AuxiliarySurface.Kind.WINDOW) {
            var content = new JPanel(new java.awt.BorderLayout());
            var toolbar = new JPanel(new java.awt.BorderLayout());
            toolbar.add(surface.titleActions(), java.awt.BorderLayout.EAST);
            toolbar.setVisible(surface.titleActions().isVisible());
            content.add(toolbar, java.awt.BorderLayout.NORTH); content.add(surface.holder(), java.awt.BorderLayout.CENTER);
            root.setContentPane(content);
            surface.onTitleActionsChanged(() -> { toolbar.setVisible(surface.titleActions().isVisible()); content.revalidate(); content.repaint(); });
        }
        if (bar != null) {
            bar.setStyle(surface.kind() == AuxiliarySurface.Kind.WINDOW ? MacTitleBar.Style.WINDOW : MacTitleBar.Style.DIALOG);
            if (surface.kind() == AuxiliarySurface.Kind.WINDOW) {
                bar.setAccessory(surface.titleActions()); surface.onTitleActionsChanged(bar::accessoryChanged);
            }
            bar.setTitle(surface.title());
            bar.setTitleSwatch(surface.swatch());
            for (var child : surface.holder().getComponents()) {
                if (child instanceof dev.jasper.ui.swing.KitView view
                        && view.spec().root() instanceof dev.jasper.ui.model.Nodes.Form form
                        && (form.kind() == dev.jasper.ui.model.Nodes.FormKind.PROMPT || form.kind() == dev.jasper.ui.model.Nodes.FormKind.PROMPT_COMPACT))
                    bar.setStyle(MacTitleBar.Style.PROMPT);
            }
            surface.integratedTitle().ifPresent(bar::integrate);
        }
        return bar;
    }

    /**
     * Asks for one existing file with the platform's own dialog, over a surface's window.
     *
     * @param owner the surface whose window the dialog belongs to
     * @param title the dialog title
     * @param suffix the file name suffix to offer, for example {@code .zip}
     * @return the chosen file, or empty when the user cancelled
     */
    public Optional<Path> chooseFile(AuxiliarySurface owner, String title, String suffix) {
        Window parent = natives.get(owner);
        if (parent == null || !owner.shown()) throw new IllegalStateException("File selection needs a shown, open window");
        Optional<Path> selected = chooseFile(TopModal.over(parent), title, Optional.empty(), Optional.of(suffix));
        return owner.closed() ? Optional.empty() : selected;
    }

    /** Owner-based file, folder or save-target selection in Jasper's own chooser, over any modal dialog the owner shows. */
    public List<Location> choosePaths(PathChoice choice, Consumer<Runnable> cancellation) {
        Window owner = choice.terminalOwner() != null ? terminalWindows.apply(choice.terminalOwner()) : natives.get(choice.auxiliaryOwner());
        if (owner == null || !owner.isShowing()) throw new IllegalArgumentException("Picker requires a shown owner");
        owner = TopModal.over(owner); // over a modal dialog showing on the owner, not blocked and covered by it
        ChooserRequest.Mode mode = switch (choice.mode()) {
            case OPEN -> ChooserRequest.Mode.OPEN;
            case DIRECTORY -> ChooserRequest.Mode.DIRECTORY;
            case SAVE -> ChooserRequest.Mode.SAVE;
        };
        var local = new LocalBrowser();
        return showChooser(owner, ChooserRequest.of(choice.title(), mode, choice.multiple(), choice.initial(), choice.terminalFolder(),
            choice.suggestedName(), Optional.empty(), Optional.ofNullable(choice.terminalOwner()), local.homePath()), local, choice.sources(), cancellation);
    }

    private Optional<Path> chooseFile(Window owner, String title, Optional<Path> initialPath, Optional<String> suffix) {
        var local = new LocalBrowser();
        return showChooser(owner, ChooserRequest.of(title, ChooserRequest.Mode.OPEN, false, initialPath.map(Location::local), Optional.empty(),
                Optional.empty(), suffix, Optional.empty(), local.homePath()), local, List.of(), action -> { })
            .stream().filter(Location::isLocal).map(Location::localPath).findFirst();
    }

    /** Shows the chooser document-modally over {@code owner}; returns when it closes. Empty means cancelled. */
    private List<Location> showChooser(Window owner, ChooserRequest request, LocalBrowser local, List<ChooserSource> extra,
                                       Consumer<Runnable> cancellation) {
        var dialog = new JDialog(owner, request.title(), Dialog.ModalityType.DOCUMENT_MODAL);
        ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "jasper-file-chooser");
            thread.setDaemon(true);
            return thread;
        });
        var sources = new java.util.ArrayList<ChooserSource>();
        sources.add(new LocalSource(local));
        sources.addAll(extra);
        var prefs = new ChooserPrefs(state);
        var model = new ChooserModel(request, sources, worker, SwingUtilities::invokeLater, prefs.showHidden());
        var result = new AtomicReference<List<Location>>(List.of());
        // The replace and new-folder questions are kit sheets over the chooser (Plan 2 Task 6c), not option panes.
        var prompts = new ChooserPrompts(() -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), title -> sheet(dialog, title));
        var panel = new FileChooserPanel(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), model, prefs, local.homePath(), chosen -> { result.set(chosen); dialog.dispose(); },
            file -> prompts.confirmReplace(model.name(file)), prompts::askFolderName);
        // Jasper's own title bar, as on the application's other dialogs, so the chooser matches the theme.
        MacTitleBar bar = MacTitleBar.install(dialog.getRootPane(), panel, () -> { }, shell.unifiedTitle());
        shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
            shell.followTheme(dialog.getRootPane(), bar, resolved.chrome().dark());
        });
        try {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setIconImages(ApplicationIcon.images());
            if (bar != null) {
                bar.setTitle(request.title());
                dialog.addWindowListener(new WindowAdapter() {
                    @Override public void windowActivated(WindowEvent event) { bar.setActive(true); }
                    @Override public void windowDeactivated(WindowEvent event) { bar.setActive(false); }
                });
                bar.attach(dialog);
            }
            panel.installDefaultAction(dialog.getRootPane());
            // As JOptionPane does: the first focus goes where the user starts, not to the first enabled toolbar button,
            // which AWT would focus while the folder is still loading.
            dialog.setFocusTraversalPolicy(new javax.swing.LayoutFocusTraversalPolicy() {
                @Override public java.awt.Component getInitialComponent(Window window) { return null; }
            });
            dialog.addWindowFocusListener(new WindowAdapter() {
                private boolean focused;
                @Override public void windowGainedFocus(WindowEvent event) { if (!focused) { focused = true; panel.focusInitially(); } }
            });
            var size = state.window(CHOOSER_BOUNDS).map(saved -> new java.awt.Dimension(saved.width(), saved.height()))
                .orElse(new java.awt.Dimension(820, 520));
            dialog.setSize(size);
            dialog.setLocationRelativeTo(owner);
            cancellation.accept(dialog::dispose);
            model.start();
            dialog.setVisible(true);
            return result.get();
        } finally {
            Rectangle bounds = dialog.getBounds();
            if (bounds.width > 0 && bounds.height > 0) {
                state.putWindow(CHOOSER_BOUNDS, new UiState.Bounds(bounds.x, bounds.y, bounds.width, bounds.height));
                state.save();
            }
            theme.close();
            if (bar != null) bar.close();
            panel.close();
            model.close();
            dialog.dispose();
            worker.shutdown();
        }
    }

    /**
     * A document-modal window over {@code owner} holding one kit sheet; closing it by its title bar cancels. When it
     * closes, the owner gets the focus back.
     */
    private ChooserPrompts.Modal sheet(Window owner, String title) {
        var window = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
        shell.followTheme(window.getRootPane(), null, themes.current().chrome().dark());
        window.setIconImages(ApplicationIcon.images());
        window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        return new ChooserPrompts.Modal() {
            @Override public void setContent(javax.swing.JComponent content) { window.getContentPane().add(content); }
            @Override public void show(Runnable shown) {
                window.addWindowListener(new WindowAdapter() {
                    @Override public void windowOpened(WindowEvent event) { shown.run(); }
                });
                window.pack();
                window.setLocationRelativeTo(owner);
                window.setVisible(true);
            }
            @Override public void close() {
                if (!window.isDisplayable()) return;
                window.dispose();
                owner.toFront();
            }
        };
    }

    /** A minimal menu bar: macOS otherwise shows only the application menu while this window has focus. */
    private JMenuBar menuBar(AuxiliarySurface surface) {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var file = new JMenu("File");
        var open = new JMenuItem("New Window");
        open.addActionListener(event -> newWindow.run());
        var close = new JMenuItem("Close Window");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcut));
        close.addActionListener(event -> {
            if (!dev.jasper.app.platform.WindowInput.captureShortcut(surface.holder(), close.getAccelerator())) surface.closeCommand();
        });
        file.add(open); file.add(close);
        if (!SystemInfo.isMacOS) {
            var exit = new JMenuItem("Quit");
            exit.addActionListener(event -> quit.run());
            file.addSeparator(); file.add(exit);
        }
        var bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    /** Remembered bounds are ignored when no current display shows them, for example after unplugging a monitor. */
    private static boolean onSomeScreen(Rectangle bounds) {
        for (var device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
            if (device.getDefaultConfiguration().getBounds().intersects(bounds)) return true;
        return false;
    }
}
