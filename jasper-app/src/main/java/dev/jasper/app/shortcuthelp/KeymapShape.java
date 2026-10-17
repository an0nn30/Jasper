package dev.jasper.app.shortcuthelp;

/**
 * How the Keymap page is composed (spec 12.2), as {@link KeymapPage} maps the look's composition once.
 *
 * @param columns the tree has Action, Scope and Shortcut columns, and a section shows "N shortcuts" in its Shortcut lane
 * @param textTools the tools are Find by Shortcut, Expand All and Collapse All, the search has no Record glyph and the
 *     status line no lock glyph
 * @param recorderField the edit band draws its recorder as a field and its hint line has no Record action (Task 4)
 */
public record KeymapShape(boolean columns, boolean textTools, boolean recorderField) {
    /** Today's Keymap. */
    public static final KeymapShape ROWS = new KeymapShape(false, false, false);
    /** The classic Keymap of the Settings mocks. */
    public static final KeymapShape CLASSIC = new KeymapShape(true, true, true);
}
