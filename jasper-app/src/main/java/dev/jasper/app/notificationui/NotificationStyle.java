package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppIcons;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/** What cards and history rows share: severity icons, theme colours and link-style action buttons. EDT only. */
final class NotificationStyle {
    private NotificationStyle() {}

    /** The poster's icon, or the bundled one for the severity. */
    static Icon icon(Notification notification) {
        return notification.request().icon().map(NotificationStyle::fitted).orElseGet(() -> AppIcons.bundled(switch (notification.request().severity()) {
            case INFO -> "status/info";
            case WARNING -> "status/warning";
            case ERROR -> "status/error";
        }));
    }

    /** A poster's icon drawn within the 16 px box: a larger one is scaled down, a smaller one centred. */
    static Icon fitted(Icon icon) {
        int box = UIScale.scale(16);
        if (icon.getIconWidth() == box && icon.getIconHeight() == box) return icon;
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                int width = Math.max(1, icon.getIconWidth()), height = Math.max(1, icon.getIconHeight());
                double scale = Math.min(1.0, Math.min((double) box / width, (double) box / height));
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.translate(x + (box - width * scale) / 2.0, y + (box - height * scale) / 2.0);
                    g2.scale(scale, scale);
                    icon.paintIcon(c, g2, 0, 0);
                } finally {
                    g2.dispose();
                }
            }
            @Override public int getIconWidth() { return box; }
            @Override public int getIconHeight() { return box; }
        };
    }

    /** A theme colour as a plain {@link Color}, so a component keeps it through {@code updateUI} until restyled. */
    static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : new Color(color.getRGB(), true);
    }

    /** A borderless, link-coloured button that never takes focus and never renders markup. */
    static JButton link(String label, Runnable run) {
        var button = new JButton();
        // The property is read when the text is set, so it goes in first: JButton(String) would parse markup before it exists.
        button.putClientProperty("html.disable", Boolean.TRUE);
        button.setText(label);
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
