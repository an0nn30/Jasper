package dev.jasper.sdk.ui;

import dev.jasper.sdk.JasperSdk;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.Variant;
import java.util.Arrays;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppearanceContractTest {
    @Test void bundledCatalogIsAnAdditiveAppearanceOverload() throws Exception {
        assertThat(Appearance.class.getMethod("icon", BundledIcon.class).isDefault()).isTrue();
        assertThat(BundledIcon.values()).hasSize(885);
        assertThat(BundledIcon.GENERAL_SEARCH.id()).isEqualTo("general/search");
    }

    @Test void appearanceOffersOneIconFamilyInSdkZeroEight() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.14.0");
        assertThat(Arrays.stream(Appearance.class.getMethods()).filter(method -> method.getName().equals("icon"))
            .map(method -> method.getParameterCount())).containsOnly(1);
    }

    @Test void olderImplementationsInheritANamedIconDefaultThatRefusesEveryName() {
        Icon original = new ImageIcon();
        Appearance legacy = new Appearance() {
            public Variant variant() { return Variant.LIGHT; }
            public Subscription onChanged(Consumer<Variant> handler) { return () -> {}; }
            public Icon icon(String path) { return original; }
        };
        assertThat(legacy.icon("icon.svg")).isSameAs(original);
        assertThatThrownBy(() -> legacy.icon(IconName.LOCK)).isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> legacy.icon((IconName) null));
        assertThatThrownBy(() -> legacy.icon(BundledIcon.GENERAL_ADD)).isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> legacy.icon((BundledIcon) null));
    }

    @Test void theCatalogHoldsFortySevenMeaningsIncludingTheChromeNames() {
        assertThat(IconName.values()).hasSize(47)
            .contains(IconName.SPLIT, IconName.ZOOM, IconName.TERMINAL, IconName.SERVER);
    }
}
