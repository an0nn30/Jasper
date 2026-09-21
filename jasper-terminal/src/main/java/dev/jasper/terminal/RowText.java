package dev.jasper.terminal;


import java.util.Arrays;

/**
 * A row as searchable text: one character per cell except wide-character continuation cells, with the first and last
 * column each character covers.
 */
record RowText(String text, int[] columns, int[] lastColumns) {

    static RowText of(TerminalRow line, int width) {
        char[] chars = new char[width];
        line.readCells(width, chars, null);
        StringBuilder text = new StringBuilder(width);
        int[] columns = new int[width];
        int[] lastColumns = new int[width];
        int count = 0;
        for (int column = 0; column < width; column++) {
            if (chars[column] == TerminalRow.CONTINUATION) {
                if (count > 0) {
                    lastColumns[count - 1] = column;
                }
                continue;
            }
            text.append(chars[column]);
            columns[count] = column;
            lastColumns[count] = column;
            count++;
        }
        return new RowText(text.toString(), count == width ? columns : Arrays.copyOf(columns, count),
            count == width ? lastColumns : Arrays.copyOf(lastColumns, count));
    }
}
