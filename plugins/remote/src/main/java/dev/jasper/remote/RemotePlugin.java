package dev.jasper.remote;

import dev.jasper.remote.agent.AgentClient;
import dev.jasper.remote.client.Connections;
import dev.jasper.remote.client.HostKeyVerifier;
import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.ConfigImport;
import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.hosts.SshConfig;
import dev.jasper.remote.trust.KnownHosts;
import dev.jasper.remote.ui.ConfirmPanel;
import dev.jasper.remote.ui.ConnectionPanel;
import dev.jasper.remote.ui.GroupNamePanel;
import dev.jasper.remote.hosts.HostInfoCache;
import dev.jasper.remote.ui.HostEditor;
import dev.jasper.remote.ui.HostKeyPanel;
import dev.jasper.remote.ui.HostsPanel;
import dev.jasper.remote.ui.ImportPanel;
import dev.jasper.remote.ui.RemoteScope;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.Direction;
import dev.jasper.sdk.terminal.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.PendingSession;
import dev.jasper.sdk.terminal.SessionKind;
import dev.jasper.sdk.terminal.SessionSpec;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.TerminalEvents;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.OverlaySpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.Kind;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the plugin: the hosts store and its poll, trust, the agent, the connection registry, the session
 * provider, the Sessions panel (saved hosts and WSL distributions), the scope, the actions, its Session and Transfer menu entries, the status item and the dialogs.
 */
public class RemotePlugin implements Plugin {
    public static final String CONNECT = "dev.jasper.remote.connect", HOSTS = "dev.jasper.remote.hosts", SPLIT = "dev.jasper.remote.split";
    public static final String NEW_HOST = "dev.jasper.remote.hosts.new";
    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", PANEL = "dev.jasper.remote.panel";
    /** The restorer kind of an SSH tab (workspace tabs spec 13). */
    public static final String SSH_TAB = "ssh";

    private final Executor ui;
    private final Function<PluginContext,Executor> transferExecutor;
    private dev.jasper.remote.transfer.TransferCoordinator transfers;
    private dev.jasper.remote.ui.transfers.TransferUi transferUi;
    /** The classic Transfers bottom panels (spec 14.4); null under ROWS. */
    private dev.jasper.remote.ui.transfers.TransfersPanels transfersPanels;
    private dev.jasper.remote.ui.sftp.SftpUi sftpUi;
    private dev.jasper.remote.tunnels.TunnelStore tunnelStore;
    private dev.jasper.remote.ui.tunnels.TunnelsUi tunnelsUi;
    private dev.jasper.remote.ui.sftp.DirectoryFollower follower;
    private dev.jasper.remote.edit.EditChannels editChannels;
    /** Opens SFTP endpoints for transfers, the file chooser and remote documents. */
    private dev.jasper.remote.sftp.EndpointFactory endpoints;
    private dev.jasper.sdk.Subscription reopener;
    private dev.jasper.remote.chooser.RemoteHosts remoteHosts;
    private dev.jasper.sdk.Subscription locations;
    private java.util.function.BiConsumer<dev.jasper.remote.api.RemoteFolder, WindowHandle> remoteOpener;
    private final Map<UUID, PendingSession> attachedPanes = new java.util.HashMap<>();
    public static final String SFTP="dev.jasper.remote.sftp", SFTP_TOGGLE=dev.jasper.remote.ui.sftp.SftpUi.TOGGLE;
    private final Function<PluginContext, Optional<AgentClient>> agentFactory;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Path sshDir;
    private PluginContext context;
    /** Which composition this run takes (Enterprise presentation spec 14.3), read once in {@link #start}. */
    private dev.jasper.remote.workstation.Layout layout = dev.jasper.remote.workstation.Layout.ROWS;
    /** The composition this run takes. */
    dev.jasper.remote.workstation.Layout layout() { return layout; }
    private RemoteSettings.Declared settings;
    private HostStore store;
    private PanelState panelState;
    private KnownHosts trust;
    private Connections connections;
    private Optional<VaultApi> vault = Optional.empty();
    private RemoteScope scope;
    private StatusItem status;
    private PluginAction splitAction;
    private SessionsToolbar sessionsToolbar;
    /** Each window's chosen session, shared by its toolbar view and, in the main window, its Sessions tree under CLASSIC (spec 14.4). */
    private final SessionSelection sessionSelection = new SessionSelection();
    private Timer poll;
    private String icon;
    private String serverIcon;
    private boolean stopped;
    /** Completes once the saved hosts have been read at start; a restored SSH tab decides how to connect after it. */
    private CompletableFuture<Void> hostsLoaded = CompletableFuture.completedFuture(null);
    /** Restored SSH tabs waiting, per window, for the connection in progress there (workspace tabs spec 13). */
    private final Map<UUID, java.util.ArrayDeque<Runnable>> waitingConnects = new HashMap<>();
    /** Windows where a restored document's quiet read opens its channel, how many: their host-key questions answer Cancel. */
    private final Map<UUID, Integer> quietWindows = new HashMap<>();
    /** Restored SSH tabs' panes and their hosts, oldest first, until their session attaches (workspace plan 6b Rulings 41). */
    private final Map<UUID, UUID> restoredPanes = new java.util.LinkedHashMap<>();
    private final Set<CompletableFuture<HostKeyVerifier.Decision>> questions = new java.util.HashSet<>();
    /** The Sessions panel while it is open: panels live in the main window (workspace tabs spec 14). */
    private HostsPanel sessionsPanel;
    private PanelHost sessionsHost;
    private final Map<UUID, UUID> panes = new HashMap<>();
    /** Tab names' {@code {host}}: set for each SSH tab when its session attaches; registered in {@link #start}. */
    private dev.jasper.sdk.terminal.TabNamePiece hostPiece;
    private final Map<UUID, dev.jasper.remote.client.ConnectionIdentity> paneIdentities = new HashMap<>();
    private HostKeyPanel currentHostKeyPanel;
    private HostEditor currentEditor;
    private ConfigImportController configImport;
    private ConfirmPanel currentConfirm;
    private ConnectionPanel currentConnectionPanel;
    private GroupNamePanel currentGroupEditor;
    private HostInfoCache hostInfo;
    private final Set<ConnectAttempt> attempts = new java.util.HashSet<>();
    private final java.util.LinkedHashMap<UUID, UUID> recentPanes = new java.util.LinkedHashMap<>();
    /** Panes Remote opened for a WSL distribution: pane id to profile id. Local panes, so not in {@link #panes}. */
    private final Map<UUID, String> distroPanes = new HashMap<>();
    /** The WSL profiles the application lists now, in its order. */
    private List<ShellProfileInfo> distros = List.of();

    public RemotePlugin() {
        this(SwingUtilities::invokeLater, context -> AgentClient.forEnvironment(System.getenv(), System.getProperty("os.name", "")),
            (delay, task) -> { var timer = new Timer((int) Math.max(1, delay.toMillis()), event -> task.run()); timer.setRepeats(false); timer.start(); return timer::stop; },
            Path.of(System.getProperty("user.home"), ".ssh"));
    }

