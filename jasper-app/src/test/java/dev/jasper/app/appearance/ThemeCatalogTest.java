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
        assertThat(snapshot.find("jasper light")).contains(Theme.JASPER_LIGHT);
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.find("Dracula")).isEmpty();
        assertThat(snapshot.names()).containsExactly("Clear Dark", "Clear Light", "Jasper Dark", "Jasper Light", "Nord");
        assertThat(snapshot.problems()).isEmpty();
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
        nativePlugin(themes().resolve("fixture.toml"), "Fixture Dark", true, "#123456", "101820");
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
