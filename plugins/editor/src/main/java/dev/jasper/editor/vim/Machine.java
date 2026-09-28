package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Normal-mode command parser (spec 1): {@code ["x][count]command}, where a command is an action, a motion, or
 * an operator followed by {@code [count][v|V]} and a motion or text object. It records each change for {@code .}
 * and brackets it in one undo step; a change that enters Insert mode stays open until the insert ends.
 */
final class Machine {
    private final VimEditor editor;
    private final Vim vim;
    private int count1, count2;
    private char register;
    private String operator;
    private char forced;
    private String keys = "";
    private final List<KeyInput> shown = new ArrayList<>();
    private Consumer<KeyInput> argument;
    /** The keys of the change being typed, without counts or register, for {@code .}. */
    private final List<KeyInput> record = new ArrayList<>();
    private char recordRegister;
    private int recordCount;
    private boolean recordingInsert, replaying, resumeReplace;
    private int resumeEndLine = -1;
    private int changeDepth;

    Machine(VimEditor editor) {
        this.editor = editor;
        this.vim = editor.vim();
    }

    boolean operatorPending() { return operator != null; }

    /** @return whether a command is partly typed */
    boolean pending() { return operator != null || argument != null || !keys.isEmpty() || count1 > 0 || count2 > 0 || register != 0; }

    /** @return the partly typed command, for 'showcmd' */
    String showcmd() { return KeyInput.format(shown); }

    boolean replaying() { return replaying; }

    /** @return whether the key means something now, for the handler rule */
    boolean wants(KeyInput key) {
        if (argument != null || key.plainChar() >= 0 || key.is("<Esc>")) return true;
        if (key.is("<C-c>")) return pending() || editor.mode() == Mode.INSERT_NORMAL;
        String candidate = keys + key.notation();
        VimCommands commands = vim.commands();
        VimCommands.Entry entry = operator == null ? commands.normal(candidate) : commands.pending(candidate);
        return entry != null || commands.prefix(candidate, operator != null);
    }

    void key(KeyInput key) {
        shown.add(key);
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            if (key.is("<Esc>")) {
                reset();
                resumeIfInsertNormal();
                return;
            }
            handler.accept(key);
            return;
        }
        if (key.is("<Esc>") || key.is("<C-c>")) {
            reset();
            resumeIfInsertNormal();
            return;
        }
        int c = key.plainChar();
        if (keys.isEmpty() && c >= '0' && c <= '9' && (c != '0' || (operator == null ? count1 : count2) > 0)) {
            if (operator == null) count1 = Math.min(count1 * 10 + (c - '0'), 99_999_999);
            else count2 = Math.min(count2 * 10 + (c - '0'), 99_999_999);
            return;
        }
        if (keys.isEmpty() && operator == null && c == '"') {
            argument = name -> {
                int n = name.plainChar();
                if (n < 0 || !Registers.valid((char) n, false)) throw Context.fail();
                register = (char) n;
            };
            return;
        }
        if (operator != null && keys.isEmpty() && (c == 'v' || c == 'V')) {
            forced = (char) c;
            record.add(key);
            return;
        }
        keys += key.notation();
        record.add(key);
        if (operator != null && doubled()) {
            applyOperator(context(null), lineSpan(context(null)));
            return;
        }
        VimCommands.Entry entry;
        if (operator == null) entry = keys.equals("~") && editor.options().flag("tildeop") ? vim.commands().normal("g~") : vim.commands().normal(keys);
        else entry = vim.commands().pending(keys);
        if (entry == null) {
            if (vim.commands().prefix(keys, operator != null)) return;
            throw Context.fail();
        }
        if (entry.needsChar()) {
            VimCommands.Entry waiting = entry;
            argument = arg -> {
                record.add(arg);
                run(waiting, arg);
            };
            return;
        }
        run(entry, null);
    }

    /** {@code dd}, {@code gUU}, {@code gUgU}, and {@code ~~} with 'tildeop'. */
    private boolean doubled() {
        if (keys.equals(operator)) return true;
        if (operator.length() == 2 && operator.charAt(0) == 'g' && keys.equals(operator.substring(1))) return true;
        return operator.equals("g~") && keys.equals("~") && editor.options().flag("tildeop");
    }

    private Context context(KeyInput argument) {
        var context = new Context(editor);
        context.hasCount = count1 > 0 || count2 > 0;
        context.count = Math.max(count1, 1) * Math.max(count2, 1);
        context.register = register;
        context.argument = argument;
        context.operator = operator;
        context.forced = forced;
        return context;
    }

    private void run(VimCommands.Entry entry, KeyInput argument) {
        Context context = context(argument);
        vim.covered(entry.id());
        switch (entry.kind()) {
            case OPERATOR -> {
                operator = entry.keys();
                count1 = context.hasCount ? context.count : 0;
                count2 = 0;
                keys = "";
            }
            case MOTION -> {
                Target target = ((VimCommands.Motion) entry.handler()).move(context);
                if (target == null) throw Context.fail();
                if (operator == null) {
                    editor.moveTo(target);
                    complete(false);
                } else applyOperator(context, span(context, target));
            }
            case OBJECT -> {
                Span span = ((VimCommands.TextObject) entry.handler()).select(context);
                if (span == null) throw Context.fail();
                applyOperator(context, span);
            }
            case ACTION -> {
                if (entry.keys().equals(".")) {
                    reset();
                    repeat(context);
                    resumeIfInsertNormal();
                    return;
                }
                boolean change = entry.change();
                if (!entry.keys().equals("u") && !entry.keys().equals("<C-r>")) editor.forgetColumn();
                if (change) beginChange();
                try {
                    ((VimCommands.Action) entry.handler()).run(context);
                } catch (RuntimeException failure) {
                    if (change) endChangeUnlessInserting();
                    throw failure;
                }
                complete(change);
            }
            case INSERT -> throw new IllegalStateException("Insert-mode key in Normal mode: " + entry.keys());
        }
    }

    /**
     * The lines a doubled operator acts on (nv_lineop): the cursor line and {@code count - 1} more. Like Vim, the
     * "motion" lands on the last line's first non-blank (keeping the column for {@code yy}, and for {@code dd},
     * {@code <<} and {@code >>} with 'nostartofline'), so the span starts at whichever comes first.
     */
    private Span lineSpan(Context context) {
        Pos cursor = context.cursor();
        int last = editor.buffer().count() - 1;
        if (context.count > 1 && cursor.line >= last) throw Context.fail();
        int end = Math.min(last, cursor.line + context.count - 1);
        if (forced == 'v') return new Span(cursor.copy(), new Pos(end, editor.wantedColumn(end)), Kind.EXCLUSIVE);
        String text = editor.buffer().line(end);
        boolean sol = operator.equals("d") || operator.equals(">") || operator.equals("<lt>");
        int col;
        if (operator.equals("y")) col = end == cursor.line ? cursor.col : editor.wantedColumn(end);
        else if (sol && !editor.options().flag("startofline")) col = editor.wantedColumn(end);
        else col = Text.firstNonBlank(text) >= text.length() && !text.isEmpty() ? Text.last(text) : Text.firstNonBlank(text);
        Pos target = new Pos(end, col);
        return new Span(Pos.min(cursor, target).copy(), Pos.max(cursor, target).copy(), Kind.LINEWISE);
    }

    /** Runs {@code x}, {@code D} and the other abbreviations as their operator and motion, inside their own step. */
    void alias(Context action, String operatorKeys, String motionKeys) {
        operator = operatorKeys;
        count1 = action.hasCount ? action.count : 0;
        count2 = 0;
        Context context = context(null);
        Span span;
        if (motionKeys.equals(operatorKeys)) span = lineSpan(context);
        else {
            VimCommands.Entry motion = vim.commands().pending(motionKeys);
            Target target = ((VimCommands.Motion) motion.handler()).move(context);
            if (target == null) throw Context.fail();
            span = span(context, target);
        }
        applyBody(context, span);
        operator = null;
    }

    /** The span an operator acts on for a motion: forced motion types and Vim's exclusive adjustment. */
    private Span span(Context context, Target target) {
        Pos cursor = context.cursor();
        Kind kind = target.kind();
        if (forced == 'v') kind = kind == Kind.LINEWISE ? Kind.EXCLUSIVE : kind == Kind.EXCLUSIVE ? Kind.INCLUSIVE : Kind.EXCLUSIVE;
        else if (forced == 'V') kind = Kind.LINEWISE;
        Pos start = Pos.min(cursor, target.pos()).copy(), end = Pos.max(cursor, target.pos()).copy();
        if (kind == Kind.EXCLUSIVE && forced == 0 && target.adjust() && end.col == 0 && end.line > start.line) {
            end.line--;
            if (context.walk(false).inIndent(start, 0)) kind = Kind.LINEWISE;
            else {
                String line = editor.buffer().line(end.line);
                end.col = line.length();
                if (end.col > 0) {
                    end.col = Text.previous(line, end.col);
                    kind = Kind.INCLUSIVE;
                }
            }
        }
        return new Span(start, end, kind, target.regOne());
    }

    private void applyOperator(Context context, Span span) {
        boolean change = !operator.equals("y");
        editor.forgetColumn();
        editor.setCursor(span.start(), true);
        if (change) beginChange();
        try {
            applyBody(context, span);
        } catch (RuntimeException failure) {
            if (change) endChangeUnlessInserting();
            throw failure;
        }
        complete(change);
    }

    private void applyBody(Context context, Span span) {
        VimCommands.Entry entry = vim.commands().normal(operator);
        vim.covered(entry.id());
        editor.setCursor(span.start(), true);
        ((VimCommands.Operator) entry.handler()).apply(context, span);
    }

    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (change && !replaying) {
            recordRegister = register;
            recordCount = count1 > 0 || count2 > 0 ? Math.max(count1, 1) * Math.max(count2, 1) : 0;
            if (editor.mode().inserting()) recordingInsert = true;
            else vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record)));
        }
        if (change) endChangeUnlessInserting();
        reset();
        resumeIfInsertNormal();
    }

    /** After {@code i_CTRL-O}'s command, or an {@code <Esc>} instead of one, Insert mode resumes as a new insert. */
    void resumeIfInsertNormal() {
        if (editor.mode() != Mode.INSERT_NORMAL) return;
        // edit.c: back after the end of the line when it was there before CTRL-O, or when the column wants it ($).
        Pos p = editor.cursor();
        String line = editor.buffer().line(p.line);
        if (!line.isEmpty() && p.col == Text.last(line) && (p.line == resumeEndLine || editor.wantsPastEnd())) editor.setCursor(new Pos(p.line, line.length()), true);
        resumeEndLine = -1;
        editor.insert().begin("i", 1, resumeReplace, -1);
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of(resumeReplace ? 'R' : 'i'));
        recordRegister = 0;
        recordCount = 0;
        recordingInsert = true;
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
    void reset() {
        count1 = count2 = 0;
        register = 0;
        operator = null;
        forced = 0;
        keys = "";
        argument = null;
        shown.clear();
        if (!recordingInsert) record.clear();
    }

    /** After an internal error: forgets everything, including a recorded insert, and closes every undo step. */
    void abort() {
        recordingInsert = false;
        reset();
        while (changeDepth > 0) endChange();
    }

    void recordInsert(KeyInput key) {
        if (recordingInsert && !replaying) record.add(key);
    }

    /** The insert a recorded change entered has ended: stores the change and closes its undo step. */
    void insertEnded() {
        if (recordingInsert && !replaying) {
            record.add(KeyInput.named("Esc"));
            vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record)));
        }
        recordingInsert = false;
        record.clear();
        endChange();
    }

    /**
     * {@code i_CTRL-O} was typed: Insert mode resumes after one command.
     *
     * @param replace whether it was Replace mode
     * @param endLine the line whose end the cursor was after, or -1
     */
    void pausedInsert(boolean replace, int endLine) {
        resumeReplace = replace;
        resumeEndLine = endLine;
    }

    /** An arrow key moved in Insert mode: a new undo step, and {@code .} repeats only what follows, as {@code i}. */
    void restartInsert() {
        endChange();
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of('i'));
        recordRegister = 0;
        recordCount = 0;
        recordingInsert = true;
    }

    /** {@code .}: the last change again, the new count replacing its count; {@code "1p.} puts register 2. */
    void repeat(Context context) {
        Vim.Change change = vim.lastChange();
        if (change == null) throw Context.fail();
        int count = context.hasCount ? context.count : change.count();
        char reg = change.register();
        if (reg >= '1' && reg < '9') reg++;
        List<KeyInput> replay = new ArrayList<>();
        if (reg != 0) {
            replay.add(KeyInput.of('"'));
            replay.add(KeyInput.of(reg));
        }
        if (count > 0) String.valueOf(count).chars().forEach(d -> replay.add(KeyInput.of(d)));
        replay.addAll(change.keys());
        replaying = true;
        beginChange();
        try {
            for (KeyInput key : replay) editor.dispatch(key);
        } finally {
            replaying = false;
            endChange();
            recordingInsert = false;
            reset();
        }
        vim.lastChange(new Vim.Change(reg, count, change.keys()));
    }

    private void beginChange() {
        changeDepth++;
        editor.beginUndoStep();
    }

    private void endChange() {
        if (changeDepth == 0) return;
        changeDepth--;
        editor.endUndoStep();
    }

    private void endChangeUnlessInserting() {
        if (!editor.mode().inserting()) endChange();
    }
}
