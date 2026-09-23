package dev.jasper.remote;

import dev.jasper.remote.hosts.HostStore;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.terminal.WindowHandle;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.PluginAction;
import dev.jasper.sdk.ui.ToolbarItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.Icon;

/** Keeps the Sessions dropdown in sync with saved hosts. Owned by RemotePlugin on the UI thread. */
final class SessionsToolbar implements AutoCloseable {
    private static final String MANAGE = "dev.jasper.remote.sessions.manage";
    private final PluginContext context;
    private final HostStore store;
    private final Icon icon;
    private final BiConsumer<WindowHandle, RemoteHost> activate;
    private final PluginAction manage;
    private final Map<UUID, PluginAction> actions = new HashMap<>();
    private List<String> placed = List.of();
    private Subscription placement;

    SessionsToolbar(PluginContext context, HostStore store, Icon icon,
                    BiConsumer<WindowHandle, RemoteHost> activate, Consumer<WindowHandle> showHosts) {
        this.context = context; this.store = store; this.icon = icon; this.activate = activate;
        manage = context.actions().register(ActionSpec.of(MANAGE, "Manage Sessions...").withIcon(icon),
            invoked -> showHosts.accept(invoked.window()));
        refresh();
    }

    void refresh() {
        var hosts = store.hosts().stream().sorted(Comparator.comparing(RemoteHost::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RemoteHost::id)).toList();
        var ids = new ArrayList<String>();
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
        for (UUID id : List.copyOf(actions.keySet())) {
            if (store.host(id).isEmpty()) actions.remove(id).close();
        }
    }

    private static String actionId(UUID id) { return "dev.jasper.remote.session.h" + id; }

    @Override public void close() {
        if (placement != null) placement.close();
        actions.values().forEach(PluginAction::close);
        actions.clear();
        manage.close();
    }
}
