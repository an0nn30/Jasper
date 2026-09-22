package dev.jasper.sdk.testing;

import dev.jasper.sdk.PluginInfo;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FakePluginHostTest {
    private static final PluginInfo INFO = new PluginInfo("test.alpha", "Alpha", "1.0.0", Set.of());

    @Test void configurationIsTypedNestedObservableAndReportable() {
        try (var host = new FakePluginHost()) {
            List<String> changes = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.config().onChanged(() -> changes.add("changed")));
            host.setConfig("test.alpha", Map.of("name", "x", "count", 3L, "on", true,
                "hosts", List.of("a", "b"), "mixed", List.of("a", 1L), "ssh", Map.of("port", 22L)));
            assertThat(changes).containsExactly("changed");
            var config = context.config();
            assertThat(config.string("name")).hasValue("x");
            assertThat(config.string("count")).isEmpty();
            assertThat(config.integer("count")).hasValue(3L);
            assertThat(config.bool("on")).hasValue(true);
            assertThat(config.stringList("hosts")).containsExactly("a", "b");
            assertThat(config.stringList("mixed")).isEmpty();
            assertThat(config.table("ssh").orElseThrow().integer("port")).hasValue(22L);
            assertThat(config.table("name")).isEmpty();
            config.table("ssh").orElseThrow().report("port", "out of range");
            assertThat(host.reports()).containsExactly("test.alpha: ssh.port: out of range");
        }
    }

    @Test void backgroundWorkWaitsUntilRunAndDataDirectoriesArePerPlugin() {
        try (var host = new FakePluginHost()) {
            List<String> ran = new ArrayList<>();
            var context = host.start(INFO, Set.of(), Set.of(), c -> c.background().execute(() -> ran.add("task")));
            assertThat(ran).isEmpty();
            assertThat(host.runBackground()).isEqualTo(1);
            assertThat(ran).containsExactly("task");
            assertThat(context.dataDirectory()).isDirectory().hasFileName("data");
            assertThat(context.dataDirectory().getParent()).as("the application layout, <plugins>/<id>/data").hasFileName("test.alpha");
            assertThat(Files.isDirectory(context.dataDirectory())).isTrue();
        }
    }
}
