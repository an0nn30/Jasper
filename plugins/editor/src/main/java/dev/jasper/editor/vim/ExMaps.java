package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The mapping commands (spec 7.2), ported from Vim's map.c (do_map, get_map_mode, map_clear, showmap) and term.c
 * (replace_termcodes): {@code :map}, {@code :noremap}, {@code :unmap} and {@code :mapclear} in their
 * {@code n v x o i c} and {@code !} forms, which define, list and remove {@link Mappings}.
 */
final class ExMaps {
    private ExMaps() { }

    /** Vim's MAXMAPLEN: the longest left-hand side, in bytes. */
    static final int MAXMAPLEN = 50;

    /** get_map_mode: the modes a command's name gives, from its first letter; {@code !} gives Insert and Command-line. */
    static int mode(String name, boolean bang) {
        char c = name.charAt(0);
        if (c == 'n' && name.charAt(1) != 'o') return Mappings.NORMAL;
        return switch (c) {
            case 'i' -> Mappings.INSERT;
            case 'c' -> Mappings.CMDLINE;
            case 'v' -> Mappings.VISUAL | Mappings.SELECT;
            case 'x' -> Mappings.VISUAL;
            case 'o' -> Mappings.OP_PENDING;
            default -> bang ? Mappings.MAP_BANG : Mappings.MAP;
        };
    }

    /** ex_map and ex_unmap: do_map for the command, with its errors. */
    static void map(ExCommand cmd) {
        String name = cmd.name();
        int mode = mode(name, cmd.forceit);
        String rest = mode == Mappings.MAP || mode == Mappings.MAP_BANG ? name : name.substring(1);
        doMap(cmd.editor, cmd.arg, mode, rest.startsWith("u"), rest.startsWith("no"));
    }

    /** ex_mapclear: only {@code <buffer>} may follow, and Jasper has no buffer-local mappings to clear. */
    static void mapclear(ExCommand cmd) {
        String arg = cmd.arg;
        boolean local = arg.equals("<buffer>");
        if (!local && !arg.isEmpty()) throw new VimFailure("E474: Invalid argument");
        if (local) return;
        cmd.editor.vim().mappings().clear(mode(cmd.name(), cmd.forceit));
    }

