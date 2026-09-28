package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KeyTranslatorTest {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK, ALT = InputEvent.ALT_DOWN_MASK, SHIFT = InputEvent.SHIFT_DOWN_MASK, META = InputEvent.META_DOWN_MASK;

    @Test void namedKeysComeFromKeyPressed() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_ESCAPE, 0, true)).contains(KeyInput.named("Esc"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_TAB, SHIFT, false)).contains(new KeyInput("Tab", KeyInput.SHIFT));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_F5, 0, false)).contains(KeyInput.named("F5"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_KP_LEFT, 0, false)).contains(KeyInput.named("Left"));
    }

    @Test void ctrlCombinationsComeFromKeyPressed() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_W, CTRL, true)).contains(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_W, CTRL | SHIFT, false)).contains(new KeyInput("w", KeyInput.CTRL));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_OPEN_BRACKET, CTRL, true)).contains(KeyInput.named("Esc"));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_6, CTRL, false)).contains(new KeyInput("^", KeyInput.CTRL));
    }

    @Test void plainLettersArriveAsTypedCharacters() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_A, 0, false)).isEmpty();
        assertThat(KeyTranslator.pressed(KeyEvent.VK_A, SHIFT, false)).isEmpty();
        assertThat(KeyTranslator.typed('A', SHIFT, false)).contains(KeyInput.of('A'));
    }

    @Test void altIsAKeyOnLinuxAndWindowsButTypesCharactersOnMac() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_X, ALT, false)).contains(new KeyInput("x", KeyInput.ALT));
        assertThat(KeyTranslator.pressed(KeyEvent.VK_E, ALT, true)).isEmpty();
        assertThat(KeyTranslator.typed('é', ALT, true)).contains(KeyInput.of(0xe9));
        assertThat(KeyTranslator.typed('x', ALT, false)).isEmpty();
    }

    @Test void altGraphTypesCharacters() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_Q, CTRL | ALT, false)).isEmpty();
        assertThat(KeyTranslator.typed('@', CTRL | ALT, false)).contains(KeyInput.of('@'));
    }

    @Test void commandKeysCarryMeta() {
        assertThat(KeyTranslator.pressed(KeyEvent.VK_S, META, true)).contains(new KeyInput("s", KeyInput.META));
        assertThat(KeyTranslator.typed('s', META, true)).isEmpty();
    }

    @Test void controlCharactersAreNotTyped() {
        assertThat(KeyTranslator.typed(0x17, CTRL, true)).isEmpty();
        assertThat(KeyTranslator.typed(KeyEvent.CHAR_UNDEFINED, 0, false)).isEqualTo(Optional.empty());
        assertThat(KeyTranslator.typed(0x7f, 0, false)).isEmpty();
    }
}
