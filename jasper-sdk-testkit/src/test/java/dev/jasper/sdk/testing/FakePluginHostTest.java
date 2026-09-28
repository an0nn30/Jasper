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
