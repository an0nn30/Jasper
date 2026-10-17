package dev.jasper.app.windows;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 10.1: the shells' values under each look. */
class ShellChromeTest {
    @Test void aNativeTitledLookFollowsTheThemeOnMacOsOnly() throws Exception {
        edt(() -> {
            var root = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, true);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, false);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            var other = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), false).followTheme(other, null, true);
            ShellChrome.of(new ShellLook(false, false, false), true).followTheme(other, null, true);
            assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("off macOS, or a look that does not follow").isNull();
        });
    }

    /** Spec 10.1: under in-window classic menus an auxiliary frame's File row is the terminal windows' row; the default look's stays FlatLaf's. */
    @Test void aClassicLookGivesAFramesMenuRowTheTerminalWindowsRow() throws Exception {
        edt(() -> {
            var themes = dev.jasper.app.appearance.ThemeTestSupport.controller(true);
            try {
                var classic = new javax.swing.JMenuBar();
                ShellChrome.of(new ShellLook(false, true, true), true).styleMenus(classic);
                var reference = new javax.swing.JMenuBar();
                dev.jasper.app.platform.ClassicMenuRow.style(reference);
                assertThat(classic.getClientProperty("FlatLaf.style")).isEqualTo(reference.getClientProperty("FlatLaf.style")).isNotNull();
                assertThat(classic.getBackground()).isEqualTo(reference.getBackground());
                assertThat(classic.getInsets()).isEqualTo(reference.getInsets());
                var standard = new javax.swing.JMenuBar();
                var border = standard.getBorder();
                ShellChrome.of(new ShellLook(false, false, false), true).styleMenus(standard);
                ShellChrome.standard().styleMenus(standard);
                assertThat(standard.getClientProperty("FlatLaf.style")).isNull();
                assertThat(standard.getBorder()).isSameAs(border);
            } finally { themes.close(); }
        });
    }

    @Test void theTitleBarIsTheLooks() {
        assertThat(ShellChrome.of(new ShellLook(true, false, false), true).unifiedTitle()).isTrue();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).unifiedTitle()).isFalse();
        assertThat(ShellChrome.standard().unifiedTitle()).isEqualTo(dev.jasper.app.platform.MacTitleBar.isSupported());
    }
}
