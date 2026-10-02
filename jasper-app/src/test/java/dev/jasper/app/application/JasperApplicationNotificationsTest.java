package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.PanelEntry;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationNotificationsTest {
    @Test void theApplicationOffersTheNotificationsPanelAndActions() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            var contributions = application[0].contributions();
            assertThat(contributions.panels()).extracting(PanelEntry::id).contains(NotificationsFeature.PANEL_ID);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).isPresent();
            assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS)).isPresent();
            application[0].quitNow();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
