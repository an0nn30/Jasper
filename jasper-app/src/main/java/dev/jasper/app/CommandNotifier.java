package dev.jasper.app;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The terminal's producer for the buddy's drawer: it posts a running card once a command has taken
 * long enough to be worth remembering, replaces that card with the outcome, and sends the outcome to
 * the OS when the command did not finish under your hands.
 *
 * <p>Holds no Swing state. Every seam is a standard functional type rather than a new interface —
 * including {@code schedule}, which is Swing's {@code Timer} in the application and a list a test
 * runs by hand. The previous {@code Channel} interface is gone: the buddy being hidden no longer
 * changes where a notice goes, so it had one real implementation left.
 *
 * <p>EDT only.
 */
final class CommandNotifier {
    /** Every notice this class posts is the terminal's; a transfer or an SSH session brings its own. */
    static final String SOURCE = "terminal";

    /** A command title longer than this is cut; the pane it points at is unaffected. */
    private static final int MAX_TITLE = 60;

    private final Supplier<Duration> threshold;
    private final BuddyDeck deck;
    private final Runnable onDeckChanged;
    private final BiConsumer<String, String> operatingSystem;
    private final Consumer<Boolean> onWorkingChanged;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final Map<Object, InFlight> inFlight = new HashMap<>();
    /** Commands that have been running past the threshold. */
    private int running;

    /** A command between its start mark and its end, with the timer that will promote it to a card. */
    private static final class InFlight {
        Runnable cancel = () -> {};
        boolean passed;
    }

    CommandNotifier(Supplier<Duration> threshold, BuddyDeck deck, Runnable onDeckChanged,
                    BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
                    BiFunction<Duration, Runnable, Runnable> schedule) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.deck = Objects.requireNonNull(deck, "deck");
        this.onDeckChanged = Objects.requireNonNull(onDeckChanged, "onDeckChanged");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
    }

    /**
     * A command began. Nothing is shown yet: after {@code threshold} it becomes a running card, so
     * the buddy does not twitch for every {@code ls}. {@code elapsedNanos} lets the card tick without
     * being re-posted every second.
     */
    void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate) {
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        inFlight.put(key, flight);
        String title = title(command);
        flight.cancel = schedule.apply(wait, () -> {
            if (inFlight.get(key) != flight) return;
            flight.passed = true;
            flight.cancel = () -> {};
            if (running++ == 0) onWorkingChanged.accept(true);
            deck.post(new BuddyNotice(SOURCE, key, title, BuddyNotice.State.ACTIVE,
                () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong())), activate));
            onDeckChanged.run();
        });
    }

    /** A command ended. Its card becomes the outcome, and the OS hears about it if you were elsewhere. */
    void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                  CommandNotice.Origin origin, Runnable activate) {
        cancel(key);
        Duration wait = threshold.get();
        if (wait.isZero() || ran.compareTo(wait) < 0) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        String title = title(command);
        deck.post(new BuddyNotice(SOURCE, key, title,
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
        onDeckChanged.run();
        if (CommandNotice.shouldNotify(origin, ran, wait)) operatingSystem.accept(title, detail);
    }

    /**
     * The pane is gone. Its card stays — the drawer is what you look at to remember — but stops
     * ticking and stops responding, because there is no longer anywhere for a click to go.
     */
    void closed(Object key, Duration ran) {
        InFlight flight = inFlight.get(key);
        boolean hadCard = flight != null && flight.passed;
        cancel(key);
        deck.orphan(SOURCE, key, hadCard ? "Stopped after " + humanize(ran) : "Stopped");
        onDeckChanged.run();
    }

    /** Drops any in-flight command for this key, releasing the typing animation if it had claimed it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        if (flight.passed && running > 0 && --running == 0) onWorkingChanged.accept(false);
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
