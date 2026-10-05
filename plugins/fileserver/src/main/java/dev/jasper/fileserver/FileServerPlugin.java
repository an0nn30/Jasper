package dev.jasper.fileserver;

import dev.jasper.fileserver.auth.LoginResolver;
import dev.jasper.fileserver.auth.SessionPasswords;
import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.definition.DefinitionStore;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.LoginThrottle;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.sftp.HostKey;
import dev.jasper.fileserver.engine.tls.TlsMaterial;
import dev.jasper.fileserver.runtime.Engines;
import dev.jasper.fileserver.runtime.ServerRegistry;
import dev.jasper.fileserver.runtime.ServerState.Phase;
import dev.jasper.fileserver.runtime.TransferActivities;
import dev.jasper.fileserver.ui.ConfirmPanel;
import dev.jasper.fileserver.ui.FileServerScope;
import dev.jasper.fileserver.ui.FileServersPanel;
import dev.jasper.fileserver.ui.ServerEditor;
import dev.jasper.fileserver.ui.ServerRows;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import dev.jasper.vault.api.CredentialDescriptor;
import dev.jasper.vault.api.LockState;
import dev.jasper.vault.api.VaultApi;
import java.awt.Desktop;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The File Server plugin: shares local folders with other computers from the File Servers panel. */
public final class FileServerPlugin implements Plugin {
    public static final String ID = "dev.jasper.fileserver";
    public static final String PANEL = ID + ".panel";
    public static final String SHOW = ID + ".show-panel";
    public static final String NEW = ID + ".new";
    public static final String STOP_ALL = ID + ".stop-all";
    public static final String STATUS = ID + ".status";
    public static final String SERVERS = ID + ".servers";
    public static final String SERVE_HTTP = ID + ".serve-http";
    public static final String SERVE_FTP = ID + ".serve-ftp";

    /** What the plugin takes from the machine; tests replace each part. */
    record Environment(Executor ui, Supplier<List<ServerAddresses.Nic>> nics, IntPredicate portFree, Consumer<String> clipboard,
                       Consumer<Path> reveal, Path home, String osName, ZoneId zone, BiFunction<Duration, Runnable, Runnable> every) {
        static Environment system() {
            return new Environment(SwingUtilities::invokeLater, ServerAddresses::system, FileServerPlugin::portFree, FileServerPlugin::copy,
                FileServerPlugin::reveal, Path.of(System.getProperty("user.home")), System.getProperty("os.name", ""), ZoneId.systemDefault(),
                (interval, task) -> {
                    var timer = new Timer((int) interval.toMillis(), event -> task.run());
                    timer.start();
                    return timer::stop;
                });
        }

        boolean linux() { return osName.toLowerCase(Locale.ROOT).contains("linux"); }
    }

    private final Environment environment;
    private PluginContext context;
    private FileServerSettings settings;
    private Optional<VaultApi> vault = Optional.empty();
    private DefinitionStore store;
    private ServerRegistry registry;
    private TlsMaterial tls;
    private HostKey hostKey;
    private TransferActivities transfers;
    private FileServerScope scope;
    private StatusItem status;
    private PluginAction stopAll;
    private Subscription quitGuard;
    private Runnable addressTimer;
    private final Map<UUID, FileServersPanel> panels = new LinkedHashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new LinkedHashMap<>();
    private List<String> reportedWarnings = List.of();
    private String reportedError;
    private boolean stopped;
    private boolean activityRefreshPending;
    private ServerEditor currentEditor;
    private ConfirmPanel currentConfirm;

    public FileServerPlugin() { this(Environment.system()); }

    FileServerPlugin(Environment environment) { this.environment = environment; }

