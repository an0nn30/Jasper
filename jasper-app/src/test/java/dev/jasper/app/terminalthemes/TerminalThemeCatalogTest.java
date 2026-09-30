package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeCatalogTest {
    @TempDir Path home;

    private Path folder() { return home.resolve("terminal-themes"); }
    private TerminalThemeCatalog catalog() { return TerminalThemeCatalog.beside(home.resolve("config.toml")); }

    static String theme(String background) {
        return "[colors.primary]\nbackground = '" + background + "'\nforeground = '#eeeeee'\n";
    }

    @Test void theBundledCollectionIsListedByNameAndFoundIgnoringCase() {
        var snapshot = TerminalThemeCatalog.bundledOnly().latest();
        assertThat(snapshot.themes()).hasSize(177).allSatisfy(theme -> assertThat(theme.bundled()).isTrue());
        assertThat(snapshot.names()).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(snapshot.find("  DRACULA ")).get().extracting(TerminalTheme::name).isEqualTo("Dracula");
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(TerminalThemeCatalog.bundledOnly().directory()).isEmpty();
    }

    @Test void userFilesComeFirstAndBeatBundledThemesOfTheSameName() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("dracula.toml"), theme("#010203"));
        Files.writeString(folder().resolve("my_theme.toml"), theme("#0a0b0c"));
        var snapshot = catalog().scan();
        assertThat(snapshot.names()).startsWith("Dracula", "My Theme");
        assertThat(snapshot.names().stream().filter(name -> name.equalsIgnoreCase("dracula"))).hasSize(1);
        TerminalTheme mine = snapshot.find("dracula").orElseThrow();
        assertThat(mine.bundled()).isFalse();
        assertThat(mine.file()).contains(folder().resolve("dracula.toml"));
        assertThat(mine.palette().background()).isEqualTo(new Color(0x010203));
        assertThat(catalog().directory()).contains(folder());
    }

    @Test void problemsAreReportedPerFileAndIgnoredFilesAreSkipped() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("broken.toml"), "[colors.primary\n");
        Files.writeString(folder().resolve("Light.toml"), theme("#ffffff"));
        // Distinct file names (macOS folders ignore case) that give one display name, "Twin Theme".
        Files.writeString(folder().resolve("twin_theme.toml"), theme("#111111"));
        Files.writeString(folder().resolve("twin-theme.toml"), theme("#222222"));
        Files.writeString(folder().resolve(".hidden.toml"), theme("#333333"));
        Files.writeString(folder().resolve("notes.txt"), "hello");
        Files.writeString(folder().resolve("huge.toml"), "#" + "x".repeat(TerminalThemeCatalog.MAX_FILE));
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).extracting(TerminalThemeCatalog.Problem::file).extracting(path -> path.getFileName().toString())
            .containsExactlyInAnyOrder("broken.toml", "huge.toml", "Light.toml", "twin_theme.toml");
        assertThat(snapshot.find("Broken")).isEmpty();
        assertThat(snapshot.find("Light")).isEmpty();
        assertThat(snapshot.find("twin theme").orElseThrow().palette().background()).as("twin-theme.toml sorts first").isEqualTo(new Color(0x222222));
        assertThat(snapshot.find("Hidden")).isEmpty();
    }

    @Test void aRescanPicksUpAddedChangedAndRemovedFiles() throws Exception {
        var catalog = catalog();
        assertThat(catalog.scan().find("Mine")).isEmpty();
        assertThat(folder()).isDirectory();
        Path file = Files.writeString(folder().resolve("mine.toml"), theme("#010101"));
        assertThat(catalog.scan().find("Mine").orElseThrow().palette().background()).isEqualTo(new Color(0x010101));
        Files.writeString(file, theme("#020202"));
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        assertThat(catalog.scan().find("Mine").orElseThrow().palette().background()).isEqualTo(new Color(0x020202));
        Files.delete(file);
        assertThat(catalog.scan().find("Mine")).isEmpty();
        assertThat(catalog.latest().find("Dracula")).isPresent();
    }

    @Test void aFileThatBreaksTheParserIsAProblemAndTheScanContinues() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("nested.toml"), AlacrittyThemeTest.NESTED);
        Files.writeString(folder().resolve("mine.toml"), theme("#010101"));
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).extracting(problem -> problem.file().getFileName().toString()).containsExactly("nested.toml");
        assertThat(snapshot.find("Mine")).isPresent();
        assertThat(snapshot.find("Nested")).isEmpty();
        assertThat(snapshot.find("Dracula")).isPresent();
    }

    @Test void aFileThatCouldNotBeReadIsRetriedOnTheNextScan() throws Exception {
        Files.createDirectories(folder());
        Path file = Files.writeString(folder().resolve("locked.toml"), theme("#010101"));
        assumeTrue(file.toFile().setReadable(false) && !Files.isReadable(file), "the file system cannot make a file unreadable");
        var catalog = catalog();
        try {
            assertThat(catalog.scan().problems()).extracting(problem -> problem.file().getFileName().toString()).containsExactly("locked.toml");
        } finally {
            file.toFile().setReadable(true);
        }
        var snapshot = catalog.scan();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(snapshot.find("Locked")).isPresent();
    }
}
