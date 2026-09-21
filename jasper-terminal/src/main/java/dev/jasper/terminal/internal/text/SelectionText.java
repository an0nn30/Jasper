package dev.jasper.terminal.internal.text;

import java.util.function.LongFunction;

/** Turns a selection into the text a user expects to paste. */
public final class SelectionText {
    private SelectionText() {
    }

    public static String extract(Selection selection, LongFunction<TerminalRow> lineAt, int width) {
        StringBuilder out = new StringBuilder();
        char[] chars = new char[width];
        for (long row = selection.startRow(); row <= selection.endRow(); row++) {
            TerminalRow line = lineAt.apply(row);
            if (line == null) {
                continue;
            }
            line.readCells(width, chars, null);
            int[] columns = wholeCharacterColumns(selection.columnsOn(row, width), chars);
            StringBuilder rowText = new StringBuilder();
            for (int column = columns[0]; column <= columns[1]; column++) {
                if (chars[column] != TerminalRow.CONTINUATION) {
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

    /** Expand inclusive UTF-16 cell endpoints to include both halves of a displayed character. */
    public static int[] wholeCharacterColumns(int[] columns, char[] chars) {
        int from = columns[0];
        int to = columns[1];
        if (from > 0 && from < chars.length && (chars[from] == TerminalRow.CONTINUATION
            || (Character.isLowSurrogate(chars[from]) && Character.isHighSurrogate(chars[from - 1])))) from--;
        if (to >= 0 && to + 1 < chars.length && (chars[to + 1] == TerminalRow.CONTINUATION
            || (Character.isHighSurrogate(chars[to]) && Character.isLowSurrogate(chars[to + 1])))) to++;
        return new int[] {from, to};
    }

    private static void stripTrailingSpaces(StringBuilder text) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == ' ') {
            end--;
        }
        text.setLength(end);
    }
}
