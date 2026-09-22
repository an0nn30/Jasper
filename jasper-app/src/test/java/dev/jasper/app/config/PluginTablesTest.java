package dev.jasper.app.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PluginTablesTest {
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(Path.of("config.toml"), text, true); }

    @Test void quotedPluginTablesAreKeptVerbatimWithoutUnknownKeyWarnings() {
        var result = parse("""
            [plugins."dev.jasper.ssh"]
            default_user = "dustin"
            port = 22
            keepalive = true
            ratio = 0.5
            hosts = ["a", "b"]

            [plugins."dev.jasper.ssh".proxy]
            host = "bastion"

            [plugins."dev.example.absent"]
            anything = "goes"
            """);
        assertThat(result.diagnostics()).as("only the moved-to-a-file warning, one per table")
            .allSatisfy(d -> assertThat(d.message()).contains("moved to plugins/")).hasSize(2);
        assertThat(result.rejected()).isFalse();
        Map<String, Object> ssh = result.snapshot().plugins().get("dev.jasper.ssh");
        assertThat(ssh).containsEntry("default_user", "dustin").containsEntry("port", 22L)
            .containsEntry("keepalive", true).containsEntry("ratio", 0.5).containsEntry("hosts", List.of("a", "b"))
            .containsEntry("proxy", Map.of("host", "bastion"));
        assertThat(result.snapshot().plugins()).containsKey("dev.example.absent");
        assertThatThrownBy(() -> ssh.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void malformedIdsAndUnsupportedValuesWarnAndAreIgnored() {
        var result = parse("""
            [plugins."Not An Id"]
            a = 1

            [plugins."dev.example.tool"]
            mixed = ["a", 1]
            when = 1979-05-27
            fine = "yes"
            """);
        assertThat(result.snapshot().plugins()).containsOnlyKeys("dev.example.tool");
        assertThat(result.snapshot().plugins().get("dev.example.tool")).containsOnlyKeys("fine");
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
            .containsExactlyInAnyOrder("plugins.\"Not An Id\"", "plugins.\"dev.example.tool\"", "plugins.\"dev.example.tool\".mixed",
                "plugins.\"dev.example.tool\".when");
    }

    @Test void defaultsAndBuilderCarryAnEmptyOrGivenPluginMap() {
        assertThat(ConfigSnapshot.defaults().plugins()).isEmpty();
        var tables = Map.of("a.b", Map.<String, Object>of("k", "v"));
        assertThat(ConfigSnapshot.builder().plugins(tables).build().plugins()).isEqualTo(tables);
        assertThat(ConfigSnapshot.builder().plugins(tables).build().toBuilder().tabHeight(40).build().plugins()).isEqualTo(tables);
    }
}
