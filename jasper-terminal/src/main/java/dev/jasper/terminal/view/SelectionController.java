package dev.jasper.terminal.view;

import dev.jasper.terminal.internal.TerminalAccess;
import dev.jasper.terminal.internal.text.SelectedCells;
import dev.jasper.terminal.internal.text.Selection;
import java.util.List;
import java.util.Optional;

/** EDT-owned selection and drag anchors; validation and text extraction share the terminal's lock. */
final class SelectionController {
    private final TerminalAccess terminal;
    private Selection selection;
    private Selection pendingAnchor;
    private Selection wordAnchor;
    private List<SelectedCells> selectedLiveCells = List.of();
    SelectionController(TerminalAccess terminal) { this.terminal = terminal; }
    Selection range() { return selection; }
    boolean hasSelection() { return selection != null; }
    void set(Selection next) {
        selection = next;
        selectedLiveCells = next == null ? List.of() : terminal.selectedLiveCells(next);
    }
    void clear() { set(null); pendingAnchor = null; wordAnchor = null; }
    Optional<String> selectedText() {
        if (selection == null) return Optional.empty();
        Optional<String> text = terminal.selectedText(selection,selectedLiveCells);
        if (text.isEmpty()) set(null);
        return text;
    }
    void validate() {
        if (selection != null && !terminal.selectionUnchanged(selectedLiveCells)) set(null);
    }
    void start(long row,int column,boolean block) {
        set(null); wordAnchor = null; pendingAnchor = Selection.at(row,column,block);
    }
    void word(long row,int column) {
        wordAnchor = terminal.wordSelection(row,column); set(wordAnchor); pendingAnchor = null;
    }
    void line(long row) {
        wordAnchor = null; set(terminal.lineSelection(row)); pendingAnchor = null;
    }
    void finish() { pendingAnchor = null; wordAnchor = null; }

    void extend(long row, int column) {

                Selection next = selection == null ? pendingAnchor : selection;
                if (wordAnchor != null) {
                    Selection word = terminal.wordSelection(row, column);
                    boolean before = word.startRow() < wordAnchor.startRow()
                        || (word.startRow() == wordAnchor.startRow() && word.startColumn() < wordAnchor.startColumn());
                    next = before
                        ? new Selection(wordAnchor.endRow(), wordAnchor.endColumn(), word.startRow(), word.startColumn(), false)
                        : new Selection(wordAnchor.startRow(), wordAnchor.startColumn(), word.endRow(), word.endColumn(), false);
                } else if (next != null) {
                    next = next.withFocus(row, column);
                }
                set(next);
                }
}
