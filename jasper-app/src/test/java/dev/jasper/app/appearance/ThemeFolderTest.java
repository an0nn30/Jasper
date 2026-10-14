package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** spec 4.2–4.3: a theme file is checked, then copied in under the id its name gives; an imported theme's file can be deleted. */
class ThemeFolderTest {
    @TempDir Path home;

    Path source(String fileName, String text) throws Exception {
        return Files.writeString(Files.createDirectories(home.resolve("downloads")).resolve(fileName), text);
    }

    @Test void idsFollowTheConvertersRules() {
        assertThat(ThemeFolder.id("Dracula")).isEqualTo("dracula");
        assertThat(ThemeFolder.id("Café  Noir!")).isEqualTo("cafe-noir");
        assertThat(ThemeFolder.id("Ночь")).isEmpty();
        assertThat(ThemeFolder.id("x".repeat(80))).hasSize(64);
        assertThat(ThemeFolder.id("a".repeat(63) + " b")).isEqualTo("a".repeat(63));
        assertThat(ThemeFolder.id("  Gruvbox Dark (hard)  ")).isEqualTo("gruvbox-dark-hard");
    }

    @Test void aGoodFileIsCopiedInUnderItsIdWithItsOriginalBytes() throws Exception {
        var folder = new ThemeFolder(home.resolve("themes"));
        String text = "\uFEFF" + ThemeTestSupport.terminalThemeJson("Gruvbox Dark");
        var file = source("anything.json", text);
        var candidate = folder.prepare(file);
        assertThat(candidate.id()).isEqualTo("gruvbox-dark");
        assertThat(candidate.name()).isEqualTo("Gruvbox Dark");
        assertThat(candidate.replaces()).isFalse();
        folder.install(candidate);
        assertThat(Files.readAllBytes(home.resolve("themes/gruvbox-dark.json"))).isEqualTo(Files.readAllBytes(file));
        try (var listing = Files.list(home.resolve("themes"))) { assertThat(listing).hasSize(1); }
        assertThat(ThemeLibrary.read(home.resolve("themes"), ThemeLibrary.bundledOnly()).theme("gruvbox-dark")).isPresent();
        assertThat(folder.prepare(file).replaces()).as("a second import replaces the first").isTrue();
    }

    @Test void aFileThatCannotBeImportedSaysWhy() throws Exception {
        var folder = new ThemeFolder(home.resolve("themes"));
        assertThatThrownBy(() -> folder.prepare(source("half.json", ThemeTestSupport.terminalThemeJson("Half").replace("\"fg.bold\": \"#FBF1C7\",", ""))))
            .isInstanceOf(ThemeFolder.ImportFailure.class).hasMessage("terminal.dark: Missing colour \"fg.bold\"");
        assertThatThrownBy(() -> folder.prepare(source("broken.json", "{ \"jasper-theme\": 1,\n  \"name\": ")))
            .hasMessageStartingWith("line 2: ");
        assertThatThrownBy(() -> folder.prepare(source("steel.json", ThemeTestSupport.uiThemeJson("Slate Steel"))))
            .hasMessage("Slate Steel is built in and can't be replaced");
        assertThatThrownBy(() -> folder.prepare(source("mocha.json", ThemeTestSupport.uiThemeJson("Catppuccin Mocha"))))
            .hasMessage("Catppuccin Mocha is built in and can't be replaced");
        assertThatThrownBy(() -> folder.prepare(source("night.json", ThemeTestSupport.terminalThemeJson("Ночь"))))
            .hasMessage("The theme's name has no letters or digits Jasper can use for a file name");
        var huge = Files.write(home.resolve("downloads/huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
        assertThatThrownBy(() -> folder.prepare(huge)).hasMessage("Theme files are at most 1 MiB.");
        var missing = home.resolve("downloads/missing.json");
        assertThatThrownBy(() -> folder.prepare(missing)).isInstanceOf(ThemeFolder.ImportFailure.class)
            .hasMessage(ThemeLibrary.reason(new java.nio.file.NoSuchFileException(missing.toString())))
            .hasMessageNotContaining(missing.toString());
        assertThat(home.resolve("themes")).doesNotExist();
    }

    @Test void aMoveThatFailsPropagatesLeavesWhatIsAtTheTargetAndNoTemporaryFile() throws Exception {
        var folder = new ThemeFolder(home.resolve("themes"));
        var candidate = folder.prepare(source("z.json", ThemeTestSupport.terminalThemeJson("Zenburn")));
        var blocker = Files.createDirectories(home.resolve("themes/zenburn.json"));
        Files.writeString(blocker.resolve("keep.txt"), "kept");
        assertThatThrownBy(() -> folder.install(candidate)).isInstanceOf(java.io.IOException.class);
        assertThat(blocker.resolve("keep.txt")).hasContent("kept");
        try (var listing = Files.list(home.resolve("themes"))) { assertThat(listing).containsExactly(blocker); }
    }

    @Test void aWriteThatFailsKeepsTheOldFileAndLeavesNoTemporaryFile() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        var themes = home.resolve("themes");
        var folder = new ThemeFolder(themes);
        folder.install(folder.prepare(source("z.json", ThemeTestSupport.terminalThemeJson("Zenburn"))));
        byte[] old = Files.readAllBytes(themes.resolve("zenburn.json"));
        var newer = folder.prepare(source("z2.json", ThemeTestSupport.terminalThemeJson("Zenburn").replace("#282828", "#1D2021")));
        Files.setPosixFilePermissions(themes, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(themes), "running as a user who writes everywhere");
            assertThatThrownBy(() -> folder.install(newer)).isInstanceOf(java.io.IOException.class);
            assertThat(Files.readAllBytes(themes.resolve("zenburn.json"))).isEqualTo(old);
            try (var listing = Files.list(themes)) { assertThat(listing.map(path -> path.getFileName().toString())).containsExactly("zenburn.json"); }
        } finally {
            Files.setPosixFilePermissions(themes, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test void removingDeletesTheFileAndNeverTouchesSlateSteel() throws Exception {
        var folder = new ThemeFolder(home.resolve("themes"));
        folder.install(folder.prepare(source("z.json", ThemeTestSupport.terminalThemeJson("Zenburn"))));
        folder.remove("zenburn");
        assertThat(home.resolve("themes/zenburn.json")).doesNotExist();
        assertThatThrownBy(() -> folder.remove("zenburn")).isInstanceOf(java.nio.file.NoSuchFileException.class);
        assertThatThrownBy(() -> folder.remove("slate-steel")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.remove("catppuccin-latte")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.remove("../config")).isInstanceOf(IllegalArgumentException.class);
    }
}
