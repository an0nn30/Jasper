package dev.jasper.indexer;

import dev.jasper.indexer.detect.Markers;
import dev.jasper.indexer.testsupport.IndexerTesting;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexerSettingsTest {
    @TempDir Path root;

    @Test void declaresEverySettingWithItsDefault() {
        try (var host = new FakePluginHost(root)) {
            var settings = new AtomicReference<IndexerSettings>();
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> settings.set(new IndexerSettings(context.settings())));
            assertThat(host.declared(IndexerPlugin.ID))
                .containsExactly("prompt", "markers", "excludes", "ignored", "max_files", "refresh_minutes");
            assertThat(settings.get().prompt.get()).isTrue();
            assertThat(settings.get().markers.get()).isEqualTo(Markers.DEFAULTS);
            assertThat(settings.get().excludes.get()).isEqualTo(IndexerSettings.DEFAULT_EXCLUDES).contains("node_modules", "target");
            assertThat(settings.get().ignored.get()).isEmpty();
            assertThat(settings.get().maxFiles.get()).isEqualTo(200_000);
            assertThat(settings.get().refreshMinutes.get()).isEqualTo(5);
            assertThat(settings.get().markers().strong()).containsExactly(".git", ".hg", ".svn", "*.sln");
        }
    }

    @Test void readsEditedValuesAndRejectsOutOfRangeNumbers() {
        try (var host = new FakePluginHost(root)) {
            var settings = new AtomicReference<IndexerSettings>();
            host.start(IndexerTesting.INFO, Set.of(), Set.of(), context -> settings.set(new IndexerSettings(context.settings())));
            host.setSetting(IndexerPlugin.ID, "markers", List.of("!.git", "WORKSPACE"));
            host.setSetting(IndexerPlugin.ID, "max_files", 5L);
            host.setSetting(IndexerPlugin.ID, "excludes", List.of("a/b"));
            host.flush();
            assertThat(settings.get().markers()).isEqualTo(new Markers(List.of(".git"), List.of("WORKSPACE")));
            assertThat(settings.get().maxFiles.get()).as("below 1,000 falls back").isEqualTo(200_000);
            assertThat(settings.get().excludes.get()).as("a folder name has no slash").isEqualTo(IndexerSettings.DEFAULT_EXCLUDES);
            assertThat(host.settingsReports(IndexerPlugin.ID)).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
