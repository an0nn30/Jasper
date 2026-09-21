package dev.jasper.app.notifications;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyCompanion;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Producer for work that something other than a terminal pane volunteers: today, plugin activities.
 * Each activity is one task card keyed by its source and id. A card appears when the activity starts;
 * unlike a shell command it was declared worth showing, so there is no threshold. Progress changes the
 * detail the running card already reads, so nothing is re-posted. This producer never drives Buddy's
 * working animation, which the command notifier owns. EDT only; holds no Swing state.
 */
public final class ActivityNotifier implements AutoCloseable {
    private final Map<BuddyNoticeId, String> details = new HashMap<>();
    private BuddyCompanion deck;
    private Runnable activate;

    /**
     * Binds the producer to the companion.
     *
     * @param deck the companion that shows the cards
     * @param activate what a click on a card does; an activity has no pane to focus, so the application raises itself
     */
    public ActivityNotifier(BuddyCompanion deck, Runnable activate) {
        this.deck = Objects.requireNonNull(deck, "deck");
        this.activate = Objects.requireNonNull(activate, "activate");
    }

    /**
     * Posts a running card.
     *
     * @param source the producer namespace, a plugin id
     * @param id the activity's identity within that source
     * @param title display title; line breaks become spaces
     * @param detail initial detail line
     */
    public void started(String source, UUID id, String title, String detail) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.put(key, detail);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title), BuddyNotice.State.RUNNING,
            () -> details.getOrDefault(key, ""), activate));
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
     * Replaces the card with its outcome.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     * @param title display title
     * @param succeeded whether the work succeeded
     * @param detail final detail line
     */
    public void finished(String source, UUID id, String title, boolean succeeded, String detail) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.post(new BuddyNotice(key, BuddyNotice.Kind.TASK, singleLine(title),
            succeeded ? BuddyNotice.State.DONE : BuddyNotice.State.FAILED, () -> detail, activate));
    }

    /**
     * Removes the card of work the user cancelled: there is no outcome worth remembering.
     *
     * @param source the producer namespace
     * @param id the activity's identity
     */
    public void cancelled(String source, UUID id) {
        if (deck == null) return;
        var key = new BuddyNoticeId(source, id);
        details.remove(key);
        deck.dismiss(key);
    }

    private static String singleLine(String title) {
        return title.replace("\r", "").replace("\n", " ");
    }

    @Override public void close() {
        deck = null;
        activate = () -> { };
        details.clear();
    }
}
