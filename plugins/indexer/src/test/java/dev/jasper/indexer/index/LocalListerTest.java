package dev.jasper.indexer.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalListerTest {
    static final List<String> EXCLUDES = List.of("node_modules", "build", ".git");
    @TempDir Path home;

    static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException missing) {
            return false;
        }
    }

    static void git(Path directory, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString(), "-c", "user.name=t", "-c", "user.email=t@t",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor()).as(output).isZero();
    }

    static Path write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }

    @Test void aWorkTreeListsTrackedAndUntrackedFilesGitDoesNotIgnore() throws Exception {
        assumeTrue(gitAvailable());
        Path root = Files.createDirectories(home.resolve("src/app"));
        git(root, "init", "-q");
        write(root.resolve(".gitignore"));
        Files.writeString(root.resolve(".gitignore"), "ignored.txt\ngen/\n");
        write(root.resolve("src/Main.java"));
        write(root.resolve("ignored.txt"));
        write(root.resolve("gen/out.txt"));
        write(root.resolve("build/kept-by-git.txt"));
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", "init");
        write(root.resolve("untracked.md"));
        var listing = new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.GIT);
        assertThat(listing.files()).containsExactlyInAnyOrder(".gitignore", "src/Main.java", "untracked.md");
        assertThat(listing.truncated()).isFalse();
    }

    @Test void aRepositoryConfiguredFsmonitorNeverRuns() throws Exception {
        assumeTrue(gitAvailable() && !System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("src/evil"));
        git(root, "init", "-q");
        Path ran = home.resolve("ran");
        Path hook = Files.writeString(home.resolve("monitor.sh"), "#!/bin/sh\ntouch '" + ran + "'\n");
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"));
        git(root, "config", "core.fsmonitor", hook.toString());
        write(root.resolve("a.txt"));
        new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(ran).doesNotExist();
    }

    @Test void aWorkTreeWhoseTopIsHomeIsWalkedInstead() throws Exception {
        assumeTrue(gitAvailable());
        git(home, "init", "-q");
        Files.writeString(home.resolve(".gitignore"), "*\n");
        Path root = Files.createDirectories(home.resolve("src/app"));
        write(root.resolve("Cargo.toml"));
        var listing = new LocalLister(List.of("git"), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.WALK);
        assertThat(listing.files()).containsExactly("Cargo.toml");
    }

    @Test void withoutGitTheFolderIsWalkedSkippingExcludedNames() throws IOException {
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("b/z.txt"));
        write(root.resolve("a.txt"));
        write(root.resolve("node_modules/x/index.js"));
        write(root.resolve("lib/build/out.o"));
        var listing = new LocalLister(List.of(home.resolve("no-such-git").toString()), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.WALK);
        assertThat(listing.files()).containsExactly("a.txt", "b/z.txt");
    }

    @Test void theWalkStopsAtTheLimitAndNeverFollowsLinks() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("app"));
        for (int i = 0; i < 5; i++) write(root.resolve("f" + i + ".txt"));
        Files.createSymbolicLink(root.resolve("loop"), root);
        var lister = new LocalLister(List.of(home.resolve("no-such-git").toString()), home);
        var capped = lister.list(root, EXCLUDES, 3, () -> false);
        assertThat(capped.files()).hasSize(3);
        assertThat(capped.truncated()).isTrue();
        var all = lister.list(root, EXCLUDES, 1_000, () -> false);
        assertThat(all.files()).hasSize(5).noneMatch(path -> path.startsWith("loop"));
    }

    @Test void namesWithNewlinesSurvive() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("odd\nname.txt"));
        var listing = new LocalLister(List.of(home.resolve("no-such-git").toString()), home).list(root, EXCLUDES, 1_000, () -> false);
        assertThat(listing.files()).containsExactly("odd\nname.txt");
    }

    @Test void anUnreadableRootFailsInsteadOfListingNothing() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows") && !"root".equals(System.getProperty("user.name")));
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("a.txt"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("---------"));
        try {
            var lister = new LocalLister(List.of(home.resolve("no-such-git").toString()), home);
            assertThatThrownBy(() -> lister.list(root, EXCLUDES, 1_000, () -> false)).isInstanceOf(IOException.class)
                .isNotInstanceOf(NoSuchFileException.class);
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test void aRootThatIsALinkListsTheFolderItNames() throws IOException {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
        Path real = Files.createDirectories(home.resolve("real"));
        write(real.resolve("a.txt"));
        write(real.resolve("sub/b.txt"));
        Path elsewhere = Files.createDirectories(home.resolve("elsewhere"));
        write(elsewhere.resolve("c.txt"));
        Files.createSymbolicLink(real.resolve("out"), elsewhere);
        Path link = Files.createSymbolicLink(home.resolve("app"), real);
        var listing = new LocalLister(List.of(home.resolve("no-such-git").toString()), home).list(link, EXCLUDES, 1_000, () -> false);
        assertThat(listing.strategy()).isEqualTo(Strategy.WALK);
        assertThat(listing.files()).as("links inside the project are still not followed").containsExactly("a.txt", "sub/b.txt");
    }

    @Test void aMissingRootAndCancellationAreReported() throws IOException {
        var lister = new LocalLister(List.of(home.resolve("no-such-git").toString()), home);
        assertThatThrownBy(() -> lister.list(home.resolve("gone"), EXCLUDES, 1_000, () -> false)).isInstanceOf(NoSuchFileException.class);
        Path root = Files.createDirectories(home.resolve("app"));
        write(root.resolve("a.txt"));
        assertThatThrownBy(() -> lister.list(root, EXCLUDES, 1_000, () -> true)).isInstanceOf(CancellationException.class);
    }
}
