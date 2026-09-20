package dev.jasper.terminal;

/** How many whole cells fit in a pixel area; never below JediTerm 3.76's five-column, two-row minimum. */
public record GridSize(int columns, int rows) {
    static final int MIN_COLUMNS = 5;
    static final int MIN_ROWS = 2;

    public GridSize {
        columns = Math.max(MIN_COLUMNS, columns);
        rows = Math.max(MIN_ROWS, rows);
    }

    public static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight) {
        return new GridSize(widthPx / cellWidth, heightPx / cellHeight);
    }
}
