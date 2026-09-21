package dev.jasper.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class WorkspaceActivityTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    @Test void replayAndCloseUseOpaqueIdentityEvenBeforeShellLaunch() throws Exception {
        edt(() -> {
            var content = content(launcher(new ArrayDeque<>()));
            var events = new ArrayList<WorkspaceActivity.Event>();
            var pane = content.currentPane();
            content.activity(events::add);
            var opened = (WorkspaceActivity.PaneState) events.getFirst();
            assertThat(opened.state()).isEqualTo(WorkspaceActivity.State.OPENED);
            assertThat(opened.id()).isNotSameAs(pane);
            content.close();
            assertThat(events).hasSize(2);
            var closed = (WorkspaceActivity.PaneState) events.getLast();
            assertThat(closed.state()).isEqualTo(WorkspaceActivity.State.CLOSED);
            assertThat(closed.id()).isEqualTo(opened.id());
        });
    }

    @Test void unsubscribingReleasesListenerBeforeMorePaneEvents() throws Exception {
        edt(() -> {
            var content = content(launcher(new ArrayDeque<>()));
            var events = new ArrayList<WorkspaceActivity.Event>();
            var registration = content.activity(events::add);
            assertThat(events).hasSize(1);
            registration.close(); registration.close();
            content.newTab(HOME); content.close();
            assertThat(events).hasSize(1);
        });
    }
}
