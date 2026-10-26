package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The terminal's producer for long commands (spec section 7, producer 3). A command that runs long enough, or runs out
 * of sight, qualifies as live work and starts Buddy typing; its card shows while its parent tab is out of sight.
 * When it ends the card goes. If it ran at least the threshold
 * and did not finish in the pane you were typing in, its outcome is posted in {@link #GROUP}: the command as the
 * title, "Finished in 2m 03s" (INFO) or "Exited 1 · 2m 03s" (WARNING) as the content, a click that focuses the pane, and
 * a card in the pane's window. A pane closing expires its notifications, since there is no pane left to focus.
 *
 * <p>While Jasper is in the background the center's delivery also has the operating system show the outcome (spec
 * section 7); this class does not talk to it.
 *
 * <p>Holds no Swing state. Every seam is a standard functional type, including {@code schedule}, which is Swing's
 * {@code Timer} in the application and a list a test runs by hand. EDT only.
 */
public final class CommandNotifier implements AutoCloseable {
    /** Where long commands' outcomes go. */
    public static final NotificationGroup GROUP = new NotificationGroup("jasper.commands", "Long-running commands",
        NotificationRequest.JASPER, DisplayMode.CARD);
    /** Every card this class posts is the terminal's; a transfer or an SSH session brings its own. */
    static final String SOURCE = "terminal";

    private final Set<Object> activeProducers = new HashSet<>();
    private final Map<Object, InFlight> inFlight = new HashMap<>();
    private final Map<Object, Context> contexts = new HashMap<>();
    private record Context(BuddyNotice.Group group, String title, boolean watched, Runnable activate) {}
    /** The notifications each pane's commands posted, expired when the pane closes. */
    private final Map<Object, Set<UUID>> outcomes = new HashMap<>();
    private boolean disposed;
    private Supplier<Duration> threshold;
    private BuddyCompanion deck;
    private NotificationCenter center;
    private Consumer<Boolean> onWorkingChanged;
    private BiFunction<Duration, Runnable, Runnable> schedule;
    /** Commands that have been running past the threshold. */
    private int running;

    /** A command between its start mark and its end, with the timer that will promote it to a card. */
    private static final class InFlight {
        Runnable cancel = () -> {};
        boolean passed;
        boolean watched;
        BuddyNotice.Group group;
        String title = "";
        Supplier<String> detail = () -> "";
        Runnable activate = () -> {};
    }

