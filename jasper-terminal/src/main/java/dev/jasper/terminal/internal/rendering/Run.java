package dev.jasper.terminal.internal.rendering;

import java.awt.Font;

/**
 * Consecutive cells sharing one style and one font, drawn in a single call.
 * {@code charColumns[i]} is the column of {@code text[i]}, relative to {@code startColumn}.
 * @param startColumn first cell in the run
 * @param columns cell width of the run
 * @param text UTF-16 glyph input
 * @param charColumns relative cell column of each UTF-16 code unit
 * @param style resolved paint style
 * @param font chosen fallback font and variant
 */
record Run(int startColumn, int columns, char[] text, int[] charColumns, CellStyle style, Font font) {
}
