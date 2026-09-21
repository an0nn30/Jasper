package dev.jasper.app.notifications;

import java.time.Duration;

/** Whether a finished command is worth interrupting for. Pure, so the rule is testable without a window. */
public final class CommandNotice {
    private CommandNotice() {}

    /**
     * Where a finished command ran, relative to what the user is looking at. All four come from state
     * the app already keeps: {@code WindowContent.active}, whether the pane's tab is the selected one,
     * and whether the pane itself owns the keyboard focus.
     */
    public record Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected,
                  boolean ownPaneFocused) {}

    /**
     * True when the command ran at least {@code threshold} and finished anywhere except the pane you
     * were typing in. This is deliberately wider than the previous rule, which stayed quiet for the
     * whole selected tab: a command finishing in a visible but unfocused split pane is easy to miss,
     * and the drawer's card is not enough on its own when the window is behind something.
     */
    static boolean shouldNotify(Origin origin, Duration ran, Duration threshold) {
        if (threshold.isZero() || ran.compareTo(threshold) < 0) return false;
        return !(origin.anyWindowActive() && origin.ownWindowActive()
            && origin.ownTabSelected() && origin.ownPaneFocused());
    }
}
