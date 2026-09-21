package dev.jasper.terminal.internal.text;

/**
 * Internal cell-reading contract. Detached rows are safe after capture; live rows are
 * valid only while their owner holds the terminal buffer lock. Neither exposes storage.
 */
public interface TerminalRow {
    char CONTINUATION = '\uE000';
    public String getText();
    public boolean isWrapped();
    public int length();
    public CellAttributes attributesAt(int column);
    public default void readCells(char[] target, CellAttributes[] styles) {
        readCells(target.length, target, styles);
    }
    public void readCells(int width, char[] target, CellAttributes[] styles);
}
