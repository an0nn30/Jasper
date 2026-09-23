package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.appearance.BuiltinTheme;
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
import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The native boundary for auxiliary windows: frame or dialog, application icon, the unified macOS title
 * bar, a menu bar so the screen menu does not vanish, theme tracking and remembered bounds. Everything
 * testable lives in {@link AuxiliarySurface}. EDT only; never constructed in headless tests.
 */
public final class NativeShells {
    private static final int TITLE_HEIGHT = 38;
    private final ThemeController themes;
    private final UiState state;
    private final Function<UUID, Window> terminalWindows;
    private final Runnable newWindow;
    private final Runnable quit;
    private final Map<AuxiliarySurface, Window> natives = new HashMap<>();

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
        this.themes = Objects.requireNonNull(themes); this.state = Objects.requireNonNull(state);
        this.terminalWindows = Objects.requireNonNull(terminalWindows);
        this.newWindow = Objects.requireNonNull(newWindow); this.quit = Objects.requireNonNull(quit);
    }

    /**
     * Builds the native window for a surface.
     *
     * @param surface the headless core
     * @return the functions the core drives
     */
    public AuxiliarySurface.Shell create(AuxiliarySurface surface) {
        return switch (surface.kind()) {
            case WINDOW -> frame(surface);
            case DIALOG -> dialog(surface);
            case OVERLAY -> overlay(surface);
        };
    }

    private AuxiliarySurface.Shell overlay(AuxiliarySurface surface) {
        Window owner = surface.ownerWindow().map(terminalWindows).orElse(null);
        if (!(owner instanceof javax.swing.RootPaneContainer container))
            throw new IllegalStateException("An overlay needs an open terminal window");
        var overlay = new WindowOverlay(container.getRootPane(), surface.holder());
        surface.holder().getAccessibleContext().setAccessibleName(surface.title());
        var close = new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { surface.close(); }
        };
        owner.addWindowListener(close);
        return new AuxiliarySurface.Shell(overlay::show, overlay::focus,
            () -> { owner.removeWindowListener(close); overlay.close(); },
            title -> surface.holder().getAccessibleContext().setAccessibleName(title), overlay::cardBounds,
            (title, initial) -> chooseFile(owner, title, initial, Optional.empty()));
    }

    private AuxiliarySurface.Shell frame(AuxiliarySurface surface) {
        var frame = new JFrame(surface.title());
        frame.setIconImages(ApplicationIcon.images());
        MacTitleBar bar = installTitleBar(frame.getRootPane(), surface, SystemInfo.isMacFullWindowContentSupported);
        frame.setJMenuBar(menuBar(surface));
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
            @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
        });
        if (bar != null) bar.attach(frame);
        frame.pack();
        frame.setSize(surface.preferredSize());
        frame.setLocationByPlatform(true);
        state.window(surface.id()).map(saved -> new Rectangle(saved.x(), saved.y(), saved.width(), saved.height()))
            .filter(NativeShells::onSomeScreen).ifPresent(frame::setBounds);
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(frame);
            if (bar != null) bar.setLight(resolved.chrome() == BuiltinTheme.LIGHT);
        });
        natives.put(surface, frame);
        return new AuxiliarySurface.Shell(() -> frame.setVisible(true), () -> {
            if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
            frame.toFront(); frame.requestFocus();
        }, () -> {
            theme.close();
            if (bar != null) bar.close();
            natives.remove(surface);
            frame.dispose();
        }, title -> { frame.setTitle(title); if (bar != null) bar.setTitle(title, true); }, frame::getBounds, (title, initial) -> chooseFile(frame, title, initial, Optional.empty()));
    }

    private AuxiliarySurface.Shell dialog(AuxiliarySurface surface) {
        Window owner = surface.ownerSurface().map(natives::get)
            .orElseGet(() -> surface.ownerWindow().map(terminalWindows).orElse(null));
        var dialog = new JDialog(owner, surface.title(), surface.modal() ? Dialog.ModalityType.DOCUMENT_MODAL : Dialog.ModalityType.MODELESS);
        dialog.setIconImages(ApplicationIcon.images());
        MacTitleBar bar = installTitleBar(dialog.getRootPane(), surface, SystemInfo.isMacFullWindowContentSupported);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
            @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
        });
        if (bar != null) bar.attach(dialog);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(resolved.chrome() == BuiltinTheme.LIGHT);
        });
        natives.put(surface, dialog);
        return new AuxiliarySurface.Shell(() -> { dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true); },
            dialog::toFront, () -> {
                theme.close();
                if (bar != null) bar.close();
                natives.remove(surface); dialog.dispose();
            }, title -> { dialog.setTitle(title); if (bar != null) bar.setTitle(title, true); }, dialog::getBounds, (title, initial) -> chooseFile(dialog, title, initial, Optional.empty()));
    }

    /** Shared, headless-testable title setup for both kinds of SDK/application auxiliary surface. */
    static MacTitleBar installTitleBar(JRootPane root, AuxiliarySurface surface, boolean supported) {
        MacTitleBar bar = MacTitleBar.install(root, surface.holder(), new JPanel(), () -> TITLE_HEIGHT, () -> { }, supported);
        if (bar != null) bar.setTitle(surface.title(), true);
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
        Optional<Path> selected = chooseFile(parent, title, Optional.empty(), Optional.of(suffix));
        return owner.closed() ? Optional.empty() : selected;
    }

    private static Optional<Path> chooseFile(Window owner, String title, Optional<Path> initialPath, Optional<String> suffix) {
        // A dialog-owned picker must stay above its modal editor, not merely above the editor's frame.
        FileDialog chooser = owner instanceof Dialog dialog ? new FileDialog(dialog, title, FileDialog.LOAD)
            : new FileDialog((Frame) owner, title, FileDialog.LOAD);
        try {
            suffix.ifPresent(value -> {
                String wanted = value.toLowerCase(Locale.ROOT);
                // macOS and Linux honor the filter; Windows honors the pattern.
                chooser.setFilenameFilter((directory, name) -> name.toLowerCase(Locale.ROOT).endsWith(wanted));
                chooser.setFile("*" + value);
            });
            initialPath.map(path -> path.toAbsolutePath().normalize()).ifPresent(path -> {
                if (java.nio.file.Files.isDirectory(path)) chooser.setDirectory(path.toString());
                else {
                    if (path.getParent() != null) chooser.setDirectory(path.getParent().toString());
                    if (path.getFileName() != null) chooser.setFile(path.getFileName().toString());
                }
            });
            chooser.setVisible(true);
            String file = chooser.getFile();
            return file == null ? Optional.empty() : Optional.of(Path.of(chooser.getDirectory(), file).toAbsolutePath().normalize());
        } finally {
            chooser.dispose();
        }
    }

    /** A minimal menu bar: macOS otherwise shows only the application menu while this window has focus. */
    private JMenuBar menuBar(AuxiliarySurface surface) {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var file = new JMenu("File");
        var open = new JMenuItem("New Window");
        open.addActionListener(event -> newWindow.run());
        var close = new JMenuItem("Close Window");
        close.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcut));
        close.addActionListener(event -> surface.requestClose());
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
