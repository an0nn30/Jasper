package dev.jasper.app.platform;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.icons.IconRegistration;
import dev.jasper.ui.swing.KitContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import javax.swing.Icon;

/** Receiving-view lifetime for immutable presentation artwork. No plugin owner, resource loader or paint callback
 * crosses into the kit registry. The receiver keeps both generations alive while reconciling its model and closes the registry with its view.
 */
public final class PresentationIcons implements AutoCloseable {
    private final IconRegistry registry = new IconRegistry();
    private record Snapshot(String name, String path) {}
    private record Registered(String name, IconRegistration lifetime) {}
    private final Map<Snapshot, Registered> registrations = new HashMap<>();
    private Set<Snapshot> requested;
    private long nextAlias;
    public KitContext context(KitContext source) {
        return KitContext.of(dev.jasper.ui.swing.FlatLafInstaller.installedKit(), source.binding(), source.route(), registry,
            source.animationsEnabled(), source.shortcuts());
    }
    public Optional<String> name(Icon icon) {
        if (!(icon instanceof PresentationIcon named)) return AppIcons.kitName(icon);
        if (named.pathData() == null) return Optional.of(named.name());
        var snapshot = new Snapshot(named.name(), named.pathData());
        var registered = registrations.computeIfAbsent(snapshot, this::register);
        if (requested != null) requested.add(snapshot);
        return Optional.of(registered.name());
    }
    private Registered register(Snapshot snapshot) {
        String name = snapshot.name();
        var occupied = registry.names();
        // Old and new immutable paths may share a semantic name. Give the new snapshot a receiver-local
        // registry name while old controls still refer to the previous one; never overwrite their artwork.
        while (occupied.contains(name)) name = snapshot.name() + ".snapshot-" + ++nextAlias;
        return new Registered(name, registry.register(name, snapshot.path()));
    }

    /** Reconciliation may refresh old controls before installing their new descriptors. Retain both generations
     * until success. A failure may have partially changed a view, so retain their union until a later successful
     * reconciliation or close; do not attempt to roll back a control tree here.
     */
    public void reconcile(Runnable update) {
        if (requested != null) throw new IllegalStateException("Reentrant icon reconciliation");
        var next = new HashSet<Snapshot>();
        requested = next;
        try {
            update.run();
            registrations.entrySet().removeIf(entry -> {
                if (next.contains(entry.getKey())) return false;
                entry.getValue().lifetime().close();
                return true;
            });
        } finally { requested = null; }
    }
    /** Clears a detached receiver; mounted receivers use reconcile so their previous controls stay paintable. */
    public void reset() { registrations.values().forEach(entry -> entry.lifetime().close()); registrations.clear(); }
    @Override public void close() { reset(); registry.close(); }
}
