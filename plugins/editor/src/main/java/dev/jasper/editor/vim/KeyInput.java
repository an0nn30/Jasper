package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One key as Vim names it (spec 2.1): a character, or a named key such as {@code Esc}, with modifiers. A character
 * never carries Shift (it is part of the character), and Ctrl with a letter always holds the lower-case letter, so
 * equal keys are equal records.
 *
 * @param key one code point as a string, a name from {@link #NAMES}, or {@code Action(id)} for an Editor command a
 *     mapping runs ({@code <Action>(id)}, spec 7.2)
 * @param modifiers {@link #SHIFT}, {@link #CTRL}, {@link #ALT} and {@link #META} combined
 */
public record KeyInput(String key, int modifiers) {
    /** Shift, only on named keys ({@code <S-Tab>}). */
    public static final int SHIFT = 1;
    /** Ctrl ({@code <C-w>}). */
    public static final int CTRL = 2;
    /** Alt or Meta ({@code <M-x>}); never produced on macOS, where Option types characters. */
    public static final int ALT = 4;
    /** ⌘ on macOS ({@code <D-s>}). */
    public static final int META = 8;
    /** The named keys, in their canonical spelling. */
    public static final List<String> NAMES = List.of("Esc", "CR", "Tab", "BS", "Del", "Insert", "Home", "End", "PageUp", "PageDown",
        "Up", "Down", "Left", "Right", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12", "Plug");

    /** Checks the key is one code point or a known name. */
    public KeyInput {
        Objects.requireNonNull(key, "key");
        if (key.isEmpty() || key.codePointCount(0, key.length()) != 1 && !NAMES.contains(key) && !isAction(key))
            throw new IllegalArgumentException("Not a key: " + key);
    }

    /**
     * @param codePoint a character
     * @return that character, unmodified
     */
    public static KeyInput of(int codePoint) { return new KeyInput(Character.toString(codePoint), 0); }

    /**
     * @param name one of {@link #NAMES}
     * @return that key, unmodified
     */
    public static KeyInput named(String name) { return new KeyInput(name, 0); }

    /**
     * @param letter a letter or punctuation character
     * @return Ctrl with it, normalized ({@code ctrl('[')} is {@code <Esc>})
     */
    public static KeyInput ctrl(char letter) { return normalize(String.valueOf(letter), CTRL); }

    /**
     * @param id an Editor command id
     * @return the key that runs it from a mapping, {@code <Action>(id)}
     */
    public static KeyInput action(String id) { return new KeyInput("Action(" + id + ")", 0); }

    private static boolean isAction(String key) { return key.startsWith("Action(") && key.endsWith(")") && key.length() > 8; }

    /** @return the Editor command id of an {@code <Action>(id)} key, or null for any other key */
    public String actionId() { return isAction(key) ? key.substring(7, key.length() - 1) : null; }

    /** @return whether this is a character rather than a named key */
    public boolean isChar() { return key.codePointCount(0, key.length()) == 1; }

    /** @return the character when this is a character without modifiers, otherwise -1 */
    public int plainChar() { return isChar() && modifiers == 0 ? key.codePointAt(0) : -1; }

    /**
     * @param notation one key in Vim notation, such as {@code <C-w>}
     * @return whether this is that key
     */
    public boolean is(String notation) {
        List<KeyInput> keys = parse(notation);
        return keys.size() == 1 && keys.getFirst().equals(this);
    }

    /** @return Vim notation: {@code a}, {@code <lt>}, {@code <C-w>}, {@code <S-Tab>} */
    public String notation() {
        if (modifiers == 0 && isChar()) return key.equals("<") ? "<lt>" : key;
        if (isAction(key)) return "<Action>(" + actionId() + ")";
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & CTRL) != 0) out.append("C-");
        if ((modifiers & SHIFT) != 0) out.append("S-");
        if ((modifiers & ALT) != 0) out.append("M-");
        if ((modifiers & META) != 0) out.append("D-");
        out.append(switch (key) {
            case "<" -> "lt";
            case " " -> "Space";
            default -> key;
        });
        return out.append('>').toString();
    }

    @Override public String toString() { return notation(); }

    /**
     * Reads Vim notation. A {@code <} that does not start a key name is the character {@code <}.
     *
     * @param notation keys such as {@code d2w} or {@code ihello<Esc>}
     * @return the keys
     */
    public static List<KeyInput> parse(String notation) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
        while (i < notation.length()) {
            if (notation.regionMatches(true, i, "<Action>(", 0, 9)) {
                int close = notation.indexOf(')', i + 9);
                if (close > i + 9) {
                    keys.add(action(notation.substring(i + 9, close)));
                    i = close + 1;
                    continue;
                }
            }
            if (notation.charAt(i) == '<') {
                int close = notation.indexOf('>', i + 1);
                if (close > i + 1) {
                    KeyInput key = bracket(notation.substring(i + 1, close));
                    if (key != null) {
                        keys.add(key);
                        i = close + 1;
                        continue;
                    }
                }
            }
            int codePoint = notation.codePointAt(i);
            keys.add(of(codePoint));
            i += Character.charCount(codePoint);
        }
        return keys;
    }

    /**
     * @param keys keys
     * @return their Vim notation, which {@link #parse} reads back to the same keys
     */
    public static String format(List<KeyInput> keys) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : keys) out.append(key.notation());
        return out.toString();
    }

    /** @return the key a {@code <…>} body names, or null when it names none (the {@code <} is then literal) */
    private static KeyInput bracket(String body) {
        int modifiers = 0;
        String rest = body;
        while (rest.length() > 2 && rest.charAt(1) == '-') {
            int flag = switch (Character.toUpperCase(rest.charAt(0))) {
                case 'S' -> SHIFT;
                case 'C' -> CTRL;
                case 'M', 'A' -> ALT;
                case 'D' -> META;
                default -> -1;
            };
            if (flag < 0) break;
            modifiers |= flag;
            rest = rest.substring(2);
        }
        String name = rest.toLowerCase(Locale.ROOT);
        if (modifiers == 0) {
            switch (name) {
                case "nl", "newline", "linefeed", "lf" -> { return new KeyInput("j", CTRL); }
                case "nul" -> { return new KeyInput("@", CTRL); }
                default -> { }
            }
            if (name.startsWith("char-")) return character(name.substring(5));
        }
        String key = switch (name) {
            case "esc", "escape" -> "Esc";
            case "cr", "return", "enter" -> "CR";
            case "tab" -> "Tab";
            case "bs", "backspace" -> "BS";
            case "del", "delete" -> "Del";
            case "insert", "ins" -> "Insert";
            case "home" -> "Home";
            case "end" -> "End";
            case "pageup" -> "PageUp";
            case "pagedown" -> "PageDown";
            case "up" -> "Up";
            case "down" -> "Down";
            case "left" -> "Left";
            case "right" -> "Right";
            case "space" -> " ";
            case "lt" -> "<";
            case "bar" -> "|";
            case "bslash" -> "\\";
            case "plug" -> "Plug";
            default -> {
                if (name.matches("f([1-9]|1[0-2])")) yield "F" + name.substring(1);
                yield modifiers != 0 && rest.codePointCount(0, rest.length()) == 1 ? rest : null;
            }
        };
        return key == null ? null : normalize(key, modifiers);
    }

    /** {@code <Char-65>}, {@code <Char-0x41>}, {@code <Char-0101>}: a character by its code, or null. */
    private static KeyInput character(String number) {
        try {
            int code = number.startsWith("0x") ? Integer.parseInt(number.substring(2), 16)
                : number.length() > 1 && number.startsWith("0") ? Integer.parseInt(number.substring(1), 8) : Integer.parseInt(number);
            return code > 0 && Character.isValidCodePoint(code) ? of(code) : null;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * @param key a character or a name from {@link #NAMES}
     * @param modifiers the modifiers held
     * @return the key as Vim sees it: Shift folded into characters (except Ctrl+Shift with a letter, its own key), Ctrl letters lower case, and {@code <C-[>},
     *     {@code <C-i>} and {@code <C-m>} read as {@code <Esc>}, {@code <Tab>} and {@code <CR>}
     */
    static KeyInput normalize(String key, int modifiers) {
        if (key.codePointCount(0, key.length()) == 1) {
            if ((modifiers & (CTRL | SHIFT | ALT | META)) == (CTRL | SHIFT) && Character.isLetter(key.codePointAt(0)))
                return new KeyInput(key.toLowerCase(Locale.ROOT), CTRL | SHIFT);
            if ((modifiers & SHIFT) != 0) {
                key = key.toUpperCase(Locale.ROOT);
                modifiers &= ~SHIFT;
            }
            if ((modifiers & CTRL) != 0) {
                key = key.toLowerCase(Locale.ROOT);
                if (modifiers == CTRL) {
                    switch (key) {
                        case "[" -> { return named("Esc"); }
                        case "i" -> { return named("Tab"); }
                        case "m" -> { return named("CR"); }
                        default -> { }
                    }
                }
            }
        }
        return new KeyInput(key, modifiers);
    }
}
