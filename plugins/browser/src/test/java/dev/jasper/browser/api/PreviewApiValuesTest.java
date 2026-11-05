package dev.jasper.browser.api;

import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.TerminalTypography;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The values the preview API takes and reports: what each refuses, and what a {@code Geometry} holds. */
class PreviewApiValuesTest {
    private static final WorkspacePalette PALETTE = new WorkspacePalette(Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.BLACK,
        Color.DARK_GRAY, Color.GRAY, Color.BLUE, Color.WHITE, Color.LIGHT_GRAY, Color.BLUE, Color.GREEN, Color.ORANGE, Color.RED);
    private static final SyntaxPalette SYNTAX = syntax();
    private static final TerminalTypography TYPOGRAPHY = new TerminalTypography("JetBrains Mono", 13, List.of("Menlo"), true, 1.2);

    @Test void aThemeRefusesNullsAndABlankTextFamily() {
        var theme = new PreviewTheme(Variant.DARK, PALETTE, SYNTAX, "Inter", TYPOGRAPHY);
        assertThat(theme.textFamily()).isEqualTo("Inter");
        assertThatThrownBy(() -> new PreviewTheme(null, PALETTE, SYNTAX, "Inter", TYPOGRAPHY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreviewTheme(Variant.LIGHT, null, SYNTAX, "Inter", TYPOGRAPHY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreviewTheme(Variant.LIGHT, PALETTE, null, "Inter", TYPOGRAPHY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreviewTheme(Variant.LIGHT, PALETTE, SYNTAX, null, TYPOGRAPHY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreviewTheme(Variant.LIGHT, PALETTE, SYNTAX, "Inter", null)).isInstanceOf(NullPointerException.class);
        for (String blank : new String[] {"", " ", "\t\n"})
            assertThatThrownBy(() -> new PreviewTheme(Variant.LIGHT, PALETTE, SYNTAX, blank, TYPOGRAPHY))
                .as("'" + blank + "'").isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aFindRefusesANullOrEmptyQuery() {
        var find = new PreviewFind(" ", true, false, true);
        assertThat(find.query()).as("a space is something to find").isEqualTo(" ");
        assertThat(find.matchCase()).isTrue();
        assertThat(find.wholeWord()).isFalse();
        assertThat(find.regex()).isTrue();
        assertThatThrownBy(() -> new PreviewFind(null, false, false, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreviewFind("", false, false, false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unavailableRefusesANullReason() {
        assertThat(new PreviewEvent.Unavailable("Chromium stopped").reason()).isEqualTo("Chromium stopped");
        assertThatThrownBy(() -> new PreviewEvent.Unavailable(null)).isInstanceOf(NullPointerException.class);
    }

    @Test void geometryCopiesItsArrayAndReadsNanAsNoBox() {
        double[] tops = {0, Double.NaN, 48.5};
        var geometry = new PreviewEvent.Geometry(3, tops, 900, 400);
        tops[0] = 99;
        assertThat(geometry.revision()).isEqualTo(3);
        assertThat(geometry.blocks()).isEqualTo(3);
        assertThat(geometry.top(0)).as("copied, not shared").isEqualTo(OptionalDouble.of(0));
        assertThat(geometry.top(1)).as("a block with no box").isEmpty();
        assertThat(geometry.top(2)).isEqualTo(OptionalDouble.of(48.5));
        assertThat(geometry.top(3)).as("past the last block").isEmpty();
        assertThat(geometry.top(-1)).isEmpty();
        assertThat(geometry.height()).isEqualTo(900);
        assertThat(geometry.viewport()).isEqualTo(400);
        assertThat(geometry).isEqualTo(new PreviewEvent.Geometry(3, new double[] {0, Double.NaN, 48.5}, 900, 400))
            .hasSameHashCodeAs(new PreviewEvent.Geometry(3, new double[] {0, Double.NaN, 48.5}, 900, 400))
            .isNotEqualTo(new PreviewEvent.Geometry(4, new double[] {0, Double.NaN, 48.5}, 900, 400))
            .isNotEqualTo(new PreviewEvent.Geometry(3, new double[] {0, 1, 48.5}, 900, 400));
        assertThat(new PreviewEvent.Geometry(1, new double[0], 0, 0).blocks()).isZero();
        assertThatThrownBy(() -> new PreviewEvent.Geometry(1, null, 0, 0)).isInstanceOf(NullPointerException.class);
    }

    private static SyntaxPalette syntax() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (SyntaxRole role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(Color.BLACK, false, false));
        return new SyntaxPalette(styles);
    }
}
