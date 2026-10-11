package dev.jasper.app.application;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ThemeSource;
import dev.jasper.app.platform.SystemAppearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One source-resolution rule for startup, saved configuration, catalog refresh and OS observations. */
final class ThemePolicy {
    record Resolved(Theme theme, List<String> problems) { Resolved { problems = List.copyOf(problems); } }
    private ThemePolicy() { }
    static Optional<Theme> manual(String name, ThemeSource source, ThemeCatalog.Snapshot catalog) {
        return source == ThemeSource.CATALOG ? catalog.find(name) : ThemeManager.builtIns().stream().filter(theme -> theme.name().equalsIgnoreCase(name.strip())).findFirst();
    }
    static Resolved resolve(ConfigSnapshot config, ThemeCatalog.Snapshot catalog, SystemAppearance.Observation observation, boolean lastDark) {
        var problems = new ArrayList<String>();
        if (config.followSystemTheme()) {
            switch (observation.preference()) {
                case DARK: return new Resolved(Theme.DARK, List.of());
                case LIGHT: return new Resolved(Theme.JASPER_LIGHT, List.of());
                case UNAVAILABLE: problems.add("Automatic system appearance is unavailable: " + observation.reason() + " Using the remembered manual theme.");
            }
        }
        Theme theme = manual(config.theme(), config.themeSource(), catalog).orElse(null);
        if (theme == null) {
            theme = lastDark ? Theme.DARK : Theme.JASPER_LIGHT;
            problems.add("Unknown " + (config.themeSource() == ThemeSource.BUNDLED ? "bundled " : "") + "theme \"" + config.theme() + "\"; using " + theme.name() + ". Available: " + String.join(", ", (config.themeSource()==ThemeSource.BUNDLED?ThemeManager.builtIns():catalog.themes()).stream().map(Theme::name).sorted(String.CASE_INSENSITIVE_ORDER).toList()) + ".");
        }
        return new Resolved(theme, problems);
    }
}
