package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ThemeCatalogTest {
    @TempDir Path home;

    private Path themes() { return home.resolve("themes"); }
    private ThemeCatalog catalog() { return ThemeCatalog.beside(home.resolve("config.toml")); }

    @Test void theBuiltInCatalogFindsBuiltInThemesByNameIgnoringCaseAndSpaces() {
        var snapshot = ThemeCatalog.builtIn().scan();
        assertThat(snapshot.themes()).containsExactlyElementsOf(ThemeManager.builtIns());
        assertThat(snapshot.find("  jasper DARK ")).contains(Theme.DARK);
        assertThat(snapshot.find("intellij light")).contains(Theme.INTELLIJ_LIGHT);
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.find("Dracula")).isEmpty();
        assertThat(snapshot.names()).containsExactly("Darcula", "IntelliJ Light", "Jasper Dark");
        assertThat(snapshot.problems()).isEmpty();
    }

    @Test void anInstalledThemeCarriesItsResolvedNameDarknessAndSchemePalette() {
        plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme theme = snapshot.find("fixture dark").orElseThrow();
        assertThat(theme.name()).isEqualTo("Fixture Dark");
        assertThat(theme.dark()).isTrue();
        assertThat(theme.id()).isEqualTo("installed:fixture.jar!/themes/Fixture_Dark.theme.json");
        assertThat(theme.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(theme.palette().ansi().get(1)).isEqualTo(new Color(0xff5555));
        assertThat(theme.palette().ansi().get(2)).as("Darcula's green").isEqualTo(new Color(0x5c962c));
        assertThat(theme.source()).isInstanceOfSatisfying(Theme.Archive.class,
            archive -> assertThat(archive.archive()).isEqualTo(themes().resolve("fixture.jar")));
        assertThat(theme.source().document().name()).isEqualTo("Fixture Dark");
        assertThat(snapshot.themes()).as("installed themes come first").startsWith(theme).endsWith(Theme.INTELLIJ_LIGHT, Theme.DARK, Theme.DARCULA);
        assertThat(catalog().latest().themes()).as("a new catalog has scanned nothing yet").containsExactlyElementsOf(ThemeManager.builtIns());
    }

    @Test void anInstalledThemeTakesPrecedenceOverABuiltInThemeOfTheSameName() {
        plugin(themes().resolve("mine.jar"), "Jasper Dark", true, "#010203", "010203");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Jasper Dark").map(Theme::source)).get().isInstanceOf(Theme.Archive.class);
        assertThat(snapshot.themes()).doesNotContain(Theme.DARK).contains(Theme.INTELLIJ_LIGHT);
    }

    @Test void duplicateInstalledNamesWarnAndTheAlphabeticallyFirstFileWins() {
        plugin(themes().resolve("b.jar"), "Twin", true, "#bbbbbb", "0b0b0b");
        plugin(themes().resolve("A.jar"), "Twin", true, "#aaaaaa", "0a0a0a");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Twin").map(Theme::source)).get().isInstanceOfSatisfying(Theme.Archive.class,
            archive -> assertThat(archive.archive().getFileName()).hasToString("A.jar"));
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("b.jar");
            assertThat(problem.message()).contains("Twin").contains("A.jar");
        });
    }

    @Test void aRescanRereadsOnlyFilesWhoseSizeOrTimeChanged() throws Exception {
        plugin(themes().resolve("one.jar"), "One", true, "#111111", "111111");
        plugin(themes().resolve("two.jar"), "Two", false, "#222222", "222222");
        var reads = new ArrayList<String>();
        var catalog = new ThemeCatalog(themes(), file -> { reads.add(file.getFileName().toString()); return ThemeArchive.read(file); });
        Theme one = catalog.scan().find("One").orElseThrow();
        assertThat(catalog.scan().find("One")).as("an unchanged file keeps its Theme instance").containsSame(one);
        assertThat(reads).containsExactlyInAnyOrder("one.jar", "two.jar");
        plugin(themes().resolve("two.jar"), "Two Again", false, "#232323", "232323");
        Files.setLastModifiedTime(themes().resolve("two.jar"), FileTime.fromMillis(System.currentTimeMillis() + 60_000));
        var snapshot = catalog.scan();
        assertThat(reads).containsExactlyInAnyOrder("one.jar", "two.jar", "two.jar");
        assertThat(snapshot.find("Two Again")).isPresent();
        assertThat(snapshot.find("Two")).isEmpty();
        Files.delete(themes().resolve("one.jar"));
        assertThat(catalog.scan().find("One")).isEmpty();
        assertThat(catalog.latest().find("Two Again")).isPresent();
    }

    @Test void onlyPluginArchivesAreReadAndLooseThemeFilesAreExplained() throws Exception {
        Files.createDirectories(themes().resolve("folder"));
        Files.writeString(themes().resolve("notes.txt"), "hello");
        Files.writeString(themes().resolve("Dracula.theme.json"), theme("Dracula", true, null, "#282a36"));
        plugin(themes().resolve(".hidden.jar"), "Hidden", true, "#000000", "000000");
        var snapshot = catalog().scan();
        assertThat(snapshot.names()).containsExactly("Darcula", "IntelliJ Light", "Jasper Dark");
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("Dracula.theme.json");
            assertThat(problem.message()).contains("aren't supported").contains(".jar or .zip");
        });
    }

    @Test void theFolderIsCreatedAndAnUnusableOneIsAWarning() throws Exception {
        catalog().scan();
        assertThat(themes()).isDirectory();
        Path other = Files.createDirectories(home.resolve("other"));
        Files.writeString(other.resolve("themes"), "a file, not a folder");
        var snapshot = ThemeCatalog.beside(other.resolve("config.toml")).scan();
        assertThat(snapshot.themes()).containsExactlyElementsOf(ThemeManager.builtIns());
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.message()).contains("Cannot read the themes folder"));
    }

    @Test void brokenThemesAndLoaderWarningsBecomeProblemsWhileGoodThemesLoad() {
        jar(themes().resolve("mixed.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Mixed", "/good.theme.json", "/bad.theme.json", "/newui.theme.json"),
            "good.theme.json", theme("Good", false, null, "#fefefe"),
            "bad.theme.json", "{\"name\": \"Bad\", \"colors\": {\"a\": \"nowhere\"}}",
            "newui.theme.json", "{\"name\": \"New UI\", \"dark\": true, \"parentTheme\": \"SomeoneElsesTheme\"}"));
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Good").map(Theme::palette)).contains(Palette.jasperLight());
        assertThat(snapshot.find("Bad")).isEmpty();
        assertThat(snapshot.find("New UI")).isPresent();
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::message)
            .anySatisfy(message -> assertThat(message).contains("mixed.jar!/bad.theme.json").contains("nowhere"))
            .anySatisfy(message -> assertThat(message).contains("SomeoneElsesTheme").contains("Darcula"));
    }

    @Test void aReaderThatThrowsAnErrorBecomesAProblemAndTheScanContinues() throws Exception {
        plugin(themes().resolve("good.jar"), "Good", true, "#111111", "111111");
        Files.writeString(themes().resolve("bad.jar"), "x");
        var catalog = new ThemeCatalog(themes(), file -> {
            if (file.getFileName().toString().equals("bad.jar")) throw new StackOverflowError();
            return ThemeArchive.read(file);
        });
        var snapshot = catalog.scan();
        assertThat(snapshot.find("Good")).isPresent();
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("bad.jar");
            assertThat(problem.message()).contains("cannot be read");
        });
    }

    @Test void aFileWhoseThemesCannotBeBuiltBecomesAProblemNotAThrow() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("odd.jar"), "x");
        var catalog = new ThemeCatalog(themes(), file -> new ThemeArchive.Contents(
            java.util.List.of(new ThemeArchive.Found("odd.jar!/a.theme.json", null, null, null, null)), java.util.List.of()));
        var snapshot = catalog.scan();
        assertThat(snapshot.themes()).containsExactlyElementsOf(ThemeManager.builtIns());
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName()).hasToString("odd.jar");
            assertThat(problem.message()).contains("cannot be read");
        });
    }

    @Test void withinOnePluginTheFirstProviderOfANameWins() {
        jar(themes().resolve("pair.jar"), entries(
            "META-INF/plugin.xml", pluginXml("Pair", "/one.theme.json", "/two.theme.json"),
            "one.theme.json", theme("Same", true, null, "#111111"),
            "two.theme.json", theme("Same", true, null, "#222222")));
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Same").map(Theme::source)).get().isInstanceOfSatisfying(Theme.Archive.class,
            archive -> assertThat(archive.entry()).isEqualTo("pair.jar!/one.theme.json"));
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.message()).contains("\"Same\"").contains("provided twice"));
    }

    @Test void inspectingAnArchiveNamesItsThemesWithoutInstallingIt() {
        Path file = plugin(home.resolve("elsewhere/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var inspection = ThemeCatalog.inspect(file);
        assertThat(inspection.themes()).containsExactly("Fixture Dark");
        assertThat(inspection.problems()).isEmpty();
        Path empty = jar(home.resolve("elsewhere/empty.jar"), entries("META-INF/plugin.xml", pluginXml("Nothing")));
        assertThat(ThemeCatalog.inspect(empty).themes()).isEmpty();
        assertThat(ThemeCatalog.inspect(home.resolve("elsewhere/missing.jar")).problems()).isNotEmpty();
        assertThat(catalog().directory()).contains(themes());
        assertThat(ThemeCatalog.builtIn().directory()).isEmpty();
        assertThat(themes()).doesNotExist();
    }
}
