package dev.jasper.app.workspace;

import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.theme.SemanticColor;

import dev.jasper.ui.theme.GlassLevels;
import dev.jasper.app.appearance.ResolvedTheme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.commands.Command;
import dev.jasper.app.commands.CommandRegistry;
import dev.jasper.app.commands.KeyContext;
import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.GlassSettings;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.ScopeRegistry;
import dev.jasper.app.palette.CommandsScope;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.config.ToolbarMode;
import dev.jasper.app.config.ViewMode;


import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import dev.jasper.app.terminals.TerminalEvent;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.terminals.SessionRequest;

/** Real window contents, independent of native JFrame construction for headless testing. */
public final class WindowContent extends JPanel implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(WindowContent.class.getName());
    private final ShellLauncher launcher;
    private final Consumer<Path> newWindow;
    private final Runnable quit;
    private final Runnable onEmpty;
    private final java.util.Map<Component, dev.jasper.app.contributions.ContentEntry> hosted = new IdentityHashMap<>();
    private dev.jasper.app.contributions.ContentRegistry contentRegistry;
    private Subscription contentHost;
    private Runnable bringToFront = () -> {};
    private final boolean detached;
    private final JTabbedPane tabs = new TerminalDeck();
    private final WindowTabs windowTabs;
    private final WorkspaceActions workspaceActions;
    private final CommandRegistry commands = new CommandRegistry();
    private final WindowCommands windowCommands;
    private final ScopeRegistry scopes = new ScopeRegistry();
    private final CommandsScope commandsScope;
    private final boolean macOs;
    private final WindowCommandPalette commandPalette;
    // EDT-owned roster gives a held sequence priority over every window's new shortcuts,
    // regardless of the order in which KeyboardFocusManager registered their dispatchers.
    private static final java.util.List<WindowContent> PALETTE_KEY_OWNERS = new ArrayList<>();
    private final PaletteKeyRouter paletteKeys;
    private final KeyEventDispatcher paletteDispatcher = this::paletteKeysDispatch;
    private KeyboardFocusManager paletteFocusManager;
    private javax.swing.Timer paletteTailCleanup;
    // The tail of a focus-traversal key that ran a shortcut: its typed and released events must not traverse either.
    private final java.util.Set<Integer> traversalHeld = new java.util.HashSet<>();
    private boolean traversalTyped;
    private ToolbarMode toolbarMode = ToolbarMode.ICONS_AND_LABELS;
    private ViewMode viewMode = ViewMode.NORMAL;
    private boolean zenMode;
    private boolean statusVisible = true;
    private KeyBindings bindings;
    private final UUID id = UUID.randomUUID();
    private KeyBindings baseBindings;
    // One root binding per stroke; dispatch picks the action for where focus is.
    private final java.util.Map<KeyStroke, String> rootKeys = new java.util.HashMap<>();
    private WindowContributions contributed;
    /** Where this window's tool windows dock: its contributions change it, its area lays it out. */
    private final DockState dock = new DockState();
    private final ToolWindowArea area;
    private final ToolStripe leftStripe = new ToolStripe(Side.LEFT), rightStripe = new ToolStripe(Side.RIGHT);
    /** Drags a tool window from its stripe button or its header to another slot. */
    private final DockDrag dockDrag;
    private UiState uiState = UiState.inMemory();
    private boolean railVisible = true;
    /** False once {@link #forgetLayout} ran: closing then saves nothing. */
    private boolean rememberLayout = true;
    private final WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration(this);
    private final WindowChrome chrome;
    private final ThemeController themes;
    private final Subscription themeRegistration;
    private JRootPane bindingRoot;
    private JComponent cornerLayer;
    private JRootPane cornerRoot;
    private final ComponentAdapter cornerRelayout = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { layoutCornerLayer(); }
        @Override public void componentMoved(ComponentEvent event) { layoutCornerLayer(); }
    };
    private final java.beans.PropertyChangeListener cornerResized = event -> layoutCornerLayer();
    private final Map<KeyStroke, String> contentBindings = new HashMap<>();
    private final Map<Component, dev.jasper.app.lifecycle.Subscription> contentListeners = new IdentityHashMap<>();
    Runnable onMinimumSizeChanged = () -> {};
    static final int DEFAULT_TAB_HEIGHT = 30;
    static final int MIN_TAB_HEIGHT = 20;
    static final int MAX_TAB_HEIGHT = 72;
    private int tabHeight = DEFAULT_TAB_HEIGHT;
    Runnable onTabHeightChanged = () -> {};
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;
    private int toolbarHeight = ConfigSnapshot.DEFAULT_TOOLBAR_HEIGHT;
    private int statusBarHeight = ConfigSnapshot.DEFAULT_STATUS_BAR_HEIGHT;
    private boolean closed;
    // Kept as given, so callers read back exactly what was applied; terminal components take it as a float.
    private double backgroundOpacity = 1;
    private Runnable glassListener = () -> {};
    private BiConsumer<GlassLevels, Boolean> glassRequests = (levels, dark) -> {};
    private boolean rearranging;
    private WindowTerminals terminals;
    private boolean active = true;
    Consumer<ResolvedTheme> onThemeChanged = theme -> {};
    private Runnable openSettings, reloadConfiguration, migrateConfiguration;
    private java.util.function.Function<dev.jasper.app.config.SettingsEdit, java.util.concurrent.CompletableFuture<?>> saveSettings;
    private Consumer<String> openSettingsAt;
    private ConfigService.State configurationState;
    static final String REJECTED_CHOICE = "config.toml has errors, so changes can't be saved until they're fixed.";
    private Runnable unregisterConfiguration = () -> {};
    private java.util.function.Consumer<TerminalPane> firstTerminal = pane -> { };
    private boolean firstTerminalSeen;
    Consumer<JComponent> showConfigDiagnostics = control -> JOptionPane.showMessageDialog(
        this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);
    Consumer<String> onTitle = title -> {};
    /** An error to tell the user about, as a title and details; the application posts it as a notification. */
    public java.util.function.BiConsumer<String, String> onError = WindowContent::unheard;
    /** Information to tell the user, such as the result of Migrate Config; the application posts it as a notification. */
    public java.util.function.BiConsumer<String, String> onInfo = WindowContent::unheard;
    /**
     * A shell could not start in this window: the directory and cause to show, and what was thrown. By default an
     * error with a fixed title; the application records an incident instead.
     */
    public java.util.function.BiConsumer<String, Throwable> onLaunchFailure =
        (details, cause) -> onError.accept("Could not start the terminal", details);
    /** Application-owned buddy toggle; the window only forwards and displays state. */
    public Runnable onToggleBuddy = () -> {};
    public java.util.function.BooleanSupplier buddyEnabled = () -> false;
    private final java.util.List<Consumer<WorkspaceActivity.Event>> activityListeners = new ArrayList<>();
    /** Whether any Jasper window has focus; application-supplied, EDT-only. */
    public java.util.function.BooleanSupplier anyWindowActive = () -> true;

    /** Replays existing producers before returning; the listener receives later events until closed. */
    public Subscription activity(Consumer<WorkspaceActivity.Event> listener) {
        Objects.requireNonNull(listener);
        if (closed) return new Subscription(() -> {});
        activityListeners.add(listener);
        try {
            for (TerminalTab tab : terminalTabs())
                for (TerminalPane pane : tab.panes())
                    listener.accept(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        } catch (RuntimeException | Error failure) { activityListeners.remove(listener); throw failure; }
        return new Subscription(() -> activityListeners.remove(listener));
    }

    /**
     * Called once, on the EDT, when this window's first terminal is configured: start-up timing. Register it before the
     * window's first terminal is configured; a later registration never fires.
     */
    public void onFirstTerminal(java.util.function.Consumer<TerminalPane> action) {
        firstTerminal = java.util.Objects.requireNonNull(action, "action");
    }

    private void emit(WorkspaceActivity.Event event) {
        for (var listener : java.util.List.copyOf(activityListeners)) listener.accept(event);
    }


    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty) {
        this(launcher, directory, newWindow, quit, onEmpty, new ThemeController(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes) {
        this(launcher, directory, newWindow, quit, onEmpty, themes,
            KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac")));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings) {
        this(launcher, directory, newWindow, quit, onEmpty, themes, bindings,
            new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, directory == null, newWindow, quit, onEmpty, themes, bindings, history, macOs);
    }

    /**
     * A {@code detached} window starts with no tab and holds detached content; any other starts one tab in
     * {@code directory}, where null means where a new shell starts. {@code newWindow} receives null the same way.
     */
    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        super(new BorderLayout());
        this.detached = detached;
        setFocusTraversalPolicyProvider(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override public Component getDefaultComponent(Container root) {
                TerminalPane pane = currentPane();
                if (closed) return null;
                if (selectedContent() != null) return selectedContent().component();
                if (pane == null) return null;
                return pane.view() == null ? pane : pane.view();
            }
        });
        this.baseBindings = bindings;
        this.bindings = bindings;
        this.macOs = macOs;
        this.themes = themes;
        this.launcher = launcher; this.newWindow = newWindow; this.quit = quit; this.onEmpty = onEmpty;
        workspaceActions = new WorkspaceActions(this);
        windowCommands = new WindowCommands(this, commands);
        chrome = new WindowChrome(this);
        commandsScope = new CommandsScope(commands, history, macOs, this::dispatchCommand);
        scopes.register(commandsScope);
        commandPalette = new WindowCommandPalette(this, scopes, history, PaletteScope.ALL_ID, macOs,
            themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));
        paletteKeys = new PaletteKeyRouter(commandPalette.controller(), commandPalette::open, () -> this.bindings, macOs,
            this::ownsKeySource);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) syncPaletteDispatcher();
        });
        windowTabs = new WindowTabs(this);
        // The tabs belong to the terminal column, so the stripes and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(windowTabs, BorderLayout.NORTH); terminalColumn.add(tabs, BorderLayout.CENTER);
        area = new ToolWindowArea(terminalColumn, dock);
        area.setStatusVisible(chrome.status().isVisible());
        area.setHeaderVisible(true);
        leftStripe.setVisible(false);
        rightStripe.setVisible(false);

        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        dockDrag = new DockDrag(this, dock, area, leftStripe, rightStripe);
        dockDrag.railsEnabled = this::railsEnabled;
        leftStripe.dragSource = dockDrag::install;
        rightStripe.dragSource = dockDrag::install;
        area.dragSource = dockDrag::install;
        area.releaseDragSource = dockDrag::uninstall;
        // A dock change moves the window's minimum size; read the hook when it fires, as the frame sets it later.
        area.onMinimumSizeChanged = () -> onMinimumSizeChanged.run();
        add(chrome.header(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH);
        tabs.addChangeListener(event -> {
            if (!rearranging) {
                if (terminals != null) terminals.tabSelected();
                if (commandPalette != null) commandPalette.dismiss();
                update();
                focusSelected();
            }
        });
        themeRegistration = themes.subscribe(this::applyTheme);
        if (!detached) newTab(directory);
        else setPreferredSize(new Dimension(900, 650));
    }

    private final JPanel body = new JPanel(new BorderLayout());
    private dev.jasper.app.platform.WindowBody bodyLease;

    java.awt.Rectangle bodyRegion() {
        var region = javax.swing.SwingUtilities.convertRectangle(body.getParent(), body.getBounds(), this);
        var status = chrome.status();
        if (status.isVisible() && status.getWidth() > 0 && status.getHeight() > 0)
            region = region.union(javax.swing.SwingUtilities.convertRectangle(status.getParent(), status.getBounds(), this));
        return region;
    }
    @Override public void doLayout() {
        super.doLayout();
        if (bodyLease != null) bodyLease.changed();
    }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        MacTitleBar bar;
        if (content.viewMode == ViewMode.COMPACT) {
            root.setContentPane(content);
            bar = null;
        } else {
            bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), supported);
        }
        if (bar != null) {
            content.remove(content.chrome.header());
            bar.setHeader(content.chrome.header(), java.util.List.of(content.chrome.header(), content.toolbar()));
        }
        content.onTitle = value -> {
            TerminalPane pane = content.currentPane();
            var document = content.selectedContent();
            var context = WindowHeaderContext.from(pane == null ? null : pane.snapshot(),
                Path.of(System.getProperty("user.home")), pane == null ? "" : pane.programTitle(),
                pane == null ? "" : pane.commandLabel());
            if (document != null) context = new WindowHeaderContext(document.title() + (document.host().isEmpty() ? "" : " — " + document.host()), document.location(), document.host().isEmpty() ? "FILE" : "SERVER");
            nativeTitle.accept(context.label());
            content.chrome.search().setContext(context.label(), context.tooltip(), dev.jasper.app.platform.AppIcons.named(context.icon()));
        };
        if (bar != null) {
            content.onThemeChanged = theme -> bar.setLight(!theme.chrome().dark());
            bar.setLight(!content.theme().chrome().dark());
        }
        if (content.viewMode == ViewMode.COMPACT && content.macOs) {
            content.onThemeChanged = theme -> root.putClientProperty("apple.awt.windowAppearance",
                theme.chrome().dark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
            content.onThemeChanged.accept(content.theme());
        }
        if (content.bodyLease != null) content.bodyLease.close();
        content.bodyLease = dev.jasper.app.platform.WindowBody.install(root, content, content::bodyRegion);
        content.update();
        return bar;
    }

    /**
     * Windows and Linux custom title bar: the header joins the menus in FlatLaf's title pane, which embeds the menu bar.
     * The title pane shows no title text (the field shows the context) and follows the toolbar height and chrome colours.
     * Returns true when {@code root} already has look-and-feel decorations. Otherwise it changes nothing yet and returns
     * false, installing once decorations arrive: on Windows 10 and 11 FlatLaf decorates a frame only when its native peer
     * is created (on {@code pack} or first show), so a new frame's root pane still reads {@code NONE} here.
     */
    static boolean installCustomTitleBar(JRootPane root, WindowContent content) {
        if (content.viewMode == ViewMode.COMPACT) return false;
        if (root.getWindowDecorationStyle() != JRootPane.NONE) {
            decorateTitleBar(root, content);
            return true;
        }
        root.addPropertyChangeListener("windowDecorationStyle", new java.beans.PropertyChangeListener() {
            @Override public void propertyChange(java.beans.PropertyChangeEvent event) {
                if (root.getWindowDecorationStyle() == JRootPane.NONE) return;
                root.removePropertyChangeListener("windowDecorationStyle", this);
                decorateTitleBar(root, content);
            }
        });
        return false;
    }

    private static void decorateTitleBar(JRootPane root, WindowContent content) {
        WindowHeader header = content.chrome.header();
        content.remove(header);
        content.chrome.menuBar().setHeader(header);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, content.toolbarHeight());
        header.addPropertyChangeListener("barHeight",
            event -> root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, event.getNewValue()));
        Runnable colors = () -> {
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(null, SemanticColor.SURFACE_APP));
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
        };
        colors.run();
        content.onThemeChanged = theme -> colors.run();
        content.revalidate();
    }

    void installRootBindings(JRootPane root) {
        removeRootBindings();
        bindingRoot = root;
        bindings.strokes().forEach((actionId, stroke) -> {
            if (rootKeys.putIfAbsent(stroke, actionId) != null) return;
            root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, actionId);
            root.getActionMap().put(actionId, new RootShortcut(stroke));
        });
        syncContentBindings();
        commandPalette.install(root);
        installCornerLayer(root);
        syncPaletteDispatcher();
    }

    /** The root input map's action for one stroke: it takes the key only when dispatch would use it. */
    private final class RootShortcut extends AbstractAction {
        private final KeyStroke stroke;
        RootShortcut(KeyStroke stroke) { this.stroke = stroke; }
        @Override public boolean accept(Object sender) { return isEnabled() && claimsShortcut(stroke, focusOwner()); }
        @Override public void actionPerformed(ActionEvent event) { dispatchShortcut(stroke, focusOwner()); }
    }

    private static Component focusOwner() { return KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(); }

    private boolean paletteKeysDispatch(KeyEvent event) {
        if (dev.jasper.app.platform.WindowInput.blocked(event.getComponent())) return false;
        boolean tail = false;
        for (WindowContent owner : java.util.List.copyOf(PALETTE_KEY_OWNERS)) {
            tail |= owner.paletteKeys.dispatchTail(event);
            if (owner.paletteKeys.drained() && (owner.closed || !owner.paletteAttached()))
                owner.removePaletteDispatcher();
        }
        return tail || paletteKeys.dispatch(event) || traversalShortcut(event);
    }

    /** Whether a key event's component is inside this window while it is open, active and attached to its root. */
    private boolean ownsKeySource(Component source) {
        return !closed && active && bindingRoot != null && source != null
            && SwingUtilities.isDescendingFrom(this, bindingRoot)
            && SwingUtilities.isDescendingFrom(source, bindingRoot);
    }

    /**
     * Runs a root-bound shortcut whose key is also one of the focus owner's focus-traversal keys before focus traversal
     * takes it, such as Linux Ctrl+Tab and Ctrl+Shift+Tab in a text area, a table or a field, and swallows that key's
     * typed and released tail wherever focus went. Plain Tab is no Keymap binding, so it still traverses.
     */
    private boolean traversalShortcut(KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_RELEASED -> { if (!traversalHeld.remove(event.getKeyCode())) return false; }
            case KeyEvent.KEY_TYPED -> { if (!traversalTyped) return false; traversalTyped = false; }
            case KeyEvent.KEY_PRESSED -> {
                traversalTyped = false;
                Component focus = event.getComponent();
                KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
                if (!ownsKeySource(focus) || !traversalKey(focus, event)
                    || !(rootKeys.containsKey(stroke) || contentBindings.containsKey(stroke))
                    || !claimsShortcut(stroke, focus)) return false;
                traversalHeld.add(event.getKeyCode()); traversalTyped = true;
                event.consume();
                dispatchShortcut(stroke, focus);
                return true;
            }
            default -> { return false; }
        }
        event.consume();
        return true;
    }

    private static boolean traversalKey(Component focus, KeyEvent event) {
        if (!focus.getFocusTraversalKeysEnabled()) return false;
        AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(event);
        return focus.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS).contains(stroke)
            || focus.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS).contains(stroke);
    }

    private boolean paletteAttached() {
        return bindingRoot != null && SwingUtilities.isDescendingFrom(this, bindingRoot);
    }

    private void syncPaletteDispatcher() {
        if (!closed && paletteAttached()) {
            if (paletteTailCleanup != null) { paletteTailCleanup.stop(); paletteTailCleanup = null; }
            if (paletteFocusManager == null) {
                paletteFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                PALETTE_KEY_OWNERS.add(this);
                paletteFocusManager.addKeyEventDispatcher(paletteDispatcher);
            }
        } else {
            if (!closed) commandPalette.dismiss();
            if (paletteKeys.drained()) removePaletteDispatcher();
            else if (paletteTailCleanup == null) {
                paletteTailCleanup = new javax.swing.Timer(2000, event -> {
                    paletteKeys.reset(); removePaletteDispatcher();
                });
                paletteTailCleanup.setRepeats(false); paletteTailCleanup.start();
            }
        }
    }

    private void removePaletteDispatcher() {
        if (paletteTailCleanup != null) { paletteTailCleanup.stop(); paletteTailCleanup = null; }
        if (paletteFocusManager != null) {
            paletteFocusManager.removeKeyEventDispatcher(paletteDispatcher); paletteFocusManager = null;
            PALETTE_KEY_OWNERS.remove(this);
        }
    }

    private void syncContentBindings() {
        if (bindingRoot == null) return;
        var input = bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW);
        contentBindings.forEach((stroke, key) -> {
            input.remove(stroke); bindingRoot.getActionMap().remove(key);
            String rootKey = rootKeys.get(stroke);
            if (rootKey != null) input.put(stroke, rootKey);
        });
        contentBindings.clear();
        var entry = selectedContent();
        if (entry == null) return;
        for (var command : entry.commands()) {
            var stroke = command.shortcut();
            if (stroke == null) continue;
            String key = "hosted:" + command.id();
            contentBindings.put(stroke, key); input.put(stroke, key);
            bindingRoot.getActionMap().put(key, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    dispatchShortcut(stroke, KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                }
            });
        }
    }

    private void removeRootBindings() {
        if (bindingRoot == null) return;
        contentBindings.forEach((stroke, key) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(key);
        });
        contentBindings.clear();
        rootKeys.forEach((stroke, actionId) -> {
            bindingRoot.getInputMap(WHEN_IN_FOCUSED_WINDOW).remove(stroke);
            bindingRoot.getActionMap().remove(actionId);
        });
        rootKeys.clear();
        bindingRoot = null;
    }

    /**
     * The application's notification card layer: kept flush with the bottom-right corner of the tool window area
     * (between the stripes, above the status bar, over any docked tool window) on the root's palette layer, at its
     * preferred size, under a dock drag's guides. Once per window; removed at close.
     */
    public void mountCornerLayer(JComponent layer) {
        if (closed || cornerLayer != null) return;
        cornerLayer = Objects.requireNonNull(layer, "layer");
        layer.addPropertyChangeListener("preferredSize", cornerResized);
        if (bindingRoot != null) installCornerLayer(bindingRoot);
    }

    private void installCornerLayer(JRootPane root) {
        if (cornerLayer == null || cornerRoot == root) return;
        uninstallCornerLayer();
        cornerRoot = root;
        root.getLayeredPane().add(cornerLayer, JLayeredPane.PALETTE_LAYER);
        root.getLayeredPane().addComponentListener(cornerRelayout);
        area.addComponentListener(cornerRelayout);
        layoutCornerLayer();
    }

    private void uninstallCornerLayer() {
        if (cornerRoot == null) return;
        cornerRoot.getLayeredPane().removeComponentListener(cornerRelayout);
        area.removeComponentListener(cornerRelayout);
        cornerRoot.getLayeredPane().remove(cornerLayer);
        cornerRoot.repaint();
        cornerRoot = null;
    }

    void layoutCornerLayer() {
        if (cornerRoot == null || cornerLayer == null) return;
        JLayeredPane pane = cornerRoot.getLayeredPane();
        if (!SwingUtilities.isDescendingFrom(area, pane)) { cornerLayer.setBounds(0, 0, 0, 0); return; }
        Rectangle bounds = SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), pane);
        Dimension size = cornerLayer.getPreferredSize();
        int width = Math.min(size.width, bounds.width), height = Math.min(size.height, bounds.height);
        cornerLayer.setBounds(bounds.x + bounds.width - width, bounds.y + bounds.height - height, width, height);
        cornerLayer.revalidate();
        cornerLayer.repaint();
    }

    void setBindings(KeyBindings replacement) {
        baseBindings = Objects.requireNonNull(replacement);
        rebind();
    }

    /** Effective bindings are the saved ones plus contributed actions in registration order. */
    void rebind() {
        JRootPane root = bindingRoot;
        removeRootBindings();
        bindings = contributed == null ? baseBindings : baseBindings.withExtensions(contributed.extensions()).bindings();
        for (ActionId id : ActionId.values())
            action(id).putValue(Action.ACCELERATOR_KEY, bindings.strokeFor(id).orElse(null));
        if (contributed != null) contributed.applyAccelerators();
        if (root != null) installRootBindings(root);
        toolbar().revalidate(); toolbar().repaint();
        refreshTabs();
    }

    public void connectConfiguration(Runnable settings, Runnable reload, Runnable unregister) {
        connectConfiguration(settings, reload, null, unregister);
    }

    /** As the three-argument form, plus Migrate Config for the diagnostics dialog; {@code migrate} may be null. */
    public void connectConfiguration(Runnable settings, Runnable reload, Runnable migrate, Runnable unregister) {
        if (closed) return;
        openSettings = settings; reloadConfiguration = reload; migrateConfiguration = migrate; unregisterConfiguration = unregister;
        action(ActionId.OPEN_SETTINGS).putValue(Action.SHORT_DESCRIPTION, "Open Settings");
        action(ActionId.RELOAD_CONFIG).putValue(Action.SHORT_DESCRIPTION, "Reload configuration file");
        updateActions();
    }

    /** How View choices save, and how Window Settings\u2026 opens a Settings page; connected with the configuration. */
    public void connectSettings(java.util.function.Function<dev.jasper.app.config.SettingsEdit, java.util.concurrent.CompletableFuture<?>> save,
                                Consumer<String> openAt) {
        if (closed) return;
        saveSettings = java.util.Objects.requireNonNull(save);
        openSettingsAt = java.util.Objects.requireNonNull(openAt);
        updateActions();
    }

    void openThemesSettings() { if (openSettingsAt != null) openSettingsAt.accept("appearance"); }
    public void disconnectConfiguration() {
        openSettings = null; reloadConfiguration = null; migrateConfiguration = null; unregisterConfiguration = () -> {};
        saveSettings = null; openSettingsAt = null; configurationState = null;
        status().onConfigurationDetails = () -> {};
        status().configButton().setEnabled(false);
        updateActions();
    }

    public void setConfigurationState(ConfigService.State state) {
        if (closed) return;
        configurationState = state;
        status().setConfiguration(state);
        status().onConfigurationDetails = () -> {
            String details = state.file() + "\n\n" + (state.diagnostics().isEmpty()
                ? (state.present() ? "Configuration loaded successfully." : "Using built-in defaults. Open Settings to create this file.")
                : String.join("\n", state.diagnostics().stream().map(ConfigDiagnostic::formatted).toList()));
            JTextArea text = new JTextArea(details, 12, 64);
            dev.jasper.ui.swing.NativeControlStyles.textArea(text, () -> ThemeZone.theme(text));
            text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName("Configuration details");
            JScrollPane scroll = new JScrollPane(text);
            Runnable migrate = migrateConfiguration;
            if (!state.migratable() || migrate == null) { showConfigDiagnostics.accept(scroll); return; }
            var button = new JButton("Migrate Config\u2026");
            dev.jasper.ui.swing.NativeControlStyles.framedButton(button, () -> ThemeZone.theme(button));
            button.setName("configuration.migrate");
            button.setToolTipText("Rewrite old keys in this file to the current layout; the original is backed up first.");
            button.addActionListener(event -> {
                Window dialog = SwingUtilities.getWindowAncestor(button);
                if (dialog != null) dialog.dispose();
                migrate.run();
            });
            var actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
            actions.setOpaque(false);
            actions.add(button);
            var panel = new JPanel(new BorderLayout(0, 8));
            panel.setOpaque(false);
            panel.add(scroll, BorderLayout.CENTER);
            panel.add(actions, BorderLayout.SOUTH);
            showConfigDiagnostics.accept(panel);
        };
    }

    /** Opens the status bar's configuration details, as clicking its configuration button does. */
    public void showConfigurationDetails() {
        if (!closed) status().onConfigurationDetails.run();
    }

    /** A message before the application connected a receiver: logged by its fixed title, never shown in a dialog. */
    private static void unheard(String title, String details) {
        LOG.log(System.Logger.Level.WARNING, "A window message had no receiver: " + title);
    }

    public void applyConfiguration(ConfigSnapshot next, boolean macOs) { workspaceConfiguration.apply(next, macOs); }
    float configuredFontSize() { return workspaceConfiguration.configuredFontSize(); }
    boolean closed() { return closed; }
    boolean configurationConnected() { return openSettings != null && reloadConfiguration != null; }
    void openSettings() { openSettings.run(); }
    boolean settingsConnected() { return openSettingsAt != null; }
    void openWindowSettings() { if (openSettingsAt != null) openSettingsAt.accept("window"); }
    void reloadConfiguration() { reloadConfiguration.run(); }
    void requestNewWindow() { newWindow.accept(directory()); }
    void requestQuit() { quit.run(); }

    CommandRegistry commands() { return commands; }
    ScopeRegistry scopes() { return scopes; }
    CommandsScope commandsScope() { return commandsScope; }
    void dispatchCommand(Command command) {
        command.action().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command.id()));
    }
    /** The shortcut that opens the palette, shown in the title search field. */
    String paletteShortcut() {
        return CommandsScope.shortcutText(action(ActionId.COMMAND_PALETTE).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    WindowCommandPalette commandPalette() { return commandPalette; }
    /** The title field's bounds in {@code target}'s coordinates while the palette can drop from it; null otherwise. */
    java.awt.Rectangle paletteAnchor(Component target) {
        if (viewMode == ViewMode.COMPACT) return null;
        TitleSearchField field = chrome.search();
        if (!field.isVisible() || field.collapsed() || field.getWidth() <= 0 || field.getParent() == null) return null;
        if (SwingUtilities.getRootPane(field) != SwingUtilities.getRootPane(target)) return null;
        return SwingUtilities.convertRectangle(field.getParent(), field.getBounds(), target);
    }
    /** The title row inside the insets that keep the header clear of the window controls, in {@code target}'s coordinates. */
    java.awt.Rectangle titleRowClear(Component target) {
        WindowHeader header = chrome.header();
        java.awt.Insets insets = header.getInsets();
        var row = new java.awt.Rectangle(insets.left, 0, Math.max(0, header.getWidth() - insets.left - insets.right), header.getHeight());
        return SwingUtilities.convertRectangle(header, row, target);
    }
    WindowChrome chrome() { return chrome; }
    UiState uiState() { return uiState; }
    WindowCommands windowCommands() { return windowCommands; }
    ToolbarMode toolbarMode() { return toolbarMode; }
    boolean statusVisible() { return statusVisible; }
    boolean zenMode() { return zenMode; }

    /** Saves this window's mode; the saved dock layout keeps the windows that showed before Zen. */
    void toggleZenMode() {
        if (closed) return;
        setZenMode(!zenMode());
        uiState.setZenMode(zenMode());
        uiState.save();
    }

    private void setZenMode(boolean enabled) {
        if (closed || zenMode == enabled) return;
        dockDrag.cancel();
        zenMode = enabled;
        area.cancelResize();
        refreshChromeVisibility();
        syncRailVisibility();
        if (contributed != null) contributed.zenChanged();
        if (zenMode()) focusSelected();
        updateActions();
    }
    ViewMode viewMode() { return viewMode; }

    /** Called only for the first configuration application, before any native chrome is installed. */
    void initializeViewMode(ViewMode mode) {
        viewMode = mode;
        if (mode == ViewMode.COMPACT) {
            remove(chrome.header());
            area.setHeaderVisible(false);
        }
        chrome.setToolbarMode(toolbarMode);

    }
    public boolean isActiveAndOpen() { return active && !closed; }

    /** Stable identity of this window for extensions; never a Swing object. */
    public UUID id() { return id; }

    /** Connects with layout state that lives only as long as this window. */
    /** Connects this window to the application-wide terminal registry, once. {@code toFront} raises the native window. */
    public void connectTerminals(TerminalRegistry registry, Runnable toFront) {
        if (closed || terminals != null) return;
        bringToFront = toFront;
        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
    }

    java.util.List<TerminalTab> terminalTabs() {
        var result = new ArrayList<TerminalTab>();
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) instanceof TerminalTab tab) result.add(tab);
        return result;
    }

    public void connectContributions(Contributions model) { connectContributions(model, UiState.inMemory()); }

    /**
     * Connects this window to the application-wide contributions model, once, remembering panel
     * placement and rail visibility in {@code state}.
     */
    public void connectContributions(Contributions model, UiState state) { connectContributions(model, state, true); }

    /**
     * Closing this window will not save its dock layout or stripe visibility: it is being discarded (it failed to
     * compose, or it is the warm-up rehearsal's), not closed by the user, so it never becomes the last window closed,
     * whose layout the next window restores. EDT.
     */
    public void forgetLayout() { rememberLayout = false; }

    /**
     * As {@link #connectContributions(Contributions, UiState)}. A window that does not {@code hostsContent} is wired to
     * the model but never registered as a place where plugin content opens, so the content registry cannot choose it as
     * the main window: the warm-up rehearsal is such a window, and content opened in it would vanish when it closes.
     */
    public void connectContributions(Contributions model, UiState state, boolean hostsContent) {
        if (closed || contributed != null) return;
        uiState = Objects.requireNonNull(state);
        railVisible = state.railVisible();
        // Restore before contributions can build or show saved panels.
        setZenMode(state.zenMode());
        contributed = new WindowContributions(this, Objects.requireNonNull(model), state);
        if (hostsContent) {
            contentRegistry = model.content();
            contentHost = contentRegistry.addHost(new dev.jasper.app.contributions.ContentHost(id, detached,
                this::mountContent, this::unmountContent, entry -> selectTab(entry.component()), () -> bringToFront.run()));
        }
        rebind();
    }

    private void mountContent(dev.jasper.app.contributions.ContentEntry entry) {
        JComponent component = entry.component();
        try {
            WorkspaceTheme.apply(component, themes.current());
            hosted.put(component, entry);
            contentListeners.put(component, entry.onChanged(this::update));
            tabs.addTab(entry.title(), component);
            selectTab(component); update();
        } catch (RuntimeException | Error failure) {
            var listener = contentListeners.remove(component);
            if (listener != null) listener.close();
            hosted.remove(component);
            tabs.remove(component);
            WorkspaceTheme.clear(component);
            throw failure;
        }
        // After the mount stands: a failing glass handler must not roll it back.
        if (backgroundOpacity < 1) glassListener.run();
    }
    private void unmountContent(dev.jasper.app.contributions.ContentEntry entry) {
        if (!hosted.containsKey(entry.component())) return;
        var listener = contentListeners.remove(entry.component());
        if (listener != null) listener.close();
        hosted.remove(entry.component()); tabs.remove(entry.component());
        WorkspaceTheme.clear(entry.component()); update();
        if (backgroundOpacity < 1) glassListener.run();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }
    public dev.jasper.app.contributions.ContentEntry selectedContent() { return hosted.get(tabs.getSelectedComponent()); }
    Component selectedComponent() { return tabs.getSelectedComponent(); }
    String tabTitle(Component component) {
        var entry = hosted.get(component);
        return entry == null ? ((TerminalTab) component).title() : (entry.dirty() ? "\u25cf " : "") + entry.title() + (entry.detail().isEmpty() ? "" : "  " + entry.detail());
    }
    Icon tabIcon(Component component) { var entry = hosted.get(component); return entry == null ? ((TerminalTab)component).icon() : entry.icon(); }
    String tabTooltip(Component component) { var entry = hosted.get(component); return entry == null ? tabTitle(component) : entry.location(); }
    JPopupMenu tabMenu(Component component) {
        var menu = new JPopupMenu(); var entry = hosted.get(component);
        if (entry != null) {
            var move = new JMenuItem(entry.detached() ? "Move to Main Window" : "Open in Separate Window");
            move.addActionListener(event -> entry.move(!entry.detached())); menu.add(move);
            for (var command : entry.commands()) { var item = new JMenuItem(command.title()); item.putClientProperty("html.disable", true);
                item.setEnabled(command.enabled()); item.setAccelerator(command.shortcut()); item.addActionListener(event -> command.run().run()); menu.add(item); }
        }
        var close = new JMenuItem("Close Tab"); close.setAccelerator(bindings().strokeFor(ActionId.CLOSE_TAB).orElse(null)); close.addActionListener(event -> closeTab(component)); menu.add(close); dev.jasper.ui.swing.NativeControlStyles.menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme); return menu;
    }
    public void focusSelected() { var entry = selectedContent(); if (entry != null) entry.focus(); else if (currentTab() != null) currentTab().focusTerminal(); }
    public boolean mayClose() { return contentRegistry == null || contentRegistry.mayClose(() -> contentRegistry.inWindow(id)); }

    ToolWindowArea area() { return area; }
    DockState dock() { return dock; }
    ToolStripe leftStripe() { return leftStripe; }
    ToolStripe rightStripe() { return rightStripe; }
    DockDrag dockDrag() { return dockDrag; }
    boolean railVisible() { return railVisible; }

    void setRailVisible(boolean value) {
        // A drag shows its view on the stripes that showed when it began; showing or hiding them ends it.
        dockDrag.cancel();
        railVisible = value;
        uiState.setRailVisible(value);
        uiState.save();
        syncRailVisibility();
        updateActions();
    }

    /**
     * Each stripe shows while it has a tool window or an action, unless View > Rail or Zen hides the rails. An empty
     * stripe stays hidden; a dock drag still drops onto it at its edge ({@link DockDrag#railsEnabled}), and its guides
     * predict this rule for the layout after the drop.
     */
    void syncRailVisibility() {
        boolean left = railsEnabled() && !leftStripe.empty(), right = railsEnabled() && !rightStripe.empty();
        area.setStripesVisible(left, right);
        if (leftStripe.isVisible() != left || rightStripe.isVisible() != right) {
            leftStripe.setVisible(left);
            rightStripe.setVisible(right);
            revalidate(); repaint();
            // The stripes' own width, and the area's lower inset, are part of the window's minimum size.
            onMinimumSizeChanged.run();
        }
    }
    /** Whether the stripes may show: neither View > Rail nor Zen hides them. */
    boolean railsEnabled() { return !zenMode() && railVisible; }
    boolean updatingActions() { return workspaceActions.updating(); }
    Action action(ActionId id) { return workspaceActions.action(id); }
    KeyBindings bindings() { return bindings; }
    JToolBar toolbar() { return chrome.toolbar(); }
    WindowStatusBar status() { return chrome.status(); }
    JMenuBar menuBar() { return chrome.menuBar(); }
    JTabbedPane tabStrip() { return tabs; }
    WindowTabs windowTabs() { return windowTabs; }
    private void refreshTabs() { windowTabs.refresh(); }

    public TerminalTab currentTab() { return tabs.getSelectedComponent() instanceof TerminalTab tab ? tab : null; }
    public TerminalPane currentPane() { return currentTab() == null ? null : currentTab().focusedPane(); }
    /**
     * Where a tab or window opened from here starts: the current pane's {@link TerminalPane#inheritableDirectory};
     * null, meaning where a new shell starts, when there is no pane or it has no directory of its own.
     */
    Path directory() { return currentPane() == null ? null : currentPane().inheritableDirectory(); }

    /** A tab whose shell starts in {@code directory}; null means where a new shell starts (the user's home). */
    public void newTab(Path directory) { openTab(directory); }

    TerminalTab openTab(Path directory) { return openTab(directory, null); }

    /** {@code requestOrNull} makes the tab's first pane a provided session instead of a local shell. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull) { return openTab(directory, requestOrNull, Optional.empty()); }

    /** As above; {@code profile} names a local first pane's shell profile, empty for the user's default. */
    TerminalTab openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        if (closed) return null;
        TerminalTab tab = new TerminalTab(directory, launcher, requestOrNull, profile);
        if (terminals != null) terminals.tabOpened(tab);
        tab.onChanged = () -> {
            for (TerminalPane pane : tab.panes()) if (pane.view() == null) pane.applyTheme(themes.current());
            update();
        };
        tab.onEmpty = () -> closeTab(tab);
        tab.onError = (message, cause) -> onLaunchFailure.accept(message, cause);
        tab.lastInWindow = () -> tabs.getTabCount() == 1;
        tab.configure = pane -> configurePane(tab, pane);
        tab.onPaneCreated = pane -> connectActivity(tab, pane);
        for (TerminalPane pane : tab.panes()) connectActivity(tab, pane);
        WorkspaceTheme.apply(tab, themes.current());
        tab.setBackground(themes.current().surface());
        tab.setBackgroundOpacity((float) backgroundOpacity);
        for (TerminalPane pane : tab.panes()) pane.applyTheme(themes.current());
        tabs.addTab(tab.title(), tab);
        tabs.setSelectedComponent(tab); update(); tab.start();
        return tab;
    }

    private void connectActivity(TerminalTab tab, TerminalPane pane) {
        emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.OPENED));
        report(new TerminalEvent.PaneOpened(tab.id(), pane.id()));
        pane.onCommandStarted = command -> {
            long startedAt = System.nanoTime();
            emit(new WorkspaceActivity.Started(pane.id(), command, () -> System.nanoTime() - startedAt,
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); },
                pane.watched() && isActiveAndOpen() && tab == currentTab()));
            report(new TerminalEvent.CommandStarted(pane.id(), command));
        };
        pane.onTitleChanged = title -> {
            emit(new WorkspaceActivity.TitleChanged(pane.id(), title));
            report(new TerminalEvent.TitleChanged(pane.id(), title));
        };
        pane.onClosed = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.CLOSED));
            report(new TerminalEvent.PaneClosed(tab.id(), pane.id()));
        };
        pane.onPaneFocused = () -> {
            emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.FOCUSED));
            report(new TerminalEvent.PaneFocused(tab.id(), pane.id()));
        };
        pane.onPaneBlurred = () -> emit(new WorkspaceActivity.PaneState(pane.id(), WorkspaceActivity.State.BLURRED));
        pane.onCommandFinished = (command, exitStatus, duration, workingDirectory, remote) -> {
            emit(new WorkspaceActivity.Finished(pane.id(), command, exitStatus, duration,
                new WorkspaceActivity.Origin(anyWindowActive.getAsBoolean(), isActiveAndOpen(),
                    tab == currentTab(), pane.view() != null && pane.view().isFocusOwner()),
                () -> { selectTab(tab); tab.focus(pane); pane.focusTerminal(); }));
            report(new TerminalEvent.CommandFinished(pane.id(), command, exitStatus, duration, workingDirectory, remote));
        };
        pane.onDirectoryChanged = (directory, remote) -> report(new TerminalEvent.DirectoryChanged(pane.id(), directory, remote));
        pane.onBell = () -> report(new TerminalEvent.Bell(tab.id(), pane.id()));
        pane.onStarted = () -> report(new TerminalEvent.SessionStarted(pane.id()));
        pane.onExited = status -> report(new TerminalEvent.SessionExited(pane.id(), status));
        pane.onConnecting = () -> report(new TerminalEvent.SessionConnecting(pane.id()));
    }

    private void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }

    private void configurePane(TerminalTab tab, TerminalPane pane) {
        pane.allowLaunchFocus = () -> commandPalette == null || !commandPalette.isOpen();
        pane.applyTheme(themes.current());
        ConfigSnapshot configured = workspaceConfiguration.snapshot();
        float configuredFontSize = workspaceConfiguration.configuredFontSize();
        if (configured == null) {
            pane.view().setFontSize(configuredFontSize);
        } else {
            pane.view().applyOptions(configured.viewOptions(configuredFontSize, themes.current().palette()));
            pane.setConfiguredDim(configured.terminal().dimInactivePanes());
            pane.setShellExitBehavior(configured.terminal().onExit());
        }
        pane.view().setShortcutHandler(event -> dispatchShortcut(KeyStroke.getKeyStrokeForEvent(event), pane.view()));
        pane.view().setContextMenuHandler(event -> {
            selectTab(tab); tab.focus(pane); pane.focusTerminal(); update();
            JPopupMenu popup = chrome.contextMenu();
            popup.show(pane.view(), event.getX(), event.getY());
        });
        if (!firstTerminalSeen) {
            firstTerminalSeen = true;
            try {
                firstTerminal.accept(pane);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "The first-terminal observer failed", failure);
            }
        }
    }

    void selectTab(Component tab) {
        if (tabs.indexOfComponent(tab) >= 0) { tabs.setSelectedComponent(tab); focusSelected(); }
    }

    void reorderTab(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= tabs.getTabCount() || to >= tabs.getTabCount()) return;
        Component selected = tabs.getSelectedComponent();
        Component tab = tabs.getComponentAt(from);
        String title = tabTitle(tab);
        rearranging = true;
        try {
            tabs.removeTabAt(from); tabs.insertTab(title, null, tab, null, to);
            tabs.setSelectedComponent(selected);
        } finally { rearranging = false; }
        update();
    }

    public void closeTab(Component tab) {
        var entry = hosted.get(tab);
        if (entry != null) { entry.requestClose(); return; }
        if (!(tab instanceof TerminalTab terminal)) return;
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        Runnable change = () -> {
            terminal.close();
            if (terminals != null) terminals.tabClosed(terminal);
            tabs.removeTabAt(index); update();
        };
        if (terminals != null) terminals.atomically(change); else change.run();
        if (tabs.getTabCount() == 0 && !closed) onEmpty.run();
    }

    boolean dispatchShortcut(KeyStroke stroke, Component source) {
        if (dev.jasper.app.platform.WindowInput.blocked(source == null ? this : source)) return true;
        if (paletteKeys.dispatchShortcut(stroke, source == null ? this : source)) return true;
        var document = selectedContent();
        if (document != null) {
            // Text controls retain their native selection/clipboard behavior (including find fields).
            if (source instanceof JTextComponent && nativeContentClipboard(document, stroke)) return false;
            for (var command : document.commands()) if (stroke.equals(command.shortcut())) {
                if (command.enabled()) command.run().run(); return true;
            }
        }
        Optional<ShortcutTarget> target = shortcutTarget(stroke, source);
        if (target.isEmpty()) return false;
        if (!target.get().runs()) return true;
        Optional<ActionId> builtIn = ActionId.forId(target.get().id());
        if (builtIn.isPresent()) invoke(builtIn.get()); else contributed.invoke(target.get().id());
        return true;
    }

    /** Side-effect free apart from refreshing enablement: whether {@link #dispatchShortcut} would take the key. */
    boolean claimsShortcut(KeyStroke stroke, Component source) {
        if (dev.jasper.app.platform.WindowInput.blocked(source == null ? this : source)) return true;
        if (paletteKeys.claimsShortcut(stroke)) return true;
        var document = selectedContent();
        if (document != null) {
            if (source instanceof JTextComponent && nativeContentClipboard(document, stroke)) return false;
            if (document.commands().stream().anyMatch(command -> stroke.equals(command.shortcut()))) return true;
        }
        return shortcutTarget(stroke, source).isPresent();
    }

    private static boolean nativeContentClipboard(dev.jasper.app.contributions.ContentEntry document, KeyStroke stroke) {
        return document.commands().stream().anyMatch(command ->
            stroke.equals(command.shortcut()) && (command.id().equals("copy") || command.id().equals("paste")));
    }

    /** The action a stroke resolves to from a focus owner; a disabled one only swallows the key. */
    private record ShortcutTarget(String id, boolean runs) {}

    /**
     * What a stroke does from this focus owner: of the actions bound to it whose context contains the focus owner, the
     * most specific. Empty when none applies, or when that action is disabled outside a terminal pane, so the key
     * reaches the focused component (the shell, in a terminal pane). In a terminal pane a disabled action takes the key
     * without running (plan Deviation 9): passed through, X11 would send Ctrl+Shift+C to the shell as ^C.
     */
    private Optional<ShortcutTarget> shortcutTarget(KeyStroke stroke, Component source) {
        Optional<String> found = bindings.resolve(stroke, context -> applies(context, source));
        if (found.isEmpty()) return Optional.empty();
        Optional<ActionId> builtIn = ActionId.forId(found.get());
        if (builtIn.isPresent() && source instanceof JTextComponent
            && (builtIn.get() == ActionId.COPY || builtIn.get() == ActionId.PASTE)) return Optional.empty();
        boolean enabled;
        if (builtIn.isEmpty()) enabled = contributed != null && contributed.enabled(found.get());
        else { updateActions(); enabled = action(builtIn.get()).isEnabled(); }
        if (!enabled && !within(TerminalPane.class, source)) return Optional.empty();
        return Optional.of(new ShortcutTarget(found.get(), enabled));
    }

    /** Whether a shortcut context contains the focus owner. */
    boolean applies(KeyContext context, Component focus) {
        return switch (context.kind()) {
            case GLOBAL -> true;
            case TERMINAL -> within(TerminalPane.class, focus);
            case CONTENT -> {
                var entry = selectedContent();
                yield entry != null && focus != null && entry.plugin().equals(context.owner())
                    && SwingUtilities.isDescendingFrom(focus, entry.component());
            }
            case PANEL -> contributed != null && contributed.panelContains(context.owner(), focus);
            // Plugin windows get the Keymap in plan K4; nothing in a terminal window is inside one.
            case WINDOW -> false;
        };
    }

    private static boolean within(Class<?> type, Component focus) {
        for (Component component = focus; component != null; component = component.getParent())
            if (type.isInstance(component)) return true;
        return false;
    }

    void invoke(ActionId id) { workspaceActions.invoke(id); }
    public void updateActions() { workspaceActions.update(); }

    void update() {
        if (closed) return;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component tab = tabs.getComponentAt(i);
            tabs.setTitleAt(i, tabTitle(tab));
            if (tab instanceof TerminalTab terminal) terminal.setActive(active && tab == currentTab());
            else { var entry = hosted.get(tab); if (entry != null) entry.active(tab == selectedComponent()); }
        }
        TerminalPane pane = currentPane();
        String size = pane == null || pane.session() == null ? "Starting terminal" :
            pane.session().columns() + " \u00d7 " + pane.session().rows();
        chrome.status().setMetadata(pane == null ? null : pane.snapshot(), pane == null ? "" : size, pane != null && pane.running());
        chrome.status().setContent(selectedContent());
        onTitle.accept(selectedComponent() == null ? "Jasper" : tabTitle(selectedComponent()));
        syncContentBindings();
        updateActions(); refreshTabs(); onMinimumSizeChanged.run();
        if (terminals != null) terminals.refresh();
    }

    ResolvedTheme theme() { return themes.current(); }

    /**
     * Slate Steel's Dark ({@code dark}) or Light variant in every window sharing this window's themes, as
     * {@code ui.appearance} applies it; the terminal scheme stays. Tests and previews switch variants this way.
     */
    void selectVariant(boolean dark) {
        if (closed) return;
        try { themes.configure(dark, themes.schemeChoice()); }
        catch (ThemeController.InstallationFailure failure) { onError.accept("Could not apply the theme", failure.getMessage()); }
        finally { chrome.refreshTheme(); chrome.status().refreshTheme(); }
    }

    /** View \u203a Terminal Scheme: saved for every window; applied to this window's themes when no configuration is connected. */
    void selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice scheme) {
        choose(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME, scheme, () -> {
            themes.configureScheme(scheme);
            chrome.refreshTheme();
        });
    }

    boolean terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice scheme) { return themes.schemeChoice() == scheme; }

    /** View \u203a Toolbar. */
    void chooseToolbarMode(ToolbarMode mode) {
        choose(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR, mode, () -> setToolbarMode(mode));
    }

    /** View \u203a Status Bar. */
    void chooseStatusVisible(boolean visible) {
        choose(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR, visible, () -> setStatusVisible(visible));
    }

    /**
     * A View choice. With a configuration it is saved, and every window follows the reload; a rejected file or a
     * failed save leaves everything as it was and says why. Without one it applies here only.
     */
    private <T> void choose(dev.jasper.app.settings.Setting<T> setting, T value, Runnable locally) {
        if (closed) return;
        var save = saveSettings;
        var state = configurationState;
        if (save == null || state == null) { locally.run(); return; }
        if (state.rejected()) { revertChoices(); onError.accept("Settings not saved", REJECTED_CHOICE); return; }
        java.util.concurrent.CompletableFuture<?> result;
        try { result = save.apply(dev.jasper.app.config.SettingsEdit.of(setting, value, state.values())); }
        catch (RuntimeException failure) { result = java.util.concurrent.CompletableFuture.failedFuture(failure); }
        result.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            SwingUtilities.invokeLater(() -> {
                if (closed) return;
                revertChoices();
                Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
                onError.accept("Settings not saved", cause.getMessage());
            });
        });
    }

    /** A clicked check box or radio item shows its new state at once; put the menus back to what is in effect. */
    private void revertChoices() {
        updateActions();
        chrome.refreshTheme();
    }

    /** Updates all retained panes without reparenting them; the native boundary hooks in last. */
    void applyTheme(ResolvedTheme theme, boolean updateDelegates) {
        if (closed) return;
        var retained = terminalTabs();
        retained.forEach(TerminalTab::beginThemeUpdate);
        try {
            if (updateDelegates) SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
            if (updateDelegates && (bindingRoot == null || menuBar().getParent() == null)) SwingUtilities.updateComponentTreeUI(menuBar());
            leftStripe.refreshTheme();
            rightStripe.refreshTheme();
            area.refreshTheme();
            area.setTerminalBackground(theme.surface());
            WorkspaceTheme.apply(windowTabs, theme);
            windowTabs.refresh();
            for (var component : new ArrayList<>(hosted.keySet())) {
                var entry = hosted.get(component);
                if (entry != null) WorkspaceTheme.apply(entry.component(), theme, () -> hosted.get(component) == entry);
            }
            if (updateDelegates && contributed != null) contributed.refreshTheme();
            for (TerminalTab tab : retained) {
                WorkspaceTheme.apply(tab, theme);
                tab.setBackground(theme.surface());
                for (TerminalPane pane : tab.panes()) {
                    // Zoom detaches sibling panes from the visible component hierarchy.
                    if (updateDelegates && !SwingUtilities.isDescendingFrom(pane, this)) SwingUtilities.updateComponentTreeUI(pane);
                    pane.applyTheme(theme);
                }
            }
            setBackground(theme.surface());
            tabs.setBackground(theme.surface());
            chrome.refreshTheme();
            if (commandPalette != null) commandPalette.refreshTheme();
            onThemeChanged.accept(theme); update();
            requestGlass();
            revalidate(); repaint();
        } finally {
            retained.forEach(TerminalTab::endThemeUpdate);
        }
    }

    /**
     * Where this window's requested workspace glass goes, with whether the workspace theme is dark (it picks the blur
     * material); the native window boundary installs it.
     */
    void setGlassRequests(BiConsumer<GlassLevels, Boolean> requests) {
        glassRequests = Objects.requireNonNull(requests);
        requestGlass();
    }

    /** Asks for the saved glass over the workspace theme's; called after every theme or configuration change. */
    void requestGlass() {
        if (closed) return;
        ConfigSnapshot configured = workspaceConfiguration.snapshot();
        var workspace = themes.current().workspace();
        glassRequests.accept((configured == null ? GlassSettings.UNSET : configured.glass()).resolve(workspace.glass()),
            workspace.dark());
    }

    /**
     * The opacity actually in effect for this window, 1 being opaque. The terminal surface and each pane paint the
     * terminal background at that opacity, each replacing what lies beneath. The flat tab strip follows it; chrome
     * and dividers stay solid.
     */
    void applyBackgroundOpacity(double opacity) {
        if (closed || opacity == backgroundOpacity) return;
        backgroundOpacity = opacity;
        float value = (float) opacity;
        boolean glass = value < 1f;
        area.setGlass(value);
        tabs.setOpaque(!glass);
        for (TerminalTab tab : terminalTabs()) tab.setBackgroundOpacity(value);
        windowTabs.refresh();
        repaint();
        glassListener.run();
    }

    /**
     * The workspace surface's colour: the terminal background, at the window's opacity while translucent. The flat
     * tab strip shares it, so tabs, terminals and hosted content read as one space.
     */
    java.awt.Color workspaceSurface() {
        java.awt.Color background = themes.current().surface();
        if (backgroundOpacity >= 1) return background;
        return new java.awt.Color(background.getRed(), background.getGreen(), background.getBlue(),
            (int) Math.round(255 * backgroundOpacity));
    }

    /** The opacity in effect for this window, as last applied; 1 is opaque. EDT only.
     * @return from 0.1 to 1
     */
    public double backgroundOpacity() { return backgroundOpacity; }

    /** The opacity behind {@code component}: this window's opacity when it sits in the workspace centre, where the
     * terminal tabs and hosted content live, and 1 anywhere else (chrome, the stripes and the tool windows). EDT only.
     * @param component a component that may be in this window
     * @return from 0.1 to 1
     */
    public double workspaceOpacity(Component component) {
        return component != null && SwingUtilities.isDescendingFrom(component, tabs) ? backgroundOpacity : 1;
    }

    /** Runs when this window's background opacity changes, or hosted content enters or leaves it while translucent.
     * EDT only; replaces any earlier listener.
     * @param listener the callback
     */
    public void setGlassListener(Runnable listener) { glassListener = Objects.requireNonNull(listener); }

    int tabHeight() { return tabHeight; }

    int railWidth() { return railWidth; }

    int toolbarHeight() { return toolbarHeight; }

    int statusBarHeight() { return statusBarHeight; }

    /** Rejects a size outside its range, naming the setting and its limits. */
    private static void requireSize(String setting, int value, int min, int max) {
        if (value < min || value > max)
            throw new IllegalArgumentException(setting + " must be between " + min + " and " + max + " pixels");
    }

    void setToolbarHeight(int height) {
        requireSize("Toolbar height", height, ConfigSnapshot.MIN_TOOLBAR_HEIGHT, ConfigSnapshot.MAX_TOOLBAR_HEIGHT);
        if (closed || toolbarHeight == height) return;
        toolbarHeight = height;
        chrome.setToolbarHeight(height);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setStatusBarHeight(int height) {
        requireSize("Status bar height", height, ConfigSnapshot.MIN_STATUS_BAR_HEIGHT, ConfigSnapshot.MAX_STATUS_BAR_HEIGHT);
        if (closed || statusBarHeight == height) return;
        statusBarHeight = height;
        chrome.status().setBarHeight(height);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setRailWidth(int width) {
        requireSize("Rail width", width, ConfigSnapshot.MIN_RAIL_WIDTH, ConfigSnapshot.MAX_RAIL_WIDTH);
        if (closed || railWidth == width) return;
        railWidth = width;
        leftStripe.setRailWidth(width);
        rightStripe.setRailWidth(width);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setAnimations(dev.jasper.app.config.AnimationSettings settings) {
        if (closed) return;
        if (windowTabs != null) windowTabs.setAnimations(settings);
        commandPalette.setAnimations(settings);
    }

    void setTabHeight(int height) {
        requireSize("Tab height", height, MIN_TAB_HEIGHT, MAX_TAB_HEIGHT);
        if (closed || tabHeight == height) return;
        tabHeight = height;
        if (windowTabs != null) { windowTabs.revalidate(); windowTabs.repaint(); }
        onTabHeightChanged.run();
        revalidate(); repaint();
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.revalidate(); root.repaint(); }
        onMinimumSizeChanged.run();
    }

    void setActive(boolean value) {
        if (!value && commandPalette != null) commandPalette.dismiss();
        // A modal dialog opening takes the drag's release, and so does another application taking focus.
        if (!value) dockDrag.cancel();
        active = value; if (value && contentRegistry != null) contentRegistry.activated(id); if (windowTabs != null) windowTabs.setActive(value); update();
    }
    void setToolbarMode(ToolbarMode mode) {
        toolbarMode = Objects.requireNonNull(mode); refreshChromeVisibility(); updateActions();
    }
    void setStatusVisible(boolean visible) {
        statusVisible = visible; refreshChromeVisibility(); updateActions();
    }

    private void refreshChromeVisibility() {
        chrome.setToolbarMode(toolbarMode);
        chrome.setStatusVisible(!zenMode() && statusVisible);
        area.setStatusVisible(!zenMode() && statusVisible);
        chrome.header().revalidate(); chrome.header().repaint();
        revalidate(); repaint(); onMinimumSizeChanged.run();
    }

    @Override public void close() {
        if (closed) return;
        if (bodyLease != null) { bodyLease.close(); bodyLease = null; }
        dockDrag.cancel();
        paletteKeys.close();
        if (contributed != null) { contributed.close(rememberLayout); contributed = null; }
        area.close();
        commandPalette.close(); windowCommands.close(); commands.close();
        scopes.close();
        closed = true;
        if (contentHost != null) { contentHost.close(); contentHost = null; }
        for (var component : new ArrayList<>(hosted.keySet())) {
            WorkspaceTheme.clear(hosted.get(component).component());
        }
        syncPaletteDispatcher();
        unregisterConfiguration.run(); disconnectConfiguration();
        showConfigDiagnostics = control -> {};
        if (windowTabs != null) windowTabs.close();
        uninstallCornerLayer();
        if (cornerLayer != null) { cornerLayer.removePropertyChangeListener("preferredSize", cornerResized); cornerLayer = null; }
        themeRegistration.close();
        Runnable closeTabs = () -> {
            for (TerminalTab tab : terminalTabs()) { tab.close(); if (terminals != null) terminals.tabClosed(tab); }
        };
        if (terminals != null) terminals.atomically(closeTabs); else closeTabs.run();
        tabs.removeAll(); removeRootBindings();
        if (terminals != null) { terminals.close(); terminals = null; }
        workspaceActions.disable();
        refreshTabs();
        onThemeChanged = theme -> {};
        onTabHeightChanged = () -> {};
        onTitle = title -> {}; onError = (title, details) -> {}; onInfo = (title, details) -> {}; onLaunchFailure = (details, cause) -> {}; onMinimumSizeChanged = () -> {};
        onToggleBuddy = () -> {}; buddyEnabled = () -> false;
        activityListeners.clear();
        anyWindowActive = () -> false;
    }
}
