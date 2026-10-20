package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.hosts.Auth;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.tunnels.Tunnel;
import dev.jasper.remote.tunnels.TunnelKind;
import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.testing.FakeKit;
import dev.jasper.sdk.ui.Nodes;
import dev.jasper.sdk.ui.UiEvent;
import dev.jasper.sdk.ui.UiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static dev.jasper.remote.testsupport.Kits.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descriptor-scenario evidence for the migrated Tunnels panel and dialogs (Plan 2 Task 8b, controller ruling G10,
 * following Task 7 ruling 2): the states the frozen pre-migration captures recorded (empty, stopped, busy, failed) and
 * the two dialogs, asserted as kit descriptors with synthetic data and no networking, written under
 * build/theme-migration-evidence/remote-tunnels-descriptors. Pixels of the real host rendering come from the app-side
 * RemoteRenderEvidenceTest. The frozen before images are untouched.
 */
class ThemeMigrationEvidenceTest {
    static final Path OUTPUT = Path.of("build/theme-migration-evidence/remote-tunnels-descriptors");

    static void save(String state, UiView view) throws Exception {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(state + ".txt"), view.spec().toString() + "\n");
    }

    @Test void describesEmptyStoppedBusyAndFailedTunnelRowsAndTheDialogs() throws Exception {
        edt(() -> {
            var fixture = new TunnelsPanelTest();
            var panel = fixture.panel();
            panel.setTunnels(List.of(), List.of(), Optional.empty(), id -> TunnelState.STOPPED, id -> Optional.empty());
            save("empty", panel.view());
            assertThat(find(panel.view(), TunnelsPanel.EMPTY)).isPresent();
            fixture.show(); save("stopped", panel.view());
            assertThat(((Nodes.StatusDot) fixture.row(fixture.db).leading().orElseThrow()).kind()).isEqualTo(Nodes.StatusKind.IDLE);
            fixture.states.put(fixture.db.id(), new TunnelState.Starting("Connecting")); fixture.show();
            save("busy", panel.view());
            assertThat(fixture.switchOf(fixture.db).pending()).isTrue();
            fixture.states.put(fixture.db.id(), new TunnelState.Failed("Fixture connection refused")); fixture.show();
            save("failed", panel.view());
            assertThat(fixture.switchOf(fixture.db).state().error()).isEqualTo("Fixture connection refused");
            emit(panel.view(), new UiEvent.Edited(TunnelsPanel.FILTER, "no such tunnel"));
            save("filtered-empty", panel.view());
            assertThat(node(panel.view(), TunnelsPanel.NO_MATCH, Nodes.Text.class).text()).isEqualTo("No tunnels match");

            var kit = new FakeKit("dev.jasper.remote");
            var host = RemoteHost.create("fixture", "fixture.example.invalid", 22, "fixture-user", Auth.AGENT, "", Optional.empty());
            var editor = new TunnelEditor(kit, List.of(host), List.of(), Optional.empty(), Optional.empty(), tunnel -> { }, () -> { });
            save("new-tunnel", editor.view());
            click(editor.view(), TunnelEditor.SAVE);
            assertThat(editor.message()).isNotBlank();
            save("new-tunnel-invalid", editor.view());
            var existing = Tunnel.create("fixture", host.id(), TunnelKind.REMOTE, Tunnel.ALL, 8080, "localhost", 3000);
            save("edit-remote-tunnel", new TunnelEditor(kit, List.of(host), List.of(existing), Optional.of(existing), Optional.empty(), tunnel -> { }, () -> { }).view());
            var forward = new ForwardPanel(kit, List.of(host), Optional.empty(), port -> true, tunnel -> { }, () -> { });
            save("forward-port", forward.view());
            click(forward.view(), ForwardPanel.MORE);
            save("forward-port-more", forward.view());
            try (var saved = Files.list(OUTPUT)) { assertThat(saved.count()).isGreaterThanOrEqualTo(9); }
        });
    }
}
