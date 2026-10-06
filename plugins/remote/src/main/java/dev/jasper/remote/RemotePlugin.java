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
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Wires the plugin: the hosts store and its poll, trust, the agent, the connection registry, the session
 * provider, the Sessions panel (saved hosts and WSL distributions), the scope, the actions, the SSH menu, the status item and the dialogs.
 */
public class RemotePlugin implements Plugin {
    public static final String CONNECT = "dev.jasper.remote.connect", HOSTS = "dev.jasper.remote.hosts", SPLIT = "dev.jasper.remote.split";
    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", MENU = "dev.jasper.remote.menu", PANEL = "dev.jasper.remote.panel";

    private final Executor ui;
    private final Function<PluginContext,Executor> transferExecutor;
    private dev.jasper.remote.transfer.TransferCoordinator transfers;
    private dev.jasper.remote.ui.transfers.TransferUi transferUi;
    private dev.jasper.remote.ui.sftp.SftpUi sftpUi;
    private dev.jasper.remote.tunnels.TunnelStore tunnelStore;
    private dev.jasper.remote.ui.tunnels.TunnelsUi tunnelsUi;
    private dev.jasper.remote.ui.sftp.DirectoryFollower follower;
    private dev.jasper.remote.edit.EditChannels editChannels;
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
    private Timer poll;
    private Icon icon;
    private Icon serverIcon;
    private boolean stopped;
    private final Set<CompletableFuture<HostKeyVerifier.Decision>> questions = new java.util.HashSet<>();
    private final Map<UUID, HostsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
    private final Map<UUID, UUID> panes = new HashMap<>();
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
        Optional<java.util.function.BiFunction<WindowHandle, UUID, CompletableFuture<Optional<dev.jasper.vault.api.Credential>>>> credentialSource = Optional.empty();
        if (vault.isPresent()) credentialSource = Optional.of((owner, id) -> vault.get().credential(owner, id));
        connections = new Connections(settings::connection, trust, agentFactory.apply(context), store::host,
            credentialSource, this::askHostKey, context.background(), ui, schedule);
        connections.onChanged(this::refreshStatus);
        icon = context.appearance().icon(dev.jasper.sdk.ui.IconName.NETWORK);
        serverIcon = context.appearance().icon(dev.jasper.sdk.ui.IconName.SERVER);
        // Before the panels: each names its toggle action, which must already be registered.
        registerNavigation();

