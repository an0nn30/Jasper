package dev.jasper.fileserver.definition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefinitionStoreTest {
    final Deque<Runnable> background = new ArrayDeque<>();
    final Executor queued = background::add;
    int changes;

    void run() { while (!background.isEmpty()) background.poll().run(); }

    DefinitionStore store(Path dir) {
        var store = new DefinitionStore(dir.resolve("servers.toml"), queued, Runnable::run);
        store.onChanged(() -> changes++);
        return store;
    }

    @Test void loadsSavesAndRemoves(@TempDir Path dir) throws Exception {
        DefinitionStore store = store(dir);
        store.load();
        run();
        assertThat(store.definitions()).isEmpty();
        assertThat(store.error()).isEmpty();
        var docs = ServerDefinition.create("docs", Protocol.HTTP, dir.toAbsolutePath());
        CompletableFuture<Void> saved = store.put(docs);
        run();
        assertThat(saved).isCompleted();
        assertThat(store.definitions()).containsExactly(docs);
        assertThat(store.definition(docs.id())).contains(docs);
        assertThat(changes).isEqualTo(2);
        assertThat(Files.readString(dir.resolve("servers.toml"))).contains("name = \"docs\"");
        try (var listing = Files.list(dir)) { assertThat(listing).as("no temp file").containsExactly(dir.resolve("servers.toml")); }
        var renamed = new ServerDefinition(docs.id(), "papers", docs.protocol(), docs.folder(), docs.bind(), 8081, docs.passiveRange(),
            docs.writable(), docs.login(), docs.authorizedKeys(), docs.tls(), docs.requireTls(), docs.autostart());
        store.put(renamed);
        run();
        assertThat(store.definitions()).containsExactly(renamed);
        CompletableFuture<Void> clash = store.put(ServerDefinition.create("PAPERS", Protocol.HTTP, dir.toAbsolutePath()));
        run();
        assertThatThrownBy(clash::join).isInstanceOf(CompletionException.class).hasMessageContaining("A server named papers exists");
        store.remove(docs.id());
        run();
        assertThat(store.definitions()).isEmpty();
    }

    @Test void aBrokenFileKeepsTheLastGoodServersAndRefusesSaves(@TempDir Path dir) throws Exception {
        DefinitionStore store = store(dir);
        var docs = ServerDefinition.create("docs", Protocol.HTTP, dir.toAbsolutePath());
        store.put(docs);
        run();
        Files.writeString(dir.resolve("servers.toml"), "[[server]\nbroken");
        store.load();
        run();
        assertThat(store.definitions()).containsExactly(docs);
        assertThat(store.error()).isPresent().get().asString().contains("TOML");
        CompletableFuture<Void> refused = store.put(ServerDefinition.create("more", Protocol.HTTP, dir.toAbsolutePath()));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class).hasMessageContaining("servers.toml has errors");
        Files.writeString(dir.resolve("servers.toml"), DefinitionFile.format(List.of(docs)));
        store.load();
        run();
        assertThat(store.error()).isEmpty();
    }

    private static String idless(String name, Path dir) {
        return "\n[[server]]\nname = \"" + name + "\"\nprotocol = \"http\"\nfolder = \"" + dir.toAbsolutePath().toString().replace("\\", "\\\\") + "\"\n";
    }

    @Test void anEntryWithoutAnIdKeepsOneIdentityThroughEditDeleteAndUnrelatedSaves(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("servers.toml");
        Files.writeString(file, DefinitionFile.HEADER + idless("a", dir) + idless("b", dir));
        DefinitionStore store = store(dir);
        store.load();
        run();
        ServerDefinition a = store.definitions().get(0);
        ServerDefinition b = store.definitions().get(1);
        assertThat(a.name()).isEqualTo("a");
        var edited = new ServerDefinition(a.id(), "a", a.protocol(), a.folder(), a.bind(), 9191, a.passiveRange(), a.writable(), a.login(),
            a.authorizedKeys(), a.tls(), a.requireTls(), a.autostart());
        CompletableFuture<Void> edit = store.put(edited);
        run();
        assertThat(edit).as("edit").isCompleted();
        assertThat(store.definitions()).containsExactly(edited, b);
        var other = ServerDefinition.create("c", Protocol.HTTP, dir.toAbsolutePath());
        store.put(other);
        run();
        assertThat(store.definitions()).as("b is unchanged by an unrelated save").containsExactly(edited, b, other);
        store.load();
        run();
        assertThat(store.definitions()).as("a re-read finds the same ids").containsExactly(edited, b, other);
        store.remove(b.id());
        run();
        assertThat(store.definitions()).as("delete").containsExactly(edited, other);
    }

    @Test void aFileWithASkippedEntryRefusesSavesAndStaysUnchanged(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("servers.toml");
        String text = DefinitionFile.HEADER + idless("good", dir) + "\n[[server]]\nname = \"bad\"\nprotocol = \"http\"\nport = 70000\nfolder = \"/x\"\n";
        Files.writeString(file, text);
        DefinitionStore store = store(dir);
        store.load();
        run();
        assertThat(store.warnings()).hasSize(1);
        assertThat(store.definitions()).hasSize(1);
        CompletableFuture<Void> refused = store.put(ServerDefinition.create("more", Protocol.HTTP, dir.toAbsolutePath()));
        run();
        assertThatThrownBy(refused::join).isInstanceOf(CompletionException.class)
            .hasMessageContaining("File Server cannot read").hasMessageContaining("server 2");
        CompletableFuture<Void> removal = store.remove(store.definitions().getFirst().id());
        run();
        assertThatThrownBy(removal::join).isInstanceOf(CompletionException.class).hasMessageContaining("cannot read");
        assertThat(Files.readString(file)).isEqualTo(text);
    }
}
