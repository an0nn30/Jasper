package dev.jasper.app.palette;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

/** Actual hosted bundled plugin scopes; fixture history/snippets paths are configured before startup. */
public final class ThemeMigrationPaletteCapture {
    private ThemeMigrationPaletteCapture() { }
    public static void capture(List<PaletteScope> scopes) throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var themes = ThemeTestSupport.controller(true);
            themes.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            var kit = themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            for (String family : List.of("history", "snippets")) {
                var scope = scopes.stream().filter(value -> value.id().startsWith("dev.jasper." + family)).findFirst().orElseThrow();
                var palette = new CommandPalette(kit, true, query -> { }, (entry, verb) -> { }, prefix -> Optional.empty(), selected -> { });
                palette.setChip(scope.label(), scope.icon()); palette.setPlaceholder(scope.placeholder());
                var context = new PaletteContext(true, PaletteTarget.window(java.util.UUID.fromString("00000000-0000-0000-0000-000000000003")));
                var results = scope.search("", context);
                if (results.rows().isEmpty()) throw new AssertionError("Synthetic " + family + " data not loaded");
                palette.setEntries(results.rows().stream().map(row -> (PaletteEntry) new PaletteEntry.Item(scope, row)).toList(), null, "No matches");
                save(family + "-palette", theme, "populated", palette, 680, 360);
                dev.jasper.app.palette.PaletteTestSupport.query(palette).setText("fixture-no-match");
                var empty = scope.search("fixture-no-match", context);
                if (!empty.rows().isEmpty()) throw new AssertionError("Expected fixture-only filtered empty");
                palette.setEntries(List.of(), null, "No matches");
                save(family + "-palette", theme, "filtered-empty", palette, 680, 360);
                palette.close();
            }
        });
    }
}
