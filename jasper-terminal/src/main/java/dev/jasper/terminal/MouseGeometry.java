package dev.jasper.terminal;

/** Geometry without copied cells, used on the mouse-report path. */
public record MouseGeometry(int width, int height, long firstRow, int scrollOffset, boolean alternateBuffer) { }
