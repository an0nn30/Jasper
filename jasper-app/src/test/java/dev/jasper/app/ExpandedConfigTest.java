package dev.jasper.app;

import dev.jasper.app.config.ToolbarMode;

import dev.jasper.terminal.config.BellMode;
import dev.jasper.terminal.config.CursorStyle;
import dev.jasper.terminal.config.OptionAsMeta;
import dev.jasper.terminal.config.Palette;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class ExpandedConfigTest {
    private static final Path FILE = Path.of("config.toml");

    private ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }

    @Test void readsThemeVariantInEveryKeyForm() {
        for (String text : List.of("[ui.theme]\nvariant='light'\n", "ui.theme.variant='light'\n", "[ui]\ntheme.variant='light'\n")) {
            var result = parse(text);
            assertThat(result.rejected()).as(text).isFalse();
            assertThat(result.diagnostics()).as(text).isEmpty();
            assertThat(result.snapshot().variant()).as(text).isEqualTo(Appearance.LIGHT);
        }
    }

    @Test void invalidVariantDefaultsWhileWrongTypeRejectsAndLegacyColorsWarn() {
        var invalid = parse("[ui.theme]\nvariant='system'");
        assertThat(invalid.rejected()).isFalse();
        assertThat(invalid.snapshot().variant()).isEqualTo(Appearance.DARK);
        assertPosition(invalid, "ui.theme.variant", 2, 1, ConfigDiagnostic.Severity.ERROR);

        var wrongType = parse("ui.theme.variant=1");
        assertThat(wrongType.rejected()).isTrue();
        assertThat(wrongType.snapshot().variant()).isEqualTo(Appearance.DARK);
        assertPosition(wrongType, "ui.theme.variant", 1, 1, ConfigDiagnostic.Severity.ERROR);

        var legacy = parse("[colors]\ntheme='jasper-light'\nappearance='system'");
        assertThat(legacy.rejected()).isFalse();
        assertThat(legacy.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        assertThat(legacy.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.key()).isEqualTo("colors");
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
        });
    }

    @Test void expandedSettingsAreRecognizedInNestedAndInlineTables() {
        for (String text : List.of("""
            [window]
            columns=100
            lines=30
            [font]
            family='Missing Example Font'
            size=19.5
            fallback=['Fallback One', 'Fallback Two']
            ligatures=false
            line_height=1.5
            [terminal]
            scrollback=321
            option_as_meta='both'
            dim_inactive_panes=0.6
            copy_on_select=true
            bell='sound'
            [terminal.shell]
            program='example-shell'
            args=['--login', 'one argument', '']
            [terminal.cursor]
            shape='beam'
            blink=false
            [terminal.env]
            EXAMPLE='one value'
            """, """
            window={columns=100, lines=30}
            font={family='Missing Example Font', size=19.5, fallback=['Fallback One', 'Fallback Two'], ligatures=false, line_height=1.5}
            terminal={scrollback=321, option_as_meta='both', dim_inactive_panes=0.6, copy_on_select=true, bell='sound', shell={program='example-shell', args=['--login', 'one argument', '']}, cursor={shape='beam', blink=false}, env={EXAMPLE='one value'}}
            """)) {
            var result = parse(text);
            assertThat(result.rejected()).isFalse();
            assertThat(result.diagnostics()).isEmpty();
            var snapshot = result.snapshot();
            assertThat(snapshot.columns()).isEqualTo(100);
            assertThat(snapshot.lines()).isEqualTo(30);
            assertThat(snapshot.font()).isEqualTo(new FontConfig("Missing Example Font", 19.5f,
                List.of("Fallback One", "Fallback Two"), false, 1.5f));
            assertThat(snapshot.terminal()).isEqualTo(new TerminalConfig(
                new TerminalConfig.Shell("example-shell", List.of("--login", "one argument", "")),
                Map.of("EXAMPLE", "one value"), 321, OptionAsMeta.BOTH, CursorStyle.BEAM, false, .6f, true, BellMode.SOUND));
            var options = snapshot.viewOptions(23f, Palette.jasperLight());
            assertThat(options.fontFamily()).isEqualTo("Missing Example Font");
            assertThat(options.fontSize()).isEqualTo(23f);
            assertThat(options.fallbackFonts()).containsExactly("Fallback One", "Fallback Two");
            assertThat(options.ligatures()).isFalse();
            assertThat(options.palette()).isEqualTo(Palette.jasperLight());
            assertThat(options.cursorStyle()).isEqualTo(CursorStyle.BEAM);
            assertThat(options.cursorBlink()).isFalse();
            assertThat(options.optionAsMeta()).isEqualTo(OptionAsMeta.BOTH);
            assertThat(options.scrollback()).isEqualTo(321);
            assertThat(options.copyOnSelect()).isTrue();
            assertThat(options.lineHeight()).isEqualTo(1.5f);
            assertThat(options.bell()).isEqualTo(BellMode.SOUND);
        }
    }

    @Test void expandedKnownTypesRejectWholeCandidateWithoutEchoingSecrets() {
        for (String assignment : List.of("window.columns=5.0", "window.lines='secret'", "font.family=7",
                "font.fallback='secret'", "font.fallback=['ok', 7]", "font.ligatures='secret'", "font.line_height=true",
                "terminal=7", "terminal.shell=[]", "terminal.cursor='secret'", "terminal.env=[]",
                "terminal.shell.program=[]", "terminal.shell.args=7", "terminal.shell.args=['secret', true]",
                "terminal.env.EXAMPLE=7", "terminal.scrollback=10.0", "terminal.option_as_meta=[]",
                "terminal.cursor.shape=7", "terminal.cursor.blink='secret'", "terminal.dim_inactive_panes='secret'",
                "terminal.copy_on_select=7", "terminal.bell=[]", "terminal.on_exit=7")) {
            var result = parse(assignment);
            assertThat(result.rejected()).as(assignment).isTrue();
            assertThat(result.diagnostics()).singleElement().satisfies(d -> {
                assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
                assertThat(d.line()).isEqualTo(1);
                assertThat(d.message()).doesNotContain("secret");
            });
        }
    }

    @Test void invalidExpandedValuesDefaultOnlyTheirField() {
        for (String assignment : List.of("window.columns=4", "window.columns=501", "window.lines=1", "window.lines=201",
                "font.family=''", "font.family='  '", "font.family=\"bad\\u0000name\"", "font.fallback=['ok', '  ']",
                "font.fallback=[\"bad\\u0000name\"]", "font.line_height=0.9", "font.line_height=3.1", "font.line_height=nan",
                "font.line_height=inf", "terminal.shell.program='  '", "terminal.shell.program=\"bad\\u0000program\"",
                "terminal.shell.args=['secret', \"bad\\u0000arg\"]", "terminal.scrollback=-1", "terminal.scrollback=1000001",
                "terminal.option_as_meta='secret'", "terminal.cursor.shape='secret'", "terminal.dim_inactive_panes=-0.1",
                "terminal.dim_inactive_panes=1.1", "terminal.dim_inactive_panes=nan", "terminal.dim_inactive_panes=inf",
                "terminal.bell='secret'", "terminal.on_exit='secret'", "terminal.on_exit='CLOSE'")) {
            var result = parse(assignment + "\nwindow.tab_height=44");
            assertThat(result.rejected()).as(assignment).isFalse();
            var expected = ConfigSnapshot.defaults();
            assertThat(result.snapshot()).isEqualTo(new ConfigSnapshot(44, expected.toolbar(), expected.statusBar(),
                expected.font(), expected.variant(), expected.keybindings(), expected.columns(), expected.lines(), expected.terminal()));
            assertThat(result.diagnostics()).singleElement().satisfies(d -> {
                assertThat(d.severity()).as(assignment).isEqualTo(ConfigDiagnostic.Severity.ERROR);
                assertThat(d.message()).doesNotContain("secret", "bad");
            });
        }
    }

    @Test void nestedDiagnosticsRetainPositionsAndQuotedPathComponents() {
        var result = parse("""
            [terminal]
            "shell.program"='secret'
            [terminal.shell]
              future=7
            [terminal.cursor.future]
            [terminal."cursor.shape"]
            [terminal.env]
              "BAD.NAME"='secret'
              TERM='secret'
              COLORTERM='secret'
              BAD_NUL="bad\\u0000secret"
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.diagnostics()).hasSize(8);
        assertPosition(result, "terminal.\"shell.program\"", 2, 1, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.shell.future", 4, 3, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.cursor.future", 5, 1, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.\"cursor.shape\"", 6, 1, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.env.\"BAD.NAME\"", 8, 3, ConfigDiagnostic.Severity.ERROR);
        assertPosition(result, "terminal.env.TERM", 9, 3, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.env.COLORTERM", 10, 3, ConfigDiagnostic.Severity.WARNING);
        assertPosition(result, "terminal.env.BAD_NUL", 11, 3, ConfigDiagnostic.Severity.ERROR);
        assertThat(result.diagnostics()).allSatisfy(d -> assertThat(d.message()).doesNotContain("secret"));
    }

    @Test void shellExitChoiceDefaultsAndDiagnosticsUseTheExactSettingPosition() {
        assertThat(parse("").snapshot().terminal().onExit()).isEqualTo(ShellExitBehavior.KEEP_OPEN);
        assertPosition(parse("[terminal]\n  on_exit='CLOSE'"), "terminal.on_exit", 2, 3, ConfigDiagnostic.Severity.ERROR);
        assertPosition(parse("[terminal]\n  on_exit=7"), "terminal.on_exit", 2, 3, ConfigDiagnostic.Severity.ERROR);
        var terminal = TerminalConfig.defaults();
        assertThatNullPointerException().isThrownBy(() -> new TerminalConfig(terminal.shell(), terminal.env(),
            terminal.scrollback(), terminal.optionAsMeta(), terminal.cursorShape(), terminal.cursorBlink(),
            terminal.dimInactivePanes(), terminal.copyOnSelect(), terminal.bell(), null));
    }

    @Test void nestedTableAndArrayErrorsUseTheExactSettingPosition() {
        assertPosition(parse("[terminal]\n  shell=7"), "terminal.shell", 2, 3, ConfigDiagnostic.Severity.ERROR);
        assertPosition(parse("[terminal]\n  cursor=[]"), "terminal.cursor", 2, 3, ConfigDiagnostic.Severity.ERROR);
        assertPosition(parse("[terminal]\n  env=false"), "terminal.env", 2, 3, ConfigDiagnostic.Severity.ERROR);
        assertPosition(parse("[terminal.shell]\nargs=[\n 'secret',\n  7\n]"), "terminal.shell.args", 2, 1, ConfigDiagnostic.Severity.ERROR);
        assertPosition(parse("[font]\nfallback=[\n 'ok',\n  false\n]"), "font.fallback", 2, 1, ConfigDiagnostic.Severity.ERROR);
    }

    @Test void templateUncommentsToEverySupportedSetting() {
        String uncommented = ConfigTemplate.text(true).lines()
            .filter(line -> !line.contains("my-theme.toml"))
            .map(line -> line.matches("# ([a-z_0-9]+ = .*|\\[.*])") ? line.substring(2) : line)
            .collect(java.util.stream.Collectors.joining("\n"));
        var toml = Toml.parse(uncommented);
        assertThat(toml.errors()).isEmpty();
        for (String key : List.of("window.columns", "window.lines", "font.family", "font.fallback", "font.ligatures",
                "font.line_height", "terminal.shell.program", "terminal.shell.args", "terminal.env", "terminal.scrollback",
                "terminal.option_as_meta", "terminal.cursor.shape", "terminal.cursor.blink", "terminal.dim_inactive_panes",
                "terminal.copy_on_select", "terminal.bell", "terminal.on_exit")) {
            assertThat(toml.contains(key)).as(key).isTrue();
        }
    }

    @Test void defaultsAndOldConstructorRetainAppDefaultsAndRuntimeConversion() {
        var snapshot = parse("").snapshot();
        assertThat(snapshot.columns()).isEqualTo(150);
        assertThat(snapshot.lines()).isEqualTo(45);
        assertThat(snapshot.font()).isEqualTo(new FontConfig("JetBrains Mono", 16f,
            List.of("Symbols Nerd Font Mono", "Apple Color Emoji"), true, 1f));
        assertThat(snapshot.terminal()).isEqualTo(new TerminalConfig(new TerminalConfig.Shell("", List.of()),
            Map.of(), 10000, OptionAsMeta.LEFT, CursorStyle.BLOCK, true, .3f, false, BellMode.VISUAL));
        var legacy = new ConfigSnapshot(40, ToolbarMode.HIDDEN, false, FontConfig.defaults().withSize(22f), BuiltinTheme.LIGHT.appearance(), Map.of(), 150, 45, TerminalConfig.defaults());
        assertThat(legacy.font().size()).isEqualTo(22f);
        assertThat(legacy.fontSize()).isEqualTo(22f);
        assertThat(legacy.columns()).isEqualTo(150);
        assertThat(legacy.lines()).isEqualTo(45);
        assertThat(legacy.terminal()).isEqualTo(snapshot.terminal());
    }

    @Test void acceptsAllNumericBoundariesAndEnumChoices() {
        for (int columns : new int[]{5, 500}) assertThat(parse("window.columns=" + columns).snapshot().columns()).isEqualTo(columns);
        for (int lines : new int[]{2, 200}) assertThat(parse("window.lines=" + lines).snapshot().lines()).isEqualTo(lines);
        for (int scrollback : new int[]{0, 1000000}) assertThat(parse("terminal.scrollback=" + scrollback).snapshot().terminal().scrollback()).isEqualTo(scrollback);
        for (float height : new float[]{1, 3}) assertThat(parse("font.line_height=" + height).snapshot().font().lineHeight()).isEqualTo(height);
        for (float dim : new float[]{0, 1}) assertThat(parse("terminal.dim_inactive_panes=" + dim).snapshot().terminal().dimInactivePanes()).isEqualTo(dim);
        for (var entry : Map.of("left", OptionAsMeta.LEFT, "right", OptionAsMeta.RIGHT, "both", OptionAsMeta.BOTH, "none", OptionAsMeta.NONE).entrySet()) {
            assertThat(parse("terminal.option_as_meta='" + entry.getKey() + "'").snapshot().terminal().optionAsMeta()).isEqualTo(entry.getValue());
        }
        for (var entry : Map.of("block", CursorStyle.BLOCK, "beam", CursorStyle.BEAM, "underline", CursorStyle.UNDERLINE).entrySet()) {
            assertThat(parse("terminal.cursor.shape='" + entry.getKey() + "'").snapshot().terminal().cursorShape()).isEqualTo(entry.getValue());
        }
        for (var entry : Map.of("visual", BellMode.VISUAL, "sound", BellMode.SOUND, "none", BellMode.NONE).entrySet()) {
            assertThat(parse("terminal.bell='" + entry.getKey() + "'").snapshot().terminal().bell()).isEqualTo(entry.getValue());
        }
        var empty = parse("font.fallback=[]\nterminal.shell={program='', args=[]}\nterminal.env={}");
        assertThat(empty.diagnostics()).isEmpty();
        assertThat(empty.snapshot().font().fallback()).isEmpty();
    }

    @Test void environmentOmitsOnlyInvalidOrReservedEntriesAndKeepsValidValuesExact() {
        var result = parse("""
            [terminal.env]
            _OK1='one argument'
            EMPTY=''
            LOWER_case='still valid'
            '1INVALID'='secret'
            'BAD-NAME'='secret'
            TERM='secret'
            COLORTERM='secret'
            NUL="bad\\u0000secret"
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().terminal().env()).containsExactlyInAnyOrderEntriesOf(
            Map.of("_OK1", "one argument", "EMPTY", "", "LOWER_case", "still valid"));
        assertThat(result.diagnostics()).hasSize(5);
        assertThat(result.diagnostics()).allSatisfy(d -> assertThat(d.formatted()).doesNotContain("secret"));
        var typed = parse("terminal.env={GOOD='value', BAD=7}");
        assertThat(typed.rejected()).isTrue();
        assertThat(typed.snapshot().terminal().env()).containsExactlyEntriesOf(Map.of("GOOD", "value"));
    }

    @Test void listsDefaultAsUnitsWhileUnrelatedValidFieldsSurvive() {
        var result = parse("font.family='Example'\nfont.fallback=['ok', ' ']\nterminal.shell={program='custom', args=['ok', \"bad\\u0000arg\"]}");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().font().family()).isEqualTo("Example");
        assertThat(result.snapshot().font().fallback()).containsExactly("Symbols Nerd Font Mono", "Apple Color Emoji");
        assertThat(result.snapshot().terminal().shell().program()).isEqualTo("custom");
        assertThat(result.snapshot().terminal().shell().args()).isEmpty();
    }

    @Test void recordsCopyCollectionsAndRejectInvalidDirectConstruction() {
        var fallbacks = new ArrayList<>(List.of("Example"));
        var args = new ArrayList<>(List.of("one argument"));
        var env = new HashMap<>(Map.of("EXAMPLE", "one value"));
        var font = new FontConfig("Example", 17f, fallbacks, false, 2f);
        var shell = new TerminalConfig.Shell("custom", args);
        var terminal = new TerminalConfig(shell, env, 12, OptionAsMeta.NONE, CursorStyle.UNDERLINE, false, 0f, true, BellMode.NONE);
        fallbacks.clear(); args.clear(); env.clear();
        assertThat(font.withSize(20f)).isEqualTo(new FontConfig("Example", 20f, List.of("Example"), false, 2f));
        assertThat(shell.args()).containsExactly("one argument");
        assertThat(terminal.env()).containsExactlyEntriesOf(Map.of("EXAMPLE", "one value"));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> font.fallback().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> shell.args().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> terminal.env().clear());
        for (String invalid : List.of("", " ", "bad" + (char) 0)) {
            assertThatIllegalArgumentException().isThrownBy(() -> new FontConfig(invalid, 16, List.of(), true, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> new FontConfig("Example", 16, List.of(invalid), true, 1));
        }
        for (float size : new float[]{5, 73, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> font.withSize(size));
        }
        for (float height : new float[]{0, 3.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new FontConfig("Example", 16, List.of(), true, height));
        }
        for (String program : List.of(" ", "bad" + (char) 0)) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig.Shell(program, List.of()));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig.Shell("", List.of("bad" + (char) 0)));
        for (Map<String, String> invalid : List.of(Map.of("BAD.NAME", "secret"), Map.of("OK", "bad" + (char) 0), Map.of("TERM", "secret"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig(shell, invalid, 12, OptionAsMeta.NONE, CursorStyle.BLOCK, true, 0, false, BellMode.NONE));
        }
        for (int scrollback : new int[]{-1, 1000001}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig(shell, Map.of(), scrollback, OptionAsMeta.NONE, CursorStyle.BLOCK, true, 0, false, BellMode.NONE));
        }
        for (float dim : new float[]{-.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new TerminalConfig(shell, Map.of(), 12, OptionAsMeta.NONE, CursorStyle.BLOCK, true, dim, false, BellMode.NONE));
        }
        for (int columns : new int[]{4, 501}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ConfigSnapshot(38, ToolbarMode.ICONS, true, font, BuiltinTheme.DARK.appearance(), Map.of(), columns, 45, terminal));
        }
        for (int lines : new int[]{1, 201}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ConfigSnapshot(38, ToolbarMode.ICONS, true, font, BuiltinTheme.DARK.appearance(), Map.of(), 150, lines, terminal));
        }
    }

    private void assertPosition(ConfigLoader.Result result, String key, int line, int column, ConfigDiagnostic.Severity severity) {
        assertThat(result.diagnostics()).filteredOn(d -> d.key().equals(key)).singleElement().satisfies(d -> {
            assertThat(d.file()).isEqualTo(FILE);
            assertThat(d.line()).isEqualTo(line);
            assertThat(d.column()).isEqualTo(column);
            assertThat(d.severity()).isEqualTo(severity);
        });
    }
}
