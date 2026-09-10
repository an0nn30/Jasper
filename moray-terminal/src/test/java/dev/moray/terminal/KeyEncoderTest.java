package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class KeyEncoderTest {
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int META = InputEvent.META_DOWN_MASK;
    private static final BiFunction<Integer, Integer, byte[]> NO_SPECIAL_KEYS = (key, modifiers) -> null;

    private final KeyEncoder mac = new KeyEncoder(OptionAsMeta.LEFT, true);
    private final KeyEncoder linux = new KeyEncoder(OptionAsMeta.NONE, false);

    @Test
    void arrowsUseJediTermEncodingIncludingApplicationMode() throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, 20, 4, 10);
        session.startReading();
        try {
            assertThat(text(mac.pressed(key(KeyEvent.VK_UP, 0), session::codeForKey))).isEqualTo("\033[A");
            connector.feed("\033[?1h"); // application cursor keys on
            Await.until(() -> "\033OA".equals(text(mac.pressed(key(KeyEvent.VK_UP, 0), session::codeForKey))), "application-mode arrow");
            assertThat(text(mac.pressed(key(KeyEvent.VK_UP, SHIFT), session::codeForKey))).isEqualTo("\033[1;2A");
        } finally {
            session.close();
        }
    }

    @Test
    void specialKeysReceiveOnlyShiftCtrlAltBits() {
        int[] seen = new int[1];
        linux.pressed(key(KeyEvent.VK_F5, CTRL | InputEvent.BUTTON1_DOWN_MASK), (k, modifiers) -> {
            seen[0] = modifiers;
            return new byte[0];
        });
        assertThat(seen[0]).isEqualTo(CTRL);
    }

    @Test
    void editingKeys() {
        assertThat(text(mac.pressed(key(KeyEvent.VK_ENTER, 0), NO_SPECIAL_KEYS))).isEqualTo("\r");
        assertThat(text(mac.pressed(key(KeyEvent.VK_BACK_SPACE, 0), NO_SPECIAL_KEYS))).isEqualTo("\177");
        assertThat(text(mac.pressed(key(KeyEvent.VK_BACK_SPACE, CTRL), NO_SPECIAL_KEYS))).isEqualTo("\b");
        assertThat(text(mac.pressed(key(KeyEvent.VK_TAB, 0), NO_SPECIAL_KEYS))).isEqualTo("\t");
        assertThat(text(mac.pressed(key(KeyEvent.VK_TAB, SHIFT), NO_SPECIAL_KEYS))).isEqualTo("\033[Z");
        assertThat(text(mac.pressed(key(KeyEvent.VK_ESCAPE, 0), NO_SPECIAL_KEYS))).isEqualTo("\033");
        assertThat(text(mac.pressed(key(KeyEvent.VK_SPACE, CTRL), NO_SPECIAL_KEYS))).isEqualTo("\0");
    }

    @Test
    void commandKeysBelongToTheApp() {
        assertThat(mac.pressed(key(KeyEvent.VK_C, META), NO_SPECIAL_KEYS)).isNull();
        assertThat(mac.typed(typed('c', META))).isNull();
    }

    @Test
    void plainLettersAreLeftToKeyTyped() {
        assertThat(mac.pressed(key(KeyEvent.VK_A, 0), NO_SPECIAL_KEYS)).isNull();
        assertThat(text(mac.typed(typed('a', 0)))).isEqualTo("a");
    }

    @Test
    void typedCharactersAreUtf8() {
        assertThat(mac.typed(typed('é', 0))).containsExactly(0xC3, 0xA9);
        assertThat(mac.typed(typed('\003', CTRL))).containsExactly(0x03);
    }

    @Test
    void typedCharactersHandledOnPressAreIgnored() {
        assertThat(mac.typed(typed('\n', 0))).isNull();
        assertThat(mac.typed(typed('\b', 0))).isNull();
        assertThat(mac.typed(typed('\t', 0))).isNull();
        assertThat(mac.typed(typed('\033', 0))).isNull();
        assertThat(mac.typed(typed(KeyEvent.CHAR_UNDEFINED, 0))).isNull();
    }

    @Test
    void surrogatePairIsEncodedOnceComplete() {
        assertThat(mac.typed(typed('\uD83D', 0))).isNull();
        assertThat(text(mac.typed(typed('\uDE80', 0)))).isEqualTo("🚀");
    }

    @Test
    void leftOptionActsAsMetaWhenConfiguredLeft() {
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_F, 'ƒ', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033f");
        assertThat(mac.pressed(new KeyInput(KeyEvent.VK_F, 'ƒ', ALT, false, true), NO_SPECIAL_KEYS)).isNull();
    }

    @Test
    void optionAsMetaSettings() {
        KeyEncoder right = new KeyEncoder(OptionAsMeta.RIGHT, true);
        KeyEncoder both = new KeyEncoder(OptionAsMeta.BOTH, true);
        KeyEncoder none = new KeyEncoder(OptionAsMeta.NONE, true);
        assertThat(text(right.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, false, true), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(text(both.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(none.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, true, false), NO_SPECIAL_KEYS)).isNull();
    }

    @Test
    void metaVariants() {
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_F, 'Ï', ALT | SHIFT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033F");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_BACK_SPACE, '\b', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033\177");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_PERIOD, '≥', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033.");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_7, '¶', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\0337");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_X, '\030', ALT | CTRL, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033\030");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_TAB, '\t', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033\t");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_TAB, '\t', ALT | SHIFT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033[Z");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_ESCAPE, '\033', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033");
    }

    @Test
    void altIsMetaOnLinuxAndWindowsButAltGrIsNot() {
        assertThat(text(linux.pressed(new KeyInput(KeyEvent.VK_B, 'b', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(linux.pressed(new KeyInput(KeyEvent.VK_Q, '@', ALT | CTRL, false, true), NO_SPECIAL_KEYS)).isNull();
        assertThat(text(linux.typed(new KeyInput(KeyEvent.VK_UNDEFINED, '@', ALT | CTRL, false, true)))).isEqualTo("@");
    }

    private static KeyInput key(int keyCode, int modifiers) {
        return new KeyInput(keyCode, KeyEvent.CHAR_UNDEFINED, modifiers, false, false);
    }

    private static KeyInput typed(char c, int modifiers) {
        return new KeyInput(KeyEvent.VK_UNDEFINED, c, modifiers, false, false);
    }

    private static String text(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
