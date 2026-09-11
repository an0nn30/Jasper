package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.util.function.LongFunction;

/** Turns a selection into the text a user expects to paste. */
final class SelectionText {
    private SelectionText() {
    }

    static String extract(Selection selection, LongFunction<TerminalLine> lineAt, int width) {
        StringBuilder out = new StringBuilder();
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        for (long row = selection.startRow(); row <= selection.endRow(); row++) {
            TerminalLine line = lineAt.apply(row);
            if (line == null) {
                continue;
            }
            RunBuilder.readCells(line, width, chars, styles);
            int[] columns = selection.columnsOn(row, width);
            StringBuilder rowText = new StringBuilder();
            for (int column = columns[0]; column <= columns[1]; column++) {
                if (chars[column] != CharUtils.DWC) {
                    rowText.append(chars[column]);
                }
            }
            boolean joinsNextRow = !selection.block() && row < selection.endRow() && line.isWrapped();
            if (!joinsNextRow) {
                stripTrailingSpaces(rowText);
            }
            out.append(rowText);
            if (row < selection.endRow() && !joinsNextRow) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static void stripTrailingSpaces(StringBuilder text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        text.setLength(end);
    }
}
