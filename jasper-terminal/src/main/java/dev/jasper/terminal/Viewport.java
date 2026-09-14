package dev.jasper.terminal;

/**
 * Which rows the view shows: the live screen, following new output, or a scrolled-back position anchored at an
 * absolute row so its lines stay put while output arrives. Event Dispatch Thread only.
 */
final class Viewport {
    private long topRow = ScreenSnapshot.FOLLOW_OUTPUT;

    long topRow() {
        return topRow;
    }

    boolean following() {
        return topRow == ScreenSnapshot.FOLLOW_OUTPUT;
    }

    void follow() {
        topRow = ScreenSnapshot.FOLLOW_OUTPUT;
    }

    /** Scrolls by lines (negative = back into the scrollback) from what {@code current} shows. */
    void scrollBy(int lines, ScreenSnapshot current) {
        moveTo(current.firstRow() + lines, current);
    }

    /** Puts an absolute row at the top of the view, as far as the scrollback allows. */
    void showAtTop(long row, ScreenSnapshot current) {
        moveTo(row, current);
    }

    /** Scrolls the least needed to make an absolute row visible. */
    void reveal(long row, ScreenSnapshot current) {
        long top = current.firstRow();
        long bottom = top + current.height() - 1;
        if (row < top) {
            moveTo(row, current);
        } else if (row > bottom) {
            moveTo(row - current.height() + 1, current);
        }
    }

    private void moveTo(long requestedTop, ScreenSnapshot current) {
        long liveTop = current.firstRow() + current.scrollOffset();
        long oldestTop = liveTop - current.historyLines();
        long top = Math.max(oldestTop, Math.min(liveTop, requestedTop));
        topRow = top == liveTop ? ScreenSnapshot.FOLLOW_OUTPUT : top;
    }
}
