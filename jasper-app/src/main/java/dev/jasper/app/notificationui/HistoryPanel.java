package dev.jasper.app.notificationui;

import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

/**
 * The Notifications panel's view in one window (spec section 4): New and Earlier sections over the shared center,
 * newest first, with Clear All. New lists every unread entry and every entry the panel read by looking, until the panel
 * hides; Earlier lists the rest. Showing it in a focused window marks everything read. A layout-only shell (a scrolled
 * column of rows that follows the panel's width) of kit views on the chrome context; a row is kept, not rebuilt, while
 * its entry stays listed, so a theme change or an update keeps it. EDT only; stops listening when its site closes.
 */
public final class HistoryPanel extends JPanel {
    private static final String NEW = "New";
    private static final String EARLIER = "Earlier";
    private static final String ROWS = "rows", EMPTY = "empty";
    static final String CLEAR_ALL = "clear-all";
    private final KitContext kit;
    private final NotificationCenter center;
    private final Set<UUID> fresh = new LinkedHashSet<>();
    private final Rows rows = new Rows();
    private final KitView header, empty, newHeading, earlierHeading;
    /** The rows, or the empty state in their place: a panel body's standard empty state, centred in the body. */
    private final java.awt.CardLayout bodies = new java.awt.CardLayout();
    private final JPanel body = new JPanel(bodies);
    private boolean showingEmpty;
    private final DateTimeFormatter time;
    private final Map<UUID, HistoryRow> shown = new LinkedHashMap<>();
    private final Subscription subscription;
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private final Runnable stopTracking;
    private boolean focused;
    private boolean visible;
    private boolean closed;

    /**
     * @param kit the chrome kit context
     * @param center the notification store
     * @param site the tool window site the panel lives in
     * @param focused whether the window is active
     */
    public HistoryPanel(KitContext kit, NotificationCenter center, PanelSite site, boolean focused) {
        this(kit, center, site, focused, "Notifications");
    }

    /**
     * As the four-argument form, named {@code title} for screen readers: Event Log under labelled stripes (spec 10.7).
     *
     * @param kit the kit the panel's views are built with
     * @param center the notification store
     * @param site the tool window site the panel lives in
     * @param focused whether the window is active
     * @param title the panel's title
     */
    public HistoryPanel(KitContext kit, NotificationCenter center, PanelSite site, boolean focused, String title) {
        super(new BorderLayout());
        this.kit = Objects.requireNonNull(kit, "kit");
        this.center = Objects.requireNonNull(center, "center");
        this.focused = focused;
        setOpaque(false);
        time = DateTimeFormatter.ofPattern("HH:mm").withZone(center.clock().getZone());
        getAccessibleContext().setAccessibleName(title);
        // The tool window header carries the title.
        header = new KitView(kit, headerSpec(false), event -> {
            if (event instanceof UiEvent.Action action && action.id().equals(CLEAR_ALL) && !closed) center.clear();
        });
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);
        empty = new KitView(kit, emptySpec(), event -> { }, true);
        // The tool window paints the body surface; the empty state sits on it as the rows do.
        empty.panelSurface(false);
        newHeading = heading(NEW);
        earlierHeading = heading(EARLIER);
        var scroll = new JScrollPane(rows, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        body.setOpaque(false);
        body.add(scroll, ROWS);
        body.add(empty, EMPTY);
        add(body, BorderLayout.CENTER);
        theme = kit.binding().subscribe(pair -> restyle());
        restyle();
        subscription = center.onChange(this::changed);
        stopTracking = WindowFocus.track(this, this::windowFocusChanged);
        site.onVisibility(this::visibilityChanged);
        site.onClosed(this::close);
        visible = site.visible();
        rebuild();
        readIfLooking();
        typography=dev.jasper.ui.swing.NativeStyles.installed().observeTypography(this::restyle);
    }

    private KitView heading(String name) {
        var view = new KitView(kit, new ViewSpec(new Nodes.Text("heading", ControlState.READY, name, Nodes.TextRole.SECTION_LABEL)), event -> { }, true);
        view.getAccessibleContext().setAccessibleName(name + " notifications");
        return view;
    }

    private ViewSpec headerSpec(boolean any) {
        return new ViewSpec(new Nodes.Button(CLEAR_ALL, new ControlState(true, any, false, ""), "Clear All", "", Nodes.ButtonKind.GHOST, CLEAR_ALL,
            "Remove every notification"));
    }

    /** The former paddings around the header, the empty text and the headings, from named metrics. */
    private void restyle() {
        int side = metric(DesignMetrics.Role.HISTORY_SIDE_PADDING);
        int vertical = metric(DesignMetrics.Role.HISTORY_HEADER_VERTICAL_PADDING);
        header.setBorder(BorderFactory.createEmptyBorder(vertical, metric(DesignMetrics.Role.HISTORY_HEADER_LEADING_PADDING), vertical,
            metric(DesignMetrics.Role.HISTORY_HEADER_TRAILING_PADDING)));
        for (var view : List.of(newHeading, earlierHeading))
            view.setBorder(BorderFactory.createEmptyBorder(metric(DesignMetrics.Role.HISTORY_HEADING_TOP_PADDING), side,
                metric(DesignMetrics.Role.HISTORY_HEADING_BOTTOM_PADDING), side));
        for (HistoryRow row : shown.values()) row.restyle();
        rows.revalidate();
        rows.repaint();
    }

    private int metric(DesignMetrics.Role role) { return NotificationStyle.metric(kit, role); }

