package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import javax.swing.JComponent;

/**
 * One window's stack of notification cards for its content area's bottom-right corner (spec section 3): newest at the
 * bottom, at most {@value #VISIBLE}; a fourth pushes out the oldest auto-hiding card, and when every visible card is
 * sticky the rest wait behind a "+N more" chip that opens the history. An auto-hiding card counts ten seconds of
 * focused, unhovered time. The layer has no mouse listeners and is never focusable, so every click beside a card
 * reaches the terminal and the shell keeps the keyboard; the cards and the chip are pointer-only kit views on the chrome
 * KitContext. No animation. EDT only; close it when its window closes.
 */
public final class CardLayer extends JComponent implements AutoCloseable {
    public static final int VISIBLE = 3;
    public static final Duration AUTO_HIDE = Duration.ofSeconds(10);
    static final String MORE = "more";

    private final KitContext kit;
    private final NotificationCenter center;
    private final BiFunction<Duration, Runnable, Runnable> schedule;
    private final LongSupplier nanoTime;
    /** Top to bottom: oldest to newest. */
    private final List<Slot> slots = new ArrayList<>();
    /** Waiting for room, oldest first. */
    private final List<UUID> waiting = new ArrayList<>();
    private final KitView more;
    private final Subscription subscription;
    private final AutoCloseable theme;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean closed;
    private int shadowRenders;

    /** What a cached shadow was rendered for: the card's size, the rings, the colour with its alpha, the device scale. */
    private record ShadowKey(int width, int height, int arc, int margin, float userScale, int argb, double deviceX, double deviceY) {}

    private static final class Slot {
        final NotificationCard card;
        long remaining = AUTO_HIDE.toNanos();
        long started;
        Runnable cancel;
        boolean hovered;
        /** The card's shadow, rendered once for {@link #shadowKey} and drawn on every paint until that changes. */
        ShadowKey shadowKey;
        BufferedImage shadow;
        Slot(NotificationCard card) { this.card = card; }
        UUID id() { return card.notification().id(); }
    }

    /**
     * @param kit the chrome kit context the cards and the chip are built with
     * @param center the notification store
     * @param focused whether the window is active
     * @param openHistory shows the Notifications panel, for the "+N more" chip
     */
    public CardLayer(KitContext kit, NotificationCenter center, boolean focused, Runnable openHistory) {
        this(kit, center, focused, openHistory, CardLayer::swingTimer, System::nanoTime);
    }

    CardLayer(KitContext kit, NotificationCenter center, boolean focused, Runnable openHistory,
              BiFunction<Duration, Runnable, Runnable> schedule, LongSupplier nanoTime) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.center = Objects.requireNonNull(center, "center");
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.focused = focused;
        Objects.requireNonNull(openHistory, "openHistory");
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        more = new KitView(kit, moreSpec(), event -> {
            if (!closed && event instanceof UiEvent.Action action && action.id().equals(MORE)) openHistory.run();
        }, true);
        more.setVisible(false);
        add(more);
        // A theme change repaints the shadows (the cache follows the colour) and re-lays the restyled cards.
        theme = kit.binding().subscribe(pair -> refresh());
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

    /** Takes the entry's card out of this window, showing or waiting, because it now shows in another one. */
    public void withdraw(UUID id) {
        if (closed) return;
        boolean waited = waiting.remove(id);
        Slot slot = slot(id);
        if (slot == null && !waited) return;
        if (slot != null) discard(slot);
        promote();
        refresh();
    }

    List<UUID> waitingIds() { return List.copyOf(waiting); }

    KitView moreView() { return more; }

    /** The chip's text, or empty while it is hidden. */
    String moreText() { return more.isVisible() ? ((Nodes.Button) more.spec().root()).label() : ""; }

