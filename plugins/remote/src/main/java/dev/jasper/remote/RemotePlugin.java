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
import dev.jasper.sdk.terminal.SessionSpec;
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
import dev.jasper.sdk.ui.StandardMenu;
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
 * provider, the panel, the scope, the actions, the SSH menu, the status item and the dialogs.
 */
public class RemotePlugin implements Plugin {
    public static final String CONNECT = "dev.jasper.remote.connect", HOSTS = "dev.jasper.remote.hosts", SPLIT = "dev.jasper.remote.split";
    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", MENU = "dev.jasper.remote.menu", PANEL = "dev.jasper.remote.panel";

    private final Executor ui;
    private final Function<PluginContext,Executor> transferExecutor;
    private volatile RemoteSettings.Sftp sftpSettings;
    private dev.jasper.remote.transfer.TransferCoordinator transfers;
    private dev.jasper.remote.ui.transfers.TransferUi transferUi;
    private dev.jasper.remote.ui.sftp.SftpUi sftpUi;
    private dev.jasper.remote.ui.sftp.DirectoryFollower follower;
    private PluginAction sftpToggle;
    public static final String SFTP="dev.jasper.remote.sftp", SFTP_TOGGLE=SFTP+".toggle";
    private final Function<PluginContext, Optional<AgentClient>> agentFactory;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Path sshDir;
    private PluginContext context;
    private RemoteSettings settings;
    private HostStore store;
    private PanelState panelState;
    private KnownHosts trust;
    private Connections connections;
    private Optional<VaultApi> vault = Optional.empty();
    private RemoteScope scope;
    private StatusItem status;
    private PluginAction splitAction;
    private PluginAction connectAction, hostsAction;
    private RemoteShortcuts shortcuts;
    private SessionsToolbar sessionsToolbar;
    private Timer poll;
    private Icon icon;
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
        settings = RemoteSettings.read(context.config());
        store = new HostStore(context.dataDirectory().resolve("hosts.toml"), context.background(), ui);
        panelState = new PanelState(context.dataDirectory().resolve("panel-state.toml"));
        hostInfo = new HostInfoCache(context.dataDirectory().resolve("host-info.properties"));
        context.background().execute(() -> { hostInfo.load(); ui.execute(() -> { if (!stopped) refreshPanels(); }); });
        trust = new KnownHosts(context.dataDirectory().resolve("known_hosts"), () -> settings.readUserKnownHosts() ? Optional.of(sshDir.resolve("known_hosts")) : Optional.empty());
        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
        Optional<java.util.function.BiFunction<WindowHandle, UUID, CompletableFuture<Optional<dev.jasper.vault.api.Credential>>>> credentialSource = Optional.empty();
        if (vault.isPresent()) credentialSource = Optional.of((owner, id) -> vault.get().credential(owner, id));
        connections = new Connections(() -> settings, trust, agentFactory.apply(context), store::host,
            credentialSource, this::askHostKey, context.background(), ui, schedule);
        connections.onChanged(this::refreshStatus);
        icon = context.appearance().icon(dev.jasper.sdk.ui.IconName.NETWORK);

