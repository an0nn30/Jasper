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
    private ThemeCatalog catalog() { return dev.jasper.app.themeimport.LegacyThemeReader.catalog(home.resolve("config.toml")); }

    @Test void theBuiltInCatalogFindsBuiltInThemesByNameIgnoringCaseAndSpaces() {
        var snapshot = ThemeCatalog.builtIn().scan();
        assertThat(snapshot.themes()).containsExactlyElementsOf(ThemeManager.builtIns());
        assertThat(snapshot.find("  jasper DARK ")).contains(Theme.DARK);
        assertThat(snapshot.find("intellij light")).contains(Theme.INTELLIJ_LIGHT);
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.find("Dracula")).isEmpty();
        assertThat(snapshot.names()).containsExactly("Darcula", "IntelliJ Light", "Jasper Dark", "Jasper Light");
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
        assertThat(snapshot.themes()).as("installed themes come first").startsWith(theme).endsWith(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA);
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

    @Test void anInvalidOverrideDoesNotHideTheValidParentOfAnotherTheme() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("a.toml"), "name = \"Jasper Dark\"\n");
        Files.writeString(themes().resolve("b.toml"), "name = \"Child\"\nextends = \"Jasper Dark\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Jasper Dark")).containsSame(Theme.DARK);
        assertThat(snapshot.find("Child")).get().extracting(Theme::palette).isEqualTo(Theme.DARK.palette());
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.file()).isEqualTo(themes().resolve("a.toml")));
    }

    @Test void anInvalidDuplicateDoesNotHideALaterUserParent() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("a.toml"), "name = \"Parent\"\nextends = \"Missing\"\n");
        Files.writeString(themes().resolve("b.toml"), "name = \"Parent\"\nextends = \"Jasper Light\"\n");
        Files.writeString(themes().resolve("c.toml"), "name = \"Child\"\nextends = \"Parent\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Parent")).get().extracting(Theme::palette).isEqualTo(Theme.JASPER_LIGHT.palette());
        assertThat(snapshot.find("Child")).get().extracting(Theme::palette).isEqualTo(Theme.JASPER_LIGHT.palette());
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.file()).isEqualTo(themes().resolve("a.toml")));
    }

    @Test void aBuiltInFallbackDoesNotHideACycleBetweenUserThemes() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("a.toml"), "name = \"Jasper Dark\"\nextends = \"Loop\"\n");
        Files.writeString(themes().resolve("b.toml"), "name = \"Loop\"\nextends = \"Jasper Dark\"\n");
        Files.writeString(themes().resolve("c.toml"), "name = \"Child\"\nextends = \"Jasper Dark\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Jasper Dark")).containsSame(Theme.DARK);
        assertThat(snapshot.find("Loop")).isEmpty();
        assertThat(snapshot.find("Child")).get().extracting(Theme::palette).isEqualTo(Theme.DARK.palette());
        assertThat(snapshot.problems()).hasSize(2).allSatisfy(problem ->
            assertThat(problem.message()).contains("cycle"));
    }

    @Test void sameNameOverridesExtendOnlyTheLowerPriorityLayers() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("a.toml"), """
            name = "Jasper Dark"
            extends = "Jasper Dark"
            [colors]
            foreground = "#eeeeee"
            """);
        Files.writeString(themes().resolve("b.toml"), """
            name = "Jasper Dark"
            extends = "Jasper Dark"
            [colors]
            background = "#101010"
            """);
        var snapshot = catalog().scan();
        Theme theme = snapshot.find("Jasper Dark").orElseThrow();
        assertThat(theme.palette().foreground()).isEqualTo(new Color(0xeeeeee));
        assertThat(theme.palette().background()).isEqualTo(new Color(0x101010));
        assertThat(snapshot.problems()).singleElement().satisfies(problem ->
            assertThat(problem.message()).contains("also installed"));
    }

    @Test void aRescanRereadsOnlyFilesWhoseSizeOrTimeChanged() throws Exception {
        plugin(themes().resolve("one.jar"), "One", true, "#111111", "111111");
        plugin(themes().resolve("two.jar"), "Two", false, "#222222", "222222");
        var reads = new ArrayList<String>();
        var catalog = new ThemeCatalog(themes(), file -> { reads.add(file.getFileName().toString()); return dev.jasper.app.themeimport.LegacyThemeReader.read(file); });
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
        assertThat(snapshot.names()).containsExactly("Darcula", "IntelliJ Light", "Jasper Dark", "Jasper Light");
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
        var snapshot = dev.jasper.app.themeimport.LegacyThemeReader.catalog(other.resolve("config.toml")).scan();
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
            return dev.jasper.app.themeimport.LegacyThemeReader.read(file);
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
        var catalog = new ThemeCatalog(themes(), file -> null);
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
        assertThat(snapshot.problems()).filteredOn(problem -> problem.message().contains("provided twice")).singleElement().satisfies(problem ->
            assertThat(problem.message()).contains("\"Same\""));
        assertThat(snapshot.problems()).filteredOn(problem -> problem.message().contains("no console colours")).hasSize(2);
    }

    @Test void inspectingAnArchiveNamesItsThemesWithoutInstallingIt() {
        Path file = plugin(home.resolve("elsewhere/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var inspection = dev.jasper.app.themeimport.LegacyThemeReader.inspect(file);
        assertThat(inspection.themes()).containsExactly("Fixture Dark");
        assertThat(inspection.problems()).isEmpty();
        Path empty = jar(home.resolve("elsewhere/empty.jar"), entries("META-INF/plugin.xml", pluginXml("Nothing")));
        assertThat(dev.jasper.app.themeimport.LegacyThemeReader.inspect(empty).themes()).isEmpty();
        assertThat(dev.jasper.app.themeimport.LegacyThemeReader.inspect(home.resolve("elsewhere/missing.jar")).problems()).isNotEmpty();
        assertThat(catalog().directory()).contains(themes());
        assertThat(ThemeCatalog.builtIn().directory()).isEmpty();
        assertThat(themes()).doesNotExist();
    }

    @Test void aThemeFileInTheFolderIsAThemeThatMayExtendABuiltIn() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("mine.toml"), """
            name = "Mine"
            extends = "Jasper Dark"
            [tokens]
            tab_active = "#123456"
            """);
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme mine = snapshot.find("mine").orElseThrow();
        assertThat(mine.id()).isEqualTo("user:mine.toml");
        assertThat(mine.dark()).isTrue();
        assertThat(mine.palette()).isEqualTo(Theme.DARK.palette());
        assertThat(mine.source()).isInstanceOfSatisfying(Theme.UserFile.class,
            user -> assertThat(user.file()).isEqualTo(themes().resolve("mine.toml")));
        assertThat(ThemeGenerator.generate(mine.source().document()).tokens().get(Token.TAB_ACTIVE)).isEqualTo(new Color(0x123456));
        assertThat(snapshot.themes()).as("user themes come first").startsWith(mine);
    }

    @Test void aThemeFileMayReplaceABuiltInThemeAndExtendIt() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("dark.toml"), "name = \"Jasper Dark\"\nextends = \"Jasper Dark\"\n[colors]\naccent = \"#ff8800\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(snapshot.find("Jasper Dark").map(Theme::source)).get().isInstanceOf(Theme.UserFile.class);
        assertThat(snapshot.themes()).doesNotContain(Theme.DARK);
    }

    @Test void aThemeFileMayExtendAPluginTheme() throws Exception {
        plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        Files.writeString(themes().resolve("tweak.toml"), "name = \"Tweak\"\nextends = \"Fixture Dark\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme tweak = snapshot.find("Tweak").orElseThrow();
        assertThat(tweak.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(tweak.source().document().uiComplete()).contains(true);
    }

    @Test void aBrokenThemeFileIsAProblemOnItsLine() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("bad.toml"), "name = \"Bad\"\n[colors]\nbackground = \"blue\"\n");
        Files.writeString(themes().resolve("orphan.toml"), "name = \"Orphan\"\n\nextends = \"Nowhere\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Bad")).isEmpty();
        assertThat(snapshot.find("Orphan")).isEmpty();
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::line, problem -> problem.file().getFileName().toString())
            .containsExactlyInAnyOrder(tuple(3, "bad.toml"), tuple(3, "orphan.toml"));
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::message)
            .anySatisfy(message -> assertThat(message).startsWith("bad.toml:3: ").contains("must be a colour"))
            .anySatisfy(message -> assertThat(message).startsWith("orphan.toml:3: ").contains("\"Nowhere\""));
    }

    @Test void aProblemInAnExtendedThemeFileIsNotPlacedOnTheExtendingFilesLines() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("a.toml"), "name = \"A\"\nextends = \"B\"\n");
        Files.writeString(themes().resolve("b.toml"), "name = \"B\"\n\nextends = \"Nowhere\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("A")).isEmpty();
        assertThat(snapshot.problems()).filteredOn(problem -> problem.file().getFileName().toString().equals("a.toml"))
            .singleElement().satisfies(problem -> {
                assertThat(problem.line()).isZero();
                assertThat(problem.message()).startsWith("b.toml:3: ").contains("\"Nowhere\"");
            });
        assertThat(snapshot.problems()).filteredOn(problem -> problem.file().getFileName().toString().equals("b.toml"))
            .singleElement().extracting(ThemeCatalog.Problem::line).isEqualTo(3);
    }

    @Test void anUnchangedThemeFileKeepsItsThemeInstanceAcrossScans() throws Exception {
        Files.createDirectories(themes());
        Path file = themes().resolve("mine.toml");
        Files.writeString(file, "name = \"Mine\"\nextends = \"Jasper Dark\"\n");
        var catalog = catalog();
        Theme first = catalog.scan().find("Mine").orElseThrow();
        assertThat(catalog.scan().themes()).as("an unchanged file").anySatisfy(theme -> assertThat(theme).isSameAs(first));
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));
        assertThat(catalog.scan().find("Mine")).as("re-read, the same theme").get().isSameAs(first);
        Files.writeString(file, "name = \"Mine\"\nextends = \"Jasper Dark\"\n[colors]\naccent = \"#ff8800\"\n");
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 10_000));
        Theme edited = catalog.scan().find("Mine").orElseThrow();
        assertThat(edited).as("an edited file").isNotSameAs(first).isNotEqualTo(first);
        assertThat(catalog.scan().find("Mine")).get().isSameAs(edited);
    }

    @Test void anOversizedThemeFileIsAProblem() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("huge.toml"), "name = \"Huge\"\n# " + "x".repeat(ThemeCatalog.MAX_THEME_FILE));
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Huge")).isEmpty();
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> assertThat(problem.message()).contains("larger than 256 KiB"));
    }
}
