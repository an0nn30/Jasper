package dev.jasper.ui.metrics;

import static dev.jasper.ui.metrics.DesignMetrics.Role.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.MetricTokens.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.4: the public tokens, their ranges and the private roles each sets. */
class MetricTokensTest {
    @Test void theTokensAreSpecFiveFoursElevenWithTheirKeysAndRanges() {
        assertThat(List.of(Token.values())).extracting(t -> t.key() + " " + t.min() + "-" + t.max()).containsExactly(
            "control_height 20-28", "row_height 18-28", "control_radius 0-4", "field_radius 0-4", "group_radius 0-6",
            "focus_ring 1-3", "tool_window_header 22-32", "group_padding 8-16", "dependent_indent 12-28",
            "navigation_width 180-300", "status_dot 5-9");
        assertThat(Token.CONTROL_HEIGHT.roles()).containsExactly(FIELD_HEIGHT, BUTTON_HEIGHT, PICKER_DEFAULT_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT,
            JOINED_ACTION_HEIGHT, PANEL_EMPTY_ACTION_HEIGHT, PANEL_SEARCH_HEIGHT, COLUMN_SEARCH_HEIGHT, KEYMAP_SEARCH_HEIGHT, FULL_SETTING_CHOICE_HEIGHT);
        assertThat(Token.ROW_HEIGHT.roles()).containsExactly(ROW_HEIGHT, MENU_ITEM_HEIGHT, PICKER_ROW_HEIGHT, RESULTS_ROW_HEIGHT,
            RESULTS_HEADING_HEIGHT, KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, ORDERED_ROW_HEIGHT, SETTINGS_NAV_ROW,
            SETTINGS_NAV_HEADING, COLUMN_RAIL_ROW, PANEL_COMPACT_ROW_HEIGHT);
        assertThat(Token.CONTROL_RADIUS.roles()).containsExactly(CONTROL_RADIUS, SEGMENT_TRACK_RADIUS, SEGMENT_ITEM_RADIUS,
            PICKER_SEARCH_RADIUS, KEYCAP_RADIUS, CHIP_RADIUS, ORDERED_RADIUS);
        assertThat(Token.FIELD_RADIUS.roles()).containsExactly(FIELD_RADIUS, TRANSPARENT_FIELD_RADIUS);
        assertThat(Token.GROUP_RADIUS.roles()).containsExactly(CARD_RADIUS);
        assertThat(Token.FOCUS_RING.roles()).containsExactly(FOCUS_RING);
        assertThat(Token.TOOL_WINDOW_HEADER.roles()).containsExactly(NATIVE_TOOL_HEADER_HEIGHT, PANEL_HEADER);
        assertThat(Token.GROUP_PADDING.roles()).containsExactly(CARD_PADDING, FULL_SETTING_SIDE_PADDING, SETTINGS_ROW_SIDE);
        assertThat(Token.DEPENDENT_INDENT.roles()).containsExactly(SETTING_DEPENDENT_INDENT);
        assertThat(Token.NAVIGATION_WIDTH.roles()).containsExactly(SETTINGS_SIDEBAR_WIDTH);
        assertThat(Token.STATUS_DOT.roles()).containsExactly(STATUS_DOT, PANEL_ROW_DOT, TOOL_HEADER_ACTIVE_DOT);
    }

    /** A catalog's own tokens are its first role of each, each inside its range (Rulings 17), and applying them changes nothing. */
    @Test void aCatalogsOwnTokensLeaveItAsItIs() {
        for (var catalog : List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            var tokens = MetricTokens.of(catalog);
            for (var token : Token.values()) {
                assertThat(tokens.value(token)).as(token.key()).isEqualTo(catalog.value(token.roles().getFirst()));
                assertThat(tokens.value(token)).as(token.key() + " in range").isBetween(token.min(), token.max());
            }
            assertThat(catalog.with(tokens)).isEqualTo(catalog);
        }
        assertThat(List.of(Token.values()).stream().map(t -> MetricTokens.of(DesignMetrics.CLASSIC).value(t)).toList())
            .as("Rulings 10: the classic base is tuned as Enterprise").containsExactly(22, 22, 3, 2, 3, 2, 24, 12, 20, 230, 7);
    }

    /** Review Focus 2: a token moves every role it sets by its change from the base, keeping the base's relations. */
    @Test void aTokenMovesEveryRoleItSetsByItsChangeFromTheBase() {
        var base = DesignMetrics.CLASSIC;
        var tall = base.with(MetricTokens.of(base).with(Token.CONTROL_HEIGHT, 26));
        for (var role : Token.CONTROL_HEIGHT.roles()) assertThat(tall.value(role)).as(role.name()).isEqualTo(base.value(role) + 4);
        assertThat(tall.value(BUTTON_HEIGHT) - tall.value(FIELD_HEIGHT)).as("buttons stay two taller").isEqualTo(2);
        for (var role : DesignMetrics.Role.values())
            if (!Token.CONTROL_HEIGHT.roles().contains(role) && role != FIELD_VERTICAL_PADDING && role != BUTTON_VERTICAL_PADDING)
                assertThat(tall.value(role)).as(role.name()).isEqualTo(base.value(role));
    }

    /** Spec 5.4: each padding is max(2, (control_height − 16) / 2) per side, so both sides' total. */
    @Test void thePaddingsFollowTheControlHeight() {
        for (int height = 20; height <= 28; height++) {
            var metrics = DesignMetrics.DEFAULT.with(MetricTokens.of(DesignMetrics.DEFAULT).with(Token.CONTROL_HEIGHT, height));
            int expected = 2 * Math.max(2, (height - MetricTokens.LINE_HEIGHT) / 2);
            assertThat(metrics.value(FIELD_VERTICAL_PADDING)).as("%d", height).isEqualTo(expected);
            assertThat(metrics.value(BUTTON_VERTICAL_PADDING)).as("%d", height).isEqualTo(expected);
        }
        assertThat(DesignMetrics.CLASSIC.value(FIELD_VERTICAL_PADDING)).isEqualTo(6);
        assertThat(DesignMetrics.DEFAULT.value(FIELD_VERTICAL_PADDING)).isEqualTo(8);
    }

    /** Review Focus 3: the message plan 5's parser shows, for each token at both ends. */
    @Test void aTokenOutsideItsRangeIsRefusedWithItsKey() {
        var tokens = MetricTokens.of(DesignMetrics.CLASSIC);
        assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(Token.ROW_HEIGHT, 40))
            .withMessage("[metrics].row_height: 40 is outside 18-28");
        for (var token : Token.values()) {
            assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(token, token.min() - 1));
            assertThatIllegalArgumentException().isThrownBy(() -> tokens.with(token, token.max() + 1));
            assertThat(tokens.with(token, token.min()).value(token)).isEqualTo(token.min());
        }
    }

    @Test void anExtremeTokenNeverMakesANegativeRole() {
        for (var base : List.of(DesignMetrics.DEFAULT, DesignMetrics.CLASSIC)) {
            var low = MetricTokens.of(base);
            for (var token : Token.values()) low = low.with(token, token.min());
            var derived = base.with(low);
            for (var role : DesignMetrics.Role.values()) assertThat(derived.value(role)).as(role.name()).isNotNegative();
        }
    }
}
