package dev.jasper.terminal;

/** How many whole cells fit in a pixel area; never below JediTerm 3.76's five-column, two-row minimum. */
record GridSize(int columns, int rows) {
    static final int MIN_COLUMNS = 5;
    static final int MIN_ROWS = 2;

    GridSize {
        columns = Math.max(MIN_COLUMNS, columns);
        rows = Math.max(MIN_ROWS, rows);
    }

    static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight) {
        return new GridSize(widthPx / cellWidth, heightPx / cellHeight);
    }
}
