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
        /** How long it has been going, so a pane closed mid-command can freeze the real figure. */
        LongSupplier elapsed = () -> 0L;
        String title = "";
        java.util.function.Supplier<String> detail = () -> "";
        Runnable activate = () -> {};
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
    void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate, boolean watched) {
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        flight.elapsed = elapsedNanos;
        flight.title = title(command);
        flight.detail = () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong()));
        flight.activate = activate;
        inFlight.put(key, flight);
        // Out of sight is the whole reason a bubble exists, so there is nothing to wait for. The
        // threshold stays as the fallback for a command running in the pane you are still watching.
        if (!watched) { promote(key, flight); return; }
        flight.cancel = schedule.apply(wait, () -> promote(key, flight));
    }

    /** The pane stopped being watched: anything running in it is now worth showing. */
    void hidden(Object key) {
        InFlight flight = inFlight.get(key);
        if (flight != null && !flight.passed) promote(key, flight);
    }

    /** Turns an in-flight command into a bubble, once. */
    private void promote(Object key, InFlight flight) {
        if (inFlight.get(key) != flight || flight.passed) return;
        flight.passed = true;
        flight.cancel.run();
        flight.cancel = () -> {};
        if (running++ == 0) onWorkingChanged.accept(true);
        deck.post(new BuddyNotice(SOURCE, key, BuddyNotice.Kind.TASK, flight.title,
            BuddyNotice.State.RUNNING, flight.detail, flight.activate));
        onDeckChanged.run();
    }

    /** Refresh wording without promoting the card, clearing acknowledgement, or replaying arrival. */
    void titleChanged(Object key, String programTitle) {
        InFlight flight = inFlight.get(key);
        if (flight == null) return; // Prompt titles cannot rename completed history.
        String updated = TerminalTitle.singleLine(programTitle);
        if (updated.isBlank() || updated.equals(flight.title)) return;
        flight.title = updated;
        if (flight.passed && deck.updateTitle(SOURCE, key, updated)) onDeckChanged.run();
    }

    /** A command ended. Its card becomes the outcome, and the OS hears about it if you were elsewhere. */
    void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                  CommandNotice.Origin origin, Runnable activate) {
        InFlight flight = inFlight.get(key);
        String title = flight == null ? title(command) : flight.title;
        cancel(key);
        Duration wait = threshold.get();
        if (wait.isZero() || ran.compareTo(wait) < 0) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        deck.post(new BuddyNotice(SOURCE, key, BuddyNotice.Kind.TASK, title,
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
        // You were looking straight at it, so it is already seen and never reaches the column.
        if (origin.ownPaneFocused()) deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
        if (CommandNotice.shouldNotify(origin, ran, wait)) operatingSystem.accept(title, detail);
    }

    /** That pane took focus: whatever it posted has now been seen. */
    void looked(Object key) {
        deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
    }

    /**
     * The pane is gone. Its card stays — the drawer is what you look at to remember — but stops
     * ticking and stops responding, because there is no longer anywhere for a click to go.
     */
    void closed(Object key) {
        InFlight flight = inFlight.get(key);
        boolean stillRunning = flight != null && flight.passed;
        Duration ran = flight == null ? Duration.ZERO : Duration.ofNanos(flight.elapsed.getAsLong());
        cancel(key);
        // Only a running card is rewritten. One that already finished keeps what it said - a pane
        // closed after a successful build must still say the build succeeded.
        if (stillRunning) deck.orphan(SOURCE, key, "Stopped after " + humanize(ran));
        else deck.orphan(SOURCE, key);
        // Its pane is gone, so it can never be looked at and would sit in the column all session.
        deck.acknowledge(SOURCE, key);
        onDeckChanged.run();
    }

    /** Drops any in-flight command for this key, releasing the typing animation if it had claimed it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        if (flight.passed && running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** Keep the full content; the tab and capsule renderers fit it to their own available width. */
    static String title(String command) {
        return TerminalTitle.singleLine(command.strip());
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
