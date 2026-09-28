package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * Macros (spec 4.3), global as in Vim: {@code q{reg}} records the keys typed, before any mapping, into a register as
 * text, and {@code @{reg}} puts a register's text in front of the typeahead to run as keys, mapped. Ported from
 * register.c (do_record, stuff_yank, do_execreg, put_in_typebuf) and getchar.c (gotchars, get_recorded,
 * check_end_reg_executing).
 */
final class Macros {
    /** The register being recorded into, or 0 (Vim's reg_recording). */
    private char recording;
    private final List<KeyInput> recorded = new ArrayList<>();
    /** How many keys the last key that ran took from what was typed (last_recorded_len): {@code q} drops them. */
    private int lastRecorded;
    /** The register {@code @@} runs (execreg_lastc), or 0. */
    private char lastExecuted;
    /** The register running, or 0 (reg_executing); {@code q} does nothing while one runs. */
    private char executing;

    static void register(VimCommands commands) {
        commands.actionWithChar("q", "`q{reg}` records typed keys into a register; `q` again stops (`{A-Z}` appends)", false, Macros::q);
        commands.visual("q", "record into a register, or stop recording, as `q`", true, false, Macros::q);
        commands.actionWithChar("@", "run a register's keys, [count] times; `@@` the last one run, `@:` the last command line", false, Macros::at);
        commands.visual("@", "run a register's keys in Visual mode, as `@`", true, false, Macros::at);
    }

    /** @return the register being recorded into, or 0 */
    char recording() { return recording; }

    /** @return whether {@code q} reads no register: while recording (it stops) or while a register runs (it does nothing) */
    boolean qTakesNoRegister() { return recording != 0 || executing != 0; }

    /** A key is about to be taken from the typeahead (vgetc): the keys it takes from what was typed are counted anew. */
    void nextKey() { lastRecorded = 0; }

    /** A typed key was taken from the typeahead (gotchars): it is recorded. */
    void typed(KeyInput key) {
        if (recording == 0) return;
        recorded.add(key);
        lastRecorded++;
    }

    /** check_end_reg_executing: no keys of the register are left. */
    void doneExecuting() { executing = 0; }

    /** nv_record: {@code q{reg}} starts recording, {@code q} stops it; while a register runs {@code q} does nothing. */
    private static void q(Context c) {
        Macros macros = c.vim.macros();
        if (macros.recording != 0) {
            macros.stop(c.editor);
            return;
        }
        if (macros.executing != 0 || c.argument == null) return;
        int name = c.argument.plainChar();
        if (name == ':' || name == '/' || name == '?') throw VimFailure.bell();
        // Registers 0-9, a-z, A-Z and " may record.
        if (name < 0 || name >= 128 || !Character.isLetterOrDigit(name) && name != '"') throw VimFailure.bell();
        macros.recording = (char) name;
        macros.recorded.clear();
    }

    /**
     * do_record's stop: the keys recorded, without those that stopped it (and a {@code <C-o>} before them in Insert
     * mode), go into the register.
     */
    private void stop(VimEditor editor) {
        char name = recording;
        recording = 0;
        int end = Math.max(0, recorded.size() - lastRecorded);
        if (end > 0 && editor.mode() == Mode.INSERT_NORMAL && recorded.get(end - 1).is("<C-o>")) end--;
        editor.vim().registers().record(name, KeyCodes.text(recorded.subList(0, end)));
        recorded.clear();
    }

    /** nv_at: the register runs [count] times; the first failure stops. */
    private static void at(Context c) {
        Macros macros = c.vim.macros();
        for (int i = 0; i < c.count; i++) macros.execute(c.editor, c.argument.plainChar(), i == 0);
    }

    /**
     * do_execreg: the register's text goes in front of the typeahead, mapped ({@code @:}'s command line is not);
     * lines are joined by line breaks, and a linewise register ends with one.
     *
     * @param first whether this is the first time for {@code @}'s count, when Insert mode's {@code <C-o>} is handled
     */
    private void execute(VimEditor editor, int name, boolean first) {
        if (name == '@') {
            if (lastExecuted == 0) throw new VimFailure("E748: No previously used register");
            name = lastExecuted;
        }
        if (name < 0 || name == '%' || name == '#' || name == '=' || !valid((char) name))
            throw new VimFailure("E354: Invalid register name: '" + (name < 0 ? "" : Character.toString(name)) + "'");
        lastExecuted = (char) name;
        if (name == '_') return;
        Registers registers = editor.vim().registers();
        Typeahead typeahead = editor.typeahead();
        if (name == ':') {
            String line = registers.get(':').map(Registers.Content::text).orElse("");
            if (line.isEmpty()) throw new VimFailure("E30: No previous command line");
            if (editor.mode().visual() && line.startsWith("'<,'>")) line = line.substring(5);
            StringBuilder escaped = new StringBuilder(":");
            for (char ch : line.toCharArray()) {
                if (ch >= 1 && ch < 0x20) escaped.append((char) 0x16);
                escaped.append(ch);
            }
            reedit(editor, first);
            typeahead.insert(KeyCodes.keys(escaped + "\n"), Typeahead.Remap.NONE);
            return;
        }
        if (name == '.') {
            String inserted = registers.lastInserted();
            if (inserted.endsWith("\033")) inserted = inserted.substring(0, inserted.length() - 1);
            if (inserted.isEmpty()) throw new VimFailure("E29: No inserted text yet");
            reedit(editor, first);
            typeahead.insert(KeyCodes.keys(inserted), Typeahead.Remap.YES);
            return;
        }
        // get_yank_register: a name that is not a yank register reads register 0.
        char register = Character.isLetterOrDigit(name) || "\"-*+".indexOf(name) >= 0 ? (char) name : '0';
        Registers.Content content = registers.get(register).orElseThrow(VimFailure::bell);
        reedit(editor, first);
        String text = String.join("\n", content.lines()) + (content.type() == Registers.Type.LINE ? "\n" : "");
        typeahead.insert(KeyCodes.keys(text), Typeahead.Remap.YES);
        executing = (char) name;
    }

    /** valid_yank_reg for reading. */
    private static boolean valid(char name) {
        return name < 128 && Character.isLetterOrDigit(name) || "/.:\"-_*+".indexOf(name) >= 0;
    }

    /**
     * put_reedit_in_typebuf: after {@code i_CTRL-O} the register runs in Normal mode and Insert mode comes back after
     * it, with an {@code i} (or {@code R}) behind its keys, not mapped.
     */
    private static void reedit(VimEditor editor, boolean first) {
        if (!first || editor.mode() != Mode.INSERT_NORMAL) return;
        editor.typeahead().insert(List.of(KeyInput.of(editor.machine().pausedReplace() ? 'R' : 'i')), Typeahead.Remap.NONE);
        editor.mode(Mode.NORMAL);
    }
}
