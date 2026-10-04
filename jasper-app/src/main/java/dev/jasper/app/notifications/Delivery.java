package dev.jasper.app.notifications;

/**
 * Where a posted notification goes (spec section 7). Pure, so the rule is testable without a window. While Jasper is
 * in the background an auto-hiding card would be stale on return, so it only enters history; a sticky one waits; and
 * the operating system shows either, which is how a person away from Jasper hears of it. With no window open the card
 * waits for the next one and the operating system shows it meanwhile. While Jasper starts or a window opens the card
 * waits for that window and the operating system is left alone, since the card is about to show.
 */
public final class Delivery {
    private Delivery() {}

    /**
     * Whether a Jasper window is focused; Jasper is behind another application; Jasper runs with every window closed
     * (a resident Jasper); or Jasper is starting, or opening a window that has not been activated yet.
     */
    public enum Presence { FOCUSED, BACKGROUND, NO_WINDOW, OPENING }

    /**
     * @param record whether it enters the history
     * @param card whether a card shows now
     * @param pending whether a card waits until Jasper is next focused
     * @param system whether the operating system shows it now
     */
    public record Outcome(boolean record, boolean card, boolean pending, boolean system) {
        static final Outcome DROP = new Outcome(false, false, false, false);
        static final Outcome HISTORY = new Outcome(true, false, false, false);
        static final Outcome CARD = new Outcome(true, true, false, false);
        static final Outcome PENDING = new Outcome(true, false, true, false);
        static final Outcome HISTORY_AND_SYSTEM = new Outcome(true, false, false, true);
        static final Outcome PENDING_AND_SYSTEM = new Outcome(true, false, true, true);
    }

    public static Outcome route(DisplayMode mode, Presence presence) {
        return switch (mode) {
            case OFF -> Outcome.DROP;
            case HISTORY -> Outcome.HISTORY;
            case CARD -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND -> Outcome.HISTORY_AND_SYSTEM;
                case NO_WINDOW -> Outcome.PENDING_AND_SYSTEM;
                case OPENING -> Outcome.PENDING;
            };
            case STICKY -> switch (presence) {
                case FOCUSED -> Outcome.CARD;
                case BACKGROUND, NO_WINDOW -> Outcome.PENDING_AND_SYSTEM;
                case OPENING -> Outcome.PENDING;
            };
        };
    }
}
