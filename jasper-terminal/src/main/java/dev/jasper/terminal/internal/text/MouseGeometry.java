package dev.jasper.terminal.internal.text;

/** Geometry without copied cells, used on the mouse-report path.
 * @param width live grid width in cells
 * @param height live grid height in cells
 * @param firstRow absolute top row of the requested viewport
 * @param scrollOffset distance from live output in rows
 * @param alternateBuffer whether the alternate screen is active
 */
public record MouseGeometry(int width, int height, long firstRow, int scrollOffset, boolean alternateBuffer) { }
