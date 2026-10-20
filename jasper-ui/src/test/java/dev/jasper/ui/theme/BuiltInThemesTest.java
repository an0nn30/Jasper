package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The built-in themes: Slate Cobalt, then Catppuccin's four flavours converted from its IntelliJ plugin (tools/themes/catppuccin). */
class BuiltInThemesTest {
    @Test void slateCobaltComesFirstThenCatppuccinsFlavours() {
        assertThat(JasperTheme.builtIns()).extracting(JasperTheme::id)
            .containsExactly("slate-cobalt", "catppuccin-latte", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha", "ubuntu-yaru", "jasper-meadow");
        assertThat(JasperTheme.builtIns()).extracting(JasperTheme::name)
            .containsExactly("Slate Cobalt", "Catppuccin Latte", "Catppuccin Frappé", "Catppuccin Macchiato", "Catppuccin Mocha", "Ubuntu Yaru", "Jasper Meadow Light");
        assertThat(JasperTheme.builtIns().getFirst()).isSameAs(JasperTheme.bundled());
    }

    @Test void latteIsLightAndTheOtherFlavoursAreDarkEachWithItsOwnScheme() {
        for (var theme : JasperTheme.builtIns().subList(1, 5)) {
            boolean dark = !theme.id().equals("catppuccin-latte");
            assertThat(theme.has(dark)).as(theme.id()).isTrue();
            assertThat(theme.has(!dark)).as(theme.id()).isFalse();
            assertThat(theme.schemes()).as(theme.id()).extracting(TerminalScheme::name).containsExactly(theme.name());
            assertThat(theme.author()).as(theme.id()).isEqualTo("Catppuccin Org <releases@catppuccin.com>");
            assertThat(theme.generated(dark).semantic().color(SemanticColor.SURFACE_TERMINAL))
                .as("%s: the terminal surface meets the text area", theme.id()).isEqualTo(theme.matching(dark).colors().background());
        }
    }

    @Test void aBuiltInThemeIsFoundByItsIdAndNoOtherIdIsBuiltIn() {
        for (var theme : JasperTheme.builtIns()) assertThat(JasperTheme.builtIn(theme.id())).as(theme.id()).containsSame(theme);
        assertThat(JasperTheme.builtIn("ocean")).isEmpty();
        assertThat(JasperTheme.builtIn("Catppuccin-Mocha")).isEmpty();
    }

    /** The vendored Catppuccin plugin sets five of the IntelliJ keys the converter reads; Slate Cobalt authors none. */
    @Test void catppuccinAuthorsFiveDerivedRolesAndSlateCobaltNone() {
        for (boolean dark : new boolean[] {true, false}) assertThat(JasperTheme.bundled().generated(dark).derivedOverrides()).isEmpty();
        for (var theme : JasperTheme.builtIns().subList(1, 5)) {
            var variant = theme.generated(!theme.id().equals("catppuccin-latte"));
            assertThat(variant.derivedOverrides()).as(theme.id()).containsOnlyKeys(DerivedColor.SURFACE_HEADER,
                DerivedColor.SURFACE_HEADER_ACTIVE, DerivedColor.SURFACE_ROW_HOVER, DerivedColor.WARNING_GROUND, DerivedColor.WARNING_BORDER);
            assertThat(DerivedPalette.of(variant).color(DerivedColor.SURFACE_HEADER_ACTIVE)).as("%s: as authored, one with the header", theme.id())
                .isEqualTo(DerivedPalette.of(variant).color(DerivedColor.SURFACE_HEADER));
        }
    }
}
