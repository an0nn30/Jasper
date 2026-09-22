package dev.jasper.snippets;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.events.AppEvents;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.snippets.api.SnippetService;
import dev.jasper.snippets.api.SnippetView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SnippetsPluginTest {
    @TempDir Path home;

    @Test void theServiceLooksUpAppendsAndReportsDuplicatesAndTheActionOpensTheScope() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var consumer = new AtomicReference<PluginContext>();
            host.start(SnippetsScopeTest.INFO, Set.of(), Set.of(), new SnippetsPlugin());
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.snippets"), Set.of(), consumer::set);
            SnippetService service = consumer.get().services().require(SnippetService.class);
            assertThat(service.byName("deploy")).isEmpty();
            var saved = new AtomicReference<Optional<SnippetView>>(); var error = new AtomicReference<Optional<String>>();
            service.append("Deploy", "make deploy", (view, message) -> { saved.set(view); error.set(message); });
            SnippetsScopeTest.await(() -> saved.get() != null);
            assertThat(error.get()).isEmpty();
            assertThat(saved.get()).contains(new SnippetView("Deploy", "make deploy", java.util.List.of()));
            assertThat(service.byName("DEPLOY")).as("case-insensitive").contains(saved.get().get());
            assertThat(Files.readString(home.resolve("plugins/dev.jasper.snippets/data/snippets.toml"))).startsWith(SnippetFile.HEADER).contains("name = \"Deploy\"");
            error.set(null);
            service.append("deploy", "again", (view, message) -> { saved.set(view); error.set(message); });
            SnippetsScopeTest.await(() -> error.get() != null);
            assertThat(error.get()).contains("A snippet named Deploy exists");
            assertThat(SnippetService.rowId("Deploy")).isEqualTo(SnippetService.rowId("deploy")).startsWith("snippet.");
            UUID window = host.addTerminalWindow();
            assertThat(host.invoke("dev.jasper.snippets.open", window, null)).isTrue();
            assertThat(host.paletteOpens()).containsExactly(window + " dev.jasper.snippets.scope - -");
        }
    }

    @Test void thePrePluginFileIsMovedOnceAndAConfigReloadRereadsTheFile() throws Exception {
        Path legacy = home.resolve("snippets.toml");
        Files.writeString(legacy, "[[snippet]]\nname = \"Old\"\ncommand = \"ls\"\n");
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var consumer = new AtomicReference<PluginContext>();
            host.start(SnippetsScopeTest.INFO, Set.of(), Set.of(), new SnippetsPlugin());
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.snippets"), Set.of(), consumer::set);
            Path moved = home.resolve("plugins/dev.jasper.snippets/data/snippets.toml");
            assertThat(moved).exists();
            assertThat(legacy).doesNotExist();
            SnippetService service = consumer.get().services().require(SnippetService.class);
            SnippetsScopeTest.await(() -> service.byName("Old").isPresent());
            Files.writeString(moved, "[[snippet]]\nname = \"New\"\ncommand = \"pwd\"\n");
            host.publishApp(AppEvents.CONFIG_RELOADED, new AppEvents.ConfigReloaded());
            host.flush();
            SnippetsScopeTest.await(() -> service.byName("New").isPresent());
            assertThat(service.byName("Old")).isEmpty();
        }
    }
}
