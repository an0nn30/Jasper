package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.ui.model.Nodes;
import java.awt.Component;
import java.awt.Graphics;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.swing.Icon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Severity presentation. Ruling G7: each severity keeps its glyph and colour (accent, warning, danger). A poster's own
 * raw icon is not shown on this branch (ruling P5): the SDK still carries it as a raw icon until Task 11 makes it named,
 * so the former fit-to-16-px behaviour is gone with it and the severity glyph shows instead.
 */
@ExtendWith(dev.jasper.app.testsupport.EdtTestExtension.class)
class NotificationStyleTest {
    @Test void everySeverityHasItsGlyphTintAndName() {
        assertThat(NotificationStyle.glyph(Severity.INFO)).isEqualTo("info");
        assertThat(NotificationStyle.glyph(Severity.WARNING)).isEqualTo("warning");
        assertThat(NotificationStyle.glyph(Severity.ERROR)).isEqualTo("error");
        assertThat(NotificationStyle.tone(Severity.INFO)).isEqualTo(Nodes.Severity.INFO);
        assertThat(NotificationStyle.tone(Severity.WARNING)).isEqualTo(Nodes.Severity.WARNING);
        assertThat(NotificationStyle.tone(Severity.ERROR)).isEqualTo(Nodes.Severity.DANGER);
        assertThat(NotificationStyle.severityName(Severity.ERROR)).isEqualTo("Error");
    }

    @Test void aPostersIconIsReplacedByTheSeverityGlyphUntilTask11() {
        var group = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
        var center = new NotificationCenter(Runnable::run, Clock.fixed(Instant.parse("2026-10-02T14:05:00Z"), ZoneOffset.UTC),
            () -> Delivery.Presence.FOCUSED);
        center.register(group);
        Icon poster = new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) { }
            @Override public int getIconWidth() { return 64; }
            @Override public int getIconHeight() { return 64; }
        };
        var entry = center.find(center.post(NotificationRequest.of(group.id(), Severity.WARNING, "Posted").withIcon(poster)).orElseThrow()).orElseThrow();
        var icon = NotificationStyle.icon("severity", entry);
        assertThat(icon.name()).isEqualTo("warning");
        assertThat(icon.severity()).contains(Nodes.Severity.WARNING);
        assertThat(icon.accessibleName()).isEqualTo("Warning");
    }
}
