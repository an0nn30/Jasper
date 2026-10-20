package dev.jasper.vault.ui;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LockShortcutTest {
    @Test void theCaptionSpellsTheDefaultAsEachPlatformResolvesIt() {
        assertThat(LockShortcut.caption(LockShortcut.BINDING, true)).isEqualTo("\u21E7\u2318L");
        assertThat(LockShortcut.caption(LockShortcut.BINDING, false)).as("cmd+shift gains alt off macOS").isEqualTo("Ctrl+Alt+Shift+L");
        assertThat(LockShortcut.caption("cmd+\\", false)).isEqualTo("Ctrl+Shift+\\");
        assertThat(LockShortcut.caption("cmd+alt+j", true)).isEqualTo("\u2325\u2318J");
        assertThat(LockShortcut.caption("ctrl+alt+l", false)).isEqualTo("Ctrl+Alt+L");
    }
}
