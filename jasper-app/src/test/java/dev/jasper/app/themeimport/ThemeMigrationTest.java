package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeFixtures;
import dev.jasper.app.appearance.ThemeToml;
import dev.jasper.app.appearance.ThemeTomlWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static dev.jasper.app.themeimport.ThemeFilesTest.named;
import static org.assertj.core.api.Assertions.*;

class ThemeMigrationTest {
    @TempDir Path home;
    private static final class Crash extends RuntimeException {}
    private Path plugin(String file, String name) {
        return ThemeFixtures.plugin(home.resolve("themes").resolve(file), name, true, "#123456", "010203");
    }
    static String alacritty() {
        String text = "[colors.primary]\nbackground='#101010'\nforeground='#eeeeee'\n";
        for (String group : java.util.List.of("normal", "bright")) {
            text += "[colors." + group + "]\n";
            for (String color : dev.jasper.app.appearance.ThemeDocument.ANSI_NAMES) text += color + "='#445566'\n";
        }
        return text;
    }
    @Test void completedPassKeepsFailedOriginalAndDiagnostics() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Path bad = Files.write(home.resolve("themes/bad.jar"), new byte[] {1, 2, 3});
        var migration = new ThemeMigration(home); var first = migration.run(); migration.complete(first);
        var second = new ThemeMigration(home).run();
        assertThat(Files.readAllBytes(bad)).containsExactly(1, 2, 3);
        assertThat(second.completed()).isTrue(); assertThat(second.outputs()).isEmpty();
        assertThat(second.problems()).anyMatch(problem -> problem.file().equals(bad));
        assertThat(home.resolve("themes/legacy/.migrated")).exists();
        Files.delete(bad); assertThat(new ThemeMigration(home).problems()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"intent", "output", "original", "before-complete", "complete"})
    void resumesEveryDurableBoundaryWithoutDuplicateOutputs(String boundary) throws Exception {
        Path source = plugin("fixture.jar", "Example"); byte[] original = Files.readAllBytes(source);
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        var interrupted = new ThemeMigration(home, step -> { if (step.equals(boundary) && !once.getAndSet(true)) throw new Crash(); });
        assertThatThrownBy(() -> { var report = interrupted.run(); interrupted.complete(report); }).isInstanceOf(Crash.class);
        var resumed = new ThemeMigration(home); var report = resumed.run();
        resumed.complete(report);
        assertThat(source).doesNotExist();
        try (var files = Files.list(home.resolve("themes"))) { assertThat(files.filter(p -> p.toString().endsWith(".toml")).toList()).hasSize(1); }
        try (var files = Files.list(home.resolve("themes/legacy"))) {
            var originals = files.filter(p -> p.toString().endsWith(".jar")).toList();
            assertThat(originals).hasSize(1); assertThat(Files.readAllBytes(originals.getFirst())).isEqualTo(original);
        }
        assertThat(new ThemeMigration(home).run().outputs()).isEmpty();
    }
    @Test void partialProviderArchiveKeepsItsOriginalAndDoesNotRepeatAfterCompletion() throws Exception {
        Path source = ThemeFixtures.jar(home.resolve("themes/partial.jar"), ThemeFixtures.entries(
            "META-INF/plugin.xml", ThemeFixtures.pluginXml("Partial", "one.theme.json", "two.theme.json", "bad.theme.json"),
            "one.theme.json", ThemeFixtures.theme("One", true, null, "#123456"),
            "two.theme.json", ThemeFixtures.theme("Two", true, null, "#654321"), "bad.theme.json", "broken"));
        byte[] original = Files.readAllBytes(source);
        var migration = new ThemeMigration(home); var first = migration.run();
        assertThat(first.outputs()).hasSize(2); assertThat(first.problems()).anyMatch(p -> p.message().contains("bad.theme.json"));
        migration.complete(first);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        assertThat(new ThemeMigration(home).run().outputs()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"a.jar", "z.jar"})
    void renamesOnlyThePreviouslyWinningSlotOwner(String archiveName) throws Exception {
        plugin(archiveName, "Example");
        Path nativeFile = Files.writeString(home.resolve("themes/m.toml"), ThemeTomlWriter.write(named("Example")));
        String nativeBytes = Files.readString(nativeFile);
        Files.createDirectory(home.resolve("terminal-themes"));
        Files.writeString(home.resolve("terminal-themes/example.toml"), alacritty());
        var report = new ThemeMigration(home).run();
        assertThat(report.outputs()).hasSize(2);
        assertThat(Files.readString(nativeFile)).isEqualTo(nativeBytes);
        if (archiveName.equals("a.jar")) {
            assertThat(report.chromeRenames()).containsEntry("example", "Example 2");
            assertThat(report.workspaceRenames()).containsEntry("example", "Example 2");
            var output = report.outputs().stream().map(path -> {
                try { return ThemeToml.parse(Files.readString(path), path.toString()); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }).filter(doc -> doc.name().equals("Example 2")).findFirst().orElseThrow();
            assertThat(output.seeds()).containsEntry(dev.jasper.app.appearance.Seed.BACKGROUND, new java.awt.Color(0x010203));
        } else {
            assertThat(report.chromeRenames()).doesNotContainKey("example");
            assertThat(report.workspaceRenames()).doesNotContainKey("example");
        }
    }
    @Test void occupiedEqualByteLegacyDestinationNeverDeletesTheOriginalInsteadOfMovingIt() throws Exception {
        Path source = plugin("fixture.jar", "Example"); byte[] original = Files.readAllBytes(source);
        Files.createDirectories(home.resolve("themes/legacy"));
        // Occupy both natural naming forms; equal bytes must not be treated as a completed move.
        Path first = Files.write(home.resolve("themes/legacy/fixture.jar"), original);
        Path second = Files.write(home.resolve("themes/legacy/themes-fixture.jar"), original);
        var report = new ThemeMigration(home).run();
        assertThat(report.originals()).singleElement().satisfies(path -> assertThat(path).isNotEqualTo(first).isNotEqualTo(second));
        assertThat(Files.readAllBytes(first)).isEqualTo(original); assertThat(Files.readAllBytes(second)).isEqualTo(original);
        assertThat(Files.readAllBytes(report.originals().getFirst())).isEqualTo(original);
    }
    @Test void sourceChangedAfterIntentBlocksAutomaticResume() throws Exception {
        Path source = plugin("fixture.jar", "Example");
        var migration = new ThemeMigration(home, step -> { if (step.equals("intent")) throw new Crash(); });
        assertThatThrownBy(migration::run).isInstanceOf(Crash.class);
        Files.writeString(source, "changed source");
        var resumed = new ThemeMigration(home).run();
        assertThat(resumed.outputs()).isEmpty(); assertThat(resumed.problems()).anyMatch(p -> p.message().contains("changed"));
        assertThat(Files.readString(source)).isEqualTo("changed source");
    }
    @Test void editedOutputAfterPublicationIsNeverOverwrittenOrDuplicated() throws Exception {
        Path source = plugin("fixture.jar", "Example");
        var migration = new ThemeMigration(home, step -> { if (step.equals("output")) throw new Crash(); });
        assertThatThrownBy(migration::run).isInstanceOf(Crash.class);
        Path output;
        try (var files = Files.list(home.resolve("themes"))) { output = files.filter(p -> p.toString().endsWith(".toml")).findFirst().orElseThrow(); }
        Files.writeString(output, "# edited\n" + Files.readString(output)); String edited = Files.readString(output);
        var resumed = new ThemeMigration(home).run();
        assertThat(Files.readString(output)).isEqualTo(edited); assertThat(source).exists();
        assertThat(resumed.problems()).anyMatch(p -> p.message().contains("output") && p.message().contains("changed"));
        try (var files = Files.list(home.resolve("themes"))) { assertThat(files.filter(p -> p.toString().endsWith(".toml")).toList()).hasSize(1); }
    }
    @Test void failedInputChangesAfterMarkerRequireManualImport() throws Exception {
        Files.createDirectories(home.resolve("themes")); Path bad = Files.writeString(home.resolve("themes/bad.jar"), "broken");
        var migration = new ThemeMigration(home); migration.complete(migration.run());
        ThemeFixtures.plugin(bad, "Repaired", true, "#123456", "010203");
        var report = new ThemeMigration(home).run();
        assertThat(report.outputs()).isEmpty(); assertThat(bad).exists();
        assertThat(report.problems()).anyMatch(p -> p.message().contains("changed") && p.message().contains("Import"));
    }

    @Test void oversizedConvertedOutputDoesNotAbortOtherSources() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Path bad = Files.writeString(home.resolve("themes/a.theme.json"),
            "{\"name\":\"Huge\",\"dark\":true,\"ui\":{\"Some.value\":\"" + "x".repeat(270000) + "\"}}");
        plugin("z.jar", "Good");
        var report = new ThemeMigration(home).run();
        assertThat(report.outputs()).hasSize(1); assertThat(bad).exists();
        assertThat(report.problems()).anyMatch(p -> p.file().equals(bad));
    }
    @Test void missingSourceAndLateOccupiedDestinationRetainOtherSources() throws Exception {
        Path missing = plugin("a.jar", "Missing"); Path blocked = plugin("b.jar", "Blocked"); plugin("c.jar", "Good");
        var stopped = new ThemeMigration(home, step -> { if (step.equals("intent")) throw new Crash(); });
        assertThatThrownBy(stopped::run).isInstanceOf(Crash.class);
        Files.delete(missing);
        Path occupied = Files.writeString(home.resolve("themes/legacy/themes-b.jar"), "foreign");
        var resumed = new ThemeMigration(home).run();
        assertThat(resumed.outputs()).hasSize(2); assertThat(blocked).exists();
        assertThat(Files.readString(occupied)).isEqualTo("foreign");
        assertThat(resumed.originals()).hasSize(1);
        assertThat(resumed.problems()).anyMatch(p -> p.message().contains("missing"));
    }
    @Test void localParentCanMoveBeforeInterruptedChildResumes() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/a.theme.json"), "{\"name\":\"Parent\",\"dark\":true,\"ui\":{\"Panel.background\":\"#123456\"}}");
        Files.writeString(home.resolve("themes/b.theme.json"), "{\"name\":\"Child\",\"dark\":true,\"parentTheme\":\"a\"}");
        var stopped = new ThemeMigration(home, step -> { if (step.equals("original")) throw new Crash(); });
        assertThatThrownBy(stopped::run).isInstanceOf(Crash.class);
        var report = new ThemeMigration(home).run();
        assertThat(report.outputs()).hasSize(2); assertThat(report.originals()).hasSize(2);
        Path child = report.outputs().stream().filter(p -> p.getFileName().toString().equals("child.toml")).findFirst().orElseThrow();
        assertThat(ThemeToml.parse(Files.readString(child), "child").ui()).containsEntry("Panel.background", "#123456");
    }
    @Test void oldBuiltinAndUserAlacrittyPrecedenceIsPreserved() throws Exception {
        Files.createDirectories(home.resolve("terminal-themes"));
        Files.writeString(home.resolve("terminal-themes/darcula.toml"), alacritty());
        Files.writeString(home.resolve("terminal-themes/catppuccin-frappe.toml"), alacritty());
        var report = new ThemeMigration(home).run();
        assertThat(report.chromeRenames()).isEmpty();
        assertThat(report.workspaceRenames()).doesNotContainKeys("darcula", "catppuccin frappe");
        assertThat(report.outputs()).hasSize(2);
    }
}
