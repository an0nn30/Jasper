package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.JasperTheme;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopThemeSelectionTest {
    @TempDir Path home;

    @Test void desktopMatchUsesBundledTerminalScheme() {
        var pick = new ThemePick.Desktop();
        assertThat(pick.id()).isEqualTo("desktop-colors");
        assertThat(pick.matching(true)).isEqualTo(JasperTheme.bundled().matching(true));
        assertThat(pick.matching(false)).isEqualTo(JasperTheme.bundled().matching(false));
    }

    private ThemeSelection selection(ThemeLibrary library, String dark, String light, TerminalSchemeChoice scheme) {
        return ThemeSelection.of(library, dark, light, scheme, (key, message) -> fail(key + ": " + message));
    }

    @Test void bothDynamicPicksUseTheSuppliedGeneratedThemeAndBundledFallback() {
        var selection = selection(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors", TerminalSchemeChoice.MATCH);
        for (boolean dark : new boolean[]{true, false}) {
            assertThat(selection.theme(dark)).isInstanceOf(ThemePick.Desktop.class);
            var generated = JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(dark);
            assertThat(ResolvedTheme.of(dark, selection, generated).chrome()).isSameAs(generated);
            assertThat(ResolvedTheme.of(dark, selection, generated).workspace()).isSameAs(generated);
            assertThat(ResolvedTheme.of(dark, selection).chrome()).isSameAs(JasperTheme.bundled().generated(dark));
        }
    }

    @Test void mixedPicksGenerateAuthoredAndDesktopIndependently() {
        var selection = selection(ThemeLibrary.bundledOnly(), "desktop-colors", "jasper-meadow", TerminalSchemeChoice.MATCH);
        var desktop = JasperTheme.bundled().generated(true);
        assertThat(selection.theme(true)).isInstanceOf(ThemePick.Desktop.class);
        assertThat(selection.theme(false)).isEqualTo(new ThemePick.Authored(JasperTheme.builtIn("jasper-meadow").orElseThrow()));
        assertThat(ResolvedTheme.of(false, selection, desktop).chrome()).isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(false));
        assertThat(ResolvedTheme.of(true, selection, desktop).chrome()).isSameAs(desktop);
    }

    @Test void namedTerminalSchemeIsIndependentOfDesktopPick() {
        var named = TerminalSchemeChoice.parse("catppuccin-mocha/dark").orElseThrow();
        var selection = selection(ThemeLibrary.bundledOnly(), "desktop-colors", "desktop-colors", named);
        for (boolean dark : new boolean[]{true, false}) {
            assertThat(selection.terminal(dark).id().toString()).isEqualTo(named.key());
            assertThat(ResolvedTheme.of(dark, selection).palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(selection.terminal(dark)));
            assertThat(selection.withScheme(TerminalSchemeChoice.MATCH).terminal(dark)).isSameAs(JasperTheme.bundled().matching(dark));
        }
    }

    @Test void libraryRefreshPreservesDynamicIdentityWithoutInventingAuthoredThemesOrSchemes() throws Exception {
        var initial = ThemeLibrary.bundledOnly();
        var before = selection(initial, "desktop-colors", "desktop-colors", TerminalSchemeChoice.MATCH);
        Files.writeString(home.resolve("ocean.json"), ThemeLibraryTest.uiTheme("Ocean"));
        var library = ThemeLibrary.read(home, initial);
        var after = selection(library, before.dark().id(), before.light().id(), before.scheme());
        assertThat(after.dark()).isEqualTo(before.dark());
        assertThat(after.light()).isEqualTo(before.light());
        assertThat(after.library()).isNotSameAs(before.library());
        assertThat(library.theme("desktop-colors")).isEmpty();
        assertThat(JasperTheme.builtIns()).extracting(JasperTheme::id).doesNotContain("desktop-colors");
        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString()).doesNotHaveDuplicates()
            .noneMatch(id -> id.startsWith("desktop-colors/"));
    }
}
