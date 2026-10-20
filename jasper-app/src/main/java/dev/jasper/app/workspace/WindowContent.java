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
import dev.jasper.app.contributions.Tab;
import dev.jasper.app.contributions.TabRegistry;
import dev.jasper.app.contributions.WorkspaceHost;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.Side;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.palette.PaletteKeyRouter;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.ScopeRegistry;
import dev.jasper.app.palette.CommandsScope;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.platform.MacTitleBand;
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
    private final Consumer<Path> newTerminalWindow;
    private final Runnable quit;
    private final Runnable onEmpty;
    /** Each tab this window shows, with its change listener. */
    private final Map<Tab, Subscription> tabListeners = new IdentityHashMap<>();
    /** This window's side of its terminal tabs (workspace tabs spec 9.3). */
    private final TabHost tabHost = new TabHost(this);
    /** Where focus goes: focus going into a plugin's view, or a pane, makes its tab the active tab (Rulings 26). */
    private final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private final java.beans.PropertyChangeListener focusOwners = event -> {
        if (event.getNewValue() instanceof Component focused) tabFocused(tabHolding(focused));
    };
    /** Where this window's tabs live: a registry of its own until it joins the application's (connectContributions). */
    private TabRegistry registry = new TabRegistry();
    /** This window's workspace in {@link #registry}; closing it removes the tabs the window still holds. */
    private Subscription host;
    private Runnable bringToFront = () -> {};
    /** The window manager that opened this window, which moves its tabs to other windows (tabs and panes spec 6); else null. */
    private WindowManager manager;
    /** The main window, with Jasper's chrome, or a child window, a workspace alone (workspace tabs spec 6). */
    private final WindowRole role;
    /**
     * The window's parent tabs, each a workspace of tabs as groups in splits under the look's strips (workspace tabs spec
     * 10; parent tabs spec 4), where the terminal column stood.
     */
    private final ParentTabsView parentTabs;
    /** Where tabs moving in land while {@link #receive} runs: a parent tab, and its place in the bar when it is new. */
    private record Landing(ParentTabId parentTab, int index) { }
    private Landing landing;
    /** The configured tab motion, which strips made later take too. */
    private dev.jasper.app.config.AnimationSettings animations = dev.jasper.app.config.AnimationSettings.defaults();
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
    /** The window's stripes: icon rails, or labelled stripes and the bottom strip (spec 10.7). */
    private final StripeSet stripes;
    private final DockStripe leftStripe, rightStripe;
    /** Drags a tool window from its stripe button or its header to another slot. */
    private final DockDrag dockDrag;
    /**
     * Drags a parent tab from the bar (tabs and panes spec 6): the window manager's, which every window it opens shares;
     * until it gives that, one of this window's own, which reorders the bar and moves nothing out of this window, as a
     * tear-out needs a manager ({@link #mayMoveTabToNewWindow}).
     */
    private TabDrag tabDrag = new TabDrag(() -> java.util.List.of(this));
    private UiState uiState = UiState.inMemory();
    private boolean railVisible = true;
    /** False once {@link #forgetLayout} ran: closing then saves nothing. */
    private boolean rememberLayout = true;
    private final WorkspaceConfiguration workspaceConfiguration = new WorkspaceConfiguration(this);
    private final WindowChrome chrome;
    /** The look's workspace strategies, fixed for this window's life. */
    private final WorkspaceChrome look;
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
    Runnable onMinimumSizeChanged = () -> {};
    /** What Close Window does: the window it belongs to sets it (workspace tabs spec 6.4). */
    Runnable onCloseWindow = () -> {};
    /** What {@code workspace.toml} saves of this window changed: its layout, or a terminal tab's name or directory (spec 13). */
    Runnable onLayoutChanged = () -> {};
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
    private WindowTerminals terminals;
    /** The tab the palette last heard was active: a layout change that keeps it, such as a maximize, leaves the palette open. */
    private Tab paletteTab;
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
    /** Whether this window's macOS appearance follows the theme (a native-titled look, spec 10.1); its dialogs follow too. */
    private boolean followsThemeAppearance;
    /** IntelliJ Classic's macOS title band over the toolbar under a classic title (Enterprise plan 14), or null. */
    private MacTitleBand titleBand;
    /** The frame's formatted title, which the Windows classic title shows too. */
    private String frameTitle = "Jasper";
    /** The Windows classic title bar's title under a classic title (Enterprise plan 14), or null until decorated. */
    private ClassicWindowTitle classicWindowTitle;
    /** A Windows child window's title pane colours, which follow the theme and the window's activity; null elsewhere. */
    private Runnable childTitleColors;
    Consumer<JComponent> showConfigDiagnostics = control -> {
        if (!followsThemeAppearance) JOptionPane.showMessageDialog(this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);
        else showOptionDialog(new JOptionPane(control, JOptionPane.PLAIN_MESSAGE), "Configuration");
    };
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
    /**
     * A Cmd-click (Ctrl-click elsewhere) named a file in one of this window's local panes: the resolved path, and the
     * line written after it, 0 for none. The application checks the file off the EDT and opens it; by default nothing
     * opens. EDT.
     */
    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };
    /**
     * File › Open File…'s chooser: given the active pane's folder, the file the person chose, or empty when they cancelled.
     * The application shows its file chooser over this window; by default nothing is chosen. EDT.
     */
    public java.util.function.Function<java.util.Optional<Path>, java.util.Optional<Path>> fileChooser = folder -> java.util.Optional.empty();

    /** Replays existing producers before returning; the listener receives later events until closed. */
    public Subscription activity(Consumer<WorkspaceActivity.Event> listener) {
        Objects.requireNonNull(listener);
        if (closed) return new Subscription(() -> {});
        activityListeners.add(listener);
        try {
            for (TerminalContent terminal : terminalTabs())
                listener.accept(new WorkspaceActivity.PaneState(terminal.pane().id(), WorkspaceActivity.State.OPENED));
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

    void emit(WorkspaceActivity.Event event) {
        for (var listener : java.util.List.copyOf(activityListeners)) listener.accept(event);
    }


    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty) {
        this(launcher, directory, newTerminalWindow, quit, onEmpty, new ThemeController(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes) {
        this(launcher, directory, newTerminalWindow, quit, onEmpty, themes,
            KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac")));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings) {
        this(launcher, directory, newTerminalWindow, quit, onEmpty, themes, bindings,
            new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, directory == null, newTerminalWindow, quit, onEmpty, themes, bindings, history, macOs);
    }

    /**
     * An {@code empty} window starts with no tab; any other starts one tab in {@code directory}, where null means where a
     * new shell starts. {@code newTerminalWindow} receives null the same way.
     */
    WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, empty, newTerminalWindow, quit, onEmpty, themes, bindings, history, macOs, WorkspaceChrome.STANDARD);
    }

    /** As above, as the main window, under the look's workspace chrome (Enterprise presentation spec 9.1). */
    WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newTerminalWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look) {
        this(WindowRole.MAIN, launcher, directory, empty, newTerminalWindow, quit, onEmpty, themes, bindings, history, macOs, look);
    }

    /**
     * A window's content in {@code role} (workspace tabs spec 6): the main window's shows Jasper's chrome around its
     * workspace; a child window's shows its workspace alone, with the same actions, menus, keymap and palette, and docks
     * no tool window.
     *
     * @param role the main window or a child window
     * @param launcher starts the window's shells
     * @param directory where the first tab starts, or null for where a new shell starts
     * @param empty whether the window starts without a tab
     * @param newTerminalWindow opens a window with a terminal tab in a directory, or where a new shell starts when it is null
     * @param quit quits Jasper
     * @param onEmpty runs when the window's last tab has closed or moved out
     * @param themes the application's look
     * @param bindings the keymap until a configuration arrives
     * @param history the shared command history
     * @param macOs whether the keymap and shortcut text are macOS's
     * @param look the workspace's chrome under the look
     */
    public WindowContent(WindowRole role, ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newTerminalWindow,
                         Runnable quit, Runnable onEmpty, ThemeController themes, KeyBindings bindings, CommandHistory history,
                         boolean macOs, WorkspaceChrome look) {
        super(new BorderLayout());
        this.role = Objects.requireNonNull(role, "role");
        this.look = Objects.requireNonNull(look, "look");
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
        this.launcher = launcher; this.newTerminalWindow = newTerminalWindow; this.quit = quit; this.onEmpty = onEmpty;
        workspaceActions = new WorkspaceActions(this);
        windowCommands = new WindowCommands(this, commands);
        chrome = new WindowChrome(this);
        commandsScope = new CommandsScope(commands, history, macOs, this::dispatchCommand);
        scopes.register(commandsScope);
        commandPalette = new WindowCommandPalette(this, scopes, history, PaletteScope.ALL_ID, macOs,
            themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), look.palette(), look.paletteHome());
        paletteKeys = new PaletteKeyRouter(commandPalette.controller(), commandPalette::open, () -> this.bindings, macOs,
            this::ownsKeySource);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) syncPaletteDispatcher();
        });
        // The parent tabs are the terminal column, so the stripes and the side panels reach the toolbar; the rounded host
        // paints behind this layout-only container, including partial tab repaints.
        parentTabs = new ParentTabsView(this);
        stripes = look().dock().stripes(themes.kit(), look().chromeArtwork());
        leftStripe = stripes.left();
        rightStripe = stripes.right();
        area = new ToolWindowArea(parentTabs, dock, themes.kit(), look().dock().flushFrames());
        area.setStatusVisible(chrome.status().isVisible());
        area.setHeaderVisible(true);
        for (DockStripe stripe : stripes.all()) stripe.setVisible(false);

        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        stripes.bottom().ifPresent(strip -> body.add(strip, BorderLayout.SOUTH));
        dockDrag = new DockDrag(this, dock, area, stripes);
        dockDrag.railsEnabled = this::railsEnabled;
        for (DockStripe stripe : stripes.all()) stripe.dragSource = dockDrag::install;
        area.dragSource = dockDrag::install;
        area.releaseDragSource = dockDrag::uninstall;
        // A dock change moves the window's minimum size; read the hook when it fires, as the frame sets it later.
        area.onMinimumSizeChanged = () -> onMinimumSizeChanged.run();
        // A child window shows its workspace alone, in the centre surface that paints the main window's glass, with no dock,
        // stripes or chrome around it; its chrome serves its menus and actions and is never shown (spec 6.3).
        if (role == WindowRole.MAIN) { add(chrome.header(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH); }
        else add(area, BorderLayout.CENTER);
        themeRegistration = themes.subscribe(this::applyTheme);
        focusManager.addPropertyChangeListener("permanentFocusOwner", focusOwners);
        host = registry.addHost(workspaceHost());
        if (!empty) {
            try { newTab(directory); }
            catch (RuntimeException | Error failure) {
                // The theme controller and the focus manager outlive a window that failed to build.
                try { themeRegistration.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                try { windowCommands.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
                throw failure;
            }
        } else setPreferredSize(new Dimension(900, 650));
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

    /**
     * Mounts {@code content} in a window frame's {@code root} under {@code look} (Enterprise presentation spec 10.1): the
     * unified macOS bar hosting the header, the system's title bar with the header as a row in the window, or, under a
     * classic title, IntelliJ Classic's band in the toolbar's colour above that row on macOS and FlatLaf's title pane with
     * the menus and the title on Windows. Either way the menu bar is the root's (the unified bar's {@code setMenuBar} puts
     * it there too); on macOS the screen menu bar shows it while the look keeps that on, and otherwise it is a row in the
     * window or of the Windows title bar. The main window's frame and the chrome renders share this path. Returns the
     * unified bar, or null.
     */
    static MacTitleBar mount(JRootPane root, WindowContent content, WindowLook look, Consumer<String> nativeTitle) {
        MacTitleBar bar = installTitleBar(root, content, look.unifiedTitle(), look.followThemeAppearance(), nativeTitle);
        // IntelliJ Classic's frame (plan 14): the band on macOS, FlatLaf's title pane with the menus and the title on Windows.
        if (bar == null && look.classicTitle() && content.macOs) installMacTitleBand(root, content);
        else if (bar == null && look.classicTitle()) installClassicTitleBar(root, content);
        else if (bar == null) installCustomTitleBar(root, content);
        // Under a native-titled look the header is a row of window chrome, whichever composition draws it (spec 10.1,
        // and spec 9.1's fallback row); the default look keeps today's title-bar zone.
        if (bar == null && look.followThemeAppearance()) content.chrome().chromeRow();
        if (bar != null) bar.setMenuBar(content.menuBar()); else root.setJMenuBar(content.menuBar());
        return bar;
    }

    /**
     * Mounts a child window's {@code content} in its frame's {@code root} (workspace tabs spec 6.3): the look's title bar
     * (the unified macOS bar with a centred title, the system's, or, under a classic title, IntelliJ Classic's band on
     * macOS and a 28 px FlatLaf title pane in the toolbar's colour on Windows) showing the active tab's title, and no
     * header, toolbar or status bar. On macOS the screen menu bar shows the same menus as the main window's, built from
     * the same menu model, while the look keeps menus there ({@code screenMenuBar}); a child window never shows a menu
     * row, and its title row ends with its ⋮ ({@code mountWindowMenu}): on Windows and Linux the title pane holds the
     * title and the ⋮. Returns the unified bar, or null.
     */
    static MacTitleBar mountChild(JRootPane root, WindowContent content, WindowLook look, boolean screenMenuBar,
                                  Consumer<String> nativeTitle) {
        // Compact keeps the system's title bar, as the main window's does (spec 10.1).
        boolean compact = content.viewMode == ViewMode.COMPACT;
        MacTitleBar bar = null;
        if (compact) root.setContentPane(content);
        else bar = MacTitleBar.install(root, content, () -> content.onMinimumSizeChanged.run(), look.unifiedTitle());
        // Under a classic title macOS always shows the menus in the screen menu bar, Compact included (Enterprise plan 14).
        if (look.classicTitle() && content.macOs) content.chrome.menuBar().screenMenuBar(true);
        boolean classic = look.classicTitle() && !compact;
        if (bar != null) {
            MacTitleBar unified = bar;
            unified.setStyle(MacTitleBar.Style.WINDOW);
            // The native controls sit on the title bar, which a theme may make dark on a light theme or light on a dark one.
            content.onThemeChanged = theme -> unified.setLight(!dev.jasper.ui.theme.TitleBarTheme.of(theme.chrome()).dark());
        } else if (classic && content.macOs) {
            // IntelliJ Classic's band, as the main window's (plan 14 Rulings 7 and 8); a child window has no toolbar, so
            // the band takes the colour the main window's toolbar row has under a classic title.
            content.titleBand = MacTitleBand.install(root, () -> ThemeZone.color(null, SemanticColor.SURFACE_APP));
            content.onThemeChanged = theme -> content.titleBand.setDark(theme.chrome().dark());
        } else if (content.macOs && (compact || look.followThemeAppearance())) {
            content.onThemeChanged = theme -> dev.jasper.app.platform.MacAppearance.follow(root, theme.chrome().dark());
        } else if (!content.macOs) {
            // FlatLaf's title pane holds the title and the window's ⋮ (mountWindowMenu), in the chrome's colours, as
            // the main window's holds its title; under a classic title it is 28 px, as the main window's (plan 14
            // Rulings 13), and in-window dialogs and overlays start below it (Rulings 19).
            if (classic) {
                root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, ClassicWindowTitle.BAR_HEIGHT);
                dev.jasper.app.platform.TitleInset.publish(root, () -> root.getContentPane() == null ? 0 : root.getContentPane().getY());
            }
            // FlatLaf paints the title in this one colour, active or not, so a classic title dims it here while the window is
            // inactive, as the main window's does (Rulings 14).
            Runnable colors = () -> {
                root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(null, SemanticColor.SURFACE_APP));
                root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, classic
                    ? dev.jasper.ui.theme.SurfaceContrast.painted(dev.jasper.ui.swing.FlatLafInstaller.installedTheme(),
                        content.active ? WindowPairs.TITLE : WindowPairs.INACTIVE_TITLE)
                    : ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
            };
            content.childTitleColors = colors;
            content.onThemeChanged = theme -> colors.run();
        }
        content.onThemeChanged.accept(content.theme());
        content.followsThemeAppearance = bar == null && look.followThemeAppearance() && content.macOs;
        if (screenMenuBar) root.setJMenuBar(content.menuBar());
        content.mountWindowMenu(root, bar, look.classicTitle());
        MacTitleBar titled = bar;
        content.onTitle = title -> {
            nativeTitle.accept(title);
            if (titled != null) titled.setTitle(title);
        };
        if (content.bodyLease != null) content.bodyLease.close();
        content.bodyLease = dev.jasper.app.platform.WindowBody.install(root, content,
            () -> new java.awt.Rectangle(0, 0, content.getWidth(), content.getHeight()));
        content.update();
        return bar;
    }

    /**
     * Puts a child window's ⋮, its compact toolbar (parent tabs spec 6; workspace plan 7b Rulings 17), at the trailing end
     * of its title row: the unified macOS bar's, or IntelliJ Classic's band on macOS. On Windows and Linux it ends a menu
     * bar of its own after a glue, which FlatLaf's title pane embeds with the title in the glue, and which is a row under
     * the system's title bar otherwise. On macOS under the system's title bar it ends a row at the top of the window, 8 px
     * from its edge as in the other title rows. Under a {@code classic} title it is the classic ⋮ the main window's classic
     * row ends with ({@link WindowChrome#classicWindowMenu(WindowContent)}).
     */
    private void mountWindowMenu(JRootPane root, MacTitleBar bar, boolean classic) {
        JComponent button = classic ? WindowChrome.classicWindowMenu(this) : new WindowMenuButton(this);
        if (bar != null) bar.setTrailing(button);
        else if (titleBand != null) titleBand.setTrailing(button);
        else if (!macOs) {
            var row = new JMenuBar();
            row.setName("windowBar");
            row.add(Box.createHorizontalGlue());
            row.add(button);
            root.setJMenuBar(row);
        } else {
            var row = new JPanel(new BorderLayout());
            row.setName("windowBar");
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, com.formdev.flatlaf.util.UIScale.scale(8)));
            row.add(button, BorderLayout.EAST);
            add(row, BorderLayout.NORTH);
        }
    }

    /**
     * How {@link #showWindowMenu} shows the menu it is given: under the ⋮, while the ⋮ shows. A headless test, where
     * nothing shows, records what a click would show instead.
     */
    BiConsumer<JComponent, java.util.function.Supplier<JPopupMenu>> windowMenuPopup = (invoker, menu) -> {
        if (invoker.isShowing()) menu.get().show(invoker, 0, invoker.getHeight());
    };

    /** Opens {@link #windowMenu()} under {@code invoker}, a ⋮, while it shows (parent tabs spec 6). */
    void showWindowMenu(JComponent invoker) {
        if (!closed) windowMenuPopup.accept(invoker, this::windowMenu);
    }

    /**
     * The macOS classic title (plan 14 Rulings 7 and 8): IntelliJ Classic's band above the toolbar row, filled with the
     * header's {@code surface.app} so the band and the toolbar read as one, never the title-bar zone's colour. The window
     * takes the vibrant appearance of the theme's darkness in place of {@link #installTitleBar}'s Aqua follower; the
     * window's dialogs still follow through {@code followsThemeAppearance}. Compact keeps the system title bar without
     * the band.
     */
    static void installMacTitleBand(JRootPane root, WindowContent content) {
        // Under a classic title macOS always shows the menus in the screen menu bar (Rulings 5 and 18), Compact included.
        content.chrome.menuBar().screenMenuBar(true);
        if (content.viewMode == ViewMode.COMPACT) return;
        content.titleBand = MacTitleBand.install(root, () -> ThemeZone.color(content.chrome().header(), SemanticColor.SURFACE_APP));
        content.onThemeChanged = theme -> content.titleBand.setDark(theme.chrome().dark());
        content.onThemeChanged.accept(content.theme());
    }

    /** The macOS classic title band, or null without it. */
    MacTitleBand titleBand() { return titleBand; }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        return installTitleBar(root, content, supported, false, nativeTitle);
    }

    /**
     * As above; with {@code followTheme} a frame that has the system's title bar on macOS takes the theme's appearance,
     * as Compact does (spec 10.1).
     */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported, boolean followTheme,
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
            var snapshot = pane == null ? null : pane.snapshot();
            Path home = Path.of(System.getProperty("user.home"));
            var context = WindowHeaderContext.from(snapshot, home, pane == null ? "" : pane.programTitle(),
                pane == null ? "" : pane.commandLabel());
            if (document != null) context = new WindowHeaderContext(document.title() + (document.host().isEmpty() ? "" : " — " + document.host()), document.location(), document.host().isEmpty() ? "FILE" : "SERVER");
            // The frame title follows the look's template (spec 10.4); the title field keeps the context.
            String formatted = WindowTitle.format(content.look().title(), context.label(),
                document != null ? document.title() : WindowTitle.session(snapshot),
                document != null ? document.location() : WindowTitle.directory(snapshot, home));
            nativeTitle.accept(formatted);
            content.frameTitle = formatted;
            if (content.classicWindowTitle != null) content.classicWindowTitle.setText(formatted);
            content.chrome.search().setContext(context.label(), context.tooltip(), dev.jasper.app.platform.AppIcons.named(context.icon()));
        };
        if (bar != null) {
            // The native controls sit on the title bar, which a theme may make dark on a light theme or light on a dark one.
            content.onThemeChanged = theme -> bar.setLight(!dev.jasper.ui.theme.TitleBarTheme.of(theme.chrome()).dark());
            bar.setLight(!dev.jasper.ui.theme.TitleBarTheme.of(content.theme().chrome()).dark());
        }
        // The window's own dialogs (the configuration diagnostics, Rename Tab) follow with it under a native-titled look.
        content.followsThemeAppearance = bar == null && followTheme && content.macOs;
        if ((content.viewMode == ViewMode.COMPACT || bar == null && followTheme) && content.macOs) {
            content.onThemeChanged = theme -> dev.jasper.app.platform.MacAppearance.follow(root, theme.chrome().dark());
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
        return whenDecorated(root, content, WindowContent::decorateTitleBar);
    }

    /**
     * The Windows classic title (plan 14 Rulings 13 to 15): FlatLaf's title pane, 28 px in the toolbar's colour, embeds the
     * menus and then the frame's title, left-aligned after them; the header stays a row of the window under it. Returns
     * as {@link #installCustomTitleBar} does, and with native decorations FlatLaf never decorates, so the menus stay a
     * row of the window.
     */
    static boolean installClassicTitleBar(JRootPane root, WindowContent content) {
        return whenDecorated(root, content, WindowContent::decorateClassicTitleBar);
    }

    /** Runs {@code decorate} now if {@code root} has look-and-feel decorations, else once when they arrive; never in Compact. */
    private static boolean whenDecorated(JRootPane root, WindowContent content, BiConsumer<JRootPane, WindowContent> decorate) {
        if (content.viewMode == ViewMode.COMPACT) return false;
        if (root.getWindowDecorationStyle() != JRootPane.NONE) {
            decorate.accept(root, content);
            return true;
        }
        root.addPropertyChangeListener("windowDecorationStyle", new java.beans.PropertyChangeListener() {
            @Override public void propertyChange(java.beans.PropertyChangeEvent event) {
                if (root.getWindowDecorationStyle() == JRootPane.NONE) return;
                root.removePropertyChangeListener("windowDecorationStyle", this);
                decorate.accept(root, content);
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

    private static void decorateClassicTitleBar(JRootPane root, WindowContent content) {
        var title = new ClassicWindowTitle();
        title.setText(content.frameTitle);
        title.setActive(content.active);
        content.classicWindowTitle = title;
        content.chrome.menuBar().setTitle(title);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
        root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_HEIGHT, ClassicWindowTitle.BAR_HEIGHT);
        Runnable colors = () -> {
            // The toolbar's colour, not a title-bar zone, so any theme's bar matches its toolbar (Rulings 13).
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_BACKGROUND, ThemeZone.color(content.chrome().header(), SemanticColor.SURFACE_APP));
            root.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TITLE_BAR_FOREGROUND, ThemeZone.color(null, SemanticColor.TEXT_SECONDARY));
            title.refresh();
        };
        colors.run();
        content.onThemeChanged = theme -> colors.run();
        dev.jasper.app.platform.TitleInset.publish(root, () -> root.getContentPane() == null ? 0 : root.getContentPane().getY());
        content.revalidate();
    }

    /** The Windows classic title bar's title, or null until FlatLaf decorates the window under a classic title. */
    ClassicWindowTitle classicWindowTitle() { return classicWindowTitle; }

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
     * takes it, such as Ctrl+Tab and Ctrl+Shift+Tab in a text area, a table or a field, and Next and Previous Parent Tab
     * before the focus owner's own bindings, such as a scroll pane's Ctrl+PageUp and Ctrl+PageDown (workspace plan 7a
     * Rulings 14); it swallows that key's typed and released tail wherever focus went. Plain Tab is no Keymap binding, so
     * it still traverses.
     */
    private boolean traversalShortcut(KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_RELEASED -> { if (!traversalHeld.remove(event.getKeyCode())) return false; }
            case KeyEvent.KEY_TYPED -> { if (!traversalTyped) return false; traversalTyped = false; }
            case KeyEvent.KEY_PRESSED -> {
                traversalTyped = false;
                Component focus = event.getComponent();
                KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
                if (!ownsKeySource(focus) || !(traversalKey(focus, event) || parentTabKey(stroke))
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

    /** Whether {@code stroke} is Next or Previous Parent Tab's, which run before the focus owner's own bindings. */
    private boolean parentTabKey(KeyStroke stroke) {
        String bound = rootKeys.get(stroke);
        return ActionId.NEXT_PARENT_TAB.id().equals(bound) || ActionId.PREVIOUS_PARENT_TAB.id().equals(bound);
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
            dev.jasper.ui.swing.NativeStyles.installed().textArea(text, () -> ThemeZone.theme(text));
            text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setCaretPosition(0);
            text.getAccessibleContext().setAccessibleName("Configuration details");
            JScrollPane scroll = new JScrollPane(text);
            Runnable migrate = migrateConfiguration;
            if (!state.migratable() || migrate == null) { showConfigDiagnostics.accept(scroll); return; }
            var button = new JButton("Migrate Config\u2026");
            dev.jasper.ui.swing.NativeStyles.installed().framedButton(button, () -> ThemeZone.theme(button));
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
    /** New Terminal Window (workspace tabs spec 6.5): a child window with a terminal tab where a tab from here starts. */
    void requestNewTerminalWindow() { newTerminalWindow.accept(directory()); }
    /** Close Window (workspace tabs spec 6.4 and 11): this window, as its title bar's close button closes it. */
    void requestCloseWindow() { onCloseWindow.run(); }
    /**
     * Move Tab to New Window (tabs and panes spec 6): the parent tab holding {@code tab}, through the window manager;
     * nothing in a window outside one.
     */
    void moveTabToNewWindow(Tab tab) {
        if (manager != null && tab != null) parentTabOf(tab).ifPresent(parentTab -> manager.moveTabToNewWindow(this, parentTab));
    }
    /** Move to Main Window: this full window's parent tabs join the main window's bar. */
    void moveToMainWindow() { if (manager != null) manager.moveToMainWindow(this); }
    /** File › Open File…: {@link #fileChooser} from the active pane's folder, then the route a Cmd-click takes, at no line. */
    void openFile() { fileChooser.apply(java.util.Optional.ofNullable(directory())).ifPresent(file -> onOpenFile.accept(file, 0)); }
    void requestQuit() { quit.run(); }

    CommandRegistry commands() { return commands; }
    /** The application's theme controller, shared by every window. */
    ThemeController themes() { return themes; }
    ScopeRegistry scopes() { return scopes; }
    CommandsScope commandsScope() { return commandsScope; }
    void dispatchCommand(Command command) {
        command.action().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, command.id()));
    }
    /** The shortcut that opens the palette, shown in the title search field. */
    String paletteShortcut() {
        return CommandsScope.shortcutText(action(ActionId.COMMAND_PALETTE).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    /** {@code id}'s shortcut as this platform writes it, from the live keymap; "" while it has none. */
    String shortcutText(ActionId id) {
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        ActionId id = ActionId.valueOf("SELECT_TAB_" + (index + 1));
        return CommandsScope.shortcutText(action(id).getValue(Action.ACCELERATOR_KEY), macOs);
    }
    /** Select Parent Tab {@code index + 1}'s shortcut as this platform writes it; "" past 9 or while it has none. */
    String parentTabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        return shortcutText(ActionId.valueOf("SELECT_PARENT_TAB_" + (index + 1)));
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
    /**
     * The dock work area in {@code target}'s coordinates: between the stripes, above the bottom strip and the status bar
     * (spec 11.5, the Run window's home); null while the area is not in {@code target}'s root. Plan 7 mounts the bottom
     * strip under the area, so the area's own bounds end above it.
     */
    java.awt.Rectangle paletteWorkArea(Component target) {
        if (area.getParent() == null || SwingUtilities.getRootPane(area) != SwingUtilities.getRootPane(target)) return null;
        return SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), target);
    }
    WindowChrome chrome() { return chrome; }
    WorkspaceChrome look() { return look; }
    /** The application's one animations flag: the look's motion folded into the setting (spec 10.9). EDT. */
    boolean lookAnimates() { return themes.animationsEnabled(); }
    /** Whether the palette is open; the run item shows it (spec 10.6). Null-safe while the window is being built. */
    boolean paletteOpen() { return commandPalette != null && commandPalette.isOpen(); }
    /** The palette opened or closed: the run item repaints. */
    void paletteChanged() { var run = chrome.status().runItem(); if (run != null) run.paletteChanged(); }
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

    /** @return this window's role: the main window or a child window (workspace tabs spec 6) */
    public WindowRole role() { return role; }

    /** @return whether this is the main window, which has Jasper's chrome and dock and whose workspace may be empty */
    public boolean isMain() { return role == WindowRole.MAIN; }

    /** Connects this window to the application-wide terminal registry, once. {@code toFront} raises the native window. */
    public void connectTerminals(TerminalRegistry registry, Runnable toFront) {
        if (closed || terminals != null) return;
        bringToFront = toFront;
        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
        // A child window has no toolbar, so it builds no toolbar views (workspace tabs spec 6.3).
        if (role == WindowRole.MAIN) chrome.windowOpened();
    }

    /** This window's terminal tabs: every parent tab's, in bar order. */
    public java.util.List<TerminalContent> terminalTabs() {
        var result = new ArrayList<TerminalContent>();
        for (Tab tab : tabs()) if (tab.content() instanceof TerminalContent terminal) result.add(terminal);
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
     * the model but never registered as a place where plugin content opens, so the tab registry cannot choose it as
     * the main window: the warm-up rehearsal is such a window, and content opened in it would vanish when it closes.
     */
    public void connectContributions(Contributions model, UiState state, boolean hostsContent) {
        if (closed || contributed != null) return;
        uiState = Objects.requireNonNull(state);
        railVisible = state.railVisible();
        // Restore before contributions can build or show saved panels.
        if (role == WindowRole.MAIN) setZenMode(state.zenMode());
        contributed = new WindowContributions(this, Objects.requireNonNull(model), state);
        if (hostsContent) {
            // The window joins the application's registry, taking the tabs it already holds along (workspace plan 3a Rulings 3).
            TabRegistry shared = model.tabs();
            shared.adopt(registry, id);
            host.close();
            registry = shared;
            host = registry.addHost(workspaceHost());
        }
        rebind();
    }

    /**
     * The registry-facing side of this window's workspace (workspace tabs spec 9.1): its groups are every parent tab's, so
     * a tab placed beside a group lands in that group's parent tab (workspace plan 7a Rulings 8).
     */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            parentTabs::groupOf,
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side, false));
    }

    /**
     * Shows a tab in this window, at the end of the shown parent tab's active group and selected: one the registry
     * placed here, or one this window opened. A failure leaves nothing mounted, takes back what the terminal registry
     * heard, and propagates.
     */
    private void attach(Tab tab) { attach(tab, false, null, null, false); }

    /**
     * As {@link #attach(Tab)}; with {@code opened} a new terminal tab's pane is announced as opened, with {@code beside}
     * the tab opens in a new group on {@code side} of that group, in the parent tab holding it, and with
     * {@code parentTab} it opens in a new parent tab of its own (parent tabs spec 8).
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side, boolean parentTab) {
        JComponent component = tab.component();
        boolean announced = false;
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            if (terminals != null) { terminals.tabArrived(tab, tab.movingFrom()); announced = true; }
            tabHost.attach(tab, opened);
            if (parentTab) parentTabs.openParentTab(tab);
            // A tab a move between windows brings in lands in the parent tab chosen for it (workspace plan 7b Rulings 6).
            else if (beside == null && landing != null && tab.moving()) parentTabs.land(tab, landing.parentTab(), landing.index());
            else if (beside == null) workspace().open(tab);
            else parentTabs.holding(beside).orElseThrow(() -> new IllegalArgumentException("Group " + beside + " is not in this window"))
                .openBeside(tab, beside, side);
            // A tab that moves in ends its parent tab's maximize; one that opens here does not (spec 15).
            if (tab.moving()) parentTabs.holding(tab).ifPresent(WorkspaceView::unmaximize);
        } catch (RuntimeException | Error failure) {
            var listener = tabListeners.remove(tab);
            if (listener != null) listener.close();
            // A new tab's pane closes while it still reports here, so its announcement is closed again (Rulings 28).
            if (opened && tab.content() instanceof TerminalContent terminal) {
                try { terminal.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            }
            tabHost.detach(tab);
            // Plugins must not believe the tab is here (workspace plan 3b Rulings 5).
            if (announced && terminals != null) terminals.tabWithdrawn(tab, tab.movingFrom());
            parentTabs.remove(tab);
            WorkspaceTheme.clear(component);
            throw failure;
        }
        // After the mount stands: a failing glass handler must not roll it back.
        if (backgroundOpacity < 1) glassListener.run();
    }

    /**
     * Takes a tab out of this window once it has closed (the registry disposes it first) or as it moves to another window:
     * a closing tab is announced closed here, and the window closes when its last tab has left. A tab whose attach failed
     * has nothing to take out.
     */
    private void detach(Tab tab) {
        var listener = tabListeners.remove(tab);
        if (listener == null) return;
        listener.close();
        tabHost.detach(tab);
        if (terminals != null && !tab.moving()) terminals.tabClosed(tab);
        if (closed) return;
        // A tab that moves out ends its parent tab's maximize; closing a tab keeps it while its group stands (spec 15).
        if (tab.moving()) parentTabs.holding(tab).ifPresent(WorkspaceView::unmaximize);
        // A parent tab goes with its last tab, except the window's only one (parent tabs spec 4).
        parentTabs.remove(tab);
        WorkspaceTheme.clear(tab.component());
        update();
        if (backgroundOpacity < 1) glassListener.run();
        // A move empties a window only once it has reported itself: the window manager closes it then (TabRegistry.Windows).
        if (parentTabs.isEmpty() && !tab.moving()) onEmpty.run();
    }

    /**
     * A parent tab's layout changed, or the window's parent tabs did (one opened, closed or shows now): the model takes
     * {@code view}'s layout, which is saved, the registry hears the selection, the palette a new active tab; the active
     * tab takes focus when {@code refocus} and {@code view} is the shown parent tab's.
     */
    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        // Every parent tab's layout change and every parent-tab change ends here (workspace plan 6a Rulings 8 and 28): the
        // save is asked for once the model has it, so a listener below that throws costs no save.
        layoutChanged();
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus && view == parentTabs.active()) focusSelected();
    }

    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }

    /** The selected plugin tab; null while a terminal tab is selected or the window is empty. */
    public Tab selectedContent() { Tab tab = selectedTab(); return tab != null && tab.kind() == Tab.Kind.HOSTED ? tab : null; }
    /** The shown parent tab's active group's selected tab; null in an empty workspace. */
    Tab selectedTab() { return parentTabs.active().selected(); }
    /** This window's tabs: its parent tabs in bar order, each one's tabs in workspace order. */
    java.util.List<Tab> tabs() { return parentTabs.tabs(); }
    int tabCount() { return parentTabs.tabCount(); }
    /** The active group's tab count. */
    int groupTabCount() { return workspace().activeGroupView().tabs().size(); }
    /** The tab showing {@code component}; null when none of this window's does. */
    Tab tab(Component component) {
        for (Tab tab : tabs()) if (tab.component() == component) return tab;
        return null;
    }
    /** The shown parent tab's workspace. */
    WorkspaceView workspace() { return parentTabs.active(); }
    /** The window's parent tabs. */
    ParentTabsView parentTabs() { return parentTabs; }

    /**
     * What this window holds (tabs and panes spec 4): its parent tabs with their layouts, and the one that shows.
     *
     * @return the parent tabs
     */
    public ParentTabs windowLayout() { return parentTabs.model(); }

    /** Whether {@code moving} holds tabs and the registry would move every one ({@code TabRegistry.movable}, Rulings 7). */
    private boolean movable(java.util.List<Tab> moving) {
        return !moving.isEmpty() && moving.stream().allMatch(tab -> registry.movable(tab));
    }

    /**
     * Whether Move Tab to New Window may take {@code parentTab}: one of this window's, beside another parent tab, holding
     * tabs that may all move; never without a window manager (tabs and panes spec 6; workspace plan 7b Rulings 7, plan 8a
     * Rulings 6).
     */
    boolean mayMoveTabToNewWindow(ParentTabId parentTab) {
        if (closed || manager == null || parentTab == null) return false;
        Optional<WorkspaceView> view = parentTabs.view(parentTab);
        return view.isPresent() && parentTabs.count() > 1 && movable(view.get().tabs());
    }

    /**
     * Whether Move to Main Window may take this window's tabs: a full window's that the window manager opened, every one
     * of which may move (Rulings 7).
     */
    boolean mayMoveToMainWindow() { return !closed && manager != null && !isMain() && movable(tabs()); }

    /**
     * Takes {@code moving}, other windows' tabs, into this window's parent tab {@code parentTab} through the tab registry,
     * one move each, so each keeps its content and session and reports {@code TAB_MOVED} once (parent tabs spec 5;
     * workspace plan 7b Rulings 6). A parent tab this window lacks opens under that id at {@code index} of the bar, in
     * place of the window's empty workspace (the main window's, or a new full window's). That parent tab then takes
     * {@code arranged} ({@link WorkspaceView#rearrange}) and shows, and {@code arranged}'s active tab is selected, focused
     * and raised. A tab the registry does not move stays where it was; the first failure propagates once the others have
     * moved. While such a tab stays in its window (a listener pinned it mid-move, or its move failed), the tabs that
     * arrived keep the places they landed in, in that parent tab's own panes, and {@code arranged}, whose ids that window
     * still holds, is not applied: no pane is in two windows (parent tabs spec 4). A receive that runs within another
     * one, from a listener say, leaves the outer one's landing as it was.
     *
     * @param moving the tabs, in the order they move
     * @param parentTab the parent tab they land in
     * @param index where a new parent tab goes in the bar
     * @param arranged the parent tab's layout once they are all in
     * @return whether a tab moved in
     */
    boolean receive(java.util.List<Tab> moving, ParentTabId parentTab, int index, Layout arranged) {
        if (closed) return false;
        Throwable failure = null;
        Landing outer = landing;
        landing = new Landing(parentTab, index);
        try {
            for (Tab tab : moving) {
                try {
                    registry.move(tab, id);
                } catch (RuntimeException | Error thrown) {
                    failure = WindowManager.keep(failure, thrown);
                }
            }
        } finally {
            landing = outer;
        }
        WorkspaceView view = closed ? null : parentTabs.view(parentTab).orElse(null);
        boolean arrived = view != null && moving.stream().anyMatch(view::contains);
        boolean leftBehind = moving.stream().anyMatch(tab -> !tab.closed() && (view == null || !view.contains(tab)));
        if (arrived) {
            if (!leftBehind) view.rearrange(arranged);
            Tab shown = view.selected();
            if (shown != null) {
                try {
                    registry.select(shown);
                } catch (RuntimeException | Error thrown) {
                    failure = WindowManager.keep(failure, thrown);
                }
            }
        }
        WindowManager.rethrow(failure);
        return arrived;
    }
    /** The parent-tab bar's strip (parent tabs spec 7). */
    TabStrip parentTabBar() { return parentTabs.bar().strip(); }
    TabRegistry tabRegistry() { return registry; }
    boolean windowActive() { return active; }
    dev.jasper.app.config.AnimationSettings animations() { return animations; }
    String tabTitle(Tab tab) {
        return tab.kind() == Tab.Kind.TERMINAL ? tab.title()
            : (tab.dirty() ? "\u25cf " : "") + tab.title() + (tab.detail().isEmpty() ? "" : "  " + tab.detail());
    }
    Icon tabIcon(Tab tab) { return tab.icon(); }
    String tabTooltip(Tab tab) { return tab.kind() == Tab.Kind.TERMINAL ? tabTitle(tab) : tab.location(); }
    /** Whether a terminal tab's shell runs, which its strip entry tells assistive technology. */
    boolean running(Tab tab) {
        return tab.content() instanceof TerminalContent terminal && terminal.pane().running();
    }
    /**
     * A tab's menu (workspace tabs spec 11; tabs and panes spec 6; workspace plan 7b Rulings 15): Rename Tab for a terminal
     * tab, the window menu's entries for this tab's parent tab, Close Tab, Close Others and Close Pane, then the content's
     * commands.
     */
    JPopupMenu tabMenu(Tab tab) {
        var menu = new JPopupMenu();
        if (tab.kind() == Tab.Kind.TERMINAL)
            menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(tab); invoke(ActionId.RENAME_TAB); }));
        for (ActionId id : windowMenuItems()) menu.add(windowItem(id, tab));
        menu.addSeparator();
        menu.add(tabMenuItem("Close Tab", ActionId.CLOSE_TAB, () -> closeTab(tab)));
        var others = tabMenuItem("Close Others", null, () -> closeOthers(tab));
        others.setEnabled(groupTabs(tab).size() > 1);
        menu.add(others);
        menu.add(tabMenuItem("Close Pane", ActionId.CLOSE_PANE, () -> closeGroup(tab)));
        if (!tab.commands().isEmpty()) menu.addSeparator();
        for (var command : tab.commands()) {
            var item = new JMenuItem(command.title()); item.putClientProperty("html.disable", true);
            item.setEnabled(command.enabled()); item.setAccelerator(command.shortcut());
            item.addActionListener(event -> command.run().run());
            menu.add(item);
        }
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * A parent tab's menu in the bar (tabs and panes spec 6; workspace plan 7a Rulings 16, plan 7b Rulings 15): the window
     * menu's entries, a rule, then Close Parent Tab, for the parent tab {@code tab} shows as its active tab.
     */
    JPopupMenu parentTabMenu(Tab tab) {
        var menu = new JPopupMenu();
        parentTabOf(tab).ifPresent(parent -> {
            for (ActionId id : windowMenuItems()) menu.add(windowItem(id, tab));
            if (menu.getComponentCount() > 0) menu.addSeparator();
            menu.add(tabMenuItem("Close Parent Tab", ActionId.CLOSE_PARENT_TAB, () -> closeParentTab(parent)));
        });
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * The window menu's entries (tabs and panes spec 6; workspace plan 8a Rulings 7): Move Tab to New Window in every
     * window, then Move to Main Window in a full window.
     *
     * @return the entries in menu order
     */
    java.util.List<ActionId> windowMenuItems() {
        return isMain() ? java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW)
            : java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TO_MAIN_WINDOW);
    }

    /**
     * The ⋮'s menu (tabs and panes spec 6): {@link #windowMenuItems()}, each enabled as its action is, for the shown
     * parent tab.
     *
     * @return the menu
     */
    JPopupMenu windowMenu() {
        updateActions();
        var menu = new JPopupMenu();
        for (ActionId id : windowMenuItems()) {
            var item = new JMenuItem(action(id));
            item.putClientProperty("html.disable", true);
            menu.add(item);
        }
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /** A menu entry for {@code id}, one of {@link #windowMenuItems()}, acting on {@code tab}'s parent tab or this window. */
    private JMenuItem windowItem(ActionId id, Tab tab) {
        Runnable run = switch (id) {
            case MOVE_TAB_TO_NEW_WINDOW -> () -> moveTabToNewWindow(tab);
            case MOVE_TO_MAIN_WINDOW -> this::moveToMainWindow;
            default -> throw new IllegalArgumentException("Not a window menu entry: " + id);
        };
        var item = tabMenuItem(id.label(), id, run);
        // Enabled only while every tab the entry would move may move (workspace plan 7b Rulings 7).
        item.setEnabled(switch (id) {
            case MOVE_TAB_TO_NEW_WINDOW -> parentTabOf(tab).map(this::mayMoveTabToNewWindow).orElse(false);
            case MOVE_TO_MAIN_WINDOW -> mayMoveToMainWindow();
            default -> false;
        });
        return item;
    }

    /** A tab menu item showing {@code shortcut}'s binding, if any, that runs {@code run}. */
    private JMenuItem tabMenuItem(String title, ActionId shortcut, Runnable run) {
        var item = new JMenuItem(title);
        if (shortcut != null) item.setAccelerator(bindings().strokeFor(shortcut).orElse(null));
        item.addActionListener(event -> run.run());
        return item;
    }
    public void focusSelected() { Tab tab = selectedTab(); if (tab != null) tab.focus(); }
    public boolean mayClose() { return registry.mayClose(() -> registry.inWindow(id)); }

    ToolWindowArea area() { return area; }
    DockState dock() { return dock; }
    DockStripe leftStripe() { return leftStripe; }
    DockStripe rightStripe() { return rightStripe; }
    /** The bottom strip of labelled stripes; empty under the icon rails. */
    java.util.Optional<DockStripe> bottomStrip() { return stripes.bottom(); }
    /** The window's stripes. */
    StripeSet stripes() { return stripes; }
    DockDrag dockDrag() { return dockDrag; }
    /** The tab drag this window's strips start. */
    TabDrag tabDrag() { return tabDrag; }
    /** The window manager that opened this window: its moves to other windows go through it. */
    void manager(WindowManager value) { manager = Objects.requireNonNull(value, "value"); }

    /** The window manager that opened this window, which carries a drag out of it; empty for any other window. */
    Optional<WindowManager> manager() { return Optional.ofNullable(manager); }

    /** Shares the window manager's tab drag; a drag of this window's own ends first. */
    void tabDrag(TabDrag shared) {
        tabDrag.cancel();
        tabDrag = Objects.requireNonNull(shared, "shared");
    }
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
        // A child window has no stripes: its workspace fills the centre surface edge to edge (workspace tabs spec 6.3).
        boolean rails = isMain() && railsEnabled();
        boolean left = rails && !leftStripe.empty(), right = rails && !rightStripe.empty();
        boolean bottom = stripes.bottom().map(strip -> rails && !strip.empty()).orElse(false);
        area.setStripesVisible(left, right);
        boolean bottomChanged = stripes.bottom().map(strip -> strip.isVisible() != bottom).orElse(false);
        if (leftStripe.isVisible() != left || rightStripe.isVisible() != right || bottomChanged) {
            leftStripe.setVisible(left);
            rightStripe.setVisible(right);
            stripes.bottom().ifPresent(strip -> strip.setVisible(bottom));
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
    /** The active group's equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) tabBar(); }
    /** The active group's strip. */
    TabStrip tabBar() { return workspace().activeGroupView().strip(); }
    private void refreshTabs() { parentTabs.refresh(); }

    /** The active tab's terminal; null while a plugin's tab is active or the workspace is empty. */
    public TerminalContent currentTerminal() {
        Tab tab = selectedTab();
        return tab != null && tab.content() instanceof TerminalContent terminal ? terminal : null;
    }
    public TerminalPane currentPane() { return currentTerminal() == null ? null : currentTerminal().pane(); }
    /**
     * Where a tab or window opened from here starts: the current pane's {@link TerminalPane#inheritableDirectory};
     * null, meaning where a new shell starts, when there is no pane or it has no directory of its own.
     */
    Path directory() { return currentPane() == null ? null : currentPane().inheritableDirectory(); }

    /** A tab whose shell starts in {@code directory}; null means where a new shell starts (the user's home). */
    public void newTab(Path directory) { openTab(directory); }

    /**
     * New Tab (parent tabs spec 8): a terminal in a new parent tab at the end of the bar, shown, starting in
     * {@code directory}. The main window's empty workspace takes it in its only parent tab, the one parent tab that may be
     * empty (workspace plan 7a Rulings 7).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal tab; null once the window has closed
     */
    public TerminalContent newParentTab(Path directory) {
        return parentTabs.isEmpty() ? openTab(directory)
            : openTerminal(directory, null, Optional.empty(), Optional.empty(), null, null, true);
    }

    /** Shows the parent tab {@code id} and focuses its active tab (workspace plan 7a Rulings 10); nothing for another window's. */
    void selectParentTab(ParentTabId id) {
        if (closed || parentTabs.view(id).isEmpty()) return;
        parentTabs.select(id);
        focusSelected();
    }

    /** Select Parent Tab 1 to 9: the parent tab at {@code index} of the bar; nothing past the last. */
    void selectParentTabAt(int index) {
        var all = parentTabs.model().parentTabs();
        if (index >= 0 && index < all.size()) selectParentTab(all.get(index).id());
    }

    /** Next and Previous Parent Tab: the parent tab {@code delta} places along the bar, wrapping around. */
    void selectParentTabRelative(int delta) { if (parentTabs.count() > 1) selectParentTab(parentTabs.model().step(delta)); }

    /**
     * Close Parent Tab (parent tabs spec 8): every tab of the parent tab {@code id} as one gesture, after their guards; the
     * parent tab goes with its last tab (spec 4).
     */
    void closeParentTab(ParentTabId id) { parentTabs.view(id).ifPresent(view -> closeTabs(view.tabs())); }

    /** The parent tab holding {@code tab}; empty for a tab this window does not hold. */
    Optional<ParentTabId> parentTabOf(Tab tab) { return parentTabs.holding(tab).map(WorkspaceView::id); }

    /** The shown parent tab. */
    ParentTabId activeParentTab() { return parentTabs.model().active(); }

    /** How many parent tabs this window has. */
    int parentTabCount() { return parentTabs.count(); }

    /**
     * A parent tab dragged along this window's bar (parent tabs spec 5): it takes {@code index} among the others, and
     * the one that shows stays.
     */
    void reorderParentTab(ParentTabId id, int index) { if (!closed) parentTabs.reorder(id, index); }

    TerminalContent openTab(Path directory) { return openTab(directory, null); }

    /** {@code requestOrNull} makes the tab's first pane a provided session instead of a local shell. */
    TerminalContent openTab(Path directory, SessionRequest requestOrNull) { return openTab(directory, requestOrNull, Optional.empty()); }

    /** As above; {@code profile} names a local first pane's shell profile, empty for the user's default. */
    TerminalContent openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile) {
        return openTab(directory, requestOrNull, profile, Optional.empty());
    }

    /** As above; {@code program} runs in the local first pane instead of its shell. */
    TerminalContent openTab(Path directory, SessionRequest requestOrNull, Optional<String> profile, Optional<java.util.List<String>> program) {
        return openTerminal(directory, requestOrNull, profile, program, null, null, false);
    }

    /**
     * A terminal tab starting in {@code directory} (null: where a new shell starts), in the active group, in a new group
     * on {@code side} of {@code beside} when that is given, or in a new parent tab of its own when {@code parentTab}.
     * Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side,
                                         boolean parentTab) {
        if (closed) return null;
        TabId tabId = TabId.random();
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        try {
            attach(tab, true, beside, side, parentTab);
        } catch (RuntimeException | Error failure) {
            // attach mounted nothing; the registered tab and its pane go with the failure (workspace plan 3a Rulings 28).
            try { tab.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        terminal.start();
        return terminal;
    }

    /**
     * Split Right or Split Down (workspace tabs spec 11, workspace plan 3a Rulings 11): a terminal tab in a new group on
     * {@code side} of the active group; beside a running terminal in its directory with its profile, otherwise (beside a
     * plugin's view or a terminal whose shell ended) where a new tab from here starts, with the default profile. On an
     * empty workspace it opens in the empty group. Null once the window has closed.
     */
    public TerminalContent split(Side side) {
        TerminalContent target = currentTerminal();
        if (target != null && target.pane().running())
            return openBeside(target, side, null, null, target.pane().profile(), Optional.empty());
        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace().model().activeGroup(), side, false);
    }

    /**
     * A terminal tab in a new group on {@code side} of {@code target}'s group (spec 9.4), starting in
     * {@code directoryOrNull}, else where {@code target} is; a request makes it a provided session. Null when the target
     * is not this window's or its shell is not running.
     */
    TerminalContent openBeside(TerminalContent target, Side side, Path directoryOrNull, SessionRequest requestOrNull,
                               Optional<String> profile, Optional<java.util.List<String>> program) {
        Tab beside = target == null ? null : tab(target);
        if (closed || beside == null || !target.pane().running()) return null;
        GroupId group = parentTabs.groupOf(beside.id()).orElseThrow();
        Path directory = directoryOrNull == null ? target.pane().inheritableDirectory() : directoryOrNull;
        return openTerminal(directory, requestOrNull, profile, program, group, side, false);
    }

    /** Maximize Pane: the active pane fills the workspace, or the splits come back. */
    void toggleMaximize() { workspace().toggleMaximize(); }

    /** Focus Pane: the neighbouring pane that way takes focus with its selected tab; nothing past the edge. */
    void focusGroup(Side side) {
        WorkspaceView workspace = workspace();
        workspace.neighbour(side).ifPresent(group -> {
            workspace.activate(group);
            focusSelected();
        });
    }

    /** The terminal tab holding {@code pane}; null when none of this window's does. */
    TerminalContent terminalOf(TerminalPane pane) {
        for (TerminalContent terminal : terminalTabs()) if (terminal.pane() == pane) return terminal;
        return null;
    }

    /**
     * Focus went into {@code tab} (a click, a traversal, a focus request): it becomes the window's active tab and its
     * group the active group, while focus stays where it went (workspace plan 3a Rulings 26). Null, or a tab that is
     * already active, changes nothing.
     */
    void tabFocused(Tab tab) {
        // Only the shown parent tab can hold the focus.
        if (!closed && tab != null) parentTabs.holding(tab).filter(view -> view == parentTabs.active()).ifPresent(view -> view.focused(tab));
    }

    /** The tab whose component holds {@code component}; null when none of this window's does. */
    Tab tabHolding(Component component) {
        for (Tab tab : tabs()) if (SwingUtilities.isDescendingFrom(component, tab.component())) return tab;
        return null;
    }

    void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }

    /** This window's configuration, or null before the first one arrives. */
    ConfigSnapshot configured() { return workspaceConfiguration.snapshot(); }

    /** A pane's view is ready: the first in this window runs the start-up timing, once. */
    void terminalConfigured(TerminalPane pane) {
        if (firstTerminalSeen) return;
        firstTerminalSeen = true;
        try {
            firstTerminal.accept(pane);
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The first-terminal observer failed", failure);
        }
    }

    /** Shows the tab holding {@code component}, as a click on its strip entry does. */
    void selectTab(Component component) { selectTab(tab(component)); }

    /** Shows {@code tab}'s parent tab, then the tab in its group, activates the group and focuses the tab. */
    void selectTab(Tab tab) {
        WorkspaceView view = closed ? null : parentTabs.holding(tab).orElse(null);
        if (view == null) return;
        parentTabs.select(view.id());
        view.select(tab);
        focusSelected();
    }

    /** Selects the active group's tab at {@code index}; nothing past its last tab. */
    void selectInGroup(int index) {
        var group = workspace().activeGroupView().tabs();
        if (index >= 0 && index < group.size()) selectTab(group.get(index));
    }

    /** Selects the active group's tab {@code delta} places from the selected one, wrapping around. */
    void selectRelative(int delta) {
        var view = workspace().activeGroupView();
        var group = view.tabs();
        if (group.isEmpty()) return;
        selectTab(group.get(Math.floorMod(Math.max(0, group.indexOf(view.selected())) + delta, group.size())));
    }

    /** Closes the tab holding {@code component}. */
    public void closeTab(Component component) { closeTab(tab(component)); }

    /**
     * Closes {@code tab}: a plugin's tab once its guard allows it; a terminal tab at once, its registry facts published as
     * one change (workspace plan 3a Rulings 4).
     */
    public void closeTab(Tab tab) {
        if (!parentTabs.contains(tab)) return;
        if (tab.kind() == Tab.Kind.HOSTED) { tab.requestClose(); return; }
        if (terminals != null) terminals.atomically(tab::close); else tab.close();
    }

    /** The tabs of {@code tab}'s group, in strip order. */
    private java.util.List<Tab> groupTabs(Tab tab) {
        return parentTabs.holding(tab).flatMap(view -> view.model().groupOf(tab.id()).map(view::groupView))
            .map(group -> java.util.List.copyOf(group.tabs())).orElse(java.util.List.of());
    }

    /** Closes the other tabs of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeOthers(Tab tab) { closeTabs(groupTabs(tab).stream().filter(other -> other != tab).toList()); }

    /** Closes every tab of {@code tab}'s group as one gesture (workspace plan 3a Rulings 14). */
    void closeGroup(Tab tab) { if (tab != null) closeTabs(groupTabs(tab)); }

    /**
     * Closes {@code batch} on one decision: the plugin tabs' guards answer once and a refusal closes nothing; then every
     * tab of the batch closes, terminal tabs included, their registry facts published as one change when the guards
     * answered at once. A batch of terminal tabs alone needs no approval and closes at once, as one change.
     */
    void closeTabs(java.util.List<Tab> batch) {
        var live = batch.stream().filter(parentTabs::contains).toList();
        if (live.isEmpty()) return;
        boolean terminalsOnly = live.stream().allMatch(tab -> tab.kind() == Tab.Kind.TERMINAL);
        Runnable close = terminalsOnly ? () -> registry.removeAll(live) : () -> registry.requestClose(live);
        if (terminals != null) terminals.atomically(close); else close.run();
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
        if (paletteKeys.claimsShortcut(stroke, source == null ? this : source)) return true;
        var document = selectedContent();
        if (document != null) {
            if (source instanceof JTextComponent && nativeContentClipboard(document, stroke)) return false;
            if (document.commands().stream().anyMatch(command -> stroke.equals(command.shortcut()))) return true;
        }
        return shortcutTarget(stroke, source).isPresent();
    }

    private static boolean nativeContentClipboard(dev.jasper.app.contributions.Tab document, KeyStroke stroke) {
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
        Tab selected = selectedTab();
        for (Tab tab : tabs()) {
            // Every tab is active while it is the window's active tab; a terminal's dimming also follows the window.
            if (tab.content() instanceof TerminalContent terminal) terminal.setActive(active && tab == selected);
            tab.active(tab == selected);
        }
        TerminalPane pane = currentPane();
        String size = pane == null || pane.session() == null ? "Starting terminal" :
            pane.session().columns() + " \u00d7 " + pane.session().rows();
        chrome.status().setMetadata(pane == null ? null : pane.snapshot(), pane == null ? "" : size, pane != null && pane.running());
        chrome.status().setContent(selectedContent());
        onTitle.accept(selected == null ? "Jasper" : tabTitle(selected));
        syncContentBindings();
        updateActions(); refreshTabs(); onMinimumSizeChanged.run();
        if (terminals != null) terminals.refresh();
    }

    ResolvedTheme theme() { return themes.current(); }
    boolean followsThemeAppearance() { return followsThemeAppearance; }

    /**
     * Gives a dialog of this window the window's macOS appearance when the window follows the theme (spec 10.1): an
     * option pane's dialog is a window of its own, which otherwise takes the system's appearance.
     */
    void followAppearance(JRootPane dialogRoot) {
        if (followsThemeAppearance) dev.jasper.app.platform.MacAppearance.follow(dialogRoot, theme().chrome().dark());
    }

    /** Shows {@code pane} in a modal dialog over this window, as {@code JOptionPane}'s own show methods do, with its appearance. */
    private void showOptionDialog(JOptionPane pane, String title) {
        JDialog dialog = pane.createDialog(this, title);
        followAppearance(dialog.getRootPane());
        pane.selectInitialValue();
        dialog.setVisible(true);
        dialog.dispose();
    }

    /** Rename Tab's question: the new name, blank for automatic, or null when cancelled. */
    String askTabName(String current) {
        String message = "Tab name (leave blank for automatic):";
        if (!followsThemeAppearance) return JOptionPane.showInputDialog(this, message, current);
        // As showInputDialog does, with the window's appearance on its dialog.
        var pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        pane.setInitialSelectionValue(current);
        showOptionDialog(pane, UIManager.getString("OptionPane.inputDialogTitle", getLocale()));
        Object value = pane.getInputValue();
        return value == JOptionPane.UNINITIALIZED_VALUE ? null : (String) value;
    }

    /**
     * Slate Cobalt's Dark ({@code dark}) or Light variant in every window sharing this window's themes, as
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
        choose(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME, scheme.key(), () -> {
            themes.configureScheme(scheme);
            chrome.refreshTheme();
        });
    }

    boolean terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice scheme) { return themes.schemeChoice().equals(scheme); }

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
        if (updateDelegates) SwingUtilities.updateComponentTreeUI(bindingRoot == null ? this : bindingRoot);
        if (updateDelegates && (bindingRoot == null || menuBar().getParent() == null)) SwingUtilities.updateComponentTreeUI(menuBar());
        for (DockStripe stripe : stripes.all()) stripe.refreshTheme();
        area.refreshTheme();
        area.setTerminalBackground(theme.surface());
        // A group the maximize keeps out of the tree updates its delegates there; split positions follow the weights.
        parentTabs.applyTheme(theme, updateDelegates);
        for (Tab tab : new ArrayList<>(tabListeners.keySet()))
            if (tab.kind() == Tab.Kind.HOSTED) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));
        if (updateDelegates && contributed != null) contributed.refreshTheme();
        for (TerminalContent terminal : terminalTabs()) {
            WorkspaceTheme.apply(terminal, theme);
            terminal.setBackground(theme.surface());
            terminal.pane().applyTheme(theme);
        }
        setBackground(theme.surface());
        chrome.refreshTheme();
        if (commandPalette != null) commandPalette.refreshTheme();
        onThemeChanged.accept(theme); update();
        requestGlass();
        revalidate(); repaint();
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
        parentTabs.setGlass(glass);
        for (TerminalContent terminal : terminalTabs()) terminal.setBackgroundOpacity(value);
        parentTabs.refresh();
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
        return component != null && SwingUtilities.isDescendingFrom(component, parentTabs) ? backgroundOpacity : 1;
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
        for (DockStripe stripe : stripes.all()) stripe.setRailWidth(width);
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }

    void setAnimations(dev.jasper.app.config.AnimationSettings settings) {
        if (closed) return;
        animations = settings;
        parentTabs.setAnimations(settings);
        commandPalette.setAnimations(settings);
    }

    void setTabHeight(int height) {
        requireSize("Tab height", height, MIN_TAB_HEIGHT, MAX_TAB_HEIGHT);
        if (closed || tabHeight == height) return;
        tabHeight = height;
        parentTabs.revalidateStrips();
        onTabHeightChanged.run();
        revalidate(); repaint();
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) { root.revalidate(); root.repaint(); }
        onMinimumSizeChanged.run();
    }

    void setActive(boolean value) {
        if (!value && commandPalette != null) commandPalette.windowDeactivated();
        // A modal dialog opening takes the drag's release, and so does another application taking focus.
        if (!value) { dockDrag.cancel(); tabDrag.cancelFrom(this); }
        if (classicWindowTitle != null) classicWindowTitle.setActive(value);
        active = value;
        if (childTitleColors != null) childTitleColors.run();
        update();
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
        focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
        if (bodyLease != null) { bodyLease.close(); bodyLease = null; }
        dockDrag.cancel();
        tabDrag.cancelFrom(this);
        paletteKeys.close();
        if (contributed != null) { contributed.close(rememberLayout); contributed = null; }
        area.close();
        commandPalette.close(); windowCommands.close(); commands.close();
        scopes.close();
        closed = true;
        syncPaletteDispatcher();
        unregisterConfiguration.run(); disconnectConfiguration();
        showConfigDiagnostics = control -> {};
        uninstallCornerLayer();
        if (cornerLayer != null) { cornerLayer.removePropertyChangeListener("preferredSize", cornerResized); cornerLayer = null; }
        themeRegistration.close();
        // Leaving the registry removes the tabs this window still holds: each closes and is announced closed.
        java.util.List<Tab> held = registry.inWindow(id);
        Throwable leaving = null;
        try { if (terminals != null) terminals.atomically(host::close); else host.close(); }
        catch (RuntimeException | Error failure) { leaving = failure; }
        for (Tab tab : held) WorkspaceTheme.clear(tab.component());
        parentTabs.close(); removeRootBindings();
        if (terminals != null) { terminals.close(); terminals = null; }
        workspaceActions.disable();
        refreshTabs();
        if (titleBand != null) { titleBand.close(); titleBand = null; }
        onThemeChanged = theme -> {};
        childTitleColors = null;
        onTabHeightChanged = () -> {};
        onTitle = title -> {}; onError = (title, details) -> {}; onInfo = (title, details) -> {}; onLaunchFailure = (details, cause) -> {}; onMinimumSizeChanged = () -> {};
        onToggleBuddy = () -> {}; buddyEnabled = () -> false; onOpenFile = (file, line) -> {}; fileChooser = folder -> java.util.Optional.empty();
        onLayoutChanged = () -> {};
        activityListeners.clear();
        anyWindowActive = () -> false;
        // A tab that failed to close does not leave the window half closed; its failure still reaches the caller.
        if (leaving instanceof Error error) throw error;
        if (leaving != null) throw (RuntimeException) leaving;
    }
}
