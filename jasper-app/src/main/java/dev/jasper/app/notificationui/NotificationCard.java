package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One notification as a card (spec section 3): severity glyph, wrapping strong title with a muted repeat count (the count
 * role), at most five lines of content in the description role, up to three action links with the rest under More, and a
 * close button shown on hover (always for a sticky card). Clicking the body runs the notification's default action;
 * that and an action link first ask {@code raise} to bring the window the notification pertains to forward, unless the
 * entry has expired. An app-owned layout shell (ruling G6) on the kit's notice surface (a grouped card's radius, hairline and padding), hosting
 * pointer-only kit views (ruling G5): nothing in it takes the keyboard from the terminal. Plain text only. EDT only;
 * close it when it leaves its layer.
 */
final class NotificationCard extends dev.jasper.ui.swing.SurfacePanel implements AutoCloseable {
    static final int CONTENT_LINES = 5;
    static final int VISIBLE_ACTIONS = 3;
    static final String ACTION = "action:", MORE = "more", CLOSE = "close", ICON = "severity";
    static final String MORE_LABEL = "More ▾";

    private final KitContext kit;
    private final dev.jasper.app.platform.PresentationIcons presentationIcons = new dev.jasper.app.platform.PresentationIcons();
    private final NotificationCenter center;
    private final boolean sticky;
    private final Consumer<UUID> raise;
    private Notification notification;
    private final KitView icon, more, close;
    /** One pointer-only view per leading link, reused by position; hidden when not shown. */
    private final List<KitView> links = new ArrayList<>();
    private int shownLinks;
    private boolean moreShown;
    private final TextBlock title, count, content;
    private final MouseAdapter hover;
    private final AutoCloseable theme;
    private final AutoCloseable typography;
    private boolean hovered, closed;
    /** Told when the pointer enters or leaves the card, so its countdown pauses. */
    Consumer<Boolean> onHover = value -> {};