        Executor transferBackground=transferExecutor.apply(context);
        var endpoints=new dev.jasper.remote.sftp.EndpointFactory(connections,context.background(),ui,()->settings.sftp().requestTimeout());
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
        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->sftpUi.reveal(window));
        sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(context,ui,transferBackground,connections,endpoints,()->transfers,store::hosts,this::sftpPane,transferUi::strip,transferUi::release);
        tunnelStore = new dev.jasper.remote.tunnels.TunnelStore(context.dataDirectory().resolve("tunnels.toml"), context.background(), ui);
        tunnelsUi = new dev.jasper.remote.ui.tunnels.TunnelsUi(context, ui, tunnelStore,
            new dev.jasper.remote.tunnels.TunnelManager(connections, () -> settings.connection().connectTimeout(), context.background(), ui),
            store::hosts, store::host, paneId -> Optional.ofNullable(panes.get(paneId)).flatMap(store::host));
        remoteHosts=new dev.jasper.remote.chooser.RemoteHosts(store::hosts,connections::connected,store::onChanged,connections::onChanged,
            paneId->context.terminals().pane(paneId).flatMap(this::sftpPane).map(target->new dev.jasper.remote.chooser.RemoteHosts.Located(target.identity(),target.directory())),
            connections::resolveIdentity,(identity,window)->endpoints.open(Optional.of(identity),window,status->{}),()->context.terminals().activeWindow());
        context.fileSources().register(remoteHosts);
        remoteOpener = (file, window) -> context.notifications().group().error("Could not open a remote file",
            "Install the Editor plugin to open " + file.path() + ".");
        try {
            var editor=context.services().find(dev.jasper.editor.api.Editor.class);
            if(editor.isPresent()) {
                editChannels=new dev.jasper.remote.edit.EditChannels((identity,owner)->endpoints.open(Optional.of(identity),owner.orElse(null),status->{}),connections::authenticated,transferBackground,ui);
                var editing=new dev.jasper.remote.edit.RemoteEditing(editor.orElseThrow(),editChannels,()->context.terminals().activeWindow(),context.notifications().group(),
                    id->store.hosts().stream().filter(host->host.id().equals(id)).findFirst());
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
        context.actions().register(ActionSpec.of(SFTP,"Open SFTP here").withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp","files","browse","remote"))
                .withDefaultBinding("cmd+shift+o").withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()),invoked->{
            invoked.pane().flatMap(this::sftpPane).ifPresentOrElse(pane->sftpUi.show(invoked.window(),pane),()->sftpUi.show(invoked.window()));
        });
        context.menus().terminalContext().add(SFTP);
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split"))
            .withContext(dev.jasper.sdk.ui.ShortcutContext.terminal()), invoked -> invoked.pane().ifPresent(this::splitSameHost));
        splitAction.setEnabled(false);
        context.actions().register(ActionSpec.of(IMPORT, "Import from ~/.ssh/config...").withKeywords(List.of("ssh", "import", "config")), invoked -> importConfig(invoked.window()));
        PluginMenu menu = context.menus().create(MENU, "SSH");
        menu.add(CONNECT); menu.add(HOSTS); menu.add(SPLIT); menu.addSeparator(); menu.add(IMPORT);
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
        tunnelsUi.addToMenu(menu);
        // View > Tools lists HOSTS, SFTP_TOGGLE and the Tunnels toggle: the panels name them as their toggles.

        distros = wslProfiles();
        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)),
            () -> distros, this::openDistro, this::splitDistro);
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 60));
        status.setIcon(icon); status.setAction(HOSTS); status.setVisible(false);
        context.panels().registerView(new PanelSpec(PANEL, "Sessions", icon, Anchor.LEFT).withToggleActionId(HOSTS), this::createPanel);
        sessionsToolbar = new SessionsToolbar(context, store, icon, this::activateHost, window -> {
            PanelHost panel = panelHosts.get(window.id());
            if (panel == null) showPanel(window); else panel.show();
        }, () -> distros, this::openDistro);

        store.onChanged(() -> { if (stopped) return; scope.changed(); refreshPanels(); sessionsToolbar.refresh(); tunnelsUi.hostsChanged(); store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "hosts.toml: {0}", warning)); });
        store.load();
        tunnelStore.load();
        poll = new Timer(1000, event -> { store.poll(); tunnelStore.poll(); });
        poll.start();
        context.terminals().onProfilesChanged(() -> {
            if (stopped) return;
            distros = wslProfiles();
            scope.changed(); sessionsToolbar.refresh(); refreshPanels();
        });
        context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, event -> {
            event.paneId().ifPresent(id->{rememberPane(id);sftpFocused(id);}); refreshSplit();
        });
        context.events().subscribe(TerminalEvents.PANE_FOCUSED, event -> { rememberPane(event.paneId());sftpFocused(event.paneId()); });
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> { attachedPanes.remove(event.paneId()); panes.remove(event.paneId()); paneIdentities.remove(event.paneId());follower.forget(event.paneId());sftpUi.ended(event.paneId()); recentPanes.remove(event.paneId()); distroPanes.remove(event.paneId()); refreshPanels(); });
        context.events().subscribe(TerminalEvents.SESSION_STATE_CHANGED, event -> { if (distroPanes.containsKey(event.paneId())) refreshPanels(); });
        context.events().subscribe(TerminalEvents.WINDOW_CLOSED, event -> {
            for (var attempt : Set.copyOf(attempts)) if (attempt.window.id().equals(event.windowId())) attempt.close();
        });
        if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refreshPanels());
        context.events().subscribe(TerminalEvents.CWD_CHANGED,event->event.remoteDirectory().ifPresent(directory->{follower.reported(event.paneId());sftpUi.directory(event.paneId(),directory.path());}));
        context.events().subscribe(TerminalEvents.TAB_SELECTED,event->context.terminals().window(event.windowId()).flatMap(window->window.activeTab()).flatMap(tab->tab.activePane()).ifPresent(pane->sftpFocused(pane.id())));
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
                .withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp", "files", "panel", "show", "hide"))
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

    private Optional<dev.jasper.remote.ui.sftp.SftpUi.PaneTarget> sftpPane(WindowHandle window) { return window.activeTab().flatMap(tab->tab.activePane()).flatMap(this::sftpPane); }
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

    private void connect(PendingSession pending, UUID hostId, Optional<String> folder) {
        var host = store.host(hostId);
        if (host.isEmpty()) { pending.fail("Host not found"); return; }
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (focusExistingAttempt(window)) { pending.fail("Another connection is already in progress"); return; }
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host.get(), folder, shell -> attach(pending, shell)); }
        catch (RuntimeException failure) { pending.fail(message(failure)); return; }
        attempt.cancelled = () -> { if (!stopped) pending.fail("Cancelled"); };
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

    void activateHost(WindowHandle window, RemoteHost host) {
        for (UUID id : recentPanes.sequencedKeySet().reversed()) {
            if (!host.id().equals(panes.get(id))) continue;
            var pane = context.terminals().pane(id);
            if (pane.isPresent() && pane.get().info().state() == dev.jasper.sdk.terminal.SessionState.RUNNING) { pane.get().focus(); return; }
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
            });
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

    private void attach(PendingSession pending, Connections.Shell shell) {
        if (stopped || pending.isCancelled()) { shell.connection().close().run(); return; }
        UUID paneId = pending.pane().id();
        attachedPanes.put(paneId, pending);
        follower.forget(paneId);
        panes.put(paneId, shell.hostId()); paneIdentities.put(paneId, shell.identity()); rememberPane(paneId);sftpFocused(paneId);
        shell.connection().exited().whenComplete((ignored, exit) -> ui.execute(() -> {
            if (attachedPanes.get(paneId) != pending) return;
            attachedPanes.remove(paneId);
            panes.remove(paneId); paneIdentities.remove(paneId); follower.forget(paneId); recentPanes.remove(paneId); refreshSplit();
            if (!stopped) { sftpUi.ended(paneId); refreshPanels(); }
        }));
        pending.attach(shell.connection());
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
            var dimensions = window.activeTab().flatMap(tab -> tab.activePane()).map(PaneHandle::info);
            int cols = dimensions.map(info -> Math.max(1, info.columns())).orElse(80);
            int rows = dimensions.map(info -> Math.max(1, info.rows())).orElse(24);
            // Followed panes get their own connection so the probe can tell their shell apart (see the directory probe spec).
            boolean dedicated = host.followDirectory() && !"Windows".equals(hostInfo.get(host).os());
            future = connections.shell(host.id(), window, cols, rows, panel::status, dedicated, folder);
            future.whenComplete((shell, failure) -> ui.execute(() -> {
                if (closed || stopped || !window.isOpen()) { if (shell != null) shell.connection().close().run(); close(); return; }
                if (failure != null) { panel.failed(message(failure)); return; }
                try { success.accept(shell); delivered = true; close(); }
                catch (RuntimeException problem) { shell.connection().close().run(); panel.failed(message(problem)); }
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
        editor[0] = new HostEditor(context.kit(), ui, store.hosts(), editing, vault.isPresent(), this::credentialName,
            () -> vault.isPresent() ? vault.get().pick(window) : CompletableFuture.completedFuture(Optional.empty()),
            host -> store.put(host).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (stopped) return;
                if (failure == null) dialog.close();
                else {
                    if (!closed[0]) editor[0].showError(message(failure));
                    context.notifications().group().error("Could not save the host", message(failure), failure);
                }
            })),
            dialog::close);
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
        var panel = new HostsPanel(context.kit(), new HostsPanel.Actions(h -> openHost(window, h), h -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitHost(pane, h)),
            editing -> editHost(window, editing), h -> reportMutation("Could not duplicate the host", store.put(duplicate(h))), h -> deleteHost(window, h),
            (h, favorite) -> reportMutation("Could not update favorites", store.put(h.withFavorite(favorite))), () -> importConfig(window)), context.appearance()::icon);
        panel.onHeaderChanged(host::setHeader);
        panel.setCredentialLabels(this::credentialLabel);
        panel.setDefaultGroup(panelState.defaultGroup());
        panel.onRenameDefaultGroup(() -> renameDefaultGroup(window));
        panel.onActivate(h -> activateHost(window, h));
        panel.onBrowseFiles(h->sftpUi.browse(window,h));
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.onDistros(profile -> openDistro(window, profile),
            profile -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitDistro(pane, profile)));
        panel.setDistros(distros, this::distroSessions);
        panel.setCollapsed(panelState.collapsed());
        panel.setWslCollapsed(panelState.wslCollapsed());
        panel.onCollapsedChanged(() -> { try { panelState.save(panel.collapsed(), panel.wslCollapsed()); } catch (IOException failure) { context.log().log(System.Logger.Level.WARNING, "panel state: {0}", failure.getMessage()); } });
        panel.setHosts(store.hosts(), store.error());
        panels.put(window.id(), panel); panelHosts.put(window.id(), host);
        // Shown again, the first host is selected as when the old list was shown; focus follows an explicit opening only
        // (a restored panel's window is not showing yet, so the kit's focus request does nothing).
        host.onVisibility(visible -> { if (visible) { panel.shown(); panel.focusWhenOpened(); } });
        // The host disposed the view; the panel goes inert so no later refresh touches it.
        host.onClosed(() -> { panel.close(); if (panels.get(window.id()) == panel) { panels.remove(window.id()); panelHosts.remove(window.id()); } });
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
        for (HostsPanel panel : panels.values()) {
            panel.setCredentialLabels(this::credentialLabel);
            panel.setDefaultGroup(panelState.defaultGroup());
            panel.setHostDetails(this::hostDetails, this::sessionCount);
            panel.setDistros(distros, this::distroSessions);
            panel.setHosts(store.hosts(), store.error());
        }
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
                for (var panel : panels.values()) panel.setCollapsed(panelState.collapsed());
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

    private Optional<String> credentialName(UUID id) {
        if (vault.isEmpty()) return Optional.empty();
        return vault.get().credentials().stream().filter(descriptor -> descriptor.id().equals(id)).findFirst().map(CredentialDescriptor::name);
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
    /** The Sessions panel open in a window, for tests. */
    HostsPanel hostsPanel(UUID windowId) { return panels.get(windowId); }
    dev.jasper.remote.ui.tunnels.TunnelsUi tunnels() { return tunnelsUi; }
    dev.jasper.remote.tunnels.TunnelStore tunnelStore() { return tunnelStore; }
}
