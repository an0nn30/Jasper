package dev.jasper.sdk.ui;

/**
 * An area of a {@link Nodes.Custom} node's registered content, in the content component's coordinates, such as a field
 * on a page: {@link UiView#anchorAt(String, ContentArea)} anchors a popover to it. An empty area is a point.
 *
 * @param x the left edge
 * @param y the top edge
 * @param width the width, not negative
 * @param height the height, not negative
 * @since 0.32.0
 */
public record ContentArea(int x, int y, int width, int height) {
    /** Refuses a negative width or height. */
    public ContentArea {
        if (width < 0) throw new IllegalArgumentException("An area's width must not be negative");
        if (height < 0) throw new IllegalArgumentException("An area's height must not be negative");
    }
}
