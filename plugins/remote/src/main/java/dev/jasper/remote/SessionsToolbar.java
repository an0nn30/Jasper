package dev.jasper.remote;

import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.ShellProfileInfo;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.ToolbarItem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Icon;

/**
 * Keeps the Sessions dropdown in sync with the WSL distributions and the saved hosts. Distributions come first, titled
 * with their WSL version because toolbar menus have no separators; hosts follow alphabetically. Owned by RemotePlugin on
 * the UI thread.
 */
final class SessionsToolbar implements AutoCloseable {
    private static final String MANAGE = "dev.jasper.remote.sessions.manage";
    private final PluginContext context;
    private final HostStore store;
    private final Icon icon;
    private final Icon distroIcon;
    private final BiConsumer<WindowHandle, RemoteHost> activate;
    private final Supplier<List<ShellProfileInfo>> distros;
    private final BiConsumer<WindowHandle, ShellProfileInfo> openDistro;
    private final PluginAction manage;
    private final Map<UUID, PluginAction> actions = new HashMap<>();
    private final Map<String, PluginAction> distroActions = new HashMap<>();
    private List<String> placed = List.of();
    private Subscription placement;

    SessionsToolbar(PluginContext context, HostStore store, Icon icon, BiConsumer<WindowHandle, RemoteHost> activate,
                    Consumer<WindowHandle> showHosts, Supplier<List<ShellProfileInfo>> distros,
                    BiConsumer<WindowHandle, ShellProfileInfo> openDistro) {
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;
        this.distros = distros; this.openDistro = openDistro;
        this.distroIcon = context.appearance().icon(IconName.TERMINAL);
        manage = context.actions().register(ActionSpec.of(MANAGE, "Manage Sessions...").withIcon(icon),
            invoked -> showHosts.accept(invoked.window()));
        refresh();
    }

    void refresh() {
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
        if (!placed.equals(ids)) {
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
    }

    /** A valid action id for any profile id: distribution names may hold capitals and dots, which action ids may not. */
    static String distroActionId(String profileId) {
        return "dev.jasper.remote.session.w" + UUID.nameUUIDFromBytes(profileId.getBytes(StandardCharsets.UTF_8));
    }

    private static String actionId(UUID id) { return "dev.jasper.remote.session.h" + id; }

    @Override public void close() {
        if (placement != null) placement.close();
        actions.values().forEach(PluginAction::close);
        actions.clear();
        distroActions.values().forEach(PluginAction::close);
        distroActions.clear();
        manage.close();
    }
}
