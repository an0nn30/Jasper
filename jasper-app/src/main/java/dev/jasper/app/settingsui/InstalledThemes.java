package dev.jasper.app.settingsui;

import dev.jasper.ui.model.Option;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the Appearance page offers, read again whenever the page refreshes: the themes for each appearance and every
 * terminal scheme, in order.
 *
 * @param dark the themes with a Dark variant, Slate Cobalt first
 * @param light the themes with a Light variant, Slate Cobalt first
 * @param schemes every installed terminal scheme, Slate Cobalt's first
 * @param names the label of every installed theme by its id, including a theme that has neither a Dark nor a Light variant
 */
public record InstalledThemes(List<ThemeOption> dark, List<ThemeOption> light, List<SchemeOption> schemes, Map<String, String> names) {
    public InstalledThemes { dark = List.copyOf(dark); light = List.copyOf(light); schemes = List.copyOf(schemes); names = Map.copyOf(names); }

    /** A theme for one appearance: its id, its label and its tile preview. */
    public record ThemeOption(String id, String label, Option.Palette preview) {
        public ThemeOption { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(preview, "preview"); }
    }

    /** A terminal scheme: its value in config.toml and its label. */
    public record SchemeOption(String key, String label) {
        public SchemeOption { Objects.requireNonNull(key, "key"); Objects.requireNonNull(label, "label"); }
    }

    /** Slate Cobalt alone, for pages built without an application: these previews and its three schemes. */
    public static InstalledThemes slateCobalt(Option.Palette dark, Option.Palette light) {
        return new InstalledThemes(List.of(new ThemeOption("slate-cobalt", "Slate Cobalt", dark)),
            List.of(new ThemeOption("slate-cobalt", "Slate Cobalt", light)),
            List.of(new SchemeOption("slate-cobalt/dark", "Slate Cobalt Dark"), new SchemeOption("slate-cobalt/light", "Slate Cobalt Light"),
                new SchemeOption("slate-cobalt/high-contrast", "High Contrast")), Map.of("slate-cobalt", "Slate Cobalt"));
    }
}