    /**
     * do_map: {@code <nowait>}, {@code <silent>}, {@code <special>} and {@code <unique>} first, in any order; then
     * the left-hand side up to white space (for {@code :unmap} the whole rest, white space and all), then the
     * right-hand side. Without a right-hand side it lists; with one it defines, replacing the mapping of the same
     * keys in these modes. Jasper never echoes a mapping's command line, so {@code <silent>} changes nothing.
     */
    static void doMap(VimEditor editor, String arg, int mode, boolean unmap, boolean noremap) {
        Mappings mappings = editor.vim().mappings();
        boolean nowait = false, unique = false;
        String keys = arg;
        while (true) {
            for (String refused : List.of("<buffer>", "<expr>", "<script>"))
                if (keys.startsWith(refused)) throw new VimFailure("Not supported in Jasper: " + refused);
            if (keys.startsWith("<nowait>")) nowait = true;
            else if (keys.startsWith("<unique>")) unique = true;
            else if (!keys.startsWith("<silent>") && !keys.startsWith("<special>")) break;
            keys = keys.substring(Ex.skipWhite(keys, keys.indexOf('>') + 1));
        }
        int p = 0;
        while (p < keys.length() && (unmap || !Ex.isWhite(keys.charAt(p)))) {
            if (keys.charAt(p) == 0x16 && p + 1 < keys.length()) p++;
            p++;
        }
        String lhsText = keys.substring(0, p);
        String rhsText = p < keys.length() ? keys.substring(Ex.skipWhite(keys, p + 1)) : "";
        boolean hasKey = !lhsText.isEmpty(), hasArg = !rhsText.isEmpty();
        if (unmap && !hasKey) throw new VimFailure("E474: Invalid argument");
        Vim vim = editor.vim();
        String leader = vim.leader("mapleader"), localLeader = vim.leader("maplocalleader");
        List<KeyInput> lhs = parse(lhsText, true, leader, localLeader);
        List<KeyInput> rhs = rhsText.equalsIgnoreCase("<Nop>") ? List.of() : parse(rhsText, false, leader, localLeader);
        if (hasKey && byteLength(lhs) > MAXMAPLEN) throw new VimFailure("E474: Invalid argument");
        if (!unmap && (!hasKey || !hasArg)) {
            list(editor, mappings, mode, hasKey ? lhs : null);
            return;
        }
        if (unmap) {
            // The left-hand side first; failing that, a mapping whose right-hand side is exactly the keys.
            for (int round = 0; round < 2; round++) {
                boolean did = false;
                for (Mapping m : mappings.all()) {
                    if ((m.modes & mode) == 0 || !(round == 0 ? m.lhs : m.rhs).equals(lhs)) continue;
                    m.modes &= ~mode;
                    if (m.modes == 0) mappings.remove(m);
                    did = true;
                }
                if (did) return;
            }
            throw new VimFailure("E31: No such mapping");
        }
        Mapping reused = null;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0 || !m.lhs.equals(lhs)) continue;
            if (unique) throw new VimFailure("E227: Mapping already exists for " + display(lhs, true));
            m.modes &= ~mode;
            if (m.modes == 0 && reused == null) {
                // A mapping left with no modes takes the new right-hand side, keeping its place.
                m.rhs = List.copyOf(rhs);
                m.noremap = noremap;
                m.nowait = nowait;
                m.modes = mode;
                reused = m;
            } else if (m.modes == 0) mappings.remove(m);
        }
        if (reused == null) mappings.add(mode, lhs, rhs, noremap, nowait);
    }

    /**
     * The listing: every mapping for the modes, or those whose left-hand side and {@code prefix} start alike (the
     * shorter a prefix of the longer), or "No mapping found".
     */
    private static void list(VimEditor editor, Mappings mappings, int mode, List<KeyInput> prefix) {
        boolean any = false;
        for (Mapping m : mappings.all()) {
            if ((m.modes & mode) == 0) continue;
            if (prefix != null) {
                int n = Math.min(prefix.size(), m.lhs.size());
                if (!m.lhs.subList(0, n).equals(prefix.subList(0, n))) continue;
            }
            editor.say(line(m));
            any = true;
        }
        if (!any) editor.say("No mapping found");
    }

    /** showmap: the modes in three columns, the left-hand side in twelve, {@code *} for noremap, the right-hand side. */
    static String line(Mapping m) {
        StringBuilder out = new StringBuilder(modeChars(m.modes));
        while (out.length() < 3) out.append(' ');
        String keys = display(m.lhs, true);
        out.append(keys);
        int len = 0;
        for (int i = 0; i < keys.length(); ) {
            int cp = keys.codePointAt(i);
            len += Text.width(cp);
            i += Character.charCount(cp);
        }
        do {
            out.append(' ');
            len++;
        } while (len < 12);
        out.append(m.noremap ? '*' : ' ').append(' ');
        return out.append(m.rhs.isEmpty() ? "<Nop>" : display(m.rhs, false)).toString();
    }

    /** map_mode_to_chars: {@code n}, {@code v}, {@code !}, or blank for {@code :map}'s four modes. */
    static String modeChars(int modes) {
        if ((modes & Mappings.MAP_BANG) == Mappings.MAP_BANG) return "!";
        if ((modes & Mappings.INSERT) != 0) return "i";
        if ((modes & Mappings.CMDLINE) != 0) return "c";
        if ((modes & Mappings.MAP) == Mappings.MAP) return " ";
        StringBuilder out = new StringBuilder();
        if ((modes & Mappings.NORMAL) != 0) out.append('n');
        if ((modes & Mappings.OP_PENDING) != 0) out.append('o');
        if ((modes & (Mappings.VISUAL | Mappings.SELECT)) == (Mappings.VISUAL | Mappings.SELECT)) out.append('v');
        else {
            if ((modes & Mappings.VISUAL) != 0) out.append('x');
            if ((modes & Mappings.SELECT) != 0) out.append('s');
        }
        return out.toString();
    }

    /**
     * msg_outtrans_special: keys as a listing shows them. Special keys and control characters in {@code <>} form
     * ({@code <C-A>}, {@code <Esc>}); a space as {@code <Space>} in a left-hand side and at either end of a
     * right-hand side.
     */
    static String display(List<KeyInput> keys, boolean lhs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            KeyInput key = keys.get(i);
            if (key.plainChar() == ' ' && (lhs || i == 0 || i == keys.size() - 1)) out.append("<Space>");
            else out.append(name(key));
        }
        return out.toString();
    }

    /** get_special_key_name for one key (modifiers in Vim's order M, C, S, D), or the character itself. */
    static String name(KeyInput key) {
        int modifiers = key.modifiers();
        if (key.isChar() && modifiers == 0) return key.key();
        if (key.isChar() && modifiers == KeyInput.CTRL) {
            char c = key.key().charAt(0);
            if (c == '@') return "<Nul>";
            if (c == 'j') return "<NL>";
            if (c >= 'a' && c <= 'z' || c >= '[' && c <= '_') return "<C-" + Character.toUpperCase(c) + ">";
        }
        StringBuilder out = new StringBuilder("<");
        if ((modifiers & KeyInput.ALT) != 0) out.append("M-");
        if ((modifiers & KeyInput.CTRL) != 0) out.append("C-");
        if ((modifiers & KeyInput.SHIFT) != 0) out.append("S-");
        if ((modifiers & KeyInput.META) != 0) out.append("D-");
        out.append(switch (key.key()) {
            case " " -> "Space";
            case "<" -> "lt";
            default -> key.key();
        });
        return out.append('>').toString();
    }

    /** {@link #parse(String, boolean, String, String)} with no {@code mapleader}: {@code <Leader>} is a backslash. */
    static List<KeyInput> parse(String text, boolean lhs) { return parse(text, lhs, "\\", "\\"); }

    /**
     * replace_termcodes: a left- or right-hand side as typed after {@code :map}, as keys. {@code <>} names a key
     * ({@code <lt>} is {@code <}); {@code <Leader>} and {@code <LocalLeader>} are {@code leader} and
     * {@code localLeader}; {@code <Nop>} is no key; CTRL-V takes the next character
     * literally (a CTRL-V ending a left-hand side stays); a left-hand side starting {@code #1} is
     * {@code <F1>}. A control character is its CTRL key. A backslash is an ordinary character, as with Vim's default
     * 'cpoptions'.
     *
     * @param leader what {@code <Leader>} stands for ({@code g:mapleader}, a backslash when it is not set)
     * @param localLeader what {@code <LocalLeader>} stands for ({@code g:maplocalleader})
     */
    static List<KeyInput> parse(String text, boolean lhs, String leader, String localLeader) {
        List<KeyInput> keys = new ArrayList<>();
        int i = 0;
        if (lhs && text.length() >= 2 && text.charAt(0) == '#' && Character.isDigit(text.charAt(1))) {
            keys.add(KeyInput.named(text.charAt(1) == '0' ? "F10" : "F" + text.charAt(1)));
            i = 2;
        }
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close > i + 1) {
                    String body = text.substring(i + 1, close).toLowerCase(Locale.ROOT);
                    if (body.equals("leader") || body.equals("localleader")) {
                        keys.addAll(KeyCodes.keys(body.equals("leader") ? leader : localLeader));
                        i = close + 1;
                        continue;
                    }
                    if (body.equals("nop")) {
                        i = close + 1;
                        continue;
                    }
                    List<KeyInput> named = KeyInput.parse(text.substring(i, close + 1));
                    if (named.size() == 1) {
                        keys.add(named.getFirst());
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == 0x16 && ++i >= text.length()) {
                if (lhs) keys.add(KeyInput.ctrl('v'));
                break;
            }
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            keys.addAll(KeyCodes.keys(Character.toString(cp)));
        }
        return keys;
    }

    /** @return the bytes Vim would store for the keys, for {@link #MAXMAPLEN} */
    static int byteLength(List<KeyInput> keys) {
        int n = 0;
        for (KeyInput key : keys) {
            if (key.modifiers() == 0 && key.isChar()) n += key.key().getBytes(StandardCharsets.UTF_8).length;
            else if (key.modifiers() == 0 && Mappings.firstByte(key) != 0x80 || key.modifiers() == KeyInput.CTRL && key.isChar()) n += 1;
            else n += key.modifiers() == 0 ? 3 : 6;
        }
        return n;
    }
}
