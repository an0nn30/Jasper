package dev.jasper.ui.metrics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TypographyTest {
    @Test void selectedResultsKeepEveryRoleFamilyAndSizeAndRestoreDeclaredWeight() {
        for (var typography : java.util.List.of(new Typography(), new Typography(java.awt.Font.MONOSPACED, 32)))
            for (var role : Typography.Role.values()) {
                var base = typography.font(role); var selected = typography.resultFont(role, true);
                assertThat(selected.getFamily()).isEqualTo(base.getFamily());
                assertThat(selected.getSize2D()).isEqualTo(base.getSize2D());
                assertThat(selected.isBold()).isTrue();
                assertThat(typography.resultFont(role, false)).isEqualTo(base);
            }
    }

    @Test void measuredMediumPolicyPreservesUserFamiliesAndProportionalSizes() {
        var user = new Typography(java.awt.Font.MONOSPACED, 26);
        assertThat(user.font(Typography.Role.HERO_TITLE).getFamily()).isEqualTo(java.awt.Font.MONOSPACED);
        assertThat(user.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(30);
        assertThat(user.font(Typography.Role.HERO_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isNull();
        assertThat(new Typography("Helvetica Neue", 19).font(Typography.Role.BODY).getSize2D()).isEqualTo(19);
        var face = new java.awt.Font("HelveticaNeue-Medium", java.awt.Font.PLAIN, 1);
        if (face.getFamily(java.util.Locale.ROOT).equals("Helvetica Neue")) {
            assertThat(new Typography("Helvetica Neue", 19).font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            if (new Typography().family().equals("Helvetica Neue"))
                assertThat(new Typography().font(Typography.Role.BODY).getFamily()).isEqualTo(new java.awt.Font(".AppleSystemUIFont", java.awt.Font.PLAIN, 13).getFamily());
            var calibrated = new Typography("Helvetica Neue", 26).font(Typography.Role.HERO_TITLE);
            assertThat(calibrated.getFontName(java.util.Locale.ROOT)).isEqualTo(face.getFontName(java.util.Locale.ROOT));
            assertThat(calibrated.getSize2D()).isEqualTo(30);
            assertThat(calibrated.getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
        }
    }

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
