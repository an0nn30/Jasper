package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.settings.LegacyKey;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigAliasTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private static ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }

    @Test void versionOneKeysStillLoadWithMovedWarnings() {
        var result = parse("[font]\nsize = 20\nfamily = 'Iosevka'\n[background]\nenabled = true\n[ui.theme]\nterminal = 'dark'\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().fontSize()).isEqualTo(20f);
        assertThat(result.snapshot().font().family()).isEqualTo("Iosevka");
        assertThat(result.snapshot().backgroundEnabled()).isTrue();
        assertThat(result.snapshot().terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(result.diagnostics()).allSatisfy(d -> assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING))
            .extracting(ConfigDiagnostic::key, ConfigDiagnostic::line, ConfigDiagnostic::message)
            .containsExactly(
                tuple("font.size", 2, "Moved to terminal.font.size. Use Migrate Config to update the file."),
                tuple("font.family", 3, "Moved to terminal.font.family. Use Migrate Config to update the file."),
                tuple("background.enabled", 5, "Moved to system.run_in_background. Use Migrate Config to update the file."),
                tuple("ui.theme.terminal", 7, "Moved to ui.theme.workspace. Use Migrate Config to update the file."));
        assertThat(result.legacyKeys()).containsExactly(
            new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")),
            new LegacyKey(List.of("font", "family"), List.of("terminal", "font", "family")),
            new LegacyKey(List.of("background", "enabled"), List.of("system", "run_in_background")),
            new LegacyKey(List.of("ui", "theme", "terminal"), List.of("ui", "theme", "workspace")));
        assertThat(result.values().source(CoreSettings.TERMINAL_FONT_SIZE)).hasValue(List.of("font", "size"));
    }

    @Test void theCurrentKeyWinsWhereverItAppears() {
        for (String text : List.of("font.size = 12\n[terminal.font]\nsize = 18\n", "[terminal.font]\nsize = 18\n[font]\nsize = 12\n")) {
            var result = parse(text);
            assertThat(result.snapshot().fontSize()).as(text).isEqualTo(18f);
            assertThat(result.diagnostics()).as(text).singleElement().satisfies(d -> {
                assertThat(d.key()).isEqualTo("font.size");
                assertThat(d.message()).isEqualTo("Superseded by terminal.font.size; ignored.");
            });
            assertThat(result.legacyKeys()).as(text).containsExactly(new LegacyKey(List.of("font", "size"), List.of("terminal", "font", "size")));
            assertThat(result.values().source(CoreSettings.TERMINAL_FONT_SIZE)).hasValue(List.of("terminal", "font", "size"));
        }
    }

    @Test void anOldKeyWithABadValueReportsBoth() {
        var result = parse("[font]\nsize = 100\n");
        assertThat(result.snapshot().fontSize()).isEqualTo(12f);
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::message).containsExactlyInAnyOrder(
            "Use a finite number from 6–72; using the default.",
            "Moved to terminal.font.size. Use Migrate Config to update the file.");
        assertThat(parse("[font]\nsize = 'big'\n").rejected()).isTrue();
    }

    @Test void theVersionKeyIsCheckedButNeverRequired() {
        assertThat(parse("version = 2\n").diagnostics()).isEmpty();
        assertThat(parse("version = 1\n").diagnostics()).isEmpty();
        assertThat(parse("version = 3\n").diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).isEqualTo("Written by a newer Jasper; unknown settings are ignored.");
        });
        assertThat(parse("version = 0\n").diagnostics()).singleElement()
            .satisfies(d -> assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR));
        assertThat(parse("version = 'two'\n").rejected()).isTrue();
    }

    @Test void theOldPaletteResultCapIsReadAsTheVisibleRows() {
        var result = parse("[palette]\nmax_results = 8\n");
        assertThat(result.snapshot().visibleRows()).isEqualTo(8);
        assertThat(result.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).isEqualTo("Moved to palette.visible_rows. Use Migrate Config to update the file.");
        });
        assertThat(result.legacyKeys()).containsExactly(
            new LegacyKey(List.of("palette", "max_results"), List.of("palette", "visible_rows")));
    }

    @Test void deadTablesAndPresenceAreReported() {
        var dead = parse("[palette.scopes]\n[plugins.'dev.x']\na = 1\n");
        assertThat(dead.deadTables()).containsExactly(List.of("palette", "scopes"), List.of("plugins", "dev.x"));
        var present = parse("[window]\ntab_height = 44\n");
        assertThat(present.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        assertThat(present.values().present(CoreSettings.WINDOW_LINES)).isFalse();
        assertThat(present.deadTables()).isEmpty();
        assertThat(parse("[palette.scopes.history]\nenabled = false\n").diagnostics().getFirst().message())
            .contains("plugins/dev.jasper.history/dev.jasper.history.toml");
    }
}
