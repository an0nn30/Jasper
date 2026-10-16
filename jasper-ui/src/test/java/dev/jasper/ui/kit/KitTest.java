package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.1: a kit names a look's catalog, type, faces, control variants, colour slots and FlatLaf keys. */
class KitTest {
    private static Kit kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots colors, UiKeySet keys) {
        return new Kit(id, metrics, type, faces, controls, colors, keys);
    }

    @Test void theDefaultKitIsTodaysLook() {
        assertThat(Kit.DEFAULT.id()).isEqualTo("default");
        assertThat(Kit.DEFAULT.metrics()).isSameAs(DesignMetrics.DEFAULT);
        assertThat(Kit.DEFAULT.type()).isSameAs(TypeScale.DEFAULT);
        assertThat(Kit.DEFAULT.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.DEFAULT.controls()).isSameAs(ControlVariants.DEFAULT);
        assertThat(Kit.DEFAULT.colors()).isSameAs(ColorSlots.DEFAULT);
        assertThat(Kit.DEFAULT.uiKeys()).isEqualTo(UiKeySet.DEFAULT);
    }

    @Test void theClassicKitIsTheClassicBase() {
        assertThat(Kit.CLASSIC.id()).isEqualTo("classic");
        assertThat(Kit.CLASSIC.metrics()).isSameAs(DesignMetrics.CLASSIC);
        assertThat(Kit.CLASSIC.type()).isSameAs(TypeScale.CLASSIC);
        assertThat(Kit.CLASSIC.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.CLASSIC.controls()).isSameAs(ControlVariants.CLASSIC);
        assertThat(Kit.CLASSIC.colors()).isSameAs(ColorSlots.CLASSIC);
        assertThat(Kit.CLASSIC.uiKeys()).isEqualTo(UiKeySet.CLASSIC);
    }

    @Test void everyPartIsRequired() {
        var m = DesignMetrics.DEFAULT; var t = TypeScale.DEFAULT; var f = FaceSet.PLATFORM; var c = ControlVariants.DEFAULT;
        var s = ColorSlots.DEFAULT; var k = UiKeySet.DEFAULT;
        assertThatNullPointerException().isThrownBy(() -> kit(null, m, t, f, c, s, k)).withMessage("id");
        assertThatNullPointerException().isThrownBy(() -> kit("k", null, t, f, c, s, k)).withMessage("metrics");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, null, f, c, s, k)).withMessage("type");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, null, c, s, k)).withMessage("faces");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, null, s, k)).withMessage("controls");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, c, null, k)).withMessage("colors");
        assertThatNullPointerException().isThrownBy(() -> kit("k", m, t, f, c, s, null)).withMessage("uiKeys");
        assertThatIllegalArgumentException().isThrownBy(() -> kit(" ", m, t, f, c, s, k)).withMessage("Blank kit id");
    }

    @Test void kitsCompareByValue() {
        var same = kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 1), TypeScale.DEFAULT.withBody(13),
            FaceSet.PLATFORM, ControlVariants.DEFAULT, ColorSlots.DEFAULT, UiKeySet.DEFAULT);
        assertThat(same).isEqualTo(Kit.DEFAULT).hasSameHashCodeAs(Kit.DEFAULT).isNotSameAs(Kit.DEFAULT);
        assertThat(kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, ControlVariants.CLASSIC, ColorSlots.DEFAULT,
            UiKeySet.DEFAULT)).isNotEqualTo(Kit.DEFAULT);
    }
}
