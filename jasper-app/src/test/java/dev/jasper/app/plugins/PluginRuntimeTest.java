package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class PluginRuntimeTest {
    static final String FIXTURE = """
        package fix.probe;
        import dev.jasper.sdk.activity.ActivitySpec;
        import dev.jasper.sdk.events.AppEvents;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            private PluginContext context;
            private static void note(PluginContext context, String name, String text) {
                try { Files.writeString(context.dataDirectory().resolve(name), text); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
            @Override public void start(PluginContext context) {
                this.context = context;
                note(context, "started", context.config().string("greeting").orElse("none"));
                context.config().onChanged(() -> note(context, "changed", context.config().string("greeting").orElse("none")));
                context.events().subscribe(AppEvents.THEME_CHANGED, event -> note(context, "theme", event.variant().name()));
                context.events().subscribe(AppEvents.CONFIG_RELOADED, event -> note(context, "reloaded", "yes"));
                context.activities().begin(ActivitySpec.of("Probe work")).progress(0.5, "half way");
            }
            @Override public void stop() { note(context, "stopped", "yes"); }
        }
        """;

    @TempDir Path root;
    private final BuddyTestSupport deck = new BuddyTestSupport();

    private PluginRuntime runtime(Path bundled, Path user, Path dev, boolean safeMode, List<String> reports) {
        var created = new AtomicReference<PluginRuntime>();
        onEdt(() -> created.set(new PluginRuntime(new PluginRuntime.Options(bundled, user, dev, safeMode,
            root.resolve("plugins.toml"), root.resolve("plugins.lock"), root.resolve("plugin-data")),
            new ActivityNotifier(deck.companion(), () -> { }), (key, message) -> reports.add(key + ": " + message),
            new dev.jasper.app.contributions.Contributions(),
            AppContractTest.headlessWindows())));
        return created.get();
    }

    private static void settle() { for (int i = 0; i < 3; i++) onEdt(() -> { }); }

    private Path probe(Path parent, String id) throws Exception {
        Path directory = parent.resolve(id);
        PluginJars.build(directory, "probe.jar", PluginJars.descriptor(id, "1.0.0", "fix.probe.Main"),
            Map.of("fix.probe.Main", FIXTURE), List.of());
        return directory;
    }

    @Test void runsADevelopmentPluginBridgesItsActivityAndStopsIt() throws Exception {
        Path dev = probe(root.resolve("dev"), "dev.example.probe");
        PluginRuntime runtime = runtime(null, root.resolve("absent"), dev, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of("dev.example.probe", Map.<String, Object>of("greeting", "hello")), true));
        settle();
        Path data = root.resolve("plugin-data/dev.example.probe");
        assertThat(data.resolve("started")).hasContent("hello");
        assertThat(runtime.statusLines()).singleElement().asString().contains("dev.example.probe", "DEV", "ACTIVE");
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.example.probe");
            assertThat(notice.title()).isEqualTo("Probe work");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.detail().get()).isEqualTo("50% · half way");
        }));

        onEdt(() -> runtime.configurationChanged(Map.of("dev.example.probe", Map.<String, Object>of("greeting", "again"))));
        onEdt(() -> runtime.themeChanged(false));
        settle();
        assertThat(data.resolve("changed")).hasContent("again");
        assertThat(data.resolve("reloaded")).hasContent("yes");
        assertThat(data.resolve("theme")).hasContent("LIGHT");

        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        settle();
        assertThat(data.resolve("stopped")).hasContent("yes");
        onEdt(() -> assertThat(deck.notices()).singleElement()
            .satisfies(notice -> assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED)));
    }

    @Test void userPluginsWaitForConsentAndSafeModeSkipsThem() throws Exception {
        Path user = root.resolve("user");
        probe(user, "dev.example.probe");
        PluginRuntime unreviewed = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> unreviewed.start(Map.of(), true));
        assertThat(unreviewed.statusLines()).singleElement().asString().contains("NEEDS_CONSENT");
        assertThat(root.resolve("plugin-data/dev.example.probe/started")).doesNotExist();

        Files.writeString(root.resolve("plugins.toml"), "version = 1\n[plugins.\"dev.example.probe\"]\nenabled = true\nconsented = []\n");
        PluginRuntime consented = runtime(null, user, null, false, new ArrayList<>());
        onEdt(() -> consented.start(Map.of(), true));
        assertThat(consented.statusLines()).singleElement().asString().contains("ACTIVE");
        onEdt(consented::stop);

        PluginRuntime safe = runtime(null, user, null, true, new ArrayList<>());
        onEdt(() -> safe.start(Map.of(), true));
        assertThat(safe.statusLines()).singleElement().asString().contains("DISABLED", "safe mode");
    }

    @Test void aPluginThatCannotLoadIsReportedAndTheRestStillStart() throws Exception {
        Path bundled = root.resolve("bundled");
        probe(bundled, "dev.example.probe");
        PluginJars.build(bundled.resolve("dev.example.broken"), "broken.jar",
            PluginJars.descriptor("dev.example.broken", "1.0.0", "fix.broken.Missing"), Map.of(), List.of());
        PluginRuntime runtime = runtime(bundled, root.resolve("absent"), null, false, new ArrayList<>());
        onEdt(() -> runtime.start(Map.of(), true));
        assertThat(runtime.statusLines()).hasSize(2)
            .anySatisfy(line -> assertThat(line).contains("dev.example.broken", "FAILED", "fix.broken.Missing"))
            .anySatisfy(line -> assertThat(line).contains("dev.example.probe", "ACTIVE"));
        onEdt(runtime::stop);
    }

    @Test void theBundledDirectorySitsBesideTheApplicationJarUnlessOverridden() throws Exception {
        Path jar = Files.createFile(root.resolve("jasper-app.jar"));
        assertThat(PluginRuntime.bundledDirectory(jar)).isEqualTo(root.resolve("plugins"));
        assertThat(PluginRuntime.bundledDirectory(root)).as("classes directory during development").isNull();
        assertThat(PluginRuntime.bundledDirectory(null)).isNull();
    }
}
