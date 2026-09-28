package dev.jasper.app.plugins;

import dev.jasper.app.config.ConfigDiagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PluginSettingsTest {
    @Test void typedGettersNestedTablesChangeDetectionAndQualifiedReports() {
        List<ConfigDiagnostic> reports = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        var containment = new Containment(() -> true);
        var file = java.nio.file.Path.of("/tmp/dev.example.tool.toml");
        java.util.function.Function<List<String>, java.util.Optional<PluginSettingsFiles.Position>> positions =
            path -> path.equals(List.of("name")) ? java.util.Optional.of(new PluginSettingsFiles.Position(3, 1)) : java.util.Optional.empty();
        var settings = new PluginSettings("dev.example.tool", file, Map.of("name", "x", "count", 3L, "on", true,
            "hosts", List.of("a"), "proxy", Map.of("port", 22L)), containment, positions, reports::add);
        assertThat(settings.file()).isEqualTo(java.nio.file.Path.of("/tmp/dev.example.tool.toml"));
        assertThat(settings.string("name")).hasValue("x");
        assertThat(settings.string("count")).isEmpty();
        assertThat(settings.integer("count")).hasValue(3L);
        assertThat(settings.bool("on")).hasValue(true);
        assertThat(settings.stringList("hosts")).containsExactly("a");
        assertThat(settings.stringList("name")).isEmpty();
        assertThat(settings.table("proxy").orElseThrow().integer("port")).hasValue(22L);
        assertThat(settings.table("name")).isEmpty();

        var subscription = settings.onChanged(() -> changes.add("changed"));
        settings.onChanged(() -> { throw new IllegalStateException("listener failure"); });
        settings.update(Map.of("name", "x", "count", 3L, "on", true, "hosts", List.of("a"), "proxy", Map.of("port", 22L)));
        assertThat(changes).as("an equal table is not a change").isEmpty();
        settings.update(Map.of("name", "y"));
        assertThat(changes).containsExactly("changed");
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
        assertThat(settings.string("name")).hasValue("y");
        subscription.close();
        settings.update(Map.of());
        assertThat(changes).hasSize(1);

        settings.report("name", "too short");
        new PluginSettings("dev.example.tool", file, Map.of("proxy", Map.of()), containment, positions, reports::add)
            .table("proxy").orElseThrow().report("port", "missing");
        assertThat(reports).extracting(ConfigDiagnostic::file, ConfigDiagnostic::key, ConfigDiagnostic::line, ConfigDiagnostic::message)
            .containsExactly(tuple(file, "name", 3, "too short"), tuple(file, "proxy.port", 0, "missing"));
        settings.onChanged(() -> changes.add("forced"));
        settings.update(Map.of(), true);
        assertThat(changes).as("a forced update reruns listeners on equal values").contains("forced");
    }
}