    @Override public void start(PluginContext context) throws Exception {
        this.context = context;
        Files.createDirectories(context.dataDirectory());
        settings = new FileServerSettings(context.settings());
        try { vault = context.services().find(VaultApi.class); } catch (NoClassDefFoundError absent) { vault = Optional.empty(); }
        var logins = new LoginResolver(vault, () -> System.getProperty("user.name", "jasper"), SessionPasswords::generate);
        transfers = new TransferActivities(context.activities()::begin, SHOW);
        tls = new TlsMaterial(context.dataDirectory().resolve("tls"), Clock.systemUTC(), FileServerPlugin::hostName);
        hostKey = new HostKey(context.dataDirectory().resolve("hostkey"));
        registry = new ServerRegistry(Engines.factory(new LoginThrottle(Clock.systemUTC()), environment.nics(), tls, settings::passiveRange, hostKey), logins,
            context.background(), environment.ui(), Clock.systemUTC(), settings.activityEntries::get, this::observe);
        store = new DefinitionStore(context.dataDirectory().resolve("servers.toml"), context.background(), environment.ui());
        var icon = context.appearance().icon(IconName.SERVER);
        context.actions().register(ActionSpec.of(SHOW, "Toggle File Servers").withIcon(icon)
                .withKeywords(List.of("file server", "share", "http", "ftp", "sftp", "tftp", "panel")),
            invoked -> context.panels().toggle(PANEL, invoked.window()));
        context.actions().register(ActionSpec.of(NEW, "New File Server…").withIcon(context.appearance().icon(IconName.ADD))
                .withKeywords(List.of("file server", "share", "folder", "http")),
            invoked -> edit(invoked.window(), Optional.empty(), false));
        stopAll = context.actions().register(ActionSpec.of(STOP_ALL, "Stop All File Servers").withKeywords(List.of("file server", "stop", "share")),
            invoked -> registry.stopAll());
        stopAll.setEnabled(false);
        context.actions().register(ActionSpec.of(SERVERS, "Start or Stop a File Server…").withIcon(icon)
                .withKeywords(List.of("file server", "start", "stop", "share")),
            invoked -> context.palette().open(invoked.window(), FileServerScope.ID, Optional.empty(), Optional.empty()));
        context.actions().register(ActionSpec.of(SERVE_HTTP, "Serve Current Folder over HTTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "http", "folder")),
            invoked -> serve(invoked, Protocol.HTTP));
        context.actions().register(ActionSpec.of(SERVE_FTP, "Serve Current Folder over FTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "ftp", "folder")),
            invoked -> serve(invoked, Protocol.FTP));
        scope = new FileServerScope(() -> registry.servers(), environment.home(), this::toggle,
            (window, id) -> registry.server(id).ifPresent(view -> edit(window, Optional.of(view.definition()), true)), SERVERS);
        context.palette().register(scope);
        context.panels().register(new PanelSpec(PANEL, "File Servers", icon, Anchor.LEFT).withToggleActionId(SHOW), this::createPanel);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 70));
        status.setIcon(icon);
        status.setAction(SHOW);
        status.setVisible(false);
        store.onChanged(this::definitionsChanged);
        registry.onChanged(this::serversChanged);
        registry.onActivity(id -> activityChanged());
        settings.confirmQuit.onChanged(value -> syncQuitGuard());
        if (vault.isPresent()) context.events().subscribe(VaultApi.LOCK_STATE_CHANGED, state -> {
            if (stopped) return;
            if (state == LockState.UNLOCKED) registry.vaultUnlocked();
            refreshPanels();
        });
        addressTimer = environment.every().apply(Duration.ofSeconds(10), () -> {
            if (!stopped && panelHosts.values().stream().anyMatch(PanelHost::visible)) registry.refreshAddresses();
        });
        store.load().whenComplete((ignored, failure) -> { if (!stopped) registry.autostart(); });
        context.background().execute(() -> {
            try { tls.fingerprint(); }
            catch (EngineFailure unreadable) { /* reported when a TLS server starts or the fingerprints are shown */ }
            environment.ui().execute(() -> { if (!stopped) refreshPanels(); });
        });
    }

    @Override public void stop() {
        stopped = true;
        if (addressTimer != null) addressTimer.run();
        if (registry != null) registry.shutdown();
    }

    DefinitionStore store() { return store; }
    ServerRegistry registry() { return registry; }
    ServerEditor currentEditor() { return currentEditor; }
    ConfirmPanel currentConfirm() { return currentConfirm; }
    WindowHandle windowHandle(UUID id) { return context.terminals().window(id).orElseThrow(); }

    /** Engine events reach the transfer activities only while the plugin runs: a late one must not open an activity on a closed context. */
    void observe(ServerEvent event) { if (!stopped) transfers.accept(event); }

    TlsMaterial tls() { return tls; }
    HostKey hostKey() { return hostKey; }

