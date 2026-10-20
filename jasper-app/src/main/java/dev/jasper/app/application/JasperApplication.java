package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commandline.CommandDispatcher;
import dev.jasper.app.commandline.CommandInstall;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ViewMode;
import dev.jasper.app.config.FontConfig;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.config.TerminalConfig;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.launch.LaunchSettings;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.launch.ShellProfile;
import dev.jasper.app.launch.ShellProfileCatalog;
import dev.jasper.app.launch.ShellProfiles;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.notifications.CommandNotice;
import dev.jasper.app.notifications.CommandNotifier;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.IncidentReport;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.notificationui.ErrorsView;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.platform.SystemFonts;
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.startup.WindowTrace;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.app.windows.NativeShells;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.TerminalWindow;
import dev.jasper.app.workspace.WindowCallbacks;
import dev.jasper.app.workspace.WorkspaceActivity;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.rendering.FontSet;
import dev.jasper.terminal.session.SessionLaunchOptions;
import dev.jasper.terminal.view.TerminalView;

import dev.jasper.terminal.session.TerminalSession;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartCommand;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.restart.RestartMode;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import dev.jasper.app.terminals.TerminalRegistry;

/** Application-level window and shell ownership; closing a window never exits sibling windows. */
public final class JasperApplication {
    private static final System.Logger LOG = System.getLogger(JasperApplication.class.getName());
    private final ThemeController themes;
    /** The application's theme controller. */
    ThemeController themes() { return themes; }
    private final dev.jasper.app.platform.SystemAppearance systemAppearance;
    private Runnable systemAppearanceListening = () -> { };
    private final Set<TerminalWindow> windows = new LinkedHashSet<>();
    /** Workspace glass exists only on macOS; elsewhere plugins see {@code GlassSource.NONE} and windows make no glass requests. */
    private static final boolean WORKSPACE_GLASS = com.formdev.flatlaf.util.SystemInfo.isMacOS;
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");
    /** The trigger of a window another launch asked for through the handoff endpoint. */
    public static final String HANDOFF = "handoff";
    private final List<Runnable> glassListeners = new ArrayList<>();
    /** Whether an open terminal window has been activated since the first of them opened; see {@link #presence}. */
    private boolean activatedSinceOpen;
    /** How many terminal windows are being opened right now (EDT): from the start of {@link #openWindow} until it returns or fails. */
    private int openingWindows;
    private final SessionLaunchCoordinator launches = new SessionLaunchCoordinator(Executors.newThreadPerTaskExecutor(
        Thread.ofPlatform().name("jasper-shell-launch-", 0).factory()));
    private final ConfigurationController configuration;
    private final ViewMode startupViewMode;
    private boolean quitting;
    /** Written on the EDT, read by the native quit handler's thread too. */
    private volatile boolean resident;
    /** A packaged macOS Jasper's Dock presence while resident; null where Quit really quits (Windows, Linux, development runs). */
    private volatile DockPresence dock;
    /** Whether the application jar changed on disk since start-up: an update was installed under this process. */
    private BooleanSupplier staleBuild = () -> false;
    private volatile StartupTrace trace = StartupTrace.current();
    private int windowsOpened;
    /**
     * The output watch of every local session this application started, until its first terminal claims it or the
     * session exits. Strongly keyed: a watch holds its session, so a weak key could never clear.
     */
    private final java.util.Map<TerminalSession, SessionOutput> outputs = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.function.Consumer<Boolean> loginItems = enabled -> { };
    private Function<dev.jasper.app.platform.ResidentTray.Actions, Runnable> residentIndicator = actions -> () -> { };
    private Runnable removeIndicator;
    /** Whether {@link #residencySettled()} has run: it does its work once per process, and idle presence begins with it. */
    private boolean startupSettled;
    /** How long after the first paint a shell that prints nothing still counts as settled. */
    private Duration settleFallback = Duration.ofSeconds(2);
    private Runnable cancelSettleFallback;
    private boolean shutdownQueued;
    private boolean stopped;
    private final java.util.List<Runnable> shutdownActions = new java.util.ArrayList<>();
    private java.awt.desktop.AppReopenedListener reopenListener;
    private boolean quitHandlerInstalled;
    private final CommandHistory history;
    private final ShellLauncher suppliedLauncher;
    private final ShellProfileCatalog shellProfiles;
    /**
     * How long a launch of a WSL or PowerShell 7 profile, resolved before the first detection completed, waits for it
     * off the EDT: both of detection's process calls may take their full timeout.
     */
    static final Duration DETECTION_WAIT = Duration.ofSeconds(12);
    private final ApplicationShutdown shutdown;
    private final Path shellIntegrationDir;
    /** The imported themes' folder, or null for the built-in theme alone. */
    private final Path themesFolder;
    private final BuddyIntegration buddy;
    private final dev.jasper.app.lifecycle.Subscription buddyAppearance;
    private final SystemNotices systemNotices;
    private final CommandNotifier notifications;
    private volatile PluginRuntime plugins;
    /** The command socket this Jasper serves; new local shells name it in JASPER_COMMAND_SOCKET. */
    private volatile Path commandSocket;
    /** Where {@code jasper --help} reads the subcommands, or null for a private launch. */
    private Path subcommandList;
    private ActionEntry migrateAction;
    private CommandInstall commandInstall;
    private dev.jasper.app.settingsui.SettingsDialog settings;
    private VmOptionsFeature vmOptions;
    private final MemoryIndicator memoryIndicator;
    private volatile MemoryWarnings memoryWarnings;
    private boolean preferencesHandlerInstalled;
    private boolean aboutHandlerInstalled;
    private UiState uiState = UiState.inMemory();
    private AuxiliaryWindows auxiliary;
    private NativeShells shells;
    private PluginManager pluginManager;
    private ResidentControl residentControl = ResidentControl.NONE;
    private boolean replacementHandsOff;
    private boolean standaloneNotice;
    /**
     * Test seam: the one collection that follows a {@link #warmUp}, started on its own daemon thread. Production is
     * {@code System.gc()}: the rehearsal's garbage would otherwise stay in the resident process's heap.
     */
    Runnable collector = System::gc;
    /** Test seams. Production plans from this process's own command line and starts a real process. */
    Function<RestartMode, Optional<List<String>>> restartPlanner = RestartCommand::current;
    Consumer<List<String>> spawner = command -> {
        try { RestartCommand.spawn(command); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    };
    private volatile List<String> relaunch;
    private volatile boolean endpointOwner;
    private volatile List<CompletableFuture<?>> processCleanups;
    private final dev.jasper.app.contributions.Contributions contributions = new dev.jasper.app.contributions.Contributions();
    private ActivityNotifier activityNotifier;
    private final NotificationCenter notificationCenter;
    private final NotificationsFeature notificationViews;
    private final Incidents incidents;
    private final IncidentNotices incidentNotices;
    /** Built once plugins start, with the auxiliary windows it opens in. */
    private ErrorsWindow errorsWindow;
    /** Built with the errors window, once plugins start. */
    private AboutWindows aboutWindows;
    /** Where Open Log Folder points, known once plugins start. */
    private Path logFolder;
    /** The clipboard Jasper and its plugins share. */
    private final dev.jasper.app.platform.HostClipboard clipboard = dev.jasper.app.platform.HostClipboard.system();
    private dev.jasper.app.lifecycle.Subscription pluginTheme;

    private int configuredLongCommandSeconds() {
        return configuration == null ? 10 : configuration.snapshot().longCommandSeconds();
    }
    private TerminalWindow lastActive;
    /** Every terminal window's tabs and panes, for features that must not hold Swing objects. */
    private final TerminalRegistry terminals = new TerminalRegistry();

    TerminalRegistry terminals() { return terminals; }

    /** The plugin selection this process starts, fixed at construction. */
    private volatile dev.jasper.app.plugins.PluginSelection selection;
    /** The look's font files that could not be registered, for its notice. */
    private List<String> lookFontProblems = List.of();
    /** The presentation every surface of this process receives (spec 4.9). */
    private volatile dev.jasper.app.presentation.Presentation presentation;

    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }

    /** The steps a branded look's splash shows (spec 10.11); none until the bootstrap hands them over. EDT. */
    private dev.jasper.app.startup.StartupSteps startupSteps = dev.jasper.app.startup.StartupSteps.NONE;

    /**
     * The steps this application reports to a branded look's splash (spec 10.11): each plugin as it starts, then the
     * first window as it is composed and shown. A presentation without branding, as a look that fell back at start has
     * (spec 4.7), ends them at once. Before {@link #startPlugins(AppDirs)}. EDT.
     *
     * @param steps the bootstrap's steps
     */
    public void startupSteps(dev.jasper.app.startup.StartupSteps steps) {
        startupSteps = java.util.Objects.requireNonNull(steps, "steps");
        if (!presentation.branding().present()) steps.done();
    }

    /** What terminal windows and the rehearsal's are made of under this process's presentation. */
    dev.jasper.app.workspace.WindowLook windowLook() { return dev.jasper.app.workspace.WindowLook.of(presentation.projections()); }
    /** The auxiliary windows; null before {@link #startPlugins(AppDirs)}. Tests. */
    dev.jasper.app.windows.AuxiliaryWindows auxiliary() { return auxiliary; }

    /** The auxiliary windows' shells; null before {@link #startPlugins(AppDirs)}. Tests. */
    dev.jasper.app.windows.NativeShells nativeShells() { return shells; }

    /** The rehearsal painted: the look proved itself (spec 4.6), recorded off the EDT. */
    void rehearsalPainted() {
        var attempt = presentationAttempt();
        Thread.ofVirtual().name("jasper-presentation-proven").start(attempt::proven);
    }

    /** The attempt record of the look this process runs; none for the default look or one that fell back. Any thread. */
    public dev.jasper.app.presentation.PresentationAttempt presentationAttempt() {
        var current = presentation;
        return current.provider().isPresent() ? selection.attempt() : dev.jasper.app.presentation.PresentationAttempt.NONE;
    }

    /** The running plugins; null before {@link #startPlugins(AppDirs)}. Tests. */
    PluginRuntime pluginRuntime() { return plugins; }


    public JasperApplication() { this(null); }

    public JasperApplication(ConfigService service) { this(service, null); }

