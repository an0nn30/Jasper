package dev.jasper.terminal;

/** The selected intersection with the live grid, used to detect overwritten selections. */
public record SelectedCells(long row, int column, String cells) { }
