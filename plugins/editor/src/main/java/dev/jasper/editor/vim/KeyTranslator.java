package dev.jasper.editor.vim;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * Turns AWT key events into {@link KeyInput}s (spec 2.1). Named keys and Ctrl, Alt and ⌘ combinations come from
 * {@code KEY_PRESSED}; characters come from {@code KEY_TYPED}, so layouts, Shift and macOS Option characters are right.
 * It takes the event's parts rather than the event, so tests need no component.
 */
final class KeyTranslator {
    private KeyTranslator() { }

    /**
     * @param keyCode the event's key code
     * @param modifiersEx the event's extended modifiers
     * @param mac whether this is macOS, where Option composes characters instead of acting as Alt
     * @return the key for a {@code KEY_PRESSED} event, or empty when the key arrives as a {@code KEY_TYPED} character
     */
    static Optional<KeyInput> pressed(int keyCode, int modifiersEx, boolean mac) {
        int modifiers = modifiers(modifiersEx, mac);
        String name = switch (keyCode) {
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_ENTER -> "CR";
            case KeyEvent.VK_TAB -> "Tab";
            case KeyEvent.VK_BACK_SPACE -> "BS";
            case KeyEvent.VK_DELETE -> "Del";
            case KeyEvent.VK_INSERT -> "Insert";
            case KeyEvent.VK_HOME -> "Home";
            case KeyEvent.VK_END -> "End";
            case KeyEvent.VK_PAGE_UP -> "PageUp";
            case KeyEvent.VK_PAGE_DOWN -> "PageDown";
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> "Up";
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> "Down";
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> "Left";
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> "Right";
            default -> keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12 ? "F" + (keyCode - KeyEvent.VK_F1 + 1) : null;
        };
        if (name != null) return Optional.of(KeyInput.normalize(name, modifiers));
        if ((modifiers & (KeyInput.CTRL | KeyInput.ALT | KeyInput.META)) == 0) return Optional.empty();
        // AltGr (Ctrl+Alt on Windows) types characters such as @ on German layouts: those arrive as KEY_TYPED.
        if (altGraph(modifiersEx, mac)) return Optional.empty();
        String key = keyText(keyCode, modifiers);
        return key == null ? Optional.empty() : Optional.of(KeyInput.normalize(key, modifiers));
    }

    /**
     * @param codePoint the typed character (surrogate pairs already combined)
     * @param modifiersEx the event's extended modifiers
     * @param mac whether this is macOS
     * @return the character, or empty for control characters and for Ctrl, Alt and ⌘ combinations, which
     *     {@link #pressed} reported
     */
    static Optional<KeyInput> typed(int codePoint, int modifiersEx, boolean mac) {
        if (codePoint == KeyEvent.CHAR_UNDEFINED || codePoint < 0x20 || codePoint == 0x7f) return Optional.empty();
        int modifiers = modifiers(modifiersEx, mac);
        if (!altGraph(modifiersEx, mac) && (modifiers & (KeyInput.CTRL | KeyInput.ALT | KeyInput.META)) != 0) return Optional.empty();
        return Optional.of(KeyInput.of(codePoint));
    }

    static int modifiers(int modifiersEx, boolean mac) {
        int modifiers = 0;
        if ((modifiersEx & InputEvent.SHIFT_DOWN_MASK) != 0) modifiers |= KeyInput.SHIFT;
        if ((modifiersEx & InputEvent.CTRL_DOWN_MASK) != 0) modifiers |= KeyInput.CTRL;
        if (!mac && (modifiersEx & InputEvent.ALT_DOWN_MASK) != 0) modifiers |= KeyInput.ALT;
        if ((modifiersEx & InputEvent.META_DOWN_MASK) != 0) modifiers |= KeyInput.META;
        return modifiers;
    }

    private static boolean altGraph(int modifiersEx, boolean mac) {
        if ((modifiersEx & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) return true;
        int both = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
        return !mac && (modifiersEx & both) == both;
    }

    /** @return the character a key code stands for with modifiers held, or null */
    private static String keyText(int keyCode, int modifiers) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return String.valueOf((char) ('a' + keyCode - KeyEvent.VK_A));
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            // Ctrl+6 is how keyboards type <C-^>.
            if (keyCode == KeyEvent.VK_6 && (modifiers & KeyInput.CTRL) != 0) return "^";
            return String.valueOf((char) ('0' + keyCode - KeyEvent.VK_0));
        }
        return switch (keyCode) {
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            case KeyEvent.VK_BACK_SLASH -> "\\";
            case KeyEvent.VK_SPACE -> " ";
            case KeyEvent.VK_MINUS -> "-";
            case KeyEvent.VK_EQUALS -> "=";
            case KeyEvent.VK_COMMA -> ",";
            case KeyEvent.VK_PERIOD -> ".";
            case KeyEvent.VK_SLASH -> "/";
            case KeyEvent.VK_SEMICOLON -> ";";
            case KeyEvent.VK_QUOTE -> "'";
            case KeyEvent.VK_BACK_QUOTE -> "`";
            case KeyEvent.VK_CIRCUMFLEX -> "^";
            default -> null;
        };
    }
}
