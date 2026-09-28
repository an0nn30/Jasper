package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The command line that {@code :}, {@code /} and {@code ?} read (spec 5.3), with Vim's editing keys (ex_getln.c): moving and
 * deleting, {@code <C-r>} for registers and the word under the cursor, {@code <C-v>} for a literal key, and history
 * walked by {@code <Up>}/{@code <Down>} filtered by the typed prefix. The Vim bar draws it; the editor feeds it keys
 * while it is active.
 */
final class CommandLine {
    private final VimEditor editor;
    private char type;
    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private final List<KeyInput> typed = new ArrayList<>();
    private List<String> history = List.of();
    private int historyIndex;
    private String historyPrefix;
    private Consumer<KeyInput> argument;
    private Consumer<String> changed, done;
    private Runnable cancelled;

    CommandLine(VimEditor editor) { this.editor = editor; }

    /**
     * Opens the command line.
     *
     * @param type {@code /}, {@code ?} or {@code :}
     * @param history its history, oldest first
     * @param changed runs after each edit with the text (for 'incsearch')
     * @param done runs with the text on {@code <CR>}
     * @param cancelled runs on {@code <Esc>}, {@code <C-c>}, or {@code <BS>} on an empty line
     */
    void start(char type, List<String> history, Consumer<String> changed, Consumer<String> done, Runnable cancelled) {
        start(type, history, "", changed, done, cancelled);
    }

    /** Opens the command line with {@code initial} typed on it ({@code '<,'>} after Visual {@code :}). */
    void start(char type, List<String> history, String initial, Consumer<String> changed, Consumer<String> done, Runnable cancelled) {
        this.type = type;
        this.history = history;
        this.changed = changed;
        this.done = done;
        this.cancelled = cancelled;
        text.setLength(0);
        text.append(initial);
        cursor = text.length();
        typed.clear();
        historyIndex = history.size();
        historyPrefix = null;
        argument = null;
    }

    boolean active() { return done != null; }

    /** @return {@code /}, {@code ?} or {@code :} */
    char type() { return type; }

    String text() { return text.toString(); }

    /** @return the cursor's index in the text */
    int cursor() { return cursor; }

    /** @return the keys typed on this command line, for {@code .} */
    List<KeyInput> typed() { return List.copyOf(typed); }

