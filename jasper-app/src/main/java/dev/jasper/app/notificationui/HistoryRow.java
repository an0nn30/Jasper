package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * One history entry: the severity glyph; the title (strong while unread, wrapping to the row) with its repeat count
 * beside it; the full content, group and time, and the action links; then remove. An app-owned row shell (the former
 * paddings, title/count gap and title/content gap as named metrics, a hairline between rows): the title and count are
 * the notification {@link TextBlock} primitive (the old row wrapped its title), the rest are pointer-only kit views on
 * the chrome context; like the former links, nothing here takes the keyboard. EDT only; the panel closes it.
 */
final class HistoryRow extends JPanel implements AutoCloseable {
    static final String CONTENT = "content", META = "meta", ACTION = "action:", REMOVE = "remove";
    private final KitContext kit;
    private final dev.jasper.app.platform.PresentationIcons presentationIcons = new dev.jasper.app.platform.PresentationIcons();
    private final NotificationCenter center;
    private final String group;
    private final DateTimeFormatter time;
    private final KitView lead, body, side;
    private final TextBlock title, count;
    private final JPanel leading = new JPanel(new BorderLayout()), trailing = new JPanel(new BorderLayout());
    private final JPanel column = new JPanel(new BorderLayout()), titleRow = new JPanel();
    private Notification notification;
    private boolean closed;

    HistoryRow(KitContext kit, NotificationCenter center, Notification notification, String group, DateTimeFormatter time) {
        super(new BorderLayout());
        this.kit = presentationIcons.context(kit);
        kit = this.kit;
        this.center = center;
        this.group = group;
        this.time = time;
        this.notification = notification;
        setOpaque(false);
        title = new TextBlock(kit, role(notification), SemanticColor.TEXT_PRIMARY, 0);
        count = new TextBlock(kit, Typography.Role.BODY, SemanticColor.TEXT_SECONDARY, 1);
        lead = new KitView(kit, leadSpec(), event -> { }, true);
        body = new KitView(kit, bodySpec(), this::event, true);
        side = new KitView(kit, sideSpec(), this::event, true);
        leading.setOpaque(false);
        leading.add(lead, BorderLayout.NORTH);
        trailing.setOpaque(false);
        trailing.add(side, BorderLayout.NORTH);
        titleRow.setOpaque(false);
        titleRow.setLayout(new TitleLayout());
        titleRow.add(title);
        titleRow.add(count);
        column.setOpaque(false);
        column.add(titleRow, BorderLayout.NORTH);
        column.add(body, BorderLayout.CENTER);
        add(leading, BorderLayout.WEST);
        add(column, BorderLayout.CENTER);
        add(trailing, BorderLayout.EAST);
        texts();
        restyle();
    }

    Notification notification() { return notification; }

    private static Typography.Role role(Notification entry) { return entry.read() ? Typography.Role.BODY : Typography.Role.BODY_STRONG; }

    /** The same entry with a new read, count or expired state. */
    void update(Notification next) {
        if (closed) return;
        presentationIcons.reset();
        notification = next;
        title.setRole(role(next));
        texts();
        lead.update(leadSpec());
        body.update(bodySpec());
        side.update(sideSpec());
        revalidate();
        repaint();
    }

