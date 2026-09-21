package dev.jasper.app.notifications;

import dev.jasper.buddy.view.BuddyCompanion;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

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
public final class CommandNotifier implements AutoCloseable {
    private final java.util.Set<Object> activeProducers = new java.util.HashSet<>();
    private boolean disposed;

    public void opened(Object key) { if (!disposed) activeProducers.add(Objects.requireNonNull(key)); }
    private boolean accepts(Object key) { return !disposed && activeProducers.contains(key); }
    /** Every notice this class posts is the terminal's; a transfer or an SSH session brings its own. */
    static final String SOURCE = "terminal";

    private Supplier<Duration> threshold;
    private BuddyCompanion deck;
    private BiConsumer<String, String> operatingSystem;
    private Consumer<Boolean> onWorkingChanged;
    private BiFunction<Duration, Runnable, Runnable> schedule;
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

    public CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck,
                    BiConsumer<String, String> operatingSystem, Consumer<Boolean> onWorkingChanged,
                    BiFunction<Duration, Runnable, Runnable> schedule) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.deck = Objects.requireNonNull(deck, "deck");
        this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
    }

    /**
     * A command began. Nothing is shown yet: after {@code threshold} it becomes a running card, so
     * the buddy does not twitch for every {@code ls}. {@code elapsedNanos} lets the card tick without
     * being re-posted every second.
     */
    public void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate, boolean watched) {
        if (!accepts(key)) return;
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
    public void hidden(Object key) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight != null && !flight.passed) promote(key, flight);
    }

    /** Turns an in-flight command into a bubble, once. */
    private void promote(Object key, InFlight flight) {
        if (!accepts(key)) return;
        if (inFlight.get(key) != flight || flight.passed) return;
        flight.passed = true;
        flight.cancel.run();
        flight.cancel = () -> {};
        if (running++ == 0) onWorkingChanged.accept(true);
        deck.post(new BuddyNotice(new BuddyNoticeId(SOURCE, key), BuddyNotice.Kind.TASK, flight.title, BuddyNotice.State.RUNNING, flight.detail, flight.activate));
    }

    /** Refresh wording without promoting the card, clearing acknowledgement, or replaying arrival. */
    public void titleChanged(Object key, String programTitle) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight == null) return; // Prompt titles cannot rename completed history.
        String updated = singleLine(programTitle);
        if (updated.isBlank() || updated.equals(flight.title)) return;
        flight.title = updated;
        if (flight.passed) deck.updateTitle(new BuddyNoticeId(SOURCE, key), updated);
    }

    /** A command ended. Its card becomes the outcome, and the OS hears about it if you were elsewhere. */
    public void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                  CommandNotice.Origin origin, Runnable activate) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        String title = flight == null ? title(command) : flight.title;
        cancel(key);
        Duration wait = threshold.get();
        if (wait.isZero() || ran.compareTo(wait) < 0) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran)
            : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        deck.post(new BuddyNotice(new BuddyNoticeId(SOURCE, key), BuddyNotice.Kind.TASK, title, succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
        // You were looking straight at it, so it is already seen and never reaches the column.
        if (origin.ownPaneFocused()) deck.acknowledge(new BuddyNoticeId(SOURCE, key));
        if (CommandNotice.shouldNotify(origin, ran, wait)) operatingSystem.accept(title, detail);
    }

    /** That pane took focus: whatever it posted has now been seen. */
    public void looked(Object key) {
        if (!accepts(key)) return;
        deck.acknowledge(new BuddyNoticeId(SOURCE, key));
    }

    /**
     * The pane is gone. Its card stays — the drawer is what you look at to remember — but stops
     * ticking and stops responding, because there is no longer anywhere for a click to go.
     */
    public void closed(Object key) {
        if (disposed || !activeProducers.remove(key)) return;
        InFlight flight = inFlight.get(key);
        boolean stillRunning = flight != null && flight.passed;
        Duration ran = flight == null ? Duration.ZERO : Duration.ofNanos(flight.elapsed.getAsLong());
        cancel(key);
        // Only a running card is rewritten. One that already finished keeps what it said - a pane
        // closed after a successful build must still say the build succeeded.
        if (stillRunning) deck.orphan(new BuddyNoticeId(SOURCE, key) , "Stopped after " + humanize(ran));
        else deck.orphan(new BuddyNoticeId(SOURCE, key));
        // Its pane is gone, so it can never be looked at and would sit in the column all session.
        deck.acknowledge(new BuddyNoticeId(SOURCE, key));
    }

    /** Drops any in-flight command for this key, releasing the typing animation if it had claimed it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        flight.cancel = () -> {}; flight.activate = () -> {}; flight.detail = () -> ""; flight.elapsed = () -> 0L;
        if (flight.passed && running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** Keep the full content; the tab and capsule renderers fit it to their own available width. */
    static String title(String command) {
        return singleLine(command.strip());
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ↵ ");
    }

    @Override public void close() {
        if (disposed) return;
        disposed = true;
        for (Object key : java.util.List.copyOf(inFlight.keySet())) cancel(key);
        activeProducers.clear();
        threshold = () -> Duration.ZERO; deck = null;
        operatingSystem = (title, detail) -> {}; onWorkingChanged = value -> {};
        schedule = (delay, action) -> () -> {};
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
