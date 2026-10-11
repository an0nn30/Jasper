package dev.jasper.app.application;

import dev.jasper.app.appearance.*;
import dev.jasper.app.config.ThemeSource;
import dev.jasper.app.settingsui.ThemeChoices;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.*;
import java.util.*;

/** Plain source previews for Settings; a catalog revision is resolved once and never renamed or inverted. */
final class SettingsThemeSources implements java.util.function.Supplier<List<ThemeChoices.Source>> {
    private final ThemeCatalog catalog;
    private ThemeCatalog.Snapshot snapshot;
    private List<ThemeChoices.Source> sources = List.of();
    SettingsThemeSources(ThemeCatalog catalog) { this.catalog = catalog; }
    @Override public List<ThemeChoices.Source> get() {
        var next = catalog.latest(); if (next == snapshot) return sources;
        var list = new ArrayList<ThemeChoices.Source>();
        next.themes().stream().sorted(Comparator.comparing(Theme::name,String.CASE_INSENSITIVE_ORDER)).forEach(theme -> list.add(source(theme)));
        // A bundled identity shadowed by an installed name is still an explicitly selectable real source.
        for (var bundled : ThemeManager.builtIns())
            if (next.find(bundled.name()).filter(bundled::equals).isEmpty()) list.add(source(bundled));
        sources = List.copyOf(list); snapshot = next; return sources;
    }
    private static ThemeChoices.Source source(Theme theme) {
        var generated = ThemeGenerator.generate(theme.source().document()); var p = generated.semantic();
        return new ThemeChoices.Source(theme.name(),theme.source() instanceof Theme.Bundled?ThemeSource.BUNDLED:ThemeSource.CATALOG,theme.dark(),
            new Option.Palette(p.color(SemanticColor.SURFACE_APP).getRGB(),p.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),p.color(SemanticColor.SURFACE_CONTROL).getRGB(),p.color(SemanticColor.TEXT_PRIMARY).getRGB(),p.color(SemanticColor.ACCENT).getRGB()),
            (float)(generated.glass().opacity()*100),generated.glass().blur());
    }
}
