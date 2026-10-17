package dev.jasper.app.workspace;

import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.Projections;
import java.util.Objects;

/**
 * What a terminal window is made of under the look (Enterprise presentation spec 9.1): the workspace's chrome and the
 * shell's title facts.
 *
 * @param chrome the workspace strategies
 * @param unifiedTitle whether the frame draws the unified macOS title bar
 * @param followThemeAppearance whether a frame with the system's title bar keeps its macOS appearance in step with the theme
 */
public record WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance) {
    /** The chrome is required. */
    public WindowLook { Objects.requireNonNull(chrome, "chrome"); }

    /**
     * The look of {@code projections}.
     *
     * @param projections the presentation's projections
     * @return the windows' look
     */
    public static WindowLook of(Projections projections) {
        return new WindowLook(WorkspaceChrome.of(projections.workspace(), projections.title(), projections.palette()), projections.shell().unifiedTitle(),
            projections.shell().followThemeAppearance());
    }

    /**
     * The default look on this host.
     *
     * @return the default look's windows
     */
    public static WindowLook standard() { return new WindowLook(WorkspaceChrome.STANDARD, MacTitleBar.isSupported(), false); }
}
