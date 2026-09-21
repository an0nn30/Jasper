package dev.jasper.sdk;

/** A gated call by a plugin that did not declare the capability, or whose user did not consent to it. */
public final class MissingCapabilityException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** The calling plugin. */
    private final String pluginId;
    /** The capability it lacks. */
    private final String capability;

    /**
     * Creates the exception.
     *
     * @param pluginId the calling plugin
     * @param capability the capability it lacks
     */
    public MissingCapabilityException(String pluginId, String capability) {
        super("Plugin " + pluginId + " needs the capability " + capability + "; declare it in plugin.toml");
        this.pluginId = pluginId;
        this.capability = capability;
    }

    /**
     * The calling plugin.
     *
     * @return its id
     */
    public String pluginId() { return pluginId; }

    /**
     * The missing capability.
     *
     * @return its name
     */
    public String capability() { return capability; }
}
