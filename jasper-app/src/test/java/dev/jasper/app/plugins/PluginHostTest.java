package dev.jasper.app.plugins;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.testing.contract.PluginContractTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.*;

class PluginHostTest {
    @TempDir Path data;

    private static HostedPlugin hosted(String id, Set<String> exports, Plugin plugin) {
        return new HostedPlugin(new PluginInfo(id, id, "1.0.0", Set.of()), Set.of(), Set.of(), exports,
            PluginHostTest.class.getClassLoader(), () -> plugin);
    }

    @Test void registrationCallsAreRejectedOffTheUiThread() {
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(200));
        var context = new AtomicReference<PluginContext>();
        onEdt(() -> host.start(hosted("test.alpha", Set.of(), context::set)));
        assertThatIllegalStateException().isThrownBy(() ->
            context.get().events().subscribe(dev.jasper.sdk.events.AppEvents.CONFIG_RELOADED, event -> { }))
            .withMessageContaining("UI thread");
        assertThatIllegalStateException().as("lifetimes belong to the UI thread too")
            .isThrownBy(() -> host.start(hosted("test.beta", Set.of(), started -> { })));
    }

    @Test void reportsStartOutcomesWithReasons() {
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(200));
        var outcomes = new ConcurrentHashMap<String, PluginHost.Outcome>();
        onEdt(() -> {
            outcomes.put("ok", host.start(hosted("test.ok", Set.of(), context -> assertThat(host.executing()).contains("test.ok", "start"))));
            outcomes.put("boom", host.start(hosted("test.boom", Set.of(), context -> { throw new java.io.IOException("disk"); })));
            outcomes.put("entry", host.start(new HostedPlugin(new PluginInfo("test.entry", "e", "1.0.0", Set.of()), Set.of(), Set.of(),
                Set.of(), PluginHostTest.class.getClassLoader(), () -> { throw new NoClassDefFoundError("gone/Missing"); })));
            outcomes.put("needs", host.start(new HostedPlugin(new PluginInfo("test.needs", "n", "1.0.0", Set.of()), Set.of("test.boom"),
                Set.of(), Set.of(), PluginHostTest.class.getClassLoader(), () -> context -> { })));
            outcomes.put("unexported", host.start(hosted("test.unexported", Set.of(),
                context -> context.services().publish(PluginContractTest.Greeter.class, name -> name))));
        });
        assertThat(outcomes.get("ok")).isEqualTo(new PluginHost.Outcome(PluginStatus.State.ACTIVE, ""));
        assertThat(outcomes.get("boom").state()).isEqualTo(PluginStatus.State.FAILED);
        assertThat(outcomes.get("boom").reason()).contains("IOException", "disk");
        assertThat(outcomes.get("entry").reason()).contains("NoClassDefFoundError");
        assertThat(outcomes.get("needs")).isEqualTo(new PluginHost.Outcome(PluginStatus.State.SKIPPED,
            "requires test.boom, which failed to start"));
        assertThat(outcomes.get("unexported").reason()).contains("exported");
        assertThat(host.failures("test.boom")).isEqualTo(1);
        assertThat(host.executing()).isNull();
    }

    @Test void backgroundWorkIsContainedDrainedAndInterruptedAfterTheGrace() throws Exception {
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(150));
        var interrupted = new CountDownLatch(1);
        var running = new CountDownLatch(1);
        var quick = new CountDownLatch(1);
        onEdt(() -> host.start(hosted("test.worker", Set.of(), context -> {
            context.background().execute(() -> { throw new IllegalStateException("contained"); });
            context.background().execute(quick::countDown);
            context.background().execute(() -> {
                running.countDown();
                try { Thread.sleep(60_000); } catch (InterruptedException stop) { interrupted.countDown(); }
            });
        })));
        assertThat(quick.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
        // The drain future is a bound, not a join: it completes once interruption has been requested.
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(host.failures("test.worker")).isEqualTo(1);
    }

    @Test void fixtureCleanupWaitsForWorkerFinallyAfterTheShutdownGrace() throws Exception {
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(20));
        var running = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var resourceClosed = new CountDownLatch(1);
        onEdt(() -> host.start(hosted("test.cleanup", Set.of(), context ->
            context.background().execute(() -> {
                running.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException stopping) {
                    interrupted.countDown();
                    try { release.await(); }
                    catch (InterruptedException failure) { throw new AssertionError(failure); }
                } finally { resourceClosed.countDown(); }
            }))));
        try {
            assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
            var workers = PluginShutdownTestSupport.capture(host);
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            onEdt(() -> pending.set(host.stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(resourceClosed.getCount()).isEqualTo(1);
            var awaiting = new CountDownLatch(1);
            var cleaned = new CompletableFuture<Void>();
            Thread.ofVirtual().start(() -> {
                awaiting.countDown();
                try { workers.await(); cleaned.complete(null); }
                catch (Throwable failure) { cleaned.completeExceptionally(failure); }
            });
            assertThat(awaiting.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> cleaned.get(100, TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            cleaned.get(5, TimeUnit.SECONDS);
            assertThat(resourceClosed.getCount()).isZero();
        } finally { release.countDown(); }
    }

    @Test void stopRunsInReverseOrderAndSettingsChangesReachOnlyChangedPlugins() throws Exception {
        Files.createDirectories(data.resolve("test.first"));
        Files.writeString(data.resolve("test.first/test.first.toml"), "k = \"v\"\n");
        PluginHost host = AppContractTest.host(data, Duration.ofMillis(200));
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        for (String id : List.of("test.first", "test.second")) {
            onEdt(() -> host.start(hosted(id, Set.of(), new Plugin() {
                @Override public void start(PluginContext context) {
                    assertThat(context.dataDirectory()).isDirectory().hasFileName(id);
                    Setting<String> k = context.settings().declare(SettingSpec.of("k", SettingKind.text(), "").label("K"));
                    k.onChanged(value -> order.add("changed:" + id + ":" + value));
                }
                @Override public void stop() { order.add("stop:" + id); }
            })));
        }
        Files.writeString(data.resolve("test.first/test.first.toml"), "k = \"w\"\n");
        onEdt(() -> host.settingsChanged(host.environment.settingsFiles().refresh(true)));
        onEdt(host::stop);
        assertThat(order).containsExactly("changed:test.first:w", "stop:test.second", "stop:test.first");
    }
}
