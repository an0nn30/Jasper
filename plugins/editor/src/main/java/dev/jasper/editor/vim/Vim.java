package dev.jasper.editor.vim;

import java.util.List;
import java.util.function.Consumer;

/**
 * Vim's global state, one per plugin (spec 1.1): options' global values, registers, the command table, the handler
 * table, the last {@code f}/{@code t} search and the last change for {@code .}.
 */
public final class Vim {
    /** The last character search, for {@code ;} and {@code ,}. */
    record Find(boolean forward, boolean till, int character) { }

    /**
     * The last change, for {@code .}: its keys without counts or register.
     *
     * @param register the register typed, or 0
     * @param count the count typed (the product of both counts), or 0 for none
     * @param keys the keys, including any text typed in Insert mode and its {@code <Esc>}
     * @param visual for a Visual-mode change, the size of its area (Vim's redo_VIsual); null otherwise
     */
    record Change(char register, int count, List<KeyInput> keys, Visual.Redo visual) {
        Change(char register, int count, List<KeyInput> keys) { this(register, count, keys, null); }
    }

    private final Options options = new Options();
    private final VimHost host;
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final Mappings mappings = new Mappings();
    private final Macros macros = new Macros();
    private final Extensions extensions = new Extensions(this);
    /** The global variables {@code :let} sets ({@code g:mapleader}, extension settings), by name without {@code g:}. */
    private final java.util.Map<String, Object> variables = new java.util.HashMap<>();
    /** The vimrc or {@code :source} file being read, or null. */
    private Vimrc.Reading reading;
    /** The message history {@code :messages} shows, oldest first (Vim's 'msghistory', 500). */
    private final java.util.List<String> messages = new java.util.ArrayList<>();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
    private Find lastFind;
    private Change lastChange;
    private final Search search = new Search();
    /** The command-line history ({@code :}), oldest first. */
    private final java.util.List<String> commandHistory = new java.util.ArrayList<>();
    /** Whether the last hex number CTRL-A changed had upper-case letters (do_addsub's static hexupper). */
    private boolean hexUpper;
    /** The uppercase marks: each remembers its editor (Vim's namedfm). */
    private final java.util.Map<Character, GlobalMark> globalMarks = new java.util.HashMap<>();

    /** An uppercase mark: the editor of the view it was set in, and where it is. */
    record GlobalMark(VimEditor editor, Pos pos) {
        /** @return whether the mark is in {@code other}'s file, in any of its views: Vim's current buffer */
        boolean inFileOf(VimEditor other) { return editor.marks().sameBuffer(other.marks()); }
    }

    /** @param host what the engine needs from the Editor */
    public Vim(VimHost host) {
        this.host = host;
        registers = new Registers(host);
        // An extension's switch (set surround) adds or takes away its mappings.
        options.onChanged(extensions::sync);
    }

    /** @return what the engine needs from the Editor */
    VimHost host() { return host; }

    /** @return the global options */
    public Options options() { return options; }

    /** @return the registers */
    public Registers registers() { return registers; }

    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

    /** @return the global variables, by name without {@code g:} */
    java.util.Map<String, Object> variables() { return variables; }

    /**
     * @param name {@code mapleader} or {@code maplocalleader}
     * @return what {@code <Leader>} or {@code <LocalLeader>} stands for: the variable, or a backslash when it is
     *     not set, empty or longer than Vim's 48 bytes
     */
    String leader(String name) {
        Object value = variables.get(name);
        String text = value == null ? "" : value.toString();
        return text.isEmpty() || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 48 ? "\\" : text;
    }

    Vimrc.Reading reading() { return reading; }

    void reading(Vimrc.Reading value) { reading = value; }

    /** An error given, or a line the vimrc skipped, joins the message history. */
    void remember(String message) {
        messages.add(message);
        if (messages.size() > 500) messages.removeFirst();
    }

    /** @return the message history, oldest first */
    java.util.List<String> messages() { return messages; }

    /**
     * Reload vimrc (spec 7.1): mappings, options' global values, the handler table and the variables go back to
     * their defaults; registers, marks and history stay.
     */
    public void reset() {
        mappings.clear(Mappings.MAP | Mappings.MAP_BANG);
        options.resetGlobal();
        handlers.clear();
        variables.clear();
    }

    /** @return the emulated extensions */
    Extensions extensions() { return extensions; }

    /** @return macro recording and execution */
    Macros macros() { return macros; }

    /** @return the key mappings */
    Mappings mappings() { return mappings; }

    /** @return the command table */
    public VimCommands commands() { return commands; }

    /** @param coverage told the id of every command that runs; the corpus coverage test listens */
    public void coverage(Consumer<String> coverage) { this.coverage = coverage; }

    void covered(String id) { coverage.accept(id); }

    Find lastFind() { return lastFind; }

    void lastFind(Find find) { lastFind = find; }

    Change lastChange() { return lastChange; }

    boolean hexUpper() { return hexUpper; }

    void hexUpper(boolean value) { hexUpper = value; }

    /** Sets an uppercase mark; it moves with that editor's text. */
    void globalMark(char name, VimEditor editor, Pos pos) {
        GlobalMark old = globalMarks.remove(name);
        if (old != null) old.editor().marks().untrack(old.pos());
        Pos tracked = pos.copy();
        editor.marks().track(tracked);
        globalMarks.put(name, new GlobalMark(editor, tracked));
    }

    /**
     * getmark_buf_fnum: a file mark in the current buffer is a mark there, whichever of the file's views it was set in.
     *
     * @return an uppercase mark's position when it is in {@code editor}'s file, or null
     */
    Pos globalMark(char name, VimEditor editor) {
        GlobalMark mark = globalMarks.get(name);
        return mark == null || !mark.inFileOf(editor) ? null : mark.pos().copy();
    }

    /** {@code :delmarks}: an uppercase mark goes. */
    void clearGlobalMark(char name) {
        GlobalMark old = globalMarks.remove(name);
        if (old != null) old.editor().marks().untrack(old.pos());
    }

    /** @return the uppercase and digit marks, by name, for {@code :marks} */
    java.util.SortedMap<Character, GlobalMark> globalMarks() { return new java.util.TreeMap<>(globalMarks); }

    /**
     * An editor closed: its uppercase marks go to another editor of its file, as Vim's file marks are the file's (pane
     * flexibility spec 6), and go, untracked, with the file's last editor.
     */
    void forget(VimEditor editor) {
        VimEditor heir = editor.marks().sibling();
        if (heir != null) moveGlobalMarks(editor, heir);
        globalMarks.values().removeIf(mark -> {
            if (mark.editor() != editor) return false;
            editor.marks().untrack(mark.pos());
            return true;
        });
    }

    /** {@code from}'s uppercase marks become {@code to}'s, an editor of the same file, where they stay tracked. */
    void moveGlobalMarks(VimEditor from, VimEditor to) {
        if (!from.marks().sameBuffer(to.marks())) return;
        globalMarks.replaceAll((name, mark) -> mark.editor() == from ? new GlobalMark(to, mark.pos()) : mark);
    }

    /** @return the last search pattern, direction and offset, and the search history */
    Search search() { return search; }

    void lastChange(Change change) { lastChange = change; }

    /** @return the {@code :} history, oldest first */
    java.util.List<String> commandHistory() { return commandHistory; }

    /** A typed command line joins the history (at its end, once), as Vim's add_to_history. */
    void rememberCommand(String line) {
        if (line.isEmpty()) return;
        commandHistory.remove(line);
        commandHistory.add(line);
        if (commandHistory.size() > 50) commandHistory.removeFirst();
    }
}
