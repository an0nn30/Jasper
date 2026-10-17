package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.MacAppearance;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ShellLook;
import dev.jasper.app.platform.ClassicMenuRow;
import javax.swing.JMenuBar;
import javax.swing.JRootPane;

/**
 * The window shells' chrome under the look (Enterprise presentation spec 9.1 and 10.1), made from {@link ShellLook}'s
 * values. It says whether auxiliary frames, dialogs and the file chooser draw the unified macOS title bar, whether a
 * frame's own menu row is the classic one, and keeps a frame that has the system's title bar in step with the theme's
 * darkness on macOS. Jasper gives the Windows and Linux system title bar no appearance hint: JBR has none. EDT only.
 *
 * @param unifiedTitle whether frames and dialogs draw the unified macOS title bar
 * @param classicMenus whether a frame's menu row is the classic row of the look's terminal windows
 * @param followTheme whether a frame with the system's title bar follows the theme's darkness
 * @param macOs whether this is macOS
 */
public record ShellChrome(boolean unifiedTitle, boolean classicMenus, boolean followTheme, boolean macOs) {
    /**
     * The shells of {@code look} on this host.
     *
     * @param look the shells' projection
     * @return the shells' chrome
     */
    public static ShellChrome of(ShellLook look) { return of(look, SystemInfo.isMacOS); }

    /** The shells of {@code look} as on macOS or not; for tests. */
    static ShellChrome of(ShellLook look, boolean macOs) {
        return new ShellChrome(look.unifiedTitle(), look.windowMenus(), look.followThemeAppearance(), macOs);
    }

    /**
     * The default look's shells on this host.
     *
     * @return the default look's shells
     */
    public static ShellChrome standard() { return new ShellChrome(MacTitleBar.isSupported(), false, false, SystemInfo.isMacOS); }

    /**
     * Gives {@code root}'s frame the theme's macOS appearance when this look follows the theme and the frame has the
     * system's title bar ({@code bar} is null); otherwise does nothing.
     */
    void followTheme(JRootPane root, MacTitleBar bar, boolean dark) {
        if (!followTheme || !macOs || bar != null) return;
        MacAppearance.follow(root, dark);
    }

    /** Styles {@code bar}, a frame's own menu row, as the look's terminal windows style theirs; the default look's stays as it is. */
    void styleMenus(JMenuBar bar) {
        if (classicMenus) ClassicMenuRow.style(bar);
    }
}
