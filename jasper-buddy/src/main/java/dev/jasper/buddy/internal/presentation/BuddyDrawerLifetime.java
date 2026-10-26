package dev.jasper.buddy.internal.presentation;

import java.awt.Rectangle;

/** Explicit opening owns visibility; passive placement never revives a closed drawer. */
final class BuddyDrawerLifetime {
    private Rectangle anchor;
    private boolean focusRequested, disposed;
    void open(Rectangle value) {
        if (disposed) return;
        anchor = new Rectangle(value); focusRequested = true;
    }
    void move(Rectangle value) { if (anchor != null && !disposed) anchor = new Rectangle(value); }
    Rectangle anchor() { return anchor == null ? null : new Rectangle(anchor); }
    boolean takeFocusRequest() { boolean request = focusRequested; focusRequested = false; return request; }
    void close() { anchor = null; focusRequested = false; }
    void dispose() { close(); disposed = true; }
}
