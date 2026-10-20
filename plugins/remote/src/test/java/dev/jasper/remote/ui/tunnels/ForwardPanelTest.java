package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.testsupport.Kits;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiKit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.*;

/** Forward a Port on the testkit fake (Plan 2 Task 8b). */
class ForwardPanelTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> forwarded = new ArrayList<>();
    final Set<Integer> taken = new HashSet<>();
    int cancelled;

    ForwardPanel panel(Optional<RemoteHost> paneHost) { return panel(new FakeKit("dev.jasper.remote"), paneHost); }

    ForwardPanel panel(UiKit kit, Optional<RemoteHost> paneHost) {
        return new ForwardPanel(kit, List.of(prod, web), paneHost, port -> !taken.contains(port), forwarded::add, () -> cancelled++);
    }

    static String value(ForwardPanel panel, String id) { return node(panel.view(), id, Nodes.Field.class).value(); }
    static boolean visible(ForwardPanel panel, String id) { return find(panel.view(), id).orElseThrow().state().visible(); }
    static boolean enabled(ForwardPanel panel, String id) { return find(panel.view(), id).orElseThrow().state().enabled(); }

    @Test void theLocalPortFollowsTheRemoteOneUnlessItIsTakenOrEdited() throws Exception {
        edt(() -> {
            taken.add(8080);
            var panel = panel(Optional.of(prod));
            type(panel.view(), ForwardPanel.REMOTE_PORT, "3000");
            assertThat(value(panel, ForwardPanel.LOCAL_PORT)).isEqualTo("3000");
            type(panel.view(), ForwardPanel.REMOTE_PORT, "8080");
            assertThat(value(panel, ForwardPanel.LOCAL_PORT)).as("the next free port").isEqualTo("8081");
            type(panel.view(), ForwardPanel.LOCAL_PORT, "9000");
            type(panel.view(), ForwardPanel.REMOTE_PORT, "3001");
            assertThat(value(panel, ForwardPanel.LOCAL_PORT)).as("an edited local port stays").isEqualTo("9000");
        });
    }

    @Test void fromAnSshPaneItForwardsThroughThatHost() throws Exception {
        edt(() -> {
            var panel = panel(Optional.of(prod));
            assertThat(visible(panel, ForwardPanel.HOST)).isFalse();
            assertThat(node(panel.view(), ForwardPanel.HEADING, Nodes.Text.class).text()).isEqualTo("Forward a port on prod to this computer");
            type(panel.view(), ForwardPanel.REMOTE_PORT, "8080");
            click(panel.view(), ForwardPanel.FORWARD);
            assertThat(forwarded).singleElement().satisfies(tunnel -> {
                assertThat(tunnel.quick()).isTrue();
                assertThat(tunnel.hostId()).isEqualTo(prod.id());
                assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
                assertThat(tunnel.bindAddress()).isEqualTo(Tunnel.LOCALHOST);
                assertThat(tunnel.bindPort()).isEqualTo(8080);
                assertThat(tunnel.targetHost()).isEqualTo("localhost");
                assertThat(tunnel.targetPort()).isEqualTo(8080);
            });
        });
    }

    @Test void elsewhereItAsksForTheHostAndMoreRevealsTheTarget() throws Exception {
        edt(() -> {
            var panel = panel(Optional.empty());
            assertThat(visible(panel, ForwardPanel.HOST)).isTrue();
            assertThat(node(panel.view(), ForwardPanel.HEADING, Nodes.Text.class).text()).isEqualTo("Forward a port on a host to this computer");
            emit(panel.view(), new UiEvent.Selected(ForwardPanel.HOST, Set.of(web.id().toString())));
            assertThat(visible(panel, ForwardPanel.TARGET_HOST)).isFalse();
            click(panel.view(), ForwardPanel.MORE);
            assertThat(visible(panel, ForwardPanel.TARGET_HOST)).isTrue();
            type(panel.view(), ForwardPanel.TARGET_HOST, "10.0.0.7");
            type(panel.view(), ForwardPanel.REMOTE_PORT, "5432");
            click(panel.view(), ForwardPanel.FORWARD);
            assertThat(forwarded.getLast().hostId()).isEqualTo(web.id());
            assertThat(forwarded.getLast().targetHost()).isEqualTo("10.0.0.7");
        });
    }

    @Test void keepingItNeedsAName() throws Exception {
        edt(() -> {
            var panel = panel(Optional.of(prod));
            type(panel.view(), ForwardPanel.REMOTE_PORT, "8080");
            assertThat(enabled(panel, ForwardPanel.SAVE_NAME)).isFalse();
            emit(panel.view(), new UiEvent.Toggled(ForwardPanel.KEEP, true));
            assertThat(node(panel.view(), ForwardPanel.KEEP, Nodes.Toggle.class).checked()).isTrue();
            assertThat(enabled(panel, ForwardPanel.SAVE_NAME)).isTrue();
            assertThat(value(panel, ForwardPanel.SAVE_NAME)).isEqualTo("Port 8080 on prod");
            type(panel.view(), ForwardPanel.SAVE_NAME, " ");
            click(panel.view(), ForwardPanel.FORWARD);
            assertThat(panel.message()).isEqualTo("Name the tunnel to keep it");
            type(panel.view(), ForwardPanel.SAVE_NAME, "web app");
            click(panel.view(), ForwardPanel.FORWARD);
            assertThat(forwarded.getLast().name()).isEqualTo("web app");
        });
    }

    @Test void badPortsAreExplained() throws Exception {
        edt(() -> {
            var panel = panel(Optional.of(prod));
            type(panel.view(), ForwardPanel.REMOTE_PORT, "http");
            click(panel.view(), ForwardPanel.FORWARD);
            assertThat(panel.message()).isEqualTo("The remote port must be a number from 1 to 65535");
            assertThat(node(panel.view(), ForwardPanel.MESSAGE, Nodes.Text.class).text()).isEqualTo("The remote port must be a number from 1 to 65535");
            assertThat(forwarded).isEmpty();
            panel.showError("A tunnel named web app exists");
            assertThat(panel.message()).isEqualTo("A tunnel named web app exists");
        });
    }

    /** The descriptor grows by the target row; the real host's layout growth is asserted in app StagedRemoteTunnelsTest. */
    @Test void moreRevealsAndLessHidesTheTargetRow() throws Exception {
        edt(() -> {
            var panel = panel(Optional.empty());
            long shown = all(panel.view()).stream().filter(n -> n.state().visible()).count();
            assertThat(button(panel.view(), ForwardPanel.MORE).label()).isEqualTo("More ▸");
            click(panel.view(), ForwardPanel.MORE);
            assertThat(button(panel.view(), ForwardPanel.MORE).label()).isEqualTo("Less ▾");
            assertThat(all(panel.view()).stream().filter(n -> n.state().visible()).count()).isGreaterThan(shown);
            click(panel.view(), ForwardPanel.MORE);
            assertThat(button(panel.view(), ForwardPanel.MORE).label()).isEqualTo("More ▸");
            assertThat(all(panel.view()).stream().filter(n -> n.state().visible()).count()).isEqualTo(shown);
        });
    }

    /** Baseline f16ae32f had no default button and no field listeners: Enter forwards nothing; Escape cancels. */
    @Test void noButtonIsPrimaryAndEscapeCancels() throws Exception {
        edt(() -> {
            var panel = panel(Optional.of(prod));
            assertThat(primaries(panel.view())).as("Enter never forwards").isEmpty();
            assertThat(all(panel.view()).stream().filter(n -> n instanceof Nodes.Field f && !f.trailingAction().isEmpty()).toList())
                .as("no field submits").isEmpty();
            assertThat(form(panel.view()).actions()).extracting(Nodes.Button::id).containsExactly(ForwardPanel.CANCEL, ForwardPanel.FORWARD);
            assertThat(form(panel.view()).cancelAction()).isEqualTo(ForwardPanel.CANCEL);
            press(panel.view(), form(panel.view()).cancelAction());
            assertThat(cancelled).isEqualTo(1);
            assertThat(forwarded).isEmpty();
        });
    }

    @Test void aClosedPanelIgnoresALateError() throws Exception {
        edt(() -> {
            var kit = new Kits.RecordingKit("dev.jasper.remote");
            var panel = panel(kit, Optional.of(prod));
            panel.close();
            panel.showError("late failure");
            assertThat(kit.lateCalls).isEmpty();
            assertThat(closed(panel.view())).isTrue();
        });
    }
}
