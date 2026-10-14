package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SchemeId;
import dev.jasper.ui.theme.TerminalScheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** spec 3.2: the built-in themes plus every valid {@code <home>/themes/*.json}; bad files are skipped and reported. */
class ThemeLibraryTest {
    static final List<String> BUILT_IN = List.of("slate-steel", "catppuccin-latte", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha");
    static final List<String> BUILT_IN_SCHEMES = List.of("slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast",
        "catppuccin-latte/light", "catppuccin-frappe/dark", "catppuccin-macchiato/dark", "catppuccin-mocha/dark");
    @TempDir Path home;

    static List<String> builtInAnd(String... more) {
        var all = new java.util.ArrayList<>(BUILT_IN);
        all.addAll(List.of(more));
        return all;
    }

    static String terminalTheme(String name) { return ThemeTestSupport.terminalThemeJson(name); }
    static String uiTheme(String name) { return ThemeTestSupport.uiThemeJson(name); }

    Path themes() throws Exception { return Files.createDirectories(home.resolve("themes")); }

    @Test void aMissingFolderIsTheBuiltInThemesAlone() {
        var library = ThemeLibrary.read(home.resolve("themes"), ThemeLibrary.bundledOnly());
        assertThat(library).isSameAs(ThemeLibrary.bundledOnly());
        assertThat(library.themes()).containsExactlyElementsOf(JasperTheme.builtIns());
        assertThat(library.themes().getFirst()).isSameAs(JasperTheme.bundled());
        assertThat(library.themes(true)).extracting(JasperTheme::id)
            .containsExactly("slate-steel", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha");
        assertThat(library.themes(false)).extracting(JasperTheme::id).containsExactly("slate-steel", "catppuccin-latte");
        assertThat(library.imported()).isEmpty();
        assertThat(library.problems()).isEmpty();
        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString()).containsExactlyElementsOf(BUILT_IN_SCHEMES);
        assertThat(library.label(library.theme("catppuccin-frappe").orElseThrow())).isEqualTo("Catppuccin Frapp\u00e9");
    }

    @Test void importedThemesFollowTheBuiltInThemesByNameThenId() throws Exception {
        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn"));
        Files.writeString(themes().resolve("ocean.json"), uiTheme("ocean"));
        Files.writeString(themes().resolve("abyss.json"), uiTheme("Ocean"));
        Files.writeString(themes().resolve("notes.txt"), "ignored");
        Files.createDirectories(themes().resolve("folder.json"));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(library.problems()).isEmpty();
        assertThat(library.themes()).extracting(JasperTheme::id).containsExactlyElementsOf(builtInAnd("abyss", "ocean", "zenburn"));
        assertThat(library.themes(true)).extracting(JasperTheme::id)
            .containsExactly("slate-steel", "catppuccin-frappe", "catppuccin-macchiato", "catppuccin-mocha", "abyss", "ocean");
        assertThat(library.themes(false)).extracting(JasperTheme::id).containsExactly("slate-steel", "catppuccin-latte", "abyss", "ocean");
        assertThat(library.theme("zenburn")).get().extracting(JasperTheme::name).isEqualTo("Zenburn");
        assertThat(library.theme("nord")).isEmpty();
        var schemes = new java.util.ArrayList<>(BUILT_IN_SCHEMES);
        schemes.addAll(List.of("abyss/dark", "abyss/light", "abyss/high-contrast", "ocean/dark", "ocean/light", "ocean/high-contrast", "zenburn/dark"));
        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString()).containsExactlyElementsOf(schemes);
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
        Files.writeString(themes().resolve("catppuccin-mocha.json"), uiTheme("Catppuccin Mocha"));
        Files.write(themes().resolve("huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
        Files.write(themes().resolve("latin.json"), new byte[] {'{', (byte) 0xE9, '}'});
        Files.writeString(themes().resolve("good.json"), terminalTheme("Good"));
        Files.writeString(themes().resolve("marked.json"), "\uFEFF" + terminalTheme("Marked"));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(library.imported()).as("a byte-order mark is not part of the text").extracting(JasperTheme::id).containsExactly("good", "marked");
        assertThat(library.problems()).extracting(problem -> problem.file().getFileName().toString())
            .containsExactly("My Theme.json", "broken.json", "catppuccin-mocha.json", "half.json", "huge.json", "latin.json", "slate-steel.json");
        assertThat(library.problems().get(0).message()).startsWith("A theme file is named after its id");
        assertThat(library.problems().get(1).line()).isEqualTo(2);
        assertThat(library.problems().get(1).message()).isEqualTo("Unexpected end");
        assertThat(library.problems().get(2).message()).isEqualTo("Catppuccin Mocha is built in; this file is ignored.");
        assertThat(library.problems().get(3).message()).isEqualTo("terminal.dark: Missing colour \"fg.bold\"");
        assertThat(library.problems().get(4).message()).isEqualTo("Theme files are at most 1 MiB.");
        assertThat(library.problems().get(5).message()).isEqualTo("A theme file is UTF-8 text.");
        assertThat(library.problems().get(6).message()).isEqualTo("Slate Steel is built in; this file is ignored.");
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
        assertThat(ThemeLibrary.read(themes(), second).themes()).extracting(JasperTheme::id).containsExactlyElementsOf(builtInAnd("zenburn"));
    }

    @Test void aFileNestedTooDeeplyIsSkippedAndReportedNotACrash() throws Exception {
        Files.writeString(themes().resolve("deep.json"), "{\"a\":".repeat(150_000) + "{}" + "}".repeat(150_000));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
        assertThat(library.imported()).isEmpty();
        assertThat(library.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.file().getFileName().toString()).isEqualTo("deep.json");
            assertThat(problem.message()).isEqualTo("Objects and arrays nested more than 8 deep");
        });
    }

    @Test void aParserThatFailsUnexpectedlyMakesThatFileAProblemNotACrash() throws Exception {
        Files.writeString(themes().resolve("good.json"), terminalTheme("Good"));
        Files.writeString(themes().resolve("odd.json"), terminalTheme("Odd"));
        Files.writeString(themes().resolve("silent.json"), terminalTheme("Silent"));
        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly(), (text, id) -> {
            if (id.equals("odd")) throw new IllegalStateException("the parser broke");
            if (id.equals("silent")) throw new NullPointerException();
            return JasperTheme.parse(text, id);
        });
        assertThat(library.imported()).extracting(JasperTheme::id).containsExactly("good");
        assertThat(library.problems()).extracting(problem -> problem.file().getFileName().toString()).containsExactly("odd.json", "silent.json");
        assertThat(library.problems().get(0).line()).isZero();
        assertThat(library.problems().get(0).message()).isEqualTo("Could not use this file: the parser broke");
        assertThat(library.problems().get(1).message()).isEqualTo("Could not use this file: NullPointerException");
    }

    @Test void aFileItMayNotReadIsReportedWithTheReasonNotThePath() throws Exception {
        var secret = Files.writeString(themes().resolve("secret.json"), terminalTheme("Secret"));
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Files.setPosixFilePermissions(secret, java.util.Set.of());
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(secret), "running as a user who reads everything");
            var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
            assertThat(library.problems()).singleElement().satisfies(problem ->
                assertThat(problem.message()).isEqualTo("Could not read this file: permission denied."));
        } finally {
            Files.setPosixFilePermissions(secret, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test void aReadFailureWithoutAMessageStillSaysSomething() {
        assertThat(ThemeLibrary.reason(new java.io.IOException((String) null))).isEqualTo("Could not read this file (IOException).");
        assertThat(ThemeLibrary.reason(new java.io.IOException("Theme files are at most 1 MiB."))).isEqualTo("Theme files are at most 1 MiB.");
        assertThat(ThemeLibrary.reason(new java.nio.file.FileSystemException("/x/secret.json", null, "Input/output error")))
            .isEqualTo("Could not read this file: Input/output error.");
    }
}
