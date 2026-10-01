package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ForwardPanelTest {
    final RemoteHost prod = RemoteHost.create("prod", "db.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final RemoteHost web = RemoteHost.create("web", "web.example", 22, "deploy", Auth.AGENT, "", Optional.empty());
    final List<Tunnel> forwarded = new ArrayList<>();
    final Set<Integer> taken = new HashSet<>();

    ForwardPanel panel(Optional<RemoteHost> paneHost) {
        return new ForwardPanel(List.of(prod, web), paneHost, port -> !taken.contains(port), forwarded::add, () -> { });
    }

    @Test void theLocalPortFollowsTheRemoteOneUnlessItIsTakenOrEdited() {
        taken.add(8080);
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("3000");
        assertThat(panel.localPort.getText()).isEqualTo("3000");
        panel.remotePort.setText("8080");
        assertThat(panel.localPort.getText()).as("the next free port").isEqualTo("8081");
        panel.localPort.setText("9000");
        panel.remotePort.setText("3001");
        assertThat(panel.localPort.getText()).as("an edited local port stays").isEqualTo("9000");
    }

    @Test void fromAnSshPaneItForwardsThroughThatHost() {
        var panel = panel(Optional.of(prod));
        assertThat(panel.hostRow.isVisible()).isFalse();
        assertThat(panel.heading.getText()).isEqualTo("Forward a port on prod to this computer");
        panel.remotePort.setText("8080");
        panel.forward.doClick();
        assertThat(forwarded).singleElement().satisfies(tunnel -> {
            assertThat(tunnel.quick()).isTrue();
            assertThat(tunnel.hostId()).isEqualTo(prod.id());
            assertThat(tunnel.kind()).isEqualTo(TunnelKind.LOCAL);
            assertThat(tunnel.bindAddress()).isEqualTo(Tunnel.LOCALHOST);
            assertThat(tunnel.bindPort()).isEqualTo(8080);
            assertThat(tunnel.targetHost()).isEqualTo("localhost");
            assertThat(tunnel.targetPort()).isEqualTo(8080);
        });
    }

    @Test void elsewhereItAsksForTheHostAndMoreRevealsTheTarget() {
        var panel = panel(Optional.empty());
        assertThat(panel.hostRow.isVisible()).isTrue();
        assertThat(panel.heading.getText()).isEqualTo("Forward a port on a host to this computer");
        panel.host.setSelectedItem(web);
        assertThat(panel.moreRow.isVisible()).isFalse();
        panel.more.doClick();
        assertThat(panel.moreRow.isVisible()).isTrue();
        panel.targetHost.setText("10.0.0.7");
        panel.remotePort.setText("5432");
        panel.forward.doClick();
        assertThat(forwarded.getLast().hostId()).isEqualTo(web.id());
        assertThat(forwarded.getLast().targetHost()).isEqualTo("10.0.0.7");
    }

    @Test void keepingItNeedsAName() {
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("8080");
        assertThat(panel.saveName.isEnabled()).isFalse();
        panel.keep.doClick();
        assertThat(panel.saveName.isEnabled()).isTrue();
        assertThat(panel.saveName.getText()).isEqualTo("Port 8080 on prod");
        panel.saveName.setText(" ");
        panel.forward.doClick();
        assertThat(panel.message.getText()).isEqualTo("Name the tunnel to keep it");
        panel.saveName.setText("web app");
        panel.forward.doClick();
        assertThat(forwarded.getLast().name()).isEqualTo("web app");
    }

    @Test void badPortsAreExplained() {
        var panel = panel(Optional.of(prod));
        panel.remotePort.setText("http");
        panel.forward.doClick();
        assertThat(panel.message.getText()).isEqualTo("The remote port must be a number from 1 to 65535");
        assertThat(forwarded).isEmpty();
        panel.showError("A tunnel named web app exists");
        assertThat(panel.message.getText()).isEqualTo("A tunnel named web app exists");
    }
}
