package dev.moray.terminal;

/** How many whole cells fit in a pixel area; never less than one by one. */
record GridSize(int columns, int rows) {

    static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight) {
        return new GridSize(Math.max(1, widthPx / cellWidth), Math.max(1, heightPx / cellHeight));
    }
}
