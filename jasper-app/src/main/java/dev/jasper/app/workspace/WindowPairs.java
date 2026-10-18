package dev.jasper.app.workspace;

import dev.jasper.ui.theme.ColorRole;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.PaintPair;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast.Guard;
import java.util.List;

/**
 * The pairs the classic window chrome paints (Enterprise presentation spec 7.3, 16.1.1 and 16.1.2; plan 6b Rulings 17):
 * every text, indicator and boundary of the classic menu row, toolbar row, tab strip, ruled status cells and run item
 * is painted through one of these with {@code SurfaceContrast.painted}. The {@code border.divider} rules (the menu
 * row's, the cells' and the keycap's edge) and the tab strip's {@code border.window} dividers and rule are painted as
 * the role, as the dock's strip divider is. {@code
 * WindowPairsTest} samples each over the theme set and the degenerate palettes under both kits.
 */
final class WindowPairs {
    private WindowPairs() { }

    private static PaintPair pair(ColorRole slot, ColorRole against, Guard guard) { return new PaintPair(slot, against, guard); }

    /** An open classic menu's title on the accent ({@code ClassicMenuRow} paints it). */
    static final PaintPair OPEN_MENU = dev.jasper.app.platform.ClassicMenuRow.OPEN_TITLE;
    /** The toolbar row's and the status bar's rule, and the toolbar's separators. */
    static final PaintPair RULE = pair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, Guard.STRUCTURAL);
    /** A classic toolbar button's label and glyphs: at rest, hovered and pressed, on the fill it lies on. */
    static final PaintPair BUTTON_LABEL = pair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_APP, Guard.TEXT),
        HOVERED_BUTTON_LABEL = pair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CONTROL_HOVER, Guard.TEXT),
        PRESSED_BUTTON_LABEL = pair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CONTROL_SELECTED, Guard.TEXT);
    /** A hovered classic toolbar button's frame, and a pressed one's. */
    static final PaintPair BUTTON_FRAME = pair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_CONTROL_HOVER, Guard.STRUCTURAL),
        PRESSED_BUTTON_FRAME = pair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_CONTROL_SELECTED, Guard.STRUCTURAL);
    /** The search field's edge, and while hovered. */
    static final PaintPair SEARCH_EDGE = pair(SemanticColor.BORDER_CONTROL, SemanticColor.SURFACE_APP, Guard.CONTROL),
        SEARCH_HOVER_EDGE = pair(DerivedColor.BORDER_HOVER, SemanticColor.SURFACE_APP, Guard.CONTROL);
    /** "Search everywhere", and the keycap's shortcut. */
    static final PaintPair PLACEHOLDER = pair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_INPUT, Guard.TEXT),
        KEYCAP_TEXT = pair(SemanticColor.TEXT_PLACEHOLDER, DerivedColor.SURFACE_TABLE_HEADER, Guard.TEXT);
    /** A tab's label: the window active or not, the tab selected (on {@code surface.card}) or not. */
    static final PaintPair TAB_LABEL = pair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_TABSTRIP, Guard.TEXT),
        SELECTED_TAB_LABEL = pair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, Guard.TEXT),
        INACTIVE_TAB_LABEL = pair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_TABSTRIP, Guard.TEXT),
        INACTIVE_SELECTED_TAB_LABEL = pair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_CARD, Guard.TEXT);
    /** A tab's status dot: running or idle, selected or not. */
    static final PaintPair RUNNING_TAB_DOT = pair(SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_TABSTRIP, Guard.INDICATOR),
        RUNNING_SELECTED_TAB_DOT = pair(SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_CARD, Guard.INDICATOR),
        IDLE_TAB_DOT = pair(SemanticColor.STATUS_IDLE, SemanticColor.SURFACE_TABSTRIP, Guard.INDICATOR),
        IDLE_SELECTED_TAB_DOT = pair(SemanticColor.STATUS_IDLE, SemanticColor.SURFACE_CARD, Guard.INDICATOR);
    /** A tab's ×, selected or not. */
    static final PaintPair TAB_CLOSE = pair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_TABSTRIP, Guard.INDICATOR),
        SELECTED_TAB_CLOSE = pair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_CARD, Guard.INDICATOR);
    /** The selected tab's underline: structural, as the card fill marks the tab too (Rulings 17). */
    static final PaintPair TAB_INDICATOR = pair(SemanticColor.TAB_INDICATOR, SemanticColor.SURFACE_CARD, Guard.STRUCTURAL);
    /** The "+" after the last tab. */
    static final PaintPair TAB_PLUS = pair(SemanticColor.TEXT_SECONDARY, SemanticColor.SURFACE_TABSTRIP, Guard.TEXT);
    /** A ruled cell's text and contributed items. */
    static final PaintPair CELL_TEXT = pair(SemanticColor.TEXT_STRONG_SECONDARY, SemanticColor.SURFACE_APP, Guard.TEXT);
    /** The session cell's dot, running or idle. */
    static final PaintPair RUNNING_SESSION_DOT = pair(SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_APP, Guard.INDICATOR),
        IDLE_SESSION_DOT = pair(SemanticColor.STATUS_IDLE, SemanticColor.SURFACE_APP, Guard.INDICATOR);
    /** The configuration cell's text on its ground. */
    static final PaintPair WARNING_CELL = pair(SemanticColor.STATUS_WARNING, DerivedColor.WARNING_GROUND, Guard.TEXT),
        DANGER_CELL = pair(SemanticColor.STATUS_DANGER, DerivedColor.DANGER_GROUND, Guard.TEXT);
    /** "▶ Run…" on ruled cells and on slash segments, and while the palette is open (spec 16.1.1's active run item). */
    static final PaintPair RUN = pair(DerivedColor.ACCENT_EDGE, SemanticColor.SURFACE_APP, Guard.TEXT),
        SLASH_RUN = pair(DerivedColor.ACCENT_EDGE, SemanticColor.SURFACE_STATUS, Guard.TEXT),
        ACTIVE_RUN = pair(DerivedColor.ACCENT_EDGE, SemanticColor.SURFACE_CONTROL_SELECTED, Guard.TEXT);

    /** Every window pair, the declared table of spec 16.1.1. */
    static final List<PaintPair> ALL = List.of(OPEN_MENU, RULE, BUTTON_LABEL, HOVERED_BUTTON_LABEL, PRESSED_BUTTON_LABEL, BUTTON_FRAME,
        PRESSED_BUTTON_FRAME, SEARCH_EDGE, SEARCH_HOVER_EDGE,
        PLACEHOLDER, KEYCAP_TEXT, TAB_LABEL, SELECTED_TAB_LABEL, INACTIVE_TAB_LABEL, INACTIVE_SELECTED_TAB_LABEL,
        RUNNING_TAB_DOT, RUNNING_SELECTED_TAB_DOT, IDLE_TAB_DOT, IDLE_SELECTED_TAB_DOT, TAB_CLOSE, SELECTED_TAB_CLOSE,
        TAB_INDICATOR, TAB_PLUS, CELL_TEXT, RUNNING_SESSION_DOT, IDLE_SESSION_DOT, WARNING_CELL, DANGER_CELL, RUN, SLASH_RUN, ACTIVE_RUN);

    static PaintPair tabLabel(boolean activeWindow, boolean selected) {
        return activeWindow ? (selected ? SELECTED_TAB_LABEL : TAB_LABEL) : (selected ? INACTIVE_SELECTED_TAB_LABEL : INACTIVE_TAB_LABEL);
    }
    static PaintPair tabDot(boolean running, boolean selected) {
        return running ? (selected ? RUNNING_SELECTED_TAB_DOT : RUNNING_TAB_DOT) : (selected ? IDLE_SELECTED_TAB_DOT : IDLE_TAB_DOT);
    }
    static PaintPair tabClose(boolean selected) { return selected ? SELECTED_TAB_CLOSE : TAB_CLOSE; }
    static PaintPair sessionDot(boolean running) { return running ? RUNNING_SESSION_DOT : IDLE_SESSION_DOT; }
    static PaintPair configuration(SemanticColor problem) { return problem == SemanticColor.STATUS_DANGER ? DANGER_CELL : WARNING_CELL; }
    static PaintPair run(boolean active, boolean ruled) { return active ? ACTIVE_RUN : ruled ? RUN : SLASH_RUN; }
}
