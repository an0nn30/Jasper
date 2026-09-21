package dev.jasper.app.plugins;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PluginStateStoreTest {
    @TempDir Path dir;

    private PluginStateStore store(Duration wait) {
        return new PluginStateStore(dir.resolve("plugins.toml"), dir.resolve("plugins.lock"), wait);
    }

    @Test void absentFileReadsAsEmptyAndTransactionsRoundTrip() throws Exception {
        PluginStateStore store = store(Duration.ofSeconds(2));
        assertThat(store.read()).isEmpty();
        store.transact(state -> {
            state.put("dev.example.tool", new PluginStateStore.Entry(false, Set.of("terminal.inject", "terminal.observe"), true));
            return state;
        });
        assertThat(store.read()).containsExactly(Map.entry("dev.example.tool",
            new PluginStateStore.Entry(false, Set.of("terminal.inject", "terminal.observe"), true)));
        assertThat(Files.readString(dir.resolve("plugins.toml"))).isEqualTo("""
            version = 1

            [plugins."dev.example.tool"]
            enabled = false
            consented = ["terminal.inject", "terminal.observe"]
            remove = true
            """);
    }

    @Test void concurrentEditorsOfDifferentEntriesBothSurvive() throws Exception {
        int writers = 8;
        var start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < writers; i++) {
            String id = "dev.example.p" + i;
            threads.add(Thread.ofPlatform().start(() -> {
                try {
                    start.await();
                    store(Duration.ofSeconds(10)).transact(state -> { state.put(id, PluginStateStore.Entry.DEFAULT); return state; });
                } catch (Throwable failure) { failures.add(failure); }
            }));
        }
        start.countDown();
        for (Thread thread : threads) thread.join();
        assertThat(failures).isEmpty();
        assertThat(store(Duration.ofSeconds(2)).read()).hasSize(writers);
    }

    @Test void aHeldLockFailsTheTransactionAndWritesNothing() throws Exception {
        PluginStateStore store = store(Duration.ofMillis(150));
        store.transact(state -> { state.put("a.b", PluginStateStore.Entry.DEFAULT); return state; });
        String before = Files.readString(dir.resolve("plugins.toml"));
        try (FileChannel other = FileChannel.open(dir.resolve("plugins.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = other.lock()) {
            assertThatThrownBy(() -> store.transact(state -> { state.clear(); return state; }))
                .isInstanceOf(IOException.class).hasMessageContaining("lock");
        }
        assertThat(Files.readString(dir.resolve("plugins.toml"))).isEqualTo(before);
    }

    @Test void malformedStateIsAnErrorRatherThanSilentlyEmpty() throws Exception {
        Files.writeString(dir.resolve("plugins.toml"), "version = 1\n[plugins.\"a.b\"]\nenabled = \"yes\"\n");
        assertThatThrownBy(() -> store(Duration.ofSeconds(1)).read()).isInstanceOf(IOException.class);
    }
}
