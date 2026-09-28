package dev.jasper.editor.vim;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * Insert and Replace mode (spec 3.4 and 4.1), ported from Vim's edit.c: an insert session records its keys for
 * {@code .} and for a count, typed characters go through the text area's own typing actions (so RSyntaxTextArea's
 * paired brackets apply), and {@code <CR>}, {@code <BS>}, {@code <Tab>} and the Ctrl keys behave as Vim's.
 */
final class InsertMode {
    private static final int CHAR = 0, WORD = 1, WORD_NOT_SPACE = 2, LINE = 3;

    /** One insert, from the command that started it to its {@code <Esc>}. */
    private static final class Session {
        final String command;
        int count;
        boolean replace;
        final boolean nested;
        /** Vim's Insstart_orig: where the insert started; <C-w> and <C-u> stop here once. */
        final Pos start;
        final List<KeyInput> keys = new ArrayList<>();
        final StringBuilder inserted = new StringBuilder();
        /** Characters Replace mode overwrote, oldest first; null for a character it appended. */
        final List<String> replaced = new ArrayList<>();
        /** The line whose indent autoindent made and nothing has been typed on (Vim's did_ai), or -1. */
        int autoIndentLine;
        /** Runs when the insert ends (a Visual block insert copies its text to the other lines). */
        Runnable onEnd;

        Session(String command, int count, boolean replace, boolean nested, Pos start, int autoIndentLine) {
            this.command = command;
            this.count = count;
            this.replace = replace;
            this.nested = nested;
            this.start = start;
            this.autoIndentLine = autoIndentLine;
        }
    }

    private final VimEditor editor;
    private Session session;
    /** Reads the next key as an argument ({@code <C-r>}'s register, {@code <C-v>}'s character). */
    private Consumer<KeyInput> argument;
    private boolean repeating;

    InsertMode(VimEditor editor) { this.editor = editor; }

    static void register(VimCommands commands) {
        commands.insert("<Esc>", "back to Normal mode, repeating the insert {count} times", (insert, key) -> insert.escape());
        commands.insert("<C-c>", "back to Normal mode", (insert, key) -> insert.escape());
        commands.insert("<CR>", "new line, with 'autoindent'", (insert, key) -> insert.newline());
        commands.insert("<C-j>", "new line", (insert, key) -> insert.newline());
        commands.insert("<BS>", "delete the character before the cursor", (insert, key) -> insert.backspace(CHAR));
        commands.insert("<C-h>", "delete the character before the cursor", (insert, key) -> insert.backspace(CHAR));
        commands.insert("<C-w>", "delete the word before the cursor", (insert, key) -> insert.backspace(WORD));
        commands.insert("<C-u>", "delete the text entered before the cursor in the line", (insert, key) -> insert.backspace(LINE));
        commands.insert("<Del>", "delete the character under the cursor", (insert, key) -> insert.delete());
        commands.insert("<Tab>", "insert a tab, or spaces with 'expandtab' or 'softtabstop'", (insert, key) -> insert.tab());
        commands.insert("<C-t>", "indent the line by 'shiftwidth'", (insert, key) -> insert.indent(true));
        commands.insert("<C-d>", "unindent the line by 'shiftwidth'", (insert, key) -> insert.indent(false));
        commands.insert("<C-r>", "insert a register", (insert, key) -> insert.register());
        commands.insert("<C-v>", "insert the next key literally, or a character by its code", (insert, key) -> insert.literal());
        commands.insert("<C-q>", "insert the next key literally, as CTRL-V", (insert, key) -> insert.literal());
        commands.insert("<C-a>", "insert the text of the last insert", (insert, key) -> insert.insertText(insert.editor.vim().registers().lastInserted(), true));
        commands.insert("<C-e>", "insert the character below the cursor", (insert, key) -> insert.copyFrom(1));
        commands.insert("<C-y>", "insert the character above the cursor", (insert, key) -> insert.copyFrom(-1));
        commands.insert("<C-o>", "run one Normal-mode command", (insert, key) -> insert.oneCommand());
        commands.insert("<Insert>", "switch between Insert and Replace mode", (insert, key) -> insert.toggleReplace());
        for (String arrow : List.of("<Left>", "<Right>", "<Up>", "<Down>", "<Home>", "<End>"))
            commands.insert(arrow, "move the cursor, starting a new insert for undo and .", InsertMode::arrow);
    }

