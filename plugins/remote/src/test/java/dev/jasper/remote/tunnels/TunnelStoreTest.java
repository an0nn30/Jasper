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
import java.util.concurrent.CompletionException;
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
        assertThat(store.error()).get().asString().as("the bare reason, the panel adds the prefix").contains("line 1").doesNotContain("tunnels.toml").doesNotContain("TOML errors");
        assertThat(store.tunnels()).containsExactly(db);
        CompletableFuture<Void> refused = store.put(Tunnel.create("other", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThat(refused).isCompletedExceptionally();
        assertThatThrownBy(refused::join).hasMessageContaining("tunnels.toml has errors; fix it before saving: ").hasMessageContaining("line 1")
            .hasMessageNotContaining("TOML errors");
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

    private static String idless(String name) {
        return "\n[[tunnel]]\nname = \"" + name + "\"\nhost = \"" + HOST + "\"\nbind_port = 3000\ntarget_host = \"localhost\"\ntarget_port = 3000\n";
    }

    @Test void anEntryWithoutAnIdKeepsOneIdentityThroughEditDeleteAndUnrelatedSaves(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tunnels.toml");
        Files.writeString(file, TunnelFile.HEADER + idless("a") + idless("b"));
        TunnelStore store = store(dir);
        store.load();
        run();
        Tunnel a = store.tunnels().get(0);
        Tunnel b = store.tunnels().get(1);
        assertThat(a.name()).isEqualTo("a");
        Tunnel edited = a.withEdited("a", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 9191, "localhost", 3000);
        CompletableFuture<Void> edit = store.put(edited);
        run();
        assertThat(edit).as("edit").isCompleted();
        assertThat(store.tunnels()).extracting(Tunnel::id, Tunnel::name, Tunnel::bindPort).containsExactly(
            tuple(a.id(), "a", 9191), tuple(b.id(), "b", 3000));
        Tunnel other = Tunnel.create("c", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 3001, "localhost", 3001);
        store.put(other);
        run();
        assertThat(store.tunnels()).extracting(Tunnel::id).as("b is unchanged by an unrelated save").containsExactly(a.id(), b.id(), other.id());
        store.load();
        run();
        assertThat(store.tunnels()).extracting(Tunnel::id).as("a re-read finds the same ids").containsExactly(a.id(), b.id(), other.id());
        CompletableFuture<Void> removal = store.remove(b.id());
        run();
        assertThat(removal).as("delete").isCompleted();
        assertThat(store.tunnels()).extracting(Tunnel::name).containsExactly("a", "c");
    }

    @Test void aFileWithASkippedEntryRefusesSavesAndStaysUnchanged(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tunnels.toml");
        String text = TunnelFile.HEADER + idless("good") + "\n[[tunnel]]\nname = \"bad\"\nhost = \"" + HOST
            + "\"\nbind_port = 70000\ntarget_host = \"x\"\ntarget_port = 1\n";
        Files.writeString(file, text);
        TunnelStore store = store(dir);
        store.load();
        run();
        assertThat(store.warnings()).hasSize(1);
        assertThat(store.tunnels()).hasSize(1);
        CompletableFuture<Void> refused = store.put(Tunnel.create("more", HOST, TunnelKind.LOCAL, Tunnel.LOCALHOST, 1, "x", 1));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class)
            .hasMessageContaining("Remote cannot read").hasMessageContaining("tunnel 2");
        CompletableFuture<Void> removal = store.remove(store.tunnels().getFirst().id());
        run();
        assertThatThrownBy(removal::join).isInstanceOf(CompletionException.class).hasMessageContaining("cannot read");
        assertThat(Files.readString(file)).isEqualTo(text);
    }

    @Test void anUnreadableOrOversizeFileReportsABareReason(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("tunnels.toml");
        Files.write(file, new byte[TunnelFile.MAX_BYTES + 1]);
        TunnelStore store = store(dir);
        store.load();
        run();
        assertThat(store.error()).contains("it is larger than " + TunnelFile.MAX_BYTES + " bytes");
        Files.delete(file);
        Files.createDirectory(file);
        store.load();
        run();
        assertThat(store.error()).get().asString().startsWith("it cannot be read (").endsWith(")");
    }
}