    RemotePlugin(Executor ui, Function<PluginContext, Optional<AgentClient>> agentFactory, BiFunction<Duration, Runnable, Runnable> schedule, Path sshDir) {
        this(ui,agentFactory,schedule,sshDir,PluginContext::background);
    }
    RemotePlugin(Executor ui, Function<PluginContext, Optional<AgentClient>> agentFactory, BiFunction<Duration,Runnable,Runnable> schedule,Path sshDir,Function<PluginContext,Executor> transferExecutor) {
        this.ui = ui; this.agentFactory = agentFactory; this.schedule = schedule; this.sshDir = sshDir;this.transferExecutor=transferExecutor;
    }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = new RemoteSettings.Declared(context.settings());
        store = new HostStore(context.dataDirectory().resolve("hosts.toml"), context.background(), ui);
        panelState = new PanelState(context.dataDirectory().resolve("panel-state.toml"));
        hostInfo = new HostInfoCache(context.dataDirectory().resolve("host-info.properties"));
        context.background().execute(() -> { hostInfo.load(); ui.execute(() -> { if (!stopped) refreshPanels(); }); });
        trust = new KnownHosts(context.dataDirectory().resolve("known_hosts"), () -> settings.connection().readUserKnownHosts() ? Optional.of(sshDir.resolve("known_hosts")) : Optional.empty());
        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
        layout = dev.jasper.remote.workstation.Layout.of(context.appearance().presentation());
        Optional<java.util.function.BiFunction<WindowHandle, UUID, CompletableFuture<Optional<dev.jasper.vault.api.Credential>>>> credentialSource = Optional.empty();
        if (vault.isPresent()) credentialSource = Optional.of((owner, id) -> vault.get().credential(owner, id));
        connections = new Connections(settings::connection, trust, agentFactory.apply(context), store::host,
            credentialSource, this::askHostKey, context.background(), ui, schedule);
        connections.onChanged(this::refreshStatus);
        icon = "network";
        serverIcon = "server";
        // Before the panels: each names its toggle action, which must already be registered.
        registerNavigation();

