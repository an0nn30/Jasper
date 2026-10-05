package dev.jasper.ui.theme;

import dev.jasper.ui.themeio.ThemeToml;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SemanticPaletteTest {
    @Test void incompleteSemanticPaletteIsRejected() {
        assertThatThrownBy(() -> new SemanticPalette(Map.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void everySemanticNameRoundTrips() {
        assertThat(SemanticColor.values()).hasSize(48);
        assertThat(SemanticColor.of("surface.status")).contains(SemanticColor.SURFACE_STATUS);
        for (var role : SemanticColor.values()) assertThat(SemanticColor.of(role.key())).contains(role);
        assertThat(SemanticColor.of("unknown")).isEmpty();
    }

    @Test void paletteCopiesInputAndRejectsNullColors() {
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, Color.RED);
        var palette = new SemanticPalette(values);
        values.put(SemanticColor.ACCENT, Color.GREEN);
        assertThat(palette.color(SemanticColor.ACCENT)).isEqualTo(Color.RED);
        values.put(SemanticColor.ACCENT, null);
        assertThatThrownBy(() -> new SemanticPalette(values)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void finalCanonicalParentsDriveDependentDefaults() {
        var theme = ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS + """
            [tokens]
            accent = "#ff0000"
            surface_sunken = "#010203"
            [semantic]
            accent = "#00ff00"
            "surface.card" = "#aabbcc80"
            "text.primary" = "#000000"
            "status.danger" = "#123456"
            "text.link" = "#abcdef"
            "text.onAccent" = "#ffeedd"
            """);
        var p = theme.semantic();
        assertThat(p.color(SemanticColor.ACCENT)).isEqualTo(Color.GREEN);
        assertThat(p.color(SemanticColor.BORDER_FOCUS)).isEqualTo(Color.GREEN);
        assertThat(p.color(SemanticColor.ACCENT_HOVER)).isEqualTo(ColorMath.mix(Color.BLACK, Color.GREEN, .08));
        assertThat(p.color(SemanticColor.BORDER_ERROR)).isEqualTo(new Color(0x123456));
        assertThat(p.color(SemanticColor.TEXT_LINK_HOVER)).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xabcdef), .08));
        assertThat(p.color(SemanticColor.HOVER_OVERLAY)).isEqualTo(Color.BLACK);
        assertThat(p.color(SemanticColor.SURFACE_CARD)).isEqualTo(new Color(0xaa, 0xbb, 0xcc, 0x80));
        assertThat(p.color(SemanticColor.TEXT_ONACCENT_SECONDARY)).isEqualTo(new Color(0xff, 0xee, 0xdd, 153));
    }

    @Test void dependentOverridesWinAndInheritanceMergesCanonicalKeys() {
        var parent = ThemeToml.parse(ThemeGeneratorTest.SEEDS + """
            [semantic]
            accent = "#ff0000"
            "border.focus" = "#abcdef80"
            "surface.card" = "#112233"
            """, "parent.toml");
        var child = ThemeToml.parse("""
            name = "Child"
            extends = "Mocha"
            [semantic]
            accent = "#00ff00"
            """, "child.toml");
        var resolved = ThemeResolver.resolve(child, name -> List.of(parent));
        var palette = ThemeGenerator.generate(resolved).semantic();
        assertThat(palette.color(SemanticColor.ACCENT)).isEqualTo(Color.GREEN);
        assertThat(palette.color(SemanticColor.BORDER_FOCUS)).isEqualTo(new Color(0xab, 0xcd, 0xef, 0x80));
        assertThat(palette.color(SemanticColor.ACCENT_HOVER)).isEqualTo(ColorMath.mix(palette.color(SemanticColor.TEXT_PRIMARY), Color.GREEN, .08));
        assertThat(palette.color(SemanticColor.SURFACE_CARD)).isEqualTo(new Color(0x112233));
    }

    @Test void partialLegacyThemesCoverEveryRoleAndKeepPaintAlphaOutOfBaseColors() {
        var theme = ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS.replace("#f38ba8", "#000000"));
        for (var role : SemanticColor.values()) assertThat(theme.semantic().color(role)).as(role.key()).isNotNull();
        Color scrim = theme.semantic().color(SemanticColor.SURFACE_SCRIM);
        assertThat(scrim).isEqualTo(Color.BLACK);
        assertThat(ColorMath.opacity(scrim, .55).getAlpha()).isEqualTo(140);
        assertThat(ColorMath.opacity(theme.semantic().color(SemanticColor.HUE_BLUE), .16).getAlpha()).isEqualTo(41);
        assertThat(ColorMath.opacity(theme.semantic().color(SemanticColor.BORDER_FOCUS), .25).getAlpha()).isEqualTo(64);
    }
}
