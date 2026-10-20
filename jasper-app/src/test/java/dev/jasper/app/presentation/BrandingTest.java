package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.testsupport.BrandingFixtures;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Spec 5.1, 9.1 and 10.11: a look's branding is its product name, seven icons and splash art, held as bytes. */
class BrandingTest {
    static Map<Integer, byte[]> icons() {
        var icons = new TreeMap<Integer, byte[]>();
        for (int size : Branding.SIZES) icons.put(size, BrandingFixtures.png(size, size));
        return icons;
    }

    @Test void noBrandingIsJaspersIconAndNoSplash() {
        assertThat(Branding.NONE.present()).isFalse();
        assertThat(Branding.NONE.product()).isEmpty();
        assertThat(Branding.NONE.iconFiles()).isEmpty();
        assertThat(Branding.NONE.files()).isEmpty();
        assertThat(Branding.NONE.splashArt()).isEmpty();
        assertThatThrownBy(() -> Branding.NONE.icon(16)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Branding.NONE).hasToString("Branding.NONE");
    }

    @Test void aBrandingHoldsCopiesInSizeOrderAndComparesByContent() {
        var icons = icons();
        byte[] art = BrandingFixtures.svg();
        var branding = Branding.of("JasperOps", icons, art);
        assertThat(branding.present()).isTrue();
        assertThat(branding.product()).isEqualTo("JasperOps");
        assertThat(branding.iconFiles()).hasSize(7);
        assertThat(branding.iconFiles().getFirst()).isEqualTo(BrandingFixtures.png(16, 16));
        assertThat(branding.iconFiles().getLast()).isEqualTo(BrandingFixtures.png(1024, 1024));
        assertThat(branding.files()).hasSize(8);
        assertThat(branding.files().getLast()).isEqualTo(BrandingFixtures.svg());
        icons.get(16)[0] = 9;
        art[0] = 9;
        assertThat(branding.icon(16)).as("a copy of what it was given").isEqualTo(BrandingFixtures.png(16, 16));
        assertThat(branding.splashArt()).isEqualTo(BrandingFixtures.svg());
        branding.icon(16)[0] = 1;
        assertThat(branding.icon(16)).as("a copy each time").isEqualTo(BrandingFixtures.png(16, 16));
        var same = Branding.of("JasperOps", icons(), BrandingFixtures.svg());
        assertThat(branding).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(branding).isNotEqualTo(Branding.of("Other", icons(), BrandingFixtures.svg()));
        assertThat(branding.toString()).contains("JasperOps").doesNotContain("[B@");
    }

    @Test void aPartMissingIsRefused() {
        assertThatThrownBy(() -> Branding.of(" ", icons(), BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Branding.of("x".repeat(33), icons(), BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        var missing = icons();
        missing.remove(48);
        assertThatThrownBy(() -> Branding.of("JasperOps", missing, BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        var extra = icons();
        extra.put(512, BrandingFixtures.png(512, 512));
        assertThatThrownBy(() -> Branding.of("JasperOps", extra, BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Branding.of("JasperOps", icons(), new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
