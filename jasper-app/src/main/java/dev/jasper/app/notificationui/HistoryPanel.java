package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * The Notifications panel's view in one window (spec section 4): New and Earlier sections over the shared center,
 * newest first, with Clear All. New lists every unread entry and every entry the panel read by looking, until the panel
 * hides; Earlier lists the rest. Showing it in a focused window marks everything read. EDT only; stops listening when
 * its site closes.
 */
public final class HistoryPanel extends JPanel {
    private static final String NEW = "New";
    private static final String EARLIER = "Earlier";
    private final NotificationCenter center;
    private final Set<UUID> fresh = new LinkedHashSet<>();
    private final Rows rows = new Rows();
    private final JLabel empty = new JLabel("No notifications", SwingConstants.CENTER);
    private final JButton clearAll = new JButton("Clear All");
    private final DateTimeFormatter time;
    private final Map<UUID, HistoryRow> shown = new LinkedHashMap<>();
    private final Subscription subscription;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean visible;
    private boolean closed;

    public HistoryPanel(NotificationCenter center, PanelSite site, boolean focused) {
        super(new BorderLayout());
        this.center = Objects.requireNonNull(center, "center");
        this.focused = focused;
        time = DateTimeFormatter.ofPattern("HH:mm").withZone(center.clock().getZone());
        getAccessibleContext().setAccessibleName("Notifications");
        // The tool window header carries the title.
        var header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        clearAll.putClientProperty("JButton.buttonType", "borderless");
        clearAll.setToolTipText("Remove every notification");
        clearAll.addActionListener(event -> center.clear());
        header.add(clearAll, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        empty.putClientProperty("html.disable", Boolean.TRUE);
        empty.setAlignmentX(LEFT_ALIGNMENT);
        empty.setBorder(BorderFactory.createEmptyBorder(24, 10, 24, 10));
        var scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        subscription = center.onChange(this::changed);
        stopTracking = WindowFocus.track(this, this::windowFocusChanged);
        site.onVisibility(this::visibilityChanged);
        site.onClosed(this::close);
        visible = site.visible();
        rebuild();
        readIfLooking();
    }

    void windowFocusChanged(boolean value) {
        focused = value;
        readIfLooking();
    }

    /** The ids shown under New, in display order. */
    List<UUID> newSection() { return displayed(NEW); }

    /** The ids shown under Earlier, in display order. */
    List<UUID> earlierSection() { return displayed(EARLIER); }

    /** The section headings shown, top to bottom. */
    List<String> sectionHeadings() {
        var names = new ArrayList<String>();
        for (Component child : rows.getComponents()) if (child instanceof JLabel heading && child != empty) names.add(heading.getText());
        return names;
    }

    /** What is on screen: each row belongs to the heading above it. */
    private List<UUID> displayed(String section) {
        var ids = new ArrayList<UUID>();
        String current = null;
        for (Component child : rows.getComponents()) {
            if (child instanceof HistoryRow row) {
                if (section.equals(current)) ids.add(row.notification().id());
            } else if (child instanceof JLabel heading && child != empty) current = heading.getText();
        }
        return ids;
    }

    boolean showingEmpty() { return empty.getParent() == rows; }

    HistoryRow row(UUID id) { return shown.get(id); }

    JButton clearAllButton() { return clearAll; }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist. Rows hold plain colours, so a theme change rebuilds them.
        if (center != null) rebuild();
    }

    private void visibilityChanged(boolean value) {
        visible = value;
        if (!value) fresh.clear();
        rebuild();
        readIfLooking();
    }

    /** Looking at the panel in a focused window reads everything. */
    private void readIfLooking() {
        if (closed || !visible || !focused) return;
        boolean any = false;
        for (Notification entry : center.history()) {
            if (entry.read()) continue;
            fresh.add(entry.id());
            any = true;
        }
        if (!any) return;
        center.markAllRead();
        rebuild();
    }

    private void changed(NotificationCenter.Change change) {
        if (closed) return;
        switch (change) {
            case NotificationCenter.Change.Upserted upserted -> {
                Notification entry = upserted.notification();
                HistoryRow earlier = shown.get(entry.id());
                // Read elsewhere while listed under New: it stays there until the panel hides.
                if (visible && entry.read() && earlier != null && !earlier.notification().read()) fresh.add(entry.id());
                if (!entry.read() && visible && focused) {
                    fresh.add(entry.id());
                    center.markRead(entry.id());
                }
                HistoryRow row = shown.get(entry.id());
                if (row != null && row.notification().count() == entry.count()) row.update(entry);
                else rebuild();
            }
            case NotificationCenter.Change.Removed removed -> {
                fresh.remove(removed.id());
                rebuild();
            }
            case NotificationCenter.Change.Cleared cleared -> {
                fresh.clear();
                rebuild();
            }
            case NotificationCenter.Change.CardRequested requested -> { }
            case NotificationCenter.Change.SystemRequested requested -> { }
        }
    }

    private void rebuild() {
        if (closed || !visible) return;
        rows.removeAll();
        shown.clear();
        List<Notification> all = center.history();
        if (all.isEmpty()) rows.add(empty);
        section(NEW, all.stream().filter(this::isNew).toList());
        section(EARLIER, all.stream().filter(entry -> !isNew(entry)).toList());
        clearAll.setEnabled(!all.isEmpty());
        rows.revalidate();
        rows.repaint();
    }

    private boolean isNew(Notification entry) { return fresh.contains(entry.id()) || !entry.read(); }

    private void section(String name, List<Notification> entries) {
        if (entries.isEmpty()) return;
        var heading = new JLabel(name);
        heading.putClientProperty("html.disable", Boolean.TRUE);
        heading.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        heading.getAccessibleContext().setAccessibleName(name + " notifications");
        rows.add(heading);
        for (Notification entry : entries) {
            String group = center.group(entry.request().group()).map(NotificationGroup::name).orElse(entry.request().group());
            var row = new HistoryRow(center, entry, group, time);
            row.setAlignmentX(LEFT_ALIGNMENT);
            shown.put(entry.id(), row);
            rows.add(row);
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        stopTracking.run();
    }

    /** Rows that follow the viewport's width, so text wraps instead of scrolling sideways. */
    private static final class Rows extends JPanel implements Scrollable {
        Rows() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return UIScale.scale(16); }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(UIScale.scale(16), visible.height - UIScale.scale(16));
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
