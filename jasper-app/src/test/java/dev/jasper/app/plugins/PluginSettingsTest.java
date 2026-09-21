package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PluginSettingsTest {
    @Test void typedGettersNestedTablesChangeDetectionAndQualifiedReports() {
        List<String> reports = new ArrayList<>();
        List<String> changes = new ArrayList<>();
        var containment = new Containment(() -> true);
        var settings = new PluginSettings("dev.example.tool", Map.of("name", "x", "count", 3L, "on", true,
            "hosts", List.of("a"), "proxy", Map.of("port", 22L)), containment, (key, message) -> reports.add(key + "=" + message));
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
        new PluginSettings("dev.example.tool", Map.of("proxy", Map.of()), containment, (key, message) -> reports.add(key + "=" + message))
            .table("proxy").orElseThrow().report("port", "missing");
        assertThat(reports).containsExactly("plugins.\"dev.example.tool\".name=too short",
            "plugins.\"dev.example.tool\".proxy.port=missing");
    }
}
