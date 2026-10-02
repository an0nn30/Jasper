package dev.jasper.app.themeimport;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MigrationJournalTest {
    @TempDir Path home;
    @Test void writerRejectsInvalidIntentBeforePublishingIt() throws Exception {
        Files.createDirectories(home.resolve("themes/legacy"));
        var journal = new MigrationJournal(home);
        var source = new MigrationJournal.Source("themes/../escape.jar", "0".repeat(64), "identity", "themes/legacy/source.jar", false, false, false, java.util.List.of(), java.util.List.of());
        assertThatThrownBy(() -> journal.write(new MigrationJournal.State(java.util.UUID.randomUUID().toString(), java.util.List.of(source), java.util.Map.of(), null)))
            .isInstanceOf(java.io.IOException.class);
        assertThat(journal.file()).doesNotExist();
    }

    @Test void corruptJournalAndMarkerFailClosedWithoutMovingOriginals() throws Exception {
        Files.createDirectories(home.resolve("themes/legacy"));
        Path original = dev.jasper.app.appearance.ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Example", true, "#123456", "010203");
        byte[] bytes = Files.readAllBytes(original);
        Files.writeString(home.resolve("themes/legacy/migration.toml"), "not a valid journal");
        Files.writeString(home.resolve("themes/legacy/.migrated"), "complete");
        var report = new ThemeMigration(home).run();
        assertThat(report.completed()).isFalse(); assertThat(report.outputs()).isEmpty(); assertThat(report.problems()).isNotEmpty();
        assertThat(Files.readAllBytes(original)).isEqualTo(bytes);
    }
    @Test void journalReadIsBoundedAndCompletionRejectsAnotherGeneration() throws Exception {
        var migration = new ThemeMigration(home); var report = migration.run();
        var forged = new ThemeMigration.MigrationReport("wrong", false, report.chromeRenames(), report.workspaceRenames(), report.outputs(), report.originals(), report.problems());
        assertThatThrownBy(() -> migration.complete(forged)).isInstanceOf(java.io.IOException.class);
        assertThat(home.resolve("themes/legacy/.migrated")).doesNotExist();
        Files.writeString(home.resolve("themes/legacy/migration.toml"), " ".repeat((4 << 20) + 1));
        assertThat(new ThemeMigration(home).run().problems()).anyMatch(p -> p.message().contains("4 MiB"));
    }

    @Test void markerWithWrongValueTypeFailsClosed() throws Exception {
        var migration = new ThemeMigration(home); migration.run();
        Files.writeString(home.resolve("themes/legacy/.migrated"), "version = []\ngeneration = []\n");
        assertThat(migration.run().problems()).isNotEmpty();
    }
    @Test void traversingJournalAndSymlinkOutputsCannotEscapeHome() throws Exception {
        Path original = dev.jasper.app.appearance.ThemeFixtures.plugin(home.resolve("themes/fixture.jar"), "Example", true, "#123456", "010203");
        var stopped = new ThemeMigration(home, stage -> { if (stage.equals("intent")) throw new IllegalStateException("stop"); });
        assertThatThrownBy(stopped::run).isInstanceOf(IllegalStateException.class);
        Path journal = home.resolve("themes/legacy/migration.toml"); String good = Files.readString(journal);
        Files.writeString(journal, good.replace("themes/example.toml", "themes/../../escaped.toml"));
        assertThat(new ThemeMigration(home).run().outputs()).isEmpty(); assertThat(original).exists();
        Files.writeString(journal, good);
        Path foreign = Files.writeString(home.resolve("foreign"), "untouched");
        Files.createSymbolicLink(home.resolve("themes/example.toml"), foreign);
        assertThat(new ThemeMigration(home).run().problems()).isNotEmpty();
        assertThat(Files.readString(foreign)).isEqualTo("untouched"); assertThat(original).exists();
    }
}
