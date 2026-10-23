package dev.jasper.app.workspace;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.dock.ToolWindowMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.ApplicationIcon;
import dev.jasper.app.platform.MacAppearance;
import dev.jasper.app.platform.MacTitleBand;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.platform.PlainTitlePane;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.app.platform.WindowInput;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.swing.JComponent;
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

/**
 * The native window of one of the main window's detached tool windows (floating tool windows spec, The detached window),
 * the shell {@code DetachedToolWindows} drives: Float's modeless dialog owned by the main window's frame, or Window's
 * ownerless frame, titled with the tool window's title and holding its frame. Its chrome follows the rules of auxiliary
 * windows ({@code NativeShells}) and child windows ({@link WindowContent#mountChild}): on macOS the unified title bar, a
 * dialog's row for Float and a window's for Window; without it, under a classic title IntelliJ Classic's band, and
 * otherwise the system's title bar, in the theme's appearance when the look follows it; on Windows and Linux FlatLaf's
 * title pane in the chrome's colours. It has the application icon, follows theme changes, binds Jasper's shortcuts on
 * its root, and on macOS with the screen menu bar a Window frame carries a minimal File menu so that the screen menu bar
 * does not empty (a Float dialog shows its owner's). Everything testable lives in {@code DetachedToolWindows}; this is
 * never constructed in headless tests. EDT only.
 */
public final class DetachedFrame {
    private final DetachedToolWindows.Events events;
    private final Window window;
    /** The Window mode's frame; null for a Float dialog. */
    private final JFrame frame;
    private final JRootPane root;
    /** The content pane's body: the tool window's frame at its centre. */
    private final JPanel holder = new JPanel(new BorderLayout());
    private MacTitleBar bar;
    private MacTitleBand band;
    /** Gives FlatLaf's title pane the chrome's colours, a classic title's dimmed while inactive (Windows and Linux). */
    private Runnable titleColors = () -> { };
    private AutoCloseable keys;
    private Subscription theme;
    private boolean disposed;

    private DetachedFrame(DetachedToolWindows.Opening opening, DetachedToolWindows.Events events) {
        this.events = events;
        if (opening.mode() == ToolWindowMode.FLOAT) {
            var dialog = new JDialog(opening.owner(), opening.title(), Dialog.ModalityType.MODELESS);
            dialog.setResizable(true);
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window = dialog;
            root = dialog.getRootPane();
            frame = null;
        } else {
            frame = new JFrame(opening.title());
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            window = frame;
            root = frame.getRootPane();
        }
    }

    /**
     * Makes the main window's detached tool windows' native windows, for {@link WindowContent#detachedShells}. Never
     * used headless.
     *
     * @param themes the application's themes, which the windows follow
     * @param look the look's title facts
     * @return makes a detached tool window's native window from what it opens with and what it reports
     */
    public static BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events, DetachedToolWindows.Shell> shells(
            ThemeController themes, WindowLook look) {
        Objects.requireNonNull(themes, "themes");
        Objects.requireNonNull(look, "look");
        return (opening, events) -> open(opening, events, themes, look);
    }

    /**
     * The native window {@code opening} describes, not yet shown. When building it fails, what was built goes and the
     * failure propagates, the cleanup's under it.
     */
    static DetachedToolWindows.Shell open(DetachedToolWindows.Opening opening, DetachedToolWindows.Events events,
                                          ThemeController themes, WindowLook look) {
        var made = new DetachedFrame(opening, events);
        try {
            made.build(opening, themes, look);
        } catch (RuntimeException | Error failure) {
            try { made.dispose(); } catch (RuntimeException | Error disposing) { failure.addSuppressed(disposing); }
            throw failure;
        }
        return new DetachedToolWindows.Shell(made::show, made::toFront, made::dispose, made::active, made::place, made::content);
    }

