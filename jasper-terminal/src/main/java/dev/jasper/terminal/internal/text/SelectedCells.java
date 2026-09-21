package dev.jasper.terminal.internal.text;

/** The selected intersection with the live grid, used to detect overwritten selections.
 * @param row absolute live row when selected
 * @param column first captured cell
 * @param cells captured UTF-16 cell content used to detect overwrite
 */
public record SelectedCells(long row, int column, String cells) { }
