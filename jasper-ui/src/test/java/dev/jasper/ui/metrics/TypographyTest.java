package dev.jasper.ui.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TypographyTest {
    /**
     * The platform family is resolved once per process. Each installed-family lookup creates native font objects that
     * macOS never releases (about twenty CFonts per call); per-paint lookups exhausted the test heap after Task 5.
     */
    @Test void platformFamilyIsResolvedOnceNotPerTypography() {
        var first = new Typography();
        int lookups = Typography.platformLookups();
        for (int i = 0; i < 100; i++) assertThat(new Typography().family()).isEqualTo(first.family());
        assertThat(Typography.platformLookups()).isEqualTo(lookups).isLessThanOrEqualTo(1);
    }
}
