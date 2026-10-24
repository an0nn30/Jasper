package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.appearance.DesktopPalette;
import dev.jasper.app.settingsui.DesktopChoices;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** The Appearance page's values: the installed themes with their previews, the workspace glass and the live OS appearance. */
final class SettingsAppearance {
    private SettingsAppearance() { }

    /** The choices over {@code library}, the installed themes as they are whenever the page reads them; {@code imports} adds the theme gears. */
    static AppearanceChoices choices(SystemAppearance system, Supplier<ThemeLibrary> library, Optional<ThemeImports> imports) {
        return choices(system, library, imports, null);
    }

    /** Adds Linux's runtime source and retains preview requests only while an Appearance page needs them. */
    static AppearanceChoices choices(SystemAppearance system, Supplier<ThemeLibrary> library, Optional<ThemeImports> imports,
                                     DesktopAppearance desktop) {
        var desktopChoices = desktop == null ? DesktopChoices.UNAVAILABLE : desktopChoices(desktop);
        var glass = JasperTheme.bundled().generated(true).glass();
        var cache = new Object() { ThemeLibrary from; InstalledThemes themes; };
        return new AppearanceChoices((float) (glass.opacity() * 100), glass.blur(),
            () -> switch (system.current().preference()) {
                case DARK -> Optional.of(true);
                case LIGHT -> Optional.of(false);
                case UNAVAILABLE -> Optional.empty();
            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())), () -> {
                var current = library.get();
                if (cache.from != current) { cache.themes = themeChoices(current, desktopChoices); cache.from = current; }
                return cache.themes;
            }, imports, desktopChoices);
    }

    private record PreviewKey(String source, boolean dark) { }

    /** Cached preview results are page demand data; the owner's current result remains the applied status. */
    private static DesktopChoices desktopChoices(DesktopAppearance desktop) {
        var cached = new HashMap<PreviewKey, Option.Palette>();
        var listeners = new LinkedHashSet<Runnable>();
        return new DesktopChoices(true, () -> desktop.current().status(),
            (source, dark) -> cached.getOrDefault(new PreviewKey(source, dark), preview(JasperTheme.builtIn("slate-cobalt").orElseThrow(), dark)),
            listener -> {
                listeners.add(listener);
                var applied = new Subscription(desktop.subscribe(listener));
                return new Subscription(() -> { listeners.remove(listener); applied.close(); });
            }, (source, dark) -> {
                var key = new PreviewKey(source, dark);
                var active = new boolean[] { true };
                var demand = desktop.preview(DesktopPalette.SourceMode.valueOf(source.toUpperCase(Locale.ROOT)), dark, result -> {
                    if (!active[0]) return;
                    var palette = result.theme().semantic();
                    var next = new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(),
                        palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(), palette.color(SemanticColor.SURFACE_APP).getRGB(),
                        palette.color(SemanticColor.TEXT_PRIMARY).getRGB(), palette.color(SemanticColor.ACCENT).getRGB());
                    if (!next.equals(cached.put(key, next)))
                        for (Runnable listener : List.copyOf(listeners)) if (listeners.contains(listener)) listener.run();
                });
                return new Subscription(() -> {
                    active[0] = false;
                    try { demand.close(); }
                    catch (Exception failure) { throw new IllegalStateException("Could not release desktop preview", failure); }
                });
            });
    }

    /** The dynamic option follows authored built-ins, before plugin and imported themes. */
    private static InstalledThemes themeChoices(ThemeLibrary library, DesktopChoices desktop) {
        var authored = themeChoices(library);
        if (!desktop.available()) return authored;
        var builtin = JasperTheme.builtIns().stream().map(JasperTheme::id).collect(java.util.stream.Collectors.toSet());
        java.util.function.BiFunction<List<InstalledThemes.ThemeOption>, Boolean, List<InstalledThemes.ThemeOption>> add = (options, dark) -> {
            var result = new ArrayList<>(options);
            int after = 0;
            while (after < result.size() && builtin.contains(result.get(after).id())) after++;
            result.add(after, new InstalledThemes.ThemeOption("desktop-colors", "Desktop colors", desktop.preview().apply("auto", dark)));
            return List.copyOf(result);
        };
        var names = new HashMap<>(authored.names()); names.put("desktop-colors", "Desktop colors");
        return new InstalledThemes(add.apply(authored.dark(), true), add.apply(authored.light(), false), authored.schemes(), names);
    }

    /** The theme gears' hooks: the folder's file work on its own daemon thread, results on the EDT. */
    static ThemeImports imports(ThemeFolder folder, Function<AuxiliarySurface, Optional<Path>> chooseFile,
                                BiConsumer<AuxiliarySurface, ThemeImports.Question> confirm, BiConsumer<AuxiliarySurface, ThemeImports.Notice> inform,
                                Function<SettingsEdit, CompletableFuture<?>> save, Supplier<CompletableFuture<?>> reload) {
        return new ThemeImports(folder, chooseFile, confirm, inform, save, reload,
            work -> Thread.ofPlatform().daemon().name("jasper-theme-files").start(work), SwingUtilities::invokeLater);
    }

    /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
    static InstalledThemes themeChoices(ThemeLibrary library) {
        java.util.function.Function<Boolean, java.util.List<InstalledThemes.ThemeOption>> themes = dark -> library.themes(dark).stream()
            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark), library.provider(theme.id()).orElse(""))).toList();
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
