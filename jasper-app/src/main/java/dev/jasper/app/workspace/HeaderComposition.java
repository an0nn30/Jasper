package dev.jasper.app.workspace;

import dev.jasper.app.config.ToolbarMode;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;

/**
 * How a window's header row is composed (Enterprise presentation spec 10.3): today's unified header, or the classic
 * toolbar row under a native title. One instance per window; the window's chrome builds it once. EDT only.
 */
sealed interface HeaderComposition permits UnifiedHeader, ClassicHeader {
    /** Fills the window's toolbars with the built-in controls; contributed controls follow the plugin group. */
    void build(WindowChrome chrome);

    /** Whether the header takes the title bar's colours ({@code ThemeZone.titleBar}). */
    boolean titleBarZone();

    /** The header's minimum size. */
    Dimension minimumSize(WindowHeader header);

    /** Lays the header's children out. */
    void layout(WindowHeader header);

    /** The search field's preferred size, in device pixels. */
    Dimension searchPreferred();

    /** The search field's minimum size before it collapses, in device pixels. */
    Dimension searchMinimum();

    /** The search field's maximum size, in device pixels. */
    Dimension searchMaximum();

    /** Paints the search field. */
    void paintSearch(TitleSearchField field, Graphics graphics, boolean hover, boolean collapsed);

    /** Applies the toolbar setting: which rows show and whether buttons carry labels; {@code normal} is the Normal view mode. */
    void toolbarMode(WindowHeader header, ToolbarMode mode, boolean normal);

    /** Restyles after a theme change. */
    void refreshTheme(WindowHeader header);

    /**
     * A contributed toolbar button or dropdown in this composition's style; the window adds its entry and, for a
     * dropdown, its menu.
     *
     * @param chrome the window's chrome
     * @param action the button's action, or null for a dropdown
     * @param label the label
     * @param icon the icon, or null for the default command icon
     * @param dropdown whether the button opens a menu
     * @return the button
     */
    JButton contributed(WindowChrome chrome, Action action, String label, Icon icon, boolean dropdown);
}
