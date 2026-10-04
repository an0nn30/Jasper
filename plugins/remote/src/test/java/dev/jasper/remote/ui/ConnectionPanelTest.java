package dev.jasper.remote.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConnectionPanelTest {
    @Test void connectionUsesDotsAndPreservesFailureRetryAndCancelActions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var retries = new AtomicInteger();
            var cancels = new AtomicInteger();
            var panel = new ConnectionPanel("prod", "user@host", retries::incrementAndGet, cancels::incrementAndGet);
            panel.addNotify();
            try {
                assertThat(hasProgressBar(panel)).as("connection uses a compact dot indicator").isFalse();
                assertThat(panel.progress.isVisible()).isTrue();
                assertThat(panel.progress.timer.isRunning()).isTrue();
                panel.status("Authenticating…");
                assertThat(panel.message.getText()).isEqualTo("Authenticating…");
                panel.failed("Could not connect");
                assertThat(panel.progress.isVisible()).isFalse();
                assertThat(panel.progress.timer.isRunning()).isFalse();
                assertThat(panel.retry.isVisible()).isTrue();
                assertThat(panel.cancel.getText()).isEqualTo("Close");
                panel.retry.doClick();
                assertThat(retries.get()).isEqualTo(1);
                panel.working();
                assertThat(panel.progress.isVisible()).isTrue();
                assertThat(panel.progress.timer.isRunning()).isTrue();
                assertThat(panel.retry.isVisible()).isFalse();
                assertThat(panel.cancel.getText()).isEqualTo("Cancel");
                panel.cancel.doClick();
                assertThat(cancels.get()).isEqualTo(1);
                panel.finished();
                assertThat(panel.progress.timer.isRunning()).isFalse();
            } finally { panel.finished(); panel.removeNotify(); }
        });
    }

    private static boolean hasProgressBar(Component component) {
        if (component instanceof JProgressBar) return true;
        if (component instanceof Container container)
            for (Component child : container.getComponents()) if (hasProgressBar(child)) return true;
        return false;
    }
}
