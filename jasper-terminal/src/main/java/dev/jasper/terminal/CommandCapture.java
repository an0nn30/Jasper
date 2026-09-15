package dev.jasper.terminal;

import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.util.function.LongFunction;

/** The text a user typed between OSC 133 B and C, read cell by cell under the buffer lock; bounded rows. */
final class CommandCapture {
    static final int MAX_ROWS = 64;

    private CommandCapture() {
    }

    static String text(long firstRow, int firstColumn, long lastRow, int width, LongFunction<TerminalLine> lineAt) {
        if (lastRow < firstRow || width <= 0) return "";
        lastRow = Math.min(lastRow, firstRow + MAX_ROWS - 1);
        var text = new StringBuilder();
        char[] cells = new char[width];
        for (long row = firstRow; row <= lastRow; row++) {
            TerminalLine line = lineAt.apply(row);
            if (line == null) break;
            RunBuilder.readCells(line, width, cells, null);
            int from = row == firstRow ? Math.min(firstColumn, width) : 0;
            var content = new StringBuilder(width - from);
            for (int column = from; column < width; column++) {
                char cell = cells[column];
                if (cell != CharUtils.DWC) content.append(cell);
            }
            text.append(content.toString().stripTrailing());
            if (row < lastRow && !line.isWrapped()) text.append('\n');
        }
        return text.toString().strip();
    }
}
