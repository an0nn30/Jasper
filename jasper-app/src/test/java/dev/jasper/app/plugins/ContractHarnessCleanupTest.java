package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.TempLeaks;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The application's contract harness keeps plugin data in a folder of its own and removes it when it closes. */
class ContractHarnessCleanupTest {
    @Test void closingTheHarnessRemovesItsDataFolderFromTheTempFolder() {
        String pluginId = "dev.leakcheck" + UUID.randomUUID().toString().replace("-", "");
        var before = TempLeaks.names("jasper-contract");

        try (var harness = new AppContractTest().newHarness()) {
            // The saved setting puts <data folder>/<plugin id>/<plugin id>.toml on disk, which names the folder.
            harness.setSetting(pluginId, "key", "value");
            assertThat(TempLeaks.leftBehind(before, "jasper-contract", pluginId)).as("while open").hasSize(1);
        }

        assertThat(TempLeaks.leftBehind(before, "jasper-contract", pluginId)).as("after close").isEmpty();
    }
}
