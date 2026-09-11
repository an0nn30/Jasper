package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.util.Arrays;

/**
 * A row as searchable text: one character per cell except wide-character continuation cells, with the first and last
 * column each character covers.
 */
record RowText(String text, int[] columns, int[] lastColumns) {

    static RowText of(TerminalLine line, int width) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        RunBuilder.readCells(line, width, chars, styles);
        StringBuilder text = new StringBuilder(width);
        int[] columns = new int[width];
        int[] lastColumns = new int[width];
        int count = 0;
        for (int column = 0; column < width; column++) {
            if (chars[column] == CharUtils.DWC) {
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
        return new RowText(text.toString(), Arrays.copyOf(columns, count), Arrays.copyOf(lastColumns, count));
    }
}
