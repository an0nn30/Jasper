package dev.jasper.ui.themeimport;

import dev.jasper.ui.themeimport.ThemeFixtures;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.theme.TerminalColors;
import java.awt.Color;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class AlacrittyThemeTest {
    private static final String FULL = """
        [colors.primary]
        background = '#282a36'
        foreground = '0xF8F8F2'
        [colors.cursor]
        text = 'CellBackground'
        cursor = '#ffb86c'
        [colors.selection]
        background = '#44475a'
        [colors.normal]
        black = '#000000'
        red = '#ff5555'
        green = '#50fa7b'
        yellow = '#f1fa8c'
        blue = '#bd93f9'
        magenta = '#ff79c6'
        cyan = '#8be9fd'
        white = '#bbbbbb'
        [colors.bright]
        black = '#555555'
        red = '#ff6e6e'
        green = '#69ff94'
        yellow = '#ffffa5'
        blue = '#d6acff'
        magenta = '#ff92df'
        cyan = '#a4ffff'
        white = '#ffffff'
        [colors.dim]
        red = '#aa0000'
        [colors.search.matches]
        foreground = '#000000'
        """;

    static final String NESTED = "x = " + "[".repeat(25_000) + "]".repeat(25_000) + "\n";

    @Test void deeplyNestedArraysAreUnusableInsteadOfThrowing() {
        var result = AlacrittyTheme.read(NESTED);
        assertThat(result.palette()).isEmpty();
        assertThat(result.problems()).containsExactly("It cannot be read as TOML.");
    }

    @Test void aFullThemeMapsOntoThePalette() {
        var result = AlacrittyTheme.read(FULL);
        assertThat(result.problems()).isEmpty();
        TerminalColors palette = result.palette().orElseThrow();
        assertThat(palette.background()).isEqualTo(new Color(0x282a36));
        assertThat(palette.foreground()).isEqualTo(new Color(0xf8f8f2));
        assertThat(palette.cursor()).isEqualTo(new Color(0xffb86c));
        assertThat(palette.selection()).isEqualTo(new Color(0x44475a));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xff5555));
        assertThat(palette.ansi().get(7)).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.ansi().get(9)).isEqualTo(new Color(0xff6e6e));
        assertThat(palette.ansi().get(15)).isEqualTo(new Color(0xffffff));
    }

    @Test void missingColoursFallBack() {
        var result = AlacrittyTheme.read("""
            [colors.primary]
            background = '#101010'
            foreground = '#e0e0e0'
            [colors.cursor]
            cursor = 'CellForeground'
            [colors.normal]
            red = '#ff0000'
            """);
        assertThat(result.problems()).isEmpty();
        TerminalColors palette = result.palette().orElseThrow();
        assertThat(palette.cursor()).as("a keyword cursor is the foreground").isEqualTo(new Color(0xe0e0e0));
        assertThat(palette.selection()).as("30% foreground over background").isEqualTo(new Color(
            (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7), (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7), (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7)));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xff0000));
        assertThat(palette.ansi().get(2)).as("dark background: Jasper Dark's green").isEqualTo(ImportPalettes.dark().ansi().get(2));
        assertThat(palette.ansi().get(9)).as("missing bright takes normal").isEqualTo(new Color(0xff0000));
        var light = AlacrittyTheme.read("[colors.primary]\nbackground = '#fafafa'\nforeground = '#111111'\n").palette().orElseThrow();
        assertThat(light.ansi().get(2)).isEqualTo(ImportPalettes.light().ansi().get(2));
    }

    @Test void badColoursWarnAndMissingPrimaryOrBadTomlIsUnusable() {
        var warned = AlacrittyTheme.read("[colors.primary]\nbackground = '#000000'\nforeground = '#ffffff'\n[colors.normal]\nred = 'reddish'\n");
        assertThat(warned.palette()).isPresent();
        assertThat(warned.problems()).singleElement().asString().contains("colors.normal.red", "reddish");
        assertThat(AlacrittyTheme.read("[colors.primary]\nbackground = '#000000'\n").palette()).isEmpty();
        var broken = AlacrittyTheme.read("[colors.primary\nbackground =");
        assertThat(broken.palette()).isEmpty();
        assertThat(broken.problems()).singleElement().asString().startsWith("It is not valid TOML");
        assertThat(AlacrittyTheme.read("colors = 'nope'\n").palette()).isEmpty();
    }

    @Test void fileNamesBecomeDisplayNames() {
        assertThat(AlacrittyTheme.displayName("gruvbox_dark.toml")).isEqualTo("Gruvbox Dark");
        assertThat(AlacrittyTheme.displayName("Cobalt2.toml")).isEqualTo("Cobalt2");
        assertThat(AlacrittyTheme.displayName("one-half_light.TOML")).isEqualTo("One Half Light");
        assertThat(AlacrittyTheme.displayName("iterm.toml")).isEqualTo("Iterm");
    }

    @Test void everyVendoredAlacrittyThemeIsUsableWithoutWarningsAndHasItsOwnName() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (!java.nio.file.Files.exists(root.resolve("tools/themes/sources.json"))) root = root.getParent();
        var manifest = ThemeSources.read(root.resolve("tools/themes/sources.json"));
        var source = manifest.sources().stream().filter(value -> value.id().equals("alacritty")).findFirst().orElseThrow();
        var files = source.assets().stream().filter(asset -> asset.path().endsWith(".toml")).map(ThemeSources.Asset::path).toList();
        java.nio.file.Path themes = root.resolve("third_party/themes/alacritty");
        assertThat(files).hasSize(177);
        var names = new HashSet<String>();
        for (String file : files) {
            var result = AlacrittyTheme.read(java.nio.file.Files.readString(themes.resolve(file)));
            assertThat(result.palette()).as(file).isPresent();
            assertThat(result.problems()).as(file).isEmpty();
            String name = AlacrittyTheme.displayName(java.nio.file.Path.of(file).getFileName().toString());
            assertThat(names.add(name.toLowerCase(java.util.Locale.ROOT))).as("duplicate name " + name).isTrue();
        }
        assertThat(names).contains("dracula", "gruvbox dark", "cobalt2");
    }
}
