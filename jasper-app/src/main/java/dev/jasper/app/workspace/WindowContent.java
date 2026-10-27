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
import dev.jasper.app.contributions.OtherTab;
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
import dev.jasper.app.persistence.WorkspaceFile;
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
import dev.jasper.app.config.TitleTemplate;
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
    /** Each tab this window shows, with its change listener. */
    private final Map<Tab, Subscription> tabListeners = new IdentityHashMap<>();
    /** This window's side of its terminal tabs (workspace tabs spec 9.3). */
    private final TabHost tabHost = new TabHost(this);
    /** Where focus goes: focus going into a plugin's view, or a pane, makes its tab the active tab (Rulings 26). */
    private final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    private final java.beans.PropertyChangeListener focusOwners = event -> {
        if (event.getNewValue() instanceof Component focused) tabFocused(tabHolding(focused));
    };
    /**
     * A left press on a pane's item makes its pane the active one, a view that takes no focus too (tabs and panes spec 5;
     * workspace plan 8a Rulings 21). With the pane drag's modifier ({@link TabDrag#movesPane}) the press and its click are
     * the pane drag's (spec 7; workspace plan 8c Rulings 2): consumed before the item hears them, the press handing the
     * drag its gesture and focusing the item, as any press would, its pane already the active one or not (Rulings 14).
     */
    private final AWTEventListener paneClicks = event -> {
        if (closed() || !(event instanceof MouseEvent press) || !SwingUtilities.isLeftMouseButton(press)
            || !(press.getSource() instanceof Component source)) return;
        int id = press.getID();
        if (id != MouseEvent.MOUSE_PRESSED && id != MouseEvent.MOUSE_CLICKED) return;
        Tab tab = tabHolding(under(source, press.getPoint()));
        if (tab == null || parentTabs().holding(tab).filter(view -> view == parentTabs().active()).isEmpty()) return;
        if (TabDrag.movesPane(press, macOs())) {
            press.consume();
            if (id == MouseEvent.MOUSE_PRESSED) {
                // A dock drag still running lost its release: it ends first, as a press on a strip's entry ends it.
                dockDrag().cancel();
                tabDrag().pressedPane(this, tab, press);
                // The item hears no press that would focus it: the window does.
                if (tab != selectedTab()) selectTab(tab);
                else focusSelected();
            }
            return;
        }
        if (id == MouseEvent.MOUSE_PRESSED && tab != selectedTab()) selectTab(tab);
    };
    /** Where this window's tabs live: a registry of its own until it joins the application's (connectContributions). */
    private TabRegistry registry = new TabRegistry();
    /** This window's workspace in {@link #registry}; closing it removes the tabs the window still holds. */
    private Subscription host;
    private Runnable bringToFront = () -> {};
    /** The window manager that opened this window, which moves its tabs to other windows (tabs and panes spec 6); else null. */
    private WindowManager manager;
    /**
     * The window's tabs, each holding panes in splits, under the window's one strip (workspace tabs spec 10; parent tabs
     * spec 4), where the terminal column stood.
     */
    private final ParentTabsView parentTabs;
    /** Where tabs moving in land while {@link #receive} runs: a parent tab, and its place in the bar when it is new. */
    private record Landing(ParentTabId parentTab, int index) { }
    private Landing landing;
    /** Pane moves between this window's tabs under way ({@link #held}): only where the last one ends is announced. */
    private int holding;
    /** Whether a change during a held move asked for the focus. */
    private boolean heldRefocus;
    /** The configured tab motion, which the window's strip takes. */
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
    /** The detached tool windows' root bindings, rebound with this window's root ({@link #bindDetachedRoot}). */
    private final java.util.List<DetachedKeys> detachedKeys = new ArrayList<>();
    private WindowContributions contributed;
    /** Makes a detached tool window's native window; null until given (headless), and detached windows wait. */
    private java.util.function.BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events, DetachedToolWindows.Shell> detachedShells;
    /** Where this window's tool windows dock: its contributions change it, its area lays it out. */
    private final DockState dock = new DockState();
    private final ToolWindowArea area;
    /** The window's stripes: icon rails, or labelled stripes and the bottom strip (spec 10.7). */
    private final StripeSet stripes;
    private final DockStripe leftStripe, rightStripe;
    /** Drags a tool window from its stripe button or its header to another slot. */
    private final DockDrag dockDrag;
    /**
     * Drags a tab from the strip, or a pane by its modifier (tabs and panes spec 6 and 7): the window manager's, which
     * every window it opens shares; until it gives that, one of this window's own, which moves tabs and panes within this
     * window and nothing out of it, as a move to another window needs a manager ({@link #mayMoveTabToNewWindow}).
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
    /**
     * How a plugin's saved item comes back in this window (workspace tabs spec 13): its plugin's restorer rebuilds it with
     * the saved id, else it is a placeholder; empty leaves it out, as closing it would. The window manager gives it.
     */
    java.util.function.BiFunction<TabId, WorkspaceFile.TabRecord.Content, Optional<Tab>> savedTabs = (tabId, saved) -> Optional.empty();
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
    /** Native showing/non-iconified state used only for Buddy attention. */
    private boolean buddyWindowVisible;
    Consumer<ResolvedTheme> onThemeChanged = theme -> {};
    public Runnable onManageCygwinPackages;
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
            publishPresentation(listener);
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


    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit) {
        this(launcher, directory, newWindow, quit, new ThemeController(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, ThemeController themes) {
        this(launcher, directory, newWindow, quit, themes, KeyBindings.defaults(System.getProperty("os.name").startsWith("Mac")));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, ThemeController themes,
                  KeyBindings bindings) {
        this(launcher, directory, newWindow, quit, themes, bindings, new CommandHistory(), System.getProperty("os.name").startsWith("Mac"));
    }

    WindowContent(ShellLauncher launcher, Path directory, Consumer<Path> newWindow, Runnable quit, ThemeController themes,
                  KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, directory == null, newWindow, quit, themes, bindings, history, macOs);
    }

    /**
     * An {@code empty} window starts with no tab; any other starts one tab in {@code directory}, where null means where a
     * new shell starts. {@code newWindow} receives null the same way.
     */
    WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow, Runnable quit,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, empty, newWindow, quit, themes, bindings, history, macOs, WorkspaceChrome.STANDARD);
    }

    /**
     * A window's content (full windows spec 5.1): Jasper's chrome around its workspace, which its last tab leaves empty
     * rather than closed, with its actions, menus, keymap, palette and dock.
     *
     * @param launcher starts the window's shells
     * @param directory where the first tab starts, or null for where a new shell starts
     * @param empty whether the window starts without a tab
     * @param newWindow opens a window with a terminal tab in a directory, or where a new shell starts when it is null
     * @param quit quits Jasper
     * @param themes the application's look
     * @param bindings the keymap until a configuration arrives
     * @param history the shared command history
     * @param macOs whether the keymap and shortcut text are macOS's
     * @param look the workspace's chrome under the look
     */
    public WindowContent(ShellLauncher launcher, Path directory, boolean empty, Consumer<Path> newWindow, Runnable quit,
                         ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look) {
        super(new BorderLayout());
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
        this.launcher = launcher; this.newWindow = newWindow; this.quit = quit;
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
        area = new ToolWindowArea(parentTabs, dock, themes.kit(), look().dock().flushFrames(), look().chromeArtwork());
        area.setStatusVisible(chrome.status().isVisible());
        area.setHeaderVisible(true);
        for (DockStripe stripe : stripes.all()) stripe.setVisible(false);

        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        stripes.bottom().ifPresent(strip -> body.add(strip, BorderLayout.SOUTH));
        dockDrag = new DockDrag(this, dock, area, stripes);
        dockDrag.railsEnabled = this::railsEnabled;
        // Over another of Jasper's windows in front of this one a dock drag detaches, as outside it. The window manager
        // gives this window its shared tab drag later, so the query asks the tab drag of the moment.
        dockDrag.covered = screen -> tabDrag().behind(this, screen);
        for (DockStripe stripe : stripes.all()) stripe.dragSource = dockDrag::install;
        area.dragSource = dockDrag::install;
        area.releaseDragSource = dockDrag::uninstall;
        // A dock change moves the window's minimum size; read the hook when it fires, as the frame sets it later.
        area.onMinimumSizeChanged = () -> onMinimumSizeChanged.run();
        add(chrome.header(), BorderLayout.NORTH); add(body); add(chrome.status(), BorderLayout.SOUTH);
        themeRegistration = themes.subscribe(this::applyTheme);
        host = registry.addHost(workspaceHost());
        focusManager.addPropertyChangeListener("permanentFocusOwner", focusOwners);
        Toolkit.getDefaultToolkit().addAWTEventListener(paneClicks, AWTEvent.MOUSE_EVENT_MASK);
        if (!empty) {
            try { newTab(directory); }
            catch (RuntimeException | Error failure) {
                // The theme controller and the focus manager outlive a window that failed to build.
                try { themeRegistration.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                try { windowCommands.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                focusManager.removePropertyChangeListener("permanentFocusOwner", focusOwners);
                Toolkit.getDefaultToolkit().removeAWTEventListener(paneClicks);
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
     * window or of the Windows title bar. Every window's frame and the chrome renders share this path. Returns the
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

    /** The actions whose keys hide a detached tool window instead (floating tool windows spec, The detached window). */
    private static final Set<String> CLOSE_KEYS = Set.of(ActionId.CLOSE_PANE.id(), ActionId.CLOSE_TAB.id(), ActionId.CLOSE_WINDOW.id());

    /**
     * Binds every keymap stroke on the root pane of one of this window's detached tool windows (floating tool
     * windows spec, The detached window), each resolved from the focus owner as on this window's root: a stroke that
     * resolves to Close Pane, Close Tab or Close Window runs {@code hide} unless the window is recording shortcuts, and
     * closes nothing of this window; while this window's modal overlay is open it holds every other key; otherwise a
     * Panel-context action of the panel holding the focus runs in place and any other action raises this window and
     * runs in it. The bindings follow keymap changes until the returned handle closes, which removes exactly them; this
     * window's close does too. EDT.
     */
    AutoCloseable bindDetachedRoot(JRootPane root, Runnable hide) {
        if (closed) return () -> { };
        var keys = new DetachedKeys(Objects.requireNonNull(root, "root"), Objects.requireNonNull(hide, "hide"));
        detachedKeys.add(keys);
        keys.bind();
        return keys;
    }

    /** One detached root's bindings ({@link #bindDetachedRoot}): one per stroke, as on this window's root. */
    private final class DetachedKeys implements AutoCloseable {
        private final JRootPane root;
        private final Runnable hide;
        private final Map<KeyStroke, DetachedShortcut> bound = new HashMap<>();

        DetachedKeys(JRootPane root, Runnable hide) { this.root = root; this.hide = hide; }

        /** Binds the keymap's strokes as they are now, in place of those bound before. */
        void bind() {
            unbind();
            bindings.strokes().forEach((actionId, stroke) -> {
                if (bound.containsKey(stroke)) return;
                var action = new DetachedShortcut(actionId, stroke, root, hide);
                bound.put(stroke, action);
                root.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(stroke, actionId);
                root.getActionMap().put(actionId, action);
            });
        }

        /** Removes each binding still its own, leaving any the root's window made since. */
        void unbind() {
            InputMap input = root.getInputMap(WHEN_IN_FOCUSED_WINDOW);
            bound.forEach((stroke, action) -> {
                if (action.key.equals(input.get(stroke))) input.remove(stroke);
                if (root.getActionMap().get(action.key) == action) root.getActionMap().remove(action.key);
            });
            bound.clear();
        }

        @Override public void close() { if (detachedKeys.remove(this)) unbind(); }
    }

    /** A detached root's action for one stroke: it takes the key only when it hides the tool window or dispatch would use it. */
    private final class DetachedShortcut extends AbstractAction {
        private final String key;
        private final KeyStroke stroke;
        private final JRootPane root;
        private final Runnable hide;

        DetachedShortcut(String key, KeyStroke stroke, JRootPane root, Runnable hide) {
            this.key = key; this.stroke = stroke; this.root = root; this.hide = hide;
        }

        @Override public boolean accept(Object sender) {
            if (!isEnabled()) return false;
            Component focus = focusOwner();
            return closesDetached(stroke, focus) || dev.jasper.app.platform.WindowInput.blocked(WindowContent.this)
                || claimsShortcut(stroke, focus);
        }

        @Override public void actionPerformed(ActionEvent event) {
            Component focus = focusOwner();
            if (closesDetached(stroke, focus)) {
                if (!dev.jasper.app.platform.WindowInput.captureShortcut(root, stroke)) hide.run();
                return;
            }
            // This window's modal overlay holds every other key, as it holds the key of a contributed action run from anywhere.
            if (dev.jasper.app.platform.WindowInput.blocked(WindowContent.this)) return;
            if (!panelShortcut(stroke, focus)) bringToFront.run();
            dispatchShortcut(stroke, focus);
        }
    }

    /** Whether a stroke from a detached window's {@code focus} resolves to Close Pane, Close Tab or Close Window. */
    private boolean closesDetached(KeyStroke stroke, Component focus) {
        return bindings.resolve(stroke, context -> applies(context, focus)).filter(CLOSE_KEYS::contains).isPresent();
    }

    /** Whether a stroke from {@code focus} resolves to a Panel-context action, of the panel holding the focus. */
    private boolean panelShortcut(KeyStroke stroke, Component focus) {
        return bindings.resolve(stroke, context -> applies(context, focus))
            .filter(id -> bindings.contextFor(id).kind() == KeyContext.Kind.PANEL).isPresent();
    }

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
     * takes it, such as a Ctrl+Tab binding in a text area, a table or a field, and Next and Previous Tab before the focus
     * owner's own bindings, such as a scroll pane's Ctrl+PageUp and Ctrl+PageDown (workspace plan 8a Rulings 16); it
     * swallows that key's typed and released tail wherever focus went. Plain Tab is no Keymap binding, so it still
     * traverses.
     */
    private boolean traversalShortcut(KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_RELEASED -> { if (!traversalHeld.remove(event.getKeyCode())) return false; }
            case KeyEvent.KEY_TYPED -> { if (!traversalTyped) return false; traversalTyped = false; }
            case KeyEvent.KEY_PRESSED -> {
                traversalTyped = false;
                Component focus = event.getComponent();
                KeyStroke stroke = KeyStroke.getKeyStrokeForEvent(event);
                if (!ownsKeySource(focus) || !(traversalKey(focus, event) || tabKey(stroke))
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

    /** Whether {@code stroke} is Next or Previous Tab's, which run before the focus owner's own bindings. */
    private boolean tabKey(KeyStroke stroke) {
        String bound = rootKeys.get(stroke);
        return ActionId.NEXT_TAB.id().equals(bound) || ActionId.PREVIOUS_TAB.id().equals(bound);
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
        for (DetachedKeys keys : detachedKeys) keys.bind();
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
    /** New Window (full windows spec 5.2): a new window with a terminal tab where a tab from here starts. */
    void requestNewWindow() { newWindow.accept(directory()); }
    /** Close Window (workspace tabs spec 6.4 and 11): this window, as its title bar's close button closes it. */
    void requestCloseWindow() { onCloseWindow.run(); }
    /**
     * Move Tab to New Window (tabs and panes spec 6): the parent tab holding {@code tab}, through the window manager;
     * nothing in a window outside one.
     */
    void moveTabToNewWindow(Tab tab) {
        if (manager != null && tab != null) parentTabOf(tab).ifPresent(parentTab -> manager.moveTabToNewWindow(this, parentTab));
    }
    /**
     * The Move Tab to Window action from the keyboard or the palette, which cannot choose: the shown parent tab moves to
     * the other window that was active last.
     */
    void moveToOtherWindow() {
        otherWindows(true).stream().findFirst().ifPresent(to -> manager.moveTabToWindow(this, activeParentTab(), to));
    }

    /**
     * Move Tab to Window (full windows spec 5.3): {@code parentTab} moves to just after {@code to}'s active parent tab,
     * through the window manager.
     */
    private void moveTabToWindow(ParentTabId parentTab, WindowContent to) {
        if (!closed && manager != null) manager.moveTabToWindow(this, parentTab, to);
    }

    /**
     * The open windows other than this one, in opening order, or the one active last first; empty outside a window
     * manager.
     */
    private java.util.List<WindowContent> otherWindows(boolean byActivation) {
        if (closed || manager == null) return java.util.List.of();
        return (byActivation ? manager.windowsByActivation() : manager.windows()).stream().map(JasperWindow::content)
            .filter(other -> other != this && !other.closed()).toList();
    }
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
    /** Select Tab {@code index + 1}'s shortcut as this platform writes it; "" past 9 or while it has none. */
    String tabShortcut(int index) {
        if (index < 0 || index >= 9) return "";
        return shortcutText(ActionId.valueOf("SELECT_TAB_" + (index + 1)));
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
    /** Whether the keymap and shortcut text are macOS's: the pane drag's modifier follows it (workspace plan 8c Rulings 1). */
    boolean macOs() { return macOs; }
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

    private final java.util.List<Runnable> afterPaint = new ArrayList<>();

    /**
     * Whether no terminal shows in this window now, so none paints the window's first output (workspace tabs spec 13):
     * no pane of the tab it shows that shows (the maximized one alone while one is maximized) holds a terminal, or the
     * workspace is empty. A hidden tab's terminal shows nothing.
     *
     * @return true when no shown pane holds a terminal
     */
    public boolean showsNoTerminal() {
        WorkspaceView workspace = workspace();
        Layout layout = workspace.model();
        java.util.List<dev.jasper.app.layout.Group> shown = layout.maximized().flatMap(layout::group).map(java.util.List::of)
            .orElseGet(layout::groups);
        for (dev.jasper.app.layout.Group group : shown)
            if (group.selected().flatMap(workspace::tab).filter(tab -> tab.content() instanceof TerminalContent).isPresent()) return false;
        return true;
    }

    /**
     * Runs {@code task} once, after this window's content next paints. EDT.
     *
     * @param task what runs after the paint
     */
    public void afterNextPaint(Runnable task) {
        afterPaint.add(Objects.requireNonNull(task, "task"));
        repaint();
    }

    @Override public void paint(Graphics graphics) {
        super.paint(graphics);
        if (afterPaint.isEmpty()) return;
        var tasks = java.util.List.copyOf(afterPaint);
        afterPaint.clear();
        for (Runnable task : tasks) task.run();
    }

    /** Stable identity of this window for extensions; never a Swing object. */
    public UUID id() { return id; }

    /** Connects this window to the application-wide terminal registry, once. {@code toFront} raises the native window. */
    public void connectTerminals(TerminalRegistry registry, Runnable toFront) {
        if (closed || terminals != null) return;
        bringToFront = toFront;
        terminals = new WindowTerminals(this, Objects.requireNonNull(registry), Objects.requireNonNull(toFront));
        chrome.windowOpened();
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
     *
     * As {@link #connectContributions(Contributions, UiState)}. A window that does not {@code hostsContent} is wired to
     * the model but never registered as a place where plugin content opens, so the tab registry cannot choose it for a
     * tab with no window of its own: the warm-up rehearsal is such a window, and content opened in it would vanish when
     * it closes.
     */
    public void connectContributions(Contributions model, UiState state, boolean hostsContent) {
        connectContributions(model, state, hostsContent, false);
    }

    /**
     * As {@link #connectContributions(Contributions, UiState, boolean)}; a window opened while {@code anotherOpen} starts
     * out of Zen, and its tool windows saved in Float or Window mode start hidden, keeping their mode and bounds
     * (full windows plan 1 Controller Ruling 10): the saved state is the other window's too.
     */
    public void connectContributions(Contributions model, UiState state, boolean hostsContent, boolean anotherOpen) {
        if (closed || contributed != null) return;
        uiState = Objects.requireNonNull(state);
        railVisible = state.railVisible();
        // Restore before contributions can build or show saved panels.
        setZenMode(!anotherOpen && state.zenMode());
        contributed = new WindowContributions(this, Objects.requireNonNull(model), state, anotherOpen);
        if (hostsContent) {
            // The window joins the application's registry, taking the tabs it already holds along (workspace plan 3a Rulings 3).
            TabRegistry shared = model.tabs();
            shared.adopt(registry, id);
            host.close();
            registry = shared;
            host = registry.addHost(workspaceHost());
        }
        rebind();
        if (detachedShells != null) detachedShells(detachedShells);
    }

    /**
     * The registry-facing side of this window's workspace (workspace tabs spec 9.1): its panes are every tab's, so an item
     * placed beside a pane lands in that pane's tab (workspace plan 7a Rulings 8), and one placed without a pane opens as
     * a new tab (plan 8a Rulings 3).
     */
    private WorkspaceHost workspaceHost() {
        return new WorkspaceHost(id, this::attach, this::detach, this::selectTab, () -> bringToFront.run(),
            parentTabs::groupOf,
            (tab, group, side) -> attach(tab, false, group != null ? group : workspace().model().activeGroup(), side),
            // A plugin's item: Terminal Right and Down beside it, which answer whether a terminal opened, and Move Pane to
            // New Tab (workspace plan 8b Rulings 18).
            (tab, side) -> {
                if (!parentTabs.contains(tab)) return false;
                selectTab(tab);
                return split(side) != null;
            },
            this::mayMoveToNewTab, this::moveToNewTab,
            // Move Pane to Tab, as ContentTab.moveToTab asks, numbered from 1 (pane flexibility spec 4).
            this::paneTargets, (tab, number) -> moveToTab(tab, number - 1));
    }

    /**
     * Shows an item in this window, in a new tab at the end of the strip and shown, which the window's empty workspace
     * takes in its place (tabs and panes spec 13; workspace plan 8a Rulings 3): one the registry placed here, or
     * one this window opened. A failure leaves nothing mounted, takes back what the terminal registry heard, and
     * propagates.
     */
    private void attach(Tab tab) { attach(tab, false, null, null); }

    /**
     * As {@link #attach(Tab)}; with {@code opened} a new terminal's pane is announced as opened, and with {@code beside}
     * the item opens in a new pane on {@code side} of that pane, in the tab holding it.
     */
    private void attach(Tab tab, boolean opened, GroupId beside, Side side) {
        JComponent component = tab.component();
        boolean announced = false;
        try {
            WorkspaceTheme.apply(component, themes.current());
            tabListeners.put(tab, tab.onChanged(this::update));
            if (terminals != null) { terminals.tabArrived(tab, tab.movingFrom()); announced = true; }
            tabHost.attach(tab, opened);
            // An item a move between windows brings in lands in the tab chosen for it (workspace plan 7b Rulings 6).
            if (beside == null && landing != null && tab.moving()) parentTabs.land(tab, landing.parentTab(), landing.index());
            // Whatever opens without a pane opens as a new tab; the window's empty workspace takes it in its place.
            else if (beside == null && parentTabs.isEmpty()) workspace().open(tab);
            else if (beside == null) parentTabs.openParentTab(tab);
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
     * a closing tab is announced closed here, and the window keeps its empty workspace when its last tab has closed
     * (full windows spec 5.3). A tab whose attach failed has nothing to take out.
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
        // A move that empties the window closes it once the move has reported itself: the window manager's (TabRegistry.Windows).
    }

    /**
     * A parent tab's layout changed, or the window's parent tabs did (one opened, closed or shows now): the model takes
     * {@code view}'s layout, which is saved, the registry hears the selection, the palette a new active tab; the active
     * tab takes focus when {@code refocus} and {@code view} is the shown parent tab's. While a pane moves between tabs
     * ({@link #held}) only the model takes it, and the rest follows once, where the pane lands.
     */
    void workspaceChanged(WorkspaceView view, boolean refocus) {
        if (closed) return;
        parentTabs.changed(view);
        // A pane moving between tabs is announced once, where it lands (workspace plan 8c Rulings 17).
        if (holding > 0) {
            heldRefocus |= refocus;
            return;
        }
        // Every parent tab's layout change and every parent-tab change ends here (workspace plan 6a Rulings 8 and 28): the
        // save is asked for once the model has it, so a listener below that throws costs no save.
        layoutChanged();
        startShown();
        if (terminals != null) terminals.tabSelected();
        Tab now = selectedTab();
        if (commandPalette != null && now != paletteTab) { paletteTab = now; commandPalette.tabChanged(); }
        update();
        if (refocus && view == parentTabs.active()) focusSelected();
    }

    /** Something {@code workspace.toml} saves of this window changed (workspace tabs spec 13). */
    void layoutChanged() { if (!closed) onLayoutChanged.run(); }

    /** Whether the start of the shown restored terminals waits for the end of this EDT turn (workspace plan 6a Rulings 31). */
    private boolean startPending;

    /**
     * Starts, once this EDT turn ends, each restored terminal of the tab this window shows then, every pane of it, a
     * maximize's hidden ones too; the others wait until their tab first shows (workspace tabs spec 13; tabs and panes
     * spec 12; workspace plan 6a Rulings 4 and 31). A gesture that moves several tabs one at a time shows each for an
     * instant: the start, asked for once per turn, reads what shows when the gesture has ended, so those stay unstarted.
     */
    private void startShown() {
        if (startPending) return;
        startPending = true;
        SwingUtilities.invokeLater(() -> {
            startPending = false;
            if (closed) return;
            for (Tab tab : workspace().tabs()) {
                if (!(tab.content() instanceof TerminalContent terminal)) continue;
                // A pane whose start throws costs only itself: the tab's other panes still start.
                try { terminal.startIfWaiting(); }
                catch (RuntimeException | Error failure) { LOG.log(System.Logger.Level.WARNING, "Could not start a restored terminal", failure); }
            }
        });
    }

    /**
     * Shows a saved window in this window, which holds no item yet (workspace tabs spec 13; tabs and panes spec 12): its
     * tabs under their saved ids, in strip order, with the saved one shown, each with its split layout and active pane;
     * each terminal with its saved id, renamed title, profile and last local directory, its shell not started until its
     * tab first shows. An item this window cannot rebuild is left out as closing it would leave it out
     * ({@code WorkspaceFile.keeping}).
     *
     * @param record the window as saved
     * @return how many items came back
     */
    int restore(WorkspaceFile.WindowRecord record) {
        if (closed || !parentTabs.isEmpty()) throw new IllegalStateException("Only an empty open window restores");
        // A window opened empty keeps a stand-in size for the item that moves in; a restored one gives it up, so its frame
        // sizes it as a new window's, whether its items come back or it opens fresh.
        setPreferredSize(null);
        var restored = new LinkedHashMap<TabId, Tab>();
        try {
            // A plugin's item comes back through its plugin, or as a placeholder (workspace tabs spec 13; workspace plan 6b
            // Rulings 19): every item takes its saved tab and pane below, terminals and plugins' items alike.
            for (TabId saved : record.layout().tabs()) {
                Optional<Tab> tab = switch (record.tabs().get(saved)) {
                    case WorkspaceFile.TabRecord.Terminal terminal -> Optional.of(restoredTerminal(saved, terminal));
                    case WorkspaceFile.TabRecord.Content content -> savedTabs.apply(saved, content);
                };
                tab.ifPresent(found -> restored.put(saved, found));
            }
            ParentTabs shown = WorkspaceFile.keeping(record.layout(), restored::containsKey);
            Runnable show = () -> {
                for (Tab tab : restored.values()) mount(tab);
                parentTabs.restore(shown, restored);
            };
            if (terminals != null) terminals.atomically(show); else show.run();
        } catch (RuntimeException | Error failure) {
            // Nothing half-restored stays: each item closes, and is announced closed where it was announced.
            for (Tab tab : restored.values()) {
                try { tab.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            }
            throw failure;
        }
        return restored.size();
    }

    /**
     * Retry on a placeholder (workspace tabs spec 9.2): {@code rebuild} runs its plugin's restorer again, and the item it
     * opens takes the placeholder's pane, in whichever tab holds it, selected; a placeholder whose restorer fails again
     * says the new reason (workspace plan 6b Rulings 6 and 19).
     *
     * @param placeholder a placeholder this window holds
     * @param rebuild runs the restorer for what was saved: the item it opened, else the sentence the placeholder says
     */
    void retry(Tab placeholder, java.util.function.Function<WorkspaceFile.TabRecord.Content, TabRegistry.Restored> rebuild) {
        if (closed || !(placeholder.content() instanceof PlaceholderContent saved)) return;
        Optional<WorkspaceView> view = parentTabs.holding(placeholder);
        if (view.isEmpty()) return;
        TabRegistry.Restored rebuilt = rebuild.apply(saved.saved());
        if (rebuilt.tab().isEmpty()) {
            saved.failed(rebuilt.problem(), true);
            return;
        }
        Tab item = rebuilt.tab().get();
        Runnable replace = () -> {
            mount(item);
            view.get().replace(placeholder, item);
            placeholder.close();
        };
        if (terminals != null) terminals.atomically(replace); else replace.run();
    }

    /** A saved terminal whose shell waits until its tab first shows (workspace tabs spec 13). */
    private Tab restoredTerminal(TabId tabId, WorkspaceFile.TabRecord.Terminal saved) {
        Path directory = null;
        try { if (!saved.directory().isEmpty()) directory = Path.of(saved.directory()); }
        catch (java.nio.file.InvalidPathException unusable) { /* where a new shell starts */ }
        Optional<String> profile = saved.profile().isEmpty() ? Optional.empty() : Optional.of(saved.profile());
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, null, profile, Optional.empty());
        terminal.pane().restored();
        if (!saved.title().isEmpty()) terminal.rename(saved.title());
        Tab tab;
        try {
            // Under its saved id, straight into this window: never through TabRegistry.open (workspace plan 4b's hand-off).
            tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        } catch (RuntimeException | Error failure) {
            // Not registered, so no item closes it: its pane is closed here.
            try { terminal.close(); } catch (RuntimeException | Error closing) { failure.addSuppressed(closing); }
            throw failure;
        }
        tab.focusTarget(terminal::focusTerminal);
        return tab;
    }

    /** Wires a restored item to this window as {@link #attach(Tab)} does, without placing it: the restore places every one at once. */
    private void mount(Tab tab) {
        WorkspaceTheme.apply(tab.component(), themes.current());
        tabListeners.put(tab, tab.onChanged(this::update));
        if (terminals != null) terminals.tabArrived(tab, Optional.empty());
        tabHost.attach(tab, true);
    }

    /**
     * Runs {@code move}, a pane moving between tabs of this window, as one change (workspace plan 8c Rulings 17): while it
     * runs the parent tabs' model takes each layout as it changes, and the save, the terminal registry, the palette, the
     * items' activation and the focus hear only where it ends, once, in one registry change. No item is announced
     * selected or turns active on the way. The move's failure propagates after that, a failure of the announcement
     * suppressed under it.
     *
     * @param move the move
     */
    void held(Runnable move) {
        holding++;
        Throwable failure = null;
        try {
            move.run();
        } catch (RuntimeException | Error thrown) {
            failure = thrown;
            throw thrown;
        } finally {
            if (--holding == 0) {
                boolean refocus = heldRefocus;
                heldRefocus = false;
                Runnable landed = () -> workspaceChanged(parentTabs.active(), refocus);
                try {
                    if (terminals != null) terminals.atomically(landed); else landed.run();
                } catch (RuntimeException | Error late) {
                    // The move's own failure stays the one that propagates.
                    if (failure == null) throw late;
                    failure.addSuppressed(late);
                }
            }
        }
    }

    /** The selected plugin tab; null while a terminal tab is selected or the window is empty. */
    public Tab selectedContent() { Tab tab = selectedTab(); return tab != null && tab.kind() == Tab.Kind.HOSTED ? tab : null; }
    /** The shown tab's active pane's item; null in an empty workspace. */
    Tab selectedTab() { return parentTabs.active().selected(); }
    /** This window's items: its tabs in strip order, each one's items pane by pane. */
    java.util.List<Tab> tabs() { return parentTabs.tabs(); }
    int tabCount() { return parentTabs.tabCount(); }
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
     *
     * Whether the Move Tab to Window action may take the shown parent tab: one holding tabs that may all move, while
     * another window is open (Rulings 7).
     */
    boolean mayMoveToOtherWindow() { return mayMoveToOtherWindow(activeParentTab()); }

    private boolean mayMoveToOtherWindow(ParentTabId parentTab) {
        return !otherWindows(false).isEmpty() && parentTabs.view(parentTab).filter(view -> !view.isEmpty())
            .map(view -> movable(view.tabs())).orElse(false);
    }

    /**
     * Whether Move Pane to New Tab may take {@code tab} (tabs and panes spec 9; workspace plan 8b Rulings 9): an item of
     * this window whose tab holds another pane, neither pinned nor being closed.
     *
     * @param tab an item, or null
     * @return whether it would move
     */
    boolean mayMoveToNewTab(Tab tab) {
        if (closed || tab == null || !registry.movable(tab)) return false;
        return parentTabs.holding(tab).map(view -> view.tabCount() > 1).orElse(false);
    }

    /**
     * Move Pane to New Tab (tabs and panes spec 9): {@code tab}'s pane leaves its tab for a new tab right after it, which
     * shows; its other panes stay, nothing closes and nothing leaves the window. Nothing when {@link #mayMoveToNewTab}
     * says no.
     *
     * @param tab an item of this window
     */
    void moveToNewTab(Tab tab) { if (mayMoveToNewTab(tab)) parentTabs.moveToNewTab(tab); }

    /**
     * A pane drag's drop beside {@code group}, a pane of this window (tabs and panes spec 7; workspace plan 8c Rulings
     * 12): {@code tab}'s pane moves there on {@code side}, within its tab a layout change, into another tab leaving its
     * tab, which goes with its last pane; the item then takes the focus. Nothing closes, nothing leaves the window and the
     * registry hears no move. Nothing for an item that is pinned or being closed, or one this window does not hold.
     *
     * @param tab an item of this window
     * @param group another pane of this window
     * @param side where the item's pane goes
     */
    void movePane(Tab tab, GroupId group, Side side) {
        if (closed || tab == null || !registry.movable(tab) || !parentTabs.contains(tab)) return;
        parentTabs.moveBeside(tab, group, side);
        selectTab(tab);
    }

    /**
     * A pane drag's drop on a new-tab place of this window's strip (tabs and panes spec 7): Move Pane to New Tab's move,
     * the new tab at {@code index} of the strip, which takes it at once (workspace plan 8c Rulings 9); the item then takes
     * the focus. Nothing when {@link #mayMoveToNewTab} says no, as for its tab's only pane (Rulings 8).
     *
     * @param tab an item of this window
     * @param index where the new tab goes among the strip's entries
     */
    void movePaneToNewTab(Tab tab, int index) {
        if (!mayMoveToNewTab(tab)) return;
        parentTabs.bar().atOnce(() -> parentTabs.moveToNewTab(tab, index));
        selectTab(tab);
    }

    /**
     * The tabs Move Pane to Tab offers {@code item} (pane flexibility spec 4): this window's other tabs, in strip order;
     * none for an item a pane drag may not move (pinned, being closed, a restorer's; workspace plan 9a Rulings 7), and
     * none in a window of one tab.
     *
     * @param item an item, or null
     * @return the tabs, each numbered as Select Tab numbers it
     */
    java.util.List<OtherTab> paneTargets(Tab item) {
        if (closed || item == null || !registry.movable(item)) return java.util.List.of();
        return parentTabOf(item).map(this::otherTabs).orElse(java.util.List.of());
    }

    /** This window's tabs but {@code own}, numbered from 1 in strip order, each titled as the strip's tab list titles it. */
    private java.util.List<OtherTab> otherTabs(ParentTabId own) {
        var others = new ArrayList<OtherTab>();
        java.util.List<WorkspaceView> views = parentTabs.views();
        for (int i = 0; i < views.size(); i++) {
            WorkspaceView view = views.get(i);
            if (!view.id().equals(own) && view.selected() != null)
                others.add(new OtherTab(view.id(), i + 1, WindowTabs.capped(tabTitle(view.selected())), view.selected().id()));
        }
        return others;
    }

    /**
     * Whether Move Pane to Tab would move {@code item} into the tab at {@code index} of this window's strip: one of
     * {@link #paneTargets}, so another tab than its own.
     *
     * @param item an item, or null
     * @param index a place in the strip, 0 for the first
     * @return whether it would move
     */
    boolean mayMoveToTab(Tab item, int index) {
        return paneTargets(item).stream().anyMatch(other -> other.number() == index + 1);
    }

    /**
     * Move Pane to Tab (pane flexibility spec 4): {@code item}'s pane moves to the right of the active pane of the tab at
     * {@code index} of the strip, as a pane drag drops it there ({@link #movePane}; workspace plan 9a Rulings 6): that tab
     * shows, the item takes the focus, a maximized pane there stops being maximized and the item's old tab goes with its
     * last pane. Nothing when {@link #mayMoveToTab} says no.
     *
     * @param item an item of this window
     * @param index a place in the strip, 0 for the first
     */
    void moveToTab(Tab item, int index) {
        if (!mayMoveToTab(item, index)) return;
        movePane(item, parentTabs.views().get(index).model().activeGroup(), Side.RIGHT);
    }

    /**
     * The tabs Move Tab Into offers the tab {@code id} (pane flexibility spec 5): this window's other tabs, in strip order;
     * none while it holds an item that may not move (a dialog open in a pane, one being closed; workspace plan 9a Rulings
     * 7), and none in a window of one tab.
     *
     * @param id a tab of this window
     * @return the tabs, each numbered as Select Tab numbers it
     */
    java.util.List<OtherTab> tabTargets(ParentTabId id) {
        if (closed || id == null) return java.util.List.of();
        Optional<WorkspaceView> view = parentTabs.view(id);
        return view.isPresent() && movable(view.get().tabs()) ? otherTabs(id) : java.util.List.of();
    }

    /**
     * Move Tab Into (pane flexibility spec 5): every pane of the tab {@code id} goes to the right of the active pane of the
     * tab {@code into}, as {@link #mergeTab} moves them. Nothing unless {@link #tabTargets} offers {@code into}.
     *
     * @param id a tab of this window
     * @param into another tab of this window
     */
    void moveTabInto(ParentTabId id, ParentTabId into) {
        if (tabTargets(id).stream().noneMatch(other -> other.id().equals(into))) return;
        parentTabs.view(into).ifPresent(target -> mergeTab(id, target.model().activeGroup(), Side.RIGHT));
    }

    /**
     * Move Tab Into, and a tab's entry dropped beside a pane (pane flexibility spec 5): every pane of the tab {@code id}
     * goes on {@code side} of {@code group}, a pane of another tab of this window, as one nested split that keeps their
     * arrangement and sizes; that tab shows, its maximize ended, and the moved tab's active item takes the focus, so the
     * tab is named after it (workspace plan 9a Rulings 10 to 12 and 16). Nothing closes, nothing leaves the window and
     * the registry hears no move. Nothing for a tab holding an item that may not move, a pane of the tab itself or one
     * the window does not hold.
     *
     * @param id a tab of this window
     * @param group a pane of another tab of this window
     * @param side where the tab's panes go
     */
    void mergeTab(ParentTabId id, GroupId group, Side side) {
        WorkspaceView view = closed ? null : parentTabs.view(id).orElse(null);
        if (view == null || !movable(view.tabs())) return;
        Tab shown = view.selected();
        parentTabs.moveInto(id, group, side);
        if (parentTabs.view(id).isEmpty()) selectTab(shown);
    }

    /**
     * Whether Move Tab Into would move the tab {@code id} into the tab at {@code index} of this window's strip: one of
     * {@link #tabTargets}, so another tab than {@code id}.
     *
     * @param id a tab of this window, or null
     * @param index a place in the strip, 0 for the first
     * @return whether it would move
     */
    boolean mayMoveTabIntoTab(ParentTabId id, int index) {
        return tabTargets(id).stream().anyMatch(other -> other.number() == index + 1);
    }

    /**
     * Move Tab Into Tab 1 to 9, and the Tab menu's Move Tab Into (pane flexibility spec 5; workspace plan 9a Rulings
     * 22): every pane of the tab {@code id} goes to the right of the active pane of the tab at {@code index} of the
     * strip, as {@link #moveTabInto(ParentTabId, ParentTabId)} moves them. Nothing when {@link #mayMoveTabIntoTab} says
     * no.
     *
     * @param id a tab of this window, or null
     * @param index a place in the strip, 0 for the first
     */
    void moveTabIntoTab(ParentTabId id, int index) {
        if (mayMoveTabIntoTab(id, index)) moveTabInto(id, parentTabs.views().get(index).id());
    }

    /**
     * Move Tab Into's submenu for the tab {@code parent} (pane flexibility spec 5; workspace plan 9a Rulings 9): an entry
     * for each other tab of the window, its number and title as plain text, that moves every pane of {@code parent} into
     * that tab; disabled while there is none.
     */
    private JMenu tabMoves(ParentTabId parent) {
        var moves = new JMenu("Move Tab Into");
        var targets = tabTargets(parent);
        for (OtherTab target : targets) {
            var entry = PlainText.item(target.label());
            entry.addActionListener(event -> moveTabInto(parent, target.id()));
            moves.add(entry);
        }
        moves.setEnabled(!targets.isEmpty());
        return moves;
    }

    /**
     * Takes {@code moving}, other windows' tabs, into this window's parent tab {@code parentTab} through the tab registry,
     * one move each, so each keeps its content and session and reports {@code TAB_MOVED} once (parent tabs spec 5;
     * workspace plan 7b Rulings 6). A parent tab this window lacks opens under that id at {@code index} of the bar, in
     * place of the window's empty workspace (its own, or a new window's). That parent tab then takes
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
    /**
     * Takes {@code tab}, another window's item, into a new tab at {@code index} of this window's strip (its end past it),
     * through the tab registry, one move, its content and session untouched (tabs and panes spec 7; workspace plan 8c
     * Rulings 12): a pane dropped on a new-tab place of this window's strip. A tab moving in takes its place at once.
     * Nothing once the window has closed; a receive that runs within another leaves the outer one's landing as it was.
     *
     * @param tab another window's item
     * @param index where the new tab goes among the strip's entries
     */
    void receiveAsTab(Tab tab, int index) {
        if (closed) return;
        Landing outer = landing;
        landing = new Landing(ParentTabId.random(), index);
        try {
            registry.move(tab, id);
        } finally {
            landing = outer;
        }
    }
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
     * A tab's menu, from its strip entry (tabs and panes spec 6 and 10; workspace plan 8a Rulings 8), for the tab holding
     * {@code item}: Rename Tab while {@code item} is a terminal, Move Tab to New Window, Move Tab Into (pane flexibility
     * spec 5), a rule, Close Tab, then
     * {@code item}'s own commands. Move Tab to Window follows Move Tab Into while another window is open. Empty for an item
     * this window does not hold.
     *
     * @param item the item the entry shows, its tab's active pane's
     * @return the menu
     */
    JPopupMenu tabMenu(Tab item) {
        var menu = new JPopupMenu();
        parentTabOf(item).ifPresent(parent -> {
            if (item.kind() == Tab.Kind.TERMINAL)
                menu.add(tabMenuItem("Rename Tab", ActionId.RENAME_TAB, () -> { selectTab(item); invoke(ActionId.RENAME_TAB); }));
            menu.add(windowItem(ActionId.MOVE_TAB_TO_NEW_WINDOW, item));
            menu.add(tabMoves(parent));
            if (!otherWindows(false).isEmpty()) menu.add(windowMoves(parent));
            menu.addSeparator();
            menu.add(tabMenuItem(ActionId.CLOSE_TAB.label(), ActionId.CLOSE_TAB, () -> closeParentTab(parent)));
            if (!item.commands().isEmpty()) menu.addSeparator();
            for (var command : item.commands()) {
                var entry = PlainText.item(command.title());
                entry.setEnabled(command.enabled()); entry.setAccelerator(command.shortcut());
                // A menu kept open across a selection change runs the command of the content selected now, if enabled.
                entry.addActionListener(event -> {
                    Tab current = selectedContent();
                    if (current == null) return;
                    current.commands().stream().filter(value -> value.id().equals(command.id()) && value.enabled())
                        .findFirst().ifPresent(value -> value.run().run());
                });
                menu.add(entry);
            }
        });
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /**
     * The window menu's entries (tabs and panes spec 6; full windows spec 5.3): Move Tab to New Window, then the Move Tab
     * to Window submenu while another window is open.
     *
     * @return the entries in menu order
     */
    java.util.List<ActionId> windowMenuItems() {
        return otherWindows(false).isEmpty() ? java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW)
            : java.util.List.of(ActionId.MOVE_TAB_TO_NEW_WINDOW, ActionId.MOVE_TAB_TO_WINDOW);
    }

    /**
     * The menu entry for one of {@link #windowMenuItems()}: the action's item, or for Move Tab to Window the submenu of
     * the other windows ({@link #windowMoves}) for the shown parent tab.
     */
    JMenuItem windowMenuEntry(ActionId id) {
        if (id == ActionId.MOVE_TAB_TO_WINDOW) return windowMoves(activeParentTab());
        var item = new JMenuItem(action(id));
        item.putClientProperty("html.disable", true);
        return item;
    }

    /**
     * Move Tab to Window's submenu for {@code parent} (full windows spec 5.3): an entry for each other window, in opening
     * order, titled with its active tab's title as plain text, that moves {@code parent} there; disabled while the tab
     * may not move.
     */
    private JMenu windowMoves(ParentTabId parent) {
        var moves = new JMenu(ActionId.MOVE_TAB_TO_WINDOW.label());
        for (WindowContent other : otherWindows(false)) {
            Tab shown = other.selectedTab();
            var entry = PlainText.item(shown == null ? other.frameTitle : other.tabTitle(shown));
            entry.addActionListener(event -> moveTabToWindow(parent, other));
            moves.add(entry);
        }
        moves.setEnabled(mayMoveToOtherWindow(parent));
        return moves;
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
        for (ActionId id : windowMenuItems()) menu.add(windowMenuEntry(id));
        dev.jasper.ui.swing.NativeStyles.installed().menuTree(menu, dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
        return menu;
    }

    /** A menu entry for {@code id}, one of {@link #windowMenuItems()}, acting on {@code tab}'s parent tab or this window. */
    private JMenuItem windowItem(ActionId id, Tab tab) {
        Runnable run = switch (id) {
            case MOVE_TAB_TO_NEW_WINDOW -> () -> moveTabToNewWindow(tab);
            default -> throw new IllegalArgumentException("Not a window menu entry: " + id);
        };
        var item = tabMenuItem(id.label(), id, run);
        // Enabled only while every tab the entry would move may move (workspace plan 7b Rulings 7).
        item.setEnabled(switch (id) {
            case MOVE_TAB_TO_NEW_WINDOW -> parentTabOf(tab).map(this::mayMoveTabToNewWindow).orElse(false);
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

    /**
     * Gives this window's detached tool windows (Float and Window) their native windows, and opens those that wait. Given
     * before contributions connect, they are used once they do. EDT.
     */
    public void detachedShells(java.util.function.BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events,
                                   DetachedToolWindows.Shell> shells) {
        detachedShells = shells;
        DetachedToolWindows windows = detachedWindows();
        if (windows == null) return;
        windows.shells = shells;
        windows.sync();
    }

    /** This window's detached tool windows' own windows; null before contributions connect. */
    DetachedToolWindows detachedWindows() { return contributed == null ? null : contributed.detached(); }

    /** Whether the panel is one of this window's tool windows detached into a window of its own, showing or not. */
    boolean panelDetached(String panelId) { return dock.mode(panelId).detached(); }
    DockStripe leftStripe() { return leftStripe; }
    DockStripe rightStripe() { return rightStripe; }
    /** The bottom strip of labelled stripes; empty under the icon rails. */
    java.util.Optional<DockStripe> bottomStrip() { return stripes.bottom(); }
    /** The window's stripes. */
    StripeSet stripes() { return stripes; }
    DockDrag dockDrag() { return dockDrag; }
    /** The drag this window's strip starts with a tab and its panes with a modifier press. */
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
        boolean rails = railsEnabled();
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
    /** The window's equal tabs; the default look's tests read them. */
    WindowTabs windowTabs() { return (WindowTabs) tabBar(); }
    /** The window's one tab strip (tabs and panes spec 5). */
    TabStrip tabBar() { return parentTabs.bar().strip(); }
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

    /**
     * New Tab (tabs and panes spec 10): a terminal in a new tab at the end of the strip, shown, starting in
     * {@code directory}. The window's empty workspace takes it in its only tab, the one tab that may be empty
     * (workspace plan 7a Rulings 7).
     *
     * @param directory where its shell starts; null means where a new shell starts
     * @return the terminal; null once the window has closed
     */
    public TerminalContent newTab(Path directory) { return openTab(directory); }

    /** Shows the parent tab {@code id} and focuses its active tab (workspace plan 7a Rulings 10); nothing for another window's. */
    void selectParentTab(ParentTabId id) {
        if (closed || parentTabs.view(id).isEmpty()) return;
        parentTabs.select(id);
        focusSelected();
    }

    /** Select Tab 1 to 9: the tab at {@code index} of the strip; nothing past the last. */
    void selectParentTabAt(int index) {
        var all = parentTabs.model().parentTabs();
        if (index >= 0 && index < all.size()) selectParentTab(all.get(index).id());
    }

    /** Next and Previous Tab: the tab {@code delta} places along the strip, wrapping around. */
    void selectParentTabRelative(int delta) { if (parentTabs.count() > 1) selectParentTab(parentTabs.model().step(delta)); }

    /**
     * Close Tab (tabs and panes spec 10): the items of every pane of the tab {@code id} as one gesture, after their
     * guards; the tab goes with its last pane (spec 4).
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
        return openTerminal(directory, requestOrNull, profile, program, null, null);
    }

    /**
     * A terminal starting in {@code directory} (null: where a new shell starts), in a new tab of its own, or in a new
     * pane on {@code side} of {@code beside} when that is given. Null once the window has closed.
     */
    private TerminalContent openTerminal(Path directory, SessionRequest requestOrNull, Optional<String> profile,
                                         Optional<java.util.List<String>> program, GroupId beside, Side side) {
        if (closed) return null;
        // A plugin's session opened while its saved tab is rebuilt here takes that tab's place (workspace tabs spec 13); any
        // other terminal tab is refused until the restorer is done (workspace plan 6b Rulings 24).
        Optional<TabId> saved = requestOrNull == null ? Optional.empty() : registry.slot(requestOrNull.providerId(), id);
        if (saved.isEmpty() && registry.restoring(id)) return null;
        TabId tabId = saved.orElseGet(TabId::random);
        TerminalContent terminal = new TerminalContent(tabId, directory, launcher, requestOrNull, profile, program);
        Tab tab = registry.add(tabId, Tab.Kind.TERMINAL, terminal, id);
        tab.focusTarget(terminal::focusTerminal);
        if (requestOrNull != null) tab.restore(requestOrNull.restore());
        if (saved.isPresent()) {
            // The restore places it with the window's other tabs; its session connects when it is first shown.
            terminal.pane().restored();
            registry.restored(tab);
            return terminal;
        }
        try {
            attach(tab, true, beside, side);
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
        return openTerminal(directory(), null, Optional.empty(), Optional.empty(), workspace().model().activeGroup(), side);
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
        return openTerminal(directory, requestOrNull, profile, program, group, side);
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

    /**
     * The component a press at {@code point} on {@code source} lands on: the deepest at that place under its native window,
     * or for content in no native window (a headless test's) under its outermost component; {@code source} when none is
     * there. In a window AWT hands a press to the deepest component with mouse listeners, or else to the frame, so a press
     * on a view's bare area comes from an ancestor of the view. A popup or the drag overlay over the place is what it
     * lands on.
     */
    private static Component under(Component source, Point point) {
        Component root = SwingUtilities.getRoot(source);
        if (root == null) { root = source; while (root.getParent() != null) root = root.getParent(); }
        Point at = SwingUtilities.convertPoint(source, point, root);
        Component deepest = SwingUtilities.getDeepestComponentAt(root, at.x, at.y);
        return deepest == null ? source : deepest;
    }

    void report(TerminalEvent event) { if (terminals != null) terminals.publish(event); }

    /** This window's configuration, or null before the first one arrives. */
    ConfigSnapshot configured() { return workspaceConfiguration.snapshot(); }

    /** The saved tab name pattern its Terminal tabs follow (tab names spec 3). */
    TitleTemplate tabNamePattern() { return workspaceConfiguration.tabName(); }

    /** Plugins' tab name pieces for a tab; none before the window is connected to the contributions model. */
    java.util.Map<String, String> pluginNamePieces(java.util.UUID tabId) {
        return contributed == null ? java.util.Map.of() : contributed.model().tabNamePieces().values(tabId);
    }

    /** How much an inactive pane dims: the configured {@code terminal.dim_inactive_panes} (workspace plan 8a Rulings 12). */
    float paneDim() {
        ConfigSnapshot configured = configured();
        return (configured == null ? ConfigSnapshot.defaults() : configured).terminal().dimInactivePanes();
    }

    /** Every tab's panes take their dim again, after the configured amount changed. */
    void dimPanes() { if (!closed) for (WorkspaceView view : parentTabs.views()) view.dimPanes(); }

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

    /**
     * Selects {@code tab} in its parent tab and activates its group, before that parent tab shows, then shows it and
     * focuses the tab: no other tab of a hidden parent tab turns active on the way.
     */
    void selectTab(Tab tab) {
        WorkspaceView view = closed ? null : parentTabs.holding(tab).orElse(null);
        if (view == null) return;
        // The item first, while its tab may still hide, so no other item of that tab turns active on the way.
        view.select(tab);
        parentTabs.select(view.id());
        focusSelected();
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

    /** Native visibility affects Buddy independently of activation event ordering. */
    void buddyWindowState(boolean showing, boolean iconified) {
        boolean visible = showing && !iconified;
        if (buddyWindowVisible == visible) return;
        buddyWindowVisible = visible;
        publishPresentation();
    }

    boolean buddyWatched(Tab tab) {
        return buddyWindowVisible && isActiveAndOpen()
            && parentTabOf(tab).filter(activeParentTab()::equals).isPresent();
    }

    /** Publishes only committed layout membership; held moves publish when they settle. */
    void publishPresentation() { publishPresentation(this::emit); }

    private void publishPresentation(Consumer<WorkspaceActivity.Event> listener) {
        if (closed || holding > 0) return;
        for (WorkspaceView view : parentTabs.views()) {
            Tab selected = view.selected();
            if (selected == null) continue;
            String groupTitle = tabTitle(selected);
            boolean watched = view == parentTabs.active() && isActiveAndOpen() && buddyWindowVisible;
            for (Tab tab : view.tabs()) {
                if (tab.content() instanceof TerminalContent terminal) {
                    listener.accept(new WorkspaceActivity.Presentation(terminal.pane().id(), view.id().value(),
                        groupTitle, tabTitle(tab), watched, () -> {
                            tab.select();
                            terminal.pane().focusTerminal();
                        }));
                }
            }
        }
    }

    void update() {
        // A held move's items hear where it ends, once (workspace plan 8c Rulings 17).
        if (closed || holding > 0) return;
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
        publishPresentation();
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
            if (tab.kind() != Tab.Kind.TERMINAL) WorkspaceTheme.apply(tab.component(), theme, () -> tabListeners.containsKey(tab));
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
        Toolkit.getDefaultToolkit().removeAWTEventListener(paneClicks);
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
        for (DetachedKeys keys : java.util.List.copyOf(detachedKeys)) keys.close();
        if (terminals != null) { terminals.close(); terminals = null; }
        workspaceActions.disable();
        refreshTabs();
        if (titleBand != null) { titleBand.close(); titleBand = null; }
        onThemeChanged = theme -> {};
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
