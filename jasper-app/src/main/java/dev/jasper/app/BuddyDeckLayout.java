package dev.jasper.app;

import java.awt.Rectangle;

/**
 * Where the deck's cards sit, collapsed and expanded. Pure geometry: no AWT devices are queried and
 * no Swing state is read, so every offset, the scroll clamp and the hit tests are unit-testable.
 *
 * <p>All cards are the same size. A stack of differently sized cards reads as a mess rather than a
 * deck, and uniform rows make the hit test arithmetic rather than a search.
 */
final class BuddyDeckLayout {
    static final int ROW_GAP = 6;
    static final int COLUMN_GAP = 6;
    /** Beyond this the nearest bubble carries a count; a taller column would cover the screen. */
    static final int MAX_IN_COLUMN = 3;
    /** The band between his head and the nearest bubble, where the thought tail sits. */
    static final int TAIL_HEIGHT = 22;
    static final int TAIL_BIG = 11;
    static final int TAIL_SMALL = 7;
    static final int DISMISS_SIZE = 14;
    static final int DISMISS_INSET = 6;
    static final int CLEAR_ROW_HEIGHT = 26;

    private BuddyDeckLayout() { }

    static int visibleInColumn(int count) { return Math.min(Math.max(count, 0), MAX_IN_COLUMN); }

    /** The bubbles, the gaps between them, and the band the tail lives in. */
    static int columnHeight(int count, int cardHeight) {
        int visible = visibleInColumn(count);
        if (visible == 0) return 0;
        return visible * cardHeight + (visible - 1) * COLUMN_GAP + TAIL_HEIGHT;
    }

    /**
     * Bubble {@code index} counting from the newest. The newest is always the one nearest his head:
     * lowest when the column is above him, highest when it has been flipped below.
     */
    static Rectangle column(int index, int count, int width, int cardHeight, boolean below) {
        int visible = visibleInColumn(count);
        int pitch = cardHeight + COLUMN_GAP;
        int y = below ? TAIL_HEIGHT + index * pitch : (visible - 1 - index) * pitch;
        return new Rectangle(0, y, width, cardHeight);
    }

    /**
     * Two circles tapering from his head towards the nearest bubble, largest first. Without them the
     * column is a floating list rather than something he is thinking.
     */
    static Rectangle[] tail(int width, int columnHeight, boolean below) {
        int centre = width / 2;
        int band = below ? 0 : columnHeight - TAIL_HEIGHT;
        int bigY = below ? band + TAIL_HEIGHT - TAIL_BIG - 2 : band + 2;
        int smallY = below ? band + 2 : band + TAIL_HEIGHT - TAIL_SMALL - 2;
        // Both sit on his centre line, with the small one trailing a little: the column is centred on
        // him, so a tail that wanders sideways stops pointing at the head it is supposed to come from.
        return new Rectangle[] {
            new Rectangle(centre - TAIL_BIG / 2, bigY, TAIL_BIG, TAIL_BIG),
            new Rectangle(centre - TAIL_SMALL / 2 + TAIL_BIG / 2, smallY, TAIL_SMALL, TAIL_SMALL)
        };
    }

    /** The bubble at {@code y} in the column, or -1 in a gap or the tail band. */
    static int columnAt(int y, int count, int cardHeight, boolean below) {
        int visible = visibleInColumn(count);
        for (int index = 0; index < visible; index++) {
            Rectangle card = column(index, count, 1, cardHeight, below);
            if (y >= card.y && y < card.y + card.height) return index;
        }
        return -1;
    }

    /** Row {@code index} in the expanded list, already shifted by the scroll offset. */
    static Rectangle expanded(int index, int width, int cardHeight, int scroll) {
        return new Rectangle(0, index * (cardHeight + ROW_GAP) - scroll, width, cardHeight);
    }

    /** Every row, the gaps between them, and the clear-all row when there is more than one card. */
    static int expandedHeight(int count, int cardHeight) {
        if (count <= 0) return 0;
        int rows = count * (cardHeight + ROW_GAP) - ROW_GAP;
        return count > 1 ? rows + ROW_GAP + CLEAR_ROW_HEIGHT : rows;
    }

    /** Null for a single card: clearing all of one card is what the card's own × already does. */
    static Rectangle clearRow(int count, int width, int cardHeight, int scroll) {
        if (count <= 1) return null;
        return new Rectangle(0, count * (cardHeight + ROW_GAP) - scroll, width, CLEAR_ROW_HEIGHT);
    }

    /** Clamped so the list can neither run past the last card nor above the first. */
    static int clampScroll(int scroll, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight)));
    }

    /** The card at {@code y} in the expanded list, or -1 in a gap, the clear-all row, or past the end. */
    static int cardAt(int y, int count, int cardHeight, int scroll) {
        int pitch = cardHeight + ROW_GAP;
        int local = y + scroll;
        if (local < 0) return -1;
        int index = local / pitch;
        if (index >= count) return -1;
        return local % pitch < cardHeight ? index : -1;
    }

    /** The dismiss target, always strictly inside its own card so it cannot hit the one behind. */
    static Rectangle dismissTarget(Rectangle card) {
        return new Rectangle(card.x + card.width - DISMISS_INSET - DISMISS_SIZE,
            card.y + DISMISS_INSET, DISMISS_SIZE, DISMISS_SIZE);
    }
}
