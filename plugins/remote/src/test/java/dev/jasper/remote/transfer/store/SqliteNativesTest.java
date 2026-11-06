package dev.jasper.remote.transfer.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where sqlite-jdbc extracts its native library: a folder of Jasper's own, per user where the temporary folder is shared.
 * The Browser's copy of the class has the same tests: {@code org.sqlite.tmpdir} is one for the JVM, so both choose alike.
 */
class SqliteNativesTest {
    private static final String PROPERTY = "org.sqlite.tmpdir";
    private String saved;

    @BeforeEach void save() { saved = System.getProperty(PROPERTY); }

    @AfterEach void restore() {
        if (saved == null) System.clearProperty(PROPERTY); else System.setProperty(PROPERTY, saved);
    }

    /** F13: Linux's {@code /tmp} is shared by every user, so each has a folder of their own there. */
    @Test void eachUserHasAFolderOfTheirOwnOnLinux() {
        Path temp = Path.of("tmp");
        assertThat(SqliteNatives.folder("Linux", temp, "ada")).isEqualTo(temp.resolve("jasper-sqlite-ada"));
        assertThat(SqliteNatives.folder("Linux", temp, "CORP\\ada smith")).as("a name that is not a safe file name")
            .isEqualTo(temp.resolve("jasper-sqlite-CORP_ada_smith"));
        assertThat(SqliteNatives.folder("Mac OS X", temp, "ada")).isEqualTo(temp.resolve("jasper-sqlite"));
        assertThat(SqliteNatives.folder("Windows 11", temp, "ada")).isEqualTo(temp.resolve("jasper-sqlite"));
        assertThat(SqliteNatives.folder().getParent()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
    }

    @Test void withoutAChosenFolderSqliteExtractsIntoTheGivenOneMadeAtEachStart(@TempDir Path dir) throws Exception {
        System.clearProperty(PROPERTY);
        Path folder = dir.resolve("jasper-sqlite");
        SqliteNatives.extractInto(folder);
        assertThat(System.getProperty(PROPERTY)).isEqualTo(folder.toString());
        assertThat(folder).isDirectory();
        Files.delete(folder);
        SqliteNatives.extractInto(folder);
        assertThat(folder).isDirectory();
    }

    /** Nobody else may write the folder a library is loaded from. */
    @Test @DisabledOnOs(OS.WINDOWS) void onlyItsOwnerMayUseTheFolder(@TempDir Path dir) throws Exception {
        System.clearProperty(PROPERTY);
        Path folder = Files.createDirectory(dir.resolve("jasper-sqlite"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")));
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxrwxrwx"));
        SqliteNatives.extractInto(folder);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder))).isEqualTo("rwx------");
    }

    @Test void aFolderChosenWithTheSystemPropertyIsKept(@TempDir Path dir) throws Exception {
        String chosen = dir.resolve("chosen").toString();
        System.setProperty(PROPERTY, chosen);
        SqliteNatives.extractInto(dir.resolve("jasper-sqlite"));
        assertThat(System.getProperty(PROPERTY)).isEqualTo(chosen);
        assertThat(dir.resolve("jasper-sqlite")).doesNotExist();
    }
}
