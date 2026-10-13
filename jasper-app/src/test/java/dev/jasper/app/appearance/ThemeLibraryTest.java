package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SchemeId;
import dev.jasper.ui.theme.TerminalScheme;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** spec 3.2: the built-in theme plus every valid {@code <home>/themes/*.json}; bad files are skipped and reported. */
class ThemeLibraryTest {
    @TempDir Path home;

    static String terminalTheme(String name) { return ThemeTestSupport.terminalThemeJson(name); }
    static String uiTheme(String name) { return ThemeTestSupport.uiThemeJson(name); }

    Path themes() throws Exception { return Files.createDirectories(home.resolve("themes")); }

    @Test void aMissingFolderIsTheBuiltInThemeAlone() {
        var library = ThemeLibrary.read(home.resolve("themes"), ThemeLibrary.bundledOnly());
        assertThat(library).isSameAs(ThemeLibrary.bundledOnly());
        assertThat(library.themes()).containsExactly(JasperTheme.bundled());
        assertThat(library.imported()).isEmpty();
        assertThat(library.problems()).isEmpty();
        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString())
            .containsExactly("slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast");
    }

    @Test void importedThemesFollowSlateSteelByNameThenId() throws Exception {
        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn"));
        Files.writeString(themes().resolve("ocean.json"), uiTheme("ocean"));
        Files.writeString(themes().resolve("abyss.json"), uiTheme("Ocean"));
        Files.writeString(themes().resolve("notes.txt"), "ignored");
        Files.createDirectories(themes().resolve("folder.json"));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(library.problems()).isEmpty();
        assertThat(library.themes()).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean", "zenburn");
        assertThat(library.themes(true)).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean");
        assertThat(library.themes(false)).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean");
        assertThat(library.theme("zenburn")).get().extracting(JasperTheme::name).isEqualTo("Zenburn");
        assertThat(library.theme("nord")).isEmpty();
        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString()).containsExactly(
            "slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast",
            "abyss/dark", "abyss/light", "abyss/high-contrast", "ocean/dark", "ocean/light", "ocean/high-contrast", "zenburn/dark");
        assertThat(library.scheme(new SchemeId("zenburn", "dark"))).get().extracting(TerminalScheme::name).isEqualTo("Zenburn");
        assertThat(library.label(library.theme("abyss").orElseThrow())).as("two themes are named Ocean").isEqualTo("Ocean (abyss)");
        assertThat(library.label(library.theme("ocean").orElseThrow())).isEqualTo("ocean (ocean)");
        assertThat(library.label(library.theme("zenburn").orElseThrow())).isEqualTo("Zenburn");
        assertThat(library.label(library.scheme(new SchemeId("zenburn", "dark")).orElseThrow())).isEqualTo("Zenburn");
        assertThat(library.label(library.scheme(new SchemeId("abyss", "high-contrast")).orElseThrow()))
            .isEqualTo("High Contrast (abyss/high-contrast)");
    }

    @Test void aFileThatCannotBeUsedIsSkippedAndReported() throws Exception {
        Files.writeString(themes().resolve("broken.json"), "{ \"jasper-theme\": 1,\n  \"name\": \"Broken\" ");
        Files.writeString(themes().resolve("half.json"), terminalTheme("Half").replace("\"fg.bold\": \"#FBF1C7\",", ""));
        Files.writeString(themes().resolve("My Theme.json"), terminalTheme("Mine"));
        Files.writeString(themes().resolve("slate-steel.json"), terminalTheme("Impostor"));
        Files.write(themes().resolve("huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
        Files.write(themes().resolve("latin.json"), new byte[] {'{', (byte) 0xE9, '}'});
        Files.writeString(themes().resolve("good.json"), terminalTheme("Good"));
        Files.writeString(themes().resolve("marked.json"), "\uFEFF" + terminalTheme("Marked"));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(library.imported()).as("a byte-order mark is not part of the text").extracting(JasperTheme::id).containsExactly("good", "marked");
        assertThat(library.problems()).extracting(problem -> problem.file().getFileName().toString())
            .containsExactly("My Theme.json", "broken.json", "half.json", "huge.json", "latin.json", "slate-steel.json");
        assertThat(library.problems().get(0).message()).startsWith("A theme file is named after its id");
        assertThat(library.problems().get(1).line()).isEqualTo(2);
        assertThat(library.problems().get(1).message()).isEqualTo("Unexpected end");
        assertThat(library.problems().get(2).message()).isEqualTo("terminal.dark: Missing colour \"fg.bold\"");
        assertThat(library.problems().get(3).message()).isEqualTo("Theme files are at most 1 MiB.");
        assertThat(library.problems().get(4).message()).isEqualTo("A theme file is UTF-8 text.");
        assertThat(library.problems().get(5).message()).isEqualTo("Slate Steel is built in; this file is ignored.");
    }

    @Test void aRescanKeepsUnchangedThemesAndAnUnchangedFolderKeepsTheLibrary() throws Exception {
        Files.writeString(themes().resolve("ocean.json"), uiTheme("Ocean"));
        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn"));
        var first = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(ThemeLibrary.read(themes(), first)).isSameAs(first);
        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn Night"));
        var second = ThemeLibrary.read(themes(), first);
        assertThat(second).isNotSameAs(first);
        assertThat(second.theme("ocean").orElseThrow()).isSameAs(first.theme("ocean").orElseThrow());
        assertThat(second.theme("zenburn").orElseThrow().name()).isEqualTo("Zenburn Night");
        Files.delete(themes().resolve("ocean.json"));
        assertThat(ThemeLibrary.read(themes(), second).themes()).extracting(JasperTheme::id).containsExactly("slate-steel", "zenburn");
    }
}
