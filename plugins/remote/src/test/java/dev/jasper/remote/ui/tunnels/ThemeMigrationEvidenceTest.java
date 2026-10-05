package dev.jasper.remote.ui.tunnels;

import dev.jasper.remote.tunnels.TunnelState;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesEmptyStoppedBusyAndFailedTunnelRows() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var fixture = new TunnelsPanelTest();
            save("remote-tunnels", theme, "empty", fixture.panel, 460, 420);
            fixture.show(); save("remote-tunnels", theme, "stopped", fixture.panel, 460, 420);
            fixture.states.put(fixture.db.id(), new TunnelState.Starting("Connecting")); fixture.show();
            save("remote-tunnels", theme, "busy", fixture.panel, 460, 420);
            fixture.states.put(fixture.db.id(), new TunnelState.Failed("Fixture connection refused")); fixture.show();
            save("remote-tunnels", theme, "failed", fixture.panel, 460, 420);
        });
    }
}
