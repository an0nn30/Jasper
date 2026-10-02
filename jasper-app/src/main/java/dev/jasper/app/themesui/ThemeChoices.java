package dev.jasper.app.themesui;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure filtering of catalog winners; never changes either requested theme. */
final class ThemeChoices {
    enum Tone { ALL, DARK, LIGHT;
        @Override public String toString() { return switch (this) { case ALL -> "All"; case DARK -> "Dark"; case LIGHT -> "Light"; }; }
    }
    enum Source { ALL, BUNDLED, MINE;
        @Override public String toString() { return switch (this) { case ALL -> "All"; case BUNDLED -> "Bundled"; case MINE -> "Mine"; }; }
    }
    private ThemeChoices() {}
    static List<Theme> filter(ThemeCatalog.Snapshot catalog, String query, Tone tone, Source source) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return catalog.themes().stream()
            .filter(theme -> theme.name().toLowerCase(Locale.ROOT).contains(needle))
            .filter(theme -> tone == Tone.ALL || theme.dark() == (tone == Tone.DARK))
            .filter(theme -> source == Source.ALL || (theme.source() instanceof Theme.Bundled) == (source == Source.BUNDLED))
            .sorted(Comparator.comparing(Theme::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Theme::name))
            .toList();
    }
}
