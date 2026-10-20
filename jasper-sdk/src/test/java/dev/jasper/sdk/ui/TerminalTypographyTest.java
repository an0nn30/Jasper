package dev.jasper.sdk.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalTypographyTest {
    @Test void copiesOrderedFallbackAndPreservesFractionalValues() {
        var names = new ArrayList<>(List.of("Monospaced", "DialogInput"));
        var value = new TerminalTypography("JetBrains Mono", 13.5, names, true, 1.25);
        names.clear();
        assertThat(value.fallback()).containsExactly("Monospaced", "DialogInput");
        assertThat(value.size()).isEqualTo(13.5);
        assertThat(value.lineHeight()).isEqualTo(1.25);
        assertThat(value.ligatures()).isTrue();
        assertThatThrownBy(() -> value.fallback().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsNonFiniteAndOutOfRangeDimensions() {
        for (double size : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 5.99, 72.01})
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalTypography("Mono", size, List.of(), false, 1));
        for (double height : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, .49, 3.01})
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalTypography("Mono", 12, List.of(), false, height));
        assertThat(new TerminalTypography("Mono", 6, List.of(), false, .5).size()).isEqualTo(6);
        assertThat(new TerminalTypography("Mono", 72, List.of(), false, 3).lineHeight()).isEqualTo(3);
    }

    @Test void rejectsBlankAndNulFamilyNames() {
        for (String name : new String[] {null, "", " \t", "Bad\0Font"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalTypography(name, 12, List.of(), false, 1));
            if (name != null)
                assertThatIllegalArgumentException().isThrownBy(() -> new TerminalTypography("Mono", 12, List.of(name), false, 1));
        }
        assertThatNullPointerException().isThrownBy(() -> new TerminalTypography("Mono", 12, null, false, 1));
    }
}