    private void texts() {
        title.setText(notification.request().title());
        count.setText(notification.count() > 1 ? "×" + notification.count() : "");
        count.setVisible(notification.count() > 1);
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(notification.request().severity()) + ": " + notification.request().title());
    }

    /** Former geometry from the module: row paddings, the glyph gap, title/count and title/content gaps, a hairline under the row. */
    void restyle() {
        ((BorderLayout) getLayout()).setHgap(NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_GAP));
        ((BorderLayout) column.getLayout()).setVgap(NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_CONTENT_GAP));
        int vertical = NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_VERTICAL_PADDING);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, NotificationStyle.metric(kit, DesignMetrics.Role.HAIRLINE), 0,
                NotificationStyle.color(kit, SemanticColor.BORDER_SUBTLE)),
            BorderFactory.createEmptyBorder(vertical, NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_SIDE_PADDING), vertical,
                NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_ROW_TRAILING_PADDING))));
        title.restyle();
        count.restyle();
        revalidate();
        repaint();
    }

    private void event(UiEvent event) {
        if (closed || !(event instanceof UiEvent.Action action)) return;
        if (action.id().equals(REMOVE)) center.remove(notification.id());
        else if (action.id().startsWith(ACTION)) center.runAction(notification.id(), Integer.parseInt(action.id().substring(ACTION.length())));
    }

    private ViewSpec leadSpec() { return new ViewSpec(NotificationStyle.icon("severity", notification, presentationIcons)); }

    private ViewSpec bodySpec() {
        var request = notification.request();
        var nodes = new ArrayList<Nodes.Node>();
        if (!request.content().isEmpty()) nodes.add(new Nodes.Text(CONTENT, ControlState.READY, request.content(), Nodes.TextRole.BODY));
        nodes.add(new Nodes.Text(META, ControlState.READY, group + " · " + time.format(notification.posted()), Nodes.TextRole.CAPTION));
        List<NotificationAction> all = request.actions();
        if (!all.isEmpty()) {
            var links = new ArrayList<Nodes.Node>();
            var state = new ControlState(true, !notification.expired(), false, "");
            for (int i = 0; i < all.size(); i++)
                links.add(new Nodes.Button(ACTION + i, state, all.get(i).label(), "", Nodes.ButtonKind.LINK, ACTION + i, "", Nodes.ButtonPresentation.EMBEDDED));
            nodes.add(new Nodes.Stack("actions", ControlState.READY, Nodes.LayoutRole.INLINE, links));
        }
        return new ViewSpec(new Nodes.Stack("entry", ControlState.READY, Nodes.LayoutRole.VERTICAL, nodes));
    }

    private ViewSpec sideSpec() {
        return new ViewSpec(new Nodes.Button(REMOVE, ControlState.READY, "", "close", Nodes.ButtonKind.GHOST, REMOVE, "Remove notification",
            Nodes.ButtonPresentation.EMBEDDED));
    }

    /** The title wraps to the column less the count and the former title/count gap; the count sits at the end. */
    private final class TitleLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        private int countWidth() { return count.isVisible() ? count.textWidth() : 0; }
        private int gap() { return count.isVisible() ? NotificationStyle.metric(kit, DesignMetrics.Role.HISTORY_TITLE_GAP) : 0; }
        private void wrap(int width) {
            count.setWrapWidth(Math.max(1, countWidth()));
            title.setWrapWidth(Math.max(1, width - countWidth() - gap()));
        }
        @Override public Dimension preferredLayoutSize(Container parent) {
            int width = parent.getWidth() > 0 ? parent.getWidth() : title.textWidth() + countWidth() + gap();
            wrap(width);
            return new Dimension(width, Math.max(title.getPreferredSize().height, count.isVisible() ? count.getPreferredSize().height : 0));
        }
        @Override public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, preferredLayoutSize(parent).height); }
        @Override public void layoutContainer(Container parent) {
            int width = parent.getWidth();
            wrap(width);
            var titleSize = title.getPreferredSize();
            title.setBounds(0, 0, titleSize.width, titleSize.height);
            if (count.isVisible()) count.setBounds(width - countWidth(), 0, countWidth(), count.getPreferredSize().height);
        }
    }

    private JComponent part(KitView view, String name) { return NotificationCard.find(view, name); }

    String titleText() { return title.text(); }
    Color titleColor() { return title.getForeground(); }
    List<String> titleLines() { return title.lines(); }
    TextBlock titleBlock() { return title; }
    /** The content text's mounted label, or null when the notification has none. */
    JComponent contentArea() { return part(body, CONTENT); }
    /** Whether the title shows strong (unread). */
    boolean titleBold() { return title.role() == Typography.Role.BODY_STRONG; }
    String metaText() { return ((javax.swing.JLabel) part(body, META)).getText(); }
    String countText() { return count.isVisible() ? count.text() : ""; }
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
        presentationIcons.close();
    }
}
