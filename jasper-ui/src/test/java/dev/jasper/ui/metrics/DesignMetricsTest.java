package dev.jasper.ui.metrics;

import java.awt.font.FontRenderContext;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DesignMetricsTest {
    @Test void fixedLogicalGeometryIsIndependentOfDisplayScale() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(28);
        assertThat(m.value(DesignMetrics.Role.HERO_FIELD_HEIGHT)).isEqualTo(32);
        assertThat(m.value(DesignMetrics.Role.FORM_LABEL_WIDTH)).isEqualTo(96);
        assertThat(m.value(DesignMetrics.Role.COLUMN_GAP)).isEqualTo(12);
        assertThat(m.value(DesignMetrics.Role.CONTROL_RADIUS)).isEqualTo(6);
        assertThat(m.value(DesignMetrics.Role.CARD_RADIUS)).isEqualTo(9);
        assertThat(m.value(DesignMetrics.Role.WINDOW_RADIUS)).isEqualTo(12);
        assertThat(m.value(DesignMetrics.Role.FOCUS_RING)).isEqualTo(3);
    }
    @Test void enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var normal = new Typography("SansSerif", 13);
        var large = new Typography("SansSerif", 18);
        var font = large.font(Typography.Role.BODY);
        int height = DesignMetrics.DEFAULT.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, font, frc);
        assertThat(height).isGreaterThan(28);
        assertThat(height).isGreaterThanOrEqualTo((int)Math.ceil(font.getLineMetrics("Ag", frc).getHeight()) + 12);
        assertThat(normal.font(Typography.Role.LABEL).getSize2D()).isEqualTo(12.5f);
        assertThat(normal.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(15);
        assertThat(large.font(Typography.Role.MONO).getFamily()).isEqualTo("Monospaced");
    }
    @Test void invalidFontSettingsAreRejected() {
        assertThatThrownBy(() -> new Typography("", 13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Typography("SansSerif", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
