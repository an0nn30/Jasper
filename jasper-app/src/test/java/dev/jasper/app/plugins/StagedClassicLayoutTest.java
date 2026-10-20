package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.EnterpriseLook;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Under the Enterprise presentation a staged consumer composes CLASSIC, and ROWS under the default look. */
class StagedClassicLayoutTest {
    @TempDir Path root;

    @Test void aStagedConsumerReadsTheClassicLayoutUnderEnterpriseAndRowsWithout() throws Exception {
        var controller = AppContractTest.onEdtValue(() -> EnterpriseLook.controller(false));
        try {
            try (var classic = new PanelSurfaceFixture(root.resolve("classic"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, true)) {
                assertThat(classic.layout()).isEqualTo("CLASSIC");
            }
            try (var rows = new PanelSurfaceFixture(root.resolve("rows"), EnterpriseLook.resolved(false), PanelSurfaceFixture.TUNNELS, false)) {
                assertThat(rows.layout()).isEqualTo("ROWS");
            }
        } finally {
            AppContractTest.onEdt(() -> { controller.close(); EnterpriseLook.restoreDefault(); });
        }
    }
}
