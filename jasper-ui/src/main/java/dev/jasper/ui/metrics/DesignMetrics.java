package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.Objects;

/**
 * Logical geometry: one value per {@link Role}. {@link #DEFAULT} is the default catalog; other catalogs derive from it:
 * {@link #CLASSIC} with the classic delta, a kit's with {@link #with(MetricTokens)}, a test's with {@link #with(Role, int)}.
 * Display transforms belong to the host; font growth belongs to content bounds.
 */
public final class DesignMetrics {
    /** Native short-window overflow keeps at least two rows or text lines in its inner body. */
    public static final int NATIVE_BODY_VISIBLE_UNITS = 2;
    /** Automatic native splits and sidecars reserve two measured control-height units horizontally. */
    public static final int NATIVE_BODY_LANE_UNITS = 2;
    /** Bounded first-column text allowance for native minimum-width negotiation; never the full longest value. */
    public static final int TABLE_MIN_VISIBLE_GLYPHS = 4;
    /** Transparent query focus is one inward stroke, measured in logical pixels before user scaling. */
    public static final float TRANSPARENT_FIELD_FOCUS_WIDTH = 1.5f;
    /** The default catalog: each role's declared value, frozen by compact-roles.tsv. */
    public static final DesignMetrics DEFAULT = new DesignMetrics(declared());
    /**
     * The classic catalog (Enterprise presentation spec 5.5): the default with the classic look's delta, measured on the
     * Enterprise mocks and frozen by classic-roles.tsv. Its own tokens are the shipped Enterprise kit's.
     */
    public static final DesignMetrics CLASSIC = classic();
    public enum Role {
        // One-line rows (menus, pickers, navigation, keycap and identity rows) pad their line by ROW_VERTICAL_PADDING; a native
        // menu row's margin is MENU_ITEM_VERTICAL_PADDING; main's Button.margin top and bottom are BUTTON_LABEL_MARGIN.
        ROW_VERTICAL_PADDING(4), MENU_ITEM_VERTICAL_PADDING(3), BUTTON_LABEL_MARGIN(2), PICKER_COMPACT_HEIGHT(22), PICKER_DEFAULT_HEIGHT(24), PICKER_FACE_WIDTH(190), PICKER_PADDING(4), PICKER_SEARCH_HEIGHT(22), PICKER_SEARCH_RADIUS(4), PICKER_SEARCH_SIDE(5), PICKER_SEARCH_PADDING(6), PICKER_SEARCH_GLYPH(9), PICKER_SEARCH_GAP(6), PICKER_SEARCH_TOP(5), PICKER_UTILITY_HEIGHT(22), PICKER_ROW_HEIGHT(22), PICKER_TILE(16), PICKER_FACE_TILE(14), PICKER_GAP(5), PICKER_ROW_GAP(7), PICKER_GLYPH(8), PICKER_FOOTER_TOP(3), PICKER_MENU_BOTTOM(2), PICKER_EMPTY_TEXT_INSET(4), PICKER_EMPTY_HEIGHT(34), PICKER_DISABLED_PERCENT(45),
        PICKER_COMPACT_FACE_INSET(4), PICKER_DEFAULT_FACE_INSET(5), PICKER_DEFAULT_FACE_TILE(18), PICKER_DEFAULT_FACE_GAP(7),
        PICKER_COMPACT_FACE_GLYPH(10), PICKER_DEFAULT_FACE_GLYPH(13), PICKER_COMPACT_FACE_CHEVRON(8), PICKER_DEFAULT_FACE_CHEVRON(10),
        FIELD_HEIGHT(24), BUTTON_HEIGHT(24), PROMPT_FIELD_HEIGHT(28), PROMPT_BUTTON_HEIGHT(28),
        PANEL_ROW_SIDE(5), PANEL_ROW_PADDING(6), PANEL_ROW_TEXT_GAP(6), PANEL_ROW_TITLE_GAP(6), PANEL_ROW_DOT(7), PANEL_ROW_OPTICAL_OFFSET(1), PANEL_ROW_ACTION_INSET(2), PANEL_ROW_ACTION_GLYPH(13), PANEL_DETAIL_PADDING(10), PANEL_DETAIL_TILE(28), PANEL_DETAIL_TILE_HEIGHT(30), PANEL_DETAIL_GLYPH(14), PANEL_DETAIL_TEXT_BASELINE(1), PANEL_DETAIL_IDENTITY_GAP(8), PANEL_DETAIL_GAP(6), PANEL_DETAIL_ACTION(22), PANEL_DETAIL_ACTION_BOTTOM_TRIM(1), PANEL_DETAIL_ACTION_ARC(8), PANEL_DETAIL_ACTION_GLYPH(17), PANEL_DETAIL_ACTION_OPTICAL_OFFSET(2), PANEL_DETAIL_ACTION_PADDING(8), PANEL_DETAIL_ACTION_VERTICAL(5), PANEL_ACTION(24), INLINE_ACTION(22), FORM_LABEL_WIDTH(96), PROPERTY_LABEL_WIDTH(89), PROPERTY_PLAIN_LABEL_WIDTH(80), PROPERTY_PLAIN_ROW_GAP(2), PROPERTY_ACTIONS_PADDING(8), COLUMN_GAP(10), INLINE_GAP(7), CARD_PADDING(11),
        PANEL_EMPTY_TILE(37), PANEL_EMPTY_TILE_RADIUS(4), PANEL_EMPTY_GLYPH(16), PANEL_EMPTY_TEXT_WIDTH(206), PANEL_EMPTY_TITLE_GAP(8), PANEL_EMPTY_TEXT_GAP(5), PANEL_EMPTY_ACTION_GAP(12), PANEL_EMPTY_LINE_HEIGHT(15), PANEL_EMPTY_LIFT(4), PANEL_EMPTY_ACTION_HEIGHT(24),
        PANEL_COMPACT_ROW_HEIGHT(28), PANEL_COMPACT_ROW_VERTICAL(2), PANEL_COMPACT_ROW_PADDING(4), PANEL_COMPACT_ROW_TRAILING(5), PANEL_COMPACT_ROW_GLYPH(16), PANEL_COMPACT_LIST_TOP(3), PANEL_CHIP_HEIGHT(17), PANEL_CHIP_PADDING(3),
        ROUTE_HEIGHT(42), ROUTE_TOP(3), ROUTE_PADDING(8), ROUTE_VERTICAL_PADDING(5), ROUTE_TILE(22), ROUTE_GLYPH(14), ROUTE_STEP_GAP(6), ROUTE_ARROW(14), ROUTE_ARROW_GAP(6), ROUTE_ARROW_INSET(2),
        PANEL_FOOTER_SIDE(13), PANEL_FOOTER_TOP(3), PANEL_FOOTER_GAP(5), PANEL_FOOTER_SEPARATOR_GAP(2), PANEL_FOOTER_ROW_HEIGHT(32),
        ROW_GAP(8), MIXED_ROW_GAP(10), SECTION_GAP(16), SHEET_SECTION_GAP(11), FORM_TOP(13), FORM_SIDES(19), PROMPT_SIDES(22),
        // Form-family controls measured on the 2026-10-10 Desktop references (forms stream).
        FORM_SEGMENT_PADDING(2), FORM_SEGMENT_HEIGHT(22), FULL_SETTING_CHOICE_HEIGHT(24), SUPPORTING_LINE_HEIGHT(17), CHOICE_CHEVRON(10), CHOICE_CHEVRON_INSET(8),
        ORDERED_ROW_SIDE(10), ORDERED_INDEX_WIDTH(16), ORDERED_TEXT_GAP(8), ORDERED_ACTION(24), ORDERED_ACTION_GAP(6), ORDERED_GLYPH(10), MENU_TILE_GAP(8),
        COLUMN_SEARCH_HEIGHT(24), COLUMN_SEARCH_SIDE(10), COLUMN_SEARCH_GLYPH(12), COLUMN_SEARCH_GAP(8), COLUMN_TEXT_GAP(8), DETAIL_SECTION_GAP(14), DETAIL_TITLE_GAP(0), DETAIL_ACTION_INSET(7), DETAIL_ACTION_GAP(8),
        // A port field's resting width: New File Server and New Tunnel agree on 83; Add Host's older 71 is a recorded residual.
        PORT_FIELD_WIDTH(83),
        FOOTER_STATUS_GAP(11), WINDOW_ACTION_MIN_WIDTH(72), MENU_ICON_ARROW_WIDTH(21), TITLE_PAIR_GAP(3), DETAIL_MASK_PITCH(7), DETAIL_MASK_COUNT(12), DETAIL_ROW_SIDE(12),
        // A manager detail's grid of label and value rows (a group face's, spec 14.8's Fields): its box inside the card's
        // sides and bottom and its rows inside the box, and the box's top line under the card's top.
        DETAIL_GRID_INSET(9), DETAIL_GRID_TOP(11),
        // A grid's rows end this far inside its box's trailing edge; its rows' Show and Copy (and a value's Copy) stand this
        // far inside their faces; a manager detail's row of text buttons keeps this minimum width (0: none).
        DETAIL_GRID_TRAILING(9), DETAIL_ACTION_PADDING(8), DETAIL_ACTION_MIN_WIDTH(0),
        CONTROL_RADIUS(4), CARD_RADIUS(6), WINDOW_RADIUS(8), SEGMENT_TRACK_RADIUS(4), SEGMENT_ITEM_RADIUS(3),
        // Title rows measured on the 2026-10-10 Desktop references: a window's row above its rule (Credential Vault, and
        // the Settings header that stands in for it), a dialog's band before its content (Add Host, New File Server, New
        // Tunnel) and where a dialog's title and window controls centre, and a prompt dialog's band and controls centre.
        WINDOW_TITLE_HEIGHT(45), DIALOG_TITLE_HEIGHT(30), DIALOG_TITLE_CENTER(21), PROMPT_TITLE_HEIGHT(20), PROMPT_CONTROLS_CENTER(18),
        // The modal overlay: the card rises this far as it fades in, over these durations; the scrim's and the soft
        // shadow's strength, the shadow's reach and the card's least distance from the window's edges.
        OVERLAY_RISE(16), OVERLAY_ENTER_MILLIS(220), OVERLAY_EXIT_MILLIS(200), OVERLAY_MARGIN(16),
        // theme.md's shadows, black at these strengths (not theme colours): the primary button's inset line and 1 px drop,
        // popovers and menus, the modal overlay's card, and the segmented control's selected segment in Light.
        BUTTON_INSET_PERCENT(14), BUTTON_DROP_PERCENT_DARK(30), BUTTON_DROP_PERCENT_LIGHT(12),
        POPOVER_SHADOW_OFFSET(12), POPOVER_SHADOW_BLUR(32), POPOVER_SHADOW_PERCENT_DARK(50), POPOVER_SHADOW_PERCENT_LIGHT(18), SWITCH_KNOB_SHADOW_PERCENT(15),
        DIALOG_SHADOW_OFFSET(30), DIALOG_SHADOW_BLUR(80), DIALOG_SHADOW_PERCENT_DARK(58), DIALOG_SHADOW_PERCENT_LIGHT(22),
        SEGMENT_SHADOW_PERCENT_LIGHT(12), SEGMENT_SHADOW_BLUR(2),
        // theme.md's alpha treatments on base tokens: the focus ring (about 32% in Dark), the error ring, and category
        // tiles and chips (16–18% in Dark, 12–14% in Light).
        FOCUS_HALO_PERCENT_DARK(32), FOCUS_HALO_PERCENT_LIGHT(25), ERROR_HALO_PERCENT(22), TILE_PERCENT_DARK(17), TILE_PERCENT_LIGHT(13),
        FOCUS_RING(3), PANEL_HEADER(35), PANEL_HEADER_BOTTOM_GAP(5), PANEL_HEADER_ACTION(24), PANEL_HEADER_ACTION_GAP(2), PANEL_HEADER_TRAILING_PADDING(6), PANEL_HEADER_CONTRIBUTED_GLYPH(18), PANEL_SEARCH_SIDE(7), PANEL_SEARCH_BOTTOM(6), PANEL_SEARCH_HEIGHT(24), PANEL_SEARCH_VERTICAL_PADDING(8), PANEL_HEADER_ACTION_VERTICAL(7), PANEL_SEARCH_PADDING(5), PANEL_SEARCH_GAP(3), PANEL_SEARCH_TEXT_BASELINE(1), PANEL_SEARCH_TOP(0), PANEL_SEARCH_RULE(0), PANEL_STATUS_RULE(0), PANEL_FOOTER(30), SWITCH_WIDTH(32), SWITCH_HEIGHT(18), SWITCH_KNOB(14),
        COMPACT_SWITCH_WIDTH(28), COMPACT_SWITCH_HEIGHT(16), COMPACT_SWITCH_KNOB(12),
        ICON_CAPTION(12), ICON_CONTROL(14), ICON_RAIL(16), ICON_EMPTY(20), ICON_PROMPT(24), ICON_SWATCH(12),
        FIELD_VERTICAL_PADDING(8), BUTTON_VERTICAL_PADDING(8), BUTTON_GAP(6), ICON_GAP(6),
        TRANSPARENT_FIELD_PADDING(5), TRANSPARENT_FIELD_RADIUS(3), TRANSPARENT_FIELD_FOCUS_RADIUS(4), FIELD_HORIZONTAL_PADDING(8), BUTTON_HORIZONTAL_PADDING(14), PRIMARY_BUTTON_HORIZONTAL_PADDING(14),
        TEXTAREA_PADDING(7), PORT_PREFIX_GAP(2), SEGMENT_PADDING(2), SEGMENT_HORIZONTAL_PADDING(10), CHIP_HEIGHT(16), CHIP_PADDING(4),
        CHIP_RADIUS(3), STATUS_DOT(6), STRENGTH_HEIGHT(3), STRENGTH_GAP(2), STRENGTH_RADIUS(2), PROGRESS_HEIGHT(3), PROMPT_WIDTH(400), PROMPT_COMPACT_WIDTH(380), SHEET_WIDTH(480), FORM_WIDTH(540), SECTION_LABEL_GAP(6),
        COLUMN_LIST_SIDE(6), COLUMN_RAIL_TOP(8), COLUMN_SEARCH_TOP(8), COLUMN_SEARCH_BOTTOM(6), COLUMN_RAIL_ROW(24), COLUMN_ITEM_ROW(36), DETAIL_HEADING_GAP(11), DETAIL_VALUE_GAP(2), DETAIL_METADATA_GAP(12), DETAIL_ROW_HEIGHT(54), DETAIL_PADDING(19), MANAGER_SIDEBAR_WIDTH(192), MANAGER_LIST_WIDTH(272), CATEGORY_RAIL_TILE(20), CATEGORY_ROW_TILE(28), CATEGORY_SHEET_TILE(32), CATEGORY_DETAIL_TILE(44), ORDERED_ROW_HEIGHT(28), ORDERED_ADD_HEIGHT(28), FULL_FOOTER_BUTTON_HEIGHT(24), FULL_FOOTER_TOP(18), ORDERED_RADIUS(4), STEPPER_BUTTON_WIDTH(22), STEPPER_VALUE_WIDTH(52), SLIDER_WIDTH(200), SLIDER_TRACK(4), SLIDER_THUMB(15), THEME_PREVIEW_WIDTH(168), THEME_PREVIEW_HEIGHT(72), THEME_PREVIEW_GAP(8), THEME_PREVIEW_INSET(8), SETTINGS_ICON_TILE(20), SETTINGS_SIDEBAR_WIDTH(236), SETTINGS_HEADER_HEIGHT(45), SETTINGS_FOOTER_HEIGHT(42), SETTINGS_PAGE_INSET(20), SETTINGS_PAGE_TOP(16), SETTINGS_CARD_WIDTH(620), SETTINGS_ROW_HEIGHT(36), SETTINGS_DESCRIPTION_HEIGHT(52), SETTINGS_SECTION_GAP(16), SETTINGS_CHOICE_WIDTH(190), CHOICE_ARROW_WIDTH(20), CHOICE_VALUE_END_PADDING(2), SETTINGS_RICH_CHOICE_WIDTH(170), SETTINGS_GROUP_HEIGHT(32), SETTINGS_NAV_INSET(6), SETTINGS_NAV_ROW(30), SETTINGS_NAV_HEADING(22), SETTINGS_NAV_ROW_SIDE(6), SETTINGS_NAV_GROUP_GAP(9), SETTINGS_ACTION_MIN_WIDTH(72), JOINED_ACTION_WIDTH(25), JOINED_ACTION_HEIGHT(24), CIRCULAR_ACTION_SIZE(24), SETTING_CHOICE_WIDTH(160), SETTING_HEIGHT(34), SETTING_DESCRIPTION_HEIGHT(40), FULL_SETTING_DESCRIPTION_HEIGHT(52), SETTING_VERTICAL_PADDING(6),
        SETTINGS_SIDEBAR_INSET(8), SETTINGS_SEARCH_GAP(4), SETTINGS_NAV_HEADING_GAP(5), SETTINGS_FOOTER_SIDE(13), SETTINGS_HEADER_GAP(8), SETTINGS_HEADER_TITLE_GAP(13), SETTINGS_CRUMB_GAP(3), SETTINGS_ROW_SIDE(12), THEME_PREVIEW_CAPTION_GAP(6), THEME_PREVIEW_RING(2), SETTINGS_SEGMENT_INSET(2), SETTINGS_ICON_TEXT_GAP(9), SETTINGS_GROUP_ROW_HEIGHT(34), SETTINGS_GROUP_ROW_PADDING(6), SETTINGS_CHOICE_CHEVRON(10), SETTINGS_CHOICE_CHEVRON_INSET(10), SLIDER_VALUE_WIDTH(50), STEPPER_VALUE_PADDING(6), SETTINGS_CHOICE_ARROW(21), SETTINGS_SEARCH_ICON_GAP(8),
        KEYCAP_TREE_GROUP_HEIGHT(26), KEYCAP_TREE_INSET(5), KEYCAP_KEYS_COLUMN(118), KEYCAP_DETAIL_TOP(2), KEYCAP_DETAIL_BOTTOM(8), KEYCAP_RECORDER_HEIGHT(26), PLUGIN_DETAILS_TRAILING_PADDING(10), KEYCAP_GROUP_SIDE(6), KEYCAP_GROUP_TEXT_GAP(6), KEYMAP_SEARCH_HEIGHT(24), KEYCAP_PADDING(5), KEYCAP_GAP(2), KEYCAP_VERTICAL_PADDING(4), KEYCAP_SQUARE_EXTRA(3), KEYCAP_RADIUS(3), KEYCAP_BASE(2), KEYCAP_EDGE_ALPHA(87), KEYCAP_FACE_ALPHA(12), KEYCAP_RECORDER_PADDING(6), KEYCAP_RECORDER_DOT(8), KEYCAP_RECORDER_HALO(3), KEYCAP_RECORDER_HALO_ALPHA(51), KEYCAP_DETAIL_GAP(6), KEYCAP_DETAIL_AFTER(2), IDENTITY_HEADING_BOTTOM(5), PLUGIN_SEARCH_TRAILING(8), PLUGIN_SEARCH_GAP(5), IDENTITY_ROW_TRAILING(6), PLUGIN_TEXT_LINE_SPACING(20), SETTINGS_POPUP_CHECK(9), SETTINGS_ANNOTATION_GAP(6),
        WINDOW_FOOTER_VERTICAL(6), WINDOW_FOOTER_SIDE(10), FOOTER_PADDING(16), CAPTION_TOP_OFFSET(6), EMPTY_TEXT_WIDTH(300), EMPTY_TILE(40), PROMPT_TILE(54), PROMPT_TOP(15),
        PROMPT_INTRO_GAP(14), PROMPT_COMPACT_INTRO_GAP(14), PROMPT_FOOTNOTE_GAP(11), PROMPT_FOOTER_TOP(16), PROMPT_SECTION_GAP(19), PROMPT_FOOTER_BOTTOM(19), PROMPT_COMPACT_FOOTER_TOP(26), PROMPT_SETTING_HEIGHT(44), STRENGTH_WIDTH(280), STRENGTH_TOP_GAP(5), PROMPT_TILE_RADIUS(8), PROMPT_TILE_GAP(14), DESCRIPTION_GAP(3), DESCRIPTION_LINE_HEIGHT(18), SETTING_DESCRIPTION_GAP(2), SETTING_SIDE_PADDING(10), FULL_SETTING_SIDE_PADDING(12), SETTING_DEPENDENT_INDENT(13), MENU_PADDING(4), MENU_MIN_WIDTH(160), MENU_ITEM_HEIGHT(22),
        MENU_ITEM_PADDING(8), MENU_SEPARATOR_MARGIN(3), MENU_ARROW_WIDTH(21), MENU_TILE(16), MENU_SHORTCUT_GAP(24),
        // Equal-width workspace tabs (main's Alacritty-style strip, merged 2026-10-05) and their measured motion.
        TAB_OPEN_MILLIS(475), TAB_CLOSE_MILLIS(425), TAB_SWITCH_MILLIS(0),
        // Plan 3 Task 10 native workspace chrome: former per-feature literals, unchanged values; shares are per mille.
        TOOLBAR_BUTTON_TRIM(6), TOOLBAR_ACTION_ICON(16),
        NATIVE_HOVER_OVERLAY_PERCENT(8), PALETTE_WINDOW_MARGIN(16), PALETTE_HINT_ACTION_GAP(10), TRANSPARENT_FIELD_ICON_GAP(4),
        WORKSPACE_GUTTER(6), WORKSPACE_SURFACE_ARC(16), DOCK_DRAG_THRESHOLD(5), DOCK_DROP_ARC(8),
        TITLE_SEARCH_WIDTH(400), TITLE_SEARCH_MIN_WIDTH(240), TITLE_SEARCH_MAX_WIDTH(480), TITLE_SEARCH_HEIGHT(22),
        TITLE_SEARCH_ARC(8), NATIVE_TOOL_HEADER_HEIGHT(32), NATIVE_HEADER_GAP(12), NATIVE_ACTION_PADDING(4), NATIVE_MENU_SPACE(10), FIND_TOP_PADDING(2), FIND_BOTTOM_PADDING(3),
        DOCK_DROP_FILL_ALPHA(64), DOCK_DROP_BORDER_ALPHA(153), SPLIT_STROKE(2), BREADCRUMB_ICON_GAP(5), BREADCRUMB_MIN_HEIGHT(20), BREADCRUMB_SEPARATOR(22), STATUS_PROGRESS_MAX_WIDTH(420), STATUS_PROGRESS_RESERVE(90), STATUS_PROGRESS_GAP(8), STATUS_PROGRESS_WIDTH(72), HAIRLINE(1), STATUS_ITEM_GAP(5), STATUS_GAP(14), STATUS_SEPARATOR(28), STATUS_LEADING(26), TOOLBAR_PADDING(8), TOOLBAR_SEPARATOR_INSET(7), TOOLBAR_SEPARATOR_INSETS(14), TITLE_SEARCH_VERTICAL_SPACE(8), RAIL_MIN_HEIGHT(80), RAIL_EDGE(3), RAIL_GAP(4), RAIL_SECTION_GAP(8), RAIL_SEPARATOR_INSET(7),
        // Native adapter geometry preserved exactly until Plan 3 Task 10; each use has its own name so that
        // changing a kit or icon role cannot silently move workspace tabs, rails, status or find-bar layout.
        RAIL_BUTTON_ARC(8), NATIVE_TOOLBAR_HOVER_ARC(6), NATIVE_TOOLBAR_DISABLED_PERCENT(38),
        TAB_CLOSE_FIT_WIDTH(80), TAB_CLOSE_GUTTER(28), TAB_COMPACT_GUTTER(4), TAB_CLOSE_WIDTH(16),
        NATIVE_SPLIT_DIVIDER(8), TAB_CLOSE_TRAILING(6), TAB_DIVIDER(1), TAB_INDICATOR(2), TAB_REORDER_THRESHOLD(5),
        STATUS_RUNNING_DOT(5), STATUS_PROGRESS_BAR_HEIGHT(8), STATUS_PROGRESS_MIN_HEIGHT(24),
        TOOL_HEADER_LEADING_PADDING(10), TOOL_HEADER_TRAILING_PADDING(6), TOOL_HEADER_DETAIL_GAP(6), TOOL_HEADER_ACTIVE_DOT(6), RAIL_BADGE_DOT(6),
        FIND_GAP(6), FIND_SIDE_PADDING(6), FIND_CONTROL_GAP(4), FIND_CLOSE_SPACER(16),
        // Compact document bars (an editor's find/replace rows, notice and command strip) inside a DOCUMENT split.
        DOCUMENT_BAR_VERTICAL_PADDING(4), DOCUMENT_BAR_SIDE_PADDING(6), DOCUMENT_BAR_GAP(8), DOCUMENT_BAR_ACTION_GAP(3), DOCUMENT_BAR_STATUS_WIDTH(84), DOCUMENT_BAR_FILL_MIN_WIDTH(120), DOCUMENT_BAR_LINE_GAP(4), DOCUMENT_BAR_SEARCH_WIDTH(0), SEPARATOR_HEIGHT(18), DOCUMENT_RULE_GAP(0),
        // A document bar's text button's side padding under a face that pads them so (the 07 Credential Vault toolbar's verbs).
        DOCUMENT_BAR_BUTTON_PADDING(7),
        // The gap on each side of a document bar's separator, which the 07 Credential Vault toolbar draws a little tighter.
        DOCUMENT_BAR_RULE_GAP(8),
        TITLE_SEARCH_PADDING(8), TITLE_SEARCH_GAP(6), NATIVE_MENU_ARROW_GAP(3),
        NOTICE_VERTICAL_PADDING(8), NOTICE_HORIZONTAL_PADDING(12), NOTICE_BUTTON_GAP(8), BREADCRUMB_COLLAPSED_HEIGHT(16),
        // Kit breadcrumbs and the opt-in overlay scroll policy (Plan 2 Task 6b), keeping the chooser's former geometry.
        BREADCRUMB_CRUMB_PADDING(4), BREADCRUMB_SEPARATOR_PADDING(2),
        OVERLAY_SCROLLBAR_WIDTH(10), OVERLAY_SCROLLBAR_INSET(2), OVERLAY_SCROLLBAR_ARC(6), OVERLAY_SCROLLBAR_ALPHA(190),
        OVERLAY_SCROLLBAR_IDLE_MILLIS(1000), TYPE_AHEAD_MILLIS(1000), TREE_DETAIL_COLUMN(88), TREE_VALUE_COLUMN(96),
        // App-owned shells keep their Plan 2 arrangement with these exact former values until Plan 3 recomposes them.
        KEYMAP_PAGE_TOP_PADDING(13), KEYMAP_PAGE_PADDING(20), KEYMAP_PAGE_BOTTOM_PADDING(6), KEYMAP_SECTION_GAP(10), KEYMAP_TOOLS_GAP(6), KEYMAP_STATUS_GAP(6),
        CHOOSER_TOOLBAR_VERTICAL_PADDING(6), CHOOSER_TOOLBAR_SIDE_PADDING(8), CHOOSER_SIDE_PADDING(8),
        CHOOSER_NAVIGATION_WIDTH(190), CHOOSER_NAVIGATION_MIN_WIDTH(140), CHOOSER_PREVIEW_WIDTH(230), CHOOSER_PREVIEW_VERTICAL_PADDING(6),
        CHOOSER_PREVIEW_GAP(5), CHOOSER_BOTTOM_VERTICAL_PADDING(8), CHOOSER_BOTTOM_SIDE_PADDING(10),
        CHOOSER_NAME_COLUMN(360), CHOOSER_MODIFIED_COLUMN(150), CHOOSER_SIZE_COLUMN(80), CHOOSER_LOADING_DELAY_MILLIS(150),
        // RESULTS item lists (Plan 2 Task 6b-2): minimum one-line rows and headings, keeping the command palette's former geometry.
        RESULTS_VERTICAL_PADDING(3), RESULTS_ROW_HEIGHT(22), RESULTS_HEADING_HEIGHT(22), RESULTS_ROW_PADDING(6), RESULTS_ROW_GAP(6), PALETTE_WIDTH(680), PALETTE_CORNER_ARC(12), PALETTE_FIELD_HEIGHT(36), PALETTE_FIELD_VERTICAL_PADDING(3), PALETTE_FIELD_SIDE_PADDING(6),
        PALETTE_CHIP_GAP(5), PALETTE_HINT_HEIGHT(26), PALETTE_HINT_SIDE_PADDING(10), PALETTE_STEP_TITLE_HEIGHT(22), PALETTE_STEP_ROW_HEIGHT(32),
        PALETTE_STEP_SIDE_PADDING(10), PALETTE_STEP_ROW_PADDING(3), PALETTE_STEP_LABEL_WIDTH(140), PALETTE_STEP_LABEL_GAP(8),
        // Flowing body text (Plan 2 Task 6c): the widest line it asks for, in widths of 'm', as the former 44-column text areas.
        TEXT_FLOW_COLUMNS(44),
        // The Plugins page and the notification surfaces (Plan 2 Task 6c) keep their former geometry until Plan 3.
        KEYCAP_TREE_ROW_HEIGHT(26), KEYCAP_TREE_LEAF_INDENT(52), KEYCAP_DETAIL_INSET(3), KEYCAP_CONTENT_TRAILING(12), KEYCAP_DETAIL_LEADING(58), IDENTITY_HEADING_HEIGHT(28), IDENTITY_ROW_HEIGHT(38), IDENTITY_ROW_TILE(30), IDENTITY_ROW_GLYPH(17), IDENTITY_ROW_SIDE(6), IDENTITY_ROW_TEXT_GAP(8), IDENTITY_HEADER_GAP(11), IDENTITY_HEADER_AFTER(14), IDENTITY_SECTION_WIDTH(220), IDENTITY_HEADER_TILE(54), IDENTITY_HEADER_GLYPH(23),
        PLUGIN_MANAGER_PADDING(10), PLUGIN_MANAGER_SIDE_PADDING(5), PLUGIN_MANAGER_GAP(6), PLUGIN_LIST_WIDTH(264), PLUGIN_LIST_MIN_WIDTH(240), PLUGIN_LIST_MIN_HEIGHT(100),
        PLUGIN_DETAILS_GAP(10), PLUGIN_DETAILS_EDGE_PADDING(7), PLUGIN_DETAILS_LEADING_PADDING(15), PLUGIN_DETAIL_ICON(52),
        PLUGIN_TEXT_VERTICAL_PADDING(6), PLUGIN_TEXT_SIDE_PADDING(0),
        NOTIFICATION_WIDTH(360), NOTIFICATION_ICON(16), NOTIFICATION_GAP(6), NOTIFICATION_CLOSE(16),
        // App-unmocked surfaces (Plan 3 Task 9): the Errors tab's trace card shows at most this many lines before it scrolls.
        ERRORS_TRACE_ROWS(12),
        // A manager detail heading's title block keeps this width (or its own, if narrower) before its actions wrap.
        DETAIL_IDENTITY_MIN_WIDTH(160),
        NOTIFICATION_COUNT_GAP(5), NOTIFICATION_TITLE_CLOSE_GAP(3), NOTIFICATION_CONTENT_GAP(3), NOTIFICATION_LINK_GAP(10),
        NOTIFICATION_LINK_VERTICAL_PADDING(2), NOTIFICATION_STACK_MARGIN(10), NOTIFICATION_STACK_GAP(6),
        NOTIFICATION_SHADOW_ALPHA_DARK(4), NOTIFICATION_SHADOW_ALPHA_LIGHT(2), NOTIFICATION_SCROLL_STEP(16),
        HISTORY_HEADER_VERTICAL_PADDING(5), HISTORY_HEADER_LEADING_PADDING(8), HISTORY_HEADER_TRAILING_PADDING(5),
        HISTORY_SIDE_PADDING(8), HISTORY_HEADING_TOP_PADDING(6), HISTORY_HEADING_BOTTOM_PADDING(3),
        HISTORY_ROW_VERTICAL_PADDING(6), HISTORY_ROW_TRAILING_PADDING(5), HISTORY_ROW_GAP(6), HISTORY_TITLE_GAP(5), HISTORY_CONTENT_GAP(2),
        // Enterprise presentation plan 4: the list, table and tree row floor (BUTTON_HEIGHT before) and the field radius
        // (CONTROL_RADIUS before), then roles only the classic faces and chrome read, measured on the Enterprise mocks.
        ROW_HEIGHT(24), FIELD_RADIUS(4),
        RUN_WINDOW_WIDTH(460), CHECKBOX_BOX(13), CHECKBOX_RADIUS(2), CHECKBOX_LABEL_GAP(7), RADIO_RING(14), RADIO_DOT(6), RADIO_LABEL_GAP(6), RADIO_GAP(18), OPTION_SWATCH(7), GROUP_TITLE_INSET(9), GROUP_TITLE_PADDING(4), GROUP_TITLE_GAP(4), SPINNER_WIDTH(82), SPINNER_ARROW_WIDTH(16), SPINNER_GLYPH(7), DROPDOWN_GLYPH(6), DROPDOWN_GLYPH_INSET(7),
        TABLE_HEADER_HEIGHT(20), TREE_INDENT(24), DISCLOSURE_GLYPH(6), DISCLOSURE_GAP(10), FOLDER_TAB_HEIGHT(26), FOLDER_TAB_PADDING(14), FOLDER_TAB_RADIUS(2), ROUTE_EDGE(3), SIDE_COLUMN_WIDTH(92), SIDE_COLUMN_GAP(6), SIDE_BUTTON_GAP(4), STRIPE_LABEL_PADDING(10), STRIPE_LABEL_GAP(0), DOCK_RULE(1), DOCK_RULE_HIT(6),
        // Plan 4c: a grouped table's data rows under their heading (Settings, Notifications mock); classic faces only.
        GROUP_ROW_INDENT(14),
        // The Run window (Enterprise presentation plan 8a), measured on the 06 Run Dialog mocks; read only by the Run window.
        RUN_WINDOW_RADIUS(4), RUN_WINDOW_TITLE_HEIGHT(24), RUN_WINDOW_TITLE_SIDE(10), RUN_WINDOW_CLOSE(16), RUN_WINDOW_CLOSE_SIDE(5), RUN_WINDOW_SHORTCUT_GAP(10), RUN_WINDOW_SIDE(12), RUN_WINDOW_HELP_TOP(10), RUN_WINDOW_TILE(32), RUN_WINDOW_TILE_GLYPH(13), RUN_WINDOW_TILE_GAP(10), RUN_WINDOW_HELP_LINE(18), RUN_WINDOW_FIELD_TOP(11), RUN_WINDOW_LABEL_COLUMN(48), RUN_WINDOW_FIELD_SIDE(6), RUN_WINDOW_MENU_WIDTH(20), RUN_WINDOW_LIST_TOP(10), RUN_WINDOW_ROW_HEIGHT(21), RUN_WINDOW_HEADING_HEIGHT(18), RUN_WINDOW_ROW_SIDE(8), RUN_WINDOW_SWATCH(7), RUN_WINDOW_ROW_GAP(7), RUN_WINDOW_DETAIL_GAP(7), RUN_WINDOW_DETAIL_TOP(10), RUN_WINDOW_DETAIL_LABEL_GAP(6), RUN_WINDOW_CHECK_TOP(10), RUN_WINDOW_BOTTOM(10), RUN_WINDOW_FOOTER_HEIGHT(40), RUN_WINDOW_BUTTON_WIDTH(70), RUN_WINDOW_BUTTON_GAP(6),
        // Plan 5b: the help text's block under the help row's top, where the mocks set its first line (16 under the body's top).
        RUN_WINDOW_HELP_TEXT_TOP(1),
        // Plan 8b: the Run window's button label padding, so every footer label the mocks show fits their 70 px buttons
        // (RUN_WINDOW_BUTTON_WIDTH); a longer label grows its button. Read only by the Run window's kit views.
        RUN_WINDOW_BUTTON_PADDING(8),
        // Enterprise presentation plan 6: the classic chrome's menu row, toolbar, tabs, status cells and dialogs,
        // measured on the Enterprise mocks and read only by classic chrome.
        MENU_BAR_SIDE(5), MENU_BAR_ITEM_PADDING(9), MENU_BAR_HEIGHT(24), CHROME_BUTTON_PADDING(8), CHROME_BUTTON_GAP(8), CHROME_SEPARATOR_GAP(10),
        CHROME_SEPARATOR_INSET(8), CHROME_GLYPH(7), CHROME_GLYPH_GAP(6), CHROME_CARET(5), CHROME_CARET_GAP(7), CHROME_TRAILING_GAP(6),
        TOOLBAR_SEARCH_WIDTH(300), TOOLBAR_SEARCH_PADDING(8), HINT_KEYCAP_HEIGHT(16), HINT_KEYCAP_PADDING(4), CLASSIC_TAB_LEADING(12), CLASSIC_TAB_GAP(9),
        CLASSIC_TAB_TRAILING(10), CLASSIC_TAB_PLUS_INSET(11), CLASSIC_TAB_MIN_LABEL(24), STATUS_CELL_PADDING(10), STATUS_CELL_DOT_GAP(7), RUN_GLYPH_GAP(5),
        DIALOG_RADIUS(4), DIALOG_HEADER_HEIGHT(26), DIALOG_HEADER_INSET(10), DIALOG_SWATCH(8), DIALOG_SWATCH_GAP(7), DIALOG_FOOTER_VERTICAL(8),
        // Plan 10a: a native dialog's band pads its buttons 10 above and below (the New File Server, Add Host and New
        // Tunnel mocks' 44 px band), where a classic dialog drawn in its window keeps DIALOG_FOOTER_VERTICAL.
        NATIVE_DIALOG_FOOTER_VERTICAL(10),
        // Enterprise plan 7: the count pill, the native header's actions, the header strips' gaps and the bottom strip's
        // height above its rule (Workstation mock); only the classic dock chrome reads them.
        STRIPE_PILL_HEIGHT(14), STRIPE_PILL_WIDTH(16), STRIPE_PILL_PADDING(5), STRIPE_PILL_GAP(5), TOOL_HEADER_ACTION(16),
        HEADER_STRIP_TITLE_GAP(8), HEADER_STRIP_DOT_GAP(6), HEADER_STRIP_COUNT_GAP(6), BOTTOM_STRIP_HEIGHT(24),
        // Plan 9a: the Settings header's inset from the body (today the page inset); its classic value is the mocks' 14.
        SETTINGS_HEADER_INSET(20),
        // Plan 9a: a page's indent under its section in the Settings navigation (the classic navigation's 15), and the gap
        // from a navigation row's leading slot (the classic triangle) to its title.
        SETTINGS_NAV_CHILD_INDENT(0), SETTINGS_NAV_TEXT_GAP(6),
        // Plan 9a: the gap a SETTINGS_TRAILING stack without its action keeps after the control (today COLUMN_GAP's 10;
        // the classic rows, whose Reset is in the row's menu, keep none).
        SETTINGS_TRAILING_EMPTY_GAP(10),
        // Plan 9b: the lanes of a keycap tree with columns (the classic Keymap mock), read only there; equal in both catalogs.
        KEYMAP_SCOPE_COLUMN(107), KEYMAP_SHORTCUT_COLUMN(137), KEYMAP_KEYS_INSET(25), KEYMAP_SCOPE_GAP(30),
        // Plan 9b: the alpha of the classic recording band's edges over its fill; 0 draws none (today's card has its own edge).
        KEYCAP_DETAIL_EDGE_ALPHA(0),
        // Plan 9b: an item list's heading row with its two rules where the table face bands list headings, and a two-line
        // item list's row; 0 keeps a heading as tall as the rows and measures a row from its lines (the classic Plugins list).
        LIST_HEADING_HEIGHT(0), LIST_TWO_LINE_ROW_HEIGHT(0),
        // Plan 12: the classic toolbar's icon-only button (IntelliJ's 22 px ActionButton) and the ▼ in an icon-only
        // dropdown's corner; only ClassicToolbarButton reads them.
        CHROME_ICON_BUTTON(22), CHROME_ICON_CARET(4),
        // Plan 12: IntelliJ's run-configuration combo (ComboBoxButton) in a toolbar strip; only the classic buttons face reads them.
        TOOLBAR_COMBO_HEIGHT(24), TOOLBAR_COMBO_LEADING(10), TOOLBAR_COMBO_ICON_GAP(4), TOOLBAR_COMBO_ARROW(16), TOOLBAR_COMBO_TRAILING(7),
        TOOLBAR_COMBO_RADIUS(3), TOOLBAR_COMBO_TEXT_MAX(240),
        // Plan 13: a labelled stripe button's icon (IntelliJ's classic 13 px tool-window icons) and its gap to the text
        // (StripeButton's iconTextGap); only the labelled stripes read them.
        STRIPE_ICON(13), STRIPE_ICON_GAP(4);
        /** The role's value in {@link DesignMetrics#DEFAULT}; every reading goes through a catalog. */
        private final int declared;
        Role(int value) { declared = value; }
    }
    private final int[] values;
    private DesignMetrics(int[] values) { this.values = values; }
    private static DesignMetrics classic() {
        var values = new java.util.EnumMap<Role, Integer>(Role.class);
        values.put(Role.FIELD_HEIGHT, 22);
        values.put(Role.BUTTON_HEIGHT, 24);
        values.put(Role.PICKER_DEFAULT_HEIGHT, 22);
        values.put(Role.FULL_FOOTER_BUTTON_HEIGHT, 24);
        values.put(Role.JOINED_ACTION_HEIGHT, 24);
        values.put(Role.PANEL_EMPTY_ACTION_HEIGHT, 24);
        values.put(Role.PANEL_SEARCH_HEIGHT, 22);
        values.put(Role.COLUMN_SEARCH_HEIGHT, 22);
        values.put(Role.KEYMAP_SEARCH_HEIGHT, 22);
        values.put(Role.FULL_SETTING_CHOICE_HEIGHT, 22);
        values.put(Role.FIELD_VERTICAL_PADDING, 6);
        values.put(Role.BUTTON_VERTICAL_PADDING, 6);
        values.put(Role.ROW_HEIGHT, 22);
        values.put(Role.MENU_ITEM_HEIGHT, 22);
        values.put(Role.PICKER_ROW_HEIGHT, 22);
        values.put(Role.RESULTS_ROW_HEIGHT, 22);
        values.put(Role.RESULTS_HEADING_HEIGHT, 22);
        values.put(Role.KEYCAP_TREE_ROW_HEIGHT, 22);
        values.put(Role.KEYCAP_TREE_GROUP_HEIGHT, 22);
        values.put(Role.ORDERED_ROW_HEIGHT, 20);
        values.put(Role.SETTINGS_NAV_ROW, 22);
        values.put(Role.SETTINGS_NAV_HEADING, 22);
        values.put(Role.COLUMN_RAIL_ROW, 22);
        values.put(Role.PANEL_COMPACT_ROW_HEIGHT, 22);
        values.put(Role.CONTROL_RADIUS, 3);
        values.put(Role.SEGMENT_TRACK_RADIUS, 3);
        values.put(Role.SEGMENT_ITEM_RADIUS, 2);
        values.put(Role.PICKER_SEARCH_RADIUS, 3);
        values.put(Role.KEYCAP_RADIUS, 2);
        values.put(Role.CHIP_RADIUS, 1);
        values.put(Role.ORDERED_RADIUS, 0);
        values.put(Role.FIELD_RADIUS, 2);
        values.put(Role.TRANSPARENT_FIELD_RADIUS, 2);
        values.put(Role.CARD_RADIUS, 3);
        values.put(Role.FOCUS_RING, 2);
        values.put(Role.NATIVE_TOOL_HEADER_HEIGHT, 24);
        values.put(Role.PANEL_HEADER, 24);
        // Plan 10b: a manager rail's rows start 4 under its header strip (the Credential Vault mock's Categories).
        values.put(Role.COLUMN_RAIL_TOP, 4);
        // Plan 10b: a manager detail's content stands 12 inside its column, its parts 12 apart (the Credential Vault
        // mock's Details pane).
        values.put(Role.DETAIL_PADDING, 12);
        values.put(Role.DETAIL_SECTION_GAP, 12);
        // Plan 10b final review: the Item selected mock's Fields: its Show and Copy 4 apart and 6 inside their faces, the
        // lane ending 4 inside the grid's box, and Edit… and Delete… 86 wide.
        values.put(Role.DETAIL_GRID_TRAILING, 4);
        values.put(Role.DETAIL_ACTION_PADDING, 6);
        values.put(Role.DETAIL_ACTION_GAP, 4);
        values.put(Role.DETAIL_ACTION_MIN_WIDTH, 86);
        // Plan 10b: a swatch is a 10 px square (the Credential Vault mocks' Name cells and Details heading).
        values.put(Role.ICON_SWATCH, 10);
        values.put(Role.CARD_PADDING, 12);
        values.put(Role.FULL_SETTING_SIDE_PADDING, 12);
        values.put(Role.SETTINGS_ROW_SIDE, 12);
        values.put(Role.SETTING_DEPENDENT_INDENT, 20);
        values.put(Role.SETTINGS_SIDEBAR_WIDTH, 230);
        values.put(Role.STATUS_DOT, 7);
        values.put(Role.PANEL_ROW_DOT, 7);
        values.put(Role.TOOL_HEADER_ACTIVE_DOT, 7);
        values.put(Role.PALETTE_WINDOW_MARGIN, 8);
        values.put(Role.WORKSPACE_GUTTER, 0);
        values.put(Role.WINDOW_RADIUS, 0);
        values.put(Role.WORKSPACE_SURFACE_ARC, 0);
        values.put(Role.PALETTE_CORNER_ARC, 0);
        values.put(Role.DOCK_DROP_ARC, 0);
        values.put(Role.RAIL_BUTTON_ARC, 0);
        values.put(Role.NATIVE_TOOLBAR_HOVER_ARC, 0);
        values.put(Role.TITLE_SEARCH_ARC, 0);
        values.put(Role.PROMPT_TILE_RADIUS, 0);
        values.put(Role.PROMPT_TILE, 30);
        // Plan 10b: a prompt is the Unlock Vault mock's: 400 wide (PROMPT_COMPACT), its content 14 inside the dialog's
        // card, the tile's row 14 under the header strip and 12 above the 22 px field.
        values.put(Role.PROMPT_COMPACT_WIDTH, 400);
        values.put(Role.PROMPT_SIDES, 14);
        values.put(Role.PROMPT_TOP, 14);
        values.put(Role.PROMPT_COMPACT_INTRO_GAP, 12);
        values.put(Role.PROMPT_FIELD_HEIGHT, 22);
        values.put(Role.ICON_PROMPT, 16);
        values.put(Role.FORM_LABEL_WIDTH, 100);
        values.put(Role.COLUMN_GAP, 12);
        values.put(Role.SECTION_GAP, 12);
        // Plan 10b: a sheet's groups stand 18 apart, rule to rule (the 07 Add Login mock).
        values.put(Role.SHEET_SECTION_GAP, 10);
        values.put(Role.FORM_SIDES, 16);
        // Plan 10a: a FULL form's first group stands 10 under the dialog's top and a mixed card's rows 8 apart (the Add
        // Host and New Tunnel mocks: the first group's rule 18 below the title bar, Authentication's rows 8 apart).
        values.put(Role.FORM_TOP, 10);
        values.put(Role.MIXED_ROW_GAP, 8);
        values.put(Role.CHIP_HEIGHT, 14);
        values.put(Role.PANEL_CHIP_HEIGHT, 14);
        values.put(Role.SETTING_HEIGHT, 26);
        values.put(Role.SETTING_DESCRIPTION_HEIGHT, 42);
        values.put(Role.FULL_SETTING_DESCRIPTION_HEIGHT, 42);
        values.put(Role.THEME_PREVIEW_WIDTH, 150);
        values.put(Role.THEME_PREVIEW_HEIGHT, 78);
        values.put(Role.THEME_PREVIEW_GAP, 20);
        // Plan 9b Task 1: mock 01's radios sit 7 below their tiles.
        values.put(Role.THEME_PREVIEW_CAPTION_GAP, 7);
        // Enterprise plan 7: the header strips' paddings and 16 px actions, 18 apart, each glyph in a 10 px box (Workstation
        // mock); a strip draws its own, the host's and contributed actions at the contributed glyph size.
        values.put(Role.TOOL_HEADER_LEADING_PADDING, 8);
        values.put(Role.TOOL_HEADER_TRAILING_PADDING, 7);
        values.put(Role.PANEL_HEADER_ACTION, 16);
        values.put(Role.PANEL_HEADER_CONTRIBUTED_GLYPH, 10);
        values.put(Role.PANEL_HEADER_ACTION_VERTICAL, 0);
        values.put(Role.PANEL_HEADER_TRAILING_PADDING, 7);
        // Plan 10a: a panel's search row stands 6 below the header strip, 8 from the sides, over a 1 px panel rule
        // (the Workstation mock's Sessions and Tunnels panels).
        values.put(Role.PANEL_SEARCH_SIDE, 8);
        values.put(Role.PANEL_SEARCH_TOP, 6);
        values.put(Role.PANEL_SEARCH_RULE, 1);
        // Plan 10a: a status line under a panel's table follows the rows on surface.card under a 1 px panel rule (the
        // Workstation mock's Tunnels panel).
        values.put(Role.PANEL_STATUS_RULE, 1);
        // Plan 10a: a panel's empty state centres in the body and wraps its sentence at up to 300 (the Workstation mock's
        // Tunnels and SFTP panels).
        values.put(Role.PANEL_EMPTY_TEXT_WIDTH, 300);
        values.put(Role.PANEL_EMPTY_LIFT, 0);
        // Plan 10a: a panel footer's persistent setting row is 28 under its rule (the Workstation mock's SFTP Follow
        // terminal folder row).
        values.put(Role.PANEL_FOOTER_ROW_HEIGHT, 28);
        // Plan 9a: the classic Settings frame (Mock measurements).
        values.put(Role.SETTINGS_HEADER_HEIGHT, 34);
        values.put(Role.SETTINGS_FOOTER_HEIGHT, 45);
        values.put(Role.SETTINGS_FOOTER_SIDE, 14);
        values.put(Role.SETTINGS_ACTION_MIN_WIDTH, 98);
        values.put(Role.CIRCULAR_ACTION_SIZE, 22);
        values.put(Role.JOINED_ACTION_WIDTH, 26);
        values.put(Role.SETTINGS_HEADER_GAP, 10);
        values.put(Role.SETTINGS_HEADER_TITLE_GAP, 30);
        values.put(Role.SETTINGS_CRUMB_GAP, 6);
        values.put(Role.SETTINGS_PAGE_INSET, 16);
        values.put(Role.SETTINGS_PAGE_TOP, 10);
        values.put(Role.SETTINGS_CARD_WIDTH, 4000);
        values.put(Role.SETTINGS_SECTION_GAP, 12);
        values.put(Role.SETTINGS_CHOICE_WIDTH, 220);
        values.put(Role.SETTINGS_RICH_CHOICE_WIDTH, 220);
        values.put(Role.SETTINGS_HEADER_INSET, 14);
        values.put(Role.SETTINGS_NAV_INSET, 0);
        values.put(Role.SETTINGS_NAV_ROW_SIDE, 9);
        values.put(Role.SETTINGS_NAV_HEADING_GAP, 0);
        values.put(Role.SETTINGS_NAV_GROUP_GAP, 0);
        values.put(Role.SETTINGS_NAV_CHILD_INDENT, 15);
        // Plan 9b: the classic Keymap (Mock measurements).
        values.put(Role.KEYMAP_PAGE_TOP_PADDING, 18);
        values.put(Role.KEYMAP_PAGE_PADDING, 16);
        values.put(Role.KEYMAP_SECTION_GAP, 6);
        values.put(Role.KEYCAP_GROUP_SIDE, 8);
        values.put(Role.KEYCAP_TREE_LEAF_INDENT, 26);
        values.put(Role.KEYCAP_DETAIL_INSET, 4);
        // Plan 9b: the classic recording band, measured from mock 07b: 60 tall from the row's bottom with #C9D7F2 edges; the
        // field 22 tall at the canvas's 34 and 7 below the band's top (centred on the 24 px Cancel and Set, which therefore
        // stand 1 px above the mock's), the hint line 4 below them, Set ending 8 before the box; the armed dot 8 px at 9
        // into the field and the prompt 9 after it (a halo at alpha 0 keeps that room).
        values.put(Role.KEYCAP_DETAIL_EDGE_ALPHA, 49);
        values.put(Role.KEYCAP_DETAIL_TOP, 6);
        values.put(Role.KEYCAP_DETAIL_BOTTOM, 10);
        values.put(Role.KEYCAP_DETAIL_GAP, 5);
        values.put(Role.KEYCAP_DETAIL_LEADING, 34);
        values.put(Role.KEYCAP_CONTENT_TRAILING, 8);
        values.put(Role.KEYCAP_DETAIL_AFTER, 0);
        values.put(Role.KEYCAP_RECORDER_PADDING, 7);
        values.put(Role.KEYCAP_RECORDER_HALO, 2);
        values.put(Role.KEYCAP_RECORDER_HALO_ALPHA, 0);
        values.put(Role.KEYCAP_RECORDER_HEIGHT, 22);
        // Plan 9b Task 3: the mock's rows from the header rule, 16 px square keycaps 3 apart in #B4B8BF on #F7F8F9, and buttons
        // with 11 px sides (Find by Shortcut 110 wide; Shell's Add Variable… and Browse… keep the same sides).
        values.put(Role.KEYCAP_TREE_INSET, 0);
        values.put(Role.KEYCAP_VERTICAL_PADDING, 2);
        values.put(Role.KEYCAP_SQUARE_EXTRA, 0);
        values.put(Role.KEYCAP_GAP, 3);
        values.put(Role.KEYCAP_PADDING, 3);
        values.put(Role.KEYCAP_EDGE_ALPHA, 116);
        values.put(Role.KEYCAP_FACE_ALPHA, 8);
        values.put(Role.BUTTON_HORIZONTAL_PADDING, 11);
        values.put(Role.KEYMAP_STATUS_GAP, 5);
        // Plan 9b Task 1: the classic Settings rows as mocks 01 and 02 draw them (39 apart; 47 with a description).
        values.put(Role.SETTINGS_ROW_HEIGHT, 39);
        values.put(Role.SETTINGS_DESCRIPTION_HEIGHT, 47);
        values.put(Role.SETTINGS_TRAILING_EMPTY_GAP, 0);
        // Plan 9b Task 5: the classic Plugins list as mock 08 draws it (headings 24 with their rules, rows 36).
        values.put(Role.LIST_HEADING_HEIGHT, 24);
        values.put(Role.LIST_TWO_LINE_ROW_HEIGHT, 36);
        // Plan 9b Task 5: mock 08's list box 292 wide, 16 from the body's sides and bottom and level with the details' top
        // (18 below the header rule), the details 12 right of it and Disable ending 16 from the body's right.
        values.put(Role.PLUGIN_MANAGER_PADDING, 16);
        values.put(Role.PLUGIN_MANAGER_SIDE_PADDING, 16);
        values.put(Role.PLUGIN_LIST_WIDTH, 292);
        values.put(Role.PLUGIN_DETAILS_LEADING_PADDING, 12);
        values.put(Role.PLUGIN_DETAILS_TRAILING_PADDING, 0);
        values.put(Role.PLUGIN_DETAILS_EDGE_PADDING, 2);
        // The overview 14 below the folder tabs, its lines 17.5 apart.
        values.put(Role.PLUGIN_DETAILS_GAP, 4);
        values.put(Role.PLUGIN_TEXT_LINE_SPACING, 3);
        // Plan 10b: the classic manager (Rulings 4), the 07 Credential Vault mocks' columns, bar padding and search width.
        values.put(Role.MANAGER_SIDEBAR_WIDTH, 171);
        values.put(Role.MANAGER_LIST_WIDTH, 348);
        values.put(Role.DOCUMENT_BAR_VERTICAL_PADDING, 6);
        values.put(Role.DOCUMENT_BAR_SEARCH_WIDTH, 200);
        // A document split's rule stands between its bar and its body, not inside the bar (the 34 px toolbar row over its rule).
        values.put(Role.DOCUMENT_RULE_GAP, 1);
        // The 07 toolbar's rules: 7 either side, so its verbs meet the mock's positions (plan 10b final review).
        values.put(Role.DOCUMENT_BAR_RULE_GAP, 7);
        int[] table = declared();
        values.forEach((role, value) -> table[role.ordinal()] = value);
        return new DesignMetrics(table);
    }
    private static int[] declared() {
        var roles = Role.values();
        var values = new int[roles.length];
        for (var role : roles) values[role.ordinal()] = role.declared;
        return values;
    }
    /** {@code role}'s logical value in this catalog. */
    public int value(Role role) { return values[Objects.requireNonNull(role).ordinal()]; }
    /** A copy of this catalog with {@code role} set to {@code value}, which must not be negative. */
    public DesignMetrics with(Role role, int value) {
        Objects.requireNonNull(role);
        if (value < 0) throw new IllegalArgumentException("Negative " + role + ": " + value);
        int[] copy = values.clone();
        copy[role.ordinal()] = value;
        return new DesignMetrics(copy);
    }
    /**
     * This catalog tuned by {@code tokens}: each token's roles move by the token's change from this catalog's own value
     * (never below zero), and the field and button paddings follow the control height (spec 5.4).
     */
    public DesignMetrics with(MetricTokens tokens) {
        Objects.requireNonNull(tokens, "tokens");
        var own = MetricTokens.of(this);
        int[] copy = values.clone();
        for (var token : MetricTokens.Token.values()) {
            int delta = tokens.value(token) - own.value(token);
            for (var role : token.roles()) copy[role.ordinal()] = Math.max(0, values[role.ordinal()] + delta);
        }
        int padding = MetricTokens.padding(tokens.value(MetricTokens.Token.CONTROL_HEIGHT));
        copy[Role.FIELD_VERTICAL_PADDING.ordinal()] = padding;
        copy[Role.BUTTON_VERTICAL_PADDING.ordinal()] = padding;
        return new DesignMetrics(copy);
    }
    /** The vertical padding a content height adds to its font's line. */
    public Role contentPadding(Role height) {
        return switch (Objects.requireNonNull(height)) {
            case PICKER_COMPACT_HEIGHT, PICKER_DEFAULT_HEIGHT, PICKER_SEARCH_HEIGHT, PICKER_ROW_HEIGHT, PICKER_UTILITY_HEIGHT,
                 FORM_SEGMENT_HEIGHT, FULL_SETTING_CHOICE_HEIGHT, MENU_ITEM_HEIGHT, SETTINGS_NAV_ROW, SETTINGS_NAV_HEADING,
                 KEYCAP_TREE_ROW_HEIGHT, KEYCAP_TREE_GROUP_HEIGHT, IDENTITY_ROW_HEIGHT, IDENTITY_HEADING_HEIGHT -> Role.ROW_VERTICAL_PADDING;
            case RESULTS_ROW_HEIGHT, RESULTS_HEADING_HEIGHT -> Role.RESULTS_VERTICAL_PADDING;
            case PANEL_SEARCH_HEIGHT -> Role.PANEL_SEARCH_VERTICAL_PADDING;
            case PANEL_HEADER_ACTION -> Role.PANEL_HEADER_ACTION_VERTICAL;
            case PANEL_DETAIL_ACTION -> Role.PANEL_DETAIL_ACTION_VERTICAL;
            case FIELD_HEIGHT, PROMPT_FIELD_HEIGHT, COLUMN_SEARCH_HEIGHT -> Role.FIELD_VERTICAL_PADDING;
            case PANEL_EMPTY_ACTION_HEIGHT, BUTTON_HEIGHT, PROMPT_BUTTON_HEIGHT, FULL_FOOTER_BUTTON_HEIGHT, ORDERED_ADD_HEIGHT,
                 PANEL_ACTION, INLINE_ACTION, ROW_HEIGHT -> Role.BUTTON_VERTICAL_PADDING;
            default -> throw new IllegalArgumentException("Not a content height role: " + height);
        };
    }
    /** Minimum outer height accommodating actual font line metrics, without scaling logical catalog values. */
    public int contentHeight(Role height, Font font, FontRenderContext context) {
        Objects.requireNonNull(font); Objects.requireNonNull(context);
        return Math.max(value(height), (int) Math.ceil(font.getLineMetrics("Ag", context).getHeight()) + value(contentPadding(height)));
    }
    @Override public boolean equals(Object other) { return other instanceof DesignMetrics metrics && Arrays.equals(values, metrics.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