    /**
     * EDT: registers {@link #GROUP} with the center.
     *
     * @param threshold how long a command must run to be worth telling; zero turns the feature off
     * @param deck shows running commands
     * @param center receives the outcomes
     * @param onWorkingChanged hears whether any long command is running, for Buddy's typing
     * @param schedule runs a task after a delay and returns the way to cancel it
     */
    public CommandNotifier(Supplier<Duration> threshold, BuddyCompanion deck, NotificationCenter center,
                           Consumer<Boolean> onWorkingChanged, BiFunction<Duration, Runnable, Runnable> schedule) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.deck = Objects.requireNonNull(deck, "deck");
        this.center = Objects.requireNonNull(center, "center");
        this.onWorkingChanged = Objects.requireNonNull(onWorkingChanged, "onWorkingChanged");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        center.register(GROUP);
    }

    /** A pane opened: events for it count from now. */
    public void opened(Object key) { if (!disposed) activeProducers.add(Objects.requireNonNull(key)); }

    private boolean accepts(Object key) { return !disposed && activeProducers.contains(key); }

    /**
     * A command began. Watched work qualifies after {@code threshold}; hidden work qualifies immediately.
     * Qualified work counts for typing even while suppressed. {@code elapsedNanos} lets the card tick without being
     * re-posted every second.
     */
    public void started(Object key, String command, LongSupplier elapsedNanos, Runnable activate, boolean watched) {
        if (!accepts(key)) return;
        Duration wait = threshold.get();
        cancel(key);
        if (wait.isZero()) return;
        InFlight flight = new InFlight();
        Context context = contexts.get(key);
        flight.title = context == null ? title(command) : context.title();
        flight.watched = context == null ? watched : context.watched();
        flight.group = context == null ? null : context.group();
        flight.detail = () -> "Running · " + humanize(Duration.ofNanos(elapsedNanos.getAsLong()));
        flight.activate = context == null ? activate : context.activate();
        inFlight.put(key, flight);
        // Out of sight is the whole reason a card exists, so there is nothing to wait for. The threshold stays as the
        // fallback for a command running in the parent tab you are still watching.
        if (!flight.watched) { promote(key, flight); return; }
        flight.cancel = schedule.apply(wait, () -> promote(key, flight));
    }

    /** The pane stopped being watched: anything running in it is now worth showing. */
    public void hidden(Object key) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight == null) return;
        flight.watched = false;
        if (!flight.passed) promote(key, flight);
        else deck.updateContext(new BuddyNoticeId(SOURCE, key), flight.group, true);
    }

    /** Current workspace presentation, retained even when the pane is idle. */
    public void presentation(Object key, Object groupKey, String groupTitle, String paneTitle,
                             boolean watched, Runnable activate) {
        if (!accepts(key)) return;
        var context = new Context(new BuddyNotice.Group(new BuddyNoticeId("terminal-tab", groupKey), title(groupTitle)),
            title(paneTitle), watched, Objects.requireNonNull(activate));
        contexts.put(key, context);
        InFlight flight = inFlight.get(key);
        if (flight == null) return;
        boolean titleChanged = !flight.title.equals(context.title());
        boolean contextChanged = !Objects.equals(flight.group, context.group()) || flight.watched != watched;
        flight.title = context.title();
        flight.activate = context.activate();
        flight.group = context.group();
        flight.watched = watched;
        if (!flight.passed) {
            if (!watched) promote(key, flight);
            return;
        }
        var id = new BuddyNoticeId(SOURCE, key);
        if (titleChanged) deck.updateTitle(id, flight.title);
        if (contextChanged) deck.updateContext(id, flight.group, !watched);
    }

    /** Qualifies an in-flight command once, retaining it even while its card is suppressed. */
    private void promote(Object key, InFlight flight) {
        if (!accepts(key)) return;
        if (inFlight.get(key) != flight || flight.passed) return;
        flight.passed = true;
        flight.cancel.run();
        flight.cancel = () -> {};
        if (running++ == 0) onWorkingChanged.accept(true);
        deck.post(new BuddyNotice(new BuddyNoticeId(SOURCE, key), BuddyNotice.Kind.TASK, flight.title,
            BuddyNotice.State.RUNNING, flight.detail, () -> flight.activate.run(), flight.group), !flight.watched);
    }

    /** Refreshes a running card's wording without promoting it or replaying its arrival. */
    public void titleChanged(Object key, String programTitle) {
        if (!accepts(key)) return;
        InFlight flight = inFlight.get(key);
        if (flight == null) return; // Nothing is running, so a prompt title has nothing to rename.
        String updated = singleLine(programTitle);
        if (updated.isBlank() || updated.equals(flight.title)) return;
        flight.title = updated;
        if (flight.passed) deck.updateTitle(new BuddyNoticeId(SOURCE, key), updated);
    }

    /**
     * A command ended. Its running card goes; the outcome is posted unless it was short or finished in the pane you were
     * typing in.
     *
     * @param window the pane's window, where the card shows; null for the window used last
     * @param activate focuses the pane
     */
    public void finished(Object key, String command, OptionalInt exitStatus, Duration ran,
                         CommandNotice.Origin origin, UUID window, Runnable activate) {
        if (!accepts(key)) return;
        // Shell and program titles can name the working directory (for example "~"); completion carries the command
        // that actually ran, arguments included.
        String title = title(command);
        cancel(key);
        if (!CommandNotice.shouldNotify(origin, ran, threshold.get())) return;
        boolean succeeded = exitStatus.isEmpty() || exitStatus.getAsInt() == 0;
        String detail = succeeded ? "Finished in " + humanize(ran) : "Exited " + exitStatus.getAsInt() + " · " + humanize(ran);
        var request = NotificationRequest.of(GROUP.id(), succeeded ? Severity.INFO : Severity.WARNING, title)
            .withContent(detail).withActivate(Objects.requireNonNull(activate, "activate"));
        if (window != null) request = request.withTarget(window);
        center.post(request).ifPresent(id -> {
            // A repeat moves the entry to this pane, so only the pane it last came from may expire it.
            outcomes.values().forEach(ids -> ids.remove(id));
            outcomes.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
        });
    }

    /**
     * Who posted a notification this notifier still answers for: the key of the pane whose command it reports, until that
     * pane closes. A click resolves the pane's window from it then, since its tab may have moved (workspace tabs spec 9.3).
     *
     * @param notification a notification's id
     * @return the pane's key; empty for any other notification
     */
    public Optional<Object> producer(UUID notification) {
        for (var entry : outcomes.entrySet()) if (entry.getValue().contains(notification)) return Optional.of(entry.getKey());
        return Optional.empty();
    }

    /** The pane is gone: its running card goes, and its notifications expire, since there is no pane to focus. */
    public void closed(Object key) {
        if (disposed || !activeProducers.remove(key)) return;
        cancel(key);
        contexts.remove(key);
        Set<UUID> posted = outcomes.remove(key);
        if (posted != null) for (UUID id : posted) center.expire(id);
    }

    /** Drops any in-flight command for this key: its card goes and it releases the typing animation if it held it. */
    private void cancel(Object key) {
        InFlight flight = inFlight.remove(key);
        if (flight == null) return;
        flight.cancel.run();
        flight.cancel = () -> {};
        flight.activate = () -> {};
        flight.detail = () -> "";
        if (!flight.passed) return;
        deck.dismiss(new BuddyNoticeId(SOURCE, key));
        if (running > 0 && --running == 0) onWorkingChanged.accept(false);
    }

    /** One line; the card and notification renderers fit it to their width. A command showing nothing is "Command". */
    static String title(String command) {
        String line = singleLine(command.strip());
        return NotificationText.visible(line) ? line : "Command";
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ↵ ");
    }

    @Override public void close() {
        if (disposed) return;
        for (Object key : List.copyOf(inFlight.keySet())) cancel(key);
        disposed = true;
        activeProducers.clear();
        contexts.clear();
        outcomes.clear();
        threshold = () -> Duration.ZERO;
        deck = null;
        center = null;
        onWorkingChanged = value -> {};
        schedule = (delay, action) -> () -> {};
    }

    /** "45s", "1m 12s", "2m", "2h 5m": the way a person would say it, not ISO-8601. */
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
