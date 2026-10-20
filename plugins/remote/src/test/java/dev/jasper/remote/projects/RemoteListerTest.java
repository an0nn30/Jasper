package dev.jasper.remote.projects;

import dev.jasper.remote.api.ListOptions;
import dev.jasper.remote.api.RemoteListing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("try")
class RemoteListerTest {
    static final ListOptions OPTIONS = new ListOptions(List.of("node_modules", "build"), 1_000, Duration.ofSeconds(30), () -> false);
    @TempDir Path dir;

    static void posix() { assumeTrue(!System.getProperty("os.name").startsWith("Windows")); }

    Path project(ProjectsFixture fixture) throws IOException {
        Path root = Files.createDirectories(fixture.home.resolve("src/app"));
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/main.rs"), "");
        Files.writeString(root.resolve("Cargo.toml"), "");
        Files.createDirectories(root.resolve("node_modules/x"));
        Files.writeString(root.resolve("node_modules/x/index.js"), "");
        return root;
    }

    @Test void aWorkTreeIsListedByGit() throws Exception {
        posix();
        assumeTrue(ScriptsTest.gitAvailable());
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            ScriptsTest.git(root, "init", "-q");
            Files.createDirectories(root.resolve("build"));
            Files.writeString(root.resolve("build/out.txt"), "");
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.GIT);
            assertThat(listing.files()).containsExactlyInAnyOrder("src/main.rs", "Cargo.toml");
        }
    }

    @Test void otherFoldersAreListedByFind() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.FIND);
            assertThat(listing.files()).containsExactly("Cargo.toml", "src/main.rs");
            assertThat(listing.truncated()).isFalse();
        }
    }

    @Test void withoutShellToolsTheSftpWalkLists() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            fixture.server.server.setCommandFactory((channel, command) -> new RealExec("exit 4", fixture.home));
            var listing = RemoteLister.list(fixture.lease(), root.toString(), OPTIONS);
            assertThat(listing.strategy()).isEqualTo(RemoteListing.Strategy.SFTP);
            assertThat(listing.files()).containsExactly("Cargo.toml", "src/main.rs");
        }
    }

    @Test void everyStrategyStopsAtTheLimit() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            Path root = project(fixture);
            var one = new ListOptions(List.of("node_modules"), 1, Duration.ofSeconds(30), () -> false);
            var found = RemoteLister.list(fixture.lease(), root.toString(), one);
            assertThat(found.files()).hasSize(1);
            assertThat(found.truncated()).isTrue();
            fixture.server.server.setCommandFactory((channel, command) -> new RealExec("exit 4", fixture.home));
            var walked = RemoteLister.list(fixture.lease(), root.toString(), one);
            assertThat(walked.strategy()).isEqualTo(RemoteListing.Strategy.SFTP);
            assertThat(walked.files()).hasSize(1);
            assertThat(walked.truncated()).isTrue();
        }
    }

    @Test void aWalkWhoseRootCannotBeReachedFailsWithoutCallingItMissing() throws Exception {
        posix();
        assumeTrue(!"root".equals(System.getProperty("user.name")));
        try (var fixture = new ProjectsFixture(dir)) {
            Path locked = Files.createDirectories(fixture.home.resolve("locked"));
            Path root = Files.createDirectories(locked.resolve("app"));
            Files.writeString(root.resolve("a.txt"), "");
            fixture.server.server.setCommandFactory((channel, command) -> new RealExec("exit 4", fixture.home));
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
            try {
                assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), root.toString(), OPTIONS))
                    .isInstanceOf(IOException.class).isNotInstanceOf(NoSuchFileException.class);
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
            }
            assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), fixture.home.resolve("gone").toString(), OPTIONS))
                .as("a root that does not exist is still missing").isInstanceOf(NoSuchFileException.class);
        }
    }

    @Test void aMissingRootAndCancellationAreReported() throws Exception {
        posix();
        try (var fixture = new ProjectsFixture(dir)) {
            assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), fixture.home.resolve("gone").toString(), OPTIONS))
                .isInstanceOf(NoSuchFileException.class);
            Path root = project(fixture);
            var cancelled = new ListOptions(List.of(), 1_000, Duration.ofSeconds(30), () -> true);
            assertThatThrownBy(() -> RemoteLister.list(fixture.lease(), root.toString(), cancelled)).isInstanceOf(CancellationException.class);
        }
    }
}
