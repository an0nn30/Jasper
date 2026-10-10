package dev.jasper.app.notificationui;

import dev.jasper.app.notifications.*;
import dev.jasper.app.platform.PresentationIcon;
import dev.jasper.app.testsupport.KitTestSupport;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(dev.jasper.app.testsupport.EdtTestExtension.class)
class HistoryRowTest {
    @Test void coalescedHistoryReplacesCustomArtworkWithBuiltinAndDifferentCustomArtwork() {
        for (String nextName : java.util.List.of("file", "owner:next")) {
            var center = new NotificationCenter(Runnable::run, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), () -> Delivery.Presence.FOCUSED);
            center.register(new NotificationGroup("test.rows", "Rows", "jasper", DisplayMode.CARD));
            var request = NotificationRequest.of("test.rows", Severity.WARNING, "Repeated");
            var id = center.post(request.withIcon(new PresentationIcon("owner:old", "M 2 2 L 14 14"))).orElseThrow();
            try (var row = new HistoryRow(KitTestSupport.context(), center, center.find(id).orElseThrow(), "Rows", DateTimeFormatter.ISO_INSTANT)) {
                var next = new PresentationIcon(nextName, nextName.contains(":") ? "M 2 14 L 14 2" : null);
                assertThat(center.post(request.withIcon(next))).contains(id);
                row.update(center.find(id).orElseThrow());
                assertThat(row.notification().request().icon()).contains(next);
                assertThat(row.notification().count()).isEqualTo(2);
                // Force a refresh/paint of the retained same-id lead control after the replacement.
                row.setSize(400, 120);
                var image = new java.awt.image.BufferedImage(400, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { row.printAll(g); } finally { g.dispose(); }
            }
        }
    }
}
