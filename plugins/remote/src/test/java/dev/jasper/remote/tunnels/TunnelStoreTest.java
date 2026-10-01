package dev.jasper.remote.tunnels;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class TunnelStoreTest {
    static final UUID HOST = UUID.randomUUID();
    final Deque<Runnable> background = new ArrayDeque<>();
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    TunnelStore store(Path dir) {
        var store = new TunnelStore(dir.resolve("tunnels.toml"), background::add, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    static void touch(Path file, int seconds) throws Exception {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(seconds)));
    }

    @Test void loadsSavesAndNoticesOutsideEdits(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        run();
        assertThat(store.tunnels()).isEmpty();
        assertThat(store.error()).isEmpty();
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
        CompletableFuture<Void> saved = store.put(db);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.tunnels()).containsExactly(db);
        assertThat(store.tunnel(db.id())).contains(db);
        assertThat(changes).isEqualTo(2);
        Path file = dir.resolve("tunnels.toml");
        assertThat(Files.readString(file)).startsWith(TunnelFile.HEADER).contains("name = \"db\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(file); }

        Tunnel socks = Tunnel.create("socks", HOST, TunnelKind.DYNAMIC, Tunnel.LOCALHOST, 1080, "", 0);
        Files.writeString(file, TunnelFile.format(List.of(db, socks)));
        touch(file, 5);
        store.poll();
        run();
        assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("db", "socks");
        store.remove(db.id());
        run();
        assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("socks");
    }

    @Test void aBrokenFileKeepsTheLastGoodTunnelsAndRefusesSaves(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        Tunnel db = Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db.internal", 5432);
        store.put(db);
        run();
        Path file = dir.resolve("tunnels.toml");
        Files.writeString(file, "[[tunnel]\nname = ");
        touch(file, 5);
        store.poll();
        run();
        assertThat(store.error()).get().asString().startsWith("tunnels.toml has TOML errors");
        assertThat(store.tunnels()).containsExactly(db);
        CompletableFuture<Void> refused = store.put(Tunnel.create("other", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThat(refused).isCompletedExceptionally();
        assertThatThrownBy(refused::join).hasMessageContaining("fix it before saving");
        Files.writeString(file, TunnelFile.format(List.of(db)));
        touch(file, 10);
        store.poll();
        run();
        assertThat(store.error()).isEmpty();
    }

    @Test void aDuplicateNameIsRefusedAndTheFileIsLeftAlone(@TempDir Path dir) throws Exception {
        TunnelStore store = store(dir);
        store.load();
        store.put(Tunnel.create("db", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 5432, "db", 5432));
        run();
        String before = Files.readString(dir.resolve("tunnels.toml"));
        CompletableFuture<Void> refused = store.put(Tunnel.create("DB", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThatThrownBy(refused::join).hasMessageContaining("A tunnel named DB exists");
        assertThat(Files.readString(dir.resolve("tunnels.toml"))).isEqualTo(before);
    }
}
