package dev.moray.terminal;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;

/** Turns key events into the bytes a terminal program expects. Event Dispatch Thread only. */
final class KeyEncoder {
    private static final int SHIFT_CTRL_ALT =
        InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
    private static final byte ESC = 0x1b;
    private static final Map<Integer, Character> PUNCTUATION = Map.ofEntries(
        Map.entry(KeyEvent.VK_PERIOD, '.'), Map.entry(KeyEvent.VK_COMMA, ','), Map.entry(KeyEvent.VK_SLASH, '/'),
        Map.entry(KeyEvent.VK_MINUS, '-'), Map.entry(KeyEvent.VK_EQUALS, '='), Map.entry(KeyEvent.VK_SEMICOLON, ';'),
        Map.entry(KeyEvent.VK_QUOTE, '\''), Map.entry(KeyEvent.VK_OPEN_BRACKET, '['),
        Map.entry(KeyEvent.VK_CLOSE_BRACKET, ']'), Map.entry(KeyEvent.VK_BACK_SLASH, '\\'),
        Map.entry(KeyEvent.VK_BACK_QUOTE, '`'));

    private final OptionAsMeta optionAsMeta;
    private final boolean macOs;
    private char pendingHighSurrogate;

    KeyEncoder(OptionAsMeta optionAsMeta, boolean macOs) {
        this.optionAsMeta = optionAsMeta;
        this.macOs = macOs;
    }

    /** Bytes for a KEY_PRESSED event, or null to leave the key to KEY_TYPED (or to the app). */
    byte[] pressed(KeyInput key, BiFunction<Integer, Integer, byte[]> specialKeys) {
        if (key.meta()) {
            return null;
        }
        int code = key.keyCode();
        if (isSpecialKey(code)) {
            return specialKeys.apply(code, key.modifiers() & SHIFT_CTRL_ALT);
        }
        boolean asMeta = altActsAsMeta(key);
        byte[] plain = switch (code) {
            case KeyEvent.VK_ENTER -> new byte[] {'\r'};
            case KeyEvent.VK_BACK_SPACE -> new byte[] {key.ctrl() ? (byte) 0x08 : (byte) 0x7f};
            case KeyEvent.VK_TAB -> key.shift() ? "\033[Z".getBytes(StandardCharsets.US_ASCII) : new byte[] {'\t'};
            case KeyEvent.VK_ESCAPE -> new byte[] {ESC};
            case KeyEvent.VK_SPACE -> key.ctrl() ? new byte[] {0} : asMeta ? new byte[] {' '} : null;
            default -> asMeta ? metaCharacter(key) : null;
        };
        if (plain == null) {
            return null;
        }
        return asMeta && code != KeyEvent.VK_ESCAPE ? prefixEscape(plain) : plain;
    }

    /** Bytes for a KEY_TYPED event that {@link #pressed} did not already handle, or null. */
    byte[] typed(KeyInput key) {
        if (key.meta()) {
            return null;
        }
        char c = key.keyChar();
        if (c == KeyEvent.CHAR_UNDEFINED || c == '\n' || c == '\r' || c == '\b' || c == 0x7f || c == '\t' || c == ESC) {
            return null;
        }
        if (Character.isHighSurrogate(c)) {
            pendingHighSurrogate = c;
            return null;
        }
        if (Character.isLowSurrogate(c) && pendingHighSurrogate != 0) {
            String pair = new String(new char[] {pendingHighSurrogate, c});
            pendingHighSurrogate = 0;
            return pair.getBytes(StandardCharsets.UTF_8);
        }
        pendingHighSurrogate = 0;
        return String.valueOf(c).getBytes(StandardCharsets.UTF_8);
    }

    private boolean altActsAsMeta(KeyInput key) {
        if (!key.alt()) {
            return false;
        }
        if (!macOs) {
            return !key.ctrl(); // Ctrl+Alt is AltGr on Linux/Windows layouts
        }
        return switch (optionAsMeta) {
            case LEFT -> key.leftAltHeld();
            case RIGHT -> key.rightAltHeld();
            case BOTH -> true;
            case NONE -> false;
        };
    }

    private static byte[] metaCharacter(KeyInput key) {
        int code = key.keyCode();
        char c;
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            c = key.ctrl()
                ? (char) (code - KeyEvent.VK_A + 1)
                : (char) ((key.shift() ? 'A' : 'a') + (code - KeyEvent.VK_A));
        } else if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            c = (char) ('0' + (code - KeyEvent.VK_0));
        } else {
            Character punctuation = PUNCTUATION.get(code);
            if (punctuation == null) {
                return null;
            }
            c = punctuation;
        }
        return new byte[] {(byte) c};
    }

    private static byte[] prefixEscape(byte[] bytes) {
        byte[] result = new byte[bytes.length + 1];
        result[0] = ESC;
        System.arraycopy(bytes, 0, result, 1, bytes.length);
        return result;
    }

    private static boolean isSpecialKey(int code) {
        return switch (code) {
            case KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                 KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
                 KeyEvent.VK_INSERT, KeyEvent.VK_DELETE,
                 KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
                 KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12 -> true;
            default -> false;
        };
    }
}
