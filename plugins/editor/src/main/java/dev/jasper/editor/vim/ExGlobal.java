package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * {@code :g/pat/cmd}, {@code :g!} and {@code :v}: a port of Vim's ex_global and global_exe (ex_cmds.c). The first
 * pass marks the lines that match (or do not); the second runs the command on each marked line in turn, the marks
 * moving with the text and going with deleted lines. The command defaults to {@code :p}.
 */
final class ExGlobal {
    private ExGlobal() { }

    static void global(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        Buffer b = editor.buffer();
        if (ex.globalBusy() && (cmd.line1 != 1 || cmd.line2 != b.count()))
            throw new VimFailure("E147: Cannot do :global recursive with a range");
        char type = cmd.forceit ? 'v' : cmd.typed.charAt(0);
        String arg = cmd.arg;
        int whichPat = -1;
        String pat;
        int i;
        if (arg.startsWith("\\")) {
            char which = arg.length() > 1 ? arg.charAt(1) : 0;
            if (which == 0 || "/?&".indexOf(which) < 0) throw new VimFailure("E10: \\ should be followed by /, ? or &");
            whichPat = which == '&' ? Search.SUBSTITUTE : Search.SEARCH;
            pat = "";
            i = 2;
        } else if (arg.isEmpty()) throw new VimFailure("E148: Regular expression missing from :global");
        else if (arg.charAt(0) < 128 && Character.isLetter(arg.charAt(0))) throw new VimFailure("E146: Regular expressions can't be delimited by letters");
        else {
            char delim = arg.charAt(0);
            int end = 1 + Search.skipPattern(arg.substring(1), delim);
            pat = arg.substring(1, end);
            i = end < arg.length() ? end + 1 : end;
        }
        String command = arg.substring(Math.min(i, arg.length()));
        Search search = editor.vim().search();
        String used;
        VimRegex regex;
        try {
            used = search.choose(pat, -1, whichPat);
            Options o = editor.options();
            regex = VimRegex.compile(used, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), search.prevSub));
        } catch (VimFailure | IllegalArgumentException bad) {
            editor.message(bad.getMessage(), true);
            throw new VimFailure("E476: Invalid command");
        }
        if (ex.globalBusy()) {
            // Nested (:g/a/v/b/cmd): only the current line.
            int lnum = editor.cursor().line;
            if (matches(regex, b, lnum) == (type == 'g')) one(editor, command, lnum);
            return;
        }
        List<Integer> marked = new ArrayList<>();
        String text = regex.multiline() ? b.text() + (b.text().endsWith("\n") ? "" : "\n") : null;
        for (int lnum = cmd.line1 - 1; lnum <= cmd.line2 - 1; lnum++) {
            boolean match = text != null ? matchesIn(regex, b, text, lnum) : matches(regex, b, lnum);
            if (match == (type == 'g')) marked.add(lnum);
        }
        if (marked.isEmpty()) {
            editor.say((type == 'v' ? "Pattern found in every line: " : "Pattern not found: ") + used);
            return;
        }
        editor.marks().markLines(marked);
        new Pass(editor, command).start();
    }

    private static boolean matches(VimRegex regex, Buffer b, int lnum) {
        String line = b.line(lnum);
        Matcher m = regex.pattern().matcher(line + "\n");
        return m.find() && m.start() <= line.length();
    }

    private static boolean matchesIn(VimRegex regex, Buffer b, String text, int lnum) {
        Matcher m = regex.pattern().matcher(text);
        int start = b.start(lnum);
        return m.find(start) && m.start() <= start + b.line(lnum).length();
    }

    /** global_exe_one: the command on one line, from its start; an empty command prints it. */
    private static boolean one(VimEditor editor, String command, int lnum) {
        editor.setCursor(new Pos(lnum, 0));
        return editor.ex().runLine(command.isEmpty() ? "p" : command);
    }

    /** global_exe: the second pass, which a confirmation inside the command can pause. */
    private static final class Pass {
        private final VimEditor editor;
        private final Ex ex;
        private final String command;
        private int oldCount;

        Pass(VimEditor editor, String command) {
            this.editor = editor;
            this.ex = editor.ex();
            this.command = command;
        }

        void start() {
            ex.setpcmark();
            ex.subNsubs = 0;
            ex.subNlines = 0;
            ex.globalNeedBeginline = false;
            ex.globalBusy(true);
            oldCount = editor.buffer().count();
            loop();
        }

        private void loop() {
            ex.globalBusy(true);
            while (true) {
                int lnum = editor.marks().firstMarked();
                if (lnum < 0) break;
                if (!one(editor, command, lnum)) break;
                if (editor.prompting()) {
                    ex.later(this::loop);
                    return;
                }
            }
            end();
        }

        private void end() {
            ex.globalBusy(false);
            editor.marks().clearMarked();
            if (ex.globalNeedBeginline) ex.beginline(editor.cursor().line + 1, false);
            else editor.setCursor(editor.cursor());
            if (!ExSubstitute.message(editor, false)) editor.msgmore(editor.buffer().count() - oldCount);
        }
    }
}
