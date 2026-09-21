package dev.jasper.app.plugins;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

/** The real sample jar, staged the way the application image bundles it, through the real runtime to Buddy. */
class BundledSamplePluginTest {
    @TempDir Path root;

    @Test void theStagedSampleLoadsFromItsJarAndItsActivityReachesBuddy() throws Exception {
        Path staged = Path.of(System.getProperty("jasper.stagedPlugins"));
        assertThat(staged.resolve("dev.jasper.sample")).isDirectory();
        var deck = new BuddyTestSupport();
        var runtime = new AtomicReference<PluginRuntime>();
        dev.jasper.app.contributions.Contributions contributions = AppContractTest.onEdtValue(dev.jasper.app.contributions.Contributions::new);
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(staged, root.resolve("user"), null, false,
                root.resolve("plugins.toml"), root.resolve("plugins.lock"), root.resolve("plugin-data")),
                new ActivityNotifier(deck.companion(), () -> { }), (key, message) -> { }, contributions));
            runtime.get().start(Map.of("dev.jasper.sample", Map.<String, Object>of("demo_activity", true, "demo_step_millis", 0L)), true);
        });
        assertThat(runtime.get().statusLines()).singleElement().asString()
            .contains("dev.jasper.sample", "0.1.0", "BUNDLED", "ACTIVE");
        var state = new AtomicReference<BuddyNotice.State>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (state.get() != BuddyNotice.State.DONE && System.nanoTime() < deadline) {
            onEdt(() -> state.set(deck.notices().isEmpty() ? null : deck.notices().get(0).state()));
            Thread.sleep(20);
        }
        onEdt(() -> assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id().source()).isEqualTo("dev.jasper.sample");
            assertThat(notice.title()).isEqualTo("Sample plugin");
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DONE);
            assertThat(notice.detail().get()).isEqualTo("Ready");
        }));
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(runtime.get().stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
    }
}
