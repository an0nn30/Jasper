package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeFilesTest {
    @TempDir Path home;

    private Path folder() { return home.resolve("terminal-themes"); }
    private Path download(String name, String text) throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        return Files.writeString(home.resolve("Downloads").resolve(name), text);
    }

    @Test void aValidThemeIsCopiedInAndNamed() throws Exception {
        var files = new TerminalThemeFiles(folder());
        Path source = download("tokyo_night.toml", TerminalThemeCatalogTest.theme("#1a1b26"));
        assertThat(files.install(source, false)).isEqualTo("Tokyo Night");
        assertThat(folder().resolve("tokyo_night.toml")).hasSameTextualContentAs(source);
        try (var listing = Files.list(folder())) { assertThat(listing).hasSize(1); }
        assertThatThrownBy(() -> files.install(source, false)).isInstanceOfSatisfying(TerminalThemeFiles.Conflict.class,
            conflict -> assertThat(conflict.existing()).isEqualTo(folder().resolve("tokyo_night.toml")));
        assertThat(files.install(source, true)).isEqualTo("Tokyo Night");
    }

    @Test void unusableFilesAreRefused() throws Exception {
        var files = new TerminalThemeFiles(folder());
        assertThatThrownBy(() -> files.install(download("notes.txt", "hi"), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessage("notes.txt is not a .toml theme file.");
        assertThatThrownBy(() -> files.install(download("bad.toml", "[colors.primary\n"), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageStartingWith("Bad cannot be used: It is not valid TOML");
        assertThatThrownBy(() -> files.install(download("dark.toml", TerminalThemeCatalogTest.theme("#000000")), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> files.install(download("big.toml", "#" + "x".repeat(TerminalThemeCatalog.MAX_FILE)), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageContaining("64 KiB");
        assertThat(folder()).satisfiesAnyOf(path -> assertThat(path).doesNotExist(), path -> assertThat(path).isEmptyDirectory());
    }

    @Test void removeDeletesOnlyThemeFilesInTheFolder() throws Exception {
        var files = new TerminalThemeFiles(folder());
        files.install(download("mine.toml", TerminalThemeCatalogTest.theme("#101010")), false);
        files.remove(folder().resolve("mine.toml"));
        assertThat(folder().resolve("mine.toml")).doesNotExist();
        Path outside = download("keep.toml", TerminalThemeCatalogTest.theme("#101010"));
        assertThatThrownBy(() -> files.remove(outside)).isInstanceOf(TerminalThemeFiles.ThemeFileException.class);
        assertThat(outside).exists();
        Files.writeString(folder().resolve(".hidden.toml"), "x");
        assertThatThrownBy(() -> files.remove(folder().resolve(".hidden.toml"))).isInstanceOf(TerminalThemeFiles.ThemeFileException.class);
    }
}
