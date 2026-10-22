package dev.jasper.app.plugins;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The staged Remote fixture is ready only once the plugin's start-up work that a stop cannot interrupt has finished: the
 * transfer queue opens its SQLite store at start, and sqlite-jdbc loads a fresh copy of its native library for each plugin
 * class loader, which can take seconds on a loaded machine. A test that stopped the plugin while that load ran waited on
 * it in {@link PluginShutdownTestSupport.Workers#await}, and under load that wait ran past its bound.
 */
class StagedRemoteHostTest {
    @TempDir Path root;

    @Test void theFixtureIsReadyOnlyOnceTheTransferStoreIsOpen() throws Exception {
        try (var remote = new StagedRemoteHost(root, true)) {
            var transfers = remote.plugin.getClass().getDeclaredField("transfers");
            transfers.setAccessible(true);
            Object coordinator = transfers.get(remote.plugin);
            var store = coordinator.getClass().getDeclaredField("store");
            store.setAccessible(true);
            assertThat(store.get(coordinator)).as("the transfer store is open when the fixture returns").isNotNull();
        }
    }
}
