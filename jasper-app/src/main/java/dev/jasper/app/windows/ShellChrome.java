package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.MacAppearance;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;

/**
 * The window shells' chrome under the look (Enterprise presentation spec 9.1 and 10.1), made from {@link ShellLook}'s
 * values. It says whether auxiliary frames, dialogs and the file chooser draw the unified macOS title bar, whether an
 * auxiliary frame shows a menu row of its own, and keeps a frame that has the system's title bar in step with the theme's
 * darkness on macOS. Jasper gives the Windows and Linux system title bar no appearance hint: JBR has none. EDT only.
 *
 * @param unifiedTitle whether frames and dialogs draw the unified macOS title bar
 * @param classicMenus whether the look's terminal windows show the classic menu row; its auxiliary frames show none
 * @param followTheme whether a frame with the system's title bar follows the theme's darkness
 * @param classicTitle whether the look draws IntelliJ Classic's title: on Windows its auxiliary frames and dialogs have
 *     FlatLaf's title panes, and in-window dialogs start below them
 * @param macOs whether this is macOS
 */
public record ShellChrome(boolean unifiedTitle, boolean classicMenus, boolean followTheme, boolean classicTitle, boolean macOs) {
    /**
     * The shells of {@code look} on this host.
     *
     * @param look the shells' projection
     * @return the shells' chrome
     */
    public static ShellChrome of(ShellLook look) { return of(look, SystemInfo.isMacOS); }

    /** The shells of {@code look} as on macOS or not; for tests. */
    static ShellChrome of(ShellLook look, boolean macOs) {
        return new ShellChrome(look.unifiedTitle(), look.windowMenus(), look.followThemeAppearance(), look.classicTitle(), macOs);
    }

    /**
     * The default look's shells on this host.
     *
     * @return the default look's shells
     */
    public static ShellChrome standard() { return new ShellChrome(MacTitleBar.isSupported(), false, false, false, SystemInfo.isMacOS); }

    /**
     * Gives {@code root}'s frame the theme's macOS appearance when this look follows the theme and the frame has the
     * system's title bar ({@code bar} is null); otherwise does nothing.
     */
    void followTheme(JRootPane root, MacTitleBar bar, boolean dark) {
        if (!followTheme || !macOs || bar != null) return;
        MacAppearance.follow(root, dark);
    }

    /**
     * Whether an auxiliary frame shows its own menu row, the minimal File menu: the default look's frames do; a classic
     * look's show none, as the 07 Credential Vault mocks draw them, and bind Close Window's key instead (spec 10.1).
     */
    boolean frameMenuRow() { return !classicMenus; }
}
