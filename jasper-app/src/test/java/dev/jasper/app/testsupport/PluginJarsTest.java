package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginJarsTest {
    @TempDir Path plugins;

    @Test void buildingAFixtureLeavesNoWorkFolderInTheTempFolder() throws Exception {
        String marker = "leakcheck" + UUID.randomUUID().toString().replace("-", "");
        var before = TempLeaks.names("plugin-fixture");

        Path jar = PluginJars.build(plugins, "probe.jar", PluginJars.descriptor("dev.leakcheck", "1.0.0", marker + ".Probe"),
            Map.of(marker + ".Probe", PluginJars.emptyPlugin(marker, "Probe")), List.of());

        assertThat(jar).isRegularFile();
        assertThat(TempLeaks.leftBehind(before, "plugin-fixture", marker)).isEmpty();
    }

    @Test void aFixtureThatDoesNotCompileLeavesNoWorkFolderEither() {
        String marker = "leakcheck" + UUID.randomUUID().toString().replace("-", "");
        var before = TempLeaks.names("plugin-fixture");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PluginJars.build(plugins, "broken.jar", null,
            Map.of(marker + ".Broken", "package " + marker + "; public final class Broken { void m( }"), List.of()))
            .hasMessageContaining("did not compile");

        assertThat(TempLeaks.leftBehind(before, "plugin-fixture", marker)).isEmpty();
    }
}
