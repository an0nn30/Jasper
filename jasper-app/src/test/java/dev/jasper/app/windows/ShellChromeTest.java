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

    @Test void theTitleBarIsTheLooks() {
        assertThat(ShellChrome.of(new ShellLook(true, false, false), true).unifiedTitle()).isTrue();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).unifiedTitle()).isFalse();
        assertThat(ShellChrome.standard().unifiedTitle()).isEqualTo(dev.jasper.app.platform.MacTitleBar.isSupported());
    }
}