    /** How many card shadows this layer has rendered (cached ones are drawn, not rendered again). */
    int shadowRenders() { return shadowRenders; }

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
        subscription.close();
        stopTracking.run();
        for (Slot slot : List.copyOf(slots)) discard(slot);
        waiting.clear();
        refresh();
        closed = true;
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the layer */ }
        more.close();
    }

    private ViewSpec moreSpec() {
        return new ViewSpec(new Nodes.Button(MORE, ControlState.READY, "+" + waiting.size() + " more", "", Nodes.ButtonKind.LINK, MORE,
            "Show the notifications waiting behind these cards"));
    }

    private int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    private Slot slot(UUID id) {
        for (Slot slot : slots) if (slot.id().equals(id)) return slot;
        return null;
    }

    private void place(Notification notification) {
        boolean sticky = center.mode(notification.request().group()) == DisplayMode.STICKY;
        var slot = new Slot(new NotificationCard(kit, center, notification, sticky));
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
        slot.card.close();
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
        slot.card.close();
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
            case NotificationCenter.Change.SystemRequested requested -> { }
        }
    }

    /** Re-lays the stack and publishes its size; the window keeps the layer in its corner from that. */
    private void refresh() {
        if (closed) return;
        more.update(moreSpec());
        more.setVisible(!waiting.isEmpty());
        setPreferredSize(stackSize());
        setVisible(!slots.isEmpty() || !waiting.isEmpty());
        revalidate();
        repaint();
    }

    private Dimension stackSize() {
        if (slots.isEmpty() && waiting.isEmpty()) return new Dimension(0, 0);
        int margin = metric(DesignMetrics.Role.NOTIFICATION_STACK_MARGIN), gap = metric(DesignMetrics.Role.NOTIFICATION_STACK_GAP);
        int height = margin * 2;
        if (more.isVisible()) height += more.getPreferredSize().height + (slots.isEmpty() ? 0 : gap);
        for (int i = 0; i < slots.size(); i++) height += slots.get(i).card.getPreferredSize().height + (i > 0 ? gap : 0);
        return new Dimension(metric(DesignMetrics.Role.NOTIFICATION_WIDTH) + margin * 2, height);
    }

    @Override public void doLayout() {
        int margin = metric(DesignMetrics.Role.NOTIFICATION_STACK_MARGIN), gap = metric(DesignMetrics.Role.NOTIFICATION_STACK_GAP);
        int width = metric(DesignMetrics.Role.NOTIFICATION_WIDTH);
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

    /**
     * Paints each card's shadow from an image rendered once per card (twelve antialiased translucent rings are most of
     * this layer's cost, and any terminal repaint beneath a card repaints the whole layer). The image is made at the
     * destination's device scale and drawn back at logical size, so it stays crisp on a HiDPI screen.
     */
    @Override protected void paintComponent(Graphics graphics) {
        if (slots.isEmpty()) return;
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // The shadow is the module's scrim colour at the former per-ring alphas, stronger on a dark theme.
            Color shadow = kit.theme().semantic().color(SemanticColor.SURFACE_SCRIM);
            int alpha = kit.metrics().value(kit.theme().dark() ? DesignMetrics.Role.NOTIFICATION_SHADOW_ALPHA_DARK : DesignMetrics.Role.NOTIFICATION_SHADOW_ALPHA_LIGHT);
            int argb = new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), alpha).getRGB();
            AffineTransform device = g.getTransform();
            double deviceX = device.getScaleX() > 0 ? device.getScaleX() : 1, deviceY = device.getScaleY() > 0 ? device.getScaleY() : 1;
            int arc = 2 * metric(DesignMetrics.Role.CARD_RADIUS), margin = metric(DesignMetrics.Role.NOTIFICATION_STACK_MARGIN);
            int rings = kit.metrics().value(DesignMetrics.Role.NOTIFICATION_STACK_MARGIN);
            for (Slot slot : slots) {
                Rectangle card = slot.card.getBounds();
                var key = new ShadowKey(card.width, card.height, arc, margin, UIScale.getUserScaleFactor(), argb, deviceX, deviceY);
                if (!key.equals(slot.shadowKey)) {
                    slot.shadow = renderShadow(key, rings);
                    slot.shadowKey = key;
                    shadowRenders++;
                }
                g.drawImage(slot.shadow, card.x - margin, card.y - margin, card.width + margin * 2, card.height + margin * 2, null);
            }
        } finally { g.dispose(); }
    }

    /** The rings around a card of the key's size, in an image {@code margin} larger on every side, at the key's device scale. */
    private static BufferedImage renderShadow(ShadowKey key, int rings) {
        int width = key.width() + key.margin() * 2, height = key.height() + key.margin() * 2;
        var image = new BufferedImage(Math.max(1, (int) Math.ceil(width * key.deviceX())),
            Math.max(1, (int) Math.ceil(height * key.deviceY())), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(key.deviceX(), key.deviceY());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(key.argb(), true));
            for (int i = rings; i >= 1; i--) {
                int grow = UIScale.scale(i);
                g.fillRoundRect(key.margin() - grow, key.margin() - grow, key.width() + grow * 2, key.height() + grow * 2,
                    key.arc() + grow, key.arc() + grow);
            }
        } finally { g.dispose(); }
        return image;
    }

    /** Swing's timer as a plain function: schedule a task, get back the way to cancel it. */
    private static Runnable swingTimer(Duration delay, Runnable task) {
        var timer = new javax.swing.Timer((int) Math.max(1, Math.min(Integer.MAX_VALUE, delay.toMillis())), event -> task.run());
        timer.setRepeats(false);
        timer.start();
        return timer::stop;
    }
}
