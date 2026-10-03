package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Failures as incidents (spec sections 5 and 7): Jasper's own (the uncaught handler, a notification action), a
 * plugin's (a contained callback) and a shell that could not start. Each is captured on the thread that caught it,
 * recorded on the EDT and announced by one ERROR notification. A repeat updates that notification and shows its card
 * again at most once a minute. A shell launch failure is the exception: it is announced in its own window on every
 * occurrence, because each says where it happened. A failure of the notification system itself is left to the log,
 * and capture never throws or re-enters. The three failure entry points are safe from any thread; everything else
 * is EDT only.
 */
final class IncidentNotices implements AutoCloseable {
    static final NotificationGroup PLUGINS = new NotificationGroup("jasper.plugins", "Plugin problems", NotificationRequest.JASPER, DisplayMode.CARD);
    static final NotificationGroup ERRORS = new NotificationGroup("jasper.errors", "Internal errors", NotificationRequest.JASPER, DisplayMode.CARD);
    /** A repeating incident shows its card again at most this often. */
    static final Duration CARD_INTERVAL = Duration.ofMinutes(1);
    static final String SHOW_MORE = "Show more…";
    static final String DISABLE_PLUGIN = "Disable Plugin";
    /** What Jasper was doing when a shell could not start. */
    static final String LAUNCH = "starting a shell";
    static final String LAUNCH_TITLE = "Could not start the terminal";
    private static final System.Logger LOG = System.getLogger(IncidentNotices.class.getName());
    /** The notification system: a failure whose first Jasper frame is in one of these is only logged. */
    private static final List<String> OWN_PACKAGES = List.of("dev.jasper.app.notifications.", "dev.jasper.app.notificationui.");
    private static final List<String> OWN_CLASSES = List.of(IncidentNotices.class.getName(), NotificationsFeature.class.getName(),
        ErrorsWindow.class.getName(), ConfigProblemNotices.class.getName());

    private final NotificationCenter center;
    private final Incidents incidents;
    private final Executor ui;
    private final Function<String, String> pluginName;
    private final BiConsumer<String, Consumer<Optional<String>>> disable;
    private final Consumer<UUID> openErrors;
    private final ThreadLocal<Boolean> capturing = new ThreadLocal<>();
    /** Each incident's notification. */
    private final Map<UUID, UUID> notices = new HashMap<>();
    /** When each incident's card last showed. */
    private final Map<UUID, Instant> carded = new HashMap<>();
    /** Each incident's launch notifications: one per window and text, never repeated into. */
    private final Map<UUID, Set<UUID>> occurrences = new HashMap<>();
    private final Subscription centerChanges;
    private final Subscription incidentChanges;
    private volatile boolean closed;

    /**
     * @param ui runs recording on the EDT ({@code SwingUtilities::invokeLater} in the application)
     * @param pluginName a plugin's display name by id
     * @param disable disables a plugin from the next launch, then reports on the EDT: empty, or why it could not
     * @param openErrors opens the Errors window at an incident
     */
    IncidentNotices(NotificationCenter center, Incidents incidents, Executor ui, Function<String, String> pluginName,
                    BiConsumer<String, Consumer<Optional<String>>> disable, Consumer<UUID> openErrors) {
        this.center = Objects.requireNonNull(center, "center");
        this.incidents = Objects.requireNonNull(incidents, "incidents");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.disable = Objects.requireNonNull(disable, "disable");
        this.openErrors = Objects.requireNonNull(openErrors, "openErrors");
        center.register(PLUGINS);
        center.register(ERRORS);
        centerChanges = center.onChange(this::centerChanged);
        incidentChanges = incidents.onChange(this::incidentChanged);
    }

    /** A failure of Jasper's own; {@code operation} says what it was doing, such as "thread main". Any thread. */
    void jasperFailure(String operation, Throwable failure) {
        capture(NotificationRequest.JASPER, operation, failure, true, this::announcement);
    }

    /** A plugin callback failed while the host asked it to do {@code operation}. Any thread. */
    void pluginFailure(String pluginId, String operation, Throwable failure) {
        capture(pluginId, operation, failure, true, this::announcement);
    }