    private void build(DetachedToolWindows.Opening opening, ThemeController themes, WindowLook look) {
        window.setIconImages(ApplicationIcon.images());
        PlainTitlePane.install(root);
        window.getAccessibleContext().setAccessibleName(opening.title());
        holder.add(opening.frame(), BorderLayout.CENTER);
        bar = MacTitleBar.install(root, holder, () -> { }, look.unifiedTitle());
        Consumer<ResolvedTheme> follow = chrome(opening, look);
        // The screen menu bar shows a frame's own menus; a dialog shows its owner's.
        if (frame != null && WindowFrame.screenMenuBar()) root.setJMenuBar(menuBar());
        if (bar != null) bar.attach(window);
        window.setMinimumSize(DetachedToolWindows.minimumSize());
        window.setBounds(opening.bounds());
        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { if (!disposed) events.closing().run(); }
            @Override public void windowActivated(WindowEvent event) { activated(true); }
            @Override public void windowDeactivated(WindowEvent event) { activated(false); }
        });
        window.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent event) { moved(); }
            @Override public void componentResized(ComponentEvent event) { moved(); }
        });
        keys = opening.keys().apply(root);
        // Replays the current theme at once: the window's delegates and decorations start in it.
        theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(window);
            follow.accept(resolved);
        });
    }

    /**
     * Puts the title bar in place, as {@link WindowContent#mountChild} chooses it, and returns how it follows the theme:
     * the unified macOS bar, IntelliJ Classic's band under a classic title on macOS, the system's bar there otherwise, and
     * FlatLaf's title pane on Windows and Linux, 28 px under a classic title.
     */
    private Consumer<ResolvedTheme> chrome(DetachedToolWindows.Opening opening, WindowLook look) {
        if (bar != null) {
            bar.setStyle(opening.mode() == ToolWindowMode.FLOAT ? MacTitleBar.Style.DIALOG : MacTitleBar.Style.WINDOW);
            bar.setTitle(opening.title());
            // The bar paints the chrome's colours; the native controls on it take the chrome's appearance.
            return resolved -> bar.setLight(!resolved.chrome().dark());
        }
        if (SystemInfo.isMacOS && look.classicTitle()) {
            band = MacTitleBand.install(root, () -> ThemeZone.color(null, SemanticColor.SURFACE_APP));
            return resolved -> band.setDark(resolved.chrome().dark());
        }
        if (SystemInfo.isMacOS)
            return look.followThemeAppearance() ? resolved -> MacAppearance.follow(root, resolved.chrome().dark()) : resolved -> { };
        boolean classic = look.classicTitle();
        if (classic) root.putClientProperty(FlatClientProperties.TITLE_BAR_HEIGHT, ClassicWindowTitle.BAR_HEIGHT);
        // FlatLaf paints the title in this one colour, active or not, so a classic title dims it while the window is inactive.
        titleColors = () -> {
            root.putClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(null, SemanticColor.SURFACE_APP));
            root.putClientProperty(FlatClientProperties.TITLE_BAR_FOREGROUND, classic
                ? SurfaceContrast.painted(FlatLafInstaller.installedTheme(), window.isActive() ? WindowPairs.TITLE : WindowPairs.INACTIVE_TITLE)
                : ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
        };
        return resolved -> titleColors.run();
    }

    /**
     * A Window frame's minimal menu bar, as auxiliary frames have it: File › Close Window on the menu shortcut and W, which
     * hides the tool window as the close button does unless the window is recording shortcuts. Only macOS shows it, in the
     * screen menu bar, so it has no Quit: the application menu does.
     */
    private JMenuBar menuBar() {
        var close = new JMenuItem("Close Window");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        close.addActionListener(event -> {
            if (!disposed && !WindowInput.captureShortcut(root, close.getAccelerator())) events.closing().run();
        });
        var file = new JMenu("File");
        file.add(close);
        var menus = new JMenuBar();
        menus.add(file);
        return menus;
    }

    /**
     * Shows the window without taking the focus: a start-up restore, Zen's return or a plain show leaves it where it is,
     * and only an activating request focuses the window ({@link #toFront}). {@code toFront} honours the same flag, so it
     * is restored at once.
     */
    private void show() {
        if (disposed) return;
        window.setAutoRequestFocus(false);
        try { window.setVisible(true); }
        finally { window.setAutoRequestFocus(true); }
    }

    /**
     * Raises the window and gives it the focus: a Float dialog hides while its owner is minimised, so the owner comes
     * back first; a Window frame comes back itself.
     */
    private void toFront() {
        if (disposed) return;
        Frame minimised = frame != null ? frame : window.getOwner() instanceof Frame owner ? owner : null;
        if (minimised != null && (minimised.getExtendedState() & Frame.ICONIFIED) != 0)
            minimised.setExtendedState(minimised.getExtendedState() & ~Frame.ICONIFIED);
        window.toFront();
        window.requestFocus();
    }

    /** Whether the window is the active one, read from the window each time. */
    private boolean active() { return !disposed && (window.isActive() || window.isFocused()); }

    private void place(Rectangle bounds) { if (!disposed) window.setBounds(bounds); }

    /** Puts {@code replacement} in place of the frame the window holds. */
    private void content(JComponent replacement) {
        if (disposed) return;
        Component current = ((BorderLayout) holder.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (current == replacement) return;
        if (current != null) holder.remove(current);
        holder.add(replacement, BorderLayout.CENTER);
        holder.revalidate();
        holder.repaint();
    }

    private void activated(boolean active) {
        if (disposed) return;
        if (bar != null) bar.setActive(active);
        titleColors.run();
    }

    /** Reports where a shown window is while it is neither minimised nor maximised: the bounds it reopens at. */
    private void moved() {
        if (disposed || !window.isShowing() || frame != null && frame.getExtendedState() != Frame.NORMAL) return;
        events.moved().accept(window.getBounds());
    }

    /** Closes the theme subscription, the shortcut bindings and the title bar, then disposes the window. */
    private void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            if (theme != null) theme.close();
            if (keys != null) keys.close();
            if (bar != null) bar.close();
            if (band != null) band.close();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("A detached tool window's shortcuts could not be unbound", failure);
        } finally {
            window.dispose();
        }
    }
}
