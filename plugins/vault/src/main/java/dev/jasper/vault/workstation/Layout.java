package dev.jasper.vault.workstation;

import dev.jasper.enterprise.api.Workstation;
import dev.jasper.sdk.services.Services;

/**
 * Which composition the Credential Vault's views take (Enterprise presentation spec 14.3): {@link #CLASSIC} while the
 * Enterprise look publishes {@link Workstation}, else {@link #ROWS}. The only Credential Vault code that names the
 * Enterprise API.
 */
public enum Layout {
    /** Today's rows, cards and switches. */
    ROWS,
    /** The 07 Credential Vault mocks' classic manager, editors and Unlock form. */
    CLASSIC;

    /**
     * The layout for this run. Enterprise is an optional requirement: when it is not installed its API class is not
     * visible to this plugin, which reads as {@link #ROWS}.
     *
     * @param services the plugin's service registry
     * @return {@link #CLASSIC} while Workstation is published, else {@link #ROWS}
     */
    public static Layout of(Services services) {
        try { return services.find(Workstation.class).isPresent() ? CLASSIC : ROWS; }
        catch (NoClassDefFoundError absent) { return ROWS; }
    }
}
