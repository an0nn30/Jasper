package dev.jasper.app.layout;

/**
 * A group's place in its workspace's unit square, from the top left.
 *
 * @param x the left edge, 0 to 1
 * @param y the top edge, 0 to 1
 * @param width the width, 0 to 1
 * @param height the height, 0 to 1
 */
public record Region(double x, double y, double width, double height) {
    /**
     * The right edge.
     *
     * @return {@code x + width}
     */
    public double right() {
        return x + width;
    }

    /**
     * The bottom edge.
     *
     * @return {@code y + height}
     */
    public double bottom() {
        return y + height;
    }

    /**
     * The horizontal centre.
     *
     * @return {@code x + width / 2}
     */
    public double centerX() {
        return x + width / 2;
    }

    /**
     * The vertical centre.
     *
     * @return {@code y + height / 2}
     */
    public double centerY() {
        return y + height / 2;
    }
}
