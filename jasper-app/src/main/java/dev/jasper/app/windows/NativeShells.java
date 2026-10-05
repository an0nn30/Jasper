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
        SwingUtilities.updateComponentTreeUI(surface.holder());
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
        MacTitleBar bar = installTitleBar(frame.getRootPane(), surface, MacTitleBar.isSupported());
        if (bar != null) bar.setMenuBar(menuBar(surface));
        else frame.setJMenuBar(menuBar(surface));
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
            if (bar != null) bar.setLight(!resolved.chrome().dark());
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
        }, title -> { frame.setTitle(title); if (bar != null) bar.setTitle(title); }, frame::getBounds, (title, initial) -> chooseFile(frame, title, initial, Optional.empty()));
    }

    private AuxiliarySurface.Shell dialog(AuxiliarySurface surface) {
        Window owner = surface.ownerSurface().map(natives::get)
            .orElseGet(() -> surface.ownerWindow().map(terminalWindows).orElse(null));
        boolean remembered = !surface.id().isEmpty();
        Dialog.ModalityType modality = !surface.modal() ? Dialog.ModalityType.MODELESS
            : owner == null ? Dialog.ModalityType.APPLICATION_MODAL : Dialog.ModalityType.DOCUMENT_MODAL;
        var dialog = new JDialog(owner, surface.title(), modality);
        dialog.setIconImages(ApplicationIcon.images());
        MacTitleBar bar = installTitleBar(dialog.getRootPane(), surface, MacTitleBar.isSupported());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
            @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
        });
        if (bar != null) bar.attach(dialog);
        dialog.pack();
        if (remembered) {
            dialog.setSize(surface.preferredSize());
            dialog.setLocationRelativeTo(owner);
            state.window(surface.id()).map(saved -> new Rectangle(saved.x(), saved.y(), saved.width(), saved.height()))
                .filter(NativeShells::onSomeScreen).ifPresent(dialog::setBounds);
        } else dialog.setLocationRelativeTo(owner);
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
        });
        FloatWhileActive floating = surface.modal() ? FloatWhileActive.attach(dialog) : null;
        natives.put(surface, dialog);
        return new AuxiliarySurface.Shell(() -> {
                if (!remembered) { dialog.pack(); dialog.setLocationRelativeTo(owner); }
                if (floating != null) floating.update();
                dialog.setVisible(true);
            },
            dialog::toFront, () -> {
                if (floating != null) floating.close();
                theme.close();
                if (bar != null) bar.close();
                natives.remove(surface); dialog.dispose();
            }, title -> { dialog.setTitle(title); if (bar != null) bar.setTitle(title); }, dialog::getBounds, (title, initial) -> chooseFile(dialog, title, initial, Optional.empty()));
    }

    /** Shared, headless-testable title setup for both kinds of SDK/application auxiliary surface. */
    static MacTitleBar installTitleBar(JRootPane root, AuxiliarySurface surface, boolean supported) {
        MacTitleBar bar = MacTitleBar.install(root, surface.holder(), () -> { }, supported);
        if (bar != null) bar.setTitle(surface.title());
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
        MacTitleBar bar = MacTitleBar.install(dialog.getRootPane(), panel, () -> { }, MacTitleBar.isSupported());
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
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
    private static ChooserPrompts.Modal sheet(Window owner, String title) {
        var window = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
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