    /** This computer's name for the certificate; empty when the system will not say. Slow on some networks, so call it off the UI thread. */
    static String hostName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException | SecurityException unknown) { return ""; }
    }

    private JComponent createPanel(PanelHost host) {
        WindowHandle window = host.window();
        var panel = new FileServersPanel(new FileServersPanel.Actions(
            id -> toggle(window, id),
            editing -> editing.ifPresentOrElse(id -> registry.server(id).ifPresent(view -> edit(window, Optional.of(view.definition()), true)),
                () -> edit(window, Optional.empty(), false)),
            this::duplicate, id -> delete(window, id), this::reveal, environment.clipboard(),
            id -> registry.password(id).ifPresent(environment.clipboard()), registry::clearActivity,
            id -> showFingerprints(window, id)),
            registry::activity, environment.home(), ServerRows.firewallHint(environment.osName()), environment.zone(),
            context.appearance().icon(IconName.SERVER));
        panel.onHeaderChanged(host::setHeader);
        panel.setTlsFingerprint(tls.cachedFingerprint());
        panel.setServers(registry.servers(), store.error());
        panels.put(window.id(), panel);
        panelHosts.put(window.id(), host);
        host.onVisibility(visible -> { if (visible) registry.refreshAddresses(); });
        host.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        return panel;
    }

    void toggle(WindowHandle window, UUID id) {
        registry.server(id).ifPresent(view -> {
            switch (view.state().phase()) {
                case STOPPED, FAILED, WAITING_FOR_VAULT -> registry.start(id, window);
                case RUNNING -> registry.stop(id);
                case STARTING -> registry.cancelStart(id);
                case STOPPING -> { }
            }
        });
    }

    /** New ({@code initial} empty), edit ({@code existing}), or save a temporary server under its own id. */
    void edit(WindowHandle window, Optional<ServerDefinition> initial, boolean existing) {
        boolean temporary = initial.flatMap(d -> registry.server(d.id())).map(ServerRegistry.View::temporary).orElse(false);
        String title = initial.isEmpty() ? "New File Server" : temporary ? "Save " + initial.get().name() + " as a Server" : "Edit " + initial.get().name();
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        boolean[] closed = {false};
        ServerEditor[] editor = new ServerEditor[1];
        editor[0] = new ServerEditor(store.definitions(), initial, existing, Engines.AVAILABLE, environment.nics().get(), vault.isPresent(),
            this::accountName, () -> pickAccount(window),
            () -> context.windows().chooseDirectory(window, "Folder to share", initial.map(ServerDefinition::folder)),
            new ServerEditor.KeySources(() -> keyFiles(window), FileServerPlugin::pasted),
            environment.linux(),
            definition -> store.put(definition).whenComplete((ignored, failure) -> environment.ui().execute(() -> {
                if (stopped) return;
                if (failure != null) { if (!closed[0]) editor[0].showError(message(failure)); return; }
                boolean dismissed = closed[0] || !window.isOpen();
                dialog.close();
                if (dismissed) return;
                registry.server(definition.id()).filter(ServerRegistry.View::restartNeeded).ifPresent(view -> confirmRestart(window, view));
            })),
            dialog::close);
        currentEditor = editor[0];
        dialog.setContent(editor[0]);
        dialog.onClosed(() -> { closed[0] = true; if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    /** The text of public key files the user picks; a file that can't be read is reported and skipped. */
    private Optional<String> keyFiles(WindowHandle window) {
        List<Path> chosen = context.windows().chooseFiles(window, "Public keys to allow", Optional.of(environment.home().resolve(".ssh")));
        var text = new StringBuilder();
        for (Path file : chosen) {
            try { text.append(Files.readString(file)).append('\n'); }
            catch (IOException | RuntimeException failure) {
                context.notifications().group().error("Could not read a public key file", file + ": " + message(failure), failure);
            }
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
    }

    /** The clipboard's text; empty when there is none or no clipboard. */
    static Optional<String> pasted() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return data instanceof String text && !text.isBlank() ? Optional.of(text) : Optional.empty();
        } catch (HeadlessException | IllegalStateException | UnsupportedFlavorException | IOException unavailable) {
            return Optional.empty();
        }
    }

    /** Shares the folder the shell is in, read-only and on this computer only, until stopped. */
    void serve(ActionContext invoked, Protocol protocol) {
        Path folder = invoked.pane().or(() -> context.terminals().activePane())
            .flatMap(pane -> pane.info().workingDirectory()).filter(Files::isDirectory).orElse(environment.home());
        Set<Integer> used = registry.servers().stream().filter(view -> !view.state().phase().startable())
            .map(view -> view.definition().port()).collect(Collectors.toSet());
        int port = protocol.defaultPort();
        while ((used.contains(port) || !environment.portFree().test(port)) && port < protocol.defaultPort() + 100) port++;
        Path leaf = folder.getFileName();
        String folderName = leaf == null ? folder.toString() : leaf.toString();
        if (folderName.length() > 60) folderName = folderName.substring(0, 60) + "…";
        var created = ServerDefinition.create(uniqueName(folderName + " (" + protocol.label() + ")"), protocol, folder);
        var definition = new ServerDefinition(created.id(), created.name(), protocol, folder, Bind.LOOPBACK, port, Optional.empty(), false,
            created.login(), List.of(), false, false, false);
        registry.startTemporary(definition, invoked.window());
        showPanel(invoked.window(), definition.id());
    }

    private void showPanel(WindowHandle window, UUID id) {
        PanelHost host = panelHosts.get(window.id());
        if (host == null) context.panels().toggle(PANEL, window);
        else if (!host.visible()) host.show();
        FileServersPanel panel = panels.get(window.id());
        if (panel != null) panel.select(id);
    }

    private void confirmRestart(WindowHandle window, ServerRegistry.View view) {
        String name = view.definition().name();
        confirm(window, "Restart " + name + "?", "‘" + name + "’ is running with its old settings. Restart it now to apply the changes?",
            "Restart Now", "Later", () -> registry.stop(view.definition().id()).thenRun(() -> registry.start(view.definition().id(), window)), () -> { });
    }

    private void duplicate(UUID id) {
        registry.server(id).ifPresent(view ->
            report("Could not duplicate the server", store.put(view.definition().copyAs(uniqueName(view.definition().name() + " copy")))));
    }

    private void delete(WindowHandle window, UUID id) {
        registry.server(id).ifPresent(view -> confirm(window, "Delete " + view.definition().name() + "?",
            "Delete ‘" + view.definition().name() + "’? Its settings are removed; the shared folder is not touched.", "Delete", "Cancel",
            () -> registry.stop(id).thenRun(() -> report("Could not delete the server", store.remove(id))), () -> { }));
    }

    private void reveal(UUID id) {
        registry.server(id).ifPresent(view -> {
            Path folder = view.definition().folder();
            context.background().execute(() -> {
                try { environment.reveal().accept(folder); }
                catch (RuntimeException failure) {
                    environment.ui().execute(() -> {
                        if (!stopped) context.notifications().group().error("Could not show a server's folder", folder + ": " + message(failure), failure);
                    });
                }
            });
        });
    }

    /** The quit guard: asks over the active terminal window; allows the quit when there is none. */
    CompletableFuture<Boolean> confirmQuit() {
        int count = registry.active();
        if (count == 0) return CompletableFuture.completedFuture(true);
        Optional<WindowHandle> owner = context.terminals().activeWindow()
            .or(() -> context.terminals().windows().stream().filter(WindowHandle::isOpen).findFirst());
        if (owner.isEmpty()) return CompletableFuture.completedFuture(true);
        var decision = new CompletableFuture<Boolean>();
        String text = count == 1 ? "1 file server is running and will stop." : count + " file servers are running and will stop.";
        confirm(owner.get(), "Quit Jasper?", text, "Quit", "Cancel", () -> decision.complete(true), () -> decision.complete(false));
        return decision;
    }

    private void syncQuitGuard() {
        boolean wanted = !stopped && registry.active() > 0 && settings.confirmQuit.get();
        if (wanted && quitGuard == null) quitGuard = context.windows().onQuitting(this::confirmQuit);
        else if (!wanted && quitGuard != null) { quitGuard.close(); quitGuard = null; }
    }

    /** A modal question; closing the dialog any other way counts as the cancel answer. */
    private void confirm(WindowHandle window, String title, String text, String confirmLabel, String cancelLabel, Runnable onConfirm, Runnable onCancel) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        boolean[] decided = {false};
        ConfirmPanel[] panel = new ConfirmPanel[1];
        panel[0] = new ConfirmPanel(text, confirmLabel, cancelLabel,
            () -> { decided[0] = true; dialog.close(); onConfirm.run(); },
            () -> { decided[0] = true; dialog.close(); onCancel.run(); });
        currentConfirm = panel[0];
        dialog.setContent(panel[0]);
        dialog.onClosed(() -> {
            if (currentConfirm == panel[0]) currentConfirm = null;
            if (!decided[0]) { decided[0] = true; onCancel.run(); }
        });
        dialog.show();
    }

    private void definitionsChanged() {
        if (stopped) return;
        registry.setDefinitions(store.definitions());
        List<String> warnings = store.warnings();
        if (!warnings.isEmpty() && !warnings.equals(reportedWarnings)) for (String warning : warnings) {
            context.log().log(System.Logger.Level.WARNING, "servers.toml: {0}", warning);
            context.notifications().group().warning("servers.toml has a problem", warning);
        }
        reportedWarnings = warnings;
        String error = store.error().orElse(null);
        if (error != null && !error.equals(reportedError))
            context.notifications().group().error("servers.toml has errors", error + ". File Server will not save changes until it is fixed.");
        reportedError = error;
        refreshPanels();
    }

    private void serversChanged() {
        if (stopped) return;
        refreshPanels();
        scope.changed();
        int running = registry.running();
        status.setText(running == 1 ? "⇅ 1 server" : "⇅ " + running + " servers");
        status.setTooltip(registry.servers().stream().filter(view -> view.state().phase() == Phase.RUNNING)
            .map(view -> view.definition().name() + view.addresses().stream().findFirst().map(uri -> " — " + uri).orElse(""))
            .collect(Collectors.joining("\n")));
        status.setVisible(running > 0);
        stopAll.setEnabled(registry.active() > 0);
        syncQuitGuard();
    }

    /** A burst of events (a transfer's progress, a busy listing) becomes one refresh on the UI thread. */
    private void activityChanged() {
        if (activityRefreshPending) return;
        activityRefreshPending = true;
        environment.ui().execute(() -> {
            activityRefreshPending = false;
            if (!stopped) panels.values().forEach(FileServersPanel::refreshActivity);
        });
    }

    private void refreshPanels() {
        for (FileServersPanel panel : panels.values()) {
            panel.setTlsFingerprint(tls.cachedFingerprint());
            panel.setServers(registry.servers(), store.error());
            panel.refreshActivity();
        }
    }

    /** Shows the shared certificate's fingerprint, offering to regenerate it; the file I/O runs off the UI thread. */
    void showFingerprints(WindowHandle window, UUID id) {
        if (registry.server(id).isEmpty()) return;
        context.background().execute(() -> {
            String text;
            try {
                text = tls.fingerprint().map(fingerprint -> "TLS certificate (SHA-256): " + fingerprint)
                    .orElse("No TLS certificate yet: Jasper creates one when a TLS server first starts.");
            } catch (EngineFailure unreadable) {
                text = unreadable.getMessage() + ". Regenerate it to make a new one.";
            }
            String shown = text;
            environment.ui().execute(() -> {
                if (stopped || !window.isOpen()) return;
                refreshPanels();
                confirm(window, "Fingerprints", shown, "Regenerate Certificate…", "Close", () -> confirmRegenerate(window), () -> { });
            });
        });
    }

    private void confirmRegenerate(WindowHandle window) {
        confirm(window, "Regenerate the TLS certificate?",
            "Clients that trusted the old certificate will warn about the new one. Running TLS servers keep the old one until they restart.",
            "Regenerate", "Cancel", () -> context.background().execute(() -> {
                EngineFailure failure = null;
                try { tls.regenerate(); }
                catch (EngineFailure unwritable) { failure = unwritable; }
                EngineFailure error = failure;
                environment.ui().execute(() -> {
                    if (stopped) return;
                    if (error != null) context.notifications().group().error("Could not regenerate the TLS certificate", message(error), error);
                    refreshPanels();
                });
            }), () -> { });
    }

    private Optional<String> accountName(UUID id) {
        if (vault.isEmpty()) return Optional.empty();
        return vault.get().credentials().stream().filter(d -> d.id().equals(id)).findFirst().map(CredentialDescriptor::name);
    }

    private CompletableFuture<Optional<ServerEditor.Account>> pickAccount(WindowHandle window) {
        if (vault.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        return vault.get().pick(window).thenApply(chosen -> chosen.map(d -> new ServerEditor.Account(d.id(), d.name())));
    }

    private String uniqueName(String base) {
        Set<String> taken = new HashSet<>();
        store.definitions().forEach(d -> taken.add(d.name().toLowerCase(Locale.ROOT)));
        registry.servers().forEach(view -> taken.add(view.definition().name().toLowerCase(Locale.ROOT)));
        if (!taken.contains(base.toLowerCase(Locale.ROOT))) return base;
        for (int n = 2; ; n++) if (!taken.contains((base + " " + n).toLowerCase(Locale.ROOT))) return base + " " + n;
    }

    private void report(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> environment.ui().execute(() -> {
            if (!stopped && failure != null) context.notifications().group().error(title, message(failure), failure);
        }));
    }

    static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    static boolean portFree(int port) {
        try (var socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException taken) {
            return false;
        }
    }

    static void copy(String text) {
        try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null); }
        catch (HeadlessException | IllegalStateException unavailable) { /* nothing to copy to */ }
    }

    static void reveal(Path folder) {
        if (!Desktop.isDesktopSupported()) throw new UnsupportedOperationException("This system cannot show folders");
        Desktop desktop = Desktop.getDesktop();
        try {
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(folder.toFile());
            else if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(folder.toFile());
            else throw new UnsupportedOperationException("This system cannot show folders");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
