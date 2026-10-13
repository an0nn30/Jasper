package dev.jasper.ui.metrics;

import java.awt.font.FontRenderContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DesignMetricsTest {
    @Test void fixedLogicalGeometryIsIndependentOfDisplayScale() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.HERO_FIELD_HEIGHT)).isEqualTo(28);
        assertThat(m.value(DesignMetrics.Role.MENU_ITEM_HEIGHT)).isEqualTo(22);
        assertThat(m.value(DesignMetrics.Role.FORM_LABEL_WIDTH)).isEqualTo(96);
        assertThat(m.value(DesignMetrics.Role.COLUMN_GAP)).isEqualTo(10);
        assertThat(m.value(DesignMetrics.Role.CONTROL_RADIUS)).isEqualTo(4);
        assertThat(m.value(DesignMetrics.Role.CARD_RADIUS)).isEqualTo(6);
        assertThat(m.value(DesignMetrics.Role.WINDOW_RADIUS)).isEqualTo(8);
        assertThat(m.value(DesignMetrics.Role.FOCUS_RING)).isEqualTo(3);
        assertThat(m.value(DesignMetrics.Role.HAIRLINE)).isEqualTo(1);
    }

    /** The whole scale, frozen: a retune edits compact-roles.tsv in the same change. */
    @Test void everyRoleHasItsCompactValue() throws Exception {
        var table = new LinkedHashMap<String, Integer>();
        try (var in = DesignMetricsTest.class.getResourceAsStream("compact-roles.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], Integer.parseInt(cells[1]));
            }
        }
        assertThat(Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : DesignMetrics.Role.values())
            assertThat(DesignMetrics.DEFAULT.value(role)).as(role.name()).isEqualTo(table.get(role.name()));
    }

    @Test void aThirteenPointLineMakesTheCompactHeights() {
        var frc = new FontRenderContext(null, true, true);
        var body = new Typography("SansSerif", 13).font(Typography.Role.BODY);
        int line = (int) Math.ceil(body.getLineMetrics("Ag", frc).getHeight());
        assumeTrue(line <= 16, "a 13-pt line of at most 16 px");
        var m = DesignMetrics.DEFAULT;
        assertThat(m.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.MENU_ITEM_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.PICKER_ROW_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_ROW, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_HEADING, body, frc)).isEqualTo(22);
    }

    @Test void enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var normal = new Typography("SansSerif", 13);
        var large = new Typography("SansSerif", 18);
        var font = large.font(Typography.Role.BODY);
        int line = (int) Math.ceil(font.getLineMetrics("Ag", frc).getHeight());
        var m = DesignMetrics.DEFAULT;
        assertThat(m.contentPadding(DesignMetrics.Role.MENU_ITEM_HEIGHT)).isEqualTo(DesignMetrics.Role.ROW_VERTICAL_PADDING);
        assertThat(m.contentPadding(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(DesignMetrics.Role.BUTTON_VERTICAL_PADDING);
        assertThat(m.contentPadding(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(DesignMetrics.Role.FIELD_VERTICAL_PADDING);
        assertThat(m.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, font, frc)).isGreaterThan(24)
            .isEqualTo(Math.max(24, line + m.value(DesignMetrics.Role.FIELD_VERTICAL_PADDING)));
        assertThat(m.contentHeight(DesignMetrics.Role.MENU_ITEM_HEIGHT, font, frc)).isGreaterThan(22)
            .isEqualTo(Math.max(22, line + m.value(DesignMetrics.Role.ROW_VERTICAL_PADDING)));
        assertThat(m.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, font, frc)).isGreaterThan(24);
        assertThatThrownBy(() -> m.contentPadding(DesignMetrics.Role.CARD_RADIUS)).isInstanceOf(IllegalArgumentException.class);
        assertThat(normal.font(Typography.Role.LABEL).getSize2D()).isEqualTo(12.5f);
        assertThat(normal.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(15);
        assertThat(large.font(Typography.Role.MONO).getFamily()).isEqualTo("Monospaced");
    }
    @Test void invalidFontSettingsAreRejected() {
        assertThatThrownBy(() -> new Typography("", 13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Typography("SansSerif", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
