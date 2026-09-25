package dev.jasper.app.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FontConfigTest {
    @Test void lineHeightAcceptsOneHalfToThreeAndRejectsTheRest() {
        for (float height : new float[]{.5f, .7f, 1f, 3f})
            assertThat(new FontConfig("Mono", 16f, List.of(), true, height).lineHeight()).isEqualTo(height);
        for (float height : new float[]{.49f, 3.01f, Float.NaN, Float.POSITIVE_INFINITY})
            assertThatIllegalArgumentException().isThrownBy(() -> new FontConfig("Mono", 16f, List.of(), true, height))
                .withMessageContaining("0.5");
    }
}
