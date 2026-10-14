package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnerOnlyFilesTest {
    @TempDir Path dir;

    private static boolean posix(Path path) { return path.getFileSystem().supportedFileAttributeViews().contains("posix"); }

    @Test void aTokenIsFortyThreeUrlSafeCharactersWrittenOwnerOnly() throws Exception {
        Path file = dir.resolve("token");
        String token = OwnerOnlyFiles.writeToken(file);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(Files.readString(file)).isEqualTo(token + "\n");
        if (posix(file)) assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test void everyTokenIsNewAndReplacesTheLastOneInTheFile() throws Exception {
        Path file = dir.resolve("token");
        String first = OwnerOnlyFiles.writeToken(file);
        String second = OwnerOnlyFiles.writeToken(file);
        assertThat(second).isNotEqualTo(first);
        assertThat(Files.readString(file)).isEqualTo(second + "\n");
    }

    @Test void theProcessIdFileHoldsThisProcessAndIsOwnerOnly() throws Exception {
        Path file = dir.resolve("owner");
        OwnerOnlyFiles.writeProcessId(file);
        assertThat(Files.readString(file)).isEqualTo(ProcessHandle.current().pid() + "\n");
        if (posix(file)) assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
    }

    @Test void restrictSetsTheGivenPermissionsWhereTheFileSystemHasThem() throws Exception {
        Path folder = Files.createDirectory(dir.resolve("daemon"));
        OwnerOnlyFiles.restrict(folder, "rwx------");
        if (posix(folder)) assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo("rwx------");
    }
}
