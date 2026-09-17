package dev.jasper.app;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Turns a finished command into a notice, and keeps the buddy typing while long ones are in flight.
 * Holds no Swing state, so the routing is testable without a window.
 */
final class CommandNotifier {
    /** Where a notice is shown. Two implementations: the buddy's bubble, and {@link NativeNotifier}. */
    interface Channel {
        void deliver(String title, String detail, boolean succeeded, Runnable onActivate);
    }

    /** A command title longer than this is cut; the row it points at is unaffected. */
    private static final int MAX_TITLE = 60;

    private final Supplier<Duration> threshold;
    private final Channel buddy;
    private final Channel operatingSystem;
    private final Consumer<Boolean> onWorkingChanged;
    /** Commands that have been running past the threshold; EDT only. */
    private int running;

    CommandNotifier(Supplier<Duration> threshold, Channel buddy, Channel operatingSystem,
                    Consumer<Boolean> onWorkingChanged) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.buddy = Objects.requireNonNull(buddy, "buddy");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
    }

    /** A command has now been running long enough to be worth a notice when it ends. */
    void passedThreshold() {
        if (running++ == 0) onWorkingChanged.accept(true);
    }

    /** A pane closed with a long command still running; it will never report a finish. */
    void abandoned() {
        release();
    }

    /**
     * A command ended. {@code buddyAvailable} picks the channel; {@code onActivate} focuses the pane
     * the command ran in, and is only ever invoked by the channel that delivered the notice.
     */
    void finished(String command, OptionalInt exitStatus, Duration ran, CommandNotice.Origin origin,
                  boolean buddyAvailable, Runnable onActivate) {
        if (ran.compareTo(threshold.get()) >= 0) release();
        if (!CommandNotice.shouldNotify(origin, ran, threshold.get())) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        (buddyAvailable ? buddy : operatingSystem).deliver(title(command), detail, succeeded, onActivate);
    }

    private void release() {
        if (running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** One line, newlines shown the way the History palette shows them, cut to a readable length. */
    static String title(String command) {
        String single = command.strip().replace("\r", "").replace("\n", " ↵ ");
        return single.length() > MAX_TITLE ? single.substring(0, MAX_TITLE - 1) + "…" : single;
    }

    /** "45s", "1m 12s", "2m", "2h 5m" — the way a person would say it, not ISO-8601. */
    static String humanize(Duration ran) {
        long seconds = Math.max(0, ran.toSeconds());
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) {
            long minutes = seconds / 60, rest = seconds % 60;
            return rest == 0 ? minutes + "m" : String.format(Locale.ROOT, "%dm %ds", minutes, rest);
        }
        long hours = seconds / 3600, minutes = (seconds % 3600) / 60;
        return minutes == 0 ? hours + "h" : String.format(Locale.ROOT, "%dh %dm", hours, minutes);
    }
}
