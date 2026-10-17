package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A generated variant carries the derived roles it authors (spec 7.2): only the overridable ones, opaque, copied. */
class GeneratedThemeTest {
    private static GeneratedTheme with(Map<DerivedColor, Color> overrides) {
        var base = KitFixtures.light();
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), overrides);
    }

    @Test void aGeneratedThemeTakesOnlyOpaqueOverridesOfTheOverridableRoles() {
        assertThatThrownBy(() -> with(Map.of(DerivedColor.BORDER_ROW, Color.RED)))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Not an override key: border.row");
        assertThatThrownBy(() -> with(Map.of(DerivedColor.SURFACE_HEADER, new Color(1, 2, 3, 4))))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Translucent override: surface.header");
        assertThatThrownBy(() -> with(null)).isInstanceOf(NullPointerException.class);
    }

    @Test void theOverridesAreCopiedAndImmutable() {
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        overrides.put(DerivedColor.SURFACE_HEADER, Color.RED);
        var theme = with(overrides);
        overrides.clear();
        assertThat(theme.derivedOverrides()).containsOnlyKeys(DerivedColor.SURFACE_HEADER);
        assertThatThrownBy(() -> theme.derivedOverrides().put(DerivedColor.SURFACE_ZEBRA, Color.BLUE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(KitFixtures.light().derivedOverrides()).as("Slate Cobalt authors none").isEmpty();
    }
}
