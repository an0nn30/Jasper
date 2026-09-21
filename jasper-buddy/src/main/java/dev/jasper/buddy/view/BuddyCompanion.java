package dev.jasper.buddy.view;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.internal.presentation.BuddyWindow;

public final class BuddyCompanion implements AutoCloseable {
    private final BuddyDeck deck;
    private BuddyOptions options;
    private BuddyWindow window;
    private boolean closed, working;
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
    public void post(BuddyNotice notice) {
        requireEdt(); if (closed) return; deck.post(java.util.Objects.requireNonNull(notice)); refresh();
    }
    public void updateTitle(BuddyNoticeId id, String title) {
        requireEdt(); if (closed) return; deck.updateTitle(id, title); refresh();
    }
    public void acknowledge(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.acknowledge(id); refresh();
    }
    public void dismiss(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.dismiss(id); refresh();
    }
    public void orphan(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.orphan(id); refresh();
    }
    public void orphan(BuddyNoticeId id, String finalDetail) {
        requireEdt(); if (closed) return; deck.orphan(id, finalDetail); refresh();
    }
    public void clear() { requireEdt(); if (closed) return; deck.clear(); refresh(); }
    public void setWorking(boolean value) {
        requireEdt(); if (closed) return; working = value;
        if (window != null) window.setWorking(value);
    }
    public boolean show() {
        requireEdt(); if (closed) return false;
        if (window == null) window = BuddyWindow.create(options, deck);
        if (window == null) return false;
        window.setWorking(working); window.show(); return true;
    }
    public void hide() { requireEdt(); if (!closed && window != null) window.hide(); }
    public void greet() { requireEdt(); if (!closed && window != null) window.greet(); }
    public void poke() { requireEdt(); if (!closed && window != null) window.poke(); }
    public void applyOptions(BuddyOptions value) {
        requireEdt(); if (closed) return; options = java.util.Objects.requireNonNull(value);
        if (window != null) window.applyOptions(options);
    }
    @Override public void close() {
        requireEdt(); if (closed) return; closed = true;
        try { if (window != null) window.dispose(); }
        finally { window = null; options = null; deck.clear(); }
    }
}
