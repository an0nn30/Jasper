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
     */
    record Change(char register, int count, List<KeyInput> keys) { }

    private final Options options = new Options();
    private final Registers registers;
    private final KeyHandlers handlers = new KeyHandlers();
    private final VimCommands commands = VimCommands.standard();
    private Consumer<String> coverage = id -> { };
    private Find lastFind;
    private Change lastChange;

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

    void lastChange(Change change) { lastChange = change; }
}
