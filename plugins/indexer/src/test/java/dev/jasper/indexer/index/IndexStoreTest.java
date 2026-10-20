package dev.jasper.indexer.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IndexStoreTest {
    @TempDir Path data;

    @Test void projectsRoundTripWithAwkwardNames() throws IOException {
        var store = new IndexStore(data);
        assertThat(store.loadProjects()).as("no file yet").isEmpty();
        var projects = List.of(new Project(ProjectKey.local(Path.of("/w/my app")), "my \"app\"", false),
            new Project(ProjectKey.remote(UUID.randomUUID(), "/srv/my \"app\"/tab\there"), "tab\there", true));
        store.saveProjects(projects);
        assertThat(Files.readString(data.resolve("projects.toml"))).startsWith("# Projects indexed by Project Index.");
        assertThat(store.loadProjects()).isEqualTo(projects);
    }

    @Test void aProjectsFileThatDoesNotParseIsAnError() throws IOException {
        Files.writeString(data.resolve("projects.toml"), "[[project]\nkey = ");
        assertThatThrownBy(() -> new IndexStore(data).loadProjects()).isInstanceOf(IOException.class);
    }

    @Test void aProjectsFileThatDoesNotLoadIsSetAsideBeforeTheNextSave() throws IOException {
        String broken = "[[project]\nkey = ";
        Files.writeString(data.resolve("projects.toml"), broken);
        Files.writeString(data.resolve("projects.toml.bad"), "an older bad file");
        var store = new IndexStore(data);
        assertThatThrownBy(store::loadProjects).isInstanceOf(IOException.class);
        var projects = List.of(new Project(ProjectKey.local(Path.of("/w/a")), "a", false));
        store.saveProjects(projects);
        assertThat(Files.readString(data.resolve("projects.toml.bad"))).isEqualTo(broken);
        assertThat(store.loadProjects()).isEqualTo(projects);
        store.saveProjects(List.of());
        assertThat(Files.readString(data.resolve("projects.toml.bad"))).as("only the unreadable file is set aside").isEqualTo(broken);
    }

    @Test void entriesWithBadKeysAreSkipped() throws IOException {
        var key = ProjectKey.local(Path.of("/w/a"));
        Files.writeString(data.resolve("projects.toml"), "[[project]]\nkey = \"nonsense\"\n[[project]]\nkey = '" + key.id() + "'\n");
        assertThat(new IndexStore(data).loadProjects()).containsExactly(new Project(ProjectKey.local(Path.of("/w/a")), "a", false));
    }

    @Test void indexesRoundTrip() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        var index = new ProjectIndex(key, List.of("a.txt", "dir/b\nc.txt"), Strategy.WALK, Instant.ofEpochMilli(1234), true);
        store.saveIndex(index);
        var loaded = store.loadIndex(key).orElseThrow();
        assertThat(loaded.key()).isEqualTo(key);
        assertThat(loaded.files()).containsExactly("a.txt", "dir/b\nc.txt");
        assertThat(loaded.strategy()).isEqualTo(Strategy.WALK);
        assertThat(loaded.indexedAt()).isEqualTo(Instant.ofEpochMilli(1234));
        assertThat(loaded.truncated()).isTrue();
        store.deleteIndex(key);
        assertThat(store.loadIndex(key)).isEmpty();
    }

    @Test void anIndexThatCannotBeReadIsKept() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        Files.createDirectories(store.indexFile(key));
        assertThat(store.loadIndex(key)).isEmpty();
        assertThat(store.indexFile(key)).as("a read error is not a corrupt file").exists();
    }

    @Test void anIndexCutShortIsDeleted() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        store.saveIndex(new ProjectIndex(key, List.of("a", "b"), Strategy.GIT, Instant.EPOCH, false));
        byte[] whole = Files.readAllBytes(store.indexFile(key));
        Files.write(store.indexFile(key), java.util.Arrays.copyOf(whole, whole.length - 12));
        assertThat(store.loadIndex(key)).isEmpty();
        assertThat(store.indexFile(key)).doesNotExist();
    }

    @Test void aCorruptOrForeignIndexIsDeleted() throws IOException {
        var store = new IndexStore(data);
        var key = ProjectKey.local(Path.of("/w/app"));
        Files.createDirectories(store.indexFile(key).getParent());
        Files.write(store.indexFile(key), new byte[] {1, 2, 3});
        assertThat(store.loadIndex(key)).isEmpty();
        assertThat(store.indexFile(key)).doesNotExist();
        var other = ProjectKey.local(Path.of("/w/other"));
        store.saveIndex(new ProjectIndex(other, List.of("x"), Strategy.GIT, Instant.EPOCH, false));
        Files.move(store.indexFile(other), store.indexFile(key));
        assertThat(store.loadIndex(key)).as("the header names another project").isEmpty();
        assertThat(store.indexFile(key)).doesNotExist();
    }
}
