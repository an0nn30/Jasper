package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.settingsui.AppearanceChoices;
import dev.jasper.app.settingsui.InstalledThemes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import java.util.Optional;

/** The Appearance page's values: the installed themes with their previews, the workspace glass and the live OS appearance. */
final class SettingsAppearance {
    private SettingsAppearance() { }

    /** The choices over {@code library}, the installed themes as they are whenever the page reads them. */
    static AppearanceChoices choices(SystemAppearance system, java.util.function.Supplier<ThemeLibrary> library) {
        var glass = JasperTheme.bundled().generated(true).glass();
        var cache = new Object() { ThemeLibrary from; InstalledThemes themes; };
        return new AppearanceChoices((float) (glass.opacity() * 100), glass.blur(),
            () -> switch (system.current().preference()) {
                case DARK -> Optional.of(true);
                case LIGHT -> Optional.of(false);
                case UNAVAILABLE -> Optional.empty();
            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())), () -> {
                var current = library.get();
                if (cache.from != current) { cache.themes = themeChoices(current); cache.from = current; }
                return cache.themes;
            }, Optional.empty());
    }

    /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
    static InstalledThemes themeChoices(ThemeLibrary library) {
        java.util.function.Function<Boolean, java.util.List<InstalledThemes.ThemeOption>> themes = dark -> library.themes(dark).stream()
            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark))).toList();
        return new InstalledThemes(themes.apply(true), themes.apply(false), library.schemes().stream()
            .map(scheme -> new InstalledThemes.SchemeOption(scheme.id().toString(), library.label(scheme))).toList());
    }

    /** A theme variant's preview: its input, sidebar and app surfaces, text and accent. */
    static Option.Palette preview(JasperTheme theme, boolean dark) {
        var palette = theme.palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }
}
