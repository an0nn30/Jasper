package dev.jasper.app;

import java.time.Duration;

/** Whether a finished command is worth interrupting for. Pure, so the rule is testable without a window. */
final class CommandNotice {
    private CommandNotice() {}

    /**
     * Where a finished command ran, relative to what the user is looking at. All three come from state
     * the app already keeps: {@code WindowContent.active} and whether the pane's tab is the selected one.
     */
    record Origin(boolean anyWindowActive, boolean ownWindowActive, boolean ownTabSelected) {}

    /**
     * True when the command ran at least {@code threshold} and finished somewhere out of sight. A
     * command in a different Jasper window, while another Jasper window has focus, is deliberately
     * quiet: you are still looking at Jasper and that window's tab strip already shows it. Changing
     * that is this one line.
     */
    static boolean shouldNotify(Origin origin, Duration ran, Duration threshold) {
        if (threshold.isZero() || ran.compareTo(threshold) < 0) return false;
        if (!origin.anyWindowActive()) return true;
        return origin.ownWindowActive() && !origin.ownTabSelected();
    }
}
