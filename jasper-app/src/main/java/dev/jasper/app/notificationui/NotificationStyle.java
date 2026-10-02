package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/** What cards and history rows share: severity icons, theme colours and link-style action buttons. EDT only. */
final class NotificationStyle {
    private NotificationStyle() {}

    /** The poster's icon, or the bundled one for the severity. */
    static Icon icon(Notification notification) {
        return notification.request().icon().orElseGet(() -> AppIcons.bundled(switch (notification.request().severity()) {
            case INFO -> "status/info";
            case WARNING -> "status/warning";
            case ERROR -> "status/error";
        }));
    }

    /** A theme colour as a plain {@link Color}, so a component keeps it through {@code updateUI} until restyled. */
    static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : new Color(color.getRGB(), true);
    }

    /** A borderless, link-coloured button that never takes focus and never renders markup. */
    static JButton link(String label, Runnable run) {
        var button = new JButton(label);
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setFocusable(false);
        button.setContentAreaFilled(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(color("Jasper.notification.linkForeground", Color.BLUE));
        button.addActionListener(event -> run.run());
        return button;
    }

    static String severityName(Severity severity) {
        return switch (severity) {
            case INFO -> "Information";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
