package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The eleven chrome variant points of a kit file's {@code [chrome]} table (spec 5.2): nine two-valued points, the
 * three-valued {@code title_bar} and the window title template. Referenced only in this package; consumers receive
 * projections ({@code Projections}).
 *
 * @param titleBar {@code title_bar}
 * @param menuBar {@code menu_bar}; {@code window} needs a native or classic title bar
 * @param windowTitle {@code window_title}, a template over {@code {context}}, {@code {session}} and {@code {directory}}
 * @param toolWindows {@code tool_windows}
 * @param editorTabs {@code editor_tabs}
 * @param statusBar {@code status_bar}
 * @param palette {@code palette}
 * @param dialogs {@code dialogs}
 * @param motion {@code motion}
 * @param icons {@code icons}
 * @param toolbarSearch {@code toolbar_search}; {@code icon} needs the Run window and a native or classic title bar
 */
public record ChromeSpec(TitleBar titleBar, MenuBar menuBar, String windowTitle, ToolWindows toolWindows, EditorTabs editorTabs,
                         StatusBar statusBar, PaletteKind palette, DialogKind dialogs, MotionKind motion, Icons icons,
                         ToolbarSearch toolbarSearch) {
    /**
     * The unified title bar, the operating system's, or IntelliJ Classic's (a toolbar-coloured band under macOS's title, a
     * custom title bar holding the menus on Windows, the system's on Linux).
     */
    public enum TitleBar { UNIFIED, NATIVE, CLASSIC }
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
    /** The toolbar's Search Everywhere field, or IntelliJ Classic's icon button. */
    public enum ToolbarSearch { FIELD, ICON }

    /** The default look. */
    public static final ChromeSpec DEFAULT = new ChromeSpec(TitleBar.UNIFIED, MenuBar.SYSTEM, "{context}", ToolWindows.RAILS,
        EditorTabs.EQUAL, StatusBar.SEGMENTS, PaletteKind.ANCHORED, DialogKind.AS_REQUESTED, MotionKind.STANDARD, Icons.NEW_UI,
        ToolbarSearch.FIELD);
    /**
     * The classic base's points: spec 5.2's Enterprise column with the default title template and the native title bar (the
     * Enterprise kit names classic).
     */
    public static final ChromeSpec CLASSIC = new ChromeSpec(TitleBar.NATIVE, MenuBar.WINDOW, "{context}", ToolWindows.STRIPES,
        EditorTabs.CLASSIC, StatusBar.CELLS, PaletteKind.RUN_WINDOW, DialogKind.CLASSIC, MotionKind.NONE, Icons.CLASSIC,
        ToolbarSearch.FIELD);

    /**
     * Every point is required, the title is not blank, window menus need a native or classic title bar, and the toolbar's
     * search icon needs the Run window and a native or classic title bar (spec 5.3).
     */
    public ChromeSpec {
        Objects.requireNonNull(titleBar, "titleBar"); Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(windowTitle, "windowTitle"); Objects.requireNonNull(toolWindows, "toolWindows");
        Objects.requireNonNull(editorTabs, "editorTabs"); Objects.requireNonNull(statusBar, "statusBar");
        Objects.requireNonNull(palette, "palette"); Objects.requireNonNull(dialogs, "dialogs"); Objects.requireNonNull(motion, "motion");
        Objects.requireNonNull(icons, "icons"); Objects.requireNonNull(toolbarSearch, "toolbarSearch");
        if (windowTitle.isBlank()) throw new IllegalArgumentException("Blank window title template");
        if (menuBar == MenuBar.WINDOW && titleBar == TitleBar.UNIFIED)
            throw new IllegalArgumentException("menu_bar = window needs title_bar = native or classic");
        if (toolbarSearch == ToolbarSearch.ICON && (palette != PaletteKind.RUN_WINDOW || titleBar == TitleBar.UNIFIED))
            throw new IllegalArgumentException("toolbar_search = icon needs palette = run_window and title_bar = native or classic");
    }
}
