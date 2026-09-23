package dev.jasper.remote.hosts;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class HostStoreTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    HostStore store(Path dir) {
        var store = new HostStore(dir.resolve("hosts.toml"), queued, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    @Test void loadsSavesAndNoticesOutsideEdits(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        store.load();
        run();
        assertThat(store.hosts()).isEmpty();
        assertThat(store.error()).isEmpty();
        RemoteHost a = RemoteHost.create("a", "a.example", 22, "u", Auth.AGENT, "G", Optional.empty());
        CompletableFuture<Void> saved = store.put(a);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.hosts()).containsExactly(a);
        assertThat(changes).isEqualTo(2);
        assertThat(Files.readString(dir.resolve("hosts.toml"))).contains("name = \"a\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(dir.resolve("hosts.toml")); }
        RemoteHost edited = a.withEdited("a", "b.example", 22, "u", Auth.AGENT, "G", Optional.empty());
        store.put(edited);
        run();
        assertThat(store.host(a.id())).map(RemoteHost::hostname).contains("b.example");
        // An outside edit: the poll sees a new size or time and re-reads.
        Files.writeString(dir.resolve("hosts.toml"), HostFile.format(List.of(a, RemoteHost.create("z", "z", 22, "u", Auth.AGENT, "", Optional.empty()))));
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(5)));
        store.poll();
        run();
        assertThat(store.hosts()).extracting(RemoteHost::name).containsExactly("a", "z");
        store.remove(a.id());
        run();
        assertThat(store.hosts()).extracting(RemoteHost::name).containsExactly("z");
    }

    @Test void aBrokenFileKeepsTheLastGoodHostsAndRefusesSaves(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        store.put(a);
        run();
        Files.writeString(dir.resolve("hosts.toml"), "[[host]\nbroken");
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(5)));
        store.poll();
        run();
        assertThat(store.hosts()).containsExactly(a);
        assertThat(store.error()).isPresent().get().asString().contains("TOML");
        CompletableFuture<Void> refused = store.put(RemoteHost.create("b", "b", 22, "u", Auth.AGENT, "", Optional.empty()));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class).hasMessageContaining("hosts.toml has errors");
        assertThat(store.hosts()).containsExactly(a);
        Files.writeString(dir.resolve("hosts.toml"), HostFile.format(List.of(a)));
        Files.setLastModifiedTime(dir.resolve("hosts.toml"), FileTime.from(Instant.now().plusSeconds(10)));
        store.poll();
        run();
        assertThat(store.error()).isEmpty();
    }

    @Test void saveValidatesBeforeWriting(@TempDir Path dir) {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        RemoteHost b = RemoteHost.create("b", "b", 22, "u", Auth.AGENT, "", Optional.of(UUID.randomUUID()));
        CompletableFuture<Void> refused = store.save(List.of(a, b));
        run();
        assertThatThrownBy(refused::join).hasMessageContaining("jump host missing");
        assertThat(dir.resolve("hosts.toml")).doesNotExist();
    }
    @Test void queuedMutationsComposeWithoutLosingEarlierEdits(@TempDir Path dir) {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        RemoteHost b = RemoteHost.create("b", "b", 22, "u", Auth.AGENT, "", Optional.empty());
        store.put(a); store.put(b); run();
        assertThat(store.hosts()).containsExactly(a, b);
        store.put(a.withFavorite(true)); store.remove(b.id()); run();
        assertThat(store.hosts()).singleElement().satisfies(h -> assertThat(h.favorite()).isTrue());
    }

    @Test void saveChecksDiskBeforeOverwritingAnUnpolledBrokenEdit(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        store.put(a); run();
        Files.writeString(dir.resolve("hosts.toml"), "[[broken");
        var saved = store.put(a.withFavorite(true)); run();
        assertThatThrownBy(saved::join).hasMessageContaining("hosts.toml has errors");
        assertThat(Files.readString(dir.resolve("hosts.toml"))).isEqualTo("[[broken");
        assertThat(store.error()).isPresent();
    }

    @Test void unreadableFileIsReportedAndKeepsLastGoodHosts(@TempDir Path dir) throws Exception {
        HostStore store = store(dir);
        RemoteHost a = RemoteHost.create("a", "a", 22, "u", Auth.AGENT, "", Optional.empty());
        store.put(a); run();
        Files.delete(dir.resolve("hosts.toml")); Files.createDirectory(dir.resolve("hosts.toml"));
        store.poll(); run();
        assertThat(store.error()).isPresent();
        assertThat(store.hosts()).containsExactly(a);
    }

}