    /**
     * Starts an insert at the cursor; the command has placed the cursor.
     *
     * @param command the command that started it ({@code i}, {@code o}, {@code c}, {@code R})
     * @param count how many times to repeat the typed text on {@code <Esc>}
     * @param replace whether this is Replace mode
     * @param autoIndentLine the line autoindent just indented (for Vim's did_ai), or -1
     */
    void begin(String command, int count, boolean replace, int autoIndentLine) {
        session = new Session(command, count, replace, false, editor.cursor(), autoIndentLine);
        editor.mode(replace ? Mode.REPLACE : Mode.INSERT);
    }

    /** @param hook runs when the current insert ends, after the cursor moves back */
    void onEnd(Runnable hook) { session.onEnd = hook; }

    /** An insert run by a Normal-mode command itself ({@code r<CR>}): not recorded, and its end tells no one. */
    void beginNested(String command) {
        session = new Session(command, 1, false, true, editor.cursor(), -1);
        editor.mode(Mode.INSERT);
    }

    /** o and O (open_line): a new line below or above with the current line's indent when 'autoindent' is set. */
    void openLine(boolean below) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        Options o = editor.options();
        String indent = o.flag("autoindent") ? Text.makeIndent(Text.indent(b.line(p.line), o.number("tabstop")), o.number("tabstop"), o.flag("expandtab")) : "";
        int line = below ? p.line + 1 : p.line;
        b.insertLines(line, List.of(indent));
        editor.setCursor(new Pos(line, indent.length()), true);
    }

    /** @return whether the key means something in Insert mode, for the handler rule */
    boolean wants(KeyInput key) {
        if (argument != null || key.plainChar() >= 0) return true;
        return editor.vim().commands().insertKey(key.notation()) != null;
    }

    void key(KeyInput key) {
        if (session == null) return;
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            try { handler.accept(key); }
            catch (VimFailure failure) { editor.insertKeyFailed(failure); }
            return;
        }
        if (key.plainChar() >= 0) {
            character(key.key());
            record(key);
            return;
        }
        VimCommands.Entry entry = editor.vim().commands().insertKey(key.notation());
        if (entry == null) return;
        editor.vim().covered(entry.id());
        // These record themselves (or, like <Esc>, are recorded by the machine), or end the recorded insert.
        boolean selfRecorded = key.is("<Esc>") || key.is("<C-c>") || key.is("<C-o>") || key.is("<C-r>") || key.is("<C-v>") || key.is("<C-q>")
            || key.is("<C-a>") || key.is("<C-e>") || key.is("<C-y>") || arrowKey(key);
        // A key recorded only after its handler returns: a failing key (e.g. <BS> or <Del> with nothing to
        // remove) never joins the recorded insert, so <Esc>'s repeat and . never replay a no-op. A failing key
        // rings the bell and the insert goes on (Vim's ins_del and ins_bs beep without ending it), so a . whose
        // replayed key fails still reaches its <Esc>.
        try { ((VimCommands.InsertKey) entry.handler()).run(this, key); }
        catch (VimFailure failure) {
            editor.insertKeyFailed(failure);
            return;
        }
        if (!selfRecorded) record(key);
    }

    /** Text an input method committed; the text area has already inserted it. */
    void typedByInputMethod(String text) {
        if (session == null) return;
        text.codePoints().forEach(cp -> record(KeyInput.of(cp)));
        session.inserted.append(text);
        session.autoIndentLine = -1;
    }

    /** Ends the session without Vim's end-of-insert work, after an internal error. */
    void abort() {
        session = null;
        argument = null;
    }

    boolean active() { return session != null; }

    private void record(KeyInput key) {
        if (repeating || session == null) return;
        session.keys.add(key);
        if (!session.nested) editor.machine().recordInsert(key);
    }

    private void recordAll(String text) {
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (cp == '\n') record(KeyInput.named("CR"));
            else if (cp < 0x20 || cp == 0x7f) {
                // Control characters replay literally, as Vim's AppendToRedobuffLit escapes them.
                record(KeyInput.ctrl('v'));
                record(KeyInput.of(cp));
            } else record(KeyInput.of(cp));
            i += Character.charCount(cp);
        }
    }

    /** A typed character: through the text area's typing action in Insert mode; overwriting in Replace mode. */
    private void character(String text) {
        Session s = session;
        s.autoIndentLine = -1;
        s.inserted.append(text);
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        if (s.replace) {
            String line = b.line(p.line);
            int offset = b.offset(p);
            if (p.col < line.length()) {
                int end = Text.next(line, p.col);
                s.replaced.add(line.substring(p.col, end));
                b.replace(offset, b.offset(p.line, end), text);
            } else {
                s.replaced.add(null);
                b.insert(offset, text);
            }
            editor.setCursorOffset(offset + text.length());
            return;
        }
        VimTextArea area = editor.area();
        // The text area's typing actions ignore control characters, so a tab or a literal goes straight in.
        if (text.length() == 1 && text.charAt(0) >= 0x20 && text.charAt(0) != 0x7f) {
            KeyStroke stroke = KeyStroke.getKeyStroke(text.charAt(0));
            Object name = area.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
            Action action = name == null ? null : area.getActionMap().get(name);
            if (action == null && area.getKeymap() != null) action = area.getKeymap().getDefaultAction();
            if (action != null) {
                area.setCaretPosition(b.offset(p));
                action.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, text));
                return;
            }
        }
        int offset = b.offset(p);
        b.insert(offset, text);
        editor.setCursorOffset(offset + text.length());
    }

    /** Text inserted as if typed ({@code <C-r>}, {@code <C-a>}): line breaks go through {@link #newline}. */
    private void insertText(String text, boolean recorded) {
        if (recorded) recordAll(text);
        boolean was = repeating;
        repeating = true;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (cp == '\n') newline();
                else character(Character.toString(cp));
                i += Character.charCount(cp);
            }
        } finally { repeating = was; }
    }

    /**
     * ins_esc. A key recorded during the original typing succeeded then, but the buffer has moved on by the time
     * the count replays it (spec: repeated {@code <BS>}/{@code <Del>} at an edge); a failure part-way through the
     * repeat must still leave Insert mode, so the cleanup below runs in a {@code finally}.
     */
    private void escape() {
        Session s = session;
        repeating = true;
        try {
            for (int n = 1; n < s.count; n++) {
                if (s.command.equals("o") || s.command.equals("O")) {
                    openLine(true);
                    s.autoIndentLine = editor.options().flag("autoindent") ? editor.cursor().line : -1;
                }
                for (KeyInput key : s.keys) key(key);
            }
        } finally {
            repeating = false;
            removeAutoIndent();
            editor.vim().registers().lastInserted(s.inserted.toString());
            Pos p = editor.cursor();
            // The '^ mark: where Insert mode ended, before the cursor moves back (ins_esc).
            editor.marks().set('^', p);
            session = null;
            editor.mode(Mode.NORMAL);
            if (p.col > 0) editor.setCursor(new Pos(p.line, Text.previous(editor.buffer().line(p.line), p.col)));
            if (s.onEnd != null) s.onEnd.run();
            if (!s.nested) editor.machine().insertEnded();
        }
    }

    /** Vim's did_ai: an autoindented line that stayed blank loses its indent. */
    private void removeAutoIndent() {
        Pos p = editor.cursor();
        if (session.autoIndentLine == p.line && p.line < editor.buffer().count() && Text.blank(editor.buffer().line(p.line))) {
            editor.buffer().setLine(p.line, "");
            editor.setCursorInInsert(new Pos(p.line, 0));
        }
        session.autoIndentLine = -1;
    }

    /** open_line in Insert mode: splits the line, copying the indent with 'autoindent'. */
    private void newline() {
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        boolean ai = o.flag("autoindent");
        int ts = o.number("tabstop");
        String before = line.substring(0, p.col), after = line.substring(p.col);
        String indent = ai ? Text.makeIndent(Text.indent(line, ts), ts, o.flag("expandtab")) : "";
        if (ai) after = after.substring(Text.firstNonBlank(after));
        if (session.autoIndentLine == p.line && Text.blank(before)) before = "";
        b.setLine(p.line, before + "\n" + indent + after);
        editor.setCursorInInsert(new Pos(p.line + 1, indent.length()));
        session.autoIndentLine = ai ? p.line + 1 : -1;
        session.inserted.append('\n');
    }

    /** ins_bs: one character, a word or the line, stopping once where the insert started. */
    private void backspace(int mode) {
        Session s = session;
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        boolean startOk = o.has("backspace", "start") || o.has("backspace", "2") || o.has("backspace", "3");
        if (p.line == 0 && p.col == 0) throw Context.fail();
        if (!startOk && p.line == s.start.line && p.col <= s.start.col) throw Context.fail();
        if (p.col == 0) {
            if (!(o.has("backspace", "eol") || o.has("backspace", "2") || o.has("backspace", "3"))) throw Context.fail();
            if (s.replace) {
                String above = b.line(p.line - 1);
                if (!s.replaced.isEmpty() && "\n".equals(s.replaced.getLast())) {
                    s.replaced.removeLast();
                    b.remove(b.end(p.line - 1), b.start(p.line));
                }
                editor.setCursorInInsert(new Pos(p.line - 1, above.length()));
                return;
            }
            int join = b.end(p.line - 1);
            int length = b.line(p.line - 1).length();
            b.remove(join, join + 1);
            if (s.start.line == p.line) {
                s.start.line--;
                s.start.col = length;
            }
            editor.setCursorOffset(join);
            trimInserted();
            return;
        }
        String line = b.line(p.line);
        int sts = o.number("softtabstop"), ts = o.number("tabstop");
        char before = line.charAt(p.col - 1);
        if (mode == CHAR && sts != 0 && (before == '\t' || before == ' ') && !s.replace) {
            int vcol = Text.virtcol(line, p.col, ts);
            int want = ((vcol - 1) / sts) * sts;
            int col = p.col;
            while (Text.virtcol(line, col, ts) > want && col > 0 && (line.charAt(col - 1) == ' ' || line.charAt(col - 1) == '\t')) col--;
            int reached = Text.virtcol(line, col, ts);
            String fill = reached < want ? " ".repeat(want - reached) : "";
            b.replace(b.offset(p.line, col), b.offset(p), fill);
            editor.setCursorInInsert(new Pos(p.line, col + fill.length()));
            trimInserted();
            return;
        }
        int mincol = 0;
        if (mode == LINE && o.flag("autoindent")) {
            int fnb = Text.firstNonBlank(line);
            if (fnb < p.col) mincol = fnb;
        }
        Keywords keywords = o.keywords();
        int cclass = p.col < line.length() ? keywords.charClass(line.codePointAt(p.col)) : 0;
        boolean wordState = false;
        int col = p.col;
        do {
            col = Text.previous(line, col);
            int cc = line.codePointAt(col);
            int prevClass = cclass;
            cclass = keywords.charClass(cc);
            if (mode == WORD && cc != ' ' && cc != '\t') {
                mode = WORD_NOT_SPACE;
                wordState = keywords.isWord(cc);
            } else if (mode == WORD_NOT_SPACE && (cc == ' ' || cc == '\t' || keywords.isWord(cc) != wordState || prevClass != cclass)) {
                col = Text.next(line, col);
                break;
            }
            if (s.replace) {
                String original = s.replaced.isEmpty() ? "" : s.replaced.removeLast();
                int end = Text.next(line, col);
                if (original == null) line = line.substring(0, col) + line.substring(end);
                else if (!original.isEmpty()) line = line.substring(0, col) + original + line.substring(end);
            } else {
                line = line.substring(0, col) + line.substring(Text.next(line, col));
                trimInserted();
            }
            if (mode == CHAR) break;
        } while (col > mincol && (p.line != s.start.line || col != s.start.col));
        b.setLine(p.line, line);
        editor.setCursorInInsert(new Pos(p.line, col));
        if (p.line == s.start.line && col < s.start.col) s.start.col = col;
    }

    private void trimInserted() {
        StringBuilder inserted = session.inserted;
        if (!inserted.isEmpty()) inserted.setLength(inserted.length() - 1);
    }

    /** {@code <Del>}: the character under the cursor, or the line break at the end of the line. */
    private void delete() {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        if (p.col < line.length()) b.remove(b.offset(p), b.offset(p.line, Text.next(line, p.col)));
        else if (p.line < b.count() - 1 && editor.options().has("backspace", "eol")) b.remove(b.end(p.line), b.end(p.line) + 1);
        else throw Context.fail();
        editor.setCursorInInsert(p);
    }

    /** ins_tab. */
    private void tab() {
        Options o = editor.options();
        boolean et = o.flag("expandtab");
        int sts = o.number("softtabstop"), ts = o.number("tabstop");
        session.autoIndentLine = -1;
        if (!et && sts == 0) {
            character("\t");
            return;
        }
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        int vcol = Text.virtcol(line, p.col, ts);
        int width = sts != 0 ? sts : ts;
        int target = (vcol / width + 1) * width;
        if (et) {
            String spaces = " ".repeat(target - vcol);
            b.insert(b.offset(p), spaces);
            editor.setCursorInInsert(new Pos(p.line, p.col + spaces.length()));
            session.inserted.append(spaces);
            return;
        }
        // 'softtabstop' without 'expandtab': fill with spaces, then turn the white space before the cursor into tabs.
        int runStart = p.col;
        while (runStart > 0 && (line.charAt(runStart - 1) == ' ' || line.charAt(runStart - 1) == '\t')) runStart--;
        int startV = Text.virtcol(line, runStart, ts);
        StringBuilder white = new StringBuilder();
        int v = startV;
        while ((v / ts + 1) * ts <= target) {
            white.append('\t');
            v = (v / ts + 1) * ts;
        }
        white.append(" ".repeat(target - v));
        b.replace(b.offset(p.line, runStart), b.offset(p), white.toString());
        editor.setCursorInInsert(new Pos(p.line, runStart + white.length()));
        session.inserted.append('\t');
    }

    /** {@code <C-t>} and {@code <C-d>}: change the indent by 'shiftwidth', rounded, keeping the cursor on its text. */
    private void indent(boolean more) {
        Buffer b = editor.buffer();
        Options o = editor.options();
        Pos p = editor.cursor();
        String line = b.line(p.line);
        int ts = o.number("tabstop"), sw = o.shiftwidth();
        int indent = Text.indent(line, ts);
        int width = more ? (indent / sw + 1) * sw : Math.max(0, (indent % sw == 0 ? indent - sw : indent / sw * sw));
        int fnb = Text.firstNonBlank(line);
        String replaced = Text.makeIndent(width, ts, o.flag("expandtab"));
        b.setLine(p.line, replaced + line.substring(fnb));
        int col = p.col >= fnb ? p.col - fnb + replaced.length() : replaced.length();
        editor.setCursorInInsert(new Pos(p.line, col));
        session.autoIndentLine = -1;
    }

    /** {@code <C-r>{register}}: the register's text as if typed; {@code <C-r><C-r>} and friends insert it the same way. */
    private void register() {
        argument = key -> {
            if (key.is("<C-r>") || key.is("<C-o>") || key.is("<C-p>")) {
                argument = this::registerNamed;
                return;
            }
            registerNamed(key);
        };
    }

    private void registerNamed(KeyInput key) {
        int c = key.plainChar();
        if (c < 0 || !Registers.valid((char) c, false)) throw Context.fail();
        var content = editor.vim().registers().get((char) c);
        if (content.isEmpty()) throw Context.fail();
        insertText(content.get().text(), true);
    }

    /** {@code <C-v>}: the next key literally, or {@code u}/{@code x}/{@code o}/decimal digits for a code. */
    private void literal() {
        argument = key -> {
            int c = key.plainChar();
            if (c == 'u' || c == 'U' || c == 'x' || c == 'X' || c == 'o' || c == 'O' || c >= '0' && c <= '9') {
                record(KeyInput.ctrl('v'));
                record(key);
                int radix = c == 'u' || c == 'U' || c == 'x' || c == 'X' ? 16 : c == 'o' || c == 'O' ? 8 : 10;
                int max = c == 'u' ? 4 : c == 'U' ? 8 : c == 'x' || c == 'X' ? 2 : 3;
                StringBuilder digits = new StringBuilder(radix == 10 ? String.valueOf((char) c) : "");
                collectDigits(digits, radix, max);
                return;
            }
            record(KeyInput.ctrl('v'));
            record(key);
            String text = literalText(key);
            if (text == null) throw Context.fail();
            insertLiteral(text);
        };
    }

    private void collectDigits(StringBuilder digits, int radix, int max) {
        if (digits.length() >= max) {
            insertLiteral(Character.toString(Integer.parseInt(digits.toString(), radix)));
            return;
        }
        argument = key -> {
            int c = key.plainChar();
            if (c >= 0 && Character.digit(c, radix) >= 0) {
                record(key);
                digits.append((char) c);
                collectDigits(digits, radix, max);
                return;
            }
            if (!digits.isEmpty()) insertLiteral(Character.toString(Integer.parseInt(digits.toString(), radix)));
            key(key);
        };
    }

    private static String literalText(KeyInput key) {
        if (key.plainChar() >= 0) return key.key();
        if (key.modifiers() == KeyInput.CTRL && key.isChar()) {
            char c = key.key().charAt(0);
            if (c >= 'a' && c <= 'z') return String.valueOf((char) (c - 'a' + 1));
            if (c == '@') return "\u0000";
            if (c == '\\') return "\u001c";
            if (c == ']') return "\u001d";
            if (c == '^') return "\u001e";
            return null;
        }
        return switch (key.key()) {
            case "Tab" -> "\t";
            case "CR" -> "\r";
            case "Esc" -> "\u001b";
            case "BS" -> "\u0008";
            case "Del" -> "\u007f";
            default -> null;
        };
    }

    private void insertLiteral(String text) {
        Buffer b = editor.buffer();
        int offset = b.offset(editor.cursor());
        b.insert(offset, text);
        editor.setCursorOffset(offset + text.length());
        session.inserted.append(text);
        session.autoIndentLine = -1;
    }

    /** {@code <C-e>} and {@code <C-y>}: the character at the same display column in the line below or above. */
    private void copyFrom(int delta) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        int other = p.line + delta;
        if (other < 0 || other >= b.count()) throw Context.fail();
        int ts = editor.options().number("tabstop");
        int vcol = Text.virtcol(b.line(p.line), p.col, ts);
        String text = b.line(other);
        int col = Text.columnAt(text, vcol, ts);
        if (col >= text.length()) throw Context.fail();
        String copied = text.substring(col, Text.next(text, col));
        recordAll(copied);
        character(copied);
    }

    /** {@code <C-o>}: one Normal-mode command, then back to Insert mode (edit.c's ins_ctrl_o). */
    private void oneCommand() {
        Session s = session;
        removeAutoIndent();
        editor.vim().registers().lastInserted(s.inserted.toString());
        Pos p = editor.cursor();
        String line = editor.buffer().line(p.line);
        // At the end of the line the command runs on the last character; Insert mode comes back after it.
        boolean atEnd = !line.isEmpty() && p.col >= line.length();
        if (atEnd) editor.setCursor(new Pos(p.line, Text.last(line)), true);
        session = null;
        editor.mode(Mode.INSERT_NORMAL);
        editor.machine().insertEnded();
        editor.machine().pausedInsert(s.replace, atEnd ? p.line : -1);
    }

    private void toggleReplace() {
        session.replace = !session.replace;
        editor.mode(session.replace ? Mode.REPLACE : Mode.INSERT);
    }

    private static boolean arrowKey(KeyInput key) {
        return switch (key.key()) {
            case "Left", "Right", "Up", "Down", "Home", "End" -> key.modifiers() == 0;
            default -> false;
        };
    }

    /** An arrow key: moves, then starts a new insert for undo and {@code .} (Vim's start_arrow and stop_arrow). */
    private void arrow(KeyInput key) {
        Buffer b = editor.buffer();
        Pos p = editor.cursor();
        int ts = editor.options().number("tabstop");
        String line = b.line(p.line);
        switch (key.key()) {
            case "Left" -> {
                if (p.col == 0) throw Context.fail();
                p.col = Text.previous(line, p.col);
            }
            case "Right" -> {
                if (p.col >= line.length()) throw Context.fail();
                p.col = Text.next(line, p.col);
            }
            case "Up", "Down" -> {
                int other = p.line + (key.key().equals("Up") ? -1 : 1);
                if (other < 0 || other >= b.count()) throw Context.fail();
                p = new Pos(other, editor.insertColumn(other));
            }
            case "Home" -> p.col = 0;
            case "End" -> p.col = line.length();
            default -> { }
        }
        removeAutoIndent();
        editor.setCursor(p, true);
        if (!key.key().equals("Up") && !key.key().equals("Down")) editor.forgetColumn();
        Session s = session;
        editor.vim().registers().lastInserted(s.inserted.toString());
        session = new Session("i", 1, s.replace, s.nested, editor.cursor(), -1);
        if (!s.nested) editor.machine().restartInsert();
    }
}
