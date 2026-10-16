package dev.jasper.ui.metrics;

import java.awt.font.FontRenderContext;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DesignMetricsTest {
    private static Typography named(String family, float size) {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM,
            new Typography.UserFont(java.util.Optional.of(family), java.util.Optional.of(size)));
    }

    @Test void fixedLogicalGeometryIsIndependentOfDisplayScale() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        assertThat(m.value(DesignMetrics.Role.PROMPT_FIELD_HEIGHT)).isEqualTo(28);
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
        var body = named("SansSerif", 13).font(Typography.Role.BODY);
        int line = (int) Math.ceil(body.getLineMetrics("Ag", frc).getHeight());
        assumeTrue(line <= 16, "a 13-pt line of at most 16 px");
        var m = DesignMetrics.DEFAULT;
        assertThat(m.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc)).isEqualTo(24);
        assertThat(m.contentHeight(DesignMetrics.Role.MENU_ITEM_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.PICKER_ROW_HEIGHT, body, frc)).isEqualTo(22);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_ROW, body, frc)).isEqualTo(30);
        assertThat(m.contentHeight(DesignMetrics.Role.SETTINGS_NAV_HEADING, body, frc)).isEqualTo(22);
    }

    @Test void enlargedUserFontGetsEnoughContentBoundsWithoutChangingCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var normal = named("SansSerif", 13);
        var large = named("SansSerif", 18);
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
        assertThat(normal.font(Typography.Role.PROMPT_TITLE).getSize2D()).isEqualTo(15);
        assertThat(large.font(Typography.Role.MONO).getFamily()).isEqualTo("Monospaced");
    }
    @Test void invalidFontSettingsAreRejected() {
        assertThatThrownBy(() -> named("", 13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> named("SansSerif", Float.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A catalog is a value with no public constructor: every catalog derives from DEFAULT. */
    @Test void catalogsComeOnlyFromTheDefaultCatalog() {
        assertThat(DesignMetrics.class.getConstructors()).isEmpty();
    }

    /** {@code with} copies one role and leaves its source and every other role alone; equal catalogs are equal values. */
    @Test void withCopiesOneRole() {
        var tall = DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, 30);
        assertThat(tall.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(30);
        assertThat(DesignMetrics.DEFAULT.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        for (var role : DesignMetrics.Role.values())
            if (role != DesignMetrics.Role.BUTTON_HEIGHT)
                assertThat(tall.value(role)).as(role.name()).isEqualTo(DesignMetrics.DEFAULT.value(role));
        assertThat(tall).isNotEqualTo(DesignMetrics.DEFAULT);
        var same = DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, 24);
        assertThat(same).isEqualTo(DesignMetrics.DEFAULT).hasSameHashCodeAs(DesignMetrics.DEFAULT).isNotSameAs(DesignMetrics.DEFAULT);
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.TAB_SWITCH_MILLIS, 0).value(DesignMetrics.Role.TAB_SWITCH_MILLIS)).isZero();
        assertThatThrownBy(() -> DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, -1))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Negative BUTTON_HEIGHT: -1");
        assertThatThrownBy(() -> DesignMetrics.DEFAULT.with(null, 1)).isInstanceOf(NullPointerException.class);
    }

    /** Content heights read the catalog they are asked of: a derived floor or padding moves them. */
    @Test void contentHeightFollowsTheCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var body = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13);
        int line = (int) Math.ceil(body.getLineMetrics("Ag", frc).getHeight());
        int padding = DesignMetrics.DEFAULT.value(DesignMetrics.Role.FIELD_VERTICAL_PADDING);
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.FIELD_HEIGHT, 40).contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc))
            .isEqualTo(Math.max(40, line + padding));
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.FIELD_VERTICAL_PADDING, 20).contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc))
            .isEqualTo(Math.max(24, line + 20));
    }
}
