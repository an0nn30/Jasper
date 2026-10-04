package dev.jasper.app.notifications;

/**
 * Where a posted notification goes (spec section 7). Pure, so the rule is testable without a window. By presence:
 * <ul>
 *   <li>focused: a card shows now;
 *   <li>in the background: an auto-hiding card would be stale on return, so it only enters history; a sticky one waits
 *       for Jasper to be focused;
 *   <li>no window yet (before the first window of a start, while one opens, or until it is activated): both wait for
 *       the window to be focused, so a failure raised while it opens shows its card;
 *   <li>idle (a resident Jasper with no window open and none being opened, which can last for hours): an auto-hiding
 *       card goes to history, as in the background; only a sticky one waits for the next window.
 * </ul>
 */
public final class Delivery {
    private Delivery() {}

    /** Where Jasper stands for notifications. */
    public enum Presence {
        /** A Jasper window is focused. */
        FOCUSED,
        /** Jasper is behind another application. */
        BACKGROUND,
        /** No window has been focused yet: before the first window of a start, while one opens, or until it is activated. */
        NO_WINDOW,
        /** A resident Jasper with no window open and none being opened; it may stay that way for hours. */
        IDLE
    }

    /**
     * @param record whether it enters the history
     * @param card whether a card shows now
     * @param pending whether a card waits until Jasper is next focused
     */
    public record Outcome(boolean record, boolean card, boolean pending) {
        static final Outcome DROP = new Outcome(false, false, false);
        static final Outcome HISTORY = new Outcome(true, false, false);
        static final Outcome CARD = new Outcome(true, true, false);
        static final Outcome PENDING = new Outcome(true, false, true);
    }

    public static Outcome route(DisplayMode mode, Presence presence) {
        return switch (mode) {
            case OFF -> Outcome.DROP;
            case HISTORY -> Outcome.HISTORY;
            case CARD -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND, IDLE -> Outcome.HISTORY;
                case NO_WINDOW -> Outcome.PENDING;
            };
            case STICKY -> presence == Presence.FOCUSED ? Outcome.CARD : Outcome.PENDING;
        };
    }
}
