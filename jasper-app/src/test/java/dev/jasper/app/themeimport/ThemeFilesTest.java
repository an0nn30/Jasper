package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.Theme;
import dev.jasper.ui.theme.ThemeDocument;
import dev.jasper.ui.themeio.ThemeToml;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ThemeFilesTest {
    @TempDir Path home;
    static ThemeDocument named(String name) {
        var base = Theme.DARK.source().document();
        return new ThemeDocument(base.origin(), name, base.dark(), base.parent(), 0, base.uiComplete(), base.metadata(),
            base.seeds(), base.ansi(), base.tokens(), base.syntax(), base.ui(), base.icons());
    }
    static ThemeDocument withUi(String name, java.util.Map<String, Object> ui) {
        var base = named(name);
        return new ThemeDocument(base.origin(), name, base.dark(), base.parent(), 0, base.uiComplete(), base.metadata(),
            base.seeds(), base.ansi(), java.util.Map.of(), base.syntax(), ui, base.icons(), base.workspace(), base.semanticOverrides());
    }
    static java.util.List<java.util.Map<String, Object>> invalidUi() {
        return java.util.List.of(java.util.Map.of("Panel.background", "@missing"),
            java.util.Map.of("Panel.background", "@one", "@one", "@two", "@two", "@one"));
    }
    @Test void unresolvedColorsCannotCreateOrReplaceNativeFiles() throws IOException {
        var files = new ThemeFiles(home);
        Path target = files.create(named("Example"));
        byte[] original = Files.readAllBytes(target);
        String expected = files.fingerprint(target);
        for (var ui : invalidUi()) {
            assertThatThrownBy(() -> files.replace(target, expected, withUi("Example", ui)))
                .isInstanceOf(IOException.class).hasMessageContaining("cannot resolve [ui] color Panel.background");
            assertThatThrownBy(() -> files.create(withUi("New", ui))).isInstanceOf(IOException.class);
            assertThat(Files.readAllBytes(target)).isEqualTo(original);
            try (var entries = Files.list(target.getParent())) {
                assertThat(entries.map(path -> path.getFileName().toString())).containsExactly("example.toml");
            }
        }
    }
    @Test void refusesStaleReplacement() throws IOException {
        var files = new ThemeFiles(home);
        Path target = files.create(named("Example")); String expected = files.fingerprint(target);
        Files.writeString(target, "# user edit\n" + Files.readString(target));
        String edited = Files.readString(target);
        assertThatThrownBy(() -> files.replace(target, expected, named("Updated"))).isInstanceOf(IOException.class);
        assertThat(Files.readString(target)).isEqualTo(edited);
    }
    @Test void safeNamesAndCaseInsensitiveOccupancyAreIndependentOfThemeNames() throws IOException {
        var files = new ThemeFiles(home); Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/EXAMPLE.toml"), "keep");
        Path second = files.create(named("Example"));
        assertThat(second.getFileName().toString()).isEqualTo("example-2.toml");
        assertThat(Files.readString(home.resolve("themes/EXAMPLE.toml"))).isEqualTo("keep");
        for (String name : java.util.List.of("CON", "NUL", "LPT1", "../escape", "...", "終端", "Name. ")) {
            Path created = files.create(named(name));
            assertThat(created.getParent()).isEqualTo(home.toRealPath().resolve("themes"));
            assertThat(created.getFileName().toString()).matches("[a-z0-9][a-z0-9-]*\\.toml");
            assertThat(created.getFileName().toString()).doesNotMatch("(?i)(con|nul|lpt1)\\.toml");
            assertThat(ThemeToml.parse(Files.readString(created), created.toString()).name()).isEqualTo(name.strip());
        }
    }
    @Test void rejectsUnsafeTargets() throws IOException {
        Path elsewhere = Files.createDirectory(home.resolve("elsewhere"));
        Files.createSymbolicLink(home.resolve("themes"), elsewhere);
        assertThatThrownBy(() -> new ThemeFiles(home).create(named("Escape"))).isInstanceOf(IOException.class);
        assertThat(elsewhere).isEmptyDirectory(); Files.delete(home.resolve("themes"));
        var files = new ThemeFiles(home); Path target = files.create(named("Normal")); String hash = files.fingerprint(target);
        Path link = home.resolve("themes/link.toml"); Files.createSymbolicLink(link, target);
        assertThatThrownBy(() -> files.replace(link, hash, named("Escape"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> files.replace(home.resolve("themes/../outside.toml"), hash, named("Escape"))).isInstanceOf(IOException.class);
        Path folder = Files.createDirectory(home.resolve("themes/folder.toml"));
        assertThatThrownBy(() -> files.fingerprint(folder)).isInstanceOf(IOException.class);
    }
    @Test void failedPublicationPreservesPreviousBytesAndRemovesOnlyOwnedTemp() throws IOException {
        var normal = new ThemeFiles(home); Path target = normal.create(named("Example"));
        byte[] original = Files.readAllBytes(target); String expected = normal.fingerprint(target);
        Path unrelated = Files.writeString(home.resolve("themes/.other.tmp"), "keep");
        for (String step : java.util.List.of("temp", "publish")) {
            var broken = new ThemeFiles(home, (stage, path) -> {
                if (stage.equals(step)) throw new UncheckedIOException(new IOException("injected " + stage));
            });
            assertThatThrownBy(() -> broken.replace(target, expected, named("Changed"))).isInstanceOf(IOException.class);
            assertThat(Files.readAllBytes(target)).isEqualTo(original);
            assertThat(Files.readString(unrelated)).isEqualTo("keep");
            try (var files = Files.list(target.getParent())) { assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder("example.toml", ".other.tmp"); }
        }
    }
    @Test void publicationRechecksEditsAfterTheTemporaryIsWritten() throws IOException {
        var normal = new ThemeFiles(home); Path target = normal.create(named("Example")); String hash = normal.fingerprint(target);
        var racing = new ThemeFiles(home, (stage, path) -> {
            if (stage.equals("publish")) try { Files.writeString(target, "external edit"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        assertThatThrownBy(() -> racing.replace(target, hash, named("Changed"))).isInstanceOf(IOException.class);
        assertThat(Files.readString(target)).isEqualTo("external edit");
    }
    @Test void invalidDocumentAndOversizedOutputCreateNothing() throws IOException {
        var files = new ThemeFiles(home);
        assertThatThrownBy(() -> files.create(named("x".repeat(300_000)))).isInstanceOf(IOException.class);
        assertThat(home.resolve("themes")).doesNotExist();
    }

    @Test void concurrentCreatorsNeverOverwriteAndShareTheHomeLock() throws Exception {
        var first = new ThemeFiles(home); var second = new ThemeFiles(home);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Path>>();
            for (int i = 0; i < 12; i++) {
                ThemeFiles files = i % 2 == 0 ? first : second;
                tasks.add(pool.submit(() -> files.create(named("Concurrent"))));
            }
            var paths = new java.util.HashSet<Path>();
            for (var future : tasks) {
                Path path = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(paths.add(path)).isTrue();
                assertThat(ThemeToml.parse(Files.readString(path), path.toString()).name()).isEqualTo("Concurrent");
            }
        }
    }

    @Test void aTargetAppearingAtPublicationIsNeverClobbered() throws IOException {
        var racing = new ThemeFiles(home, (stage, path) -> {
            if (stage.equals("publish")) try { Files.writeString(path, "external file"); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        assertThatThrownBy(() -> racing.create(named("Example"))).isInstanceOf(IOException.class);
        assertThat(Files.readString(home.resolve("themes/example.toml"))).isEqualTo("external file");
    }

    @Test void swappingTheThemesDirectoryForASymlinkDoesNotTouchTheReplacement() throws IOException {
        Path outside = Files.createDirectory(home.resolve("outside"));
        Path saved = home.resolve("saved");
        var racing = new ThemeFiles(home, (stage, path) -> {
            if (stage.equals("publish")) try {
                Files.move(home.resolve("themes"), saved);
                Files.createSymbolicLink(home.resolve("themes"), outside);
                // A foreign file with the owned temporary's name must never be cleaned up through the symlink.
                try (var pending = Files.list(saved)) {
                    for (Path file : pending.toList()) Files.writeString(outside.resolve(file.getFileName()), "foreign");
                }
            } catch (IOException failure) { throw new UncheckedIOException(failure); }
        });
        assertThatThrownBy(() -> racing.create(named("Example"))).isInstanceOf(IOException.class);
        try (var foreign = Files.list(outside)) { assertThat(foreign.toList()).hasSize(1); }
        assertThat(outside.resolve("example.toml")).doesNotExist();
    }

    @Test void publicationAndImportRejectTheEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            assertThatThrownBy(() -> new ThemeFiles(home).create(named("EDT"))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new ThemeImporter(home).plan(home.resolve("source.toml"))).isInstanceOf(IllegalStateException.class);
        });
        assertThat(home.resolve("themes")).doesNotExist();
    }
}
