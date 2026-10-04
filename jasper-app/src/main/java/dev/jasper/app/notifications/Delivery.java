package dev.jasper.app.notifications;

/**
 * Where a posted notification goes (spec section 7). Pure, so the rule is testable without a window. While Jasper is
 * in the background an auto-hiding card would be stale on return, so it only enters history; a sticky one waits. With
 * no window open, both wait for the first window to be focused.
 */
public final class Delivery {
    private Delivery() {}

    /** Whether a Jasper window is focused, Jasper is behind another application, or no Jasper window is open. */
    public enum Presence { FOCUSED, BACKGROUND, NO_WINDOW }

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
                case BACKGROUND -> Outcome.HISTORY;
                case NO_WINDOW -> Outcome.PENDING;
            };
            case STICKY -> presence == Presence.FOCUSED ? Outcome.CARD : Outcome.PENDING;
        };
    }
}
