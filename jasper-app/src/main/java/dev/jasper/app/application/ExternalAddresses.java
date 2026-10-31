package dev.jasper.app.application;

import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Web addresses from outside Jasper, such as {@code jasper <url>}, and those that cannot open while Jasper is the
 * system's default browser (browser plan 4c). They wait until {@link #ready}: the plugins run and the first answer to
 * whether Jasper is the default has come. None goes to the system browser when that would hand it back to Jasper: one
 * that comes back within {@link #COMES_BACK_WITHIN} of Jasper handing it to the system is {@link #cannotOpen}'s instead
 * (ruling Q10), and with no browser provided "Web links can’t open in Jasper" offers Turn On Browser and Copy Link. EDT,
 * except {@link #handedToSystem}, any thread.
 */
final class ExternalAddresses {
    private static final System.Logger LOG = System.getLogger(ExternalAddresses.class.getName());
    /** At most this many addresses wait; later ones are dropped. */
    static final int WAITING_AT_MOST = 32;
    /** An address handed to the system browser that comes back from outside this soon was handed back by the system. */
    static final Duration COMES_BACK_WITHIN = Duration.ofSeconds(10);
    /** The Browser plugin, which Turn On Browser turns on. */
    static final String BROWSER_PLUGIN = "dev.jasper.browser";
    private static final String QUITTING = "Jasper is quitting; run the command again";

    /** Hands an address from outside to the browser, with what takes one it does not open. */
    @FunctionalInterface
    interface Browser {
        /**
         * @param address an absolute http or https address
         * @param window the Jasper window it came from, or empty
         * @param notTaken opens one the browser did not take
         */
        void open(URI address, Optional<UUID> window, Consumer<URI> notTaken);
    }

    /**
     * What the addresses need of the application.
     *
     * @param browser {@code PluginRuntime.openExternalLink}
     * @param systemBrowser opens an address the browser did not take in the system browser
     * @param providesBrowser whether a plugin provides the web browser now
     * @param turnOnBrowser turns the Browser plugin on from the next launch; it hears, on the EDT, nothing, or why not
     * @param restart restarts Jasper as it was started; false when it cannot work out how
     * @param copy puts text on the clipboard; it throws when the clipboard refuses
     * @param quitting whether Jasper is quitting
     */
    record Hooks(Browser browser, Consumer<URI> systemBrowser, BooleanSupplier providesBrowser,
                 Consumer<Consumer<Optional<String>>> turnOnBrowser, BooleanSupplier restart, Consumer<String> copy,
                 BooleanSupplier quitting) {
        Hooks {
            Objects.requireNonNull(browser, "browser");
            Objects.requireNonNull(systemBrowser, "systemBrowser");
            Objects.requireNonNull(providesBrowser, "providesBrowser");
            Objects.requireNonNull(turnOnBrowser, "turnOnBrowser");
            Objects.requireNonNull(restart, "restart");
            Objects.requireNonNull(copy, "copy");
            Objects.requireNonNull(quitting, "quitting");
        }
    }

    /** An address that waits, the window it came from, whether it came back from the system, and what its call waits for. */
    private record Waiting(URI address, Optional<UUID> window, boolean cameBack, CompletableFuture<Void> handedOn) { }

    private final NotificationCenter center;
    private final Clock clock;
    private final Hooks hooks;
    private final Queue<Waiting> waiting = new ArrayDeque<>();
    /** When Jasper last handed each address to the system browser, within {@link #COMES_BACK_WITHIN}. Any thread. */
    private final Map<URI, Instant> handed = new ConcurrentHashMap<>();
    private boolean ready;

    /**
     * @param center where the notices go, its {@code NotificationsFeature.GENERAL} group registered
     * @param clock what tells whether an address came back within {@link #COMES_BACK_WITHIN}
     * @param hooks what the addresses need of the application
     */
    ExternalAddresses(NotificationCenter center, Clock clock, Hooks hooks) {
        this.center = Objects.requireNonNull(center, "center");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    /**
     * Opens an address from outside in the browser, or, before {@link #ready}, keeps it waiting in arrival order. One
     * the browser does not take goes to the system browser, unless it came back from there ({@link #cannotOpen}).
     *
     * @param address an absolute http or https address
     * @param window the Jasper window it came from, or empty
     * @return empty when {@link #WAITING_AT_MOST} wait already and it is dropped; otherwise completes once it has been
     *     handed on, and fails when Jasper quits first
     */
    Optional<CompletableFuture<Void>> open(URI address, Optional<UUID> window) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(window, "window");
        boolean cameBack = cameBack(address);
        if (ready) {
            handOn(address, window, cameBack);
            return Optional.of(CompletableFuture.completedFuture(null));
        }
        if (waiting.size() >= WAITING_AT_MOST) {
            // The address may be program output: the log does not repeat it.
            LOG.log(System.Logger.Level.WARNING, "A web address from outside was dropped: " + WAITING_AT_MOST
                + " were already waiting for Jasper's browser");
            return Optional.empty();
        }
        var handedOn = new CompletableFuture<Void>();
        waiting.add(new Waiting(address, window, cameBack, handedOn));
        return Optional.of(handedOn);
    }

    /** The browser is ready: the waiting addresses open in arrival order, and later ones at once; none while quitting. */
    void ready() {
        if (ready) return;
        ready = true;
        for (Waiting next = waiting.poll(); next != null; next = waiting.poll()) {
            if (hooks.quitting().getAsBoolean()) {
                next.handedOn().completeExceptionally(new IllegalStateException(QUITTING));
                continue;
            }
            try {
                handOn(next.address(), next.window(), next.cameBack());
                next.handedOn().complete(null);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "A web address from outside could not open", failure);
                next.handedOn().completeExceptionally(failure);
            }
        }
    }

    /** Jasper quits: the waiting addresses open nothing, and their calls hear so. */
    void close() {
        for (Waiting next = waiting.poll(); next != null; next = waiting.poll())
            next.handedOn().completeExceptionally(new IllegalStateException(QUITTING));
    }

    /**
     * Jasper handed {@code address} to the system browser; if the system hands it back within
     * {@link #COMES_BACK_WITHIN}, it does not go there again. Any thread.
     */
    void handedToSystem(URI address) {
        Instant now = clock.instant();
        handed.values().removeIf(at -> now.isAfter(at.plus(COMES_BACK_WITHIN)));
        handed.put(address.normalize(), now);
    }

    private boolean cameBack(URI address) {
        Instant at = handed.get(address.normalize());
        return at != null && !clock.instant().isAfter(at.plus(COMES_BACK_WITHIN));
    }

    private void handOn(URI address, Optional<UUID> window, boolean cameBack) {
        hooks.browser().open(address, window, cameBack ? this::cannotOpen : hooks.systemBrowser());
    }

    /**
     * A web address that no browser of Jasper's took, which the system browser would hand back to Jasper (rulings Q1 and
     * Q2). With no browser provided (the Browser plugin off or not installed), "Web links can’t open in Jasper" offers
     * Turn On Browser and Copy Link. A provided browser that declined it, as the Browser does once Chromium could not
     * start, has had it and says why itself, so nothing opens and it is not offered again. Nothing once Jasper quits.
     *
     * @param address an absolute http or https address
     */
    void cannotOpen(URI address) {
        if (hooks.quitting().getAsBoolean()) return;
        if (hooks.providesBrowser().getAsBoolean()) {
            LOG.log(System.Logger.Level.INFO, "Jasper's browser did not open a web address, and the system would hand it back, so nothing opens");
            return;
        }
        center.post(notice(Severity.WARNING, "Web links can’t open in Jasper")
            .withContent("The Browser plugin is off. Turn it on to open web links in Jasper, or choose another default browser "
                + "in your system settings.")
            .withAction(NotificationAction.of("Turn On Browser", this::turnOnBrowser))
            .withAction(new NotificationAction("Copy Link", () -> copy(address), false)));
    }

    /** Turn On Browser: the Browser plugin runs from the next launch, and the restart that starts it is offered. */
    private void turnOnBrowser() {
        hooks.turnOnBrowser().accept(problem -> {
            if (hooks.quitting().getAsBoolean()) return;
            if (problem.isPresent()) {
                center.post(notice(Severity.ERROR, "Could not turn on the Browser").withContent(problem.get()));
                return;
            }
            center.post(notice(Severity.INFO, "Restart Jasper to turn on the Browser")
                .withContent("The Browser plugin starts the next time Jasper starts.")
                .withAction(NotificationAction.of("Restart Now", this::restart)));
        });
    }

    /** The restart offer's Restart Now. */
    private void restart() {
        if (!hooks.restart().getAsBoolean())
            center.post(notice(Severity.ERROR, "Jasper could not restart")
                .withContent("Jasper could not work out how it was started. Quit and reopen it to turn on the Browser."));
    }

    /** Copy Link: the address on the clipboard. */
    private void copy(URI address) {
        try {
            hooks.copy().accept(address.toString());
        } catch (RuntimeException failure) {
            center.post(notice(Severity.ERROR, "Could not copy the link").withContent(failure.getMessage() == null ? "" : failure.getMessage()));
        }
    }

    private static NotificationRequest notice(Severity severity, String title) {
        return NotificationRequest.of(NotificationsFeature.GENERAL.id(), severity, title);
    }
}
