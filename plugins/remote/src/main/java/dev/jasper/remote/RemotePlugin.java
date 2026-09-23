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
 * provider, the panel, the scope, the actions, the SSH menu, the status item and the dialogs.
 */
public class RemotePlugin implements Plugin {
    public static final String CONNECT = "dev.jasper.remote.connect", HOSTS = "dev.jasper.remote.hosts", SPLIT = "dev.jasper.remote.split";
    public static final String IMPORT = "dev.jasper.remote.import", STATUS = "dev.jasper.remote.status", MENU = "dev.jasper.remote.menu", PANEL = "dev.jasper.remote.panel";

    private final Executor ui;
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
    private Timer poll;
    private Icon icon;
    private boolean stopped;
    private final Set<CompletableFuture<HostKeyVerifier.Decision>> questions = new java.util.HashSet<>();
    private final Map<UUID, HostsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
    private final Map<UUID, UUID> panes = new HashMap<>();
    private HostKeyPanel currentHostKeyPanel;
    private HostEditor currentEditor;
    private ImportPanel currentImport;
    private ConfirmPanel currentConfirm;

    public RemotePlugin() {
        this(SwingUtilities::invokeLater, context -> AgentClient.forEnvironment(System.getenv(), System.getProperty("os.name", "")),
            (delay, task) -> { var timer = new Timer((int) Math.max(1, delay.toMillis()), event -> task.run()); timer.setRepeats(false); timer.start(); return timer::stop; },
            Path.of(System.getProperty("user.home"), ".ssh"));
    }

    RemotePlugin(Executor ui, Function<PluginContext, Optional<AgentClient>> agentFactory, BiFunction<Duration, Runnable, Runnable> schedule, Path sshDir) {
        this.ui = ui; this.agentFactory = agentFactory; this.schedule = schedule; this.sshDir = sshDir;
    }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = RemoteSettings.read(context.config());
        context.config().onChanged(() -> settings = RemoteSettings.read(context.config()));
        store = new HostStore(context.dataDirectory().resolve("hosts.toml"), context.background(), ui);
        panelState = new PanelState(context.dataDirectory().resolve("panel-state.toml"));
        trust = new KnownHosts(context.dataDirectory().resolve("known_hosts"), () -> settings.readUserKnownHosts() ? Optional.of(sshDir.resolve("known_hosts")) : Optional.empty());
        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
        connections = new Connections(() -> settings, trust, agentFactory.apply(context), store::host,
            vault.map(api -> id -> api.credential(id)), this::askHostKey, context.background(), ui, schedule);
        connections.onChanged(this::refreshStatus);
        icon = context.appearance().icon("dev/jasper/remote/server.svg");

