package dev.jasper.buddy.view;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.internal.presentation.BuddyWindow;

/**
 * EDT-confined companion facade over a bounded notice model and lazy native presentation.
 * Construction acquires no window, timer or worker. Hiding preserves notices; closing releases
 * presentation resources and all retained notice/host callbacks. Calls after close are inert,
 * except that every call still enforces EDT confinement.
 * <p>All operations throw {@link IllegalStateException} off the EDT. Active mutations reject
 * null required values; titles must be nonblank. Unknown valid IDs are harmless no-ops.
 * The host owns producer cancellation, visibility policy, persistence and resolved fonts.
 */
public final class BuddyCompanion implements AutoCloseable {
    private final BuddyDeck deck;
    private BuddyOptions options;
    private BuddyWindow window;
    private boolean closed, working;
    /** Creates a model-only companion. Options must be nonnull; call on EDT before registering producers. */
    public BuddyCompanion(BuddyOptions options) { this(options, new BuddyDeck()); }
    BuddyCompanion(BuddyOptions options, BuddyDeck deck) {
        requireEdt();
        this.options = java.util.Objects.requireNonNull(options, "options");
        this.deck = java.util.Objects.requireNonNull(deck, "deck");
    }
    private static void requireEdt() {
        if (!javax.swing.SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Buddy operations require the EDT");
    }
    private void refresh() { if (window != null) window.refreshDeck(); }
    /** Posts or replaces a nonnull validated notice and promotes it to newest. The oldest notice is dropped beyond 50. */
    public void post(BuddyNotice notice) {
        post(notice, true);
    }
    /** Retains a notice, promotes it to newest, and sets its visibility; the retained limit includes hidden notices. */
    public void post(BuddyNotice notice, boolean visible) {
        requireEdt(); if (closed) return; deck.post(java.util.Objects.requireNonNull(notice), visible); refresh();
    }
    /** Updates nullable grouping and visibility for a nonnull existing ID without promotion or new attention. */
    public void updateContext(BuddyNoticeId id, BuddyNotice.Group group, boolean visible) {
        requireEdt(); if (closed) return; deck.updateContext(id, group, visible); refresh();
    }
    /** Changes the title of an existing ID without promotion or new attention. ID must be nonnull and title nonblank. */
    public void updateTitle(BuddyNoticeId id, String title) {
        requireEdt(); if (closed) return; deck.updateTitle(id, title); refresh();
    }
    /** Removes an existing nonnull ID and its retained callbacks; a producer calls it when the work ends. */
    public void dismiss(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.dismiss(id); refresh();
    }
    /** Drops every notice and its callbacks, retaining presentation ownership. */
    public void clear() { requireEdt(); if (closed) return; deck.clear(); refresh(); }
    /** Sets host-supplied typing-animation intent, retained while hidden and until first show. Does not create presentation or implement a notification threshold; ignored after close. */
    public void setWorking(boolean value) {
        requireEdt(); if (closed) return; working = value;
        if (window != null) window.setWorking(value);
    }
    /** Realizes presentation lazily and shows it. Returns false after close, when headless, unsupported or missing its sprite; native runtime failures propagate for the host to handle. Failed availability is not cached here, so a host that wants one attempt must retain that decision. */
    public boolean show() {
        requireEdt(); if (closed) return false;
        if (window == null) window = BuddyWindow.create(options, deck);
        if (window == null) return false;
        window.setWorking(working); window.show(); return true;
    }
    /** Stops animation and pending drag frames and hides dependent windows; retains model and current working intent. */
    public void hide() { requireEdt(); if (!closed && window != null) window.hide(); }
    /** Requests a greeting when presentation is visible; does not create a native window. */
    public void greet() { requireEdt(); if (!closed && window != null) window.greet(); }
    /** Resets visible idle behavior without a greeting; does not create a native window. */
    public void poke() { requireEdt(); if (!closed && window != null) window.poke(); }
    /** Replaces nonnull appearance and future callbacks; initial position is used only at first native realization, never to undo a drag. */
    public void applyOptions(BuddyOptions value) {
        requireEdt(); if (closed) return; options = java.util.Objects.requireNonNull(value);
        if (window != null) window.applyOptions(options);
    }
    /** Closes once on EDT, disposing windows/timers/listeners and clearing retained options and notices. */
    @Override public void close() {
        requireEdt(); if (closed) return; closed = true;
        try { if (window != null) window.dispose(); }
        finally { window = null; options = null; deck.clear(); }
    }
}
