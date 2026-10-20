package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;

/**
 * Facts about the look Jasper runs with, read through {@link Appearance#presentation()}. The look is chosen at start
 * and stays for the run. The faces, the body size and the toolbar height are the look's own: the user's settings for
 * them are not reported.
 *
 * @param classic whether the look starts from the classic catalog: classic controls and IntelliJ Classic's layout
 * @param icons the icon set the look paints with
 * @param toolWindows how tool windows are reached
 * @param titleBar the title bar on this platform
 * @param menus where the menus are on this platform
 * @param toolbarSearch how the toolbar offers Search Everywhere
 * @param uiFace the look's UI face for its regular weight, a name {@code java.awt.Font} accepts, or empty for the
 *     platform face; the user's font setting is not reported
 * @param dataFace the look's data face for its regular weight, a name {@code java.awt.Font} accepts, or empty for the
 *     platform face ({@code Font.MONOSPACED}); the user's font setting is not reported
 * @param bodySize the look's body text size in points, from 6 to 96; the user's font size is not reported
 * @param toolbarHeight the look's default toolbar height in pixels, from 28 to 64; the user's toolbar height is not
 *     reported
 * @since 0.18.2
 */
public record Presentation(boolean classic, Icons icons, ToolWindows toolWindows, TitleBar titleBar, Menus menus,
                           ToolbarSearch toolbarSearch, Optional<String> uiFace, Optional<String> dataFace,
                           int bodySize, int toolbarHeight) {
    /** Checks every fact.
     * @throws NullPointerException when a part is null
     * @throws IllegalArgumentException when a present face is blank, or a size is outside its range
     */
    public Presentation {
        Objects.requireNonNull(icons, "icons");
        Objects.requireNonNull(toolWindows, "toolWindows");
        Objects.requireNonNull(titleBar, "titleBar");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(toolbarSearch, "toolbarSearch");
        Objects.requireNonNull(uiFace, "uiFace");
        Objects.requireNonNull(dataFace, "dataFace");
        if (uiFace.filter(String::isBlank).isPresent()) throw new IllegalArgumentException("uiFace: a present face must not be blank");
        if (dataFace.filter(String::isBlank).isPresent()) throw new IllegalArgumentException("dataFace: a present face must not be blank");
        if (bodySize < 6 || bodySize > 96) throw new IllegalArgumentException("bodySize: " + bodySize + " is outside 6-96");
        if (toolbarHeight < 28 || toolbarHeight > 64)
            throw new IllegalArgumentException("toolbarHeight: " + toolbarHeight + " is outside 28-64");
    }

    /** The icon set the look paints with.
     * @since 0.18.2
     */
    public enum Icons {
        /** The New UI icons, the default look's. */
        NEW_UI,
        /** IntelliJ's classic icons. */
        CLASSIC
    }

    /** How tool windows are reached.
     * @since 0.18.2
     */
    public enum ToolWindows {
        /** Icon rails beside the workspace, the default look's. */
        RAILS,
        /** Labelled stripes along the window's edges, as in IntelliJ Classic. */
        STRIPES
    }

    /** The title bar on this platform.
     * @since 0.18.2
     */
    public enum TitleBar {
        /** One band that holds the title and the toolbar, the default look's. */
        UNIFIED,
        /** The platform's own title bar. */
        NATIVE,
        /** IntelliJ Classic's title bar. */
        CLASSIC
    }

    /** Where the menus are on this platform.
     * @since 0.18.2
     */
    public enum Menus {
        /** The macOS screen menu bar. */
        SCREEN_MENU_BAR,
        /** In the window's title bar. */
        TITLE_BAR,
        /** A menu bar row in the window, below the title. */
        WINDOW
    }

    /** How the toolbar offers Search Everywhere.
     * @since 0.18.2
     */
    public enum ToolbarSearch {
        /** A search field. */
        FIELD,
        /** An icon button. */
        ICON
    }
}
