package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.1: a kit names a look's catalog, type, faces and FlatLaf keys. */
class KitTest {
    @Test void theDefaultKitIsTodaysCatalogTypeFacesAndKeys() {
        assertThat(Kit.DEFAULT.id()).isEqualTo("default");
        assertThat(Kit.DEFAULT.metrics()).isSameAs(DesignMetrics.DEFAULT);
        assertThat(Kit.DEFAULT.type()).isSameAs(TypeScale.DEFAULT);
        assertThat(Kit.DEFAULT.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.DEFAULT.uiKeys()).isEqualTo(UiKeySet.DEFAULT);
    }

    @Test void everyPartIsRequired() {
        assertThatNullPointerException().isThrownBy(() -> new Kit(null, DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("id");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", null, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("metrics");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, null, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("type");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, TypeScale.DEFAULT, null, UiKeySet.DEFAULT)).withMessage("faces");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, null)).withMessage("uiKeys");
        assertThatIllegalArgumentException().isThrownBy(() -> new Kit(" ", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT))
            .withMessage("Blank kit id");
    }

    @Test void kitsCompareByValue() {
        var same = new Kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 1), TypeScale.DEFAULT.withBody(13),
            FaceSet.PLATFORM, UiKeySet.DEFAULT);
        assertThat(same).isEqualTo(Kit.DEFAULT).hasSameHashCodeAs(Kit.DEFAULT).isNotSameAs(Kit.DEFAULT);
        assertThat(new Kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 2), TypeScale.DEFAULT, FaceSet.PLATFORM,
            UiKeySet.DEFAULT)).isNotEqualTo(Kit.DEFAULT);
    }
}
