package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Objects;

/** Fixed logical geometry. Display transforms belong to the host; font growth belongs to content bounds. */
public final class DesignMetrics {
    public static final DesignMetrics DEFAULT = new DesignMetrics();
    public enum Role {
        FIELD_HEIGHT(28), BUTTON_HEIGHT(28), HERO_FIELD_HEIGHT(32), HERO_BUTTON_HEIGHT(30),
        PANEL_ACTION(24), INLINE_ACTION(22), FORM_LABEL_WIDTH(96), COLUMN_GAP(12), CARD_PADDING(14),
        ROW_GAP(10), SECTION_GAP(20), SHEET_SECTION_GAP(14), FORM_SIDES(24), HERO_SIDES(28),
        CONTROL_RADIUS(6), CARD_RADIUS(9), WINDOW_RADIUS(12), SEGMENT_TRACK_RADIUS(7), SEGMENT_ITEM_RADIUS(5),
        FOCUS_RING(3), PANEL_HEADER(38), PANEL_FOOTER(34), SWITCH_WIDTH(32), SWITCH_HEIGHT(18), SWITCH_KNOB(14),
        COMPACT_SWITCH_WIDTH(28), COMPACT_SWITCH_HEIGHT(16), COMPACT_SWITCH_KNOB(12),
        ICON_CAPTION(12), ICON_CONTROL(14), ICON_RAIL(16), ICON_EMPTY(20), ICON_HERO(24),
        FIELD_VERTICAL_PADDING(12), BUTTON_VERTICAL_PADDING(10), BUTTON_GAP(8), ICON_GAP(6),
        FIELD_HORIZONTAL_PADDING(10), BUTTON_HORIZONTAL_PADDING(14), PRIMARY_BUTTON_HORIZONTAL_PADDING(16),
        TEXTAREA_PADDING(9), PORT_PREFIX_GAP(3), SEGMENT_PADDING(2), CHIP_HEIGHT(16), CHIP_PADDING(5),
        CHIP_RADIUS(4), STATUS_DOT(6), STRENGTH_HEIGHT(3), STRENGTH_GAP(3), STRENGTH_RADIUS(2), PROGRESS_HEIGHT(3), HERO_WIDTH(400), SHEET_WIDTH(480), FORM_WIDTH(540), SECTION_LABEL_GAP(8),
        SETTING_HEIGHT(42), SETTING_DESCRIPTION_HEIGHT(48), SETTING_VERTICAL_PADDING(8),
        FOOTER_PADDING(20), CAPTION_TOP_OFFSET(6), EMPTY_TEXT_WIDTH(300), EMPTY_TILE(40), HERO_TILE(52),
        HERO_TILE_RADIUS(13), HERO_TILE_GAP(14), DESCRIPTION_GAP(4), MENU_PADDING(5), MENU_MIN_WIDTH(200),
        MENU_ITEM_PADDING(9), MENU_SEPARATOR_MARGIN(4), MENU_ITEM_RADIUS(5), MENU_ARROW_WIDTH(26), MENU_TILE(18);
        private final int value;
        Role(int value) { this.value = value; }
    }
    public DesignMetrics() {}
    public int value(Role role) { return Objects.requireNonNull(role).value; }
    /** Minimum outer height accommodating actual font line metrics, without scaling logical catalog values. */
    public int contentHeight(Role height, Font font, FontRenderContext context) {
        Objects.requireNonNull(font); Objects.requireNonNull(context);
        int padding = switch (height) {
            case FIELD_HEIGHT, HERO_FIELD_HEIGHT -> value(Role.FIELD_VERTICAL_PADDING);
            case BUTTON_HEIGHT, HERO_BUTTON_HEIGHT, PANEL_ACTION, INLINE_ACTION -> value(Role.BUTTON_VERTICAL_PADDING);
            default -> throw new IllegalArgumentException("Not a content height role: " + height);
        };
        return Math.max(value(height), (int)Math.ceil(font.getLineMetrics("Ag", context).getHeight()) + padding);
    }
}
