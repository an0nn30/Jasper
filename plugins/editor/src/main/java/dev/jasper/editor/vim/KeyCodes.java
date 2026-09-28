package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keys as Vim keeps them in a register's text (spec 4.3): {@code q} records keys into a register as text, and
 * {@code @} reads the text back as keys. A character is itself; CTRL with a letter is its control character
 * ({@code <C-a>} is U+0001, {@code <Esc>} U+001B, {@code <CR>} U+000D, {@code <C-j>} a line break); other named keys
 * are Vim's K_SPECIAL sequences, with U+0080 standing for the 0x80 byte ({@code <BS>} is U+0080 {@code kb}), so
 * {@code :registers} shows a macro as Vim does ({@code <80>kb}).
 */
final class KeyCodes {
    private KeyCodes() { }

    private static final char SPECIAL = (char) 0x80, MODIFIER = (char) 0xFC, EXTRA = (char) 0xFD;
    /** Vim's MOD_MASK_* bits. */
    private static final int SHIFT = 0x02, CTRL = 0x04, ALT = 0x08, CMD = 0x80;

    /** Each key with its own code (keymap.h's termcap names and KE_ values), by notation. */
    private static final Map<String, String> CODES = new HashMap<>();
    private static final Map<String, KeyInput> KEYS = new HashMap<>();

    private static void code(String notation, String code) {
        CODES.put(notation, code);
        KEYS.put(code, KeyInput.parse(notation).getFirst());
    }

    private static void extra(String notation, int value) { code(notation, String.valueOf(EXTRA) + (char) value); }

    static {
        code("<BS>", "kb");
        code("<Del>", "kD");
        code("<Insert>", "kI");
        code("<Home>", "kh");
        code("<End>", "@7");
        code("<PageUp>", "kP");
        code("<PageDown>", "kN");
        code("<Up>", "ku");
        code("<Down>", "kd");
        code("<Left>", "kl");
        code("<Right>", "kr");
        for (int f = 1; f <= 9; f++) code("<F" + f + ">", "k" + f);
        code("<F10>", "k;");
        code("<F11>", "F1");
        code("<F12>", "F2");
        code("<S-Tab>", "kB");
        code("<S-Left>", "#4");
        code("<S-Right>", "%i");
        code("<S-Home>", "#2");
        code("<S-End>", "*7");
        extra("<S-Up>", 4);
        extra("<S-Down>", 5);
        for (int f = 1; f <= 12; f++) extra("<S-F" + f + ">", 5 + f);
        extra("<C-Left>", 85);
        extra("<C-Right>", 86);
        extra("<C-Home>", 87);
        extra("<C-End>", 88);
        extra("<Plug>", 83);
        extra("<S-BS>", 105);
    }

    /**
     * @param keys keys, as typed
     * @return them as a register's text
     */
    static String text(List<KeyInput> keys) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : keys) out.append(text(key));
        return out.toString();
    }

    private static String text(KeyInput key) {
        String code = CODES.get(key.notation());
        if (code != null) return SPECIAL + code;
        int modifiers = key.modifiers();
        if (key.isChar()) {
            if (modifiers == 0) return key.key();
            if (modifiers == KeyInput.CTRL) {
                char c = key.key().charAt(0);
                if (c == '?') return String.valueOf((char) 0x7f);
                if (c == '@' || c >= 'a' && c <= 'z' || c >= '[' && c <= '_') return String.valueOf((char) (Character.toUpperCase(c) - '@'));
            }
        } else if (modifiers == 0) {
            switch (key.key()) {
                case "Esc" -> { return "\033"; }
                case "CR" -> { return "\r"; }
                case "Tab" -> { return "\t"; }
                default -> { }
            }
        }
        // Any other key with modifiers: K_SPECIAL KS_MODIFIER {mask} and the key without them.
        int mask = ((modifiers & KeyInput.SHIFT) != 0 ? SHIFT : 0) | ((modifiers & KeyInput.CTRL) != 0 ? CTRL : 0)
            | ((modifiers & KeyInput.ALT) != 0 ? ALT : 0) | ((modifiers & KeyInput.META) != 0 ? CMD : 0);
        return "" + SPECIAL + MODIFIER + (char) mask + text(new KeyInput(key.key(), 0));
    }

    /**
     * @param text a register's text
     * @return the keys it holds
     */
    static List<KeyInput> keys(String text) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int[] end = { i };
            keys.add(key(text, end));
            i = end[0];
        }
        return keys;
    }

    /** The key at {@code at[0]}, which moves past it. */
    private static KeyInput key(String text, int[] at) {
        int i = at[0];
        char c = text.charAt(i);
        if (c == SPECIAL && i + 2 < text.length() && text.charAt(i + 1) == MODIFIER && i + 3 < text.length()) {
            int mask = text.charAt(i + 2);
            at[0] = i + 3;
            KeyInput base = key(text, at);
            int modifiers = ((mask & SHIFT) != 0 ? KeyInput.SHIFT : 0) | ((mask & CTRL) != 0 ? KeyInput.CTRL : 0)
                | ((mask & ALT) != 0 ? KeyInput.ALT : 0) | ((mask & CMD) != 0 ? KeyInput.META : 0);
            return KeyInput.normalize(base.key(), base.modifiers() | modifiers);
        }
        if (c == SPECIAL && i + 2 < text.length()) {
            KeyInput named = KEYS.get(text.substring(i + 1, i + 3));
            if (named != null) {
                at[0] = i + 3;
                return named;
            }
        }
        int cp = text.codePointAt(i);
        at[0] = i + Character.charCount(cp);
        if (cp == 0) return new KeyInput("@", KeyInput.CTRL);
        if (cp == '\n') return new KeyInput("j", KeyInput.CTRL);
        if (cp < 0x20) return KeyInput.ctrl((char) Character.toLowerCase(cp + '@'));
        if (cp == 0x7f) return KeyInput.named("Del");
        return KeyInput.of(cp);
    }

    /**
     * How Vim shows a register's text (msg_outtrans on its bytes): control characters as {@code ^X}, and the bytes
     * of a K_SPECIAL sequence that are not ASCII as {@code <80>}.
     *
     * @return one piece for each code point of the text
     */
    static List<String> shown(String text) {
        List<String> pieces = new ArrayList<>();
        int special = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean inSpecial = special > 0;
            special = cp == SPECIAL ? 2 : Math.max(0, special - 1);
            if (cp < 0x20) pieces.add("^" + (char) (cp + '@'));
            else if (cp == 0x7f) pieces.add("^?");
            else if (cp >= 0x80 && cp < 0xa0 || inSpecial && cp >= 0x80 && cp <= 0xff) pieces.add(String.format("<%02x>", cp));
            else pieces.add(new String(Character.toChars(cp)));
        }
        return pieces;
    }

    /** @return the cells a piece from {@link #shown} takes */
    static int cells(String piece) {
        return piece.codePointCount(0, piece.length()) == 1 ? Text.width(piece.codePointAt(0)) : piece.length();
    }
}
