package dev.jasper.ui.metrics;

import static dev.jasper.ui.metrics.DesignMetrics.Role.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The 11 public metric tokens a kit file tunes (Enterprise presentation spec 5.4): each a value in its range that sets the
 * private catalog roles it names. {@link DesignMetrics#with(MetricTokens)} applies them to a base catalog. Immutable.
 */
public final class MetricTokens {
    /** The host-calibrated line height of a base face's 12 px line, which the vertical paddings are measured against. */
    public static final int LINE_HEIGHT = 16;

    /** A public token: its kit-file key, its range and the catalog roles it sets, the first being the one it reads. */
    public enum Token {
        CONTROL_HEIGHT("control_height", 20, 28, FIELD_HEIGHT, BUTTON_HEIGHT, PICKER_DEFAULT_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT,
            JOINED_ACTION_HEIGHT, PANEL_EMPTY_ACTION_HEIGHT, PANEL_SEARCH_HEIGHT, COLUMN_SEARCH_HEIGHT, KEYMAP_SEARCH_HEIGHT,
            FULL_SETTING_CHOICE_HEIGHT),
        ROW_HEIGHT("row_height", 18, 28, DesignMetrics.Role.ROW_HEIGHT, MENU_ITEM_HEIGHT, PICKER_ROW_HEIGHT, RESULTS_ROW_HEIGHT,
            RESULTS_HEADING_HEIGHT, KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, ORDERED_ROW_HEIGHT, SETTINGS_NAV_ROW,
            SETTINGS_NAV_HEADING, COLUMN_RAIL_ROW, PANEL_COMPACT_ROW_HEIGHT),
        CONTROL_RADIUS("control_radius", 0, 4, DesignMetrics.Role.CONTROL_RADIUS, SEGMENT_TRACK_RADIUS, SEGMENT_ITEM_RADIUS,
            PICKER_SEARCH_RADIUS, KEYCAP_RADIUS, CHIP_RADIUS, ORDERED_RADIUS),
        FIELD_RADIUS("field_radius", 0, 4, DesignMetrics.Role.FIELD_RADIUS, TRANSPARENT_FIELD_RADIUS),
        GROUP_RADIUS("group_radius", 0, 6, CARD_RADIUS),
        FOCUS_RING("focus_ring", 1, 3, DesignMetrics.Role.FOCUS_RING),
        TOOL_WINDOW_HEADER("tool_window_header", 22, 32, NATIVE_TOOL_HEADER_HEIGHT, PANEL_HEADER),
        GROUP_PADDING("group_padding", 8, 16, CARD_PADDING, FULL_SETTING_SIDE_PADDING, SETTINGS_ROW_SIDE),
        DEPENDENT_INDENT("dependent_indent", 12, 28, SETTING_DEPENDENT_INDENT),
        NAVIGATION_WIDTH("navigation_width", 180, 300, SETTINGS_SIDEBAR_WIDTH),
        STATUS_DOT("status_dot", 5, 9, DesignMetrics.Role.STATUS_DOT, PANEL_ROW_DOT, TOOL_HEADER_ACTIVE_DOT);

        private final String key;
        private final int min, max;
        private final List<DesignMetrics.Role> roles;

        Token(String key, int min, int max, DesignMetrics.Role... roles) {
            this.key = key; this.min = min; this.max = max; this.roles = List.of(roles);
        }

        /** The token's key in a kit file's {@code [metrics]} table. */
        public String key() { return key; }
        /** The least value the token takes. */
        public int min() { return min; }
        /** The greatest value the token takes. */
        public int max() { return max; }
        /** The catalog roles the token sets, the first being the one {@link MetricTokens#of} reads. */
        public List<DesignMetrics.Role> roles() { return roles; }
    }

    private final int[] values;

    private MetricTokens(int[] values) { this.values = values; }

    /** {@code catalog}'s own tokens: each token's first role, as the catalog holds it. */
    public static MetricTokens of(DesignMetrics catalog) {
        Objects.requireNonNull(catalog, "catalog");
        var values = new int[Token.values().length];
        for (var token : Token.values()) values[token.ordinal()] = catalog.value(token.roles().getFirst());
        return new MetricTokens(values);
    }

    /** {@code token}'s value. */
    public int value(Token token) { return values[Objects.requireNonNull(token, "token").ordinal()]; }

    /** A copy with {@code token} at {@code value}, which must lie in the token's range. */
    public MetricTokens with(Token token, int value) {
        Objects.requireNonNull(token, "token");
        if (value < token.min() || value > token.max())
            throw new IllegalArgumentException("[metrics]." + token.key() + ": " + value + " is outside " + token.min() + "-" + token.max());
        int[] copy = values.clone();
        copy[token.ordinal()] = value;
        return new MetricTokens(copy);
    }

    /** Spec 5.4's vertical padding for {@code controlHeight}: {@code max(2, (height − line) / 2)} per side, both sides' total. */
    static int padding(int controlHeight) { return 2 * Math.max(2, (controlHeight - LINE_HEIGHT) / 2); }

    @Override public boolean equals(Object other) { return other instanceof MetricTokens tokens && Arrays.equals(values, tokens.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