        context.actions().register(ActionSpec.of(CONNECT, "Connect to SSH Host...").withIcon(icon).withKeywords(List.of("ssh", "remote", "host", "connect")).withDefaultBinding("cmd+shift+h"),
            invoked -> context.palette().open(invoked.window(), RemoteScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(HOSTS, "SSH Hosts").withIcon(icon).withKeywords(List.of("ssh", "hosts", "panel")), invoked -> showPanel(invoked.window()));
        splitAction = context.actions().register(ActionSpec.of(SPLIT, "Split with Same Host").withKeywords(List.of("ssh", "split")), invoked -> invoked.pane().ifPresent(this::splitSameHost));
        splitAction.setEnabled(false);
        context.actions().register(ActionSpec.of(IMPORT, "Import from ~/.ssh/config...").withKeywords(List.of("ssh", "import", "config")), invoked -> importConfig(invoked.window()));
        PluginMenu menu = context.menus().create(MENU, "SSH");
        menu.add(CONNECT); menu.add(HOSTS); menu.add(SPLIT); menu.addSeparator(); menu.add(IMPORT);

        scope = new RemoteScope(store::hosts, store::error, this::openHost, this::splitHost, (window, host) -> editHost(window, Optional.of(host)));
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 60));
        status.setIcon(icon); status.setAction(HOSTS); status.setVisible(false);
        context.panels().register(new PanelSpec(PANEL, "SSH hosts", icon, Anchor.LEFT), this::createPanel);

        store.onChanged(() -> { if (stopped) return; scope.changed(); refreshPanels(); store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "hosts.toml: {0}", warning)); });
        store.load();
        poll = new Timer(1000, event -> store.poll());
        poll.start();
        context.events().subscribe(TerminalEvents.ACTIVE_PANE_CHANGED, event -> splitAction.setEnabled(event.paneId().map(panes::containsKey).orElse(false)));
        context.events().subscribe(TerminalEvents.PANE_CLOSED, event -> panes.remove(event.paneId()));
        vault.ifPresent(api -> context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> refreshPanels()));
    }

    @Override public void stop() {
        stopped = true;
        for (var question : Set.copyOf(questions)) question.cancel(true);
        if (poll != null) poll.stop();
        if (connections != null) connections.close();
    }

    // ---- sessions

    SessionSpec session(RemoteHost host) { return SessionSpec.of(host.name(), pending -> connect(pending, host.id())); }

    private void connect(PendingSession pending, UUID hostId) {
        CompletableFuture<Connections.Shell> future = connections.shell(hostId, pending.columns(), pending.rows(), pending::status);
        var cancellation = pending.onCancelled(() -> ui.execute(() -> future.cancel(true)));
        future.whenComplete((shell, failure) -> ui.execute(() -> {
            cancellation.close();
            if (stopped) { if (shell != null) shell.connection().close().run(); return; }
            if (failure != null) { pending.fail(message(failure)); return; }
            UUID paneId = pending.pane().id();
            panes.put(paneId, hostId);
            shell.connection().exited().whenComplete((ignored, exit) -> ui.execute(() -> { panes.remove(paneId); refreshSplit(); }));
            pending.attach(shell.connection());
            refreshSplit();
        }));
    }

    void openHost(WindowHandle window, RemoteHost host) { context.terminals().openTab(window, OpenRequest.session(session(host))); }
    void splitHost(PaneHandle pane, RemoteHost host) { context.terminals().split(pane, Direction.RIGHT, OpenRequest.session(session(host))); }

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
        var decision = new CompletableFuture<HostKeyVerifier.Decision>();
        ui.execute(() -> {
            if (decision.isDone() || stopped) { decision.cancel(true); return; }
            questions.add(decision);
            Optional<WindowHandle> owner = owner();
            if (owner.isEmpty()) { decision.complete(HostKeyVerifier.Decision.CANCEL); return; }
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Verify host key " + question.host(), owner.get(), true));
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
        currentEditor = new HostEditor(store.hosts(), editing, vault.isPresent(), this::credentialName,
            () -> vault.map(api -> api.pick(window)).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())),
            host -> store.put(host).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure == null) dialog.close(); else currentEditor.showError(message(failure)); })),
            dialog::close);
        dialog.setContent(currentEditor);
        dialog.onClosed(() -> currentEditor = null);
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
        Path config = sshDir.resolve("config");
        Map<String, UUID> vaultKeys = new HashMap<>();
        vault.ifPresent(api -> { for (CredentialDescriptor descriptor : api.credentials()) if (descriptor.kind() == Kind.SSH_KEY) vaultKeys.put(descriptor.subtitle(), descriptor.id()); });
        Path home = Path.of(System.getProperty("user.home"));
        String user = System.getProperty("user.name", "");
        List<RemoteHost> existing = store.hosts();
        context.background().execute(() -> {
            SshConfig.Parsed parsed;
            try { parsed = SshConfig.parse(config); }
            catch (IOException unreadable) { ui.execute(() -> context.notices().error("Could not read " + config + ": " + unreadable.getMessage())); return; }
            List<ConfigImport.Candidate> plan = ConfigImport.plan(parsed, existing, vaultKeys, pub -> { try { return Files.isRegularFile(pub) ? Optional.of(Files.readString(pub)) : Optional.empty(); } catch (IOException e) { return Optional.empty(); } }, home, user);
            ui.execute(() -> {
                if (stopped || !window.isOpen()) return;
                PluginDialog dialog = context.windows().dialog(new DialogSpec("Import from ~/.ssh/config", window, true));
                currentImport = new ImportPanel(plan, parsed.skipped(), chosen -> {
                    var next = new java.util.ArrayList<>(store.hosts());
                    Set<String> names = new java.util.HashSet<>(next.stream().map(host -> host.name().toLowerCase(java.util.Locale.ROOT)).toList());
                    for (RemoteHost host : chosen) if (names.add(host.name().toLowerCase(java.util.Locale.ROOT))) next.add(host);
                    store.save(next).whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null) context.notices().error(message(failure)); dialog.close(); }));
                }, dialog::close);
                dialog.setContent(currentImport);
                dialog.onClosed(() -> currentImport = null);
                dialog.show();
            });
        });
    }

    // ---- panel and status

    private javax.swing.JComponent createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new HostsPanel(new HostsPanel.Actions(h -> openHost(window, h), h -> window.activeTab().flatMap(tab -> tab.activePane()).ifPresent(pane -> splitHost(pane, h)),
            editing -> editHost(window, editing), h -> store.put(duplicate(h)), h -> deleteHost(window, h),
            (h, favorite) -> store.put(h.withFavorite(favorite)), () -> importConfig(window)));
        panel.setCredentialLabels(this::credentialLabel);
        panel.setCollapsed(panelState.collapsed());
        panel.onCollapsedChanged(() -> { try { panelState.save(panel.collapsed()); } catch (IOException failure) { context.log().log(System.Logger.Level.WARNING, "panel state: {0}", failure.getMessage()); } });
        panel.setHosts(store.hosts(), store.error());
        panels.put(window.id(), panel); panelHosts.put(window.id(), host);
        host.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        return panel;
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
        PanelHost host = panelHosts.get(window.id());
        if (host != null) host.show();
        else context.palette().open(window, RemoteScope.ID, Optional.empty(), Optional.empty());
    }

    private void refreshPanels() { for (HostsPanel panel : panels.values()) { panel.setCredentialLabels(this::credentialLabel); panel.setHosts(store.hosts(), store.error()); } }

    private String credentialLabel(RemoteHost host) {
        return switch (host.auth()) {
            case Auth.Agent agent -> "SSH agent";
            case Auth.Vault credential -> vault.map(api -> api.lockState() != LockState.UNLOCKED ? "Vault locked" : credentialName(credential.credentialId()).orElse("credential missing")).orElse("needs Credential Vault");
        };
    }

    private Optional<String> credentialName(UUID id) {
        return vault.flatMap(api -> api.credentials().stream().filter(descriptor -> descriptor.id().equals(id)).findFirst()).map(CredentialDescriptor::name);
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
    ImportPanel currentImport() { return currentImport; }
    ConfirmPanel currentConfirm() { return currentConfirm; }
}
