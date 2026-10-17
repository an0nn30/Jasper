package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;

/**
 * The window shells' chrome under the look (Enterprise presentation spec 9.1 and 10.1): the one reader of
 * {@link ShellLook}. It says whether auxiliary frames, dialogs and the file chooser draw the unified macOS title bar,
 * and keeps a frame that has the system's title bar in step with the theme's darkness on macOS. On Windows and Linux the
 * system title bar follows the operating system's light or dark setting: JBR has no hint for a title bar it does not
 * draw. EDT only.
 */
public final class ShellChrome {
    private final boolean unifiedTitle, followTheme, macOs;

    private ShellChrome(boolean unifiedTitle, boolean followTheme, boolean macOs) {
        this.unifiedTitle = unifiedTitle; this.followTheme = followTheme; this.macOs = macOs;
    }

    /** The shells of {@code look} on this host. */
    public static ShellChrome of(ShellLook look) { return of(look, SystemInfo.isMacOS); }

    /** The shells of {@code look} as on macOS or not; for tests. */
    static ShellChrome of(ShellLook look, boolean macOs) { return new ShellChrome(look.unifiedTitle(), look.followThemeAppearance(), macOs); }

    /** The default look's shells on this host. */
    public static ShellChrome standard() { return new ShellChrome(MacTitleBar.isSupported(), false, SystemInfo.isMacOS); }

    /** Whether frames and dialogs draw the unified macOS title bar. */
    public boolean unifiedTitle() { return unifiedTitle; }

    /**
     * Gives {@code root}'s frame the theme's macOS appearance when this look follows the theme and the frame has the
     * system's title bar ({@code bar} is null); otherwise does nothing.
     */
    void followTheme(JRootPane root, MacTitleBar bar, boolean dark) {
        if (!followTheme || !macOs || bar != null) return;
        root.putClientProperty("apple.awt.windowAppearance", dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
    }
}
