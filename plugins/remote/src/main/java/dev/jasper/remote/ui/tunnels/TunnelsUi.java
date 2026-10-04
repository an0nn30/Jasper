package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelManager;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.remote.tunnels.TunnelStore;
import dev.jasper.remote.ui.ConfirmPanel;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.notifications.DisplayMode;
import dev.jasper.sdk.notifications.GroupSpec;
import dev.jasper.sdk.notifications.NotificationGroup;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.Anchor;
import dev.jasper.sdk.ui.BundledIcon;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PanelHost;
import dev.jasper.sdk.ui.PanelSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.Side;
import dev.jasper.sdk.ui.StatusItem;
import dev.jasper.sdk.ui.StatusItemSpec;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Registers the tunnels surface — the Tunnels panel, its actions, the SSH menu group, Forward a Port on the terminal
 * menu, the palette scope and the status item — and keeps the manager in step with tunnels.toml: a removed tunnel stops,
 * a tunnel whose forwarding changed restarts. UI thread only.
 */
public final class TunnelsUi implements AutoCloseable {
    public static final String PANEL = "dev.jasper.remote.tunnels.panel", TOGGLE = "dev.jasper.remote.tunnels",
        SHOW = "dev.jasper.remote.tunnels.show", NEW = "dev.jasper.remote.tunnels.new", FORWARD = "dev.jasper.remote.tunnels.forward",
        STOP_ALL = "dev.jasper.remote.tunnels.stop-all", STATUS = "dev.jasper.remote.tunnels.status";
    /** The notification group for tunnel failures, which the user can mute apart from Remote's own. */
    public static final String NOTIFICATIONS = "dev.jasper.remote.tunnels";

