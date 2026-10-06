package dev.jasper.remote.ui;

import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/**
 * The connecting overlay. Its motion is the kit's indeterminate progress (controller ruling G7), which replaces the
 * former bouncing dots: the kit runs and stops that animation (no timer while hidden, determinate, removed or closed,
 * none with motion off; jasper-ui RemoteKitTest and SwitchesTest), so this test asserts the descriptor states.
 */
class ConnectionPanelTest {
    @Test void connectionShowsIndeterminateProgressAndPreservesFailureRetryAndCancelActions() throws Exception {
        edt(() -> {
            var retries = new AtomicInteger();
            var cancels = new AtomicInteger();
            var panel = new ConnectionPanel(new FakeKit("dev.jasper.remote"), "prod", "user@host", retries::incrementAndGet, cancels::incrementAndGet);
            var view = panel.view();
            var progress = node(view, ConnectionPanel.PROGRESS, Nodes.Progress.class);
            assertThat(progress.fraction()).as("indeterminate").isEqualTo(-1);
            assertThat(progress.label()).isEqualTo("Connecting");
            assertThat(progress.state().visible()).isTrue();
            assertThat(button(view, ConnectionPanel.RETRY).state().visible()).isFalse();
            assertThat(texts(view)).contains("prod", "user@host", "Preparing connection…");
            panel.status("Authenticating…");
            assertThat(panel.message()).isEqualTo("Authenticating…");
            panel.failed("Could not connect");
            assertThat(node(view, ConnectionPanel.PROGRESS, Nodes.Progress.class).state().visible()).as("no motion after a failure").isFalse();
            assertThat(button(view, ConnectionPanel.RETRY).state().visible()).isTrue();
            assertThat(button(view, ConnectionPanel.CANCEL).label()).isEqualTo("Close");
            assertThat(panel.message()).isEqualTo("Could not connect");
            click(view, ConnectionPanel.RETRY);
            assertThat(retries.get()).isEqualTo(1);
            panel.working();
            assertThat(node(view, ConnectionPanel.PROGRESS, Nodes.Progress.class).state().visible()).isTrue();
            assertThat(button(view, ConnectionPanel.RETRY).state().visible()).isFalse();
            assertThat(button(view, ConnectionPanel.CANCEL).label()).isEqualTo("Cancel");
            click(view, ConnectionPanel.CANCEL);
            assertThat(cancels.get()).isEqualTo(1);
            panel.finished();
            assertThat(node(view, ConnectionPanel.PROGRESS, Nodes.Progress.class).state().visible()).as("finished stops the motion").isFalse();
        });
    }

    /** G11: Escape in the overlay runs its Cancel (Close after a failure); nothing is Enter's default. */
    @Test void escapeCancelsOrClosesAndNoActionIsTheDefault() throws Exception {
        edt(() -> {
            var cancels = new AtomicInteger();
            var panel = new ConnectionPanel(new FakeKit("dev.jasper.remote"), "prod", "user@host", () -> { }, cancels::incrementAndGet);
            assertThat(primaries(panel.view())).isEmpty();
            press(panel.view(), form(panel.view()).cancelAction());
            panel.failed("Refused");
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(cancels.get()).isEqualTo(2);
        });
    }

    /** A status or failure arriving after the overlay closed (a stale completion) never touches the disposed view. */
    @Test void lateUpdatesAfterCloseAreIgnored() throws Exception {
        edt(() -> {
            var kit = new RecordingKit("dev.jasper.remote");
            var panel = new ConnectionPanel(kit, "prod", "user@host", () -> { }, () -> { });
            panel.close();
            panel.status("Opening shell…"); panel.failed("late"); panel.working(); panel.finished();
            assertThat(kit.lateCalls).isEmpty();
            assertThat(closed(panel.view())).isTrue();
        });
    }
}
