package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * One history entry: the severity glyph; the title (strong while unread), full content, group and time, and the action
 * links; then the repeat count and remove. An app-owned row shell (former paddings as named metrics, a hairline
 * between rows) of three pointer-only kit views on the chrome context: like the former links, nothing here takes the
 * keyboard. EDT only; the panel closes it.
 */
final class HistoryRow extends JPanel implements AutoCloseable {
    static final String TITLE = "title", CONTENT = "content", META = "meta", ACTION = "action:", COUNT = "count", REMOVE = "remove";
    private final KitContext kit;
    private final NotificationCenter center;
    private final String group;
    private final DateTimeFormatter time;
    private final KitView lead, body, side;
    private final JPanel leading = new JPanel(new BorderLayout()), trailing = new JPanel(new BorderLayout());
    private Notification notification;
    private boolean closed;

    HistoryRow(KitContext kit, NotificationCenter center, Notification notification, String group, DateTimeFormatter time) {
        super(new BorderLayout());
        this.kit = kit;
        this.center = center;
        this.group = group;
        this.time = time;
        this.notification = notification;
        setOpaque(false);
        lead = new KitView(kit, leadSpec(), event -> { }, true);
        body = new KitView(kit, bodySpec(), this::event, true);
        side = new KitView(kit, sideSpec(), this::event, true);
        leading.setOpaque(false);
        leading.add(lead, BorderLayout.NORTH);
        trailing.setOpaque(false);
        trailing.add(side, BorderLayout.NORTH);
        add(leading, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
        add(trailing, BorderLayout.EAST);
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(notification.request().severity()) + ": " + notification.request().title());
        restyle();
    }

    Notification notification() { return notification; }

    /** The same entry with a new read, count or expired state. */
    void update(Notification next) {
        if (closed) return;
        notification = next;
        lead.update(leadSpec());
        body.update(bodySpec());
        side.update(sideSpec());
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(next.request().severity()) + ": " + next.request().title());
    }

    /** Former geometry from the module: row paddings, the glyph gap and a hairline under the row. */
    void restyle() {
        int gap = NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_GAP);
        ((BorderLayout) getLayout()).setHgap(gap);
        int vertical = NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_VERTICAL_PADDING);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, NotificationStyle.metric(kit, DesignMetrics.Role.HAIRLINE), 0,
                NotificationStyle.color(kit, SemanticColor.BORDER_SUBTLE)),
            BorderFactory.createEmptyBorder(vertical, NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_SIDE_PADDING), vertical,
                NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_TRAILING_PADDING))));
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        if (action.id().equals(REMOVE)) center.remove(notification.id());
        else if (action.id().startsWith(ACTION)) center.runAction(notification.id(), Integer.parseInt(action.id().substring(ACTION.length())));
    }

    private ViewSpec leadSpec() { return new ViewSpec(NotificationStyle.icon("severity", notification)); }

    private ViewSpec bodySpec() {
        var request = notification.request();
        var nodes = new ArrayList<Nodes.Node>();
        nodes.add(new Nodes.Text(TITLE, ControlState.READY, request.title(), notification.read() ? Nodes.TextRole.BODY : Nodes.TextRole.BODY_STRONG));
        if (!request.content().isEmpty()) nodes.add(new Nodes.Text(CONTENT, ControlState.READY, request.content(), Nodes.TextRole.BODY));
        nodes.add(new Nodes.Text(META, ControlState.READY, group + " · " + time.format(notification.posted()), Nodes.TextRole.CAPTION));
        List<NotificationAction> all = request.actions();
        if (!all.isEmpty()) {
            var links = new ArrayList<Nodes.Node>();
            var state = new ControlState(true, !notification.expired(), false, "");
            for (int i = 0; i < all.size(); i++) links.add(new Nodes.Button(ACTION + i, state, all.get(i).label(), "", Nodes.ButtonKind.LINK, ACTION + i, ""));
            nodes.add(new Nodes.Stack("actions", ControlState.READY, Nodes.LayoutRole.INLINE, links));
        }
        return new ViewSpec(new Nodes.Stack("entry", ControlState.READY, Nodes.LayoutRole.VERTICAL, nodes));
    }

    private ViewSpec sideSpec() {
        return new ViewSpec(new Nodes.Stack("side", ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
            new Nodes.Button(REMOVE, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, REMOVE, "Remove notification"),
            new Nodes.Text(COUNT, notification.count() > 1 ? ControlState.READY : new ControlState(false, true, false, ""),
                notification.count() > 1 ? "×" + notification.count() : " ", Nodes.TextRole.COUNT))));
    }

    private JComponent part(KitView view, String name) { return NotificationCard.find(view, name); }

    String titleText() { return ((javax.swing.JLabel) part(body, TITLE)).getText(); }
    Color titleColor() { return part(body, TITLE).getForeground(); }
    /** The content text's mounted label, or null when the notification has none. */
    JComponent contentArea() { return part(body, CONTENT); }
    /** Whether the title shows strong (unread). */
    boolean titleBold() {
        return ((Nodes.Text) ((Nodes.Stack) body.spec().root()).children().getFirst()).role() == Nodes.TextRole.BODY_STRONG;
    }
    String metaText() { return ((javax.swing.JLabel) part(body, META)).getText(); }
    String countText() { return notification.count() > 1 ? ((javax.swing.JLabel) part(side, COUNT)).getText() : ""; }
    List<AbstractButton> actionButtons() {
        var buttons = new ArrayList<AbstractButton>();
        for (int i = 0; i < notification.request().actions().size(); i++) buttons.add((AbstractButton) part(body, ACTION + i));
        return buttons;
    }
    AbstractButton removeButton() { return (AbstractButton) part(side, REMOVE); }
    KitView bodyView() { return body; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        lead.close(); body.close(); side.close();
    }
}
