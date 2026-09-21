package dev.jasper.terminal.internal.text;

/**
 * A selection over absolute rows (see {@code TerminalSession}) and columns, both ends inclusive. A stream selection
 * runs like text from its start cell to its end cell; a block selection is a rectangle.
 * @param anchorRow absolute anchor row
 * @param anchorColumn anchor cell
 * @param focusRow absolute focus row
 * @param focusColumn focus cell
 * @param block whether the range is rectangular
 */
public record Selection(long anchorRow, int anchorColumn, long focusRow, int focusColumn, boolean block) {

    public static Selection at(long row, int column, boolean block) {
        return new Selection(row, column, row, column, block);
    }

    public Selection withFocus(long row, int column) {
        return new Selection(anchorRow, anchorColumn, row, column, block);
    }

    public long startRow() {
        return Math.min(anchorRow, focusRow);
    }

    public long endRow() {
        return Math.max(anchorRow, focusRow);
    }

    /** First selected column on the start row (stream), or the left edge (block). */
    public int startColumn() {
        if (block) {
            return Math.min(anchorColumn, focusColumn);
        }
        return anchorFirst() ? anchorColumn : focusColumn;
    }

    /** Last selected column on the end row (stream), or the right edge (block). */
    public int endColumn() {
        if (block) {
            return Math.max(anchorColumn, focusColumn);
        }
        return anchorFirst() ? focusColumn : anchorColumn;
    }

    public boolean contains(long row, int column) {
        if (row < startRow() || row > endRow()) {
            return false;
        }
        if (block) {
            return column >= startColumn() && column <= endColumn();
        }
        if (row == startRow() && column < startColumn()) {
            return false;
        }
        return row != endRow() || column <= endColumn();
    }

    /** The selected columns of a row as inclusive {from, to}, clipped to the width; null when the row is not selected. */
    public int[] columnsOn(long row, int width) {
        if (row < startRow() || row > endRow()) {
            return null;
        }
        int from = block || row == startRow() ? startColumn() : 0;
        int to = block || row == endRow() ? endColumn() : width - 1;
        return new int[] {Math.max(0, from), Math.min(width - 1, to)};
    }

    private boolean anchorFirst() {
        return anchorRow < focusRow || (anchorRow == focusRow && anchorColumn <= focusColumn);
    }
}
