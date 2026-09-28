package dev.jasper.editor.vim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Ex command line (spec 5.3): {@code :} reads a line and this runs it, ported from Vim's ex_docmd.c (do_cmdline,
 * do_one_cmd, parse_cmd_address, get_address, find_ex_command and separate_nextcmd). A line is one undo step; what
 * its commands say goes to the output panel.
 *
 * <p>Vim asks questions in the middle of a command (the {@code :s} confirmation, "Backwards range given") and reads
 * the answer there. Jasper's keys arrive one at a time, so a command that asks returns with the question open, and
 * each caller that has more to do afterwards (the rest of the line, {@code :global}'s next line) leaves that as a
 * continuation with {@link #later}. When the answer comes, the asking command goes on, then the continuations run,
 * innermost first. Each run of a command line keeps its own continuations, so a question asked inside
 * {@code :normal} is settled before the command that ran {@code :normal} goes on.
 */
final class Ex {
    /** Argument flags (ex_cmds.h's EX_*). */
    static final int RANGE = 1, BANG = 1 << 1, EXTRA = 1 << 2, DFLALL = 1 << 3, WHOLEFOLD = 1 << 4, NEEDARG = 1 << 5,
        TRLBAR = 1 << 6, REGSTR = 1 << 7, COUNT = 1 << 8, NOTRLCOM = 1 << 9, ZEROR = 1 << 10, CTRLV = 1 << 11, FLAGS = 1 << 12;
    /** Flags after a command (EXFLAG_*): print, list or number the cursor line afterwards. */
    static final int PRINT = 1, LIST = 2, NUMBER = 4;
    /** Vim's MAXLNUM: no address was given. */
    static final int NO_ADDRESS = Integer.MIN_VALUE;

    /** What an address counts: lines, something else ({@code :undo}'s change number), or nothing. */
    enum Addr { LINES, OTHER, NONE }

    @FunctionalInterface interface Handler { void run(ExCommand cmd); }

    /**
     * A command Jasper implements.
     *
     * @param name its full name, as in Vim's table
     * @param flags its argument flags
     * @param addr what its addresses count
     * @param description what it does, for the reference
     * @param handler what runs it
     */
    record Def(String name, int flags, Addr addr, String description, Handler handler) {
        /** @return the shortest abbreviation Vim takes for it ({@code s} for {@code substitute}) */
        String abbreviation() {
            if (name.equals("k")) return "k";
            for (int n = 1; n < name.length(); n++) if (name.equals(ExTable.resolve(name.substring(0, n)))) return name.substring(0, n);
            return name;
        }
    }

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();

    private static void def(String name, int flags, Addr addr, String description, Handler handler) {
        DEFS.put(name, new Def(name, flags, addr, description, handler));
    }

    static {
        int print = RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR;
        def("print", print, Addr.LINES, "print the lines ([range], default the cursor line); `P` too", ExInfo::print);
        def("Print", print, Addr.LINES, "print the lines, as `:print`", ExInfo::print);
        def("list", print, Addr.LINES, "print the lines with tabs as `^I` and `$` at the end", ExInfo::print);
        def("number", print, Addr.LINES, "print the lines with their numbers", ExInfo::print);
        def("#", print, Addr.LINES, "print the lines with their numbers, as `:number`", ExInfo::print);
        def("=", RANGE | TRLBAR | DFLALL | FLAGS, Addr.LINES, "show the last line's number, or the range's last", ExInfo::equal);
        def("delete", RANGE | WHOLEFOLD | REGSTR | COUNT | TRLBAR, Addr.LINES, "delete the lines [into a register] [count]", ExLines::operators);
        def("yank", RANGE | WHOLEFOLD | REGSTR | COUNT | TRLBAR, Addr.LINES, "yank the lines [into a register] [count]", ExLines::operators);
        def(">", RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "shift the lines right; `>>` twice, and so on", ExLines::operators);
        def("<", RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "shift the lines left; `<<` twice, and so on", ExLines::operators);
        def("put", RANGE | WHOLEFOLD | BANG | REGSTR | TRLBAR | ZEROR, Addr.LINES, "put a register as lines below the line; `!` above; `:0put` above the first", ExLines::put);
        def("join", BANG | RANGE | WHOLEFOLD | COUNT | FLAGS | TRLBAR, Addr.LINES, "join the lines (at least two); `!` without spaces", ExLines::join);
        def("copy", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "copy the lines below {address}", ExLines::copyMove);
        def("t", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "copy the lines below {address}, as `:copy`", ExLines::copyMove);
        def("move", RANGE | WHOLEFOLD | EXTRA | TRLBAR, Addr.LINES, "move the lines below {address}", ExLines::copyMove);
        def("normal", RANGE | BANG | EXTRA | NEEDARG | NOTRLCOM | CTRLV, Addr.LINES, "run {keys} as Normal-mode keys, on each line of the range", ExLines::normal);
        def("sort", RANGE | DFLALL | WHOLEFOLD | BANG | EXTRA | NOTRLCOM, Addr.LINES,
            "sort the lines (all by default); `!` reverses; `i`, `n`, `x`, `o`, `b`, `f`, `u`, `r` and a `/pattern/`", ExLines::sort);
        def("left", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "align the lines left, with [indent]", ExLines::align);
        def("right", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "align the lines right at [width] ('textwidth', or 80)", ExLines::align);
        def("center", TRLBAR | RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "center the lines in [width] ('textwidth', or 80)", ExLines::align);
        def("substitute", RANGE | WHOLEFOLD | EXTRA, Addr.LINES,
            "`:s/pattern/string/flags [count]`: replace matches; flags `& c e g i I n p # l r`", ExSubstitute::substitute);
        def("&", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the same pattern and string; `:&&` keeps its flags", ExSubstitute::substitute);
        def("~", RANGE | WHOLEFOLD | EXTRA, Addr.LINES, "repeat the last `:s` with the last used pattern", ExSubstitute::substitute);
        def("global", RANGE | WHOLEFOLD | BANG | EXTRA | DFLALL, Addr.LINES, "`:g/pattern/cmd`: run cmd (default `:p`) on the matching lines; `!` on the others", ExGlobal::global);
        def("vglobal", RANGE | WHOLEFOLD | EXTRA | DFLALL, Addr.LINES, "`:v/pattern/cmd`: run cmd on the lines that do not match", ExGlobal::global);
        // Vim's EX_WORD1 includes EX_EXTRA: one word of argument.
        def("k", RANGE | EXTRA | TRLBAR, Addr.LINES, "set a mark at the line (`:ka`, `:k a`)", ExInfo::mark);
        def("mark", RANGE | EXTRA | TRLBAR, Addr.LINES, "set a mark at the line", ExInfo::mark);
        def("delmarks", BANG | EXTRA | TRLBAR, Addr.NONE, "delete marks (`a b-d`); `!` all lowercase ones", ExInfo::delmarks);
        def("marks", EXTRA | TRLBAR, Addr.NONE, "list the marks, or those named", ExInfo::marks);
        def("jumps", TRLBAR, Addr.NONE, "list the jumplist", ExInfo::jumps);
        def("changes", TRLBAR, Addr.NONE, "list the change list", ExInfo::changes);
        def("registers", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "list the registers, or those named", ExInfo::registers);
        def("display", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "list the registers, as `:registers`", ExInfo::registers);
        def("undo", RANGE | COUNT | ZEROR | TRLBAR, Addr.OTHER, "undo one change; `:undo N` goes to the text after change N", ExLines::undo);
        def("redo", TRLBAR, Addr.NONE, "redo one change", ExLines::redo);
        def("nohlsearch", TRLBAR, Addr.NONE, "hide the 'hlsearch' matches until the next search", ExInfo::nohlsearch);
        def("set", BANG | TRLBAR | EXTRA, Addr.NONE, "show or set options: `opt`, `noopt`, `invopt`, `opt!`, `opt&`, `opt?`, `opt=val`, `+=`, `-=`, `^=`, `all`", ExInfo::set);
        def("setlocal", BANG | TRLBAR | EXTRA, Addr.NONE, "`:set` for this tab's local values only", ExInfo::set);
        def("setglobal", BANG | TRLBAR | EXTRA, Addr.NONE, "`:set` for the global values only", ExInfo::set);
        int file = RANGE | WHOLEFOLD | BANG | EXTRA | DFLALL | TRLBAR;
        def("write", file, Addr.LINES, "save the tab (`:w file` saves it as the file; `!` replaces a file that exists)", ExFiles::write);
        def("wq", file, Addr.LINES, "save the tab, then close it", ExFiles::writeQuit);
        def("xit", file, Addr.LINES, "save the tab if it has changes, then close it", ExFiles::exit);
        def("exit", file, Addr.LINES, "as `:x`", ExFiles::exit);
        def("saveas", BANG | EXTRA | TRLBAR, Addr.NONE, "save the tab as {file}", ExFiles::saveAs);
        def("wall", BANG | TRLBAR, Addr.NONE, "save every tab with changes", ExFiles::writeAll);
        def("xall", BANG | TRLBAR, Addr.NONE, "save every tab with changes, then close them all", ExFiles::exitAll);
        def("wqall", BANG | TRLBAR, Addr.NONE, "as `:xa`", ExFiles::exitAll);
        def("quit", BANG | TRLBAR, Addr.NONE, "close the tab; `!` discards its changes", ExFiles::quit);
        def("qall", BANG | TRLBAR, Addr.NONE, "close every tab; `!` discards their changes", ExFiles::quitAll);
        def("quitall", BANG | TRLBAR, Addr.NONE, "as `:qa`", ExFiles::quitAll);
        def("edit", BANG | EXTRA | TRLBAR, Addr.NONE, "open {file} in a tab, or read the tab's file again (`!` discards changes)", ExFiles::edit);
        def("enew", BANG | TRLBAR, Addr.NONE, "a new untitled tab", ExFiles::newTab);
        def("read", BANG | RANGE | WHOLEFOLD | EXTRA | TRLBAR | ZEROR, Addr.LINES, "put {file}'s lines below the line (`:0r` above the first)", ExFiles::read);
        def("ls", BANG | EXTRA | TRLBAR, Addr.NONE, "list the tabs as buffers", ExFiles::list);
        def("buffers", BANG | EXTRA | TRLBAR, Addr.NONE, "as `:ls`", ExFiles::list);
        def("files", BANG | EXTRA | TRLBAR, Addr.NONE, "as `:ls`", ExFiles::list);
        def("buffer", BANG | RANGE | COUNT | EXTRA | TRLBAR, Addr.OTHER, "select the tab with buffer number N, or whose name holds {name}", ExFiles::buffer);
        def("bnext", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the next tab, [N] times", ExFiles::step);
        def("bNext", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the previous tab, [N] times", ExFiles::step);
        def("bprevious", BANG | RANGE | COUNT | TRLBAR, Addr.OTHER, "the previous tab, [N] times", ExFiles::step);
        def("tabnext", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "the next tab, or tab [N]", ExFiles::tabNext);
        def("tabNext", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "[N] tabs back", ExFiles::tabPrevious);
        def("tabprevious", RANGE | ZEROR | EXTRA | TRLBAR, Addr.OTHER, "[N] tabs back", ExFiles::tabPrevious);
        def("tabnew", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "a new untitled tab, or {file} in a tab", ExFiles::newTab);
        def("tabedit", BANG | EXTRA | RANGE | ZEROR | TRLBAR, Addr.OTHER, "as `:tabnew`", ExFiles::newTab);
        // The mapping commands take | only escaped and keep CTRL-V (EX_TRLBAR, EX_NOTRLCOM, EX_CTRLV).
        int map = EXTRA | TRLBAR | NOTRLCOM | CTRLV;
        def("map", BANG | map, Addr.NONE, "map {lhs} to {rhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line); list mappings", ExMaps::map);
        def("nmap", map, Addr.NONE, "map {lhs} to {rhs} in Normal mode; list the mappings", ExMaps::map);
        def("vmap", map, Addr.NONE, "map {lhs} to {rhs} in Visual mode; list the mappings", ExMaps::map);
        def("xmap", map, Addr.NONE, "map {lhs} to {rhs} in Visual mode, as `:vmap`; list the mappings", ExMaps::map);
        def("omap", map, Addr.NONE, "map {lhs} to {rhs} in Operator-pending mode; list the mappings", ExMaps::map);
        def("imap", map, Addr.NONE, "map {lhs} to {rhs} in Insert mode; list the mappings", ExMaps::map);
        def("cmap", map, Addr.NONE, "map {lhs} to {rhs} on the command line; list the mappings", ExMaps::map);
        def("noremap", BANG | map, Addr.NONE, "as `:map`, but {rhs} is not mapped again", ExMaps::map);
        def("nnoremap", map, Addr.NONE, "as `:nmap`, but {rhs} is not mapped again", ExMaps::map);
        def("vnoremap", map, Addr.NONE, "as `:vmap`, but {rhs} is not mapped again", ExMaps::map);
        def("xnoremap", map, Addr.NONE, "as `:xmap`, but {rhs} is not mapped again", ExMaps::map);
        def("onoremap", map, Addr.NONE, "as `:omap`, but {rhs} is not mapped again", ExMaps::map);
        def("inoremap", map, Addr.NONE, "as `:imap`, but {rhs} is not mapped again", ExMaps::map);
        def("cnoremap", map, Addr.NONE, "as `:cmap`, but {rhs} is not mapped again", ExMaps::map);
        def("unmap", BANG | map, Addr.NONE, "remove the mapping of {lhs} in Normal, Visual and Operator-pending mode (`!`: Insert and Command-line)", ExMaps::map);
        def("nunmap", map, Addr.NONE, "remove the Normal-mode mapping of {lhs}", ExMaps::map);
        def("vunmap", map, Addr.NONE, "remove the Visual-mode mapping of {lhs}", ExMaps::map);
        def("xunmap", map, Addr.NONE, "remove the Visual-mode mapping of {lhs}, as `:vunmap`", ExMaps::map);
        def("ounmap", map, Addr.NONE, "remove the Operator-pending mapping of {lhs}", ExMaps::map);
        def("iunmap", map, Addr.NONE, "remove the Insert-mode mapping of {lhs}", ExMaps::map);
        def("cunmap", map, Addr.NONE, "remove the command-line mapping of {lhs}", ExMaps::map);
        def("mapclear", BANG | EXTRA | TRLBAR, Addr.NONE, "remove every mapping of `:map` (`!`: of `:map!`)", ExMaps::mapclear);
        def("nmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Normal-mode mapping", ExMaps::mapclear);
        def("vmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Visual-mode mapping", ExMaps::mapclear);
        def("xmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Visual-mode mapping, as `:vmapclear`", ExMaps::mapclear);
        def("omapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Operator-pending mapping", ExMaps::mapclear);
        def("imapclear", EXTRA | TRLBAR, Addr.NONE, "remove every Insert-mode mapping", ExMaps::mapclear);
        def("cmapclear", EXTRA | TRLBAR, Addr.NONE, "remove every command-line mapping", ExMaps::mapclear);
        def("let", EXTRA | NOTRLCOM, Addr.NONE, "`:let mapleader = \",\"`, `:let @a = \"…\"`, `:let &tw = 80`, `:let g:name = …`: a variable, register or option", ExVimrc::let);
        def("source", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run {file}'s lines as command lines, as the vimrc's", ExVimrc::source);
        def("messages", RANGE | EXTRA | TRLBAR, Addr.OTHER, "the message history: errors, and the vimrc's skipped lines; `:messages clear`", ExVimrc::messages);
        def("sethandler", EXTRA | TRLBAR, Addr.NONE, "`:sethandler {key} {modes}:{vim|ide} …`: who gets a key in each mode", ExVimrc::sethandler);
        def("action", EXTRA | NEEDARG | TRLBAR, Addr.NONE, "run the Editor command {id}, as `<Action>(id)` does in a mapping", ExVimrc::action);
        def("actionlist", EXTRA | TRLBAR, Addr.NONE, "list the Editor's command ids, or those holding [text]", ExVimrc::actionlist);
        def("syntax", EXTRA | NOTRLCOM | TRLBAR, Addr.NONE, "accepted and ignored: the Editor highlights by language", ExVimrc::ignored);
        def("filetype", EXTRA | TRLBAR, Addr.NONE, "accepted and ignored: the Editor knows each file's language", ExVimrc::ignored);
    }

    /** @return the commands Jasper implements, in the reference's order */
    static List<Def> implemented() { return List.copyOf(DEFS.values()); }

    /** The keys that open a command line leave the remembered column alone ({@code :s} after {@code $} keeps it). */
    static final List<String> KEEP_COLUMN = List.of(":", "&", "g&");

    /** The Normal- and Visual-mode keys that reach the command line. */
    static void register(VimCommands commands) {
        commands.action(":", "a command line; with a count, for that many lines (`:.,.+N`)", false, c -> c.editor.ex().open(c));
        commands.visual(":", "a command line for the Visual lines (`:'<,'>`)", false, false, c -> c.editor.ex().open(c));
        commands.action("ZZ", "save the tab if it has changes and close it (`:x`)", false, c -> c.editor.ex().execute("x"));
        commands.action("ZQ", "close the tab, discarding its changes (`:q!`)", false, c -> c.editor.ex().execute("q!"));
        commands.action("gt", "the next tab, or tab {count}", false, ExFiles::gt);
        commands.action("gT", "{count} tabs back", false, ExFiles::gT);
        commands.action("&", "repeat the last `:s` on the line, without its flags", false, c -> {
            String line = (c.hasCount ? c.count > 1 ? ".,.+" + (c.count - 1) : "." : "") + "s";
            c.editor.ex().execute(line);
            c.editor.vim().registers().lastCommand(line);
        });
        commands.action("g&", "repeat the last `:s` on every line, with its flags (`:%s//~/&`)", false,
            c -> c.editor.ex().execute("%s//~/&"));
    }

    /**
     * A line of a sourced file (do_source's do_cmdline): it runs as a command line in one undo step, and what it says
     * is dropped.
     *
     * @return its error message, or null when it did not fail with one
     */
    String sourceLine(String line) {
        String error = null;
        // A line that fails is reported with the file's other errors; it does not fail the command line that sourced it.
        boolean outerFailed = failed;
        editor.beginUndoStep();
        try {
            for (VimEditor.OutputLine said : editor.quietly(() -> runLine(line))) if (said.error()) error = said.text();
        } finally {
            editor.endUndoStep();
            failed = outerFailed;
        }
        return error;
    }

    /** A continuation: work left for after a question, or the end of a command line's run (closing its undo step). */
    private record Step(Runnable work, boolean finisher) { }

    private static final Object SUSPENDED = new Object();

    private final VimEditor editor;
    /** The continuations of each suspended run, innermost run first. */
    private final Deque<ArrayDeque<Step>> frames = new ArrayDeque<>();
    /** The continuations registered in the stretch of work now running. */
    private List<Step> fresh = new ArrayList<>();
    /** :global's second pass is running (Vim's global_busy). */
    private int globalBusy;
    /** Whether a command of the running command line failed (do_cmdline's FAIL: no hit-enter prompt then). */
    private boolean failed;
    /** How deep {@code :normal} is running: its keys are not typed (Vim's KeyTyped is false). */
    int normalDepth;
    /** The substitutions and lines {@code :s} changed, for its message and {@code :global}'s (sub_nsubs, sub_nlines). */
    long subNsubs, subNlines;
    /** A {@code :s} inside {@code :global} wants the cursor on the first non-blank at the end (global_need_beginline). */
    boolean globalNeedBeginline;

    Ex(VimEditor editor) { this.editor = editor; }

    boolean globalBusy() { return globalBusy > 0; }

    void globalBusy(boolean busy) {
        globalBusy = busy ? 1 : 0;
        editor.marks().frozen = busy;
    }

    /** @return whether the keys running now were typed (Vim's KeyTyped) */
    boolean typed() { return normalDepth == 0 && editor.typeahead().keyTyped(); }

    /**
     * {@code :} in Normal or Visual mode: the command line opens. A count puts {@code .,.+N} on it, and Visual mode
     * ends with {@code '<,'>} on it (nv_colon, op_colon).
     */
    void open(Context c) {
        String initial = "";
        if (c.inVisual()) {
            // op_colon: the cursor goes to the start of the area (a line's start, a block's top left).
            Pos anchor = c.visual, cursor = c.cursor();
            Mode mode = c.editor.mode();
            Pos start = Pos.min(anchor, cursor).copy();
            if (mode == Mode.VISUAL_LINE) start = cursor.compareTo(anchor) <= 0 ? cursor.copy() : new Pos(anchor.line, 0);
            else if (mode == Mode.VISUAL_BLOCK) {
                int ts = c.tabstop();
                int left = Math.min(Visual.startVcol(c.buffer, anchor, ts), Visual.startVcol(c.buffer, cursor, ts));
                start.col = Text.columnAt(c.buffer.line(start.line), left, ts);
            }
            Visual.end(c.editor);
            c.editor.setCursor(start);
            initial = "'<,'>";
        } else if (c.hasCount) initial = c.count > 1 ? ".,.+" + (c.count - 1) : ".";
        Vim vim = editor.vim();
        editor.commandLine().completer(before -> Completion.complete(editor, before));
        editor.commandLine().start(':', vim.commandHistory(), initial, null, text -> {
            // Only a typed line joins the history; one typed by :normal does not.
            if (typed()) vim.rememberCommand(text);
            execute(text);
            editor.vim().registers().lastCommand(text);
        }, () -> { });
    }

    /** Runs a command line, as typed: one undo step, and what it says gathered for the output panel. */
    void execute(String line) {
        List<VimEditor.OutputLine> before = editor.startCollecting();
        boolean outerFailed = failed;
        if (before == null) failed = false;
        editor.beginUndoStep();
        Runnable finish = () -> {
            editor.endUndoStep();
            editor.stopCollecting(before, failed);
            if (before != null) failed |= outerFailed;
        };
        List<Step> saved = fresh;
        fresh = new ArrayList<>();
        boolean suspended = false;
        try {
            runLine(line);
            if (editor.prompting()) {
                ArrayDeque<Step> frame = new ArrayDeque<>(fresh);
                frame.addLast(new Step(finish, true));
                frames.push(frame);
                suspended = true;
            }
        } finally {
            fresh = saved;
            if (!suspended) finish.run();
        }
    }

    /**
     * do_cmdline: the commands of one line, separated by {@code |}; an error ends the line.
     *
     * @return false when a command failed
     */
    boolean runLine(String line) {
        String text = line;
        while (text != null) {
            Object next;
            try {
                next = one(text);
            } catch (VimFailure failure) {
                fail(failure);
                return false;
            }
            if (next == SUSPENDED) return true;
            String rest = (String) next;
            if (editor.prompting()) {
                if (rest != null) later(() -> runLine(rest));
                return true;
            }
            text = rest;
        }
        return true;
    }

    /** An error message, or the bell for a failure without one. */
    void fail(VimFailure failure) {
        if (!failure.getMessage().isEmpty()) failed = true;
        if (failure.quiet) return;
        // emsg and beep_flush drop the keys of a running mapping or macro.
        editor.typeahead().flush();
        if (failure.getMessage().isEmpty()) editor.bell();
        else editor.message(failure.getMessage(), true);
    }

    /** A command that returned with a question open leaves what it still has to do. */
    void later(Runnable work) { fresh.add(new Step(work, false)); }

    /** The answer to a question came: the asking command goes on, then what waited for it. */
    void answered(Consumer<KeyInput> handler, KeyInput key) {
        ArrayDeque<Step> frame = frames.peek();
        stretch(frame, () -> handler.accept(key));
        if (frame == null) return;
        while (!editor.prompting() && !frame.isEmpty()) {
            Step next = frame.pollFirst();
            stretch(frame, next.work());
        }
        if (frame.isEmpty()) frames.remove(frame);
    }

    /** Runs some work; continuations it registers go in front of the frame's. An error drops the frame's work. */
    private void stretch(ArrayDeque<Step> frame, Runnable body) {
        List<Step> saved = fresh;
        fresh = new ArrayList<>();
        try {
            body.run();
        } catch (VimFailure failure) {
            fail(failure);
            fresh.removeIf(step -> !step.finisher());
            if (frame != null) frame.removeIf(step -> !step.finisher());
        } finally {
            if (frame != null) for (int i = fresh.size() - 1; i >= 0; i--) frame.addFirst(fresh.get(i));
            fresh = saved;
        }
    }

    /** After an internal error or when the text is replaced: every run ends, its undo step closed. */
    void abort() {
        globalBusy(false);
        normalDepth = 0;
        while (!frames.isEmpty()) {
            for (Step step : frames.pop()) {
                if (!step.finisher()) continue;
                try { step.work().run(); }
                catch (RuntimeException ignored) { }
            }
        }
    }

    /**
     * do_one_cmd: parses one command and runs it.
     *
     * @return the text after its {@code |}, null when there is none, or {@link #SUSPENDED} when it asked a question
     *     and will run the rest of the line itself once answered
     */
    private Object one(String line) {
        ExCommand cmd = new ExCommand(editor);
        int start = skipColons(line, 0);
        String afterModifier = line.substring(start);
        // Find the command after the range first: it says what the addresses count.
        int nameStart = skipRange(line, start);
        Found found = find(line, nameStart);
        Addr addr = found.def != null ? found.def.addr() : Addr.LINES;
        int[] at = { start };
        parseAddresses(line, at, cmd, addr);
        int i = skipColons(line, at[0]);
        if (i >= line.length() || line.charAt(i) == '"' || nextCommand(line, i) >= 0) {
            int next = i < line.length() && line.charAt(i) != '"' ? nextCommand(line, i) : -1;
            withoutCommand(cmd, i < line.length() && line.charAt(i) == '|');
            return next >= 0 ? line.substring(next) : null;
        }
        if (found.name == null) throw new VimFailure("E492: Not an editor command: " + afterModifier);
        if (found.def == null) throw new VimFailure("E319: Sorry, the command is not available in this version");
        cmd.def = found.def;
        cmd.flags |= found.flags;
        cmd.typed = line.substring(i);
        int p = found.end;
        if (p < line.length() && line.charAt(p) == '!' && !cmd.name().equals("substitute")) {
            cmd.forceit = true;
            p++;
        }
        int argt = cmd.def.flags();
        if ((argt & RANGE) == 0 && cmd.addrCount > 0) throw new VimFailure("E481: No range allowed");
        if ((argt & BANG) == 0 && cmd.forceit) throw new VimFailure("E477: No ! allowed");
        int argStart = p;
        if ((argt & RANGE) != 0 && globalBusy == 0 && cmd.line1 > cmd.line2) {
            ask("Backwards range given, OK to swap (y/n)?", yes -> {
                int swap = cmd.line1;
                cmd.line1 = cmd.line2;
                cmd.line2 = swap;
                String rest;
                try {
                    rest = finish(cmd, line, argStart);
                } catch (VimFailure failure) {
                    fail(failure);
                    return;
                }
                if (editor.prompting()) {
                    if (rest != null) later(() -> runLine(rest));
                } else if (rest != null) runLine(rest);
            });
            return SUSPENDED;
        }
        return finish(cmd, line, argStart);
    }

    /** ask_yesno: {@code y} or {@code n} (or {@code <Esc>}); another key asks again. Runs {@code yes} for {@code y}. */
    private void ask(String question, Consumer<KeyInput> yes) {
        editor.ask(question, key -> {
            int c = key.plainChar();
            if (c == 'y') yes.accept(key);
            else if (c != 'n' && !key.is("<Esc>") && !key.is("<C-c>")) ask(question, yes);
        });
    }

    /** The rest of do_one_cmd, from the range checks on: the argument, and running the command. */
    private String finish(ExCommand cmd, String line, int p) {
        int argt = cmd.def.flags();
        Buffer b = editor.buffer();
        if ((argt & RANGE) != 0) {
            if (cmd.line1 < 0 || cmd.line2 < 0 || cmd.line1 > cmd.line2) throw new VimFailure("E16: Invalid range");
            if (cmd.def.addr() == Addr.LINES && cmd.line2 > b.count()) throw new VimFailure("E16: Invalid range");
        }
        if (cmd.def.addr() == Addr.OTHER && cmd.addrCount == 0) cmd.line2 = 1;
        if ((argt & ZEROR) == 0) {
            if (cmd.line1 == 0) cmd.line1 = 1;
            if (cmd.line2 == 0) cmd.line2 = 1;
        }
        if (((argt & WHOLEFOLD) != 0 || cmd.addrCount >= 2) && globalBusy == 0 && cmd.def.addr() == Addr.LINES) {
            int[] first = editor.folds().closed(cmd.line1), last = editor.folds().closed(cmd.line2);
            if (first != null) cmd.line1 = first[0];
            if (last != null) cmd.line2 = last[1];
        }
        String arg = line.substring(skipWhite(line, p));
        if (cmd.name().equals(">") || cmd.name().equals("<")) {
            cmd.amount = 1;
            char c = cmd.name().charAt(0);
            int n = 0;
            while (n < arg.length() && arg.charAt(n) == c) {
                n++;
                cmd.amount++;
            }
            arg = arg.substring(skipWhite(arg, n));
        }
        if ((argt & TRLBAR) != 0) {
            String[] split = separate(arg, argt);
            arg = split[0];
            cmd.nextcmd = split[1];
        }
        if ((argt & DFLALL) != 0 && cmd.addrCount == 0) {
            cmd.line1 = 1;
            cmd.line2 = b.count();
        }
        if ((argt & REGSTR) != 0 && !arg.isEmpty() && !((argt & COUNT) != 0 && Character.isDigit(arg.charAt(0)))) {
            char r = arg.charAt(0);
            if (Registers.valid(r, !cmd.name().equals("put"))) {
                cmd.regname = r;
                arg = arg.substring(skipWhite(arg, 1));
            }
        }
        if ((argt & COUNT) != 0 && !arg.isEmpty() && Character.isDigit(arg.charAt(0))) {
            int n = 0;
            long count = 0;
            while (n < arg.length() && Character.isDigit(arg.charAt(n))) count = Math.min(Integer.MAX_VALUE, count * 10 + arg.charAt(n++) - '0');
            arg = arg.substring(skipWhite(arg, n));
            if (count <= 0 && (argt & ZEROR) == 0) throw new VimFailure("E939: Positive count required");
            if (cmd.def.addr() != Addr.LINES) {
                cmd.line2 = (int) count;
                if (cmd.addrCount == 0) cmd.addrCount = 1;
            } else {
                cmd.line1 = cmd.line2;
                cmd.line2 = (int) Math.min((long) b.count(), cmd.line2 + count - 1);
                cmd.addrCount++;
            }
        }
        if ((argt & FLAGS) != 0) {
            int n = 0;
            while (n < arg.length() && "lp#".indexOf(arg.charAt(n)) >= 0) {
                char f = arg.charAt(n);
                cmd.flags |= f == 'l' ? LIST : f == 'p' ? PRINT : NUMBER;
                n = skipWhite(arg, n + 1);
            }
            arg = arg.substring(n);
        }
        if ((argt & EXTRA) == 0 && !arg.isEmpty() && arg.charAt(0) != '"' && (arg.charAt(0) != '|' || (argt & TRLBAR) == 0))
            throw new VimFailure("E488: Trailing characters: " + arg);
        if ((argt & NEEDARG) != 0 && arg.isEmpty()) throw new VimFailure("E471: Argument required");
        cmd.arg = arg;
        editor.vim().covered("ex " + cmd.name());
        cmd.def.handler().run(cmd);
        if (editor.cursor().line < 0) editor.setCursor(new Pos(0, 0));
        return cmd.nextcmd;
    }

    /**
     * separate_nextcmd: a {@code |} ends the argument (a backslash before it keeps it, dropping the backslash), and
     * {@code "} starts a comment unless the command takes it; CTRL-V quotes the next character.
     *
     * @return the argument without trailing white space, and the next command or null
     */
    private static String[] separate(String arg, int argt) {
        StringBuilder out = new StringBuilder();
        String next = null;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == 0x16) {
                if ((argt & CTRLV) != 0) out.append(c);
                if (++i < arg.length()) out.append(arg.charAt(i));
                continue;
            }
            if (c == '"' && (argt & NOTRLCOM) == 0 || c == '|' || c == '\n') {
                if (c != '"' && !out.isEmpty() && out.charAt(out.length() - 1) == '\\') {
                    out.setLength(out.length() - 1);
                    out.append(c);
                    continue;
                }
                if (c == '"' && !out.isEmpty() && out.charAt(out.length() - 1) == '\\') {
                    out.setLength(out.length() - 1);
                    out.append(c);
                    continue;
                }
                int n = nextCommand(arg, i);
                next = n >= 0 ? arg.substring(n) : null;
                break;
            }
            out.append(c);
        }
        String result = out.toString();
        if ((argt & NOTRLCOM) == 0) {
            int end = result.length();
            while (end > 0 && isWhite(result.charAt(end - 1)) && !(end > 1 && result.charAt(end - 2) == '\\')) end--;
            result = result.substring(0, end);
        }
        return new String[] { result, next };
    }

    /** ex_range_without_command: {@code :3} goes to line 3; {@code :3|} and {@code :|} print it. */
    private void withoutCommand(ExCommand cmd, boolean bar) {
        Buffer b = editor.buffer();
        if (bar) {
            if (cmd.line1 < 0 || cmd.line2 < 0 || cmd.line1 > cmd.line2 || cmd.line2 > b.count()) throw new VimFailure("E16: Invalid range");
            if (cmd.line1 == 0) cmd.line1 = 1;
            if (cmd.line2 == 0) cmd.line2 = 1;
            ExInfo.printLines(cmd, false, false);
            return;
        }
        if (cmd.addrCount == 0) return;
        int line = Math.min(cmd.line2, b.count());
        if (line < 0) throw new VimFailure("E16: Invalid range");
        beginline(Math.max(1, line), true);
    }

    /**
     * beginline: the cursor to a line's first non-blank; with {@code sol} (BL_SOL) and 'nostartofline', to the
     * remembered column instead.
     */
    void beginline(int lnum, boolean sol) {
        int line = lnum - 1;
        if (sol && !editor.options().flag("startofline")) editor.setCursor(new Pos(line, editor.wantedColumn(line)));
        else {
            editor.setCursor(new Pos(line, Text.firstNonBlank(editor.buffer().line(line))));
            editor.forgetColumn();
        }
    }

    /** setpcmark, which does nothing while {@code :global} runs. */
    void setpcmark() {
        if (globalBusy == 0) editor.marks().setpcmark(editor.cursor());
    }

    private record Found(String name, Def def, int end, int flags) { }

    /** find_ex_command: the name at {@code i}, resolved like Vim's (one-letter {@code k} and {@code s} forms, {@code :dl}). */
    private static Found find(String line, int i) {
        int n = line.length();
        char c0 = i < n ? line.charAt(i) : 0;
        char c1 = i + 1 < n ? line.charAt(i + 1) : 0, c2 = i + 2 < n ? line.charAt(i + 2) : 0;
        char c3 = i + 3 < n ? line.charAt(i + 3) : 0, c4 = i + 4 < n ? line.charAt(i + 4) : 0;
        if (c0 == 'k' && (c1 != 'e' || c2 != 'e')) return found("k", i + 1, 0);
        if (c0 == 's' && (c1 == 'c' && (c2 == 0 || c2 != 's' && c2 != 'r' && (c3 == 0 || c3 != 'i' && c4 != 'p'))
            || c1 == 'g' || c1 == 'i' && c2 != 'm' && c2 != 'l' && c2 != 'g' || c1 == 'I' || c1 == 'r' && c2 != 'e'))
            return found("substitute", i + 1, 0);
        int p = i;
        while (p < n && isAlpha(line.charAt(p))) p++;
        if (p == i && p < n && "@*!=><&~#}".indexOf(line.charAt(p)) >= 0) p++;
        int len = p - i;
        int flags = 0;
        if (c0 == 'd' && len > 0 && (line.charAt(p - 1) == 'l' || line.charAt(p - 1) == 'p')) {
            int k = 0;
            while (k < len && k < 6 && line.charAt(i + k) == "delete".charAt(k)) k++;
            if (k == len - 1) {
                flags = line.charAt(p - 1) == 'l' ? LIST : PRINT;
                len--;
            }
        }
        if (len == 0) return new Found(null, null, i, 0);
        String name = ExTable.resolve(line.substring(i, i + len));
        // ":*" is the Visual range, not a command, unless 'cpoptions' has '*'.
        if ("*".equals(name)) name = null;
        return name == null ? new Found(null, null, i, 0) : found(name, p, flags);
    }

    private static Found found(String name, int end, int flags) { return new Found(name, DEFS.get(name), end, flags); }

    /** skip_range: past the characters a range may hold, to where the command's name starts. */
    static int skipRange(String line, int start) {
        int i = start, n = line.length();
        while (i < n && " \t0123456789.$%'/?-+,;\\".indexOf(line.charAt(i)) >= 0) {
            char c = line.charAt(i);
            if (c == '\\') {
                if (i + 1 < n && "?/&".indexOf(line.charAt(i + 1)) >= 0) i++;
                else break;
            } else if (c == '\'') {
                // A quote starts a mark only at the start or after a separator.
                int q = i;
                while (q > start) {
                    q--;
                    if (!isWhite(line.charAt(q))) break;
                }
                if (i > start && !isWhite(line.charAt(q)) && line.charAt(q) != ',' && line.charAt(q) != ';') break;
                i++;
            } else if (c == '/' || c == '?') {
                i++;
                while (i < n && line.charAt(i) != c) if (line.charAt(i++) == '\\' && i < n) i++;
            }
            if (i < n) i++;
        }
        i = skipColons(line, i);
        if (i < n && line.charAt(i) == '*') i = skipWhite(line, i + 1);
        return i;
    }

    /** parse_cmd_address: the {@code ,} and {@code ;} separated addresses, {@code %} and {@code *}. */
    private void parseAddresses(String line, int[] at, ExCommand cmd, Addr addr) {
        Buffer b = editor.buffer();
        addressCursor = editor.cursor().line + 1;
        int addressCount = 1;
        int lnum = NO_ADDRESS;
        boolean movedCursor = false;
        while (true) {
            cmd.line1 = cmd.line2;
            cmd.line2 = defaultAddress(addr);
            at[0] = skipWhite(line, at[0]);
            lnum = address(line, at, addr, cmd.addrCount == 0, addressCount++);
            if (lnum == NO_ADDRESS) {
                char c = at[0] < line.length() ? line.charAt(at[0]) : 0;
                if (c == '%') {
                    at[0]++;
                    if (addr == Addr.LINES) {
                        cmd.line1 = 1;
                        cmd.line2 = b.count();
                    } else if (addr == Addr.OTHER) throw new VimFailure("E16: Invalid range");
                    cmd.addrCount++;
                } else if (c == '*') {
                    if (addr != Addr.LINES) throw new VimFailure("E16: Invalid range");
                    at[0]++;
                    cmd.line1 = MarkCommands.check(b, MarkCommands.get(editor, '<')).line + 1;
                    cmd.line2 = MarkCommands.check(b, MarkCommands.get(editor, '>')).line + 1;
                    cmd.addrCount++;
                }
            } else cmd.line2 = lnum;
            cmd.addrCount++;
            char c = at[0] < line.length() ? line.charAt(at[0]) : 0;
            if (c == ';') {
                // The cursor moves to the address, and the next one counts from there; 0 is kept, so 0;/pat/ can
                // find a match in the first line.
                addressCursor = Math.max(0, Math.min(cmd.line2, b.count()));
                if (addressCursor > 0) editor.setCursor(new Pos(addressCursor - 1, editor.cursor().col));
                movedCursor = true;
            } else if (c != ',') break;
            at[0]++;
        }
        if (cmd.addrCount == 1) {
            cmd.line1 = cmd.line2;
            if (lnum == NO_ADDRESS) cmd.addrCount = 0;
        }
        if (movedCursor) editor.setCursor(editor.cursor());
    }

    /** get_address for {@code :move} and {@code :copy}'s destination, from the start of {@code text}. */
    int address(String text, int[] at) {
        addressCursor = editor.cursor().line + 1;
        return address(text, at, Addr.LINES, false, 1);
    }

    /** The cursor line addresses count from (1-based); {@code ;} moves it, perhaps to 0. */
    private int addressCursor;

    private int defaultAddress(Addr addr) {
        return addr == Addr.NONE ? 0 : Math.min(addressCursor, editor.buffer().count());
    }

    /** get_address: one address and its offsets; {@link #NO_ADDRESS} when there is none. */
    private int address(String line, int[] at, Addr addr, boolean first, int addressCount) {
        Buffer b = editor.buffer();
        int i = skipWhite(line, at[0]), n = line.length();
        int lnum = NO_ADDRESS;
        do {
            char c = i < n ? line.charAt(i) : 0;
            switch (c) {
                case '.' -> {
                    i++;
                    if (addr == Addr.NONE) throw new VimFailure("E481: No range allowed");
                    lnum = addr == Addr.LINES ? addressCursor : lnum;
                }
                case '$' -> {
                    i++;
                    if (addr == Addr.NONE) throw new VimFailure("E481: No range allowed");
                    lnum = b.count();
                }
                case '\'' -> {
                    if (++i >= n) throw VimFailure.quiet();
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    Pos mark = MarkCommands.check(b, MarkCommands.get(editor, line.charAt(i)));
                    i++;
                    lnum = mark.line + 1;
                }
                case '/', '?' -> {
                    i++;
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    int from = lnum != NO_ADDRESS && lnum > 0 ? Math.min(lnum, b.count()) : addressCursor;
                    int[] result = editor.vim().search().address(new Context(editor), c, line.substring(i), from);
                    lnum = result[0];
                    i += result[1];
                }
                case '\\' -> {
                    i++;
                    if (addr != Addr.LINES) throw new VimFailure(addr == Addr.NONE ? "E481: No range allowed" : "E16: Invalid range");
                    char which = i < n ? line.charAt(i) : 0;
                    if ("&?/".indexOf(which) < 0 || which == 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
                    int from = lnum != NO_ADDRESS ? lnum : addressCursor;
                    lnum = editor.vim().search().repeatAddress(new Context(editor), which, from);
                    i++;
                }
                default -> {
                    if (Character.isDigit(c)) {
                        long v = 0;
                        while (i < n && Character.isDigit(line.charAt(i))) v = Math.min(Integer.MAX_VALUE - 1, v * 10 + line.charAt(i++) - '0');
                        lnum = (int) v;
                    }
                }
            }
            while (true) {
                i = skipWhite(line, i);
                char d = i < n ? line.charAt(i) : 0;
                if (d != '-' && d != '+' && !Character.isDigit(d)) break;
                if (lnum == NO_ADDRESS) lnum = addr == Addr.LINES ? addressCursor : addr == Addr.OTHER ? 1 : 0;
                char sign;
                if (Character.isDigit(d)) sign = '+';
                else {
                    sign = d;
                    i++;
                }
                long v = 1;
                if (i < n && Character.isDigit(line.charAt(i))) {
                    v = 0;
                    while (i < n && Character.isDigit(line.charAt(i))) v = Math.min(Integer.MAX_VALUE - 1, v * 10 + line.charAt(i++) - '0');
                }
                // After the first address, relative addresses count a closed fold as one line.
                if (addr == Addr.LINES && addressCount >= 2) {
                    int[] fold = editor.folds().closed(lnum);
                    if (fold != null) lnum = fold[1];
                }
                long moved = sign == '-' ? lnum - v : lnum + v;
                if (moved >= Integer.MAX_VALUE - 1) throw new VimFailure("E1247: Line number out of range");
                lnum = (int) moved;
            }
        } while (i < n && (line.charAt(i) == '/' || line.charAt(i) == '?'));
        at[0] = i;
        return lnum;
    }

    /** check_nextcmd: the index after a {@code |} at {@code i} (after white space), or -1. */
    private static int nextCommand(String s, int i) {
        int j = skipWhite(s, i);
        return j < s.length() && (s.charAt(j) == '|' || s.charAt(j) == '\n') ? j + 1 : -1;
    }

    private static int skipColons(String s, int i) {
        i = skipWhite(s, i);
        while (i < s.length() && s.charAt(i) == ':') i = skipWhite(s, i + 1);
        return i;
    }

    static int skipWhite(String s, int i) {
        while (i < s.length() && isWhite(s.charAt(i))) i++;
        return i;
    }

    static boolean isWhite(char c) { return c == ' ' || c == '\t'; }

    private static boolean isAlpha(char c) { return c < 128 && Character.isLetter(c); }
}
