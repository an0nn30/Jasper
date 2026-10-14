package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.settingsui.AppearanceChoices;
import dev.jasper.app.settingsui.InstalledThemes;
import dev.jasper.app.settingsui.ThemeImports;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** The Appearance page's values: the installed themes with their previews, the workspace glass and the live OS appearance. */
final class SettingsAppearance {
    private SettingsAppearance() { }

    /** The choices over {@code library}, the installed themes as they are whenever the page reads them; {@code imports} adds the Imported themes group. */
    static AppearanceChoices choices(SystemAppearance system, Supplier<ThemeLibrary> library, Optional<ThemeImports> imports) {
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
            }, imports);
    }

    /**
     * The Imported themes group's hooks: the folder's file work on its own daemon thread, results on the EDT, the
     * imported themes from {@code library} as it is when the group reads them.
     */
    static ThemeImports imports(ThemeFolder folder, Supplier<ThemeLibrary> library, Function<AuxiliarySurface, Optional<Path>> chooseFile,
                                BiConsumer<AuxiliarySurface, ThemeImports.Question> confirm, Function<SettingsEdit, CompletableFuture<?>> save,
                                Supplier<CompletableFuture<?>> reload) {
        return new ThemeImports(folder, () -> imported(library.get()), chooseFile, confirm, save, reload,
            work -> Thread.ofPlatform().daemon().name("jasper-theme-files").start(work), SwingUtilities::invokeLater);
    }

    /** The library's imported themes as the group lists them. */
    static List<ThemeImports.Imported> imported(ThemeLibrary library) {
        return library.imported().stream().map(theme -> new ThemeImports.Imported(theme.id(), library.label(theme), offers(theme))).toList();
    }

    /**
     * What a theme offers: "UI theme · Dark", "UI theme · Light" or "UI theme · Dark and Light", with ", N terminal
     * schemes" when it has schemes beyond its variants' own; a terminal-only theme is "Terminal scheme" or "Terminal schemes".
     */
    static String offers(JasperTheme theme) {
        int schemes = theme.schemes().size();
        if (!theme.has(true) && !theme.has(false)) return schemes == 1 ? "Terminal scheme" : "Terminal schemes";
        String variants = theme.has(true) && theme.has(false) ? "Dark and Light" : theme.has(true) ? "Dark" : "Light";
        int own = (theme.has(true) ? 1 : 0) + (theme.has(false) ? 1 : 0);
        return "UI theme \u00b7 " + variants + (schemes > own ? ", " + schemes + " terminal schemes" : "");
    }

    /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
    static InstalledThemes themeChoices(ThemeLibrary library) {
        java.util.function.Function<Boolean, java.util.List<InstalledThemes.ThemeOption>> themes = dark -> library.themes(dark).stream()
            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark))).toList();
        return new InstalledThemes(themes.apply(true), themes.apply(false), library.schemes().stream()
            .map(scheme -> new InstalledThemes.SchemeOption(scheme.id().toString(), library.label(scheme))).toList(),
            library.themes().stream().collect(java.util.stream.Collectors.toMap(JasperTheme::id, library::label)));
    }

    /** A theme variant's preview: its input, sidebar and app surfaces, text and accent. */
    static Option.Palette preview(JasperTheme theme, boolean dark) {
        var palette = theme.palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }
}
