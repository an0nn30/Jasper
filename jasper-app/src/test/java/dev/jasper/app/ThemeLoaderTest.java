package dev.jasper.app;

import dev.jasper.terminal.Palette;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ThemeLoaderTest {
    private final Path file = Path.of("night.toml");

    @Test void parsesSupportedColorsAndKeepsFixedDefaults() {
        var result = ThemeLoader.parse(file, """
            [colors.primary]
            background = '#101820'
            [colors.normal]
            red = '0xEF3340'
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(result.palette().ansi().get(1)).isEqualTo(new Color(0xef3340));
        assertThat(result.palette().foreground()).isEqualTo(Palette.jasperDark().foreground());
    }

    @Test void parsesEverySupportedFieldInAnsiOrder() {
        var result = ThemeLoader.parse(file, """
            [colors.primary]
            foreground = '#000001'
            background = '#000002'
            [colors.cursor]
            cursor = '#000003'
            [colors.selection]
            background = '#000004'
            [colors.normal]
            black='#000010'
            red='#000011'
            green='#000012'
            yellow='#000013'
            blue='#000014'
            magenta='#000015'
            cyan='#000016'
            white='#000017'
            [colors.bright]
            black='#000020'
            red='#000021'
            green='#000022'
            yellow='#000023'
            blue='#000024'
            magenta='#000025'
            cyan='#000026'
            white='#000027'
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.palette().foreground()).isEqualTo(new Color(1));
        assertThat(result.palette().background()).isEqualTo(new Color(2));
        assertThat(result.palette().cursor()).isEqualTo(new Color(3));
        assertThat(result.palette().selection()).isEqualTo(new Color(4));
        assertThat(result.palette().ansi()).containsExactly(
            new Color(0x10), new Color(0x11), new Color(0x12), new Color(0x13),
            new Color(0x14), new Color(0x15), new Color(0x16), new Color(0x17),
            new Color(0x20), new Color(0x21), new Color(0x22), new Color(0x23),
            new Color(0x24), new Color(0x25), new Color(0x26), new Color(0x27));
    }

    @Test void warnsForUnknownComponentWithItsSourcePosition() {
        var result = ThemeLoader.parse(file, """
            [colors.primary]
            background = '#101820'
            future = '#ffffff'
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(diagnostic.key()).isEqualTo("colors.primary.future");
            assertThat(diagnostic.line()).isEqualTo(3);
            assertThat(diagnostic.column()).isEqualTo(1);
        });
    }

    @Test void rejectsWrongTablesMalformedStringsTypesAndSymbolicReferences() {
        for (String text : new String[]{
            "colors.primary='#ffffff'",
            "[colors.primary]\nbackground='broken'",
            "[colors.primary]\nbackground=123",
            "[colors.primary]\nbackground='red'"}) {
            var result = ThemeLoader.parse(file, text);
            assertThat(result.rejected()).as(text).isTrue();
            assertThat(result.diagnostics()).anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
            assertThat(result.palette()).isEqualTo(Palette.jasperDark());
        }
    }

    @Test void rejectsSyntaxDuplicatesAndFilesWithoutSupportedLeaves() {
        for (String text : new String[]{
            "[colors.primary",
            "[colors.primary]\nbackground='#000000'\nbackground='#ffffff'",
            "[metadata]\nname='night'"}) {
            var result = ThemeLoader.parse(file, text);
            assertThat(result.rejected()).as(text).isTrue();
            assertThat(result.diagnostics()).anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        }
    }

    @Test void quotedDottedKeysAreUnknownSingleComponents() {
        var result = ThemeLoader.parse(file, """
            [colors.primary]
            background='#101820'
            'foreground.extra'='#ffffff'
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
            .containsExactly("colors.primary.foreground.extra");
    }
}
