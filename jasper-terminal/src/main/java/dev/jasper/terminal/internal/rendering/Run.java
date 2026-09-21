package dev.jasper.terminal.internal.rendering;

import java.awt.Font;

/**
 * Consecutive cells sharing one style and one font, drawn in a single call.
 * {@code charColumns[i]} is the column of {@code text[i]}, relative to {@code startColumn}.
 */
record Run(int startColumn, int columns, char[] text, int[] charColumns, CellStyle style, Font font) {
}
