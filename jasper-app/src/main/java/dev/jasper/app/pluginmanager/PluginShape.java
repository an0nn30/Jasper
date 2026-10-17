package dev.jasper.app.pluginmanager;

/**
 * How the Plugins page is composed (spec 12.2), as {@link PluginsPage} maps the look's composition once.
 *
 * @param checkboxRows rows lead with their enable checkbox and show no plugin icon; the list is one boxed lane holding its
 *     search; the details show no identity icon
 * @param ellipsisActions the plugin's actions are a ⋯ beside its primary action rather than a More actions menu
 */
public record PluginShape(boolean checkboxRows, boolean ellipsisActions) {
    /** Today's Plugins page. */
    public static final PluginShape ROWS = new PluginShape(false, false);
    /** The classic Plugins page of the Settings mocks. */
    public static final PluginShape CLASSIC = new PluginShape(true, true);
}
