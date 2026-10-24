package dev.jasper.app.notificationui;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.Severity;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.awt.Font;
import java.util.Optional;

/**
 * What cards, history rows and the Errors tab share: the severity glyph and module values read from the chrome
 * KitContext (colours, fonts and the former geometry as named metrics). Nothing here reads UIManager. Named poster artwork retains the severity tint; absent artwork uses the severity glyph.
 * EDT only.
 */
final class NotificationStyle {
    private NotificationStyle() {}

    /** The kit glyph for a severity: information, warning and error. */
    static String glyph(Severity severity) {
        return switch (severity) {
            case INFO -> "info";
            case WARNING -> "warning";
            case ERROR -> "error";
        };
    }

    /** The kit severity tint: the accent for information, the status colours for warnings and errors. */
    static Nodes.Severity tone(Severity severity) {
        return switch (severity) {
            case INFO -> Nodes.Severity.INFO;
            case WARNING -> Nodes.Severity.WARNING;
            case ERROR -> Nodes.Severity.DANGER;
        };
    }

    /** The notification's severity glyph, tinted by its severity. */
    static Nodes.Icon icon(String id, Notification notification, dev.jasper.app.platform.PresentationIcons icons) {
        Severity severity = notification.request().severity();
        var name = notification.request().icon().flatMap(value -> icons.name(value)).orElseGet(() -> glyph(severity));
        return new Nodes.Icon(id, ControlState.READY, name, Nodes.Hue.NONE, Nodes.IconSize.RAIL, severityName(severity),
            Optional.of(tone(severity)));
    }

    static Color color(KitContext kit, SemanticColor role) { return kit.theme().semantic().color(role); }

    static Font font(KitContext kit, Typography.Role role) {
        return dev.jasper.ui.swing.NativeStyles.installed().font(kit,role);
    }

    static int metric(KitContext kit, DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }

    static String severityName(Severity severity) {
        return switch (severity) {
            case INFO -> "Information";
            case WARNING -> "Warning";
            case ERROR -> "Error";
        };
    }
}
