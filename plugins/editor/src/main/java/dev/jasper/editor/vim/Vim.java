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
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
    private Find lastFind;
    private Change lastChange;
    private final Search search = new Search();
    /** Whether the last hex number CTRL-A changed had upper-case letters (do_addsub's static hexupper). */
    private boolean hexUpper;
    /** The uppercase marks: each remembers its editor (Vim's namedfm). */
    private final java.util.Map<Character, GlobalMark> globalMarks = new java.util.HashMap<>();

    private record GlobalMark(VimEditor editor, Pos pos) { }

    /** @param host what the engine needs from the Editor */
    public Vim(VimHost host) { registers = new Registers(host); }

    /** @return the global options */
    public Options options() { return options; }

    /** @return the registers */
    public Registers registers() { return registers; }

    /** @return the handler table */
    public KeyHandlers handlers() { return handlers; }

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

    /** @return an uppercase mark's position when it is in {@code editor}, or null */
    Pos globalMark(char name, VimEditor editor) {
        GlobalMark mark = globalMarks.get(name);
        return mark == null || mark.editor() != editor ? null : mark.pos().copy();
    }

    /** An editor closed: its uppercase marks go. */
    void forget(VimEditor editor) { globalMarks.values().removeIf(mark -> mark.editor() == editor); }

    /** @return the last search pattern, direction and offset, and the search history */
    Search search() { return search; }

    void lastChange(Change change) { lastChange = change; }
}