        sftpSettings=RemoteSettings.sftp(context.config());
        Executor transferBackground=transferExecutor.apply(context);
        var endpoints=new dev.jasper.remote.sftp.EndpointFactory(connections,context.background(),ui,()->sftpSettings.requestTimeout());
        transfers=new dev.jasper.remote.transfer.TransferCoordinator(context.dataDirectory().resolve("transfers"),transferBackground,ui,(ref,owner)-> {
            try { return endpoints.open(ref.identity(),owner,status->{}); }catch(IOException failure) { return CompletableFuture.failedFuture(failure); }
        },()->sftpSettings.maxParallelFiles());
        transfers.setResumeValidator((ref,owner)-> {
            if(ref.hostId().isEmpty()) return CompletableFuture.completedFuture(null);
            try {
                var expected=ref.identity().orElseThrow();
                return validateResumeIdentity(expected,connections.resolveIdentity(ref.hostId().orElseThrow(),owner));
            }catch(Exception failure) { return CompletableFuture.failedFuture(failure); }
        });
        transferUi=new dev.jasper.remote.ui.transfers.TransferUi(context,transfers,ui,window->sftpUi.reveal(window));
        sftpUi=new dev.jasper.remote.ui.sftp.SftpUi(context,ui,transferBackground,connections,endpoints,()->transfers,store::hosts,this::sftpPane,transferUi::strip,transferUi::release);
        follower=new dev.jasper.remote.ui.sftp.DirectoryFollower(ui,schedule,id->sftpUi.following(id),sftpUi::directory,sftpUi::notice);
        sftpUi.onFollowRequested(follower::request);
        context.actions().register(ActionSpec.of(SFTP,"Open SFTP here").withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER)).withKeywords(List.of("sftp","files","browse","remote")),invoked->{
            invoked.pane().flatMap(this::sftpPane).ifPresentOrElse(pane->sftpUi.show(invoked.window(),pane),()->sftpUi.show(invoked.window()));
        });
        context.menus().terminalContext().add(SFTP);
        configureShortcuts();
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split")), invoked -> invoked.pane().ifPresent(this::splitSameHost));
        splitAction.setEnabled(false);
        context.actions().register(ActionSpec.of(IMPORT, "Import from ~/.ssh/config...").withKeywords(List.of("ssh", "import", "config")), invoked -> importConfig(invoked.window()));
        PluginMenu menu = context.menus().create(MENU, "SSH");
        menu.add(CONNECT); menu.add(HOSTS); menu.add(SPLIT); menu.addSeparator(); menu.add(IMPORT);
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
        context.menus().standard(StandardMenu.VIEW).add(SFTP_TOGGLE);
        context.menus().standard(StandardMenu.VIEW).add(HOSTS);

        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)));
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 60));
        status.setIcon(icon); status.setAction(HOSTS); status.setVisible(false);
        context.panels().register(new PanelSpec(PANEL, "SSH hosts", icon, Anchor.LEFT), this::createPanel);
        sessionsToolbar = new SessionsToolbar(context, store, icon, this::activateHost, window -> {
            PanelHost panel = panelHosts.get(window.id());
            if (panel == null) showPanel(window); else panel.show();
        });

        store.onChanged(() -> { if (stopped) return; scope.changed(); refreshPanels(); sessionsToolbar.refresh(); store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "hosts.toml: {0}", warning)); });
        store.load();
        poll = new Timer(1000, event -> store.poll());
        poll.start();
        context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, event -> {
            event.paneId().ifPresent(id->{rememberPane(id);sftpFocused(id);}); refreshSplit();
        });
        context.events().subscribe(TerminalEvents.PANE_FOCUSED, event -> { rememberPane(event.paneId());sftpFocused(event.paneId()); });
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> { panes.remove(event.paneId()); paneIdentities.remove(event.paneId());follower.forget(event.paneId());sftpUi.ended(event.paneId()); recentPanes.remove(event.paneId()); refreshPanels(); });
        context.events().subscribe(TerminalEvents.WINDOW_CLOSED, event -> {
            for (var attempt : Set.copyOf(attempts)) if (attempt.window.id().equals(event.windowId())) attempt.close();
        });
        if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refreshPanels());
        context.events().subscribe(TerminalEvents.CWD_CHANGED,event->event.remoteDirectory().ifPresent(directory->{follower.reported(event.paneId());sftpUi.directory(event.paneId(),directory.path());}));
        context.events().subscribe(TerminalEvents.TAB_SELECTED,event->context.terminals().window(event.windowId()).flatMap(window->window.activeTab()).flatMap(tab->tab.activePane()).ifPresent(pane->sftpFocused(pane.id())));
        context.config().onChanged(() -> {
            if (stopped) return;
            settings = RemoteSettings.read(context.config());sftpSettings=RemoteSettings.sftp(context.config());
            configureShortcuts();
        });
    }

    private void configureShortcuts() {
        RemoteShortcuts next = RemoteShortcuts.read(context.config());
        // Binding preferences are immutable in the SDK. Replace only changed actions under their
        // stable ids; the host refreshes shortcuts and existing placements in every open window.
        if (shortcuts == null || !next.openPalette().equals(shortcuts.openPalette())) {
            if (connectAction != null) connectAction.close();
            connectAction = context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon)
                .withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBinding(next.openPalette().orElse(null)),
                invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        }
        if (shortcuts == null || !next.togglePanel().equals(shortcuts.togglePanel())) {
            if (hostsAction != null) hostsAction.close();
            hostsAction = context.actions().register(ActionSpec.of(HOSTS, "SSH Hosts").withIcon(icon)
                .withKeywords(List.of("ssh", "hosts", "panel")).withDefaultBinding(next.togglePanel().orElse(null)),
                invoked -> showPanel(invoked.window()));
        }
        if(shortcuts==null || !next.toggleSftp().equals(shortcuts.toggleSftp())) {
            if(sftpToggle!=null)sftpToggle.close();
            sftpToggle=context.actions().register(ActionSpec.of(SFTP_TOGGLE,"SFTP panel").withDefaultBinding(next.toggleSftp().orElse(null)),invoked->sftpUi.toggle(invoked.window()));
        }
        shortcuts = next;
    }

    @Override public void stop() {
        stopped = true;
        if(follower!=null)follower.close();if(sftpUi!=null)sftpUi.close();if(transferUi!=null)transferUi.close();if(transfers!=null)transfers.close();
        if (configImport != null) configImport.close();
        for (var attempt : Set.copyOf(attempts)) attempt.close();
        for (var question : Set.copyOf(questions)) question.cancel(true);
        if (poll != null) poll.stop();
        if (sessionsToolbar != null) sessionsToolbar.close();
        if (connections != null) connections.close();
    }

    private Optional<dev.jasper.remote.ui.sftp.SftpUi.PaneTarget> sftpPane(WindowHandle window) { return window.activeTab().flatMap(tab->tab.activePane()).flatMap(this::sftpPane); }
    private Optional<dev.jasper.remote.ui.sftp.SftpUi.PaneTarget> sftpPane(PaneHandle pane) {
        var identity=paneIdentities.get(pane.id());if(identity==null)return Optional.empty();
        return Optional.of(new dev.jasper.remote.ui.sftp.SftpUi.PaneTarget(pane.id(),identity,pane.info().remoteDirectory().map(dev.jasper.sdk.terminal.RemoteDirectory::path).orElse("")));
    }
    private void sftpFocused(UUID id) { context.terminals().pane(id).ifPresent(pane->sftpPane(pane).ifPresent(target->sftpUi.pane(pane.tab().window(),target))); }
    /** Linux and macOS panes on a dedicated connection report their folder to SFTP follow; Windows hosts are not followed. */
    private void followFolder(UUID paneId, Connections.Shell shell, dev.jasper.remote.hosts.HostInfo info) {
        if (shell.folder().isEmpty() || "Windows".equals(info.os())) return;
        var folder = shell.folder().orElseThrow();
        follower.track(paneId, () -> CompletableFuture.supplyAsync(() -> {
            try { return folder.read(); } catch (IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, context.background()));
        folder.onEnter(() -> ui.execute(() -> follower.enter(paneId)));
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

    private void connect(PendingSession pending, UUID hostId) {
        var host = store.host(hostId);
        if (host.isEmpty()) { pending.fail("Host not found"); return; }
        if (pending.isCancelled() || stopped) return;
        WindowHandle window = pending.pane().tab().window();
        if (focusExistingAttempt(window)) { pending.fail("Another connection is already in progress"); return; }
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host.get(), shell -> attach(pending, shell)); }
        catch (RuntimeException failure) { pending.fail(message(failure)); return; }
        attempt.cancelled = () -> { if (!stopped) pending.fail("Cancelled"); };
        var cancellation = pending.onCancelled(() -> ui.execute(attempt::close));
        attempt.finished = cancellation::close;
        attempts.add(attempt); attempt.start();
    }

    void openHost(WindowHandle window, RemoteHost host) {
        prepare(window, host, request -> context.terminals().openTab(window, request));
    }
    void splitHost(PaneHandle pane, RemoteHost host) {
        prepare(pane.tab().window(), host, request -> context.terminals().split(pane, Direction.RIGHT, request));
    }

    void activateHost(WindowHandle window, RemoteHost host) {
        for (UUID id : recentPanes.sequencedKeySet().reversed()) {
            if (!host.id().equals(panes.get(id))) continue;
            var pane = context.terminals().pane(id);
            if (pane.isPresent() && pane.get().info().state() == dev.jasper.sdk.terminal.SessionState.RUNNING) { pane.get().focus(); return; }
        }
        openHost(window, host);
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

    private void prepare(WindowHandle window, RemoteHost host, Function<OpenRequest, Optional<PaneHandle>> open) {
        if (!window.isOpen() || stopped || focusExistingAttempt(window)) return;
        final ConnectAttempt attempt;
        try { attempt = new ConnectAttempt(window, host, shell -> {
            var ready = new java.util.concurrent.atomic.AtomicReference<>(shell);
            SessionSpec spec = SessionSpec.of(host.name(), pending -> {
                Connections.Shell first = ready.getAndSet(null);
                if (first == null) connect(pending, host.id());
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
        }); } catch (RuntimeException failure) { context.notices().error(message(failure)); return; }
        attempts.add(attempt); attempt.start();
    }

    private void attach(PendingSession pending, Connections.Shell shell) {
        if (stopped || pending.isCancelled()) { shell.connection().close().run(); return; }
        UUID paneId = pending.pane().id();
        panes.put(paneId, shell.hostId()); paneIdentities.put(paneId, shell.identity()); rememberPane(paneId);sftpFocused(paneId);
        shell.connection().exited().whenComplete((ignored, exit) -> ui.execute(() -> {
            panes.remove(paneId); paneIdentities.remove(paneId); follower.forget(paneId); recentPanes.remove(paneId); refreshSplit();
            if (!stopped) { sftpUi.ended(paneId); refreshPanels(); }
        }));
        pending.attach(shell.connection());
        RemoteHost authenticatedHost = shell.host();
        connections.inspect(shell).thenAccept(info -> {
            if (stopped) return;
            ui.execute(() -> { if (!stopped && panes.containsKey(paneId)) followFolder(paneId, shell, info); });
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
        final java.util.function.Consumer<Connections.Shell> success;
        final PluginDialog dialog;
        final ConnectionPanel panel;
        CompletableFuture<Connections.Shell> future;
        boolean closed, delivered;
        Runnable cancelled = () -> {}, finished = () -> {};
        ConnectAttempt(WindowHandle window, RemoteHost host, java.util.function.Consumer<Connections.Shell> success) {
            this.window = window; this.host = host; this.success = success;
            dialog = context.windows().overlay(new OverlaySpec("Connecting to " + host.name(), window));
            panel = new ConnectionPanel(host.name(), host.label(), this::start, this::close);
            currentConnectionPanel = panel;
            dialog.setContent(panel); dialog.onClosed(this::close);
        }
        void start() {
            if (closed || stopped || !window.isOpen()) { close(); return; }
            if (future != null && !future.isDone()) return;
            panel.working();
            try { dialog.show(); }
            catch (RuntimeException failure) { context.notices().error(message(failure)); close(); return; }
            var dimensions = window.activeTab().flatMap(tab -> tab.activePane()).map(PaneHandle::info);
            int cols = dimensions.map(info -> Math.max(1, info.columns())).orElse(80);
            int rows = dimensions.map(info -> Math.max(1, info.rows())).orElse(24);
            // Followed panes get their own connection so the probe can tell their shell apart (see the directory probe spec).
            boolean dedicated = host.followDirectory() && !"Windows".equals(hostInfo.get(host).os());
            future = connections.shell(host.id(), window, cols, rows, panel::status, dedicated);
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
            panel.finished(); finished.run();
            if (!delivered) cancelled.run();
            dialog.close();
            if (currentConnectionPanel == panel) currentConnectionPanel = null;
        }
    }

    private void splitSameHost(PaneHandle pane) {
        UUID hostId = panes.get(pane.id());
        Optional<RemoteHost> host = hostId == null ? Optional.empty() : store.host(hostId);
        if (host.isEmpty()) { context.notices().error("Split with Same Host needs a connected SSH pane"); return; }
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
            currentHostKeyPanel = new HostKeyPanel(question, answer -> { decision.complete(answer); dialog.close(); });
            dialog.setContent(currentHostKeyPanel);
            decision.whenComplete((answer, failure) -> ui.execute(() -> { questions.remove(decision); dialog.close(); }));
            dialog.onClosed(() -> { currentHostKeyPanel = null; decision.complete(HostKeyVerifier.Decision.CANCEL); });
            dialog.show();
        });
        return decision;
    }

    private Optional<WindowHandle> owner() { return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst()); }

    void editHost(WindowHandle window, Optional<RemoteHost> editing) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(editing.isPresent() ? "Edit " + editing.get().name() : "Add Host", window, true));
        HostEditor[] editor = new HostEditor[1];
        boolean[] closed = {false};
        editor[0] = new HostEditor(store.hosts(), editing, vault.isPresent(), this::credentialName,
            () -> vault.isPresent() ? vault.get().pick(window) : CompletableFuture.completedFuture(Optional.empty()),
            host -> store.put(host).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (stopped) return;
                if (failure == null) dialog.close();
                else { if (!closed[0]) editor[0].showError(message(failure)); context.notices().error(message(failure)); }
            })),
            dialog::close);
        editor[0].setDefaultGroupName(panelState.defaultGroup());
        currentEditor = editor[0];
        dialog.setContent(editor[0]);
        dialog.onClosed(() -> { closed[0] = true; if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    private void deleteHost(WindowHandle window, RemoteHost host) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec("Delete " + host.name() + "?", window, true));
        currentConfirm = new ConfirmPanel("Delete the saved host " + host.name() + "? Open sessions stay connected.", "Delete",
            () -> store.remove(host.id()).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null) context.notices().error(message(failure)); dialog.close(); })), dialog::close);
        dialog.setContent(currentConfirm);
        dialog.onClosed(() -> currentConfirm = null);
        dialog.show();
    }

    private void importConfig(WindowHandle window) {
        if (configImport != null) configImport.close();
        configImport = new ConfigImportController(context, vault, store, sshDir, ui);
        configImport.show(window);
    }

    // ---- panel and status

    private javax.swing.JComponent createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new HostsPanel(new HostsPanel.Actions(h -> openHost(window, h), h -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitHost(pane, h)),
            editing -> editHost(window, editing), h -> reportMutation(store.put(duplicate(h))), h -> deleteHost(window, h),
            (h, favorite) -> reportMutation(store.put(h.withFavorite(favorite))), () -> importConfig(window)));
        panel.setCredentialLabels(this::credentialLabel);
        panel.setDefaultGroup(panelState.defaultGroup());
        panel.onRenameDefaultGroup(() -> renameDefaultGroup(window));
        panel.onActivate(h -> activateHost(window, h));
        panel.onBrowseFiles(h->sftpUi.browse(window,h));
        panel.setHostDetails(this::hostDetails, this::sessionCount);
        panel.setCollapsed(panelState.collapsed());
        panel.onCollapsedChanged(() -> { try { panelState.save(panel.collapsed()); } catch (IOException failure) { context.log().log(System.Logger.Level.WARNING, "panel state: {0}", failure.getMessage()); } });
        panel.setHosts(store.hosts(), store.error());
        panels.put(window.id(), panel); panelHosts.put(window.id(), host);
        host.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        return panel;
    }

    private void reportMutation(CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> {
            if (!stopped && failure != null) context.notices().error(message(failure));
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
        editor[0] = new GroupNamePanel(panelState.defaultGroup(), name -> {
            try {
                String value = name.strip();
                if (store.hosts().stream().anyMatch(host -> !host.group().isEmpty() && host.group().equalsIgnoreCase(value)))
                    throw new IllegalArgumentException("A group with that name already exists");
                panelState.renameDefaultGroup(value);
                for (var panel : panels.values()) panel.setCollapsed(panelState.collapsed());
                refreshPanels(); dialog.close();
            } catch (IOException | IllegalArgumentException failure) { editor[0].showError(message(failure)); }
        }, dialog::close);
        currentGroupEditor = editor[0]; dialog.setContent(editor[0]);
        dialog.onClosed(() -> { if (currentGroupEditor == editor[0]) currentGroupEditor = null; }); dialog.show();
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
}
