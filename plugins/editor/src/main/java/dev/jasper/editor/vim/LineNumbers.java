package dev.jasper.editor.vim;

import java.awt.event.HierarchyListener;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretListener;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.LineNumberFormatter;
import org.fife.ui.rtextarea.LineNumberList;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * 'number' and 'relativenumber' on the text area's gutter (spec 5.4). The gutter shows line numbers while either is
 * on; with 'relativenumber' each line shows its distance from the cursor line, counting a closed fold as one line,
 * and the cursor line shows its own number with 'number' and 0 without. The text area's row after a final line break
 * is not a line (see {@link Buffer}) and shows no number. When Vim turns off, the gutter is put back as it was.
 */
final class LineNumbers {
    private final VimEditor editor;
    private final VimTextArea area;
    private RTextScrollPane pane;
    private boolean originalEnabled;
    private LineNumberFormatter originalFormatter;
    private boolean closed;
    private int shownLine = -1;
    private final HierarchyListener moved = event -> find();
    private final CaretListener caret = event -> caretMoved();
    private final LineNumberFormatter vimNumbers = new LineNumberFormatter() {
        @Override public String format(int lineNumber) { return LineNumbers.this.format(lineNumber); }

        @Override public int getMaxLength(int maxLineNumber) { return LineNumberList.DEFAULT_LINE_NUMBER_FORMATTER.getMaxLength(maxLineNumber); }
    };

    LineNumbers(VimEditor editor, VimTextArea area) {
        this.editor = editor;
        this.area = area;
        area.addHierarchyListener(moved);
        area.addCaretListener(caret);
        editor.options().onChanged(this::apply);
        find();
    }

    /** The text area may be put into its scroll pane after Vim turns on. */
    private void find() {
        if (closed) return;
        RTextScrollPane found = (RTextScrollPane) SwingUtilities.getAncestorOfClass(RTextScrollPane.class, area);
        if (found == pane) return;
        restore();
        pane = found;
        if (pane == null) return;
        originalEnabled = pane.getLineNumbersEnabled();
        originalFormatter = pane.getGutter().getLineNumberFormatter();
        apply();
    }

    /** The options changed: the gutter follows them. */
    void apply() {
        if (closed || pane == null) return;
        Options options = editor.options();
        boolean number = options.flag("number"), relativeNumber = options.flag("relativenumber");
        pane.setLineNumbersEnabled(number || relativeNumber);
        Gutter gutter = pane.getGutter();
        gutter.setLineNumberFormatter(vimNumbers);
        gutter.repaint();
    }

    /** With 'relativenumber', every number changes when the cursor changes line. */
    private void caretMoved() {
        if (closed || pane == null || !editor.options().flag("relativenumber")) return;
        int line = editor.cursor().line;
        if (line == shownLine) return;
        shownLine = line;
        pane.getGutter().repaint();
    }

    /**
     * @return what the gutter shows for a line (1-based): nothing on the text area's row after a final line break,
     *     which is not a line in Vim; the distance from the cursor line with 'relativenumber'; else the number
     */
    String format(int lineNumber) {
        if (lineNumber > editor.buffer().count()) return "";
        if (!editor.options().flag("relativenumber")) return originalFormatter.format(lineNumber);
        int cursor = editor.cursor().line + 1;
        if (lineNumber == cursor) return editor.options().flag("number") ? Integer.toString(lineNumber) : "0";
        int from = Math.min(lineNumber, cursor), to = Math.max(lineNumber, cursor);
        Folds folds = editor.folds();
        int distance = 0;
        for (int line = from + 1; line <= to; line++) {
            int[] fold = folds.closed(line);
            if (fold == null || fold[0] == line) distance++;
        }
        return Integer.toString(distance);
    }

    /** Vim turned off: the gutter is as it was. */
    void close() {
        restore();
        closed = true;
        area.removeHierarchyListener(moved);
        area.removeCaretListener(caret);
    }

    private void restore() {
        if (pane == null) return;
        pane.getGutter().setLineNumberFormatter(originalFormatter);
        pane.setLineNumbersEnabled(originalEnabled);
        pane = null;
    }
}