        Executor transferBackground=transferExecutor.apply(context);
        endpoints=new dev.jasper.remote.sftp.EndpointFactory(connections,context.background(),ui,()->settings.sftp().requestTimeout());
        transfers=new dev.jasper.remote.transfer.TransferCoordinator(context.dataDirectory().resolve("transfers"),transferBackground,ui,(ref,owner)-> {
            try { return endpoints.open(ref.identity(),owner,status->{}); }catch(IOException failure) { return CompletableFuture.failedFuture(failure); }
        },()->settings.sftp().maxParallelFiles());
        transfers.setResumeValidator((ref,owner)-> {
            if(ref.hostId().isEmpty()) return CompletableFuture.completedFuture(null);
            try {
                var expected=ref.identity().orElseThrow();
                return validateResumeIdentity(expected,connections.resolveIdentity(ref.hostId().orElseThrow(),owner));
            }catch(Exception failure) { return CompletableFuture.failedFuture(failure); }
        });
        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->{
            if(transfersPanels!=null) transfersPanels.show(window); else sftpUi.reveal(window);
        });
        if(layout==dev.jasper.remote.workstation.Layout.CLASSIC) transfersPanels=new dev.jasper.remote.ui.transfers.TransfersPanels(context,transferUi);
        sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(context,ui,transferBackground,connections,endpoints,()->transfers,store::hosts,this::sftpPane,transferUi::strip,transferUi::release,layout);
        sftpUi.hiddenItems(panelState.sftpShowHidden(),shown-> { try { panelState.saveSftpShowHidden(shown); }
            catch(IOException failure) { context.log().log(System.Logger.Level.WARNING,"panel state: {0}",failure.getMessage()); } });
        tunnelStore = new dev.jasper.remote.tunnels.TunnelStore(context.dataDirectory().resolve("tunnels.toml"), context.background(), ui);
        tunnelsUi = new dev.jasper.remote.ui.tunnels.TunnelsUi(context, ui, tunnelStore,
            new dev.jasper.remote.tunnels.TunnelManager(connections, () -> settings.connection().connectTimeout(), context.background(), ui),
            store::hosts, store::host, paneId -> Optional.ofNullable(panes.get(paneId)).flatMap(store::host), layout);
        remoteHosts=new dev.jasper.remote.chooser.RemoteHosts(store::hosts,connections::connected,store::onChanged,connections::onChanged,
            paneId->context.terminals().pane(paneId).flatMap(this::sftpPane).map(target->new dev.jasper.remote.chooser.RemoteHosts.Located(target.identity(),target.directory())),
            connections::resolveIdentity,(identity,window)->endpoints.open(Optional.of(identity),window,status->{}),()->context.terminals().activeWindow());
        context.fileSources().register(remoteHosts);
        remoteOpener = (file, window) -> context.notifications().group().error("Could not open a remote file",
            "Install the Editor plugin to open " + file.path() + ".");
        try {
            var editor=context.services().find(dev.jasper.editor.api.Editor.class);
            if(editor.isPresent()) {
                // A restored document's quiet read opens its channel asking nothing (workspace plan 6b Rulings 29).
                editChannels=new dev.jasper.remote.edit.EditChannels((identity,owner)->endpoints.open(Optional.of(identity),owner.orElse(null),status->{}),
                    this::openQuietly,connections::authenticated,transferBackground,ui);
                // Jasper restores its windows while the saved hosts still load: a remote document finds them when first read
                // (workspace tabs spec 13), and its first read after a restore asks nothing unless signing in would not.
                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst(),()->hostsLoaded,ui,this::signsInAlone);
                sftpUi.editing(editing);
                remoteOpener = (file, window) -> remoteIdentity(file.hostId()).ifPresentOrElse(
                    identity -> editing.open(identity, file.path(), window),
                    () -> context.notifications().group().error("Could not open a remote file", file.hostName() + " is no longer saved."));
                try { reopener=editing.registerReopener(); } catch(NoSuchMethodError older) { /* an Editor before 0.4 keeps no recent files */ }
                try { locations=editing.registerLocations(dev.jasper.remote.chooser.RemoteHosts.ID,root->remoteHosts.identity(root).or(()->configuredIdentity(root))); }
                catch(NoSuchMethodError older) { /* an Editor before 0.6 opens only local files from its chooser */ }
            }
        } catch(NoClassDefFoundError absent) { /* the Editor is not installed: files in the SFTP panel do not open */ }
        context.services().publish(dev.jasper.remote.api.RemoteProjects.class, new dev.jasper.remote.projects.ProjectsService(connections,
            store::host, this::paneFolder, (file, window) -> remoteOpener.accept(file, window),
            (folder, window) -> store.host(folder.hostId()).ifPresent(saved -> openHostIn(window, saved, folder.path())), context.background()));
        follower=new dev.jasper.remote.ui.sftp.DirectoryFollower(ui,schedule,id->sftpUi.following(id)||context.terminals().activePane().map(pane->pane.id().equals(id)).orElse(false),this::probedDirectory,sftpUi::notice);
        sftpUi.onFollowRequested(follower::request);
        context.actions().register(ActionSpec.of(SFTP,"Open SFTP here").withIcon("folder").withKeywords(List.of("sftp","files","browse","remote"))
                .withDefaultBinding("cmd+shift+o").withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()),invoked->{
            invoked.pane().flatMap(this::sftpPane).ifPresentOrElse(pane->sftpUi.show(invoked.window(),pane),()->sftpUi.show(invoked.window()));
        });
        context.menus().terminalContext().add(SFTP);
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split"))
            .withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()), invoked -> invoked.pane().ifPresent(this::splitSameHost));
        splitAction.setEnabled(false);
        context.actions().register(ActionSpec.of(NEW_HOST, "New Host...").withIcon("add").withKeywords(List.of("ssh", "host", "session", "add")),
            invoked -> editHost(invoked.window(), Optional.empty()));
        context.actions().register(ActionSpec.of(IMPORT, "Import from ~/.ssh/config...").withKeywords(List.of("ssh", "import", "config")), invoked -> importConfig(invoked.window()));
        // Spec 13: Remote's entries live in the Session and Transfer standard menus, which show only while they have entries.
        PluginMenu session = context.menus().standard(dev.jasper.sdk.ui.StandardMenu.SESSION);
        session.add(CONNECT); session.add(HOSTS); session.add(SPLIT); session.addSeparator(); session.add(NEW_HOST); session.add(IMPORT);
        tunnelsUi.addToMenu(session);
        PluginMenu transfer = context.menus().standard(dev.jasper.sdk.ui.StandardMenu.TRANSFER);
        transfer.add(SFTP); transfer.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
        // View > Tool Windows lists HOSTS, SFTP_TOGGLE and the Tunnels toggle: the panels name them as their toggles.

        distros = wslProfiles();
        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)),
            () -> distros, this::openDistro, this::splitDistro);
        context.palette().register(scope);
        // Tab names' {host}; registered before any session, a restored SSH tab's included, can attach.
        // Piece names are global: another plugin that started first may hold host, and that must not fail Remote.
        try {
            hostPiece = context.terminals().addTabNamePiece(
                new dev.jasper.sdk.terminal.TabNamePieceSpec("host", "the SSH host this tab is connected to"));
        } catch (IllegalStateException held) {
            context.log().log(System.Logger.Level.WARNING, "Another plugin holds the {host} tab name piece; SSH tabs will not fill it: " + held.getMessage());
        }
        // Workspace tabs spec 13: an SSH tab saved with the workspace comes back to its host and folder.
        context.windows().restorer(SSH_TAB, this::restoreHost);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 60));
        status.setIcon(icon); status.setAction(HOSTS); status.setVisible(false);
        // Enterprise's default slots (spec 14.4): Sessions on the right; the default look keeps the left.
        context.panels().register(new PanelSpec(PANEL, "Sessions", icon, layout == dev.jasper.remote.workstation.Layout.CLASSIC ? Anchor.RIGHT : Anchor.LEFT)
            .withToggleActionId(HOSTS), this::createPanel);
        sessionsToolbar = new SessionsToolbar(context, store, icon, this::activateHost, window -> {
            if (sessionsHost == null) showPanel(window); else sessionsHost.show();
        }, () -> distros, this::openDistro, layout, sessionSelection, this::activeHostId);
        // The Sessions panel is the main window's: only that window's row moves it.
        sessionSelection.onChanged((windowId, row) -> {
            if (sessionsPanel != null && windowId.equals(sessionsHost.window().id())) row.ifPresent(sessionsPanel::selectRow);
        });

        store.onChanged(() -> { if (stopped) return; scope.changed(); refreshPanels(); sessionsToolbar.refresh(); tunnelsUi.hostsChanged(); sftpUi.hostsChanged(); store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "hosts.toml: {0}", warning)); });
        hostsLoaded = store.reload();
        tunnelStore.load();
        poll = new Timer(1000, event -> { store.poll(); tunnelStore.poll(); });
        poll.start();
        context.terminals().onProfilesChanged(() -> {
            if (stopped) return;
            distros = wslProfiles();
            scope.changed(); sessionsToolbar.refresh(); refreshPanels();
        });
        context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, event -> {
            event.paneId().ifPresent(id->{rememberPane(id);sftpFocused(id);}); refreshSplit(); syncSessions();
        });
        context.events().subscribe(TerminalEvents.PANE_FOCUSED, event -> { rememberPane(event.paneId());sftpFocused(event.paneId()); syncSessions(); });
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> { attachedPanes.remove(event.paneId()); restoredPanes.remove(event.paneId()); panes.remove(event.paneId()); paneIdentities.remove(event.paneId());follower.forget(event.paneId());sftpUi.ended(event.paneId()); recentPanes.remove(event.paneId()); distroPanes.remove(event.paneId()); refreshPanels(); syncSessions(); });
        context.events().subscribe(TerminalEvents.SESSION_STATE_CHANGED, event -> { if (distroPanes.containsKey(event.paneId())) refreshPanels(); });
        context.events().subscribe(TerminalEvents.WINDOW_CLOSED, event -> {
            for (var attempt : Set.copyOf(attempts)) if (attempt.window.id().equals(event.windowId())) attempt.close();
            sessionSelection.forget(event.windowId());
        });
        if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refreshPanels());
        context.events().subscribe(TerminalEvents.CWD_CHANGED,event->event.remoteDirectory().ifPresent(directory->{follower.reported(event.paneId());sftpUi.directory(event.paneId(),directory.path());}));
        context.events().subscribe(TerminalEvents.TAB_SELECTED,event->{context.terminals().window(event.windowId()).flatMap(window->window.activeTab()).flatMap(TabHandle::terminal).ifPresent(pane->sftpFocused(pane.id())); syncSessions();});
    }

    /** Remote's navigation actions with their default bindings; Keymap rebinds them. */
    private void registerNavigation() {
        context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon)
                .withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBindings("cmd+shift+h", "ctrl+shift+h"),
            invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(HOSTS, "Toggle SSH hosts").withIcon(icon)
                .withKeywords(List.of("ssh", "hosts", "panel", "show", "hide")).withDefaultBindings("cmd+alt+1", "alt+1"),
            invoked -> showPanel(invoked.window()));
        context.actions().register(ActionSpec.of(SFTP_TOGGLE, "Toggle SFTP")
                .withIcon("folder").withKeywords(List.of("sftp", "files", "panel", "show", "hide"))
                .withDefaultBindings("cmd+alt+2", "alt+2"),
            invoked -> sftpUi.toggle(invoked.window()));
    }

    RemoteSettings.Sftp sftpSettings() { return settings.sftp(); }

    @Override public void stop() {
        stopped = true;
        if(follower!=null)follower.close();if(sftpUi!=null)sftpUi.close();if(locations!=null)locations.close();if(reopener!=null)reopener.close();if(editChannels!=null)editChannels.close();if(transferUi!=null)transferUi.close();if(transfers!=null)transfers.close();
        if (configImport != null) configImport.close();
        for (var attempt : Set.copyOf(attempts)) attempt.close();
        for (var question : Set.copyOf(questions)) question.cancel(true);
        if (poll != null) poll.stop();
        if (sessionsToolbar != null) sessionsToolbar.close();
        if (tunnelsUi != null) tunnelsUi.close();
        if (connections != null) connections.close();
    }

    private Optional<dev.jasper.remote.ui.sftp.SftpUi.PaneTarget> sftpPane(WindowHandle window) { return window.activeTab().flatMap(TabHandle::terminal).flatMap(this::sftpPane); }
    private Optional<dev.jasper.remote.ui.sftp.SftpUi.PaneTarget> sftpPane(PaneHandle pane) {
        var identity=paneIdentities.get(pane.id());if(identity==null)return Optional.empty();
        return Optional.of(new dev.jasper.remote.ui.sftp.SftpUi.PaneTarget(pane.id(),identity,pane.info().remoteDirectory().map(dev.jasper.sdk.terminal.RemoteDirectory::path).orElse("")));
    }
    /** A saved host's configured route, for opening a chosen file whose logins were not resolved in this session. */
    private Optional<dev.jasper.remote.client.ConnectionIdentity> configuredIdentity(String root) {
        try { return Optional.of(connections.identity(UUID.fromString(root))); }
        catch (RuntimeException unknown) { return Optional.empty(); }
    }
    /** The identity last used for a host, else its configured route; empty when it is no longer saved. */
    private Optional<dev.jasper.remote.client.ConnectionIdentity> remoteIdentity(UUID hostId) {
        String root = hostId.toString();
        return remoteHosts.identity(root).or(() -> configuredIdentity(root));
    }

    /** Where an SSH pane Remote opened is, for {@code RemoteProjects.locate}; empty until its folder is known. */
    private Optional<dev.jasper.remote.api.RemoteFolder> paneFolder(UUID paneId) {
        return context.terminals().pane(paneId).flatMap(this::sftpPane).filter(target -> target.directory().startsWith("/"))
            .flatMap(target -> store.host(target.identity().host().id())
                .map(saved -> new dev.jasper.remote.api.RemoteFolder(saved.id(), saved.name(), target.directory())));
    }
    private void sftpFocused(UUID id) {
        context.terminals().pane(id).ifPresent(pane->sftpPane(pane).ifPresent(target->sftpUi.pane(pane.tab().window(),target)));
        follower.request(id);
    }
    private void probedDirectory(UUID paneId, String path) {
        var pending = attachedPanes.get(paneId);
        var identity = paneIdentities.get(paneId);
        if (pending == null || identity == null) return;
        pending.reportRemoteDirectory(new dev.jasper.sdk.terminal.RemoteDirectory(identity.host().hostname(), path));
        sftpUi.directory(paneId, path);
    }
    /** Dedicated supported shells share their folder with pane metadata and SFTP follow. */
    private void followFolder(UUID paneId, Connections.Shell shell, dev.jasper.remote.hosts.HostInfo info) {
        if (shell.folder().isEmpty() || "Windows".equals(info.os())) return;
        var folder = shell.folder().orElseThrow();
        follower.track(paneId, () -> CompletableFuture.supplyAsync(() -> {
            try { return folder.read(); } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, context.background()));
        var pending = attachedPanes.get(paneId);
        folder.onEnter(() -> ui.execute(() -> {
            if (attachedPanes.get(paneId) == pending) follower.enter(paneId);
        }));
        follower.request(paneId);
    }
    static CompletableFuture<Void> validateResumeIdentity(dev.jasper.remote.client.ConnectionIdentity expected,CompletableFuture<dev.jasper.remote.client.ConnectionIdentity> source) {
        CompletableFuture<Void> validated=source.thenApply(actual->{if(!actual.equals(expected)) throw new java.util.concurrent.CompletionException(new IOException("Saved host or authentication identity changed; queue a new transfer"));return null;});
        validated.whenComplete((ignored,failure)->{if(validated.isCancelled())source.cancel(true);});
        return validated;
    }
    dev.jasper.remote.transfer.TransferCoordinator transfers() { return transfers; }
    CompletableFuture<Void> transfersStopped() { return transfers==null?CompletableFuture.completedFuture(null):transfers.stopped(); }

    // ---- sessions

    private void connect(PendingSession pending, UUID hostId, Optional<String> folder) { connect(pending, hostId, folder, false); }

    /**
     * As {@link #connect(PendingSession, UUID, Optional)}; a {@code quiet} connect, a restored SSH tab's first, asks
     * nothing: a host-key question is answered Cancel, and a failure closes the overlay and leaves the tab not connected,
     * with Reconnect, which asks as usual (workspace plan 6b Rulings 29).
     */
    private void connect(PendingSession pending, UUID hostId, Optional<String> folder, boolean quiet) {
        // A quiet connect's failures all say Not connected, with Reconnect (workspace plan 6b Rulings 45).
        String failed = quiet ? "Not connected: " : "";
        var host = store.host(hostId);
        if (host.isEmpty()) { pending.fail(failed + "Host not found"); return; }
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (focusExistingAttempt(window)) { pending.fail("Another connection is already in progress"); return; }
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host.get(), folder, shell -> attach(pending, shell)); }
        catch (RuntimeException failure) { pending.fail(failed + message(failure)); return; }
        attempt.quiet = quiet;
        attempt.cancelled = () -> {
            if (stopped) return;
            // A quiet connect that failed says why, but for the host-key question it answered Cancel (Rulings 29).
            if (quiet) pending.fail("Not connected: " + attempt.reason.orElse("Reconnect to sign in"));
            else pending.fail("Cancelled");
        };
        var cancellation = pending.onCancelled(() -> ui.execute(attempt::close));
        attempt.finished = cancellation::close;
        attempts.add(attempt); attempt.start();
    }

    void openHost(WindowHandle window, RemoteHost host) {
        prepare(window, host, Optional.empty(), request -> context.terminals().openTab(window, request));
    }
    /** Opens a new tab on a saved host whose login shell starts in {@code folder}; Reconnect returns there. */
    void openHostIn(WindowHandle window, RemoteHost host, String folder) {
        prepare(window, host, Optional.of(folder), request -> context.terminals().openTab(window, request));
    }
    void splitHost(PaneHandle pane, RemoteHost host) {
        prepare(pane.tab().window(), host, Optional.empty(), request -> context.terminals().split(pane, Direction.RIGHT, request));
    }

    /** How an SSH tab comes back (workspace tabs spec 13): its saved host, that host's name for its title, and its folder. */
    static dev.jasper.sdk.RestoreState sshTab(UUID hostId, String name, Optional<String> folder) {
        var values = new java.util.LinkedHashMap<String, String>();
        values.put("host", hostId.toString());
        values.put("name", name);
        folder.ifPresent(path -> values.put("folder", path));
        return new dev.jasper.sdk.RestoreState(SSH_TAB, values);
    }

    /**
     * An SSH tab's restore state, as {@link #sshTab} builds it, for a tab about to open: one too large for a restore state
     * leaves out the folder, and when even that does not fit the tab is not saved, with one warning that names no folder.
     * Opening a tab never fails for its restore state (workspace plan 6b's review).
     */
    private Optional<dev.jasper.sdk.RestoreState> sshRestore(UUID hostId, String name, Optional<String> folder) {
        try { return Optional.of(sshTab(hostId, name, folder)); }
        catch (IllegalArgumentException tooLarge) { /* the folder, or the name, is too long: try without the folder */ }
        if (folder.isPresent()) {
            try {
                var state = sshTab(hostId, name, Optional.empty());
                context.log().log(System.Logger.Level.WARNING, "An SSH tab on host {0} comes back in its default folder: its folder"
                    + " is too long for a restore state of at most {1} characters", hostId, dev.jasper.sdk.RestoreState.MAX_CHARACTERS);
                return Optional.of(state);
            } catch (IllegalArgumentException stillTooLarge) { /* the name alone is too long */ }
        }
        context.log().log(System.Logger.Level.WARNING, "An SSH tab on host {0} does not come back at the next start: its restore"
            + " state would hold more than {1} characters", hostId, dev.jasper.sdk.RestoreState.MAX_CHARACTERS);
        return Optional.empty();
    }

    /** Why a saved host is not known while Remote's hosts.toml cannot be read: its error's first line (6b's review). */
    private Optional<String> hostsUnreadable() {
        return store.error().map(error -> "Remote's hosts.toml could not be read: " + error.lines().findFirst().orElse(""));
    }

    /**
     * Opens a saved SSH tab again (workspace tabs spec 13), waiting, as every restored terminal tab does, until it is first
     * shown. Then Remote chooses: it reconnects when every host on the route signs in with nothing to ask (through the SSH
     * agent), after any connection already in progress in that window, and otherwise leaves the tab not connected with
     * Reconnect, so restoring windows never asks for a password, or the Vault for a grant, before the user does. A state
     * that names no host, or a host gone from the saved hosts once they have loaded, declines.
     *
     * @return empty once the tab is open; else why it does not come back
     */
    Optional<String> restoreHost(dev.jasper.sdk.ui.RestoreRequest request) {
        Map<String, String> saved = request.state().values();
        UUID hostId;
        try { hostId = UUID.fromString(saved.getOrDefault("host", "")); }
        catch (IllegalArgumentException malformed) { return Optional.of("it names no saved host"); }
        // The saved hosts are read in the background at start: once they are, a host that is gone restores nothing.
        if (hostsLoaded.isDone() && store.host(hostId).isEmpty()) return Optional.of(hostsUnreadable().orElse("it names no saved host"));
        Optional<String> folder = Optional.ofNullable(saved.get("folder"));
        String name = saved.getOrDefault("name", "").isBlank() ? request.title() : saved.get("name");
        boolean[] first = {true};
        SessionSpec spec = new SessionSpec(name.isBlank() ? "SSH" : name, Optional.of(serverIcon), dev.jasper.sdk.terminal.ExitPolicy.KEEP_OPEN, pending -> {
            if (!first[0]) { connect(pending, hostId, folder); return; }
            first[0] = false;
            // The saved hosts are read in the background at start: the choice waits for them.
            hostsLoaded.whenComplete((loaded, failure) -> ui.execute(() -> {
                if (stopped || pending.isCancelled()) return;
                if (store.host(hostId).isEmpty()) pending.fail(hostsUnreadable().orElse("Its host is no longer saved in Remote"));
                else if (signsInAlone(hostId)) connectWhenFree(pending, hostId, folder);
                else pending.fail("Not connected: Reconnect to sign in");
            }));
        }, sshRestore(hostId, name, folder));
        context.terminals().openTab(request.window(), OpenRequest.session(spec)).ifPresent(pane -> restoredPanes.put(pane.id(), hostId));
        return Optional.empty();
    }

    /**
     * Connects a restored SSH tab once no connection is in progress in its window, which shows one at a time: several
     * restored tabs shown together connect one after another (workspace tabs spec 13).
     */
    private void connectWhenFree(PendingSession pending, UUID hostId, Optional<String> folder) {
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (attempts.stream().noneMatch(attempt -> attempt.window.id().equals(window.id()))) { connect(pending, hostId, folder, true); return; }
        waitingConnects.computeIfAbsent(window.id(), id -> new java.util.ArrayDeque<>()).add(() -> connectWhenFree(pending, hostId, folder));
    }

    /** The connection in progress in {@code window} ended: the next restored SSH tab still waiting there connects. */
    private void nextWaitingConnect(UUID window) {
        var waiting = waitingConnects.get(window);
        // The next one that still waits connects; one cancelled meanwhile, or failing before it starts, passes its turn on.
        while (waiting != null && !waiting.isEmpty() && attempts.stream().noneMatch(attempt -> attempt.window.id().equals(window)))
            waiting.poll().run();
        if (waiting != null && waiting.isEmpty()) waitingConnects.remove(window);
    }

    /**
     * {@code open}, a restored document's quiet channel, with {@code owner}'s host-key questions answered Cancel until it
     * completes (workspace plan 6b Rulings 29).
     */
    private <T> CompletableFuture<T> quietly(Optional<WindowHandle> owner, java.util.function.Supplier<CompletableFuture<T>> open) {
        owner.ifPresent(window -> quietWindows.merge(window.id(), 1, Integer::sum));
        CompletableFuture<T> opened;
        try { opened = open.get(); }
        catch (RuntimeException failure) { opened = CompletableFuture.failedFuture(failure); }
        opened.whenComplete((value, failure) -> ui.execute(() -> owner.ifPresent(window ->
            quietWindows.computeIfPresent(window.id(), (id, count) -> count == 1 ? null : count - 1))));
        return opened;
    }

    /**
     * A restored document's quiet channel to {@code identity}, opened asking nothing (workspace plan 6b Rulings 29). Signing
     * in must still ask nothing as it opens: the connection that let the document read quietly may have closed since its
     * read was queued, and a Vault login would then ask (6b's review).
     */
    CompletableFuture<dev.jasper.remote.sftp.FileEndpoint> openQuietly(dev.jasper.remote.client.ConnectionIdentity identity, Optional<WindowHandle> owner) {
        if (!signsInAlone(identity)) return CompletableFuture.failedFuture(dev.jasper.remote.edit.RemoteDocument.notSignedIn(identity));
        return quietly(owner, () -> endpoints.open(Optional.of(identity), owner.orElse(null), status -> { }));
    }

    /** Whether a host-key question for {@code owner} is answered Cancel now: a quiet connect runs there (Rulings 29). */
    private boolean quiet(WindowHandle owner) {
        return owner != null && (quietWindows.containsKey(owner.id())
            || attempts.stream().anyMatch(attempt -> attempt.quiet && attempt.window.id().equals(owner.id())));
    }

    /** Whether every host on {@code hostId}'s route signs in without asking: through the SSH agent (workspace plan 6b Rulings 9). */
    private boolean signsInAlone(UUID hostId) {
        var seen = new java.util.HashSet<UUID>();
        Optional<RemoteHost> current = store.host(hostId);
        if (current.isEmpty()) return false;
        while (current.isPresent()) {
            RemoteHost hop = current.get();
            if (!seen.add(hop.id()) || !signsInAlone(hop)) return false;
            current = hop.jump().flatMap(store::host);
        }
        return true;
    }

    /**
     * Whether {@code host} signs in without asking: through the SSH agent. A Vault login does not, even while the Vault is
     * unlocked: the Vault may ask to grant Remote the credential (workspace plan 6b Rulings 9).
     */
    private boolean signsInAlone(RemoteHost host) {
        return host.auth() instanceof Auth.Agent;
    }

    /** Whether signing in to every host of {@code identity}'s route asks nothing: an open connection, or each signs in alone. */
    private boolean signsInAlone(dev.jasper.remote.client.ConnectionIdentity identity) {
        return connections.authenticated(identity) || identity.hops().stream().allMatch(hop -> signsInAlone(hop.host()));
    }

    void activateHost(WindowHandle window, RemoteHost host) {
        for (UUID id : recentPanes.sequencedKeySet().reversed()) {
            if (!host.id().equals(panes.get(id))) continue;
            var pane = context.terminals().pane(id);
            if (pane.isPresent() && pane.get().info().state() == dev.jasper.sdk.terminal.SessionState.RUNNING) { pane.get().focus(); return; }
        }
        // Else the oldest restored tab of the host that still waits to be shown, NOT_STARTED, shows: it then connects, or
        // offers Reconnect, as every restored SSH tab does (workspace plan 6a Task 1's hand-off; Rulings 41).
        restoredPanes.keySet().removeIf(id -> context.terminals().pane(id).isEmpty());
        for (var entry : restoredPanes.entrySet()) {
            if (!host.id().equals(entry.getValue())) continue;
            var pane = context.terminals().pane(entry.getKey());
            if (pane.isPresent() && pane.get().info().state() == dev.jasper.sdk.terminal.SessionState.NOT_STARTED) { pane.get().focus(); return; }
        }
        openHost(window, host);
    }

    /** Opens a WSL distribution in a new local tab; the application runs it, Remote only counts the pane. */
    void openDistro(WindowHandle window, ShellProfileInfo profile) {
        if (stopped || !window.isOpen()) return;
        context.terminals().openTab(window, OpenRequest.localProfile(profile.id())).ifPresent(pane -> trackDistro(pane, profile));
    }

    void splitDistro(PaneHandle pane, ShellProfileInfo profile) {
        if (stopped) return;
        context.terminals().split(pane, Direction.RIGHT, OpenRequest.localProfile(profile.id())).ifPresent(created -> trackDistro(created, profile));
    }

    private void trackDistro(PaneHandle pane, ShellProfileInfo profile) {
        distroPanes.put(pane.id(), profile.id());
        refreshPanels();
    }

    private List<ShellProfileInfo> wslProfiles() {
        return context.terminals().profiles().stream().filter(profile -> profile.kind() == ShellProfileInfo.Kind.WSL).toList();
    }

    /** Local panes Remote opened for this distribution whose shell still runs. */
    private int distroSessions(ShellProfileInfo profile) {
        int count = 0;
        for (var entry : distroPanes.entrySet()) {
            if (!entry.getValue().equals(profile.id())) continue;
            var info = context.terminals().pane(entry.getKey()).map(PaneHandle::info);
            if (info.isPresent() && info.get().kind() == SessionKind.LOCAL && info.get().state() != dev.jasper.sdk.terminal.SessionState.EXITED) count++;
        }
        return count;
    }

    private void rememberPane(UUID id) {
        UUID hostId = panes.get(id);
        if (hostId == null) return;
        recentPanes.remove(id); recentPanes.put(id, hostId);
    }

    private boolean focusExistingAttempt(WindowHandle window) {
        for (ConnectAttempt attempt : attempts) if (attempt.window.id().equals(window.id())) {
            attempt.dialog.toFront(); return true;
        }
        return false;
    }

    private void prepare(WindowHandle window, RemoteHost host, Optional<String> folder, Function<OpenRequest, Optional<PaneHandle>> open) {
        if (!window.isOpen() || stopped || focusExistingAttempt(window)) return;
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host, folder, shell -> {
            var ready = new java.util.concurrent.atomic.AtomicReference<>(shell);
            SessionSpec spec = new SessionSpec(host.name(), java.util.Optional.of(serverIcon), dev.jasper.sdk.terminal.ExitPolicy.KEEP_OPEN, pending -> {
                Connections.Shell first = ready.getAndSet(null);
                if (first == null) connect(pending, host.id(), folder);
                else ui.execute(() -> attach(pending, first));
            }, sshRestore(host.id(), host.name(), folder));
            try {
                if (open.apply(OpenRequest.session(spec)).isEmpty()) {
                    var unused = ready.getAndSet(null); if (unused != null) unused.connection().close().run();
                }
            } catch (RuntimeException failure) {
                var unused = ready.getAndSet(null); if (unused != null) unused.connection().close().run();
                throw failure;
            }
        }); } catch (RuntimeException failure) {
            context.notifications().group().error("Could not start the connection", host.name() + ": " + message(failure), failure);
            return;
        }
        attempts.add(attempt); attempt.start();
    }

    /** Gives {@code pending} its connected shell; package-private so a test can make it fail. */
    void attach(PendingSession pending, Connections.Shell shell) {
        if (stopped || pending.isCancelled()) { shell.connection().close().run(); return; }
        UUID paneId = pending.pane().id();
        attachedPanes.put(paneId, pending);
        restoredPanes.remove(paneId);
        follower.forget(paneId);
        panes.put(paneId, shell.hostId()); paneIdentities.put(paneId, shell.identity()); rememberPane(paneId);sftpFocused(paneId); syncSessions();
        shell.connection().exited().whenComplete((ignored, exit) -> ui.execute(() -> {
            if (attachedPanes.get(paneId) != pending) return;
            attachedPanes.remove(paneId);
            panes.remove(paneId); paneIdentities.remove(paneId); follower.forget(paneId); recentPanes.remove(paneId); refreshSplit();
            if (!stopped) { sftpUi.ended(paneId); refreshPanels(); syncSessions(); }
        }));
        pending.attach(shell.connection());
        // Tab names' {host}: the saved host's address, kept while the tab shows it (tab names spec 7).
        if (hostPiece != null) hostPiece.set(pending.pane().tab(), shell.host().hostname());
        RemoteHost authenticatedHost = shell.host();
        connections.inspect(shell).thenAccept(info -> {
            if (stopped) return;
            ui.execute(() -> { if (!stopped && attachedPanes.get(paneId) == pending) followFolder(paneId, shell, info); });
            context.background().execute(() -> {
                try { hostInfo.put(authenticatedHost, info); }
                catch (IOException unavailable) { context.log().log(System.Logger.Level.DEBUG, "Host information cache: {0}", unavailable.getMessage()); }
                ui.execute(() -> { if (!stopped) refreshPanels(); });
            });
        });
        refreshSplit(); refreshPanels();
    }

    private final class ConnectAttempt {
        final WindowHandle window;
        final RemoteHost host;
        final Optional<String> folder;
        final java.util.function.Consumer<Connections.Shell> success;
        final PluginDialog dialog;
        final ConnectionPanel panel;
        CompletableFuture<Connections.Shell> future;
        boolean closed, delivered;
        /** A restored tab's first connect: it asks nothing, and a failure leaves the tab not connected (Rulings 29). */
        boolean quiet;
        /** Why a quiet connect failed, for its notice; empty when it was cancelled or its host-key question answered Cancel. */
        Optional<String> reason = Optional.empty();
        /** A host-key question this quiet connect raised was answered Cancel: its failure is that, not a reason to show. */
        boolean refusedQuietly;
        Runnable cancelled = () -> {}, finished = () -> {};
        ConnectAttempt(WindowHandle window, RemoteHost host, Optional<String> folder, java.util.function.Consumer<Connections.Shell> success) {
            this.window = window; this.host = host; this.folder = folder; this.success = success;
            dialog = context.windows().overlay(new OverlaySpec("Connecting to " + host.name(), window));
            panel = new ConnectionPanel(context.kit(), host.name(), host.label(), this::start, this::close);
            currentConnectionPanel = panel;
            dialog.setContent(panel.view()); dialog.onClosed(this::close);
        }
        void start() {
            if (closed || stopped || !window.isOpen()) { close(); return; }
            if (future != null && !future.isDone()) return;
            panel.working();
            try { dialog.show(); }
            catch (RuntimeException failure) {
                context.notifications().group().error("Could not show the connection dialog", host.name() + ": " + message(failure), failure);
                close();
                return;
            }
            var dimensions = window.activeTab().flatMap(TabHandle::terminal).map(PaneHandle::info);
            int cols = dimensions.map(info -> Math.max(1, info.columns())).orElse(80);
            int rows = dimensions.map(info -> Math.max(1, info.rows())).orElse(24);
            // Followed panes get their own connection so the probe can tell their shell apart (see the directory probe spec).
            boolean dedicated = host.followDirectory() && !"Windows".equals(hostInfo.get(host).os());
            future = connections.shell(host.id(), window, cols, rows, panel::status, dedicated, folder);
            future.whenComplete((shell, failure) -> ui.execute(() -> {
                if (closed || stopped || !window.isOpen()) { if (shell != null) shell.connection().close().run(); close(); return; }
                // A quiet connect that fails asks nothing more: its overlay closes and the tab offers Reconnect, saying why
                // unless the failure is the host-key question it answered Cancel (Rulings 29).
                if (failure != null && quiet) { if (!refusedQuietly) reason = Optional.of(message(failure)); close(); return; }
                if (failure != null) { panel.failed(message(failure)); return; }
                try { success.accept(shell); delivered = true; close(); }
                catch (RuntimeException problem) {
                    shell.connection().close().run();
                    // A quiet connect asks nothing more here either: the tab says why, with Reconnect (Rulings 45).
                    if (quiet) { reason = Optional.of(message(problem)); close(); return; }
                    panel.failed(message(problem));
                }
            }));
        }
        void close() {
            if (closed) return;
            closed = true; attempts.remove(this);
            if (future != null && !future.isDone()) future.cancel(true);
            // The overlay may already have disposed the view; the panel ignores any later status (a stale completion).
            panel.close(); finished.run();
            if (!delivered) cancelled.run();
            dialog.close();
            if (currentConnectionPanel == panel) currentConnectionPanel = null;
            // A restored SSH tab waiting for this window connects next (workspace tabs spec 13).
            ui.execute(() -> nextWaitingConnect(window.id()));
        }
    }

    private void splitSameHost(PaneHandle pane) {
        UUID hostId = panes.get(pane.id());
        Optional<RemoteHost> host = hostId == null ? Optional.empty() : store.host(hostId);
        if (host.isEmpty()) { context.notifications().group().warning("Split with Same Host needs a connected SSH pane", ""); return; }
        splitHost(pane, host.get());
    }

    private void refreshSplit() {
        if (stopped) return;
        splitAction.setEnabled(context.terminals().activePane().map(pane -> panes.containsKey(pane.id())).orElse(false));
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    // ---- prompts and dialogs

    /** The host-key question, answered through a window-modal dialog; closing it answers Cancel. */
    CompletableFuture<HostKeyVerifier.Decision> askHostKey(HostKeyVerifier.Question question) {
        return askHostKey(owner().orElse(null), question);
    }

    CompletableFuture<HostKeyVerifier.Decision> askHostKey(WindowHandle owner, HostKeyVerifier.Question question) {
        var decision = new CompletableFuture<HostKeyVerifier.Decision>();
        ui.execute(() -> {
            if (decision.isDone() || stopped) { decision.cancel(true); return; }
            // A quiet connect there, a restored tab's, asks nothing: it fails, and Reconnect or Retry asks (Rulings 29).
            if (quiet(owner)) {
                for (ConnectAttempt attempt : attempts) if (attempt.quiet && attempt.window.id().equals(owner.id())) attempt.refusedQuietly = true;
                decision.complete(HostKeyVerifier.Decision.CANCEL);
                return;
            }
            questions.add(decision);
            if (owner == null || !owner.isOpen()) { decision.complete(HostKeyVerifier.Decision.CANCEL); return; }
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Verify host key " + question.host(), owner, true));
            var panel = new HostKeyPanel(context.kit(), question, answer -> { decision.complete(answer); dialog.close(); });
            currentHostKeyPanel = panel;
            dialog.setContent(panel.view());
            decision.whenComplete((answer, failure) -> ui.execute(() -> { questions.remove(decision); dialog.close(); }));
            // The surface disposed the view first; closing never trusts: it answers Cancel, as before.
            dialog.onClosed(() -> { panel.close(); if (currentHostKeyPanel == panel) currentHostKeyPanel = null; decision.complete(HostKeyVerifier.Decision.CANCEL); });
            dialog.show();
        });
        return decision;
    }

    private Optional<WindowHandle> owner() { return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()); }

    void editHost(WindowHandle window, Optional<RemoteHost> editing) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(editing.isPresent() ? "Edit " + editing.get().name() : "Add Host", window, true));
        HostEditor[] editor = new HostEditor[1];
        boolean[] closed = {false};
        editor[0] = new HostEditor(context.kit(), ui, store.hosts(), editing, vault.isPresent(), this::credentialDescriptor,
            vault.isPresent() ? request -> vault.get().pick(window, request) : null,
            host -> store.put(host).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (!stopped && failure != null)
                    context.notifications().group().error("Could not save the host", message(failure), failure);
            })),
            () -> { if (!stopped && !closed[0]) dialog.close(); }, dialog::close,
            host -> { if (!stopped && window.isOpen()) openHost(window, host); });
        editor[0].layout(layout);
        editor[0].setDefaultGroupName(panelState.defaultGroup());
        currentEditor = editor[0];
        dialog.setContent(editor[0].view());
        dialog.onClosed(() -> { closed[0] = true; editor[0].close(); if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    private void deleteHost(WindowHandle window, RemoteHost host) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Delete " + host.name() + "?", window, true));
        var confirm = new ConfirmPanel(context.kit(), deleteHostText(host), "Delete",
            () -> store.remove(host.id()).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (failure != null) context.notifications().group().error("Could not delete the host", message(failure), failure);
                dialog.close();
            })), dialog::close);
        currentConfirm = confirm;
        dialog.setContent(confirm.view());
        dialog.onClosed(() -> { confirm.close(); if (currentConfirm == confirm) currentConfirm = null; });
        dialog.show();
    }

    /** The confirmation names the tunnels that go through the host: they stay, marked Host missing. */
    private String deleteHostText(RemoteHost host) {
        List<String> users = tunnelsUi.usersOf(host.id());
        String tunnels = users.isEmpty() ? "" : " " + (users.size() == 1 ? "1 tunnel uses" : users.size() + " tunnels use")
            + " this host (" + String.join(", ", users) + "); they stay, marked Host missing.";
        return "Delete the saved host " + host.name() + "? Open sessions stay connected." + tunnels;
    }

    private void importConfig(WindowHandle window) {
        if (configImport != null) configImport.close();
        configImport = new ConfigImportController(context, vault, store, sshDir, ui);
        configImport.show(window);
    }

    // ---- panel and status

    private dev.jasper.sdk.ui.UiView createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new HostsPanel(context.kit(), new HostsPanel.Actions(h -> openHost(window, h), h -> window.activeTab().flatMap(TabHandle::terminal).ifPresent(pane -> splitHost(pane, h)),
            editing -> editHost(window, editing), h -> reportMutation("Could not duplicate the host", store.put(duplicate(h))), h -> deleteHost(window, h),
            (h, favorite) -> reportMutation("Could not update favorites", store.put(h.withFavorite(favorite))), () -> importConfig(window)), layout);
        panel.onHeaderChanged(host::setHeader);
        if (layout == dev.jasper.remote.workstation.Layout.CLASSIC) panel.onSelectionChanged(row -> sessionSelection.set(window.id(), row));
        panel.setCredentialLabels(this::credentialLabel);
        panel.setDefaultGroup(panelState.defaultGroup());
        panel.onRenameDefaultGroup(() -> renameDefaultGroup(window));
        panel.onActivate(h -> activateHost(window, h));
        panel.onBrowseFiles(h->sftpUi.browse(window,h));
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.onDistros(profile -> openDistro(window, profile),
            profile -> window.activeTab().flatMap(TabHandle::terminal).ifPresent(pane -> splitDistro(pane, profile)));
        panel.setDistros(distros, this::distroSessions);
        panel.setCollapsed(panelState.collapsed());
        panel.setWslCollapsed(panelState.wslCollapsed());
        panel.onCollapsedChanged(() -> { try { panelState.save(panel.collapsed(), panel.wslCollapsed()); } catch (IOException failure) { context.log().log(System.Logger.Level.WARNING, "panel state: {0}", failure.getMessage()); } });
        panel.setHosts(store.hosts(), store.error());
        // In a window of its own or docked at the bottom the list sits beside the detail; the panel is right from the start
        // and follows each move.
        panel.wide(dev.jasper.remote.workstation.Layout.wide(host.placement()));
        host.onPlacement(placement -> panel.wide(dev.jasper.remote.workstation.Layout.wide(placement)));
        sessionsPanel = panel; sessionsHost = host;
        seedSessions(window.id(), panel);
        // Shown again, the first host is selected as when the old list was shown (under CLASSIC the window's row, which the
        // toolbar view shares); focus follows an explicit opening only (a restored panel's window is not showing yet, so
        // the kit's focus request does nothing).
        host.onVisibility(visible -> { if (visible) { panel.shown(); seedSessions(window.id(), panel); panel.focusWhenOpened(); } });
        // The host disposed the view; the panel goes inert so no later refresh touches it.
        host.onClosed(() -> { panel.close(); if (sessionsPanel == panel) { sessionsPanel = null; sessionsHost = null; } });
        return panel.view();
    }

    private void reportMutation(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!stopped && failure != null) context.notifications().group().error(title, message(failure), failure);
        }));
    }

    private RemoteHost duplicate(RemoteHost host) {
        String base = host.name() + " copy";
        String name = base;
        int i = 2;
        while (nameTaken(name)) name = base + " " + i++;
        return RemoteHost.create(name, host.hostname(), host.port(), host.username(), host.auth(), host.group(), host.jump());
    }

    private boolean nameTaken(String name) { return store.hosts().stream().anyMatch(host -> host.name().equalsIgnoreCase(name)); }

    private void showPanel(WindowHandle window) {
        context.panels().toggle(PANEL, window);
    }

    private void refreshPanels() {
        var panel = sessionsPanel;
        if (panel == null) return;
        panel.setCredentialLabels(this::credentialLabel);
        panel.setDefaultGroup(panelState.defaultGroup());
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.setDistros(distros, this::distroSessions);
        panel.setHosts(store.hosts(), store.error());
        seedSessions(sessionsHost.window().id(), panel);
    }

    /**
     * Under CLASSIC, after the window's active pane or its SSH panes changed: each toolbar view and Sessions tree shows the
     * window's row again (its choice, else the active SSH pane's host, else the first), as Connect would act on it.
     */
    private void syncSessions() {
        if (stopped || layout != dev.jasper.remote.workstation.Layout.CLASSIC || sessionsToolbar == null) return;
        sessionsToolbar.renderViews();
        if (sessionsPanel != null) seedSessions(sessionsHost.window().id(), sessionsPanel);
    }

    /**
     * Under CLASSIC the Sessions tree selects the window's row as its toolbar view shows it (spec 14.4). A row the tree
     * does not list (filtered out) leaves the tree's own selection, which then becomes the window's choice.
     */
    private void seedSessions(UUID windowId, HostsPanel panel) {
        if (layout != dev.jasper.remote.workstation.Layout.CLASSIC || sessionsToolbar == null) return;
        var window = context.terminals().window(windowId).filter(WindowHandle::isOpen);
        if (window.isEmpty()) return;
        var selected = panel.selectedRow().map(HostsPanel::rowId);
        // The user's own choice of a row the toolbar does not list (a group) stays selected in the tree.
        if (sessionSelection.get(windowId).isPresent() && sessionSelection.get(windowId).equals(selected)) return;
        var row = sessionsToolbar.current(window.get());
        row.ifPresent(panel::selectRow);
        var shown = panel.selectedRow().map(HostsPanel::rowId);
        if (row.isPresent() && !row.equals(shown)) sessionSelection.set(windowId, shown);
    }

    /** The host of the window's active pane, when Remote opened that pane. */
    private Optional<UUID> activeHostId(WindowHandle window) {
        return window.activeTab().flatMap(TabHandle::terminal).flatMap(pane -> Optional.ofNullable(panes.get(pane.id())));
    }

    private int sessionCount(RemoteHost host) { return (int) panes.values().stream().filter(host.id()::equals).count(); }
    private String hostDetails(RemoteHost host) {
        var info = hostInfo.get(host);
        String address = info.address().equals(host.hostname()) ? "" : info.address();
        return info.os().isEmpty() ? address : address.isEmpty() ? info.os() : info.os() + " · " + address;
    }

    private void renameDefaultGroup(WindowHandle window) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Rename default group", window, true));
        GroupNamePanel[] editor = new GroupNamePanel[1];
        editor[0] = new GroupNamePanel(context.kit(), panelState.defaultGroup(), name -> {
            try {
                String value = name.strip();
                if (store.hosts().stream().anyMatch(host -> !host.group().isEmpty() && host.group().equalsIgnoreCase(value)))
                    throw new IllegalArgumentException("A group with that name already exists");
                panelState.renameDefaultGroup(value);
                if (sessionsPanel != null) sessionsPanel.setCollapsed(panelState.collapsed());
                refreshPanels(); dialog.close();
            } catch (IOException | IllegalArgumentException failure) { editor[0].showError(message(failure)); }
        }, dialog::close);
        currentGroupEditor = editor[0]; dialog.setContent(editor[0].view());
        dialog.onClosed(() -> { editor[0].close(); if (currentGroupEditor == editor[0]) currentGroupEditor = null; }); dialog.show();
    }

    private String credentialLabel(RemoteHost host) {
        return switch (host.auth()) {
            case Auth.VaultKeys keys -> vault.isEmpty() ? "needs Credential Vault" : vault.get().lockState() != LockState.UNLOCKED ? "Vault locked" : keys.credentialIds().size() + " Vault key(s)";
            case Auth.Agent agent -> "SSH agent";
            case Auth.Vault credential -> vault.isEmpty() ? "needs Credential Vault" : vault.get().lockState() != LockState.UNLOCKED ? "Vault locked" : credentialName(credential.credentialId()).orElse("credential missing");
        };
    }

    private Optional<CredentialDescriptor> credentialDescriptor(UUID id) {
        if (vault.isEmpty()) return Optional.empty();
        return vault.get().credentials().stream().filter(descriptor -> descriptor.id().equals(id)).findFirst();
    }

    private Optional<String> credentialName(UUID id) {
        if (vault.isEmpty()) return Optional.empty();
        return credentialDescriptor(id).map(CredentialDescriptor::name);
    }

    private void refreshStatus() {
        if (stopped) return;
        int count = connections.channelCount();
        status.setText(count == 1 ? "1 SSH session" : count + " SSH sessions");
        status.setVisible(count > 0);
    }

    HostStore store() { return store; }
    Connections connections() { return connections; }
    HostKeyPanel currentHostKeyPanel() { return currentHostKeyPanel; }
    HostEditor currentEditor() { return currentEditor; }
    ImportPanel currentImport() { return configImport == null ? null : configImport.panel(); }
    GroupNamePanel currentGroupEditor() { return currentGroupEditor; }
    ConnectionPanel currentConnectionPanel() { return currentConnectionPanel; }
    ConfirmPanel currentConfirm() { return currentConfirm; }
    /** The panes of restored SSH tabs that have not attached yet, oldest first, for tests. */
    List<UUID> restoredPaneIds() { return List.copyOf(restoredPanes.keySet()); }
    /** The Sessions panel while it is open, for tests. */
    HostsPanel hostsPanel() { return sessionsPanel; }
    dev.jasper.remote.ui.tunnels.TunnelsUi tunnels() { return tunnelsUi; }
    /** The SFTP explorers, for tests. */
    dev.jasper.remote.ui.sftp.SftpUi sftp() { return sftpUi; }
    dev.jasper.remote.tunnels.TunnelStore tunnelStore() { return tunnelStore; }
}
