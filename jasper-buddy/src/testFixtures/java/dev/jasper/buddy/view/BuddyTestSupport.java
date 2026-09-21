package dev.jasper.buddy.view;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.notice.*;
import dev.jasper.buddy.internal.model.BuddyDeck;
import java.awt.Font;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Test artifact only; never part of Buddy's production jar or supported API. */
public final class BuddyTestSupport {
    private final BuddyDeck deck = new BuddyDeck();
    private final BuddyCompanion companion = edt(() -> new BuddyCompanion(
        BuddyOptions.builder(new Font("Dialog", Font.PLAIN, 13)).build(), deck));
    public BuddyCompanion companion() { return companion; }
    public List<BuddyNotice> notices() { return edt(deck::notices); }
    public List<BuddyNotice> column() { return edt(deck::column); }
    public int generation() { return edt(deck::generation); }
    public int size() { return edt(deck::size); }
    public boolean isEmpty() { return edt(deck::isEmpty); }
    public boolean acknowledged(BuddyNoticeId id) { return edt(() -> deck.acknowledged(id)); }
    public void acknowledge(BuddyNoticeId id) { edt(() -> { companion.acknowledge(id); return null; }); }
    public void dismiss(BuddyNoticeId id) { edt(() -> { companion.dismiss(id); return null; }); }
    private static <T> T edt(Supplier<T> action) {
        if (SwingUtilities.isEventDispatchThread()) return action.get();
        var value = new java.util.concurrent.atomic.AtomicReference<T>();
        try { SwingUtilities.invokeAndWait(() -> value.set(action.get())); }
        catch (Exception failure) { throw new AssertionError(failure); }
        return value.get();
    }
}
