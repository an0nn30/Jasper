package dev.jasper.app.config;

import dev.jasper.app.appearance.BuiltinTheme;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.HistorySettings;
import dev.jasper.app.config.ToolbarMode;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import static org.assertj.core.api.Assertions.*;

class ConfigLoaderTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }

    @Test void themeVariantDefaultsToDarkAndAcceptsLight() {
        var path = Path.of("config.toml");
        assertThat(ConfigLoader.parse(path, "", true).snapshot().variant()).isEqualTo(Appearance.DARK);
        assertThat(ConfigLoader.parse(path, "[ui.theme]\nvariant = 'light'\n", true).snapshot().variant())
            .isEqualTo(Appearance.LIGHT);
        assertThat(ConfigLoader.parse(path, "ui.theme.variant = 'dark'\n", true).snapshot().variant())
            .isEqualTo(Appearance.DARK);
    }

    @Test void emptyFileSuppliesUsableDefaultsOnBothPlatforms() {
        for (boolean mac : new boolean[]{true, false}) {
            var result = ConfigLoader.parse(FILE, "# empty", mac);
            assertThat(result.rejected()).isFalse();
            assertThat(result.diagnostics()).isEmpty();
            assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
            int modifiers = mac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
            assertThat(result.snapshot().bindings(mac).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_C, modifiers)))
                .contains(ActionId.COPY);
        }
    }

    @Test void readsEverySupportedFieldIncludingQuotedKeysAndIntegerFontSize() {
        var result = parse("""
            [window]
            "tab_height" = 44
            toolbar = "icons"
            status_bar = false
            [font]
            size = 18
            [ui.theme]
            variant = "light"
            [keybindings]
            "copy" = "cmd+v"
            paste = "cmd+c"
            next_tab = "cmd+}"
            previous_tab = "cmd+{"
            new_tab = "none"
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.diagnostics()).isEmpty();
        var state = result.snapshot();
        assertThat(state.tabHeight()).isEqualTo(44);
        assertThat(state.toolbar()).isEqualTo(ToolbarMode.ICONS);
        assertThat(state.statusBar()).isFalse();
        assertThat(state.fontSize()).isEqualTo(18f);
        assertThat(state.variant()).isEqualTo(Appearance.LIGHT);
        assertThat(state.bindings(true).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK)))
            .contains(ActionId.COPY);
        assertThat(state.bindings(true).strokeFor(ActionId.NEW_TAB)).isEmpty();
        assertThat(state.bindings(true).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_TAB);
    }

    @Test void acceptsNumericBoundariesAndEveryToolbarChoice() {
        for (int height : new int[]{28, 72}) assertThat(parse("window.tab_height=" + height).snapshot().tabHeight()).isEqualTo(height);
        for (float size : new float[]{6f, 72f, 13.5f}) assertThat(parse("font.size=" + size).snapshot().fontSize()).isEqualTo(size);
        assertThat(parse("window.toolbar='hidden'").snapshot().toolbar()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(parse("window.toolbar='icons_and_labels'").diagnostics()).isEmpty();
    }

    @Test void invalidValuesDefaultOnlyTheirKeyWithExactPosition() {
        var result = parse("[window]\ntab_height = 44\n[font]\n  size = 900");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().tabHeight()).isEqualTo(44);
        assertThat(result.snapshot().fontSize()).isEqualTo(16f);
        assertDiagnostic(result, "font.size", 4, 3, ConfigDiagnostic.Severity.ERROR);
        for (String text : new String[]{"window.tab_height=27", "window.tab_height=73", "font.size=5.9",
                "font.size=72.1", "font.size=nan", "font.size=inf", "font.size=-inf",
                "window.toolbar='secret-value'"}) {
            var invalid = parse(text);
            assertThat(invalid.rejected()).as(text).isFalse();
            assertThat(invalid.snapshot()).isEqualTo(ConfigSnapshot.defaults());
            assertThat(invalid.diagnostics()).hasSize(1);
            assertThat(invalid.diagnostics().getFirst().severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(invalid.diagnostics().getFirst().message()).doesNotContain("secret-value");
        }
    }

    @Test void wrongScalarTypesRejectWholeCandidate() {
        for (String assignment : new String[]{"window.tab_height=38.0", "window.toolbar=true", "window.status_bar=1",
                "font.size='secret-value'", "ui.theme.variant=1", "keybindings.copy=7", "font.size=[16]",
                "window.tab_height={x=38}"}) {
            var result = parse(assignment);
            assertThat(result.rejected()).as(assignment).isTrue();
            assertThat(result.diagnostics()).hasSize(1);
            assertThat(result.diagnostics().getFirst().line()).isEqualTo(1);
            assertThat(result.diagnostics().getFirst().severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(result.diagnostics().getFirst().message()).doesNotContain("secret-value");
        }
    }

    @Test void wrongKnownTablesRejectAtTheirOwnPosition() {
        for (String table : new String[]{"window", "font", "ui", "keybindings"}) {
            var result = parse("# comment\n  " + table + " = 2");
            assertThat(result.rejected()).isTrue();
            assertDiagnostic(result, table, 2, 3, ConfigDiagnostic.Severity.ERROR);
        }
        var array = parse("[[window]]\ntab_height=44");
        assertThat(array.rejected()).isTrue();
        assertDiagnostic(array, "window", 1, 1, ConfigDiagnostic.Severity.ERROR);
    }

    @Test void syntaxAndDuplicateKeysRejectWithoutEchoingValues() {
        var duplicate = parse("[font]\nsize=16\nsize=20");
        assertThat(duplicate.rejected()).isTrue();
        assertThat(duplicate.diagnostics().getFirst().line()).isEqualTo(3);
        var malformed = parse("[font]\nsize = secret-value");
        assertThat(malformed.rejected()).isTrue();
        assertThat(malformed.diagnostics().getFirst().line()).isEqualTo(2);
        assertThat(malformed.diagnostics().getFirst().column()).isPositive();
        assertThat(malformed.diagnostics().getFirst().message()).doesNotContain("secret-value");
    }

    @Test void unknownKeysAndEmptyTablesWarnWhileSupportedSettingsApply() {
        var result = parse("""
            [window]
            tab_height=44
            future=9
            [font]
            future_font='secret-value'
            [unknown]
            [keybindings]
            "future.action"='secret-value'
            """);
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().tabHeight()).isEqualTo(44);
        assertThat(result.snapshot().keybindings()).isEmpty();
        assertThat(result.diagnostics()).hasSize(4);
        assertDiagnostic(result, "window.future", 3, 1, ConfigDiagnostic.Severity.WARNING);
        assertDiagnostic(result, "font.future_font", 5, 1, ConfigDiagnostic.Severity.WARNING);
        assertDiagnostic(result, "unknown", 6, 1, ConfigDiagnostic.Severity.WARNING);
        assertDiagnostic(result, "keybindings.\"future.action\"", 8, 1, ConfigDiagnostic.Severity.WARNING);
        assertThat(result.diagnostics()).allSatisfy(d -> assertThat(d.message()).doesNotContain("secret-value"));
    }

    @Test void quotedDottedKeysDoNotMasqueradeAsKnownPaths() {
        var result = parse("\"window.tab_height\"=60\nwindow.\"tab.height\"=61\nwindow.tab_height=44");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().tabHeight()).isEqualTo(44);
        assertThat(result.diagnostics()).hasSize(2);
        assertDiagnostic(result, "\"window.tab_height\"", 1, 1, ConfigDiagnostic.Severity.WARNING);
        assertDiagnostic(result, "window.\"tab.height\"", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void unknownActionOfAnyTypeWarnsAndDoesNotRejectKnownOverrides() {
        var result = parse("[keybindings]\nunknown=42\ncopy='none'");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().bindings(true).strokeFor(ActionId.COPY)).isEmpty();
        assertDiagnostic(result, "keybindings.unknown", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void invalidOrCollidingBindingsDefaultEntireMapAndPreserveOtherFields() {
        for (String binding : new String[]{"cmd+v", "cmd+secret-value", "cmd+{"}) {
            var result = parse("window.tab_height=44\n[keybindings]\nnew_tab='none'\ncopy='" + binding + "'");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().tabHeight()).isEqualTo(44);
            assertThat(result.snapshot().keybindings()).isEmpty();
            assertDiagnostic(result, "keybindings.copy", 4, 1, ConfigDiagnostic.Severity.ERROR);
            assertThat(result.diagnostics().getFirst().message()).doesNotContain("secret-value");
        }
    }

    @Test void usesActualNonMacDefaultsAndLiteralOverrideSemantics() {
        var result = ConfigLoader.parse(FILE, "keybindings.split_down='cmd+shift+d'", false);
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().keybindings()).isEmpty();
        assertThat(result.diagnostics()).hasSize(1);
        assertThat(result.snapshot().bindings(false).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_D,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).contains(ActionId.SPLIT_DOWN);
    }

    @Test void snapshotAndResultDefensivelyCopyCollections() {
        var bindings = new HashMap<String,String>();
        bindings.put("copy", "none");
        var snapshot = new ConfigSnapshot(40, ToolbarMode.HIDDEN, false, FontConfig.defaults().withSize(20f), BuiltinTheme.LIGHT.appearance(), bindings, 150, 45, TerminalConfig.defaults());
        bindings.put("copy", "cmd+c");
        assertThat(snapshot.bindings(true).strokeFor(ActionId.COPY)).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> snapshot.keybindings().put("paste", "none"));
        var diagnostics = new ArrayList<ConfigDiagnostic>();
        var result = new ConfigLoader.Result(snapshot, diagnostics, false);
        diagnostics.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, FILE, 2, 3, "x", "Unknown setting."));
        assertThat(result.diagnostics()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> result.diagnostics().clear());
    }

    @Test void directSnapshotsRejectInvalidScalarFieldsAndBindings() {
        for (int height : new int[]{0, 27, 73}) assertThatIllegalArgumentException().isThrownBy(() ->
            new ConfigSnapshot(height, ToolbarMode.ICONS, true, FontConfig.defaults().withSize(16f), BuiltinTheme.DARK.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()));
        for (float size : new float[]{0, 5.9f, 72.1f, Float.NaN, Float.POSITIVE_INFINITY}) assertThatIllegalArgumentException().isThrownBy(() ->
            new ConfigSnapshot(38, ToolbarMode.ICONS, true, FontConfig.defaults().withSize(size), BuiltinTheme.DARK.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()));
        assertThatNullPointerException().isThrownBy(() -> new ConfigSnapshot(38, null, true, FontConfig.defaults().withSize(16f), BuiltinTheme.DARK.appearance(), Map.of(), 150, 45, TerminalConfig.defaults()));
        assertThatNullPointerException().isThrownBy(() -> new ConfigSnapshot(38, ToolbarMode.ICONS, true, FontConfig.defaults().withSize(16f), (Appearance) null, Map.of(), 150, 45, TerminalConfig.defaults()));
        for (Map<String,String> bindings : java.util.List.of(Map.of("unknown", "none"), Map.of("copy", "cmd+secret"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> new ConfigSnapshot(38, ToolbarMode.ICONS, true, FontConfig.defaults().withSize(16f), BuiltinTheme.DARK.appearance(), bindings, 150, 45, TerminalConfig.defaults()));
        }
    }

    @Test void diagnosticsFormatPositionAndHandleUnavailableLocation() {
        assertThat(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, FILE, 4, 3, "font.size", "Expected a number.").formatted())
            .contains(FILE.toString() + ":4:3", "font.size", "Expected a number.");
        assertThat(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, FILE, 0, 0, "", "Cannot read file.").formatted())
            .contains(FILE.toString(), "Cannot read file.").doesNotContain(":0");
    }

    @Test void buddyEnabledParsesAndRejectsNonBooleans() {
        var off = parse("[buddy]\nenabled = false\n");
        assertThat(off.rejected()).isFalse();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(off.snapshot().buddyEnabled()).isFalse();
        assertThat(ConfigSnapshot.defaults().buddyEnabled()).isTrue();
        var bad = parse("[buddy]\nenabled = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().buddyEnabled()).isTrue();
        assertDiagnostic(bad, "buddy.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[buddy]\nvisible = true\n");
        assertDiagnostic(unknown, "buddy.visible", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void backgroundEnabledDefaultsToOffAndRejectsNonBooleans() {
        var on = parse("[background]\nenabled = true\n");
        assertThat(on.rejected()).isFalse();
        assertThat(on.diagnostics()).isEmpty();
        assertThat(on.snapshot().backgroundEnabled()).isTrue();
        // Residency is opt-in: an absent table and an empty file both mean off.
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isFalse();
        assertThat(parse("# empty").snapshot().backgroundEnabled()).isFalse();
        var bad = parse("[background]\nenabled = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().backgroundEnabled()).isFalse();
        assertDiagnostic(bad, "background.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[background]\nstart_at_login = true\n");
        assertDiagnostic(unknown, "background.start_at_login", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void paletteMaxResultsParsesWithinRangeAndRejectsOthers() {
        var three = parse("[palette]\nmax_results = 3\n");
        assertThat(three.rejected()).isFalse();
        assertThat(three.diagnostics()).isEmpty();
        assertThat(three.snapshot().maxResults()).isEqualTo(3);
        assertThat(ConfigSnapshot.defaults().maxResults()).isEqualTo(5);
        var tooMany = parse("[palette]\nmax_results = 21\n");
        assertThat(tooMany.rejected()).isFalse();
        assertThat(tooMany.snapshot().maxResults()).isEqualTo(5);
        assertDiagnostic(tooMany, "palette.max_results", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var zero = parse("[palette]\nmax_results = 0\n");
        assertThat(zero.snapshot().maxResults()).isEqualTo(5);
        assertDiagnostic(zero, "palette.max_results", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var text = parse("[palette]\nmax_results = \"5\"\n");
        assertThat(text.rejected()).isTrue();
        assertDiagnostic(text, "palette.max_results", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[palette]\nrows = 5\n");
        assertDiagnostic(unknown, "palette.rows", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void historyEnabledParsesAndRejectsNonBooleans() {
        var off = parse("[palette.scopes.history]\nenabled = false\n");
        assertThat(off.rejected()).isFalse();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(off.snapshot().historyEnabled()).isFalse();
        assertThat(ConfigSnapshot.defaults().historyEnabled()).isTrue();
        var bad = parse("[palette.scopes.history]\nenabled = \"yes\"\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().historyEnabled()).isTrue();
        assertDiagnostic(bad, "palette.scopes.history.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[palette.scopes.history]\nshells = [\"zsh\"]\n");
        assertDiagnostic(unknown, "palette.scopes.history.shells", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void shellIntegrationParsesItsThreeChoicesAndRejectsOthers() {
        assertThat(parse("[terminal]\nshell_integration = \"manual\"\n").snapshot().terminal().shellIntegration())
            .isEqualTo(ShellIntegrationMode.MANUAL);
        assertThat(parse("[terminal]\nshell_integration = \"off\"\n").snapshot().terminal().shellIntegration())
            .isEqualTo(ShellIntegrationMode.OFF);
        assertThat(ConfigSnapshot.defaults().terminal().shellIntegration()).isEqualTo(ShellIntegrationMode.AUTO);
        var bad = parse("[terminal]\nshell_integration = \"sometimes\"\n");
        assertThat(bad.snapshot().terminal().shellIntegration()).isEqualTo(ShellIntegrationMode.AUTO);
        assertDiagnostic(bad, "terminal.shell_integration", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var wrongType = parse("[terminal]\nshell_integration = true\n");
        assertThat(wrongType.rejected()).isTrue();
    }

    private void assertDiagnostic(ConfigLoader.Result result, String key, int line, int column, ConfigDiagnostic.Severity severity) {
        assertThat(result.diagnostics()).filteredOn(d -> d.key().equals(key)).singleElement().satisfies(d -> {
            assertThat(d.file()).isEqualTo(FILE);
            assertThat(d.line()).isEqualTo(line);
            assertThat(d.column()).isEqualTo(column);
            assertThat(d.severity()).isEqualTo(severity);
        });
    }

    @Test void trivialCommandsParsesValidatesAndDefaultsToTheBuiltInList() {
        assertThat(parse("").snapshot().trivialCommands())
            .isEqualTo(HistorySettings.defaults().trivialCommands())
            .contains("exit", "clear", "cd");

        assertThat(parse("[palette.scopes.history]\ntrivial_commands=['foo','BAR']\n").snapshot().trivialCommands())
            .as("the user's list replaces the default, normalized to lower case")
            .containsExactly("foo", "bar");

        assertThat(parse("[palette.scopes.history]\ntrivial_commands=[]\n").snapshot().trivialCommands())
            .as("an empty list turns de-ranking off").isEmpty();

        // An entry with whitespace could never match: only a command's first word is compared.
        var spaced = parse("[palette.scopes.history]\ntrivial_commands=['git status']\n");
        assertThat(spaced.snapshot().trivialCommands()).isEqualTo(HistorySettings.defaults().trivialCommands());
        assertThat(spaced.diagnostics()).isNotEmpty();

        var wrongType = parse("[palette.scopes.history]\ntrivial_commands='exit'\n");
        assertThat(wrongType.snapshot().trivialCommands()).isEqualTo(HistorySettings.defaults().trivialCommands());
        assertThat(wrongType.diagnostics()).isNotEmpty();
    }

    /** The scope settings moved under palette.scopes so other scopes can gain their own. */
    @Test void historyScopeSettingsLiveUnderPaletteScopes() {
        assertThat(parse("[palette.scopes.history]\nenabled=false\n").snapshot().historyEnabled()).isFalse();
        // The old top-level table is no longer part of the schema and is reported, not silently kept.
        var old = parse("[history]\nenabled=false\n");
        assertThat(old.snapshot().historyEnabled()).as("the old key no longer applies").isTrue();
        assertThat(old.diagnostics()).isNotEmpty();
    }

    @Test void longCommandSecondsParsesItsRangeAndDefaultsToTen() {
        assertThat(parse("").snapshot().longCommandSeconds()).isEqualTo(10);
        assertThat(parse("[notifications]\nlong_command_seconds=0\n").snapshot().longCommandSeconds()).isZero();
        assertThat(parse("[notifications]\nlong_command_seconds=3600\n").snapshot().longCommandSeconds()).isEqualTo(3600);
        var high = parse("[notifications]\nlong_command_seconds=3601\n");
        assertThat(high.snapshot().longCommandSeconds()).as("out of range keeps the default").isEqualTo(10);
        assertThat(high.diagnostics()).isNotEmpty();
    }
}
