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
        String text = "﻿" + ThemeTestSupport.terminalThemeJson("Gruvbox Dark");
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
        assertThatThrownBy(() -> folder.prepare(source("night.json", ThemeTestSupport.terminalThemeJson("Ночь"))))
            .hasMessage("The theme's name has no letters or digits Jasper can use for a file name");
        var huge = Files.write(home.resolve("downloads/huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
        assertThatThrownBy(() -> folder.prepare(huge)).hasMessage("Theme files are at most 1 MiB.");
        assertThatThrownBy(() -> folder.prepare(home.resolve("downloads/missing.json"))).isInstanceOf(ThemeFolder.ImportFailure.class);
        assertThat(home.resolve("themes")).doesNotExist();
    }

    @Test void removingDeletesTheFileAndNeverTouchesSlateSteel() throws Exception {
        var folder = new ThemeFolder(home.resolve("themes"));
        folder.install(folder.prepare(source("z.json", ThemeTestSupport.terminalThemeJson("Zenburn"))));
        folder.remove("zenburn");
        assertThat(home.resolve("themes/zenburn.json")).doesNotExist();
        assertThatThrownBy(() -> folder.remove("zenburn")).isInstanceOf(java.nio.file.NoSuchFileException.class);
        assertThatThrownBy(() -> folder.remove("slate-steel")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> folder.remove("../config")).isInstanceOf(IllegalArgumentException.class);
    }
}
