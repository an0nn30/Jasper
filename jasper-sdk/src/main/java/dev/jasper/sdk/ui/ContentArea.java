package dev.jasper.sdk.ui;

/**
 * An area of a {@link Nodes.Custom} node's registered content, in the content component's coordinates, such as a field
 * on a page: {@link UiView#anchorAt(String, ContentArea)} anchors a popover to it, and the area must lie inside the
 * content. An empty area is a point. The host sees where the content is, not what it draws there: a content that moves
 * what it draws, by scrolling or zooming, closes the anchor itself.
 *
 * @param x the left edge
 * @param y the top edge
 * @param width the width, not negative
 * @param height the height, not negative
 * @since 0.32.0
 */
public record ContentArea(int x, int y, int width, int height) {
    /**
     * Refuses a negative width or height, and an area whose right or bottom edge is past {@link Integer#MAX_VALUE}.
     *
     * @throws IllegalArgumentException when {@code width} or {@code height} is negative, or {@code x + width} or
     *     {@code y + height} is greater than {@link Integer#MAX_VALUE}
     */
    public ContentArea {
        if (width < 0) throw new IllegalArgumentException("An area's width must not be negative");
        if (height < 0) throw new IllegalArgumentException("An area's height must not be negative");
        if ((long) x + width > Integer.MAX_VALUE) throw new IllegalArgumentException("An area's right edge must be an int");
        if ((long) y + height > Integer.MAX_VALUE) throw new IllegalArgumentException("An area's bottom edge must be an int");
    }
}
