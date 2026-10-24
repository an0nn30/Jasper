package dev.jasper.editor.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KeyNamesTest {
    @Test void macUsesSymbolsAndOtherPlatformsSpellModifiersOut() {
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK), true)).isEqualTo("⌥⌘S");
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.META_DOWN_MASK), true)).isEqualTo("⌘/");
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false))
            .isEqualTo("Ctrl+Shift+]");
        assertThat(KeyNames.stroke(KeyEvent.VK_O, 0).getModifiers() & KeyNames.MENU).isEqualTo(KeyNames.MENU);
    }

    @Test void findAgainIsCmdGOnMacOsAndF3Elsewhere() {
        assertThat(KeyNames.findAgain(false, true)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK));
        assertThat(KeyNames.findAgain(true, true))
            .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        // Ctrl+Shift+G is the window's Find Next on Linux and Windows, so the Editor's Find Previous must not take it.
        assertThat(KeyNames.findAgain(false, false)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        assertThat(KeyNames.findAgain(true, false)).isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.SHIFT_DOWN_MASK));
        assertThat(KeyNames.describe(KeyNames.findAgain(true, false), false)).isEqualTo("Shift+F3");
        assertThat(KeyNames.describe(KeyNames.findAgain(true, true), true)).isEqualTo("⇧⌘G");
    }
}
