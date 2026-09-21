package dev.jasper.terminal.config;

/**
 * Terminal dimensions clamped to the pinned emulator minimum of five columns and two rows.
 * @param columns requested number of cell columns, clamped upward to 5
 * @param rows requested number of cell rows, clamped upward to 2
 */
public record GridSize(int columns, int rows) {
    /** Smallest supported emulator width in cells. */
    public static final int MIN_COLUMNS = 5;
    /** Smallest supported emulator height in cells. */
    public static final int MIN_ROWS = 2;

    /** Clamps small dimensions; layout may temporarily have no available pixels. */
    public GridSize {
        columns = Math.max(MIN_COLUMNS, columns);
        rows = Math.max(MIN_ROWS, rows);
    }

    /** Returns whole cells fitting the pixel area, clamped to minimum; cell dimensions must be positive. */
    public static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight) {
        return new GridSize(widthPx / cellWidth, heightPx / cellHeight);
    }
}
