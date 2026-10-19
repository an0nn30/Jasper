package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The ten chrome variant points of a kit file's {@code [chrome]} table (spec 5.2): nine two-valued points and the
 * window title template. Referenced only in this package; consumers receive projections ({@code Projections}).
 *
 * @param titleBar {@code title_bar}
 * @param menuBar {@code menu_bar}; {@code window} needs a native title bar
 * @param windowTitle {@code window_title}, a template over {@code {context}}, {@code {session}} and {@code {directory}}
 * @param toolWindows {@code tool_windows}
 * @param editorTabs {@code editor_tabs}
 * @param statusBar {@code status_bar}
 * @param palette {@code palette}
 * @param dialogs {@code dialogs}
 * @param motion {@code motion}
 * @param icons {@code icons}
 */
public record ChromeSpec(TitleBar titleBar, MenuBar menuBar, String windowTitle, ToolWindows toolWindows, EditorTabs editorTabs,
                         StatusBar statusBar, PaletteKind palette, DialogKind dialogs, MotionKind motion, Icons icons) {
    /** The unified title bar, or the operating system's. */
    public enum TitleBar { UNIFIED, NATIVE }
    /** The screen menu bar on macOS, or a menu row in each window. */
    public enum MenuBar { SYSTEM, WINDOW }
    /** Icon rails, or labelled stripes with a bottom strip. */
    public enum ToolWindows { RAILS, STRIPES }
    /** Equal-width tabs, or content-sized classic tabs. */
    public enum EditorTabs { EQUAL, CLASSIC }
    /** Slash-separated segments, or ruled cells. */
    public enum StatusBar { SEGMENTS, CELLS }
    /** The anchored palette card, or the Run window. */
    public enum PaletteKind { ANCHORED, RUN_WINDOW }
    /** Dialogs as each requests, or overlay and attached-sheet dialogs drawn in the window. */
    public enum DialogKind { AS_REQUESTED, CLASSIC }
    /** Motion as the settings say, or none. */
    public enum MotionKind { STANDARD, NONE }
    /** IntelliJ's New UI chrome icons, or its classic ones. */
    public enum Icons { NEW_UI, CLASSIC }

    /** The default look. */
    public static final ChromeSpec DEFAULT = new ChromeSpec(TitleBar.UNIFIED, MenuBar.SYSTEM, "{context}", ToolWindows.RAILS,
        EditorTabs.EQUAL, StatusBar.SEGMENTS, PaletteKind.ANCHORED, DialogKind.AS_REQUESTED, MotionKind.STANDARD, Icons.NEW_UI);
    /** The classic base's points: spec 5.2's Enterprise column with the default title template. */
    public static final ChromeSpec CLASSIC = new ChromeSpec(TitleBar.NATIVE, MenuBar.WINDOW, "{context}", ToolWindows.STRIPES,
        EditorTabs.CLASSIC, StatusBar.CELLS, PaletteKind.RUN_WINDOW, DialogKind.CLASSIC, MotionKind.NONE, Icons.CLASSIC);

    /** Every point is required, the title is not blank, and window menus need a native title bar (spec 5.3). */
    public ChromeSpec {
        Objects.requireNonNull(titleBar, "titleBar"); Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(windowTitle, "windowTitle"); Objects.requireNonNull(toolWindows, "toolWindows");
        Objects.requireNonNull(editorTabs, "editorTabs"); Objects.requireNonNull(statusBar, "statusBar");
        Objects.requireNonNull(palette, "palette"); Objects.requireNonNull(dialogs, "dialogs"); Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(icons, "icons");
        if (windowTitle.isBlank()) throw new IllegalArgumentException("Blank window title template");
        if (menuBar == MenuBar.WINDOW && titleBar != TitleBar.NATIVE)
            throw new IllegalArgumentException("menu_bar = window needs title_bar = native");
    }
}
