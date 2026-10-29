package dev.jasper.remote.transfer.store;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class SqliteNativesTest {
    private static final String PROPERTY = "org.sqlite.tmpdir";
    private String saved;

    @BeforeEach void save() { saved = System.getProperty(PROPERTY); }

    @AfterEach void restore() {
        if (saved == null) System.clearProperty(PROPERTY); else System.setProperty(PROPERTY, saved);
    }

    @Test void sqliteExtractsIntoJaspersOwnTemporaryFolder() {
        assertThat(SqliteNatives.folder()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "jasper-sqlite"));
    }

    @Test void withoutAChosenFolderSqliteExtractsIntoTheGivenOneMadeAtEachStart(@TempDir Path dir) throws Exception {
        System.clearProperty(PROPERTY);
        Path folder = dir.resolve("jasper-sqlite");
        SqliteNatives.extractInto(folder);
        assertThat(System.getProperty(PROPERTY)).isEqualTo(folder.toString());
        assertThat(folder).isDirectory();
        // A later start, after the folder was cleaned away, makes it again.
        Files.delete(folder);
        SqliteNatives.extractInto(folder);
        assertThat(folder).isDirectory();
    }

    @Test void aFolderChosenWithTheSystemPropertyIsKept(@TempDir Path dir) throws Exception {
        String chosen = dir.resolve("chosen").toString();
        System.setProperty(PROPERTY, chosen);
        SqliteNatives.extractInto(dir.resolve("jasper-sqlite"));
        assertThat(System.getProperty(PROPERTY)).isEqualTo(chosen);
        assertThat(dir.resolve("jasper-sqlite")).doesNotExist();
    }
}
