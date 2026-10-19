package dev.jasper.indexer.index;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectKeyTest {
    static final UUID HOST = UUID.fromString("0b6f2b5e-8c1a-4c55-9d43-2f3a4b5c6d7e");

    @Test void idsRoundTrip() {
        var local = ProjectKey.local(Path.of("/Users/me/src/../src/app").toAbsolutePath());
        assertThat(local.id()).isEqualTo("local:" + Path.of("/Users/me/src/app").toAbsolutePath());
        assertThat(ProjectKey.parse(local.id())).contains(local);
        var remote = ProjectKey.remote(HOST, "/srv/app:v2");
        assertThat(remote.id()).isEqualTo("remote:" + HOST + ":/srv/app:v2");
        assertThat(ProjectKey.parse(remote.id())).contains(remote);
        assertThat(ProjectKey.parse("remote:not-a-uuid:/x")).isEmpty();
        assertThat(ProjectKey.parse("other:/x")).isEmpty();
    }

    @Test void containmentRespectsPathBoundaries() {
        var local = ProjectKey.local(Path.of("/w/app").toAbsolutePath());
        assertThat(local.contains(Path.of("/w/app").toAbsolutePath())).isTrue();
        assertThat(local.contains(Path.of("/w/app/src").toAbsolutePath())).isTrue();
        assertThat(local.contains(Path.of("/w/application").toAbsolutePath())).isFalse();
        var remote = ProjectKey.remote(HOST, "/srv/app");
        assertThat(remote.contains(HOST, "/srv/app/lib")).isTrue();
        assertThat(remote.contains(HOST, "/srv/application")).isFalse();
        assertThat(remote.contains(UUID.randomUUID(), "/srv/app")).isFalse();
        assertThat(remote.contains(Path.of("/srv/app").toAbsolutePath())).as("a local folder is never in a remote project").isFalse();
    }

    @Test void namesAndFileReferences() {
        var local = ProjectKey.local(Path.of("/w/app").toAbsolutePath());
        assertThat(local.name()).isEqualTo("app");
        assertThat(ProjectKey.remote(HOST, "/srv/app/").name()).isEqualTo("app");
        var ref = new FileRef(local, "src/main/Main.java");
        assertThat(ref.name()).isEqualTo("Main.java");
        assertThat(ref.localPath()).isEqualTo(Path.of("/w/app/src/main/Main.java").toAbsolutePath());
        assertThat(new FileRef(ProjectKey.remote(HOST, "/srv/app"), "a/b.txt").remotePath()).isEqualTo("/srv/app/a/b.txt");
        assertThatThrownBy(() -> ProjectKey.remote(HOST, "relative")).isInstanceOf(IllegalArgumentException.class);
    }
}
