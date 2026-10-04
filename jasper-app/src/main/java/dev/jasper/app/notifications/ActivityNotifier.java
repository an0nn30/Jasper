package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Buddy's producer for what something other than a terminal pane volunteers: plugin activities and plugin connections,
 * each keyed by its source and id. An activity is one live task card from its start until it ends, however it ends; its
 * outcome is a notification the plugin host posts (spec section 7, producer 4), not a card. A connection is one
 * connection card: a new state posts it again, which is new news, and closing the connection removes it. Progress, and a
 * report that keeps a connection's state, only change the detail the card already reads, so nothing is re-posted. A
 * click raises the application, then runs the producer's action when it named one. This producer never drives Buddy's
 * working animation, which the command notifier owns. EDT only; holds no Swing state.
 */
public final class ActivityNotifier implements AutoCloseable {
    /** What a connection card can say. */
    public enum ConnectionState {
        /** Available. */
        UP,
        /** Available with a problem, or recovering. */
        DEGRADED,
        /** Unavailable. */
        DOWN
    }

    private final Map<BuddyNoticeId, String> details = new HashMap<>();
    private final Map<BuddyNoticeId, ConnectionState> connections = new HashMap<>();
    private BuddyCompanion deck;
    private Runnable activate;

    /**
     * Binds the producer to the companion.
     *
     * @param deck the companion that shows the cards
     * @param activate what every click on a card does first; a producer has no pane to focus, so the application
     *                 raises itself
     */
    public ActivityNotifier(BuddyCompanion deck, Runnable activate) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.activate = Objects.requireNonNull(activate, "activate");
    }

    /**
     * Posts a running card whose click only raises the application.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     */
    public void started(String source, UUID id, String title, String detail) {
        started(source, id, title, detail, Optional.empty());
    }

    /**
     * Posts a running card.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     * @param action run after the application is raised when the card is clicked; empty to only raise the application
     */
    public void started(String source, UUID id, String title, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title), BuddyNotice.State.RUNNING,
            () -> details.getOrDefault(key, ""), activation(action)));
    }

    /**
     * Changes a running card's detail; ignored when no such card is running.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param detail the new detail line
     */
    public void progress(String source, UUID id, String detail) {
        if (deck == null) return;
        details.computeIfPresent(new BuddyNoticeId(source, id), (key, previous) -> detail);
    }

    /**
     * Removes the card of work that ended, however it ended; its outcome, if any, is a notification.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     */
    public void ended(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.dismiss(key);
    }

    /**
     * Shows a connection. A new state posts the card again, which is new news; the same state only changes the detail
     * the card reads.
     *
     * @param source the producer namespace, a plugin id
     * @param id the connection's identity within that source
     * @param title display title; line breaks become spaces
     * @param state what the card says
     * @param detail detail line
     * @param action run after the application is raised when the card is clicked; empty to only raise the application
     */
    public void connection(String source, UUID id, String title, ConnectionState state, String detail, Optional<Runnable> action) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        if (connections.put(key, Objects.requireNonNull(state, "state")) == state) return;
        BuddyNotice.State shown = switch (state) {
            case UP -> BuddyNotice.State.UP;
            case DEGRADED -> BuddyNotice.State.DEGRADED;
            case DOWN -> BuddyNotice.State.DOWN;
        };
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.CONNECTION, singleLine(title), shown,
            () -> details.getOrDefault(key, ""), activation(action)));
    }

    /**
     * Removes a connection's card: its producer no longer reports it.
     *
     * @param source the producer namespace
     * @param id the connection's identity
     */
    public void connectionClosed(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        connections.remove(key);
        deck.dismiss(key);
    }

    /** Raises the application, then runs the producer's action when it named one. */
    private Runnable activation(Optional<Runnable> action) {
        Objects.requireNonNull(action, "action");
        return () -> {
            if (deck == null) return;
            activate.run();
            action.ifPresent(Runnable::run);
        };
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ");
    }

    @Override public void close() {
        deck = null;
        activate = () -> { };
        details.clear();
        connections.clear();
    }
}
