package dev.jasper.remote;

import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.workstation.Layout;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.ControlState;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.Option;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.ToolbarItem;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import dev.jasper.sdk.ui.ViewSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Keeps the Sessions dropdown in sync with the WSL distributions and the saved hosts. Distributions come first, titled
 * with their WSL version because toolbar menus have no separators; hosts follow alphabetically. Every host and
 * distribution is an action in both looks (the palette lists them and Keymap binds them). Under {@link Layout#CLASSIC}
 * the toolbar holds one view per window, the session combo and Run, in place of the dropdown (Enterprise presentation
 * spec 14.4). Owned by RemotePlugin on the UI thread.
 */
final class SessionsToolbar implements AutoCloseable {
    private static final String MANAGE = "dev.jasper.remote.sessions.manage";
    private final PluginContext context;
    private final HostStore store;
    private final String icon;
    private final String distroIcon;
    private final BiConsumer<WindowHandle, RemoteHost> activate;
    private final Supplier<List<ShellProfileInfo>> distros;
    private final BiConsumer<WindowHandle, ShellProfileInfo> openDistro;
    private final PluginAction manage;
    private final Map<UUID, PluginAction> actions = new HashMap<>();
    private final Map<String, PluginAction> distroActions = new HashMap<>();
    private List<String> placed = List.of();
    /** The ROWS dropdown's placement; null under CLASSIC. */
    private Subscription placement;
    /** The CLASSIC view's placement; null under ROWS. */
    private Subscription viewPlacement;
    private boolean closed;
    /** Which composition the toolbar takes (spec 14.4): the dropdown under ROWS, one view per window under CLASSIC. */
    private final Layout layout;
    private final SessionSelection selection;
    private final Consumer<WindowHandle> showHosts;
    private final Function<WindowHandle, Optional<UUID>> activeHost;
    private final Map<UUID, UiView> views = new HashMap<>();
    static final String VIEW = "session", CHOICE = "session.choice", CONNECT = "session.connect",
        MANAGE_OPTION = "manage", NONE_OPTION = "none";

    SessionsToolbar(PluginContext context, HostStore store, String icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro) {
        this(context, store, icon, activate, showHosts, distros, openDistro, Layout.ROWS, new SessionSelection(), window -> Optional.empty());
    }

    /**
     * {@code selection} is the windows' chosen rows, shared with their Sessions trees; {@code activeHost} is a window's active
     * SSH pane's host, the fallback while nothing is chosen.
     */
    SessionsToolbar(PluginContext context, HostStore store, String icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro, Layout layout, SessionSelection selection,
                    Function<WindowHandle, Optional<UUID>> activeHost) {
        this.layout = layout; this.selection = selection; this.showHosts = showHosts; this.activeHost = activeHost;
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;
        this.distros = distros; this.openDistro = openDistro;
        this.distroIcon = "terminal";
        manage = context.actions().register(ActionSpec.of(MANAGE, "Manage Sessions...").withIcon(icon),
            invoked -> showHosts.accept(invoked.window()));
        if (layout == Layout.CLASSIC) {
            viewPlacement = context.toolbar().add(ToolbarItem.view(this::create));
            selection.onChanged((window, row) -> renderViews());
        }
        refresh();
    }

    /** Registers, retitles and drops the host and distribution actions; places the dropdown (ROWS) or renders the views (CLASSIC). */
    void refresh() {
        if (closed) return;
        var ids = new ArrayList<String>();
        Set<String> listed = new HashSet<>();
        for (ShellProfileInfo profile : distros.get()) {
            String profileId = profile.id();
            String title = profile.name() + profile.detail().map(detail -> " (" + detail + ")").orElse("");
            PluginAction action = distroActions.get(profileId);
            if (action == null) {
                action = context.actions().register(ActionSpec.of(distroActionId(profileId), title).withIcon(distroIcon),
                    invoked -> distros.get().stream().filter(current -> current.id().equals(profileId)).findFirst()
                        .ifPresent(current -> openDistro.accept(invoked.window(), current)));
                distroActions.put(profileId, action);
            } else action.setTitle(title);
            ids.add(distroActionId(profileId));
            listed.add(profileId);
        }
        var hosts = store.hosts().stream().sorted(Comparator.comparing(RemoteHost::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RemoteHost::id)).toList();
        for (RemoteHost host : hosts) {
            UUID id = host.id();
            String title = host.name() + " (" + host.label() + ")";
            PluginAction action = actions.get(id);
            if (action == null) {
                action = context.actions().register(ActionSpec.of(actionId(id), title).withIcon(icon),
                    invoked -> store.host(id).ifPresent(current -> activate.accept(invoked.window(), current)));
                actions.put(id, action);
            } else action.setTitle(title);
            ids.add(actionId(id));
        }
        ids.add(MANAGE);
        if (layout == Layout.ROWS && !placed.equals(ids)) {
            if (placement != null) placement.close();
            placement = context.toolbar().add(ToolbarItem.menu(icon, "Sessions", ids));
            placed = List.copyOf(ids);
        }
        for (String profileId : List.copyOf(distroActions.keySet())) {
            if (!listed.contains(profileId)) distroActions.remove(profileId).close();
        }
        for (UUID id : List.copyOf(actions.keySet())) {
            if (store.host(id).isEmpty()) actions.remove(id).close();
        }
        if (layout == Layout.CLASSIC) renderViews();
    }

    // ---- the classic toolbar view (spec 14.4)

    private UiView create(WindowHandle window) {
        var view = context.kit().create(new ViewSpec(classic(window)), event -> classicEvent(window, event));
        views.put(window.id(), view);
        return view;
    }

    /**
     * Every open window's view shows the current hosts, distributions and choice (the choice, else the active SSH pane's
     * host, which RemotePlugin re-renders on pane and tab changes); a closed window's view is dropped.
     */
    void renderViews() {
        if (closed) return;
        for (var entry : List.copyOf(views.entrySet())) {
            var window = context.terminals().window(entry.getKey()).filter(WindowHandle::isOpen);
            if (window.isEmpty()) { views.remove(entry.getKey()); selection.forget(entry.getKey()); continue; }
            try { entry.getValue().update(new ViewSpec(classic(window.get()))); }
            catch (IllegalStateException disposed) { views.remove(entry.getKey()); }
        }
    }

    private List<Option> options() {
        var options = new ArrayList<Option>();
        store.hosts().stream().sorted(Comparator.comparing(RemoteHost::name, String.CASE_INSENSITIVE_ORDER).thenComparing(RemoteHost::id))
            .forEach(host -> options.add(new Option("host:" + host.id(), host.name() + " (" + host.hostname() + ")", Optional.empty())));
        for (ShellProfileInfo profile : distros.get())
            options.add(new Option("distro:" + profile.id(), profile.name() + profile.detail().map(detail -> " (" + detail + ")").orElse(""), Optional.empty()));
        if (options.isEmpty()) options.add(new Option(NONE_OPTION, "No saved sessions", Optional.empty()));
        options.add(new Option(MANAGE_OPTION, "Manage Sessions…", Optional.empty()));
        return options;
    }

    /** The window's row: its choice while it is listed, else its active SSH pane's host, else the first host or distribution. */
    Optional<String> current(WindowHandle window) {
        var listed = options().stream().map(Option::id).filter(id -> id.startsWith("host:") || id.startsWith("distro:")).toList();
        var chosen = selection.get(window.id()).filter(listed::contains);
        if (chosen.isPresent()) return chosen;
        var pane = activeHost.apply(window).map(id -> "host:" + id).filter(listed::contains);
        return pane.isPresent() ? pane : listed.stream().findFirst();
    }

    /**
     * IntelliJ's run-configuration combo and Run (plan 12 Rulings 12): the combo's face names the window's row (else the
     * first option, "No saved sessions" when nothing is saved) beside the application icon, and Run is disabled while
     * there is nothing to run.
     */
    private Nodes.Stack classic(WindowHandle window) {
        var options = options();
        var current = current(window);
        String primary = current.flatMap(id -> options.stream().filter(option -> option.id().equals(id)).findFirst())
            .orElse(options.getFirst()).label();
        return new Nodes.Stack(VIEW, ControlState.READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Choice(CHOICE, ControlState.READY, Nodes.ChoiceKind.DROPDOWN, options, current.orElse(NONE_OPTION), "Session", "", "",
                Optional.empty(), Optional.of(new Nodes.PickerFace(primary, "", "application", Nodes.Hue.NONE, Nodes.PickerDensity.COMPACT))),
            new Nodes.Button(CONNECT, new ControlState(true, current.isPresent(), false, ""), "", "run", Nodes.ButtonKind.GHOST, CONNECT, "Connect")));
    }

    private void classicEvent(WindowHandle window, UiEvent event) {
        if (closed) return;
        switch (event) {
            case UiEvent.Selected chosen when chosen.id().equals(CHOICE) -> {
                String id = chosen.ids().stream().findFirst().orElse(NONE_OPTION);
                if (id.equals(MANAGE_OPTION)) showHosts.accept(window);
                else if (!id.equals(NONE_OPTION)) selection.set(window.id(), Optional.of(id));
                renderViews();
            }
            case UiEvent.Action action when action.id().equals(CONNECT) -> connect(window);
            default -> { }
        }
    }

    /** Connect: activates the window's host (focus a running pane, else open one) or opens its distribution. */
    void connect(WindowHandle window) {
        current(window).ifPresent(row -> {
            if (row.startsWith("host:")) store.host(UUID.fromString(row.substring("host:".length()))).ifPresent(host -> activate.accept(window, host));
            else distros.get().stream().filter(profile -> ("distro:" + profile.id()).equals(row)).findFirst()
                .ifPresent(profile -> openDistro.accept(window, profile));
        });
    }

    /** A valid action id for any profile id: distribution names may hold capitals and dots, which action ids may not. */
    static String distroActionId(String profileId) {
        return "dev.jasper.remote.session.w" + UUID.nameUUIDFromBytes(profileId.getBytes(StandardCharsets.UTF_8));
    }

    private static String actionId(UUID id) { return "dev.jasper.remote.session.h" + id; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (placement != null) placement.close();
        if (viewPlacement != null) viewPlacement.close();
        views.clear();
        actions.values().forEach(PluginAction::close);
        actions.clear();
        distroActions.values().forEach(PluginAction::close);
        distroActions.clear();
        manage.close();
    }
}
