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
 * What cards, history rows and the Errors window share: the severity glyph and module values read from the chrome
 * KitContext (colours, fonts and the former geometry as named metrics). Nothing here reads UIManager. A poster's own
 * icon is not shown on this branch (ruling P5/G7): the SDK still carries it as a raw icon until Task 11 makes it named.
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
    static Nodes.Icon icon(String id, Notification notification) {
        Severity severity = notification.request().severity();
        return new Nodes.Icon(id, ControlState.READY, glyph(severity), Nodes.Hue.NONE, Nodes.IconSize.RAIL, severityName(severity),
            Optional.of(tone(severity)));
    }

    static Color color(KitContext kit, SemanticColor role) { return kit.theme().semantic().color(role); }

    static Font font(KitContext kit, Typography.Role role) {
        Font font = kit.typography().font(role);
        return font.deriveFont(UIScale.scale(font.getSize2D()));
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