    /** The keys that end the line, and whether each executes it. */
    void key(KeyInput key) {
        typed.add(key);
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            handler.accept(key);
            edited();
            return;
        }
        int ch = key.plainChar();
        if (ch >= 0) {
            insert(new String(Character.toChars(ch)));
            return;
        }
        switch (key.notation()) {
            case "<CR>", "<C-j>", "<C-m>", "<NL>", "<kEnter>" -> finish();
            case "<Esc>", "<C-c>", "<C-[>" -> cancel();
            case "<BS>", "<C-h>" -> {
                if (text.isEmpty()) cancel();
                else if (cursor > 0) {
                    int from = text.offsetByCodePoints(cursor, -1);
                    text.delete(from, cursor);
                    cursor = from;
                    edited();
                }
            }
            case "<Del>" -> {
                if (text.isEmpty()) cancel();
                else if (cursor < text.length()) {
                    text.delete(cursor, text.offsetByCodePoints(cursor, 1));
                    edited();
                } else {
                    int from = text.offsetByCodePoints(cursor, -1);
                    text.delete(from, cursor);
                    cursor = from;
                    edited();
                }
            }
            case "<Left>" -> { if (cursor > 0) cursor = text.offsetByCodePoints(cursor, -1); }
            case "<Right>" -> { if (cursor < text.length()) cursor = text.offsetByCodePoints(cursor, 1); }
            case "<S-Left>", "<C-Left>" -> cursor = wordBack(cursor);
            case "<S-Right>", "<C-Right>" -> cursor = wordForward(cursor);
            case "<Home>", "<C-b>" -> cursor = 0;
            case "<End>", "<C-e>" -> cursor = text.length();
            case "<C-w>" -> {
                int from = wordBack(cursor);
                text.delete(from, cursor);
                cursor = from;
                edited();
            }
            case "<C-u>" -> {
                text.delete(0, cursor);
                cursor = 0;
                edited();
            }
            case "<C-v>", "<C-q>" -> argument = k -> {
                int c = k.plainChar();
                insertQuietly(c >= 0 ? new String(Character.toChars(c)) : literal(k));
            };
            case "<C-r>" -> argument = this::register;
            case "<Tab>" -> insert("\t");
            case "<Up>", "<S-Up>", "<PageUp>" -> recall(-1, key.is("<Up>"));
            case "<Down>", "<S-Down>", "<PageDown>" -> recall(1, key.is("<Down>"));
            default -> editor.bell();
        }
    }

    private static String literal(KeyInput key) {
        String n = key.notation();
        if (n.startsWith("<C-") && n.length() == 5) {
            char c = Character.toUpperCase(n.charAt(3));
            if (c >= '@' && c <= '_') return String.valueOf((char) (c - '@'));
        }
        return switch (n) {
            case "<CR>" -> "\r";
            case "<Tab>" -> "\t";
            case "<Esc>" -> "\033";
            default -> n;
        };
    }

    /** {@code <C-r>}: a register, or with {@code <C-w>}/{@code <C-a>} the word or WORD under the cursor. */
    private void register(KeyInput key) {
        if (key.is("<C-r>") || key.is("<C-o>") || key.is("<C-p>")) {
            argument = this::register;
            return;
        }
        if (key.is("<C-w>") || key.is("<C-a>")) {
            Pos p = editor.cursor();
            String line = editor.buffer().line(p.line);
            int[] found = key.is("<C-w>") ? Search.identifier(line, p.col, editor.options().keywords()) : nonBlank(line, p.col);
            if (found != null) insertQuietly(line.substring(found[0], found[1]));
            return;
        }
        int name = key.plainChar();
        if (name < 0 || !Registers.valid((char) name, false)) return;
        editor.vim().registers().get((char) name).ifPresent(content -> {
            List<String> lines = content.lines();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                out.append(lines.get(i));
                if (content.type() == Registers.Type.LINE || i < lines.size() - 1) out.append('\r');
            }
            insertQuietly(out.toString());
        });
    }

    private static int[] nonBlank(String line, int col) {
        int start = col;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
        if (start >= line.length()) return null;
        while (start > 0 && !Character.isWhitespace(line.charAt(start - 1))) start--;
        int end = start;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
        return new int[] { start, end };
    }

    private void recall(int step, boolean filtered) {
        if (historyPrefix == null) historyPrefix = filtered ? text.substring(0, cursor) : "";
        int i = historyIndex;
        while (true) {
            i += step;
            if (i < 0) return;
            if (i >= history.size()) {
                historyIndex = history.size();
                replace(historyPrefix);
                return;
            }
            if (history.get(i).startsWith(historyPrefix)) break;
        }
        historyIndex = i;
        replace(history.get(i));
    }

    /** A history entry replaces the text; the walk through the history goes on from it. */
    private void replace(String value) {
        text.setLength(0);
        text.append(value);
        cursor = text.length();
        if (changed != null) changed.accept(text.toString());
    }

    private int wordBack(int from) {
        int i = from;
        while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) i--;
        if (i > 0 && isWord(text.charAt(i - 1))) while (i > 0 && isWord(text.charAt(i - 1))) i--;
        else if (i > 0) i--;
        return i;
    }

    private int wordForward(int from) {
        int i = from;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private boolean isWord(char c) { return editor.options().keywords().isWord(c); }

    private void insert(String value) {
        insertQuietly(value);
        edited();
    }

    private void insertQuietly(String value) {
        text.insert(cursor, value);
        cursor += value.length();
        historyPrefix = null;
    }

    private void edited() {
        historyIndex = history.size();
        if (changed != null) changed.accept(text.toString());
    }

    private void finish() {
        Consumer<String> then = done;
        close();
        then.accept(text.toString());
    }

    private void cancel() {
        Runnable then = cancelled;
        close();
        then.run();
    }

    private void close() {
        done = null;
        changed = null;
        argument = null;
    }
}
