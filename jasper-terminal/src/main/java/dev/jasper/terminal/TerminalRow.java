package dev.jasper.terminal;

/**
 * Internal cell-reading contract. Detached rows are safe after capture; live rows are
 * valid only while their owner holds the terminal buffer lock. Neither exposes storage.
 */
public interface TerminalRow {
    char CONTINUATION = '\uE000';
    String getText();
    boolean isWrapped();
    int length();
    CellAttributes attributesAt(int column);
    default void readCells(char[] target, CellAttributes[] styles) {
        readCells(target.length, target, styles);
    }
    void readCells(int width, char[] target, CellAttributes[] styles);
}
