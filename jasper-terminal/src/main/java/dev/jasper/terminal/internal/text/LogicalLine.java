package dev.jasper.terminal.internal.text;

import java.util.function.LongFunction;

/**
 * Shared soft-wrap traversal, called under the buffer lock. Includes at most 4096 rows and 1 MiB of
 * UTF-16 cell/text work (two bytes per cell), plus constant-size boundary probes. This is a text
 * budget, not a bound on all temporary object bytes. Extreme lines retain a bounded range containing
 * the clicked row, preferring preceding rows, and cannot produce a plain-text URL. A single row wider
 * than the budget selects only its first MAX_CELLS columns and is always considered truncated.
 * @param firstRow first included absolute row
 * @param lastRow last included absolute row, inclusive
 * @param columns maximum readable cells per included row
 * @param truncated whether bounded traversal omitted part of the logical line
 */
public record LogicalLine(long firstRow, long lastRow, int columns, boolean truncated) {
    public static final int MAX_ROWS = 4096;
    public static final int MAX_TEXT_BYTES = 1 << 20;
    public static final int MAX_CELLS = MAX_TEXT_BYTES / Character.BYTES;

    public static LogicalLine around(long row, int width, LongFunction<TerminalRow> lineAt) {
        if (width <= 0) throw new IllegalArgumentException("Logical line width must be positive");
        if (width > MAX_CELLS) return new LogicalLine(row, row, MAX_CELLS, true);
        int limit = Math.min(MAX_ROWS, MAX_CELLS / width);
        int count = 1;
        long first = row;
        long last = row;
        TerminalRow clicked = lineAt.apply(row);
        if (clicked == null) return new LogicalLine(row, row, width, true);
        boolean truncated = false;
        while (first != Long.MIN_VALUE) {
            TerminalRow above = lineAt.apply(first - 1);
            if (above == null || !above.isWrapped()) break;
            if (count == limit) {
                truncated = true;
                break;
            }
            first--;
            count++;
        }
        TerminalRow current = clicked;
        while (current.isWrapped()) {
            if (last == Long.MAX_VALUE) {
                truncated = true;
                break;
            }
            TerminalRow below = lineAt.apply(last + 1);
            if (below == null || count == limit) {
                truncated = true;
                break;
            }
            last++;
            count++;
            current = below;
        }
        return new LogicalLine(first, last, width, truncated);
    }

    public int rowCount() {
        return (int) (lastRow - firstRow + 1);
    }
}