    void windowFocusChanged(boolean value) {
        focused = value;
        readIfLooking();
    }

    /** The ids shown under New, in display order. */
    List<UUID> newSection() { return displayed(newHeading); }

    /** The ids shown under Earlier, in display order. */
    List<UUID> earlierSection() { return displayed(earlierHeading); }

    /** The section headings shown, top to bottom. */
    List<String> sectionHeadings() {
        var names = new ArrayList<String>();
        for (Component child : rows.getComponents()) {
            if (child == newHeading) names.add(NEW);
            if (child == earlierHeading) names.add(EARLIER);
        }
        return names;
    }

    /** What is on screen: each row belongs to the heading above it. */
    private List<UUID> displayed(KitView section) {
        var ids = new ArrayList<UUID>();
        Component current = null;
        for (Component child : rows.getComponents()) {
            if (child instanceof HistoryRow row) {
                if (current == section) ids.add(row.notification().id());
            } else if (child == newHeading || child == earlierHeading) current = child;
        }
        return ids;
    }

    boolean showingEmpty() { return showingEmpty; }

    /** The empty state's view (a panel whose body is the standard empty state), for its placement. */
    KitView emptyView() { return empty; }

    /** A hidden-header panel whose body is the standard empty state, so it takes the plugin panels' empty geometry. */
    private static ViewSpec emptySpec() {
        return new ViewSpec(new Nodes.Panel("history.empty", ControlState.READY, new Nodes.Header("history.empty.header",
            new ControlState(false, true, false, ""), "Notification history", java.util.OptionalInt.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), List.of()), java.util.Optional.empty(), new Nodes.Empty("empty", ControlState.READY,
            new Nodes.Icon("empty.icon", ControlState.READY, "bell", Nodes.Hue.NEUTRAL, Nodes.IconSize.EMPTY, "No notifications"),
            "No Notifications", "Notifications from Jasper and its plugins appear here.", List.of()), java.util.Optional.empty()));
    }

    HistoryRow row(UUID id) { return shown.get(id); }

    AbstractButton clearAllButton() { return (AbstractButton) NotificationCard.find(header, CLEAR_ALL); }

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

    /** Lists the history again; a row whose entry is still listed is kept and updated rather than rebuilt. */
    private void rebuild() {
        if (closed || !visible) return;
        List<Notification> all = center.history();
        var previous = new HashMap<>(shown);
        shown.clear();
        rows.removeAll();
        showingEmpty = all.isEmpty();
        bodies.show(body, showingEmpty ? EMPTY : ROWS);
        section(newHeading, all.stream().filter(this::isNew).toList(), previous);
        section(earlierHeading, all.stream().filter(entry -> !isNew(entry)).toList(), previous);
        previous.values().forEach(HistoryRow::close);
        if (!header.isClosed()) header.update(headerSpec(!all.isEmpty()));
        rows.revalidate();
        rows.repaint();
    }

    private boolean isNew(Notification entry) { return fresh.contains(entry.id()) || !entry.read(); }

    private void section(KitView heading, List<Notification> entries, Map<UUID, HistoryRow> previous) {
        if (entries.isEmpty()) return;
        rows.add(heading);
        for (Notification entry : entries) {
            HistoryRow row = previous.remove(entry.id());
            if (row == null) {
                String group = center.group(entry.request().group()).map(NotificationGroup::name).orElse(entry.request().group());
                row = new HistoryRow(kit, center, entry, group, time);
            } else row.update(entry);
            shown.put(entry.id(), row);
            rows.add(row);
        }
    }

    private void close() {
        if (closed) return;
        closed = true;
        subscription.close();
        stopTracking.run();
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the panel */ }
        shown.values().forEach(HistoryRow::close);
        shown.clear();
        header.close(); empty.close(); newHeading.close(); earlierHeading.close();
    }

    /**
     * Rows that follow the viewport's width, so text wraps instead of scrolling sideways: each row is laid out at the
     * column's width before its height is read, as wrapped text needs.
     */
    private final class Rows extends JPanel implements Scrollable {
        Rows() {
            setOpaque(false);
            setLayout(new LayoutManager() {
                @Override public void addLayoutComponent(String name, Component component) { }
                @Override public void removeLayoutComponent(Component component) { }
                @Override public Dimension preferredLayoutSize(Container parent) {
                    int width = parent.getWidth();
                    if (width <= 0) for (Component child : parent.getComponents()) width = Math.max(width, child.getPreferredSize().width);
                    int height = 0;
                    for (Component child : parent.getComponents()) if (child.isVisible()) height += heightAt(child, width);
                    return new Dimension(width, height);
                }
                @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
                @Override public void layoutContainer(Container parent) {
                    int y = 0, width = parent.getWidth();
                    for (Component child : parent.getComponents()) {
                        if (!child.isVisible()) continue;
                        int height = heightAt(child, width);
                        child.setBounds(0, y, width, height);
                        layoutTree(child);
                        y += height;
                    }
                }
            });
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return metric(DesignMetrics.Role.NOTIFICATION_SCROLL_STEP);
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            int step = metric(DesignMetrics.Role.NOTIFICATION_SCROLL_STEP);
            return Math.max(step, visible.height - step);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** A row's height at {@code width}: laid out at that width first, since its wrapped text decides its height. */
    private static int heightAt(Component child, int width) {
        if (width <= 0) return child.getPreferredSize().height;
        child.setSize(width, Math.max(1, child.getHeight()));
        layoutTree(child);
        return child.getPreferredSize().height;
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container container) for (Component child : container.getComponents()) layoutTree(child);
    }
}
