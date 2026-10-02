package dev.jasper.app.application;

import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.config.ThemeSelectionMigration;
import dev.jasper.app.themeimport.ThemeMigration;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ThemeUpgradeTest {
    @TempDir Path home;
    @Test void configWriteCrashDoesNotApplyARenameChainTwiceEvenAfterUnrelatedEdit() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "ui.theme.name='A'\n");
        var migration = new ThemeMigration(home); var report = migration.run();
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("a", "A 2", "a 2", "A 3"), Map.of()).orElseThrow();
        migration.recordConfigIntent(report.generation(), new ThemeMigration.ConfigIntent("config.toml", edit.expectedSha256(), edit.desiredSha256(), edit.before(), edit.after()));
        ThemeSelectionMigration.apply(config, false, edit);
        Files.writeString(config, Files.readString(config) + "# unrelated edit after crash\n");
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty();
        assertThat(Files.readString(config)).contains("A 2", "# unrelated").doesNotContain("A 3");
        assertThat(home.resolve("themes/legacy/.migrated")).exists();
    }
    @Test void changedThemeOrDifferentOverrideLeavesIntentPending() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "ui.theme.name='A'\n");
        var migration = new ThemeMigration(home); var report = migration.run();
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("a", "A 2"), Map.of()).orElseThrow();
        migration.recordConfigIntent(report.generation(), new ThemeMigration.ConfigIntent("config.toml", edit.expectedSha256(), edit.desiredSha256(), edit.before(), edit.after()));
        Path other = home.resolve("other.toml"); Files.writeString(other, "ui.theme.name='A'\n");
        assertThat(ThemeUpgrade.prepare(other, false)).anyMatch(p -> p.message().contains("config"));
        Files.writeString(config, "ui.theme.name='Changed'\n");
        assertThat(ThemeUpgrade.prepare(config, false)).anyMatch(p -> p.message().contains("changed"));
        assertThat(home.resolve("themes/legacy/.migrated")).doesNotExist(); assertThat(Files.readString(config)).contains("Changed");
    }
    @Test void rejectedConfigRetainsPendingMigrationAndFailedInputDiagnostics() throws Exception {
        Path config = home.resolve("config.toml"); Files.writeString(config, "bad=[");
        Files.createDirectories(home.resolve("themes")); Path bad = Files.writeString(home.resolve("themes/bad.jar"), "broken");
        ThemeFixtures.plugin(home.resolve("themes/good.jar"), "Example", true, "#123456", "010203");
        assertThat(ThemeUpgrade.prepare(config, false)).anyMatch(p -> p.file().equals(bad));
        assertThat(Files.readString(config)).isEqualTo("bad=["); assertThat(home.resolve("themes/legacy/.migrated")).doesNotExist();
        Files.writeString(config, "ui.theme.name='Example'\n"); ThemeUpgrade.prepare(config, false);
        assertThat(home.resolve("themes/legacy/.migrated")).exists();
        assertThat(ThemeUpgrade.diagnostics(config)).anyMatch(p -> p.file().equals(bad));
        Files.delete(bad); assertThat(ThemeUpgrade.diagnostics(config)).isEmpty();
    }
    @Test void noConfigDoesNotCreateOneAndMarkedRerunsDoNotConvertNewFiles() throws Exception {
        Path config = home.resolve("config.toml");
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty(); assertThat(config).doesNotExist();
        Path source = ThemeFixtures.plugin(home.resolve("themes/new.jar"), "New", true, "#123456", "010203");
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty(); assertThat(source).exists();
    }
    @Test void pendingAliasEditUsesParserPrecedenceAndPreservesConcurrentComments() throws Exception {
        Path config = home.resolve("config.toml");
        Files.writeString(config, "ui.theme.variant='unrecognized'\nterminal.colors='First'\nui.theme.terminal='Second'\n");
        var migration = new ThemeMigration(home); var report = migration.run();
        var edit = ThemeSelectionMigration.plan(config, false, Map.of("jasper dark", "Custom"), Map.of("first", "First 2", "second", "Second 2")).orElseThrow();
        migration.recordConfigIntent(report.generation(), new ThemeMigration.ConfigIntent("config.toml", edit.expectedSha256(), edit.desiredSha256(), edit.before(), edit.after()));
        Files.writeString(config, Files.readString(config) + "# concurrent comment\n");
        assertThat(ThemeUpgrade.prepare(config, false)).isEmpty();
        assertThat(ThemeSelectionMigration.values(config, false)).isEqualTo(edit.after());
        assertThat(Files.readString(config)).contains("# concurrent comment");
    }
}
