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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * One notification as a card (spec section 3): severity glyph, wrapping strong title with a muted repeat count, at most
 * five lines of content, up to three action links with the rest under More, and a close button shown on hover (always
 * for a sticky card). Clicking the body runs the notification's default action. An app-owned layout shell (ruling G6)
 * painted from module roles, hosting pointer-only kit views (ruling G5): nothing in it takes the keyboard from the
 * terminal. Plain text only. EDT only; close it when it leaves its layer.
 */
final class NotificationCard extends JPanel implements AutoCloseable {
    static final int CONTENT_LINES = 5;
    static final int VISIBLE_ACTIONS = 3;
    static final String ACTION = "action:", MORE = "more", CLOSE = "close", ICON = "severity";
    static final String MORE_LABEL = "More ▾";

    private final KitContext kit;
    private final NotificationCenter center;
    private final boolean sticky;
    private Notification notification;
    private final KitView icon, actions, close;
    private final TextBlock title, count, content;
    private final MouseAdapter hover;
    private final AutoCloseable theme;
    private boolean hovered, closed;
    /** Told when the pointer enters or leaves the card, so its countdown pauses. */
    Consumer<Boolean> onHover = value -> {};

    NotificationCard(KitContext kit, NotificationCenter center, Notification notification, boolean sticky) {
        this.kit = kit;
        this.center = center;
        this.sticky = sticky;
        this.notification = notification;
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        title = new TextBlock(kit, Typography.Role.BODY_STRONG, SemanticColor.TEXT_PRIMARY, 0);
        count = new TextBlock(kit, Typography.Role.BODY, SemanticColor.TEXT_SECONDARY, 1);
        content = new TextBlock(kit, Typography.Role.BODY, SemanticColor.TEXT_PRIMARY, CONTENT_LINES);
        icon = new KitView(kit, new ViewSpec(NotificationStyle.icon(ICON, notification)), event -> { }, true);
        actions = new KitView(kit, actionsSpec(List.of(), 0, false), this::event, true);
        close = new KitView(kit, closeSpec(), this::event, true);
        hover = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { setHovered(true); }
            @Override public void mouseExited(MouseEvent event) { if (!pointerInside()) setHovered(false); }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getSource() == NotificationCard.this && SwingUtilities.isLeftMouseButton(event))
                    center.activate(NotificationCard.this.notification.id());
            }
        };
        addMouseListener(hover);
        add(icon); add(title); add(count); add(content); add(actions); add(close);
        theme = kit.binding().subscribe(pair -> restyle());
        update(notification);
    }

    Notification notification() { return notification; }

    boolean sticky() { return sticky; }

    /** Shows {@code next}: the same entry with a new count, or new text. */
    void update(Notification next) {
        if (closed) return;
        notification = next;
        var request = next.request();
        icon.update(new ViewSpec(NotificationStyle.icon(ICON, next)));
        title.setText(request.title());
        count.setText(next.count() > 1 ? "×" + next.count() : "");
        count.setVisible(next.count() > 1);
        content.setText(request.content());
        content.setVisible(!request.content().isEmpty());
        fillActions(request.actions());
        actions.setVisible(!request.actions().isEmpty());
        setCursor(request.activate().isPresent() ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        close.update(closeSpec());
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(request.severity()) + ": " + request.title());
        getAccessibleContext().setAccessibleDescription(request.content());
        track(this);
        arrange(true);
        revalidate();
        repaint();
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
        else if (action.id().startsWith(ACTION)) center.runAction(notification.id(), Integer.parseInt(action.id().substring(ACTION.length())));
    }

    private ViewSpec closeSpec() {
        return new ViewSpec(new Nodes.Button(CLOSE, new ControlState(sticky || hovered, true, false, ""), "", "close", Nodes.ButtonKind.GHOST,
            CLOSE, "Close notification"));
    }

    /** {@code shown} leading links, then More holding the rest when {@code more}. */
    private ViewSpec actionsSpec(List<NotificationAction> all, int shown, boolean more) {
        var nodes = new ArrayList<Nodes.Node>();
        for (int i = 0; i < shown; i++)
            nodes.add(new Nodes.Button(ACTION + i, ControlState.READY, all.get(i).label(), "", Nodes.ButtonKind.LINK, ACTION + i, ""));
        if (more) {
            var items = new ArrayList<MenuItem>();
            for (int i = shown; i < all.size(); i++) items.add(new MenuItem("item:" + i, all.get(i).label(), "", ACTION + i, true, false));
            if (items.isEmpty()) items.add(new MenuItem("item:none", MORE_LABEL, "", "none", false, false));
            nodes.add(new Nodes.MenuButton(MORE, ControlState.READY, MORE_LABEL, "", Nodes.ButtonKind.LINK, "", items, ""));
        }
        return new ViewSpec(new Nodes.Stack("actions", ControlState.READY, Nodes.LayoutRole.INLINE, nodes));
    }

    /**
     * At most {@link #VISIBLE_ACTIONS} leading links, all of them when they fit one row; otherwise as many as fit beside
     * More (possibly none), with every other action under More. Wrapping onto a second row would hide More. Widths are the
     * mounted kit links' own.
     */
    private void fillActions(List<NotificationAction> all) {
        int candidates = Math.min(all.size(), VISIBLE_ACTIONS);
        actions.update(actionsSpec(all, candidates, true));
        var widths = new int[candidates];
        for (int i = 0; i < candidates; i++) widths[i] = width(ACTION + i);
        int moreWidth = width(MORE), gap = metric(DesignMetrics.Role.COLUMN_GAP), available = textWidth();
        int shown = candidates;
        if (all.size() > candidates || widthOf(widths, candidates, gap) > available) {
            shown = Math.min(candidates, all.size() - 1);
            while (shown > 0 && widthOf(widths, shown, gap) + gap + moreWidth > available) shown--;
        }
        actions.update(actionsSpec(all, shown, shown < all.size()));
    }

    private static int widthOf(int[] widths, int count, int gap) {
        int width = 0;
        for (int i = 0; i < count; i++) width += widths[i] + (i > 0 ? gap : 0);
        return width;
    }

    private int width(String name) {
        var component = find(actions, name);
        return component == null ? 0 : component.getPreferredSize().width;
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
    KitView actionsView() { return actions; }
    KitView closeView() { return close; }
    KitView iconView() { return icon; }
    boolean closeShown() { return close.spec().root().state().visible(); }

    /** The visible links' labels, then More when it shows. */
    List<String> actionLabels() {
        var labels = new ArrayList<String>();
        for (var node : ((Nodes.Stack) actions.spec().root()).children())
            labels.add(node instanceof Nodes.Button button ? button.label() : MORE_LABEL);
        return labels;
    }

    /** The More menu's item labels, or none. */
    List<String> moreLabels() {
        for (var node : ((Nodes.Stack) actions.spec().root()).children())
            if (node instanceof Nodes.MenuButton more) return more.items().stream().map(MenuItem::label).toList();
        return List.of();
    }

    private boolean pointerInside() {
        try { return getMousePosition(true) != null; }
        catch (HeadlessException headless) { return false; }
    }

    private int metric(DesignMetrics.Role role) { return NotificationStyle.metric(kit, role); }

    /** The width of the text column: the card less its padding, glyph and the gap after the glyph. */
    private int textWidth() {
        return metric(DesignMetrics.Role.NOTIFICATION_WIDTH) - metric(DesignMetrics.Role.NOTIFICATION_PADDING)
            - metric(DesignMetrics.Role.NOTIFICATION_ICON) - metric(DesignMetrics.Role.NOTIFICATION_GAP) - metric(DesignMetrics.Role.NOTIFICATION_PADDING);
    }

    /** Places the parts and returns the height they need; with {@code apply} false it only measures. */
    private int arrange(boolean apply) {
        int pad = metric(DesignMetrics.Role.NOTIFICATION_PADDING), iconSize = metric(DesignMetrics.Role.NOTIFICATION_ICON);
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
        if (actions.isVisible()) {
            y += gap;
            Dimension actionSize = actions.getPreferredSize();
            if (apply) actions.setBounds(textX, y, textWidth, actionSize.height);
            y += actionSize.height;
        }
        return y + pad;
    }

    @Override public void doLayout() { arrange(true); }
    @Override public Dimension getPreferredSize() { return new Dimension(metric(DesignMetrics.Role.NOTIFICATION_WIDTH), arrange(false)); }
    @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = metric(DesignMetrics.Role.NOTIFICATION_ARC);
            g.setColor(NotificationStyle.color(kit, SemanticColor.SURFACE_CARD));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(NotificationStyle.color(kit, SemanticColor.BORDER_SUBTLE));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        } finally { g.dispose(); }
    }

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
        try { theme.close(); } catch (Exception ignored) { /* the binding outlives the card */ }
        icon.close(); actions.close(); close.close();
    }

    boolean closed() { return closed; }
}