    /** Isolated launch ownership for controlled tools; production still resolves captured settings. */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher) {
        this(service, suppliedLauncher, new CommandHistory());
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history) {
        this(service, suppliedLauncher, history, null);
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile) {
        this(service, suppliedLauncher, history, buddyStateFile, () -> {});
    }

    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, null);
    }

    /**
     * {@code buddyStateFile} may be null: the buddy then starts in the default corner and forgets drags.
     * {@code terminate} runs once, off the EDT, after shutdown's bounded cleanup; production passes the JVM exit.
     * {@code shellIntegrationDir} is the extracted script directory, or null when extraction failed or tests want none.
     */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, null);
    }

    /** As above; {@code themesFolder} holds the imported themes, or is null for the built-in theme alone. */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, themesFolder,
            dev.jasper.app.plugins.PluginSelection.none());
    }

    /**
     * As above, under {@code bootstrapSelection}: the bootstrap's plugin selection, whose presentation every surface of
     * this process receives (spec 4.9) and whose plugins {@link #startPlugins(AppDirs)} starts.
     */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, themesFolder, bootstrapSelection,
            dev.jasper.app.appearance.ThemeController.INSTALLERS);
    }

    /** As above, with the installers that put a theme in place under a kit: the seam of spec 4.7's fallback tests. */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection,
                      java.util.function.Function<dev.jasper.ui.kit.Kit, java.util.function.Predicate<dev.jasper.ui.theme.GeneratedTheme>> installers) {
        // Not named selection: the field changes during construction when the kit is blamed (spec 4.7), the parameter never.
        this.selection = Objects.requireNonNull(bootstrapSelection, "bootstrapSelection");
        presentation = this.selection.presentation();
        dev.jasper.app.platform.BundledFonts.register();
        // The look's own faces, before any font list is cached (spec 4.9).
        lookFontProblems = LookFonts.register(presentation.faces());
        ConfigSnapshot startup = service == null ? ConfigSnapshot.defaults() : service.initialState().snapshot();
        startupViewMode = startup.viewMode();
        systemAppearance = dev.jasper.app.platform.SystemAppearance.open();
        try {
        boolean dark = AppearancePolicy.dark(startup.appearance(), systemAppearance.current());
        // First Swing work: every later component may read the installed snapshot. A variant that cannot be installed
        // never stops start-up; the configuration reports it.
        var library = themesFolder == null ? dev.jasper.app.appearance.ThemeLibrary.of(selection.themes())
            : dev.jasper.app.appearance.ThemeLibrary.read(themesFolder, dev.jasper.app.appearance.ThemeLibrary.bundledOnly(), selection.themes());
        // The configuration reports unusable picks; start-up only replaces them.
        var picks = dev.jasper.app.appearance.ThemeSelection.of(library, startup.darkTheme(), startup.lightTheme(),
            startup.terminalScheme(), (key, message) -> { });
        var started = ThemeController.start(dark, picks, presentation.kit(), installers,
            failure -> LOG.log(System.Logger.Level.WARNING, "Could not start with this theme and look; trying the next: " + failure.getMessage(), failure));
        themes = started.controller();
        // Spec 4.7: the kit was at fault. Every consumer gets the fallback before plugins start, and the provider is skipped.
        // The field, not the constructor's bootstrapSelection: startPlugins(AppDirs) starts this.selection.
        if (started.step().blamesKit()) {
            LOG.log(System.Logger.Level.WARNING, "The " + presentation.look() + " look could not be installed; Jasper uses its standard look");
            var attempt = this.selection.attempt();
            this.selection = this.selection.blamed();
            presentation = this.selection.presentation();
            // The start-up line names the look this process runs: the fallback is the default look.
            trace.look(presentation.look());
            Thread.ofVirtual().name("jasper-presentation-attempt").start(attempt::failed);
        }
        // App-native menus show each item's action binding from the live keymap.
        themes.appearance().shortcuts(this::shortcutLabel);
        notificationCenter = new NotificationCenter(SwingUtilities::invokeLater, java.time.Clock.systemDefaultZone(), this::presence);
        notificationViews = new NotificationsFeature(notificationCenter, contributions,
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), presentation().projections().panels());
        systemNotices = new SystemNotices(notificationCenter, this::systemNotificationsEnabled,
            new SystemNotices.Windows(this::raiseFor, notificationViews::showHistory, this::showErrors));
        incidents = new Incidents(notificationCenter.clock());
        incidentNotices = new IncidentNotices(notificationCenter, incidents, SwingUtilities::invokeLater,
            JasperApplication::afterDelay, this::pluginName, this::disablePlugin, this::showErrors);
        notificationCenter.reportActionFailuresTo(failure -> incidentNotices.jasperFailure("a notification action", failure));
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        // The exit thread runs this after the bounded cleanup wait, so a replacement never meets this process's endpoint.
        this.shutdown = new ApplicationShutdown(() -> { relaunchIfRequested(); terminate.run(); });
        this.shellIntegrationDir = shellIntegrationDir;
        this.themesFolder = themesFolder;
        buddy = new BuddyIntegration(buddyStateFile, SystemFonts.system(java.awt.Font.PLAIN, 13f),
            themes.current().chrome().dark(), this::raiseTerminal, this::toggleBuddy, this::owns);
        buddyAppearance = themes.subscribe((theme, changed) -> buddy.appearance(SystemFonts.system(java.awt.Font.PLAIN, 13f), theme.chrome().dark()));
        notifications = new CommandNotifier(() -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
            buddy.companion(), notificationCenter, buddy.companion()::setWorking, JasperApplication::afterDelay);
        configuration = service == null ? null : new ConfigurationController(themes, service, this::openForEditing,
            systemAppearance.current(), themesFolder, LookMotion.of(presentation.projections()));
        // Without a configuration the setting is its default, on; the look still decides (spec 10.9).
        if (configuration == null) themes.configureAnimations(LookMotion.of(presentation.projections()).allows(true));
        if (configuration != null) systemAppearanceListening = systemAppearance.subscribe(configuration::systemAppearanceChanged);
        Supplier<TerminalConfig.Shell> savedShell = () -> (configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot()).terminal().shell();
        shellProfiles = ShellProfileCatalog.system(savedShell);
        // A supplied launcher (the benchmark, tests) never starts a detected profile, so reg.exe and wsl.exe stay
        // unrun; nothing waits on that catalog, because awaitDetection returns at once when nobody refreshed it.
        if (suppliedLauncher == null) {
            warnWhenDetected(shellProfiles, savedShell, this::shellProfileProblem);
            shellProfiles.refresh();
        }
        // The indicator reads the heap only while shown; nothing here runs before the first output paints.
        memoryIndicator = new MemoryIndicator(contributions, dev.jasper.app.vmoptions.HeapReading::current, System::gc,
            MemoryIndicator::swingTimer);
        if (configuration != null) {
            showMemoryIndicator(configuration.state());
            configuration.onState(this::showMemoryIndicator);
        }
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddy.configured(snapshot.buddyEnabled()); updateBuddyActions();
            loginItems.accept(snapshot.backgroundEnabled());
            if (plugins != null) { plugins.configurationChanged(snapshot.plugins()); reportBindingProblems(); }
            updateMigrateAction();
        });
        if (configuration != null) {
            applyGroupModes(configuration.state());
            configuration.onState(this::applyGroupModes);
        }
        if (configuration != null) {
            var problems = new ConfigProblemNotices(notificationCenter, this::showConfigurationProblems, this::openConfigFile);
            configuration.onProblems(problems::accept);
        }
        if (supports(Desktop.Action.APP_QUIT_HANDLER)) {
            Desktop.getDesktop().setQuitHandler((event, response) -> nativeQuitRequested(response));
            quitHandlerInstalled = true;
        }
        // Clicking the Dock icon of a running Jasper creates no process: AppKit sends this instead.
        // Registered whether or not residency is on, because a window that is merely minimized
        // should come back the same way.
        if (supports(Desktop.Action.APP_EVENT_REOPENED)) {
            reopenListener = event -> SwingUtilities.invokeLater(this::reopened);
            Desktop.getDesktop().addAppEventListener(reopenListener);
        }
        if (supports(Desktop.Action.APP_PREFERENCES)) {
            Desktop.getDesktop().setPreferencesHandler(event -> SwingUtilities.invokeLater(() -> openSettings(null, null)));
            preferencesHandlerInstalled = true;
        }
        // Jasper › About Jasper: this window instead of AWT's default panel.
        if (supports(Desktop.Action.APP_ABOUT)) {
            Desktop.getDesktop().setAboutHandler(event -> SwingUtilities.invokeLater(this::showAbout));
            aboutHandlerInstalled = true;
        }
        } catch (RuntimeException | Error failure) {
            systemAppearanceListening.run();
            systemAppearance.close();
            throw failure;
        }
    }

    /** Swing's timer, as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable afterDelay(java.time.Duration delay, Runnable task) {
        javax.swing.Timer timer = new javax.swing.Timer(
            (int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }

    /** Tests replace the process-wide trace. EDT. */
    void trace(StartupTrace value) { trace = java.util.Objects.requireNonNull(value, "value"); }

    /** Tests shorten the fallback that settles a shell printing nothing, 2 s by default. Set before {@link #residency}. EDT. */
    void settleFallback(Duration delay) { settleFallback = java.util.Objects.requireNonNull(delay, "delay"); }

    /**
     * The first plain window of a process is its cold start, unless the process already warmed up (a {@code --background}
     * launch rehearsed a window): then it comes from a menu or the Dock, like every later plain window.
     */
    static String windowTrigger(int windowsOpened, boolean warmedUp) { return windowsOpened == 0 && !warmedUp ? "cold" : "menu"; }

    /** The opacity in effect behind {@code component}: its terminal window's within that window's workspace area, else 1. */
    private double glassOpacity(Component component) {
        for (TerminalWindow window : windows) if (window.owns(component)) return window.content().workspaceOpacity(component);
        return 1.0;
    }

    private void glassChanged() { for (Runnable listener : List.copyOf(glassListeners)) listener.run(); }

    /** A window whose tab starts where a new shell starts: the user's home, which a WSL profile reads as its Linux home. */
    public TerminalWindow newWindow() {
        return openWindow(null, false,
            windowTrigger(windowsOpened, trace.millis(StartupTrace.Milestone.WARMED_UP).isPresent()));
    }

    /** A window whose tab starts in {@code directory}; null opens a detached window with no tab. */
    public TerminalWindow newWindow(Path directory) { return openWindow(directory, directory == null, "menu"); }

    /** New windows take live settings while retaining the application startup decoration choice. */
    ConfigSnapshot windowConfiguration() {
        ConfigSnapshot latest = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        return latest.toBuilder().viewMode(startupViewMode).build();
    }

    private TerminalWindow openWindow(Path directory, boolean detached, String trigger) {
        contributions.content().setFactory(separate -> {
            var created = separate ? newWindow(null) : newWindow();
            if (created == null) throw new IllegalStateException("Application is closing");
            return created.content().id();
        });
        if (quitting) return null;
        // Counted until composeWindow returns or fails. Incidents are always queued, so none lands inside the count; it
        // protects what is posted directly on the EDT while composing (a plugin's panel factory posting inside
        // connectContributions, say): that waits for the window's card, as it does before the first window of a start.
        openingWindows++;
        try {
            return composeWindow(directory, detached, trigger);
        } finally {
            openingWindows--;
        }
    }

    private TerminalWindow composeWindow(Path directory, boolean detached, String trigger) {
        WindowTrace windowTrace = trace.window(trigger);
        windowsOpened++;
        boolean first = windows.isEmpty();
        ShellLauncher launcher = suppliedLauncher != null ? suppliedLauncher : windowLauncher(launches,
            configuration == null ? ConfigSnapshot::defaults : configuration::snapshot,
            (path, settings) -> track(watchOutput(startSession(path, settings))), shellIntegrationDir, shellProfiles::resolve,
            () -> shellProfiles.awaitDetection(DETECTION_WAIT), this::shellProfileProblem, this::paneVariables, this::pluginShellVariables);
        // Back in the Dock before the window exists: macOS gives a window of a hidden application neither Jasper's menu
        // bar nor focus. If composing fails nothing opened, so the presence is worked out again.
        windowOpening();
        TerminalWindow window = null;
        try {
            window = new TerminalWindow(new WindowCallbacks(
                requested -> { if (requested == null) newWindow(); else newWindow(requested); }, this::quitRequested,
                this::windowActivated, this::windowClosed,
                state -> windowStateChanged(state.window(), state.showing(), state.iconified())),
                launcher, directory, detached, themes, configuration == null ? null : windowConfiguration(), history, windowLook());
            if (configuration != null) configuration.register(window.content());
            window.content().connectContributions(contributions, uiState);
            UUID windowId = window.content().id();
            window.content().onError = (title, details) -> notificationViews.error(title, details, windowId);
            window.content().onInfo = (title, details) -> notificationViews.info(title, details, windowId);
            window.content().onLaunchFailure = (details, cause) -> incidentNotices.launchFailure(windowId, details, cause);
            window.content().onOpenFile = (file, line) -> openFileAt(file, line, windowId);
            // File › Open File… (spec 13): the plugins' file chooser over this window; nothing before plugins start.
            window.content().fileChooser = folder -> auxiliary == null ? Optional.empty() : auxiliary.openFile(windowId, folder);
            notificationViews.attach(window.content());
            window.content().connectTerminals(terminals, window::toFront);
            if (WORKSPACE_GLASS) window.content().setGlassListener(this::glassChanged);
            window.content().onToggleBuddy = this::toggleBuddy;
            window.content().onFirstTerminal(pane -> timeFirstTerminal(pane, windowTrace));
            window.content().buddyEnabled = this::buddyEnabled;
            window.content().anyWindowActive = () -> windows.stream().anyMatch(open -> open.content().isActiveAndOpen());
            window.content().activity(event -> {
                switch (event) {
                    case WorkspaceActivity.Started value -> notifications.started(value.id(), value.command(),
                        value.elapsedNanos(), value.activate(), value.watched());
                    case WorkspaceActivity.Finished value -> notifications.finished(value.id(), value.command(),
                        value.exitStatus(), value.duration(), new CommandNotice.Origin(value.origin().anyWindowActive(),
                            value.origin().ownWindowActive(), value.origin().ownTabSelected(), value.origin().ownPaneFocused()), windowId, value.activate());
                    case WorkspaceActivity.TitleChanged value -> notifications.titleChanged(value.id(), value.title());
                    case WorkspaceActivity.PaneState value -> {
                        switch (value.state()) {
                            case OPENED -> notifications.opened(value.id());
                            case CLOSED -> notifications.closed(value.id());
                            case FOCUSED -> { }
                            case BLURRED -> notifications.hidden(value.id());
                        }
                    }
                }
            });
            windows.add(window); window.restoreLayout(uiState); startupSteps.workstation(); window.show();
            memoryIndicator.windows(windows.size());
        } catch (RuntimeException | Error failure) {
            // A half-composed window must not outlive the failure, nor keep Jasper in the Dock with nothing open.
            try {
                if (window != null) discard(window, failure);
            } finally {
                updateDock();
            }
            throw failure;
        }
        windowTrace.mark(WindowTrace.Milestone.SHOWN);
        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);
        startupSteps.done();
        return window;
    }

    /**
     * Closes a window whose composition failed, without saving its bounds or dock layout: it must not become the last
     * window closed, whose layout the next window restores. If closing it fails too, the window is still forgotten, as
     * {@link #windowClosed} forgets one (by the window list, the notification cards and the Buddy, which would otherwise
     * keep routing to it, and as the window used last) and its frame disposed, and each further failure is added to
     * {@code failure} as suppressed. EDT.
     */
    private void discard(TerminalWindow window, Throwable failure) {
        try {
            window.discard();
        } catch (RuntimeException | Error closing) {
            failure.addSuppressed(closing);
            try {
                forget(window);
            } catch (RuntimeException | Error forgetting) {
                failure.addSuppressed(forgetting);
            }
            try {
                window.nativeWindow().dispose();
            } catch (RuntimeException | Error disposing) {
                failure.addSuppressed(disposing);
            }
        }
    }

    /**
     * Whether this process outlives its windows. Decided once at startup: which process owns the
     * handoff endpoint is not something to renegotiate while running. The indicator appears once start-up has
     * settled: the first output painted, a finished {@code --background} warm-up, or 2 s after the first paint.
     */
    public void residency(boolean resident) {
        this.resident = resident;
        if (!resident) {
            stopSettleFallback();
            showIndicator(false);
            return;
        }
        // Never on the start-up path: not at the first paint, which comes before the first shell output is shown.
        Runnable settled = this::residencySettled;
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, settled);
        // A shell that prints nothing never paints output; the first paint then starts a short countdown.
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, this::armSettleFallback);
    }

    public boolean resident() { return resident; }

    /** Where {@code jasper --help} reads the subcommands; before {@link #startPlugins}. Never set for a private launch. */
    public void subcommandList(Path file) { subcommandList = file; }

    /** The command socket this Jasper serves, named in each new local shell's {@code JASPER_COMMAND_SOCKET}. */
    public void commandSocket(Path socket) { commandSocket = socket; }

    /** The command socket new local shells are given; null when this Jasper serves none. */
    public Path commandSocket() { return commandSocket; }

    /**
     * Opens a file for editing on a worker thread: the editor a plugin provides, else the desktop's edit or open action,
     * else revealing the file. Reads {@link #plugins} at call time, so openers built before the plugins start use it too.
     */
    private void openForEditing(Path file) {
        var desktop = new dev.jasper.app.platform.ConfigEditor();
        PluginRuntime runtime = plugins;
        if (runtime == null) desktop.open(file); else runtime.open(file, desktop::open);
    }

    /**
     * Opens a file that a Cmd-click in window {@code window} named, at {@code line} (0 for none), when it is an existing
     * regular file: on a virtual thread, through the editor a plugin provides, else the desktop's edit or reveal action.
     * The file's path is program output, so it is opened for editing only: never the desktop's open action, which runs
     * programs and scripts. Anything else opens nothing and tells nobody. Reads {@link #plugins} when the thread runs.
     */
    void openFileAt(Path file, int line, UUID window) {
        openFileAt(file, line, window, new dev.jasper.app.platform.ConfigEditor());
    }

    /** As {@link #openFileAt(Path, int, UUID)}, falling back to {@code desktop}; tests inject its actions. */
    void openFileAt(Path file, int line, UUID window, dev.jasper.app.platform.ConfigEditor desktop) {
        Thread.ofVirtual().name("jasper-open-file").start(() -> {
            try {
                if (!java.nio.file.Files.isRegularFile(file)) return;
                PluginRuntime runtime = plugins;
                if (runtime == null) desktop.editOrReveal(file);
                else runtime.open(file, line > 0 ? OptionalInt.of(line) : OptionalInt.empty(), Optional.of(window), desktop::editOrReveal);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not open a clicked file", failure);
            }
        });
    }

    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */
    private Map<String, String> pluginShellVariables(Map<String, String> inherited) {
        PluginRuntime runtime = plugins;
        return runtime == null ? Map.of() : runtime.shellVariables(inherited);
    }

    /** What a new local shell in {@code pane} gets last, over everything else. Any thread. */
    Map<String, String> paneVariables(UUID pane) {
        Path socket = commandSocket;
        return socket == null ? Map.of("JASPER_PANE", pane.toString())
            : Map.of("JASPER_PANE", pane.toString(), "JASPER_COMMAND_SOCKET", socket.toString());
    }

    /**
     * What the command socket runs: no arguments opens a window or raises one, as a launch would; anything else is a
     * plugin's subcommand. Called on listener threads.
     */
    public CommandDispatcher commands() {
        return invocation -> {
            if (invocation.arguments().isEmpty()) {
                SwingUtilities.invokeLater(() -> openOrRaise(HANDOFF));
                return CompletableFuture.completedFuture(0);
            }
            PluginRuntime runtime = plugins;
            if (runtime == null) {
                invocation.err("jasper: Jasper's plugins are not running\n");
                return CompletableFuture.completedFuture(1);
            }
            return runtime.commands().dispatch(invocation);
        };
    }

    /** Quits a Jasper that is not resident once nothing keeps it: no terminal or plugin window, and {@code busy} false. EDT. */
    public void quitIfUnused(BooleanSupplier busy) {
        if (resident || quitting || stopped) return;
        boolean pluginWindowsOpen = auxiliary != null && !auxiliary.open().isEmpty();
        if (windows.isEmpty() && !pluginWindowsOpen && !busy.getAsBoolean()) requestShutdown();
    }

    /**
     * Who shows that this process is resident (the macOS menu bar item, the Windows notification-area icon), given its
     * menu's actions; it returns the way to remove what it showed. Injected, like {@link #loginItems}, so tests never
     * touch the real menu bar or notification area. Install before {@link #residency}.
     */
    public void residentIndicator(Function<dev.jasper.app.platform.ResidentTray.Actions, Runnable> install) {
        residentIndicator = Objects.requireNonNull(install, "install");
    }

    /**
     * Where notifications go while Jasper is away: this desktop's banners, balloons or {@code notify-send}. Injected,
     * like {@link #residentIndicator}, so tests never reach the real operating system; the application closes it.
     */
    public void systemNotifier(SystemNotifier notifier) {
        systemNotices.use(Objects.requireNonNull(notifier, "notifier"));
    }

    /**
     * macOS, a packaged Jasper: while resident, Quit closes every window and keeps the process, hidden from the Dock and
     * Cmd+Tab until the next window opens. Install before {@link #residency}.
     *
     * @param control switches the Dock presence and watches for a log-out or shutdown
     * @param visibleAtLaunch false for a {@code --background} launch, which starts hidden
     */
    public void backgroundOnQuit(DockControl control, boolean visibleAtLaunch) {
        dock = new DockPresence(control, visibleAtLaunch);
    }

    /**
     * Tells this process how to notice that it was updated on disk. Install before the first reopen.
     *
     * @param stale true once the application jar this process runs from has changed or gone
     */
    public void staleBuild(BooleanSupplier stale) {
        staleBuild = Objects.requireNonNull(stale, "stale");
    }

    /**
     * The macOS reopen event: a Dock click, Spotlight or {@code open -a} reaching the running Jasper. With no window open
     * and the application replaced on disk since start-up, it relaunches as the new build through the restart path and
     * exits; if that cannot happen (the command line is unknown, or a plugin refuses), the running build opens the
     * window. With windows open it raises them: an update never takes a running terminal down. EDT.
     */
    void reopened() {
        if (quitting || stopped) return;
        if (resident && !windowsOpen() && staleBuild.getAsBoolean()) {
            restart(RestartMode.SAME);
            if (quitting || stopped) return;
            LOG.log(System.Logger.Level.WARNING,
                "Jasper was updated on disk but could not relaunch as the new build; this window comes from the running build");
        }
        openOrRaise("reopen");
    }

    private void showIndicator(boolean show) {
        if (show && removeIndicator == null && !quitting && !stopped) {
            removeIndicator = residentIndicator.apply(new dev.jasper.app.platform.ResidentTray.Actions(
                () -> openOrRaise("menu"), this::newWindowFromMenuBar, this::settingsFromMenuBar, this::quit));
        } else if (!show && removeIndicator != null) {
            Runnable remove = removeIndicator;
            removeIndicator = null;
            remove.run();
        }
    }

    /** The first paint is in: unless something settles start-up first, it does after {@link #settleFallback}. EDT. */
    private void armSettleFallback() {
        if (startupSettled || cancelSettleFallback != null || !resident || quitting || stopped) return;
        cancelSettleFallback = afterDelay(settleFallback, this::residencySettled);
    }

    private void stopSettleFallback() {
        Runnable cancel = cancelSettleFallback;
        cancelSettleFallback = null;
        if (cancel != null) cancel.run();
    }

    /**
     * Start-up has settled while resident: the macOS bridge loads, so a log-out is recognised from here on, and the
     * indicator appears. Runs once per process, whichever of the first output painted, a finished warm-up or the
     * fallback after the first paint comes first. EDT.
     */
    private void residencySettled() {
        if (startupSettled || !resident || quitting || stopped) return;
        startupSettled = true;
        stopSettleFallback();
        try {
            if (dock != null) dock.prepare();
        } finally {
            // Settling happens once and nothing retries it: a bridge that throws must not leave a resident process unseen.
            showIndicator(true);
        }
    }

    /** The menu bar item's New Window: always a new window, in front of other applications. EDT. */
    private void newWindowFromMenuBar() {
        if (quitting || stopped) return;
        openWindow(null, false, "menu");
        requestForeground();
    }

    /** The menu bar item's Settings\u2026: over the last active window, or on its own while Jasper has none. EDT. */
    private void settingsFromMenuBar() {
        if (quitting || stopped) return;
        requestForeground();
        openSettings(null, null);
    }

    /** Brings Jasper in front of other applications: choosing a menu bar item does not activate its application. */
    private static void requestForeground() {
        if (supports(Desktop.Action.APP_REQUEST_FOREGROUND)) Desktop.getDesktop().requestForeground(false);
    }

    /**
     * The handoff endpoint is gone, so nothing can reach this process any more. It stops being
     * resident, and with no windows left there is nothing to wait for: shut down properly rather
     * than letting the JVM starve once the accept thread ends, which skips every close.
     * Distinct from {@link #residency} because that is also how startup reports a failed bind,
     * before any window exists.
     *
     * <p>When windows are still open, this only clears residency and shutdown waits for the last
     * one to close, the same path {@link #windowClosed} already takes. That branch needs a live
     * window and is not covered by a headless test — a known gap, not an oversight.
     */
    public void endpointReleased() {
        residency(false);
        if (windows.isEmpty()) requestShutdown();
    }

    /**
     * Who reconciles the user's login items with the setting. Injected, because reconciling from
     * here would have these tests write into the developer's real login items. Setting it replays
     * the current value at once: the constructor's own listener registration has already fired by
     * the time production can install this.
     */
    public void loginItems(java.util.function.Consumer<Boolean> reconcile) {
        loginItems = Objects.requireNonNull(reconcile, "reconcile");
        if (configuration != null) loginItems.accept(configuration.snapshot().backgroundEnabled());
    }

    /**
     * The handoff, the macOS reopen event and the resident indicator share this: raise what is open, or open the
     * first window where a new shell starts. {@code trigger} names the caller for start-up timing.
     */
    public void openOrRaise(String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(null, false, trigger);
        else raiseTerminal();
    }

    /** As {@link #openOrRaise(String)}, opening the first window in {@code directory}. */
    public void openOrRaise(Path directory, String trigger) {
        if (quitting || stopped) return;
        if (windows.isEmpty()) openWindow(directory, directory == null, trigger);
        else raiseTerminal();
    }

    /**
     * Pays the first window's one-time costs with nothing on screen, then runs {@code done} on the EDT, whatever happens
     * on the way: even if building the fonts or composing, painting or closing the rehearsal fails, and an
     * {@link Error} propagates only after {@code done} ran. Constructing this application installed the look and
     * feel; this builds the configured font set and rehearses a window: composed with the configured grid, theme and
     * contributions, its terminal fed a canned prompt from memory (no shell, no PTY), painted once into an image
     * through the window's own graphics pipeline, and disposed without ever being shown. Plugins never see its pane:
     * it is not registered with the terminal registry, and it is not a place where plugin content opens. It opens no
     * window timing and marks no first paint, so only {@code done} tells the trace the warm-up finished. Headless there
     * is nothing to rehearse with. Once {@code done} has run, whatever the outcome, one explicit collection starts off the
     * EDT, so the heap the rehearsal grew returns to the operating system. EDT.
     */
    public void warmUp(Runnable done) {
        Objects.requireNonNull(done, "done");
        Runnable finished = collectingAfter(done);
        // What a new window gets, start-up view mode included, so the rehearsal warms the chrome the next window shows.
        ConfigSnapshot snapshot = windowConfiguration();
        boolean rehearsing = false;
        try {
            FontConfig font = snapshot.font();
            try {
                new dev.jasper.terminal.rendering.FontSet(font.family(), font.size(), font.fallback(),
                    font.ligatures(), font.lineHeight());
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Font warm-up failed; the first window will pay for it", failure);
            }
            rehearsing = !GraphicsEnvironment.isHeadless() && !quitting && !stopped;
        } finally {
            // Nothing to rehearse, or the font set threw an Error: finished now. Otherwise the rehearsal reports it.
            if (!rehearsing) finished.run();
        }
        if (rehearsing) {
            // This bypasses openWindow: no window timing and no FIRST_PAINT or OUTPUT_PAINTED, which would settle
            // residency for a window nobody sees. Only done marks WARMED_UP.
            Rehearsal.run(() -> Rehearsal.feed(snapshot.columns(), snapshot.lines()), feed -> rehearsalWindow(feed, snapshot),
                finished, Rehearsal.FALLBACK);
        }
    }

    /**
     * {@code done} followed, whether it returned or threw, by one {@link #collector} run on a new daemon thread. The thread
     * starts from a later EDT event, not from the one that finished the rehearsal: that event's frames still hold the
     * closed window, which the collection could not free. An {@link Error} from {@code done} still propagates; the
     * returned task runs on the EDT, like {@link #warmUp}.
     */
    Runnable collectingAfter(Runnable done) {
        return () -> {
            try {
                done.run();
            } finally {
                SwingUtilities.invokeLater(() -> Thread.ofPlatform().daemon().name("jasper-warm-up-collection").start(collector));
            }
        };
    }

    /**
     * The rehearsal's window around {@code feed}: wired to the contributions but registered nowhere else, and silent,
     * so a problem is logged rather than shown. Its frame is always disposed, even when closing the window fails. If the
     * window's own constructor fails there is no window to reach its frame through; nothing was shown. EDT.
     */
    private Rehearsal.Stage rehearsalWindow(Rehearsal.Feed feed, ConfigSnapshot snapshot) {
        TerminalWindow window = new TerminalWindow(Rehearsal.CALLBACKS, Rehearsal.launcher(feed), null, false, themes, snapshot,
            new CommandHistory(), windowLook());
        // The pane's launch completion is queued, so this is in place before a failed launch could ask for a dialog.
        Rehearsal.silence(window.content());
        // Discarded, not closed: never shown and at the default size, it must not become the last window closed.
        Runnable close = () -> Rehearsal.closeAndDispose(window::discard, window.nativeWindow()::dispose);
        // Its own layout state, in memory and never saved, carrying only Zen mode: it warms the Zen chrome when the next
        // window will show it.
        UiState rehearsalState = UiState.inMemory();
        rehearsalState.setZenMode(uiState.zenMode());
        try {
            window.content().connectContributions(contributions, rehearsalState, false);
        } catch (RuntimeException | Error failure) {
            try {
                close.run();
            } catch (RuntimeException | Error closing) {
                failure.addSuppressed(closing);
            }
            throw failure;
        }
        return new Rehearsal.Stage(() -> {
            Rehearsal.paint(window.nativeWindow());
            rehearsalPainted();
        }, close);
    }

    /** Launch housekeeping for plugins: pending removals and installs. Off the EDT, before {@link #startPlugins}. */
    public static void preparePlugins(AppDirs dirs) {
        // The pre-2026-09-22 data directory, migrated into plugins/<id>/data once.
        PluginRuntime.maintain(dirs.plugins(), dirs.pluginState(), dirs.pluginLock(), dirs.root().resolve("plugin-data"));
    }

    /**
     * Scans for plugins here and starts them: for an application made without the bootstrap's selection. A scan that
     * chooses a presentation is refused, because the look must be chosen before the application is made.
     * {@code codeLocation} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeLocation, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        if (!selection.isNone()) throw new IllegalStateException("This application starts the bootstrap's plugin selection");
        var scanned = dev.jasper.app.plugins.PluginSelection.of(dirs, codeLocation, "", developmentDirectory, safeMode);
        if (scanned.presentation().provider().isPresent())
            throw new IllegalStateException("A presentation provider needs the bootstrap's selection");
        selection = scanned;
        startPlugins(dirs);
    }

    /** Starts the selection's plugins once, before the first window, so their contributions are in place when windows appear. */
    public void startPlugins(AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        if (selection.isNone()) {
            var scanned = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "", null, false);
            if (scanned.presentation().provider().isPresent())
                throw new IllegalStateException("A presentation provider needs the bootstrap's selection");
            selection = scanned;
        }
        activityNotifier = new ActivityNotifier(buddy.companion(), this::raiseTerminal);
        uiState = UiState.load(dirs.uiState(), presentation().projections().dockLayout().key());
        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(), this::quit, dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));
        auxiliary = new AuxiliaryWindows(uiState, shells::create, shells::choosePaths, presentation.projections().dialogs());
        auxiliary.onAllClosed = () -> {
            if (windows.isEmpty() && !resident && !quitting) requestShutdown();
            else updateDock();
        };
        auxiliary.onFirstOpened = this::windowOpening;
        logFolder = dirs.logs();
        errorsWindow = new ErrorsWindow(contributions, auxiliary, incidents, new ErrorsView.Hooks(this::pluginName,
            id -> plugins == null ? "" : plugins.pluginVersions().getOrDefault(id, ""), IncidentReport.Environment.current(),
            this::copyReport, incidentNotices::disablePlugin, this::openLogFolder), notificationCenter.clock().getZone(),
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));
        aboutWindows = new AboutWindows(contributions, auxiliary, () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
            new AboutWindows.Hooks(dev.jasper.app.platform.BuildInfo.current(), IncidentReport.Environment.current(),
                notificationCenter.clock().getZone(), this::copyBuildInformation, this::browse, JasperApplication::afterDelay));
        plugins = new PluginRuntime(selection.options(), activityNotifier,
            new PluginRuntime.SettingsReports(
                diagnostic -> { if (configuration != null) configuration.report(diagnostic); },
                file -> { if (configuration != null) configuration.clearReports(file); }), contributions, auxiliary, terminals,
            new PluginRuntime.NotificationHost(notificationCenter,
                caused -> incidentNotices.pluginNotice(caused.pluginId(), caused.request(), caused.cause(), caused.posted()),
                (title, details) -> notificationCenter.submit(NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.ERROR, title)
                    .withContent(details))),
            new dev.jasper.app.platform.ConfigEditor()::open,
            new PluginRuntime.ShellProfiles(shellProfiles::current, shellProfiles::onChanged));
        plugins.glass(WORKSPACE_GLASS ? new dev.jasper.app.plugins.GlassSource(this::glassOpacity, listener -> {
            glassListeners.add(listener);
            return new Subscription(() -> glassListeners.remove(listener));
        }) : dev.jasper.app.plugins.GlassSource.NONE);
        plugins.reportFailuresTo(failure -> incidentNotices.pluginFailure(failure.pluginId(), failure.operation(), failure.cause()));
        plugins.animations(themes::animationsEnabled);
        // The kit the controller installed and the look plugins read: the presentation's, or the fallback when the kit was blamed (spec 4.7).
        plugins.presentation(themes.kit(), presentation.projections().plugins(), presentation.defaults());
        plugins.shortcuts(this::shortcutLabel);
        plugins.clipboard(clipboard);
        plugins.commandClient(CommandInstall.client(System.getProperty("os.name"), System.getProperty("jpackage.app-path"),
            System.getProperty("jasper.commandClient")));
        plugins.appearance(themes::current, handler -> themes.subscribe((value, changed) -> handler.accept(value)));
        // The application's own entry, contributed like any other action so it is in the palette, rebindable and in the menu.
        contributions.addAction("plugins.manage", "Manage Plugins…", null, List.of("plugins", "extensions", "install", "safe mode"),
            Optional.empty(), invocation -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID));
        contributions.addMenuSection(MenuTarget.standard(MenuTarget.Slot.FILE)).set(List.of(new MenuEntry.Item("plugins.manage")));
        if (configuration != null) configuration.pluginSettingsExist(plugins::settingsFileExists);
        migrateAction = contributions.addAction("config.migrate", "Migrate Config", null,
            List.of("configuration", "settings", "upgrade", "old keys", "config.toml"), Optional.empty(), invocation -> migrateConfiguration());
        updateMigrateAction();
        commandInstall = CommandInstall.forThisPlatform(System.getProperty("os.name"), System.getProperty("jpackage.app-path"),
            Path.of(System.getProperty("user.home")));
        List<String> commandKeywords = List.of("jasper", "command", "shell", "terminal", "path", "cli");
        contributions.addAction("commandline.install", "Install 'jasper' Command in PATH", null, commandKeywords,
            Optional.empty(), invocation -> installCommand(false)).setEnabled(commandInstall.available());
        contributions.addAction("commandline.uninstall", "Uninstall 'jasper' Command from PATH", null, commandKeywords,
            Optional.empty(), invocation -> uninstallCommand()).setEnabled(commandInstall.available());
        contributions.content().setFactory(separate -> {
            var created = separate ? newWindow(null) : newWindow();
            if (created == null) throw new IllegalStateException("Application is closing");
            return created.content().id();
        });
        if (subcommandList != null) plugins.subcommandList(subcommandList);
        plugins.starting(startupSteps::plugin);
        plugins.start(selection, configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome().dark());
        new PluginProblemNotices(notificationCenter, incidentNotices,
            () -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID)).announce(plugins.problems());
        // Spec 4.6: a look the attempt record skipped says so once, and Try Again forgets the record and restarts.
        selection.skipped().ifPresent(skipped -> notificationCenter.post(NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.WARNING,
                "Jasper started with its standard look")
            .withContent("The last start with the " + skipped.name() + " look did not finish, so Jasper uses its standard look until you try again.")
            .withAction(new dev.jasper.app.notifications.NotificationAction("Try Again", () -> Thread.ofVirtual().name("jasper-presentation-retry").start(() -> {
                skipped.attempt().clear();
                SwingUtilities.invokeLater(() -> {
                    if (!restart(RestartMode.SAME))
                        notificationCenter.post(NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.INFO, "Restart Jasper to try the look again")
                            .withContent("Jasper could not work out how it was started. Quit and reopen it to start with the " + skipped.name() + " look."));
                });
            }), false))));
        // Spec 5.8 and 5.9: a look that starts without some of its files says so once.
        var missing = new ArrayList<String>(lookFontProblems);
        presentation.droppedDefaults().forEach(key -> missing.add("[defaults].\"" + key + "\": its theme did not load, so the setting keeps its core default"));
        presentation.provider().filter(provider -> !missing.isEmpty()).ifPresent(provider -> notificationCenter.post(NotificationRequest.of(
            IncidentNotices.PLUGINS.id(), Severity.WARNING, "The " + provider.name() + " look is missing some of its files")
            .withContent(String.join("\n", missing))));
        // Spec 4.6: the first window's first paint proves the look, off the EDT.
        trace.when(StartupTrace.Milestone.FIRST_PAINT, task -> Thread.ofVirtual().name("jasper-presentation-proven").start(task),
            () -> presentationAttempt().proven());
        boolean macOs = configuration == null ? System.getProperty("os.name").startsWith("Mac") : configuration.macOs();
        var keymap = new dev.jasper.app.shortcuthelp.KeymapPage.Hooks(contributions,
            () -> (configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot()).keybindings(),
            plugins::pluginNames, macOs, changes -> configuration == null
                ? java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Settings are unavailable."))
                : configuration.saveKeybindings(changes, contributions.actions().stream()
                    .filter(a -> KeyBindings.extensionId(a.id()))
                    .map(ActionEntry::extension).toList()));
        var vmEditor = new dev.jasper.app.platform.ConfigEditor();
        vmOptions = new VmOptionsFeature(dev.jasper.app.vmoptions.VmOptionsLaunch.current(),
            () -> dev.jasper.app.vmoptions.LauncherImage.locate(System.getProperty("jpackage.app-path")),
            image -> new dev.jasper.app.vmoptions.VmOptionsCheck(dev.jasper.app.vmoptions.VmOptionsCheck.runtimeJava(), image,
                dev.jasper.app.vmoptions.VmOptionsCheck::runProcess, Duration.ofSeconds(10)),
            work -> Thread.ofVirtual().name("jasper-vmoptions").start(work), SwingUtilities::invokeLater, notificationCenter,
            () -> restart(RestartMode.SAME), this::openForEditing, vmEditor::reveal,
            dev.jasper.app.vmoptions.VmOptionsLaunch::runningMaxHeapMiB, dev.jasper.app.vmoptions.VmOptionsLaunch::physicalMemoryMiB,
            Duration.ofSeconds(2));
        memoryWarnings = new MemoryWarnings(new dev.jasper.app.vmoptions.LowMemoryWatch(java.time.Clock.systemUTC()),
            signal -> dev.jasper.app.vmoptions.HeapThreshold.install(dev.jasper.app.vmoptions.LowMemoryWatch.FRACTION, signal)
                .<Runnable>map(threshold -> threshold::close).orElse(() -> { }),
            notificationCenter, dev.jasper.app.vmoptions.VmOptionsLaunch::runningMaxHeapMiB, vmOptions::savedHeapMiB,
            page -> openSettings(null, page));
        vmOptions.onHeapSaved(memoryWarnings::heapChanged);
        // Never on the start-up path: the options file is read and the heap watched and shown once the first output shows, the warm-up ends, or 5 s after the first paint.
        Runnable settled = () -> {
            vmOptions.startupSettled();
            memoryWarnings.start();
            memoryIndicator.ready();
        };
        trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.WARMED_UP, SwingUtilities::invokeLater, settled);
        trace.when(StartupTrace.Milestone.FIRST_PAINT, SwingUtilities::invokeLater, () -> afterDelay(Duration.ofSeconds(5), settled));
        contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("help", "keys", "bindings", "keymap", "edit shortcuts", "keyboard settings"),
            Optional.empty(), invocation -> openSettings(null, dev.jasper.app.shortcuthelp.KeymapPage.ID));
        contributions.addAction("app.memory", "Change Memory Settings…", null, List.of("memory", "heap", "xmx", "jvm", "java", "vm options"),
            Optional.empty(), invocation -> openSettings(invocation.windowId(), "system#vm.heap"));
        contributions.addAction("app.vmoptions", "Edit Custom VM Options…", null, List.of("memory", "heap", "xmx", "jvm", "java", "vm options", "vmoptions"),
            Optional.empty(), invocation -> {
                if (vmOptions.editable()) vmOptions.editCustomOptions();
                else openSettings(invocation.windowId(), "system#vm.heap");
            });
        contributions.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(AboutWindows.helpEntries(AboutWindows.aboutInApplicationMenu(macOs, presentation.committedDesktop().screenMenuBar())));
        if (configuration != null) {
            var commandLine = new dev.jasper.app.settingsui.CorePages.CommandLineActions(() -> commandInstall.state().text(),
                () -> installCommand(true),
                () -> commandInstall.state().kind() != CommandInstall.Kind.INSTALLED && commandInstall.available(),
                this::uninstallCommand, () -> commandInstall.state().kind() == CommandInstall.Kind.INSTALLED);
            var system = new dev.jasper.app.settingsui.CorePages.SystemActions(() -> configuration.state().file(),
                this::openConfigFile, this::revealConfigFile, () -> configuration.reload(), this::migrateConfiguration,
                configuration::migrationAvailable, Optional.of(vmOptions.hooks()), Optional.of(commandLine));
            var shells = new dev.jasper.app.settingsui.ShellChoices(() -> shellProfiles.current().stream()
                    .map(profile -> new dev.jasper.app.settingsui.ShellChoices.Choice(profile.id(), profile.label())).toList(),
                shellProfiles::refresh, shellProfiles::onChanged);
            // ThemeFolder.prepare runs on the import worker (ThemeMenu.chooseAndImport), so the folder reads the session's
            // fixed plugin tier, never ThemeController.library(), which requires the EDT.
            var pluginThemes = dev.jasper.app.appearance.ThemeLibrary.of(selection.themes());
            Optional<dev.jasper.app.settingsui.ThemeImports> imports = themesFolder == null ? Optional.empty()
                : Optional.of(SettingsAppearance.imports(new dev.jasper.app.appearance.ThemeFolder(themesFolder, () -> pluginThemes),
                    surface -> surface == null ? Optional.empty() : this.shells.chooseFile(surface, "Import Theme", ".json"),
                    this::confirmOver, this::informOver, configuration::save, configuration::reload));
            var appearanceChoices = SettingsAppearance.choices(systemAppearance, themes::library, imports);
            settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                dev.jasper.app.settingsui.CorePages.pages(appearanceChoices, system, shells,
                    new dev.jasper.app.settingsui.NotificationGroupChoices(() -> groupChoices(notificationCenter.groups(), this::pluginName, plugins == null ? Map.of() : plugins.pluginIcons())),
                    new dev.jasper.app.settingsui.CorePages.Pages(
                        context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context),
                        context -> plugins.settingsPages(context))),
                new dev.jasper.app.settingsui.SettingsDialog.Hooks(configuration::save, configuration::state, configuration::onState,
                    this::openConfigFile, this::migrateConfiguration, this::browse, (parent, count) -> settings.askDiscard(parent, count),
                    () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
                    presentation.projections().settings(), presentation.projections().composition()));
            configuration.settingsOpener((owner, page) -> openSettings(owner.id(), page));
            // Never on the start-up path: the font families are read once the first output shows or the warm-up ends.
            trace.when(StartupTrace.Milestone.OUTPUT_PAINTED, Runnable::run, dev.jasper.app.settingsui.SettingsDialog::prepareFonts);
            trace.when(StartupTrace.Milestone.WARMED_UP, Runnable::run, dev.jasper.app.settingsui.SettingsDialog::prepareFonts);
        }
        boolean[] replayed = new boolean[1];
        // subscribe replays the current theme at once; plugins read the look on demand, so only later changes are events.
        pluginTheme = themes.subscribe((theme, chromeChanged) -> {
            if (replayed[0] && chromeChanged) plugins.themeChanged(theme.chrome().dark());
            replayed[0] = true;
        });
        reportBindingProblems();
    }

        private void updateMigrateAction() {
            if (migrateAction != null && configuration != null) migrateAction.setEnabled(configuration.migrationAvailable());
        }

        private void migrateConfiguration() {
            if (configuration == null || quitting || stopped) return;
            configuration.migrate().whenComplete((message, failure) -> javax.swing.SwingUtilities.invokeLater(() -> {
                if (failure == null) notificationViews.info("Migrate Config", message);
                else notificationViews.error("Migrate Config failed",
                    (failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null ? failure.getCause() : failure).getMessage());
            }));
        }

        /**
         * Puts the jasper command on PATH off the EDT. Another program of that name is replaced only after Settings asks;
         * from the palette, Settings opens instead so the question has a place. EDT.
         */
        private void installCommand(boolean fromSettings) {
            CommandInstall install = commandInstall;
            if (install == null || !install.available()) return;
            CommandInstall.State state = install.state();
            boolean replace = false;
            if (state.kind() == CommandInstall.Kind.OTHER_PROGRAM) {
                if (!fromSettings || settings == null) {
                    openSettings(null, "system");
                    return;
                }
                if (!settings.confirm("Replace the jasper Command?", state.text() + ". Replace it with Jasper's command?", "Replace")) return;
                replace = true;
            }
            boolean replacing = replace;
            commandWork(() -> install.install(replacing), "Installed the jasper Command",
                () -> install.state().text() + install.hint(System.getenv().getOrDefault("PATH", "")).map(hint -> ". " + hint).orElse(""),
                "Could not install the jasper Command");
        }

        /** Takes the jasper command off PATH, if Jasper put it there, off the EDT; otherwise says it did not. EDT. */
        private void uninstallCommand() {
            CommandInstall install = commandInstall;
            if (install == null || !install.available()) return;
            CommandInstall.State state = install.state();
            if (state.kind() != CommandInstall.Kind.INSTALLED) {
                notificationViews.info("The jasper Command is not installed by Jasper", state.text());
                return;
            }
            commandWork(install::uninstall, "Removed the jasper Command", () -> "jasper is no longer on your PATH.",
                "Could not remove the jasper Command");
        }

        private interface CommandWork { void run() throws java.io.IOException; }

        private void commandWork(CommandWork work, String done, Supplier<String> details, String failed) {
            CompletableFuture.runAsync(() -> {
                try { work.run(); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }, task -> Thread.ofPlatform().daemon().name("jasper-command-install").start(task)).whenComplete((ignored, failure) -> {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
                if (cause instanceof java.io.UncheckedIOException unchecked) cause = unchecked.getCause();
                Throwable problem = cause;
                String text = problem == null ? details.get() : null;
                SwingUtilities.invokeLater(() -> {
                    if (problem == null) notificationViews.info(done, text);
                    else if (problem instanceof CommandInstall.Declined) notificationViews.info(failed, "The administrator prompt was cancelled; nothing changed.");
                    else notificationViews.error(failed, problem.getMessage());
                    if (settings != null) settings.refreshPages();
                });
            });
        }

        /**
         * Opens Settings over the given window, or the active one when null, at a page (the last visited when null). The
         * dialog is modal, so the first call returns once it closed; then a pending VM-options restart is repeated.
         */
        void openSettings(java.util.UUID ownerOrNull, String pageOrNull) {
            if (settings == null || quitting || stopped) return;
            java.util.UUID owner = ownerOrNull != null ? ownerOrNull
                : lastActive != null && windows.contains(lastActive) ? lastActive.content().id() : null;
            boolean opening = !settings.isOpen();
            settings.open(owner, pageOrNull);
            if (opening && !settings.isOpen() && vmOptions != null && !quitting && !stopped) vmOptions.settingsClosed();
        }

        private void openConfigFile() {
            if (configuration == null) return;
            configuration.openConfigFile().whenComplete((file, failure) -> {
                if (failure != null) SwingUtilities.invokeLater(() -> notificationViews.error("Could not open config.toml", failure.getMessage()));
            });
        }

        private void revealConfigFile() {
            if (configuration == null) return;
            Path folder = configuration.state().file().toAbsolutePath().getParent();
            CompletableFuture.runAsync(() -> new dev.jasper.app.platform.ConfigEditor().reveal(folder))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) SwingUtilities.invokeLater(() -> notificationViews.error("Could not reveal the folder", folder.toString()));
                });
        }

        private void browse(java.net.URI uri) {
            if (!supports(Desktop.Action.BROWSE)) return;
            CompletableFuture.runAsync(() -> {
                try { Desktop.getDesktop().browse(uri); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
        }

    /**
     * Where Jasper stands for notifications: focused, in the background, idle (resident, start-up settled, with no window
     * of any kind open and no terminal window being opened) or with no window yet. Idle waits for start-up to settle
     * because incidents raised while the application was built are recorded later through {@code invokeLater}, in a
     * {@code --background} launch with the process already resident and windowless: they must wait for the first window
     * like the problems posted directly. With only a plugin window open Jasper is not idle, because cards target
     * terminal windows: the next one still shows what waited. EDT.
     */
    private Delivery.Presence presence() {
        boolean residentIdle = resident && startupSettled && !windowsOpen() && openingWindows == 0;
        return presence(!windows.isEmpty(), activatedSinceOpen,
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null, residentIdle);
    }

    /**
     * The rule behind {@link #presence()}. A resident Jasper whose start-up has settled, with no window open and none
     * being opened, is idle ({@code residentIdle}), however long that lasts: an ordinary card would only pile up, so it
     * goes to history, and the operating system shows it meanwhile. Otherwise a window counts only once it has been
     * activated: a failure raised while the first window opens is queued ahead of the native activation event, and
     * judging it "background" would keep its card out of sight for good. Until then the card waits, as it does with no
     * window at all, and shows on activation; the operating system is left alone, since the card is about to show.
     */
    static Delivery.Presence presence(boolean windowsOpen, boolean activatedSinceOpen, boolean jasperWindowActive, boolean residentIdle) {
        if (residentIdle) return Delivery.Presence.IDLE;
        if (!windowsOpen || !activatedSinceOpen) return Delivery.Presence.NO_WINDOW;
        return jasperWindowActive ? Delivery.Presence.FOCUSED : Delivery.Presence.BACKGROUND;
    }

    /**
     * An uncaught failure, after the default handler logged it: an incident (spec section 7, producer 6), and the
     * low-memory warning when it is an {@code OutOfMemoryError} (spec section 5.4). Any thread.
     */
    public void unexpected(Thread thread, Throwable failure) {
        MemoryWarnings warnings = memoryWarnings;
        if (warnings != null && MemoryWarnings.isOutOfMemory(failure)) warnings.outOfMemory();
        incidentNotices.jasperFailure(thread == null ? "an unknown thread" : "thread " + thread.getName(), failure);
    }

    /** {@code notifications.groups}, live: each listed group's mode replaces its default from the next post on. */
    private void applyGroupModes(dev.jasper.app.config.ConfigService.State state) {
        notificationCenter.useModes(groupModes(state.values().get(dev.jasper.app.config.CoreSettings.NOTIFICATIONS_GROUPS)));
    }

    /** {@code window.memory_indicator}, live; the indicator shows only where the status bar does. */
    private void showMemoryIndicator(dev.jasper.app.config.ConfigService.State state) {
        memoryIndicator.show(state.values().get(dev.jasper.app.config.CoreSettings.WINDOW_MEMORY_INDICATOR)
            && state.values().get(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR));
    }

    /** The configured modes the center understands; the reader has already left out anything else. */
    static Map<String, dev.jasper.app.notifications.DisplayMode> groupModes(Map<String, String> configured) {
        var modes = new LinkedHashMap<String, dev.jasper.app.notifications.DisplayMode>();
        configured.forEach((id, token) -> dev.jasper.app.notifications.DisplayMode.parse(token).ifPresent(mode -> modes.put(id, mode)));
        return modes;
    }

    /** What Settings › Notifications lists: every group declared so far, with who declared it as people know them. */
    static List<dev.jasper.app.settingsui.NotificationGroupChoices.Group> groupChoices(
            List<dev.jasper.app.notifications.NotificationGroup> groups, java.util.function.Function<String, String> pluginName) {
        return groupChoices(groups,pluginName,Map.of());
    }
    static List<dev.jasper.app.settingsui.NotificationGroupChoices.Group> groupChoices(
            List<dev.jasper.app.notifications.NotificationGroup> groups, java.util.function.Function<String,String> pluginName,
            Map<String,PluginRuntime.PluginIcon> artwork) {
        return groups.stream().map(group -> new dev.jasper.app.settingsui.NotificationGroupChoices.Group(group.id(), group.name(),
            group.owner().equals(dev.jasper.app.notifications.NotificationRequest.JASPER) ? "Jasper" : pluginName.apply(group.owner()),
            group.defaultMode().token(), group.owner(), group.owner().equals(dev.jasper.app.notifications.NotificationRequest.JASPER) ? "appearance" : "plugin", Optional.ofNullable(artwork.get(group.owner())).map(PluginRuntime.PluginIcon::artwork), Optional.ofNullable(artwork.get(group.owner())).map(icon -> icon.identityArtwork().tileHue()).orElse(dev.jasper.ui.model.Nodes.Hue.NEUTRAL), Optional.ofNullable(artwork.get(group.owner())).map(icon -> icon.identityArtwork().selectedInk()).orElse(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED))).toList();
    }

    private String pluginName(String id) {
        PluginRuntime running = plugins;
        return running == null ? id : running.pluginNames().getOrDefault(id, id);
    }

    /** Disable Plugin on an incident: the Plugins page's operation, effective from the next launch. */
    private void disablePlugin(String id, Consumer<Optional<String>> done) {
        if (plugins == null || stopped) {
            done.accept(Optional.of("Plugins are not running."));
            return;
        }
        plugins.setEnabled(id, false, outcome -> done.accept(outcome.ok() ? Optional.empty()
            : Optional.of(outcome.message() == null ? "" : outcome.message())));
    }

    /** Show more… on an incident's notification: the Errors window, with that incident selected. */
    private void showErrors(UUID incident) {
        if (errorsWindow != null && !quitting && !stopped) errorsWindow.show(Optional.of(incident));
    }

    /** Jasper › About Jasper on macOS: the About window, once the auxiliary windows exist. */
    private void showAbout() {
        if (aboutWindows != null && !quitting && !stopped) aboutWindows.showAbout();
    }

    private void copyReport(String text) {
        try {
            clipboard.copy(text);
        } catch (RuntimeException failure) {
            notificationViews.error("Could not copy the report", failure.getMessage());
        }
    }

    /** About's Copy; false (after saying so) when the clipboard refused. */
    private boolean copyBuildInformation(String text) {
        try {
            clipboard.copy(text);
            return true;
        } catch (RuntimeException failure) {
            notificationViews.error("Could not copy the build information", failure.getMessage());
            return false;
        }
    }

    private void openLogFolder() {
        Path folder = logFolder;
        if (folder == null) return;
        CompletableFuture.runAsync(() -> new dev.jasper.app.platform.ConfigEditor().openFolder(folder))
            .whenComplete((ignored, failure) -> {
                if (failure != null) SwingUtilities.invokeLater(() -> notificationViews.error("Could not open the log folder", folder.toString()));
            });
    }

    /** Show Problems on the configuration notification: the details over the window used last. */
    private void showConfigurationProblems() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive : windows.stream().findFirst().orElse(null);
        if (target == null) return;
        target.toFront();
        target.content().showConfigurationDetails();
    }

    private boolean macOs() { return configuration == null ? System.getProperty("os.name").startsWith("Mac") : configuration.macOs(); }

    /** The keymap every window resolves right now: the saved shortcuts with the contributed actions' defaults. */
    KeyBindings.Resolved resolvedBindings() {
        ConfigSnapshot snapshot = configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot();
        List<KeyBindings.Extension> extensions = new ArrayList<>();
        for (ActionEntry action : contributions.actions())
            if (KeyBindings.extensionId(action.id())) extensions.add(action.extension());
        return snapshot.bindings(macOs()).withExtensions(extensions);
    }

    /** What resolving the saved shortcuts against the contributed actions could not honor, right now. */
    List<KeyBindings.Problem> bindingProblems() { return resolvedBindings().problems(); }

    /** A menu's text for {@code actionId}'s current binding in the platform's glyphs; "" when it is unbound or unknown. */
    String shortcutLabel(String actionId) {
        return KeyBindings.display(resolvedBindings().bindings().strokeFor(actionId).orElse(null), macOs());
    }

    /** A user's binding for an unknown action is theirs to fix; a plugin's losing default is only worth a log line. */
    private void reportBindingProblems() {
        for (KeyBindings.Problem problem : bindingProblems()) {
            if (problem.kind() == KeyBindings.Problem.Kind.UNKNOWN_ACTION && configuration != null)
                configuration.report("keybindings.\"" + problem.actionId() + "\"", problem.message());
            else LOG.log(System.Logger.Level.INFO, "Keybinding for " + problem.actionId() + ": " + problem.message());
        }
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start) {
        return windowLauncher(executor, snapshots, start, null);
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir) {
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        return windowLauncher(executor, snapshots, start, integrationDir, (requested, shell) -> ShellProfiles.resolve(requested,
                ShellProfiles.available(os, environment, List.of(), Optional.empty(), shell), true, os, environment, shell),
            () -> false, message -> LOG.log(System.Logger.Level.WARNING, message));
    }

    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems) {
        return windowLauncher(executor, snapshots, start, integrationDir, profiles, awaitDetection, problems, pane -> Map.of());
    }

    /**
     * Each request's profile is resolved on the EDT by {@code profiles}, from the requested id (empty: the configured
     * default) and the saved shell settings; {@code problems} hears, on the EDT, why a profile was replaced.
     *
     * <p>Until detection completes, {@code profiles} trusts a WSL or PowerShell 7 id it cannot check yet. Such a
     * launch waits on the launch executor, off the EDT, for {@code awaitDetection} (bounded; true once detection has
     * completed) and then resolves again on the EDT, so a profile that turned out to be missing starts the platform
     * default with a warning instead of failing the pane. When {@code awaitDetection} gives false (it timed out, or
     * nothing detects) the trusted profile starts as resolved. {@link ShellLauncher#sessionDefaults} reports nothing:
     * a provided session does not start the local profile.
     */
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems,
                                        Function<UUID, Map<String, String>> paneVariables) {
        return windowLauncher(executor, snapshots, start, integrationDir, profiles, awaitDetection, problems, paneVariables,
            inherited -> Map.of());
    }

    /**
     * As above; a local shell also gets what plugins add ({@code contributions}, given Jasper's own environment, on the
     * EDT) after Jasper's own variables and before {@code [terminal.env]}. A PowerShell 7 launch confirmed after
     * detection resolves again, so its contributions are asked twice.
     */
    static ShellLauncher windowLauncher(Executor executor, Supplier<ConfigSnapshot> snapshots,
                                        BiFunction<Path, LaunchSettings, TerminalSession> start, Path integrationDir,
                                        BiFunction<Optional<String>, TerminalConfig.Shell, ShellProfiles.Resolution> profiles,
                                        BooleanSupplier awaitDetection, Consumer<String> problems,
                                        Function<UUID, Map<String, String>> paneVariables,
                                        Function<Map<String, String>, Map<String, String>> contributions) {
        ConfigSnapshot initial = snapshots.get();
        String os = System.getProperty("os.name");
        Map<String, String> environment = System.getenv();
        Function<Optional<String>, LaunchSettings> settings = requested -> {
            ConfigSnapshot snapshot = snapshots.get();
            ShellProfiles.Resolution resolution = profiles.apply(requested, snapshot.terminal().shell());
            resolution.warning().ifPresent(problems);
            return LaunchSettings.resolve(snapshot, resolution.profile(), os, environment, initial.columns(), initial.lines(), integrationDir,
                contributions);
        };
        BiFunction<Optional<String>, LaunchSettings, LaunchSettings> confirm = (requested, captured) -> {
            ShellProfile profile = captured.profile();
            boolean checkable = profile.kind() == ShellProfile.Kind.WSL || profile.id().equals(ShellProfiles.PWSH);
            if (!checkable || SwingUtilities.isEventDispatchThread() || !awaitDetection.getAsBoolean()) return captured;
            var confirmed = new java.util.concurrent.atomic.AtomicReference<LaunchSettings>();
            try {
                SwingUtilities.invokeAndWait(() -> confirmed.set(settings.apply(requested)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return captured;
            } catch (java.lang.reflect.InvocationTargetException failure) {
                throw new IllegalStateException("The shell profile could not be resolved", failure.getCause());
            }
            return confirmed.get().profile().equals(profile) ? captured : confirmed.get();
        };
        return new ShellLauncher(executor, settings, confirm, () -> {
            ConfigSnapshot snapshot = snapshots.get();
            return new ShellLauncher.SessionDefaults(initial.columns(), initial.lines(), snapshot.terminal().scrollback());
        }, start, paneVariables);
    }

    /**
     * After each detection that changed the profiles (the first always does), tells {@code problems} when the
     * configured profile is missing, so a first window that started before detection completed still shows why its
     * shell is not the configured one. EDT.
     */
    static Subscription warnWhenDetected(ShellProfileCatalog catalog, Supplier<TerminalConfig.Shell> shell, Consumer<String> problems) {
        return catalog.onChanged(() -> catalog.resolve(Optional.empty(), shell.get()).warning().ifPresent(problems));
    }

    /** A shell profile that could not start: shown with the configuration problems until the next reload. */
    private void shellProfileProblem(String message) {
        if (configuration != null) configuration.report(CoreSettings.TERMINAL_SHELL_PROFILE.name(), message);
        else LOG.log(System.Logger.Level.WARNING, message);
    }

    /** The detected shell profiles, for Settings, new panes and plugins. EDT. */
    ShellProfileCatalog shellProfiles() { return shellProfiles; }

    private static TerminalSession startSession(Path directory, LaunchSettings settings) {
        try {
            return TerminalSession.start(sessionOptions(directory, settings));
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.ERROR, "Shell launch failed", failure);
            throw new UncheckedIOException(failure);
        }
    }

    /** The session a shell starts as; on Windows it ignores a reported directory on another host ({@link LaunchSettings#localPaths}). */
    static SessionLaunchOptions sessionOptions(Path directory, LaunchSettings settings) {
        return SessionLaunchOptions.builder().command(settings.command()).environment(settings.environment()).workingDirectory(directory).grid(new GridSize(settings.columns(), settings.lines())).scrollback(settings.scrollback())
            .localHostNames(dev.jasper.app.launch.LocalHostNames.cached()).localPathMapper(settings.localPaths(WINDOWS)).build();
    }

    /** Marks the first spawn and watches the session's first output from right after its start. Any thread. */
    private TerminalSession watchOutput(TerminalSession session) {
        trace.mark(StartupTrace.Milestone.SHELL_SPAWNED);
        trackOutput(session, SessionOutput.watch(session, () -> trace.mark(StartupTrace.Milestone.SHELL_OUTPUT)));
        return session;
    }

    /** Keeps a session's output watch until its first terminal claims it or the session exits. Any thread. */
    void trackOutput(TerminalSession session, SessionOutput output) {
        outputs.put(session, output);
        session.exitFuture().whenComplete((code, failure) -> outputs.remove(session));
    }

    /** Whether a session's output watch is still held. Any thread. */
    boolean watchingOutput(TerminalSession session) { return outputs.containsKey(session); }

    /** First paint, then the first paint after output: the window is usable. EDT. */
    private void timeFirstTerminal(TerminalPane pane, WindowTrace windowTrace) {
        TerminalView view = pane.view();
        view.afterNextPaint(() -> {
            windowTrace.mark(WindowTrace.Milestone.FIRST_PAINT);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
        });
        view.repaint();
        SessionOutput output = outputs.remove(pane.session());
        if (output == null) return; // A provider's connection or a supplied test launcher: no output timing.
        output.whenSeen(() -> SwingUtilities.invokeLater(() -> {
            if (pane.view() != view) return;
            view.afterNextPaint(() -> {
                windowTrace.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
                trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
            });
            view.repaint();
        }));
    }

    /** Remembers a shell so shutdown can wait for it to leave before terminating the JVM. */
    TerminalSession track(TerminalSession session) { return launches.track(session); }

    /** A terminal window's native window, for parenting application-built dialogs; null when it is gone. */
    private java.awt.Window nativeWindow(java.util.UUID id) {
        for (TerminalWindow window : windows) if (window.content().id().equals(id)) return window.nativeWindow();
        return null;
    }

    /**
     * Forgets a window the application no longer holds: out of the window list, the notification cards and the Buddy's
     * windows, no longer the one used last, and with no window left nothing counts as activated. The window list
     * changes first, so a failure after it still leaves the window forgotten. EDT.
     */
    private void forget(TerminalWindow window) {
        windows.remove(window);
        memoryIndicator.windows(windows.size());
        if (window != null) notificationViews.detach(window.content()); // tests close "the last window" with null
        buddy.removeWindow(window);
        if (lastActive == window) lastActive = null;
        if (windows.isEmpty()) activatedSinceOpen = false;
    }

    void windowClosed(TerminalWindow window) {
        forget(window);
        // Residency keeps the warm process: the command history, the plugins and the configuration
        // watcher are precisely what makes the next window fast, and shutdown would close all of them.
        // Quit still terminates.
        boolean pluginWindowsOpen = auxiliary != null && !auxiliary.open().isEmpty();
        if (windows.isEmpty() && !resident && !pluginWindowsOpen) requestShutdown();
        else {
            // The next terminal window's timing says how long this process sat without one.
            if (windows.isEmpty()) trace.windowsClosed();
            updateBuddyActions();
            updateDock();
        }
    }

    void windowStateChanged(TerminalWindow window, boolean showing, boolean iconified) {
        if (!windows.contains(window)) return;
        buddy.window(window, showing, iconified);
        updateBuddyActions();
    }

    /** Coming back to a Jasper window is attention: the buddy wakes and waves. */
    void windowActivated(TerminalWindow window) {
        if (!windows.contains(window)) return;
        lastActive = window;
        activatedSinceOpen = true;
        notificationViews.activated(window.content());
        systemNotices.focused();
        terminals.windowActivated(window.content().id());
        if (buddy != null && !quitting && !stopped) buddy.greet();
    }

    private boolean owns(Component component) {
        for (TerminalWindow window : windows) if (window.owns(component)) return true;
        return false;
    }

    void toggleBuddy() { buddy.toggle(); updateBuddyActions(); }

    boolean buddyEnabled() { return buddy.enabled(); }

    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }
    /**
     * Brings a terminal window to the front for a click on an OS notification: the notification's own window while it
     * is open, else the one used last, and with none open a new one. Names the window; empty while Jasper quits.
     */
    private Optional<UUID> raiseFor(Optional<UUID> target) {
        if (quitting || stopped) return Optional.empty();
        TerminalWindow window = target.flatMap(this::openWindowWith).orElse(null);
        if (window == null && lastActive != null && windows.contains(lastActive)) window = lastActive;
        if (window == null && !windows.isEmpty()) window = windows.iterator().next();
        if (window == null) window = newWindow();
        if (window == null) return Optional.empty();
        window.toFront();
        return Optional.of(window.content().id());
    }

    private Optional<TerminalWindow> openWindowWith(UUID id) {
        for (TerminalWindow window : windows) if (window.content().id().equals(id)) return Optional.of(window);
        return Optional.empty();
    }

    /**
     * {@code notifications.system}, live: whether the operating system shows notifications while Jasper is away; never
     * once Jasper quits.
     */
    private boolean systemNotificationsEnabled() {
        return systemNotificationsEnabled(
            configuration == null || configuration.state().values().get(dev.jasper.app.config.CoreSettings.NOTIFICATIONS_SYSTEM),
            quitting, stopped);
    }

    /**
     * The rule behind {@link #systemNotificationsEnabled()}. Quitting closes every window, so Jasper then looks away,
     * but a banner raised while windows close or a plugin stops would outlive the process, and on macOS a click on it
     * would relaunch Jasper with nothing to route to.
     */
    static boolean systemNotificationsEnabled(boolean configured, boolean quitting, boolean stopped) {
        return configured && !quitting && !stopped;
    }

    private void updateBuddyActions() {
        for (TerminalWindow window : List.copyOf(windows)) window.content().updateActions();
    }

    /**
     * Registers process cleanup before quit. Each action runs on a daemon worker and participates
     * in the bounded exit wait; callbacks must not access Swing. Registration is EDT-owned.
     */
    public void onShutdown(Runnable action) {
        if (stopped) processCleanup(java.util.Objects.requireNonNull(action));
        else shutdownActions.add(java.util.Objects.requireNonNull(action));
    }

    /**
     * Quits through the normal quit path and starts a replacement process once process cleanup, which
     * releases the handoff endpoint, has finished. EDT.
     *
     * @param mode which launch flags the replacement keeps
     * @return false, and nothing happens, when this process's command line cannot be determined; otherwise
     *     true. It also returns true, with nothing changed, when a plugin window's closing guard refuses the quit.
     */
    public boolean restart(RestartMode mode) {
        if (quitting || stopped) return true;
        Optional<List<String>> planned = restartPlanner.apply(mode);
        if (planned.isEmpty()) return false;
        // A refusal leaves no relaunch behind: a later ordinary quit must not start a replacement.
        if (!pluginWindowsMayClose()) return true;
        relaunch = planned.get();
        endpointOwner = resident;
        quitNow();
        return true;
    }

    /** Exit thread. A replacement that could hand off must not start while this process may still answer the endpoint. */
    private void relaunchIfRequested() {
        List<String> command = relaunch;
        if (command == null) return;
        List<CompletableFuture<?>> cleanups = processCleanups;
        boolean released = cleanups != null && cleanups.stream().allMatch(done -> done.isDone() && !done.isCompletedExceptionally());
        if (endpointOwner && !released) {
            LOG.log(System.Logger.Level.WARNING, "Process cleanup did not finish; the replacement starts standalone so it cannot hand off to this process");
            command = RestartCommand.standalone(command);
        }
        try { spawner.accept(command); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.ERROR, "Could not start the replacement process", failure); }
    }

    /**
     * Tells the Plugins manager about the resident process. EDT, before the manager is first opened.
     *
     * @param control probes and retires the process that holds the handoff endpoint
     * @param replacementHandsOff whether leaving safe mode produces a plain launch, which a resident would swallow
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     */
    public void residentControl(ResidentControl control, boolean replacementHandsOff, boolean standaloneNotice) {
        this.residentControl = java.util.Objects.requireNonNull(control);
        this.replacementHandsOff = replacementHandsOff;
        this.standaloneNotice = standaloneNotice;
    }

    dev.jasper.app.contributions.Contributions contributions() { return contributions; }

    NotificationCenter notificationCenter() { return notificationCenter; }

    Incidents incidents() { return incidents; }

    /** Asks {@code question} with a kit sheet over {@code surface}, the Settings dialog; its yes runs at most once. */
    /** A notice sheet over the Settings dialog's {@code surface}; nothing while that dialog is closed. */
    private void informOver(dev.jasper.app.windows.AuxiliarySurface surface, dev.jasper.app.settingsui.ThemeImports.Notice notice) {
        if (surface == null || auxiliary == null) return;
        var dialog = auxiliary.dialog(notice.title(), true, surface);
        var view = dev.jasper.app.pluginmanager.ConfirmView.notice(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
            notice.title(), notice.message(), dialog::close);
        dialog.onClosed(view::close);
        dialog.setContent(view.view());
        dialog.show();
    }

    private void confirmOver(dev.jasper.app.windows.AuxiliarySurface surface, dev.jasper.app.settingsui.ThemeImports.Question question) {
        if (surface == null || auxiliary == null) return;
        var dialog = auxiliary.dialog(question.title(), true, surface);
        boolean[] answered = {false};
        var view = new dev.jasper.app.pluginmanager.ConfirmView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
            question.question(), question.consequence(), question.yes(), () -> {
                if (answered[0]) return;
                answered[0] = true;
                dialog.close();
                question.onYes().run();
            }, dialog::close);
        dialog.onClosed(view::close);
        dialog.setContent(view.view());
        dialog.show();
    }

    /** The Plugins page's controller, created the first time Settings builds that page. */
    private PluginManager pluginManager() {
        if (pluginManager == null) {
            java.util.concurrent.Executor worker = work -> Thread.ofPlatform().daemon().name("jasper-restart").start(work);
            // Only a plain replacement can be swallowed by a resident; any other is standalone and needs no conversation.
            var flow = new RestartFlow(replacementHandsOff ? residentControl : ResidentControl.NONE, this::restart, worker,
                SwingUtilities::invokeLater, Duration.ofSeconds(30), Duration.ofMillis(250));
            var editor = new dev.jasper.app.platform.ConfigEditor();
            pluginManager = new PluginManager(plugins, auxiliary, new PluginManager.Hooks(
                surface -> shells.chooseFile(surface, "Install Plugin", ".zip"), flow, this::quit, residentControl, standaloneNotice, worker,
                this::openForEditing, editor::reveal, this::browse));
        }
        return pluginManager;
    }

    /** Asks plugin windows first; any refusal leaves Jasper running with nothing changed. */
    public void quit() {
        if (quitting || stopped) return;
        if (!pluginWindowsMayClose()) return;
        quitNow();
    }

    /** Plugin windows', content tabs' and quit guards, asked before quit or restart changes anything and before the exit deadline is armed. */
    private boolean pluginWindowsMayClose() {
        return windowsMayClose() && contributions.quitGuards().mayQuit();
    }

    /** Content tabs' and plugin windows' closing guards: what closing every window asks. Quit guards are not asked. EDT. */
    private boolean windowsMayClose() {
        var requested = contributions.content().entries();
        return contributions.content().mayClose(requested) && (auxiliary == null || auxiliary.mayQuit())
            && requested.equals(contributions.content().entries());
    }

    /**
     * macOS's quit request (Cmd+Q, the application menu, the Dock's Quit, a log-out or a shutdown). Any thread.
     *
     * <p>An ordinary request is cancelled here and goes through {@link #quitRequested} unless a window that is recording
     * shortcuts takes the Cmd+Q. A system quit (see {@link #quitIsSystemQuit}) quits for real, with the request left
     * unanswered; the one exception is a user recording Cmd+Q in the Shortcuts panel while macOS is not powering off:
     * the recorder takes it and the request is cancelled.
     */
    void nativeQuitRequested(java.awt.desktop.QuitResponse response) {
        nativeQuitRequested(response, JasperApplication::commandQCaptured);
    }

    /** As {@link #nativeQuitRequested(java.awt.desktop.QuitResponse)}; tests replace how a recording window is found. */
    void nativeQuitRequested(java.awt.desktop.QuitResponse response, BooleanSupplier recorderTakesCommandQ) {
        DockPresence presence = dock;
        if (quitIsSystemQuit()) {
            SwingUtilities.invokeLater(() -> {
                // A log-out is never cancelled; a flag not yet watched (before the bridge is prepared) reads as no log-out.
                if (!presence.poweringOff() && recorderTakesCommandQ.getAsBoolean()) response.cancelQuit();
                else quitForSystem(response);
            });
            return;
        }
        response.cancelQuit();
        SwingUtilities.invokeLater(() -> { if (!recorderTakesCommandQ.getAsBoolean()) quitRequested(); });
    }

    /** Whether the focused window is recording shortcuts and takes Cmd+Q for itself. EDT. */
    private static boolean commandQCaptured() {
        var focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        var stroke = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.META_DOWN_MASK);
        return dev.jasper.app.platform.WindowInput.captureShortcut(focused, stroke);
    }

    /**
     * Whether a native quit request is the system's (a log-out, restart or shutdown) or must be treated as one: a
     * resident process with Dock presence, until its bridge is prepared and from the moment macOS is powering off.
     * Before the bridge is prepared a log-out cannot be told from a user's Quit, so both quit for real and the request
     * is never interrupted. A process that is not resident, or has no Dock presence (development runs, Windows, Linux),
     * keeps today's handler: cancel, then quit. Any thread.
     */
    boolean quitIsSystemQuit() {
        DockPresence presence = dock;
        // The power-off flag is sticky: macOS has no "log-out cancelled" notification, so after a cancelled log-out a
        // later Cmd+Q quits for real, which is the safe direction.
        return presence != null && resident && (!presence.prepared() || presence.poweringOff());
    }

    /**
     * Cmd+Q, Jasper \u203a Quit, the Dock's Quit and Jasper's own Quit action: on a resident macOS Jasper with Dock presence
     * whose bridge is prepared, every window closes and the process stays in the background; anywhere else, and before
     * start-up has settled, it quits. EDT.
     */
    void quitRequested() {
        DockPresence presence = dock;
        if (presence != null && resident && presence.prepared()) toBackground();
        else quit();
    }

    /**
     * macOS asked every application to quit (a log-out, restart or shutdown): a real quit. The request stays unanswered
     * so the exiting process ends it and the log-out goes on; a plugin refusing the quit cancels it. EDT.
     */
    void quitForSystem(java.awt.desktop.QuitResponse response) {
        quit();
        if (!quitting && !stopped) response.cancelQuit();
    }

    /**
     * Closes every window through the guards closing them asks (content tabs, plugin windows) and keeps the process, its
     * plugins and configuration; with Dock presence Jasper leaves the Dock. A refusal leaves everything open. EDT.
     */
    public void toBackground() {
        if (quitting || stopped) return;
        if (!windowsMayClose()) return;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        if (auxiliary != null) auxiliary.close();
        updateDock();
    }

    /** Whether any Jasper window, terminal or plugin, is open. EDT. */
    private boolean windowsOpen() {
        return !windows.isEmpty() || (auxiliary != null && !auxiliary.open().isEmpty());
    }

    /**
     * Whether this process switches its Dock presence now: a resident macOS Jasper that is not quitting. Quitting for
     * real never touches the Dock, so a log-out or shutdown never waits on AppKit. EDT.
     */
    private boolean dockFollowsWindows() {
        return dock != null && resident && !quitting && !stopped;
    }

    /**
     * Foreground with a window open, Background with none; only a resident process with Dock presence switches. Not to
     * be confused with {@link #presence()}, which is where Jasper stands for notifications. EDT.
     */
    private void updateDock() {
        if (dockFollowsWindows()) dock.windows(windowsOpen());
    }

    /** A window is about to open: Foreground first. EDT. */
    private void windowOpening() {
        if (dockFollowsWindows()) dock.windows(true);
    }

    /** Quits without asking plugin windows: start-up rollback and programmatic exits, which no user asked for. EDT. */
    public void quitNow() {
        presentationAttempt().orderlyQuit();
        quitting = true;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        requestShutdown();
    }

    private void requestShutdown() {
        quitting = true;
        if (shutdownQueued || stopped) return;
        shutdownQueued = true;
        // Accepted palette actions record only after dispatch, including Quit/last-tab close.
        SwingUtilities.invokeLater(this::shutdown);
    }

    private void shutdown() {
        if (stopped) return;
        stopped = true;
        quitting = true;
        stopSettleFallback();
        showIndicator(false);
        // Armed before anything else: a plugin's stop() that blocks the EDT cannot be abandoned, and
        // await() below is never reached in that case.
        shutdown.arm(() -> plugins == null ? null : plugins.executing());
        launches.close();
        shellProfiles.close();
        // Application-owned state first, so none of it depends on plugins behaving.
        history.close();
        release("the heap watch", () -> {
            MemoryWarnings warnings = memoryWarnings;
            if (warnings != null) warnings.close();
        });
        release("the memory indicator", memoryIndicator::close);
        List<CompletableFuture<?>> pluginWork = plugins == null ? List.of() : plugins.stop();
        // Plugins cleared their own secrets as they stopped; this clears anything still pending.
        clipboard.quit();
        // Plugins closed their own windows while stopping; this is the safety net, and the state's last write.
        if (auxiliary != null) auxiliary.close();
        uiState.save();
        if (pluginTheme != null) pluginTheme.close();
        notifications.close();
        if (activityNotifier != null) activityNotifier.close();
        buddyAppearance.close();
        systemAppearanceListening.run();
        systemAppearance.close();
        themes.close();
        buddy.close();
        incidentNotices.close();
        notificationViews.close();
        systemNotices.close();
        if (configuration != null) configuration.close();
        if (quitHandlerInstalled) { Desktop.getDesktop().setQuitHandler(null); quitHandlerInstalled = false; }
        if (preferencesHandlerInstalled) { Desktop.getDesktop().setPreferencesHandler(null); preferencesHandlerInstalled = false; }
        if (aboutHandlerInstalled) { Desktop.getDesktop().setAboutHandler(null); aboutHandlerInstalled = false; }
        if (reopenListener != null) { Desktop.getDesktop().removeAppEventListener(reopenListener); reopenListener = null; }
        // Cross-process locks and socket probes must not hold up Swing or the exit deadline.
        List<CompletableFuture<?>> pending = new ArrayList<>(launches.pendingExits());
        pending.addAll(pluginWork);
        List<CompletableFuture<?>> cleanups = new ArrayList<>();
        for (Runnable action : java.util.List.copyOf(shutdownActions)) cleanups.add(processCleanup(action));
        shutdownActions.clear();
        pending.addAll(cleanups);
        processCleanups = List.copyOf(cleanups);
        // Nothing else ends the JVM: without an explicit exit, AWT waits a full quiet second before it lets go.
        pending.add(history.closedFuture());
        shutdown.await(pending);
    }

    /** Closes one child whose failure must not hold up the rest of the shutdown. */
    private static void release(String what, Runnable close) {
        try {
            close.run();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "Could not close " + what, failure);
        }
    }

    private static CompletableFuture<Void> processCleanup(Runnable action) {
        return CompletableFuture.runAsync(action, work ->
            Thread.ofPlatform().name("jasper-process-cleanup").daemon().start(work));
    }

    private static boolean supports(Desktop.Action action) {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(action);
    }
}
