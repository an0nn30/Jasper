package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Replays a corpus case on Jasper's engine (spec 11.1): a headless {@link VimTextArea} holding the text with a final
 * line break, Vim's own option defaults, and the keys fed as typed.
 */
final class CorpusRunner {
    private CorpusRunner() { }

    /** @return what the engine did, in the corpus's terms; runs on the event thread, as the Editor does */
    static CorpusCase.Expected run(CorpusCase c, Consumer<String> coverage) {
        var result = new java.util.concurrent.atomic.AtomicReference<CorpusCase.Expected>();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> result.set(runHere(c, coverage)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (java.lang.reflect.InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failed.getCause());
        }
        return result.get();
    }

    private static CorpusCase.Expected runHere(CorpusCase c, Consumer<String> coverage) {
        var vim = new Vim(VimHost.detached());
        vim.coverage(coverage);
        var area = new VimTextArea();
        area.setInsertPairedCharacters(false);
        area.setCloseCurlyBraces(false);
        area.setCloseMarkupTags(false);
        area.setText(String.join("\n", c.text()) + "\n");
        area.discardAllEdits();
        if (c.folds() != null) javaFolds(area, c.closedAtStart());
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        // Vim's window when the recorder starts: 80 columns, 23 rows (24 lines, one for the command line).
        int[] size = c.window() != null ? c.windowSize() : new int[] { 80, 23 };
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
            vim.registers().set(entry.getKey(), register.content());
        }
        String start = c.text().get(c.line() - 1);
        editor.setCursor(new Pos(c.line() - 1, Text.column(start, c.col() - 1)));
        // Vim scrolls to the cursor before it reads the first key.
        editor.view().updateTopline();
        editor.messageLog().clear();
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
        List<String> output = null;
        if (c.output()) {
            output = new ArrayList<>();
            for (String said : editor.messageLog()) for (String line : said.split("\n", -1)) if (!line.isEmpty()) output.add(line);
        }
        Buffer buffer = editor.buffer();
        List<String> text = new ArrayList<>();
        for (int line = 0; line < buffer.count(); line++) text.add(buffer.line(line));
        if (buffer.length() == 0) text = List.of("");
        Pos cursor = editor.cursor();
        int col = Text.characters(buffer.line(cursor.line), cursor.col) + 1;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        for (char name : c.registers()) {
            vim.registers().get(name).ifPresent(content -> registers.put(name, CorpusCase.Register.of(content)));
        }
        String visual = null;
        Pos anchor = editor.visualAnchor();
        if (editor.mode().visual() && anchor != null)
            visual = (anchor.line + 1) + "," + (Text.characters(buffer.line(anchor.line), Math.min(anchor.col, buffer.line(anchor.line).length())) + 1);
        String view = c.window() == null ? null : editor.view().topline + "," + editor.view().leftcol;
        String folds = c.folds() == null ? null : closedFolds(editor);
        String mode = editor.mode().id();
        editor.close();
        return new CorpusCase.Expected("jasper", text, cursor.line + 1, col, mode, visual, registers, view, folds, output);
    }

    /** The text's code folds as RSyntaxTextArea's Java fold parser finds them, those starting on the given lines closed. */
    static void javaFolds(VimTextArea area, List<Integer> closed) {
        area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
        area.setCodeFoldingEnabled(true);
        area.getFoldManager().reparse();
        for (int line : closed) collapse(area.getFoldManager(), line - 1);
    }

    private static void collapse(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager, int startLine) {
        for (var fold : folds(manager)) if (fold.getStartLine() == startLine) fold.setCollapsed(true);
    }

    /** @return every fold, outer before inner */
    static List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager) {
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> out = new ArrayList<>();
        for (int i = 0; i < manager.getFoldCount(); i++) add(manager.getFold(i), out);
        return out;
    }

    private static void add(org.fife.ui.rsyntaxtextarea.folding.Fold fold, List<org.fife.ui.rsyntaxtextarea.folding.Fold> out) {
        out.add(fold);
        for (int i = 0; i < fold.getChildCount(); i++) add(fold.getChild(i), out);
    }

    /** @return the closed folds as Vim's foldclosed() reports them: {@code 2-5 8-9}, or {@code none} */
    static String closedFolds(VimEditor editor) {
        List<String> out = new ArrayList<>();
        int count = editor.buffer().count();
        for (int lnum = 1; lnum <= count; lnum++) {
            int[] fold = editor.folds().closed(lnum);
            if (fold != null && fold[0] == lnum) {
                out.add(fold[0] + "-" + fold[1]);
                lnum = fold[1];
            }
        }
        return out.isEmpty() ? "none" : String.join(" ", out);
    }

    /** @return the differences from the expectation, or empty */
    static String compare(CorpusCase.Expected expected, CorpusCase.Expected actual) {
        List<String> problems = new ArrayList<>();
        if (!expected.text().equals(actual.text())) problems.add("text " + actual.text() + " (Vim: " + expected.text() + ")");
        if (expected.line() != actual.line() || expected.col() != actual.col())
            problems.add("cursor " + actual.line() + "," + actual.col() + " (Vim: " + expected.line() + "," + expected.col() + ")");
        if (!expected.mode().equals(actual.mode())) problems.add("mode " + actual.mode() + " (Vim: " + expected.mode() + ")");
        if (!java.util.Objects.equals(expected.visual(), actual.visual())) problems.add("visual " + actual.visual() + " (Vim: " + expected.visual() + ")");
        if (!java.util.Objects.equals(expected.view(), actual.view())) problems.add("view " + actual.view() + " (Vim: " + expected.view() + ")");
        if (!java.util.Objects.equals(expected.folds(), actual.folds())) problems.add("folds " + actual.folds() + " (Vim: " + expected.folds() + ")");
        if (!expected.registers().equals(actual.registers())) problems.add("registers " + show(actual.registers()) + " (Vim: " + show(expected.registers()) + ")");
        if (expected.output() != null && !expected.output().equals(actual.output())) problems.add("output " + actual.output() + " (Vim: " + expected.output() + ")");
        return String.join("; ", problems);
    }

    private static String show(Map<Character, CorpusCase.Register> registers) {
        List<String> parts = new ArrayList<>();
        registers.forEach((name, register) -> parts.add(name + "=" + register.type() + Json.quote(register.text())));
        return "{" + String.join(", ", parts) + "}";
    }
}
