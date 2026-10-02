package dev.jasper.app.notifications;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationCenterThreadTest {
    @Test void submitFromAWorkerLandsOnTheEdt() throws Exception {
        var center = new AtomicReference<NotificationCenter>();
        var deliveredOnEdt = new AtomicReference<Boolean>();
        SwingUtilities.invokeAndWait(() -> {
            center.set(new NotificationCenter(SwingUtilities::invokeLater, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED));
            center.get().register(new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD));
            center.get().onChange(change -> deliveredOnEdt.compareAndSet(null, SwingUtilities.isEventDispatchThread()));
        });
        center.get().submit(NotificationRequest.of("test.cards", Severity.INFO, "from a worker"));
        var titles = new AtomicReference<Object>();
        SwingUtilities.invokeAndWait(() -> titles.set(center.get().history().stream().map(entry -> entry.request().title()).toList()));
        assertThat(titles.get()).isEqualTo(java.util.List.of("from a worker"));
        assertThat(deliveredOnEdt.get()).isTrue();
    }
}
