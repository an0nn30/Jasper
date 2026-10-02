package dev.jasper.app.notificationui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * One window's stack of notification cards for its content area's bottom-right corner (spec section 3): newest at the
 * bottom, at most {@value #VISIBLE}; a fourth pushes out the oldest auto-hiding card, and when every visible card is
 * sticky the rest wait behind a "+N more" chip that opens the history. An auto-hiding card counts ten seconds of
 * focused, unhovered time. The layer has no mouse listeners and is never focusable, so every click beside a card
 * reaches the terminal and the shell keeps the keyboard. No animation. EDT only; close it when its window closes.
 */
public final class CardLayer extends JComponent implements AutoCloseable {
    public static final int VISIBLE = 3;
    public static final Duration AUTO_HIDE = Duration.ofSeconds(10);
    /** Room around the stack: the cards' inset from the content corner, and their shadow. */
    static final int MARGIN = 12;
    static final int GAP = 8;

    private final NotificationCenter center;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final LongSupplier nanoTime;
    /** Top to bottom: oldest to newest. */
    private final List<Slot> slots = new ArrayList<>();
    /** Waiting for room, oldest first. */
    private final List<UUID> waiting = new ArrayList<>();
    private final JButton more;
    private final Subscription subscription;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean closed;

    private static final class Slot {
        final NotificationCard card;
        long remaining = AUTO_HIDE.toNanos();
        long started;
        Runnable cancel;
        boolean hovered;
        Slot(NotificationCard card) { this.card = card; }
        UUID id() { return card.notification().id(); }
    }

    public CardLayer(NotificationCenter center, boolean focused, Runnable openHistory) {
        this(center, focused, openHistory, CardLayer::swingTimer, System::nanoTime);
    }

    CardLayer(NotificationCenter center, boolean focused, Runnable openHistory,
              BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime) {
        this.center = Objects.requireNonNull(center, "center");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.focused = focused;
        Objects.requireNonNull(openHistory, "openHistory");
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        more = NotificationStyle.link("", openHistory);
        more.setVisible(false);
        add(more);
        subscription = center.onChange(this::changed);
        stopTracking = WindowFocus.track(this, this::windowFocusChanged);
        refresh();
    }

    /** Shows a card for the entry, or refreshes and restarts the one already showing. */
    public void show(Notification notification) {
        if (closed) return;
        Notification latest = center.find(notification.id()).orElse(null);
        if (latest == null || latest.read() || latest.expired()) return;
        Slot existing = slot(latest.id());
        if (existing != null) {
            existing.card.update(latest);
            pause(existing);
            existing.remaining = AUTO_HIDE.toNanos();
            resume(existing);
            refresh();
            return;
        }
        if (!waiting.contains(latest.id())) {
            if (slots.size() >= VISIBLE) {
                Slot oldest = slots.stream().filter(slot -> !slot.card.sticky()).findFirst().orElse(null);
                if (oldest == null) waiting.add(latest.id());
                else {
                    discard(oldest);
                    place(latest);
                }
            } else place(latest);
        }
        refresh();
    }

    /** Dismiss Cards: every card here, showing or waiting, closes and is read. */
    public void dismissAll() {
        var ids = new ArrayList<UUID>();
        for (Slot slot : slots) ids.add(slot.id());
        ids.addAll(waiting);
        for (UUID id : ids) center.markRead(id);
    }

    /** The entries with a card showing, top to bottom. */
    public List<UUID> showing() { return slots.stream().map(Slot::id).toList(); }

    List<UUID> waitingIds() { return List.copyOf(waiting); }

    JButton moreButton() { return more; }

    NotificationCard card(UUID id) {
        Slot slot = slot(id);
        return slot == null ? null : slot.card;
    }

    void windowFocusChanged(boolean value) {
        if (focused == value) return;
        focused = value;
        for (Slot slot : slots) {
            if (value) resume(slot);
            else pause(slot);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        stopTracking.run();
        for (Slot slot : List.copyOf(slots)) discard(slot);
        waiting.clear();
        refresh();
    }

    @Override public void updateUI() {
        super.updateUI();
        // The chip's link colour is a plain colour, so a theme change has to set it again.
        if (more != null) more.setForeground(NotificationStyle.color("Jasper.notification.linkForeground", Color.BLUE));
    }

    private Slot slot(UUID id) {
        for (Slot slot : slots) if (slot.id().equals(id)) return slot;
        return null;
    }

    private void place(Notification notification) {
        boolean sticky = center.mode(notification.request().group()) == DisplayMode.STICKY;
        var slot = new Slot(new NotificationCard(center, notification, sticky));
        slot.card.onHover = hovered -> {
            slot.hovered = hovered;
            if (hovered) pause(slot);
            else resume(slot);
        };
        slots.add(slot);
        add(slot.card);
        resume(slot);
    }

    private void discard(Slot slot) {
        pause(slot);
        slots.remove(slot);
        remove(slot.card);
    }

    private void promote() {
        while (slots.size() < VISIBLE && !waiting.isEmpty()) {
            Notification next = center.find(waiting.removeFirst()).orElse(null);
            if (next != null && !next.read() && !next.expired()) place(next);
        }
    }

    private void resume(Slot slot) {
        if (closed || slot.card.sticky() || slot.hovered || !focused || slot.cancel != null) return;
        slot.started = nanoTime.getAsLong();
        slot.cancel = schedule.apply(Duration.ofNanos(slot.remaining), () -> hidden(slot));
    }

    private void pause(Slot slot) {
        if (slot.cancel == null) return;
        slot.cancel.run();
        slot.cancel = null;
        slot.remaining = Math.max(0, slot.remaining - (nanoTime.getAsLong() - slot.started));
    }

    /** The countdown ran out: the card leaves; the entry stays unread in history. */
    private void hidden(Slot slot) {
        slot.cancel = null;
        if (!slots.remove(slot)) return;
        remove(slot.card);
        promote();
        refresh();
    }

    private void changed(NotificationCenter.Change change) {
        switch (change) {
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                boolean gone = entry.read() || entry.expired();
                Slot slot = slot(entry.id());
                if (gone) {
                    waiting.remove(entry.id());
                    if (slot != null) discard(slot);
                    promote();
                } else if (slot != null) slot.card.update(entry);
                refresh();
            }
            case NotificationCenter.Change.Removed removed -> {
                waiting.remove(removed.id());
                Slot slot = slot(removed.id());
                if (slot != null) discard(slot);
                promote();
                refresh();
            }
            case NotificationCenter.Change.Cleared cleared -> {
                waiting.clear();
                for (Slot slot : List.copyOf(slots)) discard(slot);
                refresh();
            }
            case NotificationCenter.Change.CardRequested requested -> { }
        }
    }

    /** Re-lays the stack and publishes its size; the window keeps the layer in its corner from that. */
    private void refresh() {
        more.setText("+" + waiting.size() + " more");
        more.setVisible(!waiting.isEmpty());
        setPreferredSize(stackSize());
        setVisible(!slots.isEmpty() || !waiting.isEmpty());
        revalidate();
        repaint();
    }

    private Dimension stackSize() {
        if (slots.isEmpty() && waiting.isEmpty()) return new Dimension(0, 0);
        int margin = UIScale.scale(MARGIN), gap = UIScale.scale(GAP);
        int height = margin * 2;
        if (more.isVisible()) height += more.getPreferredSize().height + (slots.isEmpty() ? 0 : gap);
        for (int i = 0; i < slots.size(); i++) height += slots.get(i).card.getPreferredSize().height + (i > 0 ? gap : 0);
        return new Dimension(UIScale.scale(NotificationCard.WIDTH) + margin * 2, height);
    }

    @Override public void doLayout() {
        int margin = UIScale.scale(MARGIN), gap = UIScale.scale(GAP), width = UIScale.scale(NotificationCard.WIDTH);
        // Bottom-aligned, so a clipped layer still shows the newest card.
        int y = getHeight() - margin;
        for (int i = slots.size() - 1; i >= 0; i--) {
            NotificationCard card = slots.get(i).card;
            int height = card.getPreferredSize().height;
            y -= height;
            card.setBounds(margin, y, width, height);
            y -= gap;
        }
        if (slots.isEmpty()) y += gap;
        if (more.isVisible()) {
            Dimension size = more.getPreferredSize();
            more.setBounds(margin + width - size.width, y - size.height, size.width, size.height);
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (slots.isEmpty()) return;
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color shadow = UIManager.getColor("Popup.dropShadowColor");
            if (shadow == null) shadow = Color.BLACK;
            g.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), FlatLaf.isLafDark() ? 4 : 2));
            int arc = UIScale.scale(NotificationCard.ARC);
            for (Slot slot : slots) {
                Rectangle card = slot.card.getBounds();
                for (int i = MARGIN; i >= 1; i--) {
                    int grow = UIScale.scale(i);
                    g.fillRoundRect(card.x - grow, card.y - grow, card.width + grow * 2, card.height + grow * 2, arc + grow, arc + grow);
                }
            }
        } finally { g.dispose(); }
    }

    /** Swing's timer as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable swingTimer(Duration delay, Runnable task) {
        var timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
}