    /**
     * A shell could not start in {@code window} (none when null): an incident, and a Jasper notification in that window
     * whose content is {@code details}, the directory and the cause (spec section 7, producer 1). Every occurrence is
     * announced, never folded into an earlier one's notification, which belongs to another window and directory. A
     * failure that cannot be recorded (the notification system's own, or raised while an incident is announced) still
     * gets the plain notification, without Show more.... Any thread.
     */
    void launchFailure(UUID window, String details, Throwable failure) {
        if (closed) return;
        String text = details == null ? "" : details;
        if (capture(NotificationRequest.JASPER, LAUNCH, failure, false,
                incident -> launchRequest(window, text).withAction(showMore(incident)))) return;
        try { center.submit(launchRequest(window, text)); }
        catch (RuntimeException unannounced) {
            LOG.log(System.Logger.Level.WARNING, "A shell launch failure could not be announced", unannounced);
        }
    }

    private static NotificationRequest launchRequest(UUID window, String details) {
        NotificationRequest request = NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, LAUNCH_TITLE)
            .withContent(details);
        return window == null ? request : request.withTarget(window);
    }

    /** Disable Plugin on an incident: the plugin is disabled from the next launch and the outcome is posted. EDT. */
    void disablePlugin(String pluginId) {
        if (closed) return;
        String name = nameOf(pluginId);
        disable.accept(pluginId, problem -> center.submit(problem.isEmpty()
            ? NotificationRequest.of(PLUGINS.id(), Severity.INFO, name + " will be disabled when Jasper restarts")
            : NotificationRequest.of(PLUGINS.id(), Severity.ERROR, "Could not disable " + name).withContent(problem.get())));
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        centerChanges.close();
        incidentChanges.close();
    }

    /** "Remote failed to start", "Remote failed to stop", otherwise "Remote failed in panel remote.sftp". */
    static String pluginTitle(String name, String operation) {
        return switch (operation) {
            case "start" -> name + " failed to start";
            case "stop" -> name + " failed to stop";
            default -> name + " failed in " + operation;
        };
    }

    /**
     * Whether the notification system itself failed: the first {@code dev.jasper.} frame of the failure, or of any
     * cause, lies in it. Whoever caught such a failure has logged it; recording it could fail the same way again.
     */
    static boolean ownFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable next = failure; next != null && seen.add(next); next = next.getCause()) {
            for (StackTraceElement frame : next.getStackTrace()) {
                String type = frame.getClassName();
                if (!type.startsWith("dev.jasper.")) continue;
                if (own(type)) return true;
                break;
            }
        }
        return false;
    }

    private static boolean own(String type) {
        for (String name : OWN_PACKAGES) if (type.startsWith(name)) return true;
        for (String name : OWN_CLASSES) if (type.equals(name) || type.startsWith(name + "$")) return true;
        return false;
    }

    /**
     * Takes the capture here, on the thread that caught the failure, then records it on the EDT. Never throws.
     *
     * @param repeatable whether a repeat updates the incident's earlier notification (Jasper's and plugins' failures)
     *     or every occurrence posts its own
     * @return false when the failure was not handed over for recording, so the caller may announce it another way
     */
    private boolean capture(String source, String operation, Throwable failure, boolean repeatable,
                            Function<Incident, NotificationRequest> announce) {
        if (closed) return true;
        if (failure == null || Boolean.TRUE.equals(capturing.get())) return false;
        Capture capture;
        String thread;
        capturing.set(Boolean.TRUE);
        try {
            if (ownFailure(failure)) return false;
            capture = Capture.of(failure);
            thread = Thread.currentThread().getName();
        } catch (RuntimeException | Error unrecorded) {
            LOG.log(System.Logger.Level.WARNING, "A failure could not be recorded as an incident", unrecorded);
            return false;
        } finally {
            capturing.remove();
        }
        try { ui.execute(() -> record(source, operation, thread, capture, repeatable, announce)); }
        catch (RuntimeException rejected) {
            LOG.log(System.Logger.Level.WARNING, "A failure could not be recorded as an incident", rejected);
            return false;
        }
        return true;
    }

    private void record(String source, String operation, String thread, Capture capture, boolean repeatable,
                        Function<Incident, NotificationRequest> announce) {
        if (closed || Boolean.TRUE.equals(capturing.get())) return;
        capturing.set(Boolean.TRUE);
        try {
            Incident incident = incidents.record(source, operation, thread, capture);
            if (repeatable) announceRepeatable(incident, announce);
            else center.post(announce.apply(incident)).ifPresent(id ->
                occurrences.computeIfAbsent(incident.id(), key -> new LinkedHashSet<>()).add(id));
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "An incident could not be announced", failure);
        } finally {
            capturing.remove();
        }
    }

    /** One notification for the incident: a repeat updates it, with its card at most once a minute. */
    private void announceRepeatable(Incident incident, Function<Incident, NotificationRequest> announce) {
        Instant now = center.clock().instant();
        UUID notice = notices.get(incident.id());
        if (notice != null) {
            Instant shown = carded.get(incident.id());
            boolean card = shown == null || !now.isBefore(shown.plus(CARD_INTERVAL));
            if (center.repeat(notice, card)) {
                if (card) carded.put(incident.id(), now);
                return;
            }
        }
        center.post(announce.apply(incident)).ifPresent(id -> {
            notices.put(incident.id(), id);
            carded.put(incident.id(), now);
        });
    }

    /** An internal error, or a plugin problem with Disable Plugin. */
    private NotificationRequest announcement(Incident incident) {
        if (!incident.fromPlugin())
            return NotificationRequest.of(ERRORS.id(), Severity.ERROR, "Unexpected error in Jasper")
                .withContent(incident.summary()).withAction(showMore(incident));
        String plugin = incident.source();
        return NotificationRequest.of(PLUGINS.id(), Severity.ERROR, pluginTitle(nameOf(plugin), incident.operation()))
            .withContent(incident.summary())
            .withAction(showMore(incident))
            .withAction(new NotificationAction(DISABLE_PLUGIN, () -> disablePlugin(plugin), false));
    }

    private NotificationAction showMore(Incident incident) {
        UUID id = incident.id();
        return new NotificationAction(SHOW_MORE, () -> openErrors.accept(id), false);
    }

    private String nameOf(String pluginId) {
        return Objects.requireNonNullElse(pluginName.apply(pluginId), pluginId);
    }

    /** The user removed an incident's notification, or cleared them all: a repeat posts a new one. */
    private void centerChanged(NotificationCenter.Change change) {
        if (change instanceof NotificationCenter.Change.Removed removed) {
            for (Iterator<Map.Entry<UUID, UUID>> links = notices.entrySet().iterator(); links.hasNext(); ) {
                Map.Entry<UUID, UUID> link = links.next();
                if (!link.getValue().equals(removed.id())) continue;
                carded.remove(link.getKey());
                links.remove();
            }
            for (Iterator<Set<UUID>> launches = occurrences.values().iterator(); launches.hasNext(); ) {
                Set<UUID> launched = launches.next();
                launched.remove(removed.id());
                if (launched.isEmpty()) launches.remove();
            }
        } else if (change instanceof NotificationCenter.Change.Cleared) {
            notices.clear();
            carded.clear();
            occurrences.clear();
        }
    }

    /** An incident was cleared or dropped: its notifications expire, so their links stop working. */
    private void incidentChanged(Incidents.Change change) {
        if (change instanceof Incidents.Change.Removed removed) {
            carded.remove(removed.id());
            UUID notice = notices.remove(removed.id());
            if (notice != null) center.expire(notice);
            Set<UUID> launched = occurrences.remove(removed.id());
            if (launched != null) List.copyOf(launched).forEach(center::expire);
        } else if (change instanceof Incidents.Change.Cleared) {
            List<UUID> expiring = new ArrayList<>(notices.values());
            occurrences.values().forEach(expiring::addAll);
            notices.clear();
            carded.clear();
            occurrences.clear();
            expiring.forEach(center::expire);
        }
    }
}
