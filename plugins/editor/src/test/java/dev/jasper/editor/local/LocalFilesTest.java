package dev.jasper.editor.local;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisabledOnOs(OS.WINDOWS)
class LocalFilesTest {
    @TempDir Path folder;

    @Test void aSafeWriteReplacesTheFileAtomicallyAndKeepsItsPermissions() throws Exception {
        Path file = Files.writeString(folder.resolve("app.toml"), "old\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        Object before = Files.readAttributes(file, BasicFileAttributes.class).fileKey();
        Revision revision = LocalFiles.write(file, "new content\n".getBytes(UTF_8));
        assertThat(Files.readString(file)).isEqualTo("new content\n");
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
        assertThat(Files.readAttributes(file, BasicFileAttributes.class).fileKey()).as("a new file replaced the old one").isNotEqualTo(before);
        assertThat(revision).isEqualTo(LocalFiles.revision(file).orElseThrow());
        try (var files = Files.list(folder)) { assertThat(files).as("no sibling left behind").containsExactly(file); }
    }

    @Test void theSiblingOfAnExistingFileIsCreatedOwnerOnlyBeforeAnyContentIsWritten() throws Exception {
        Path file = Files.writeString(folder.resolve("credentials"), "secret\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        Path sibling = LocalFiles.createSibling(file, true);
        try {
            assertThat(Files.size(sibling)).as("nothing written yet").isZero();
            assertThat(Files.getPosixFilePermissions(sibling)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        } finally { Files.deleteIfExists(sibling); }
    }

    @Test void aReadOnlyFileIsNotReplaced() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores file permissions");
        Path file = Files.writeString(folder.resolve("locked.conf"), "original\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        try {
            assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(() -> LocalFiles.write(file, "new\n".getBytes(UTF_8)));
            assertThat(Files.readString(file)).isEqualTo("original\n");
            try (var files = Files.list(folder)) { assertThat(files).as("no sibling left behind").containsExactly(file); }
        } finally { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--")); }
    }

    @Test void aSymlinkStaysALinkAndItsTargetChanges() throws Exception {
        Path target = Files.writeString(folder.resolve("real.conf"), "a\n");
        Path link = Files.createSymbolicLink(folder.resolve("link.conf"), target.getFileName());
        LocalFiles.write(link, "b\n".getBytes(UTF_8));
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("b\n");
    }

    @Test void aHardLinkedFileIsWrittenInPlace() throws Exception {
        Path file = Files.writeString(folder.resolve("shared.txt"), "one\n");
        Path other = Files.createLink(folder.resolve("other.txt"), file);
        LocalFiles.write(file, "two\n".getBytes(UTF_8));
        assertThat(Files.readString(other)).as("both names still share the file").isEqualTo("two\n");
    }

    @Test void anUnwritableFolderFallsBackToWritingInPlace() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores folder permissions");
        Path locked = Files.createDirectory(folder.resolve("locked"));
        Path file = Files.writeString(locked.resolve("f.txt"), "long original text\n");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            LocalFiles.write(file, "short\n".getBytes(UTF_8));
            assertThat(Files.readString(file)).as("truncated to the new length").isEqualTo("short\n");
        } finally { Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x")); }
    }

    @Test void aMissingFileIsCreated() throws Exception {
        Path file = folder.resolve("new.yaml");
        LocalFiles.write(file, "a: 1\n".getBytes(UTF_8));
        assertThat(Files.readString(file)).isEqualTo("a: 1\n");
    }

    @Test void anOversizedFileIsRefused() throws Exception {
        Path file = Files.write(folder.resolve("big.log"), new byte[2048]);
        assertThatExceptionOfType(TooLargeException.class).isThrownBy(() -> LocalFiles.read(file, 1024))
            .satisfies(failure -> {
                assertThat(failure.size()).isEqualTo(2048);
                assertThat(failure.limit()).isEqualTo(1024);
            });
    }

    @Test void anEmptyFileReadsAndWritesAsZeroBytes() throws Exception {
        Path file = Files.createFile(folder.resolve("empty.toml"));
        assertThat(LocalFiles.read(file, 10).bytes()).isEmpty();
        Files.writeString(file, "x");
        LocalFiles.write(file, new byte[0]);
        assertThat(Files.size(file)).isZero();
    }

    @Test void aFolderIsRefused() {
        assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> LocalFiles.read(folder, 1024))
            .withMessageContaining("Is a folder");
    }

    @Test void aFileThatIsNotRegularIsRefused() {
        assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> LocalFiles.read(Path.of("/dev/null"), 10))
            .withMessageContaining("Not a regular file");
    }

    @Test void revisionsFollowTheFileAndAreEmptyOnceItIsGone() throws Exception {
        Path file = Files.writeString(folder.resolve("r.txt"), "abc");
        assertThat(LocalFiles.revision(file)).hasValueSatisfying(revision -> assertThat(revision.size()).isEqualTo(3));
        Files.delete(file);
        assertThat(LocalFiles.revision(file)).isEmpty();
    }

    @Test void aLinkLoopIsAnError() throws Exception {
        Path a = folder.resolve("a"), b = folder.resolve("b");
        Files.createSymbolicLink(a, b);
        Files.createSymbolicLink(b, a);
        assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> LocalFiles.resolve(a))
            .withMessageContaining("Too many levels");
    }
}
