package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/** One history entry: icon, title (bold while unread) and count, full content, group and time, actions, remove. EDT only. */
final class HistoryRow extends JPanel {
    private Notification notification;
    private final WrappedText title;
    private WrappedText content;
    private final JLabel count = new JLabel();
    private final JLabel meta = new JLabel();
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    private final JButton remove = new JButton();

    HistoryRow(NotificationCenter center, Notification notification, String groupName, DateTimeFormatter time) {
        super(new BorderLayout(UIScale.scale(8), 0));
        this.notification = notification;
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, NotificationStyle.color("Separator.foreground", Color.LIGHT_GRAY)),
            BorderFactory.createEmptyBorder(8, 10, 8, 6)));
        var icon = new JLabel(NotificationStyle.icon(notification));
        icon.setVerticalAlignment(SwingConstants.TOP);
        add(icon, BorderLayout.WEST);
        var body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        title = new WrappedText(notification.request().title(), "Label.foreground", !notification.read());
        var titleRow = new JPanel(new BorderLayout(UIScale.scale(6), 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.add(title, BorderLayout.CENTER);
        count.putClientProperty("html.disable", Boolean.TRUE);
        count.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        count.setVerticalAlignment(SwingConstants.TOP);
        titleRow.add(count, BorderLayout.EAST);
        body.add(titleRow);
        if (!notification.request().content().isEmpty()) {
            content = new WrappedText(notification.request().content(), "Label.foreground", false);
            content.setAlignmentX(LEFT_ALIGNMENT);
            body.add(Box.createVerticalStrut(UIScale.scale(2)));
            body.add(content);
        }
        meta.putClientProperty("html.disable", Boolean.TRUE);
        meta.setForeground(NotificationStyle.color("Jasper.mutedForeground", Color.GRAY));
        meta.setText(groupName + " · " + time.format(notification.posted()));
        meta.setAlignmentX(LEFT_ALIGNMENT);
        body.add(Box.createVerticalStrut(UIScale.scale(4)));
        body.add(meta);
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        List<NotificationAction> all = notification.request().actions();
        for (int i = 0; i < all.size(); i++) {
            int index = i;
            JButton link = NotificationStyle.link(all.get(i).label(), () -> center.runAction(this.notification.id(), index));
            if (i > 0) link.setBorder(BorderFactory.createEmptyBorder(2, UIScale.scale(12), 2, 0));
            actions.add(link);
        }
        if (!all.isEmpty()) {
            body.add(Box.createVerticalStrut(UIScale.scale(4)));
            body.add(actions);
        }
        add(body, BorderLayout.CENTER);
        remove.setIcon(AppIcons.bundled("general/closeSmall"));
        remove.setRolloverIcon(AppIcons.bundled("general/closeSmallHovered"));
        remove.putClientProperty("JButton.buttonType", "borderless");
        remove.setFocusable(false);
        remove.setToolTipText("Remove");
        remove.getAccessibleContext().setAccessibleName("Remove notification");
        remove.addActionListener(event -> center.remove(this.notification.id()));
        var side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.add(remove, BorderLayout.NORTH);
        add(side, BorderLayout.EAST);
        update(notification);
    }

    Notification notification() { return notification; }

    /** The same entry with a new read or expired state. */
    void update(Notification next) {
        notification = next;
        title.setBold(!next.read());
        count.setText(next.count() > 1 ? "×" + next.count() : "");
        for (Component link : actions.getComponents()) link.setEnabled(!next.expired());
        getAccessibleContext().setAccessibleName(NotificationStyle.severityName(next.request().severity()) + ": " + next.request().title());
    }

    String titleText() { return title.getText(); }
    Color titleColor() { return title.getForeground(); }
    /** The content text, or null when the notification has none. */
    JTextArea contentArea() { return content; }
    boolean titleBold() { return title.getFont().isBold(); }
    String metaText() { return meta.getText(); }
    String countText() { return count.getText(); }
    List<JButton> actionButtons() { return Arrays.stream(actions.getComponents()).map(JButton.class::cast).toList(); }
    JButton removeButton() { return remove; }
}
