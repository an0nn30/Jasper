package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import java.util.List;
import java.util.Set;

/**
 * A validated {@code plugin.toml}. {@code presentation} is the jar path of the plugin's kit file ({@code ""} for none),
 * {@code themes} the jar paths of its Jasper theme files, and {@code enabledByDefault} false keeps a bundled plugin off
 * until the user turns it on.
 */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                        dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands,
                        String presentation, List<String> themes, boolean enabledByDefault) {
    /** The form that predates presentations: no kit, no themes, on by default. */
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                     dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage, iconHue,
            iconSelectedInk, subcommands, "", List.of(), true);
    }
    /** The form that predates subcommands. */
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                     dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage, iconHue,
            iconSelectedInk, List.of());
    }
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue) {
        this(id,name,version,entry,sdk,description,vendor,capabilities,exports,requires,icon,homepage,iconHue,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED);
    }
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage,
             dev.jasper.ui.model.Nodes.Hue.NEUTRAL);
    }
    record Requirement(String id, VersionRange version, boolean optional) { }

    /** A {@code [[subcommands]]} entry: {@code jasper <name>} reaches this plugin; {@code summary} is its help line. */
    record Subcommand(String name, String summary) { }

    PluginDescriptor {
        capabilities = Set.copyOf(capabilities);
        exports = Set.copyOf(exports);
        requires = List.copyOf(requires);
        subcommands = List.copyOf(subcommands);
        java.util.Objects.requireNonNull(presentation, "presentation");
        themes = List.copyOf(themes);
    }

    /** Whether the plugin provides a presentation. */
    boolean declaresPresentation() { return !presentation.isEmpty(); }

    /** Each declared theme's id: its file name without {@code .json}, in declared order. */
    List<String> themeIds() {
        return themes.stream().map(path -> path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length())).toList();
    }

    PluginInfo info() { return new PluginInfo(id, name, version.toString(), capabilities); }
}
