package dev.jasper.remote.edit;

import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.remote.sftp.SftpFixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** POSIX paths, modes and links: SafeWrite serves the SFTP browser, whose paths are POSIX text. */
@DisabledOnOs(OS.WINDOWS)
class SafeWriteTest {
    @TempDir Path temp;

    Path folder() throws IOException { return temp.toRealPath(); }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static List<String> names(Path folder) throws IOException {
        try (Stream<Path> list = Files.list(folder)) { return list.map(path -> path.getFileName().toString()).sorted().toList(); }
    }
    /** The hidden sibling the write created, from its recorded {@code create} call. */
    static String sibling(TestEndpoint endpoint) {
        return endpoint.calls.stream().filter(call -> call.startsWith("create ")).findFirst().orElseThrow()
            .replaceFirst("^create (.*) [0-7]+$", "$1");
    }
    static FileEntry withPermissions(FileEntry entry, int permissions) {
        return new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), permissions,
            entry.linkTarget(), entry.fileKey(), entry.uid(), entry.gid());
    }

    @Test void aSafeWriteReplacesTheFileAndKeepsItsPermissions() throws Exception {
        Path file = Files.writeString(folder().resolve("notes.toml"), "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            FileEntry written = SafeWrite.write(endpoint, file.toString(), bytes("new text"));
            assertThat(Files.readString(file)).isEqualTo("new text");
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
            assertThat(written.size()).isEqualTo(8);
            assertThat(written.modifiedMillis()).isEqualTo(Files.getLastModifiedTime(file).toMillis());
            assertThat(names(folder())).as("no sibling is left").containsExactly("notes.toml");
            assertThat(endpoint.calls).as("the sibling is created owner-only")
                .anyMatch(call -> call.matches("create .*/\\.notes\\.toml\\.jasper-[0-9a-f]+\\.tmp 600"));
            assertThat(endpoint.calls).contains("publish " + file);
        }
    }

    @Test void aTargetWithUnknownPermissionsIsWrittenInPlace() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            // As SFTP reports when the server leaves out the permissions attribute.
            endpoint.stats = entry -> entry.name().equals("app.conf")
                ? new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), -1,
                    entry.linkTarget(), entry.fileKey(), entry.uid(), entry.gid())
                : entry;
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(endpoint.calls).contains("truncate " + file)
                .noneMatch(call -> call.startsWith("create ") || call.startsWith("publish"));
            assertThat(names(folder())).as("no sibling is created").containsExactly("app.conf");
        }
    }

    @Test void aNewFileIsCreatedWithTheDefaultMode() throws Exception {
        Path file = folder().resolve("new.txt");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            SafeWrite.write(endpoint, file.toString(), bytes("fresh"));
            assertThat(Files.readString(file)).isEqualTo("fresh");
            assertThat(endpoint.calls).anyMatch(call -> call.startsWith("create ") && call.endsWith(".tmp 666"));
            assertThat(names(folder())).containsExactly("new.txt");
        }
    }

    @Test void aReadOnlyFileIsRefusedAndLeftAlone() throws Exception {
        Path file = Files.writeString(folder().resolve("locked.conf"), "keep");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, file.toString(), bytes("lost"))).isInstanceOf(AccessDeniedException.class);
            assertThat(Files.readString(file)).isEqualTo("keep");
            assertThat(names(folder())).containsExactly("locked.conf");
        } finally {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        }
    }

    @Test void aSymlinkStaysALinkAndItsTargetChanges() throws Exception {
        Path target = Files.writeString(folder().resolve("target.conf"), "old");
        Path link = Files.createSymbolicLink(folder().resolve("link.conf"), Path.of("target.conf"));
        try (var endpoint = new LocalEndpoint()) {
            SafeWrite.write(endpoint, link.toString(), bytes("new"));
            assertThat(Files.isSymbolicLink(link)).isTrue();
            assertThat(Files.readString(target)).isEqualTo("new");
        }
    }

    @Test void aLinkThroughALinkedFolderAndDotDotIsFollowed() throws Exception {
        Path real = Files.createDirectory(folder().resolve("real"));
        Path file = Files.writeString(real.resolve("app.yaml"), "old");
        Files.createSymbolicLink(folder().resolve("alias"), Path.of("real"));
        Path deep = Files.createDirectory(folder().resolve("deep"));
        Path link = Files.createSymbolicLink(deep.resolve("app.yaml"), Path.of("../alias/app.yaml"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(SafeWrite.resolve(endpoint, link.toString()).path()).isEqualTo(file.toString());
            SafeWrite.write(endpoint, link.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(Files.isSymbolicLink(link)).isTrue();
        }
    }

    @Test void aDanglingLinkCreatesItsTarget() throws Exception {
        Path link = Files.createSymbolicLink(folder().resolve("pending.conf"), Path.of("created.conf"));
        try (var endpoint = new LocalEndpoint()) {
            assertThat(SafeWrite.resolve(endpoint, link.toString()).entry()).isEmpty();
            SafeWrite.write(endpoint, link.toString(), bytes("made"));
            assertThat(Files.readString(folder().resolve("created.conf"))).isEqualTo("made");
            assertThat(Files.isSymbolicLink(link)).isTrue();
        }
    }

    @Test void aLinkLoopIsAnError() throws Exception {
        Files.createSymbolicLink(folder().resolve("a"), Path.of("b"));
        Files.createSymbolicLink(folder().resolve("b"), Path.of("a"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, folder().resolve("a").toString(), bytes("x")))
                .isInstanceOf(FileSystemException.class).hasMessageContaining("Too many levels of symbolic links");
        }
    }

    @Test void aFolderIsRefused() throws Exception {
        Path directory = Files.createDirectory(folder().resolve("conf.d"));
        try (var endpoint = new LocalEndpoint()) {
            assertThatThrownBy(() -> SafeWrite.write(endpoint, directory.toString(), bytes("x")))
                .isInstanceOf(FileSystemException.class).hasMessageContaining("Is a folder");
        }
    }

    @Test void anUnwritableFolderFallsBackToWritingInPlace() throws Exception {
        Path locked = Files.createDirectory(folder().resolve("locked"));
        Path file = Files.writeString(locked.resolve("app.conf"), "a much longer old text");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            SafeWrite.write(endpoint, file.toString(), bytes("short"));
            assertThat(Files.readString(file)).as("written over and truncated").isEqualTo("short");
            assertThat(endpoint.calls).contains("truncate " + file).noneMatch(call -> call.startsWith("publish"));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test void aNonPermissionCreateFailurePropagatesUnchanged() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            IOException failure = new IOException("connection reset");
            endpoint.createFailure = failure;
            assertThatThrownBy(() -> SafeWrite.write(endpoint, file.toString(), bytes("new"))).isSameAs(failure);
            assertThat(Files.readString(file)).as("left alone").isEqualTo("old contents");
            assertThat(endpoint.calls).noneMatch(call -> call.equals("truncate " + file) || call.equals("write " + file));
        }
    }

    @Test void anOwnerOrGroupMismatchWritesInPlace() throws Exception {
        Path file = Files.writeString(folder().resolve("shared.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            // The sibling reports another owner, as a new file would in a folder whose setgid group differs.
            endpoint.stats = entry -> entry.name().startsWith(".shared.conf.jasper-")
                ? new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), entry.permissions(),
                    entry.linkTarget(), entry.fileKey(), entry.uid() + 1, entry.gid())
                : entry;
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(endpoint.calls).contains("truncate " + file).noneMatch(call -> call.startsWith("publish"));
            assertThat(names(folder())).as("the sibling is removed").containsExactly("shared.conf");
        }
    }

    @Test void withoutAtomicReplacementItWritesInPlace() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.replaceFailure = new AtomicMoveNotSupportedException("from", "to", "Server does not support atomic replacement");
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(endpoint.calls).contains("truncate " + file);
            assertThat(names(folder())).as("the sibling is removed").containsExactly("app.conf");
        }
    }

    @Test void aFailedReplacementRemovesTheSiblingAndLeavesTheTarget() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.replaceFailure = new IOException("connection reset");
            assertThatThrownBy(() -> SafeWrite.write(endpoint, file.toString(), bytes("new"))).hasMessage("connection reset");
            assertThat(Files.readString(file)).isEqualTo("old");
            assertThat(names(folder())).containsExactly("app.conf");
        }
    }

    /** Final review F1: over SFTP a failed write aborts the endpoint, so removing the sibling fails too. */
    @Test void aSiblingThatCannotBeRemovedIsMarkedOnTheOriginalFailure() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            var failure = new SocketTimeoutException("Timed out waiting for SFTP response");
            endpoint.replaceFailure = failure;
            endpoint.removeFailure = new IOException("SFTP operation cancelled");
            Throwable thrown = catchThrowable(() -> SafeWrite.write(endpoint, file.toString(), bytes("new")));
            String sibling = sibling(endpoint);
            assertThat(thrown).as("the original failure keeps its type and message").isSameAs(failure);
            assertThat(SafeWrite.leftover(thrown)).contains(sibling);
            assertThat(SafeWrite.leftover(new CompletionException(thrown))).as("unwrapped from a future").contains(sibling);
            assertThat(thrown.getSuppressed()).anySatisfy(marker -> assertThat(marker).isInstanceOf(SafeWrite.SiblingLeft.class)
                .hasMessage("Could not remove " + sibling).hasCause(endpoint.removeFailure));
            assertThat(Files.readString(file)).isEqualTo("old");
            assertThat(names(folder())).as("the sibling is still there").hasSize(2).contains("app.conf");
            assertThat(SafeWrite.leftover(new IOException("unrelated"))).isEmpty();
        }
    }

    @Test void aFailedOwnerCheckMarksASiblingItCannotRemove() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            var failure = new UncheckedIOException(new IOException("stat failed"));
            endpoint.stats = entry -> { if (entry.name().startsWith(".app.conf.jasper-")) throw failure; return entry; };
            endpoint.removeFailure = new IOException("SFTP operation cancelled");
            Throwable thrown = catchThrowable(() -> SafeWrite.write(endpoint, file.toString(), bytes("new")));
            assertThat(thrown).isSameAs(failure);
            assertThat(SafeWrite.leftover(thrown)).contains(sibling(endpoint));
            assertThat(Files.readString(file)).isEqualTo("old");
        }
    }

    @Test void withoutAtomicReplacementASiblingThatCannotBeRemovedFailsTheSaveAndLeavesTheTarget() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.replaceFailure = new AtomicMoveNotSupportedException(null, null, "Server does not support atomic replacement");
            var removal = new IOException("SFTP operation cancelled");
            endpoint.removeFailure = removal;
            Throwable thrown = catchThrowable(() -> SafeWrite.write(endpoint, file.toString(), bytes("new")));
            assertThat(thrown).isSameAs(removal);
            assertThat(SafeWrite.leftover(thrown)).contains(sibling(endpoint));
            assertThat(Files.readString(file)).as("not written in place").isEqualTo("old contents");
            assertThat(endpoint.calls).noneMatch(call -> call.equals("write " + file) || call.equals("truncate " + file));
        }
    }

    @Test void anOwnerMismatchWhoseSiblingFailsToCloseStillRemovesIt() throws Exception {
        Path file = Files.writeString(folder().resolve("shared.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.stats = entry -> entry.name().startsWith(".shared.conf.jasper-")
                ? new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), entry.permissions(),
                    entry.linkTarget(), entry.fileKey(), entry.uid() + 1, entry.gid())
                : entry;
            var failure = new IOException("close failed");
            endpoint.closeFailure = failure;
            Throwable thrown = catchThrowable(() -> SafeWrite.write(endpoint, file.toString(), bytes("new")));
            assertThat(thrown).isSameAs(failure);
            assertThat(SafeWrite.leftover(thrown)).isEmpty();
            assertThat(endpoint.calls).contains("remove " + sibling(endpoint));
            assertThat(names(folder())).as("the sibling is removed").containsExactly("shared.conf");
            assertThat(Files.readString(file)).isEqualTo("old contents");
        }
    }

    @Test void anOwnerMismatchWhoseSiblingCannotBeRemovedFailsTheSaveAndLeavesTheTarget() throws Exception {
        Path file = Files.writeString(folder().resolve("shared.conf"), "old contents");
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.stats = entry -> entry.name().startsWith(".shared.conf.jasper-")
                ? new FileEntry(entry.name(), entry.kind(), entry.size(), entry.modifiedMillis(), entry.permissions(),
                    entry.linkTarget(), entry.fileKey(), entry.uid() + 1, entry.gid())
                : entry;
            var removal = new IOException("SFTP operation cancelled");
            endpoint.removeFailure = removal;
            Throwable thrown = catchThrowable(() -> SafeWrite.write(endpoint, file.toString(), bytes("new")));
            assertThat(thrown).isSameAs(removal);
            assertThat(SafeWrite.leftover(thrown)).contains(sibling(endpoint));
            assertThat(Files.readString(file)).as("not written in place").isEqualTo("old contents");
            assertThat(endpoint.calls).noneMatch(call -> call.equals("write " + file) || call.equals("truncate " + file));
        }
    }

    /** Final review M2: a server that ignores the create's mode must not expose the new content while it is written. */
    @Test void aSiblingCreatedReadableByOthersIsNarrowedBeforeAnyByteIsWritten() throws Exception {
        Path file = Files.writeString(folder().resolve("app.conf"), "old");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            endpoint.stats = entry -> entry.name().startsWith(".app.conf.jasper-") ? withPermissions(entry, 0644) : entry;
            SafeWrite.write(endpoint, file.toString(), bytes("new"));
            String sibling = sibling(endpoint);
            assertThat(endpoint.calls).containsSubsequence("create " + sibling + " 600", "stat " + sibling, "metadata " + sibling,
                "bytes " + sibling, "metadata " + sibling, "publish " + file);
            assertThat(Files.readString(file)).isEqualTo("new");
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
        }
        try (var endpoint = new TestEndpoint(new LocalEndpoint())) {
            SafeWrite.write(endpoint, file.toString(), bytes("again"));
            assertThat(endpoint.calls).as("an owner-only sibling is left as created, then given the target's mode")
                .filteredOn(call -> call.startsWith("metadata ")).hasSize(1);
        }
    }

    @Test void overSftpItReplacesAtomicallyRefusesReadOnlyFilesAndFallsBackWithoutPosixRename() throws Exception {
        try (var fixture = new SftpFixture(temp)) {
            Path file = Files.writeString(fixture.files.resolve("site.conf"), "old");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            try (var endpoint = fixture.endpoint()) {
                FileEntry written = SafeWrite.write(endpoint, "/site.conf", bytes("new text"));
                assertThat(Files.readString(file)).isEqualTo("new text");
                assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
                assertThat(written.size()).isEqualTo(8);
                assertThat(names(fixture.files)).containsExactly("site.conf");
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
                assertThatThrownBy(() -> SafeWrite.write(endpoint, "/site.conf", bytes("lost"))).isInstanceOf(AccessDeniedException.class);
                assertThat(Files.readString(file)).isEqualTo("new text");
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
            }
            Path locked = Files.createDirectory(fixture.files.resolve("locked"));
            Path inLocked = Files.writeString(locked.resolve("app.conf"), "a much longer old text");
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
            try (var endpoint = new TestEndpoint(fixture.endpoint())) {
                SafeWrite.write(endpoint, "/locked/app.conf", bytes("short"));
                assertThat(Files.readString(inLocked)).as("the server refused the sibling, so written in place").isEqualTo("short");
                assertThat(endpoint.calls).contains("truncate /locked/app.conf").noneMatch(call -> call.startsWith("publish"));
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
            org.apache.sshd.sftp.SftpModuleProperties.OPENSSH_EXTENSIONS.set(fixture.server.server, ",");
            try (var endpoint = new TestEndpoint(fixture.endpoint())) {
                SafeWrite.write(endpoint, "/site.conf", bytes("short"));
                assertThat(Files.readString(file)).as("written over and truncated").isEqualTo("short");
                assertThat(endpoint.calls).contains("truncate /site.conf");
                assertThat(names(fixture.files)).containsExactly("locked", "site.conf");
            }
        }
    }
}