    NotificationCard(KitContext kit, NotificationCenter center, Notification notification, boolean sticky,
                     Consumer<UUID> raise) {
        super(kit, dev.jasper.ui.swing.SurfacePanel.Kind.NOTICE);
        this.kit = presentationIcons.context(kit);
        kit = this.kit;
        this.center = center;
        this.sticky = sticky;
        this.raise = java.util.Objects.requireNonNull(raise, "raise");
        this.notification = notification;
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        title = new TextBlock(kit, Typography.Role.BODY_STRONG, SemanticColor.TEXT_PRIMARY, 0);
        count = new TextBlock(kit, Typography.Role.COUNT, SemanticColor.TEXT_SECONDARY, 1, SemanticColor.SURFACE_CARD);
        content = new TextBlock(kit, Typography.Role.DESCRIPTION, SemanticColor.TEXT_SECONDARY, CONTENT_LINES);
        icon = new KitView(kit, new ViewSpec(NotificationStyle.icon(ICON, notification, presentationIcons)), event -> { }, true);
        for (int i = 0; i < VISIBLE_ACTIONS; i++) {
            var link = new KitView(kit, linkSpec(i, "Action"), this::event, true);
            link.setVisible(false);
            links.add(link);
        }
        more = new KitView(kit, moreSpec(List.of(), 0), this::event, true);
        more.setVisible(false);
        close = new KitView(kit, closeSpec(), this::event, true);
        hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { setHovered(true); }
            @Override public void mouseExited(MouseEvent event) { if (!pointerInside()) setHovered(false); }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getSource() != NotificationCard.this || !SwingUtilities.isLeftMouseButton(event)) return;
                UUID id = NotificationCard.this.notification.id();
                raiseUnlessExpired(id);
                center.activate(id);
            }
        };
        addMouseListener(hover);
        add(icon); add(title); add(count); add(content); links.forEach(this::add); add(more); add(close);
        theme = kit.binding().subscribe(pair -> restyle());
        update(notification);
        typography=dev.jasper.ui.swing.NativeStyles.installed().observeTypography(this::restyle);
    }

    Notification notification() { return notification; }

    boolean sticky() { return sticky; }

    /** Shows {@code next}: the same entry with a new count, or new text. */
    void update(Notification next) {
        if (closed) return;
        presentationIcons.reconcile(() -> {
            notification = next;
            var request = next.request();
            icon.update(new ViewSpec(NotificationStyle.icon(ICON, next, presentationIcons)));
            title.setText(request.title());
            count.setText(next.count() > 1 ? "×" + next.count() : "");
            count.setVisible(next.count() > 1);
            content.setText(request.content());
            content.setVisible(!request.content().isEmpty());
            fillActions(request.actions());
            setCursor(request.activate().isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            close.update(closeSpec());
            getAccessibleContext().setAccessibleName(NotificationStyle.severityName(request.severity()) + ": " + request.title());
            getAccessibleContext().setAccessibleDescription(request.content());
            arrange(true);
            revalidate();
            repaint();
        });
    }

    void setHovered(boolean value) {
        if (hovered == value) return;
        hovered = value;
        if (!closed) close.update(closeSpec());
        onHover.accept(value);
    }

    boolean hovered() { return hovered; }

    /** Runs the kit views' actions: a link or More item runs its action, close reads the entry. */
    private void event(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        if (action.id().equals(CLOSE)) center.markRead(notification.id());
        else if (action.id().startsWith(ACTION)) {
            raiseUnlessExpired(notification.id());
            center.runAction(notification.id(), Integer.parseInt(action.id().substring(ACTION.length())));
        }
    }

    /** An expired entry's click does nothing ({@link NotificationCenter#activate}), so it raises nothing either. */
    private void raiseUnlessExpired(UUID id) {
        if (center.find(id).filter(entry -> !entry.expired()).isPresent()) raise.accept(id);
    }

    private ViewSpec closeSpec() {
        return new ViewSpec(new Nodes.Button(CLOSE, new ControlState(sticky || hovered, true, false, ""), "", "close", Nodes.ButtonKind.GHOST,
            CLOSE, "Close notification", Nodes.ButtonPresentation.EMBEDDED));
    }

    /** A leading link: an EMBEDDED LINK, sized to its label, placed by the card with the former gap and padding. */
    private static ViewSpec linkSpec(int index, String label) {
        return new ViewSpec(new Nodes.Button(ACTION + index, ControlState.READY, label, "", Nodes.ButtonKind.LINK, ACTION + index, "",
            Nodes.ButtonPresentation.EMBEDDED));
    }

    /** More, holding every action from {@code first} on, as an EMBEDDED LINK menu. */
    private static ViewSpec moreSpec(List<NotificationAction> all, int first) {
        var items = new ArrayList<MenuItem>();
        for (int i = first; i < all.size(); i++) items.add(new MenuItem("item:" + i, all.get(i).label(), "", ACTION + i, true, false));
        if (items.isEmpty()) items.add(new MenuItem("item:none", MORE_LABEL, "", "none", false, false));
        return new ViewSpec(new Nodes.MenuButton(MORE, ControlState.READY, MORE_LABEL, "", Nodes.ButtonKind.LINK, "", items, "",
            Nodes.ButtonPresentation.EMBEDDED));
    }

    /**
     * At most {@link #VISIBLE_ACTIONS} leading links, all of them when they fit one row; otherwise as many as fit beside
     * More (possibly none), with every other action under More. Wrapping onto a second row would hide More. Widths are the
     * mounted kit links' own plus the former {@code NOTIFICATION_LINK_GAP} before every link but the first, as the old
     * link borders had; More is measured with its gap, as before.
     */
    private void fillActions(List<NotificationAction> all) {
        int candidates = Math.min(all.size(), VISIBLE_ACTIONS), gap = metric(DesignMetrics.Role.NOTIFICATION_LINK_GAP);
        var widths = new int[candidates];
        for (int i = 0; i < candidates; i++) {
            links.get(i).update(linkSpec(i, all.get(i).label()));
            widths[i] = links.get(i).getPreferredSize().width + (i > 0 ? gap : 0);
        }
        more.update(moreSpec(all, candidates));
        int moreWidth = more.getPreferredSize().width + gap, available = textWidth();
        int shown = candidates;
        if (all.size() > candidates || widthOf(widths, candidates) > available) {
            shown = Math.min(candidates, all.size() - 1);
            while (shown > 0 && widthOf(widths, shown) + moreWidth > available) shown--;
        }
        shownLinks = shown;
        moreShown = shown < all.size();
        if (moreShown) more.update(moreSpec(all, shown));
        for (int i = 0; i < links.size(); i++) links.get(i).setVisible(i < shown);
        more.setVisible(moreShown);
        // Links mounted or remounted now (an update, a theme switch) keep the pointer over the card counted as hover.
        track(this);
    }

    private static int widthOf(int[] widths, int count) {
        int width = 0;
        for (int i = 0; i < count; i++) width += widths[i];
        return width;
    }

    static JComponent find(Component root, String name) {
        if (root instanceof JComponent component && name.equals(component.getName())) return component;
        if (root instanceof Container container) for (Component child : container.getComponents()) {
            var found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    /** The pointer anywhere over the card, its links or its close button keeps it hovered. */
    private void track(Component component) {
        if (component != this && component instanceof JComponent owned && owned.getClientProperty(hover) == null) {
            owned.putClientProperty(hover, Boolean.TRUE);
            component.addMouseListener(hover);
        }
        if (component instanceof Container container) for (Component child : container.getComponents()) track(child);
    }

    List<String> titleLines() { return title.lines(); }
    List<String> contentLines() { return content.lines(); }
    String countText() { return count.text(); }
    java.awt.Color titleColor() { return title.getForeground(); }
    /** The leading links' views, shown or not. */
    List<KitView> linkViews() { return links; }
    KitView moreView() { return more; }
    KitView closeView() { return close; }
    KitView iconView() { return icon; }
    boolean closeShown() { return close.spec().root().state().visible(); }

    /** The visible links' labels, then More when it shows. */
    List<String> actionLabels() {
        var labels = new ArrayList<String>();
        for (int i = 0; i < shownLinks; i++) labels.add(((Nodes.Button) links.get(i).spec().root()).label());
        if (moreShown) labels.add(MORE_LABEL);
        return labels;
    }

    /** The More menu's item labels, or none. */
    List<String> moreLabels() {
        return moreShown ? ((Nodes.MenuButton) more.spec().root()).items().stream().map(MenuItem::label).toList() : List.of();
    }

    private boolean pointerInside() {
        try { return getMousePosition(true) != null; }
        catch (HeadlessException headless) { return false; }
    }

    private int metric(DesignMetrics.Role role) { return NotificationStyle.metric(kit, role); }

    /** A form card's inner padding: the card padding inside its hairline, as every grouped card. */
    private int padding() { return metric(DesignMetrics.Role.CARD_PADDING) + metric(DesignMetrics.Role.HAIRLINE); }

    /** The width of the text column: the card less its padding, glyph and the gap after the glyph. */
    private int textWidth() {
        return metric(DesignMetrics.Role.NOTIFICATION_WIDTH) - padding()
            - metric(DesignMetrics.Role.NOTIFICATION_ICON) - metric(DesignMetrics.Role.NOTIFICATION_GAP) - padding();
    }

    /** Places the parts and returns the height they need; with {@code apply} false it only measures. */
    private int arrange(boolean apply) {
        int pad = padding(), iconSize = metric(DesignMetrics.Role.NOTIFICATION_ICON);
        int gap = metric(DesignMetrics.Role.NOTIFICATION_GAP), closeSize = metric(DesignMetrics.Role.NOTIFICATION_CLOSE);
        int width = metric(DesignMetrics.Role.NOTIFICATION_WIDTH);
        int textX = pad + iconSize + gap;
        int titleWidth = width - textX - pad - closeSize - metric(DesignMetrics.Role.NOTIFICATION_TITLE_CLOSE_GAP);
        int textWidth = textWidth();
        title.setWrapWidth(titleWidth);
        content.setWrapWidth(textWidth);
        count.setWrapWidth(Math.max(1, count.textWidth()));
        int y = pad;
        Dimension titleSize = title.getPreferredSize();
        int titleHeight = titleSize.height;
        if (count.isVisible()) {
            Dimension countSize = count.getPreferredSize();
            int lineHeight = title.lineHeight();
            int lines = Math.max(1, title.lines().size());
            int countX = textX + title.lastLineWidth() + metric(DesignMetrics.Role.NOTIFICATION_COUNT_GAP);
            int countY = y + (lines - 1) * lineHeight;
            if (countX + countSize.width > textX + titleWidth) {
                countX = textX;
                countY = y + lines * lineHeight;
                titleHeight += lineHeight;
            }
            if (apply) count.setBounds(countX, countY + (lineHeight - countSize.height) / 2, countSize.width, countSize.height);
        }
        if (apply) {
            icon.setBounds(pad, pad, iconSize, iconSize);
            title.setBounds(textX, y, titleSize.width, titleSize.height);
            close.setBounds(width - pad - closeSize, pad, closeSize, closeSize);
        }
        y += Math.max(titleHeight, iconSize);
        if (content.isVisible()) {
            y += metric(DesignMetrics.Role.NOTIFICATION_CONTENT_GAP);
            Dimension contentSize = content.getPreferredSize();
            if (apply) content.setBounds(textX, y, contentSize.width, contentSize.height);
            y += contentSize.height;
        }
        if (shownLinks > 0 || moreShown) {
            // The former link row: each link as tall as its text plus NOTIFICATION_LINK_VERTICAL_PADDING above and below,
            // NOTIFICATION_LINK_GAP before every link but the first.
            y += gap;
            int padding = metric(DesignMetrics.Role.NOTIFICATION_LINK_VERTICAL_PADDING), linkGap = metric(DesignMetrics.Role.NOTIFICATION_LINK_GAP);
            var row = new ArrayList<KitView>(links.subList(0, shownLinks));
            if (moreShown) row.add(more);
            int rowHeight = 0;
            for (var view : row) rowHeight = Math.max(rowHeight, view.getPreferredSize().height + 2 * padding);
            int x = textX;
            for (int i = 0; i < row.size(); i++) {
                var view = row.get(i);
                if (i > 0) x += linkGap;
                int linkWidth = view.getPreferredSize().width;
                if (apply) view.setBounds(x, y, linkWidth, rowHeight);
                x += linkWidth;
            }
            y += rowHeight;
        }
        return y + pad;
    }

    @Override public void doLayout() { arrange(true); }
    @Override public Dimension getPreferredSize() { return new Dimension(metric(DesignMetrics.Role.NOTIFICATION_WIDTH), arrange(false)); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    /** The theme changed: the texts take the new roles, the links refit, the card repaints. Content and state stay. */
    private void restyle() {
        if (closed) return;
        title.restyle(); count.restyle(); content.restyle();
        fillActions(notification.request().actions());
        arrange(true);
        revalidate();
        repaint();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try { typography.close(); } catch (Exception ignored) { /* idempotent module subscription */ }
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the card */ }
        icon.close(); links.forEach(KitView::close); more.close(); close.close();
        presentationIcons.close();
    }

    boolean closed() { return closed; }
}
