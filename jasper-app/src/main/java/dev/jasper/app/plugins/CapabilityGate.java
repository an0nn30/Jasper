package dev.jasper.app.plugins;

import dev.jasper.sdk.MissingCapabilityException;
import java.util.Set;

/**
 * One plugin's capabilities. The resolver loads a user plugin only when the user consented to everything
 * it declares, and bundled and development plugins are consented in advance, so the declared set is the
 * granted set. The audit log names the plugin and the amount, never the content.
 */
final class CapabilityGate {
    private static final System.Logger AUDIT = System.getLogger("dev.jasper.app.plugins.audit");
    private final String pluginId;
    private final Set<String> granted;

    CapabilityGate(String pluginId, Set<String> granted) { this.pluginId = pluginId; this.granted = Set.copyOf(granted); }

    boolean has(String capability) { return granted.contains(capability); }

    void require(String capability) {
        if (!granted.contains(capability)) throw new MissingCapabilityException(pluginId, capability);
    }

    void audit(String capability, String what) {
        AUDIT.log(System.Logger.Level.INFO, "Plugin " + pluginId + " used " + capability + ": " + what);
    }
}
