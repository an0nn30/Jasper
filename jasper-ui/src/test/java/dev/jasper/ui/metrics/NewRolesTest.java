package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.4 and 5.5: the roles plan 4 adds start at today's values, so nothing moves. */
class NewRolesTest {
    static final List<String> ADDED = List.of("ROW_HEIGHT", "FIELD_RADIUS", "RUN_WINDOW_WIDTH", "CHECKBOX_BOX", "CHECKBOX_RADIUS",
        "CHECKBOX_LABEL_GAP", "RADIO_RING", "RADIO_DOT", "RADIO_LABEL_GAP", "RADIO_GAP", "OPTION_SWATCH", "GROUP_TITLE_INSET",
        "GROUP_TITLE_PADDING", "GROUP_TITLE_GAP", "SPINNER_WIDTH", "SPINNER_ARROW_WIDTH", "SPINNER_GLYPH", "DROPDOWN_GLYPH",
        "DROPDOWN_GLYPH_INSET", "TABLE_HEADER_HEIGHT", "TREE_INDENT", "DISCLOSURE_GLYPH", "DISCLOSURE_GAP", "FOLDER_TAB_HEIGHT",
        "FOLDER_TAB_PADDING", "FOLDER_TAB_RADIUS", "ROUTE_EDGE", "SIDE_COLUMN_WIDTH", "SIDE_COLUMN_GAP", "SIDE_BUTTON_GAP",
        "STRIPE_LABEL_PADDING", "STRIPE_LABEL_GAP", "DOCK_RULE", "DOCK_RULE_HIT",
        "RUN_WINDOW_RADIUS", "RUN_WINDOW_TITLE_HEIGHT", "RUN_WINDOW_TITLE_SIDE", "RUN_WINDOW_CLOSE",
        "RUN_WINDOW_CLOSE_SIDE", "RUN_WINDOW_SHORTCUT_GAP", "RUN_WINDOW_SIDE", "RUN_WINDOW_HELP_TOP", "RUN_WINDOW_TILE",
        "RUN_WINDOW_TILE_GLYPH", "RUN_WINDOW_TILE_GAP", "RUN_WINDOW_HELP_LINE", "RUN_WINDOW_FIELD_TOP",
        "RUN_WINDOW_LABEL_COLUMN", "RUN_WINDOW_FIELD_SIDE", "RUN_WINDOW_MENU_WIDTH", "RUN_WINDOW_LIST_TOP",
        "RUN_WINDOW_ROW_HEIGHT", "RUN_WINDOW_HEADING_HEIGHT", "RUN_WINDOW_ROW_SIDE", "RUN_WINDOW_SWATCH",
        "RUN_WINDOW_ROW_GAP", "RUN_WINDOW_DETAIL_GAP", "RUN_WINDOW_DETAIL_TOP", "RUN_WINDOW_DETAIL_LABEL_GAP",
        "RUN_WINDOW_CHECK_TOP", "RUN_WINDOW_BOTTOM", "RUN_WINDOW_FOOTER_HEIGHT", "RUN_WINDOW_BUTTON_WIDTH",
        "RUN_WINDOW_BUTTON_GAP");

    /** Plan 4's roles are appended together, in order; a later plan's roles may follow them. */
    @Test void theAddedRolesFollowEachOtherInOrder() {
        var roles = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        assertThat(java.util.Collections.indexOfSubList(roles, ADDED)).isNotNegative();
    }

    @Test void theRowFloorAndTheFieldRadiusStartAtTheRolesTheyReplace() {
        var m = DesignMetrics.DEFAULT;
        assertThat(m.value(DesignMetrics.Role.ROW_HEIGHT)).isEqualTo(m.value(DesignMetrics.Role.BUTTON_HEIGHT));
        assertThat(m.value(DesignMetrics.Role.FIELD_RADIUS)).isEqualTo(m.value(DesignMetrics.Role.CONTROL_RADIUS));
        assertThat(m.contentPadding(DesignMetrics.Role.ROW_HEIGHT)).isEqualTo(m.contentPadding(DesignMetrics.Role.BUTTON_HEIGHT));
    }

    /** Review Focus 4: a large line grows a row exactly as it grew the button-height floor. */
    @Test void rowHeightGrowsWithTheLineLikeTheFloorItReplaces() {
        var frc = new FontRenderContext(null, true, true);
        for (float size : new float[] {12, 13, 18, 24}) {
            var font = new Font("SansSerif", Font.PLAIN, 1).deriveFont(size);
            assertThat(DesignMetrics.DEFAULT.contentHeight(DesignMetrics.Role.ROW_HEIGHT, font, frc)).as("%s pt", size)
                .isEqualTo(DesignMetrics.DEFAULT.contentHeight(DesignMetrics.Role.BUTTON_HEIGHT, font, frc));
        }
    }

    /** Review Focus 4: the classic catalog's 22 px rows grow past 22 with a large line, by the button padding. */
    @Test void classicRowsGrowWithTheLine() {
        var frc = new FontRenderContext(null, true, true);
        var classic = DesignMetrics.CLASSIC;
        assertThat(classic.value(DesignMetrics.Role.ROW_HEIGHT)).isEqualTo(22);
        for (float size : new float[] {12, 18, 24}) {
            var font = new Font("SansSerif", Font.PLAIN, 1).deriveFont(size);
            int line = (int) Math.ceil(font.getLineMetrics("Ag", frc).getHeight());
            assertThat(classic.contentHeight(DesignMetrics.Role.ROW_HEIGHT, font, frc)).as("%s pt", size)
                .isEqualTo(Math.max(22, line + classic.value(DesignMetrics.Role.BUTTON_VERTICAL_PADDING)));
        }
        var large = new Font("SansSerif", Font.PLAIN, 1).deriveFont(24f);
        assertThat(classic.contentHeight(DesignMetrics.Role.ROW_HEIGHT, large, frc)).as("24 pt").isGreaterThan(22);
    }
}
