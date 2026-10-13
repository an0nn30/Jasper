package dev.jasper.app.settingsui;

import dev.jasper.ui.model.Option;
import java.util.List;
import java.util.Objects;

/**
 * What the Appearance page offers, read again whenever the page refreshes: the themes for each appearance and every
 * terminal scheme, in order.
 *
 * @param dark the themes with a Dark variant, Slate Steel first
 * @param light the themes with a Light variant, Slate Steel first
 * @param schemes every installed terminal scheme, Slate Steel's first
 */
public record InstalledThemes(List<ThemeOption> dark, List<ThemeOption> light, List<SchemeOption> schemes) {
    public InstalledThemes { dark = List.copyOf(dark); light = List.copyOf(light); schemes = List.copyOf(schemes); }

    /** A theme for one appearance: its id, its label and its tile preview. */
    public record ThemeOption(String id, String label, Option.Palette preview) {
        public ThemeOption { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(preview, "preview"); }
    }

    /** A terminal scheme: its value in config.toml and its label. */
    public record SchemeOption(String key, String label) {
        public SchemeOption { Objects.requireNonNull(key, "key"); Objects.requireNonNull(label, "label"); }
    }

    /** Slate Steel alone, for pages built without an application: these previews and its three schemes. */
    public static InstalledThemes slateSteel(Option.Palette dark, Option.Palette light) {
        return new InstalledThemes(List.of(new ThemeOption("slate-steel", "Slate Steel", dark)),
            List.of(new ThemeOption("slate-steel", "Slate Steel", light)),
            List.of(new SchemeOption("slate-steel/dark", "Slate Steel Dark"), new SchemeOption("slate-steel/light", "Slate Steel Light"),
                new SchemeOption("slate-steel/high-contrast", "High Contrast")));
    }
}