    private final PluginContext context;
    private final Executor ui;
    private final TunnelStore store;
    private final TunnelManager manager;
    private final Supplier<List<RemoteHost>> hosts;
    private final Function<UUID, Optional<RemoteHost>> host;
    private final Consumer<String> clipboard;
    private final Icon icon;
    private final NotificationGroup notifications;
    private Icon warningIcon;
    private final Map<UUID, TunnelsPanel> panels = new HashMap<>();
    private final Map<UUID, PanelHost> panelHosts = new HashMap<>();
    private final Set<PluginDialog> dialogs = new HashSet<>();
    private final TunnelsScope scope;
    private final StatusItem status;
    private final PluginAction stopAll;
    private final Timer ticker;
    private final Subscription storeSubscription, managerSubscription;
    private TunnelEditor currentEditor;
    private ForwardPanel currentForward;
    private ConfirmPanel currentConfirm;
    private boolean closed;

    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost) {
        this(context, ui, store, manager, hosts, host, paneHost,
            text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
    }

    /**
     * @param host a saved host by id
     * @param paneHost the saved host behind an SSH pane, by pane id; empty for any other pane
     * @param clipboard where Copy Address puts its text
     */
    public TunnelsUi(PluginContext context, Executor ui, TunnelStore store, TunnelManager manager, Supplier<List<RemoteHost>> hosts,
                     Function<UUID, Optional<RemoteHost>> host, Function<UUID, Optional<RemoteHost>> paneHost, Consumer<String> clipboard) {
        this.context = context; this.ui = ui; this.store = store; this.manager = manager; this.hosts = hosts; this.host = host;
        this.clipboard = clipboard;
        notifications = context.notifications().group(new GroupSpec(NOTIFICATIONS, "Tunnels", DisplayMode.CARD));
        icon = context.appearance().icon(BundledIcon.ACTIONS_SWAP_PANELS);
        // Actions first: the panel names its toggle, and menus may only name registered actions.
        context.actions().register(ActionSpec.of(TOGGLE, "Toggle Tunnels").withIcon(icon)
            .withKeywords(List.of("tunnels", "ports", "forward", "panel", "show", "hide")), invoked -> context.panels().toggle(PANEL, invoked.window()));
        context.actions().register(ActionSpec.of(SHOW, "Show Tunnels").withIcon(icon).withKeywords(List.of("tunnels", "ports")),
            invoked -> reveal(invoked.window()));
        context.actions().register(ActionSpec.of(NEW, "New Tunnel...").withKeywords(List.of("tunnel", "port", "forward", "ssh", "socks")),
            invoked -> edit(invoked.window(), Optional.empty(), invoked.pane().flatMap(pane -> paneHost.apply(pane.id())).map(RemoteHost::id)));
        context.actions().register(ActionSpec.of(FORWARD, "Forward a Port...").withKeywords(List.of("tunnel", "port", "forward", "ssh")),
            invoked -> forward(invoked.window(), invoked.pane().flatMap(pane -> paneHost.apply(pane.id()))));
        stopAll = context.actions().register(ActionSpec.of(STOP_ALL, "Stop All Tunnels").withKeywords(List.of("tunnels", "stop")),
            invoked -> manager.stopAll());
        stopAll.setEnabled(false);
        context.menus().terminalContext().add(FORWARD);
        context.panels().register(new PanelSpec(PANEL, "Tunnels", icon, Anchor.LEFT).withToggleActionId(TOGGLE), this::create);
        scope = new TunnelsScope(this::all, store::error, manager::state, this::hostName,
            this::toggle, (window, tunnel) -> current(tunnel.id()).ifPresent(this::browse),
            (window, tunnel) -> current(tunnel.id()).filter(now -> !now.quick()).ifPresent(now -> edit(window, Optional.of(now), Optional.empty())));
        context.palette().register(scope);
        status = context.statusBar().add(new StatusItemSpec(STATUS, Side.RIGHT, 59));
        status.setIcon(icon);
        status.setAction(TOGGLE);
        status.setVisible(false);
        ticker = new Timer(1000, event -> panels.values().forEach(TunnelsPanel::refresh));
        storeSubscription = store.onChanged(this::storeChanged);
        managerSubscription = manager.onChanged(this::refresh);
    }

    /** The SSH menu's tunnels group. */
    public void addToMenu(PluginMenu menu) {
        menu.addSeparator();
        menu.add(TOGGLE);
        menu.add(NEW);
        menu.add(FORWARD);
        menu.add(STOP_ALL);
    }

    /** Saved hosts changed: the rows' host names follow. */
    public void hostsChanged() { refresh(); }

    /** The saved tunnels that go through {@code hostId}, for the delete-host confirmation. */
    public List<String> usersOf(UUID hostId) {
        return store.tunnels().stream().filter(tunnel -> tunnel.hostId().equals(hostId)).map(Tunnel::name).toList();
    }

    public TunnelManager manager() { return manager; }
    public TunnelEditor currentEditor() { return currentEditor; }
    public ForwardPanel currentForward() { return currentForward; }
    public ConfirmPanel currentConfirm() { return currentConfirm; }

    /** Plugin stop: every tunnel stops and the dialogs close. */
    @Override public void close() {
        closed = true;
        ticker.stop();
        storeSubscription.close();
        managerSubscription.close();
        for (PluginDialog dialog : List.copyOf(dialogs)) dialog.close();
        manager.close();
    }

    private JComponent create(PanelHost panelHost) {
        WindowHandle window = panelHost.window();
        var panel = new TunnelsPanel(new TunnelsPanel.Actions(tunnel -> manager.start(tunnel, window), tunnel -> manager.stop(tunnel.id()),
            tunnel -> edit(window, Optional.of(tunnel), Optional.empty()), this::duplicate, tunnel -> delete(window, tunnel),
            tunnel -> edit(window, Optional.of(tunnel), Optional.empty()), this::browse, this::copyAddress,
            () -> edit(window, Optional.empty(), Optional.empty())), context.appearance()::icon, icon);
        panel.onHeaderChanged(panelHost::setHeader);
        panels.put(window.id(), panel);
        panelHosts.put(window.id(), panelHost);
        panelHost.onClosed(() -> { panels.remove(window.id()); panelHosts.remove(window.id()); });
        fill(panel);
        return panel;
    }

    private void fill(TunnelsPanel panel) { panel.setTunnels(store.tunnels(), quickOnly(), store.error(), manager::state, this::hostName); }

    /** Quick forwards not yet in the store; one that was just saved is listed once. */
    private List<Tunnel> quickOnly() {
        Set<UUID> saved = store.tunnels().stream().map(Tunnel::id).collect(Collectors.toSet());
        return manager.quickForwards().stream().filter(tunnel -> !saved.contains(tunnel.id())).toList();
    }

    private List<Tunnel> all() {
        var all = new ArrayList<>(store.tunnels());
        all.addAll(quickOnly());
        return all;
    }

    private Optional<String> hostName(UUID id) { return host.apply(id).map(RemoteHost::name); }

    private void refresh() {
        if (closed) return;
        for (TunnelsPanel panel : panels.values()) fill(panel);
        scope.changed();
        int active = manager.active(), failing = manager.failed();
        status.setText(TunnelRows.status(active, failing));
        status.setTooltip(all().stream().filter(tunnel -> !(manager.state(tunnel.id()) instanceof TunnelState.Stopped))
            .map(tunnel -> TunnelRows.title(tunnel, manager.state(tunnel.id())) + ": " + TunnelRows.stateText(manager.state(tunnel.id())))
            .collect(Collectors.joining(", ")));
        if (failing > 0) {
            if (warningIcon == null) warningIcon = context.appearance().icon(BundledIcon.STATUS_WARNING);
            status.setIcon(warningIcon);
        } else status.setIcon(icon);
        status.setVisible(active > 0 || failing > 0);
        stopAll.setEnabled(active > 0 || failing > 0);
        if (active > 0) ticker.start(); else ticker.stop();
    }

    /**
     * tunnels.toml changed: a tunnel with no row left stops, a running one whose forwarding changed restarts. A row is a
     * saved tunnel or a quick forward; the ids decide, not the running definition, which stays unnamed after Save as Tunnel.
     */
    private void storeChanged() {
        if (closed) return;
        store.warnings().forEach(warning -> context.log().log(System.Logger.Level.WARNING, "tunnels.toml: {0}", warning));
        Map<UUID, Tunnel> saved = store.tunnels().stream().collect(Collectors.toMap(Tunnel::id, tunnel -> tunnel));
        Set<UUID> rows = new HashSet<>(saved.keySet());
        manager.quickForwards().forEach(quick -> rows.add(quick.id()));
        // Running, starting or failed: stopping clears a removed tunnel's failure as well.
        for (UUID id : manager.known()) if (!rows.contains(id)) manager.stop(id);
        for (Tunnel running : manager.runningTunnels()) {
            Tunnel now = saved.get(running.id());
            if (now != null && !now.sameForwarding(running)) { manager.stop(running.id()); manager.start(now, owner().orElse(null)); }
        }
        refresh();
    }

    private Optional<WindowHandle> owner() {
        return context.terminals().activeWindow().or(() -> context.terminals().windows().stream().findFirst());
    }

    /**
     * The tunnel as it is now. A palette row's token is a snapshot from when the row was built, and a saved tunnel may have
     * been edited since, so a verb acts on this.
     */
    private Optional<Tunnel> current(UUID id) {
        return store.tunnel(id).or(() -> manager.quickForwards().stream().filter(tunnel -> tunnel.id().equals(id)).findFirst());
    }

    /** The palette's Start or stop: a running tunnel stops, any other starts as it is saved now. */
    private void toggle(WindowHandle window, Tunnel snapshot) {
        UUID id = snapshot.id();
        if (manager.state(id).running()) manager.stop(id);
        else current(id).ifPresent(tunnel -> manager.start(tunnel, window));
    }

    private void reveal(WindowHandle window) {
        if (closed || !window.isOpen()) return;
        PanelHost existing = panelHosts.get(window.id());
        if (existing == null) context.panels().toggle(PANEL, window); else existing.show();
    }

    private void edit(WindowHandle window, Optional<Tunnel> editing, Optional<UUID> preferredHost) {
        if (closed || !window.isOpen()) return;
        String title = editing.isEmpty() ? "New Tunnel" : editing.get().quick() ? "Save as Tunnel" : "Edit " + editing.get().name();
        PluginDialog dialog = dialog(window, title);
        TunnelEditor[] editor = new TunnelEditor[1];
        editor[0] = new TunnelEditor(hosts.get(), store.tunnels(), editing, preferredHost, tunnel -> store.put(tunnel).whenComplete((ignored, failure) -> ui.execute(() -> {
            if (closed) return;
            if (failure != null) { editor[0].showError(message(failure)); return; }
            editing.filter(Tunnel::quick).ifPresent(saved -> manager.saved(saved.id()));
            dialog.close();
        })), dialog::close);
        currentEditor = editor[0];
        dialog.setContent(editor[0]);
        dialog.onClosed(() -> { if (currentEditor == editor[0]) currentEditor = null; });
        dialog.show();
    }

    private void forward(WindowHandle window, Optional<RemoteHost> fromPane) {
        if (closed || !window.isOpen()) return;
        PluginDialog dialog = dialog(window, "Forward a Port");
        ForwardPanel[] panel = new ForwardPanel[1];
        panel[0] = new ForwardPanel(hosts.get(), fromPane, TunnelsUi::freeLoopbackPort, tunnel -> {
            if (tunnel.quick()) {
                manager.forward(tunnel, window);
                dialog.close();
                reveal(window);
                return;
            }
            store.put(tunnel).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (closed) return;
                if (failure != null) { panel[0].showError(message(failure)); return; }
                manager.start(tunnel, window);
                dialog.close();
                reveal(window);
            }));
        }, dialog::close);
        currentForward = panel[0];
        dialog.setContent(panel[0]);
        dialog.onClosed(() -> { if (currentForward == panel[0]) currentForward = null; });
        dialog.show();
    }

    private void delete(WindowHandle window, Tunnel tunnel) {
        if (closed) return;
        PluginDialog dialog = dialog(window, "Delete " + tunnel.name() + "?");
        boolean running = manager.state(tunnel.id()).running();
        // The tunnel stops when the store reports its entry gone (storeChanged), so a refused removal leaves it running.
        var confirm = new ConfirmPanel("Delete the tunnel " + tunnel.name() + "?" + (running ? " It is running; deleting it stops it." : ""), "Delete", () -> {
            store.remove(tunnel.id()).whenComplete((ignored, failure) -> ui.execute(() -> {
                if (failure != null && !closed) notifications.error("Could not delete the tunnel", message(failure), failure);
                dialog.close();
            }));
        }, dialog::close);
        currentConfirm = confirm;
        dialog.setContent(confirm);
        dialog.onClosed(() -> { if (currentConfirm == confirm) currentConfirm = null; });
        dialog.show();
    }

    private void duplicate(Tunnel tunnel) {
        String base = tunnel.name() + " copy";
        String name = base;
        for (int i = 2; taken(name); i++) name = base + " " + i;
        report("Could not duplicate the tunnel", store.put(Tunnel.create(name, tunnel.hostId(), tunnel.kind(), tunnel.bindAddress(), tunnel.bindPort(), tunnel.targetHost(), tunnel.targetPort())));
    }

    private boolean taken(String name) { return store.tunnels().stream().anyMatch(tunnel -> tunnel.name().equalsIgnoreCase(name)); }

    private void browse(Tunnel tunnel) {
        TunnelRows.browseUri(tunnel, manager.state(tunnel.id())).ifPresent(uri -> context.platform().browse(uri));
    }

    private void copyAddress(Tunnel tunnel) {
        TunnelRows.address(tunnel, manager.state(tunnel.id()), host.apply(tunnel.hostId()).map(RemoteHost::hostname)).ifPresent(text -> {
            try { clipboard.accept(text); }
            catch (RuntimeException unavailable) { notifications.error("Could not copy the address", message(unavailable), unavailable); }
        });
    }

    private PluginDialog dialog(WindowHandle window, String title) {
        PluginDialog dialog = context.windows().dialog(new DialogSpec(title, window, true));
        dialogs.add(dialog);
        dialog.onClosed(() -> dialogs.remove(dialog));
        return dialog;
    }

    private void report(String title, CompletableFuture<Void> mutation) {
        mutation.whenComplete((ignored, failure) -> ui.execute(() -> { if (failure != null && !closed) notifications.error(title, message(failure), failure); }));
    }

    /** Whether nothing listens on this computer's loopback at {@code port}. */
    static boolean freeLoopbackPort(int port) {
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException taken) { return false; }
    }

    private static String message(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
