package dev.jasper.indexer;

import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerPluginTest {
    @TempDir Path root;

    @Test void startsAndStopsCleanly() {
        try (var host = new FakePluginHost(root.resolve("plugins"))) {
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), new IndexerPlugin());
            assertThat(host.active(IndexerPlugin.ID)).isTrue();
            assertThat(host.failures()).isEmpty();
        }
    }
}
