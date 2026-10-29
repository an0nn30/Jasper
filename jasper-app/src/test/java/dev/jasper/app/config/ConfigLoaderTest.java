package dev.jasper.app.config;

import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ToolbarMode;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import static org.assertj.core.api.Assertions.*;

class ConfigLoaderTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private ConfigLoader.Result parse(String text) { return ConfigLoader.parse(FILE, text, true); }

    @Test void missingFontSettingsUseBundledCousineAtTwelvePointsWhileSavedChoicesWin() {
        var defaults = parse("").snapshot();
        assertThat(defaults.font().family()).isEqualTo("Cousine Nerd Font Mono");
        assertThat(defaults.fontSize()).isEqualTo(12f);
        var saved = parse("terminal.font.family='JetBrains Mono'\nterminal.font.size=16").snapshot();
        assertThat(saved.font().family()).isEqualTo("JetBrains Mono");
        assertThat(saved.fontSize()).isEqualTo(16f);
        assertThat(parse("terminal.font.size=19").snapshot().font().family()).isEqualTo("Cousine Nerd Font Mono");
        assertThat(parse("terminal.font.family='Monospaced'").snapshot().fontSize()).isEqualTo(12f);
    }

    @Test void uiTypographyIsIndependentOfTerminalFontAndSurvivesBuilderCopies() {
        var result = parse("ui.font.family='Serif'\nui.font.size=18.5\nterminal.font.size=23");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().uiFont()).isEqualTo(new UiFontConfig("Serif", 18.5f));
        assertThat(result.snapshot().fontSize()).isEqualTo(23);
        assertThat(result.snapshot().toBuilder().columns(80).build().uiFont()).isEqualTo(result.snapshot().uiFont());
        for (String invalid : new String[]{"ui.font.size=7", "ui.font.size=33", "ui.font.size=nan", "ui.font.family=' '"}) {
            var bad = parse(invalid);
            assertThat(bad.diagnostics()).hasSize(1);
            assertThat(bad.snapshot().uiFont()).isEqualTo(UiFontConfig.defaults());
        }
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
            [terminal.font]
            size = 18
            [ui]
            appearance = "light"
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
        assertThat(state.appearance()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(state.bindings(true).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK)))
            .contains(ActionId.COPY);
        assertThat(state.bindings(true).strokeFor(ActionId.NEW_TAB)).isEmpty();
        assertThat(state.bindings(true).actionFor(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET,
            InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).contains(ActionId.NEXT_TAB);
    }

    @Test void acceptsNumericBoundariesAndEveryToolbarChoice() {
        for (int height : new int[]{20, 30, 72}) assertThat(parse("window.tab_height=" + height).snapshot().tabHeight()).isEqualTo(height);
        for (float size : new float[]{6f, 72f, 13.5f}) assertThat(parse("terminal.font.size=" + size).snapshot().fontSize()).isEqualTo(size);
        assertThat(parse("window.toolbar='hidden'").snapshot().toolbar()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(parse("window.toolbar='icons_and_labels'").diagnostics()).isEmpty();
    }

    @Test void railWidthDefaultsToTwentyEightAndClampsOutOfRangeValuesWithAWarning() {
        assertThat(parse("").snapshot().railWidth()).isEqualTo(28);
        assertThat(ConfigSnapshot.defaults().railWidth()).isEqualTo(28);
        for (int width : new int[]{28, 36, 64}) assertThat(parse("window.rail_width=" + width).snapshot().railWidth()).isEqualTo(width);
        for (int[] example : new int[][]{{10, 28}, {27, 28}, {65, 64}, {200, 64}}) {
            var result = parse("[window]\nrail_width = " + example[0] + "\n");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().railWidth()).as("rail_width = %d", example[0]).isEqualTo(example[1]);
            assertDiagnostic(result, "window.rail_width", 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics().getFirst().message()).contains(String.valueOf(example[1]));
        }
        assertThat(ConfigSnapshot.defaults().toBuilder().railWidth(48).build().railWidth()).isEqualTo(48);
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.defaults().toBuilder().railWidth(27).build());
    }

    @Test void toolbarHeightDefaultsToThirtyAndClampsOutOfRangeValuesWithAWarning() {
        assertThat(parse("").snapshot().toolbarHeight()).isEqualTo(30);
        assertThat(ConfigSnapshot.defaults().toolbarHeight()).isEqualTo(30);
        for (int height : new int[]{28, 30, 64}) assertThat(parse("window.toolbar_height=" + height).snapshot().toolbarHeight()).isEqualTo(height);
        for (int[] example : new int[][]{{0, 28}, {24, 28}, {27, 28}, {65, 64}, {200, 64}}) {
            var result = parse("[window]\ntoolbar_height = " + example[0] + "\n");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().toolbarHeight()).as("toolbar_height = %d", example[0]).isEqualTo(example[1]);
            assertDiagnostic(result, "window.toolbar_height", 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics().getFirst().message()).contains(String.valueOf(example[1]));
        }
        assertThat(ConfigSnapshot.defaults().toBuilder().toolbarHeight(40).build().toolbarHeight()).isEqualTo(40);
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.defaults().toBuilder().toolbarHeight(27).build());
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.defaults().toBuilder().toolbarHeight(65).build());
    }

    @Test void statusBarHeightDefaultsToTwentyFourAndClampsOutOfRangeValuesWithAWarning() {
        assertThat(parse("").snapshot().statusBarHeight()).isEqualTo(24);
        assertThat(ConfigSnapshot.defaults().statusBarHeight()).isEqualTo(24);
        for (int height : new int[]{20, 30, 48}) assertThat(parse("window.status_bar_height=" + height).snapshot().statusBarHeight()).isEqualTo(height);
        for (int[] example : new int[][]{{0, 20}, {19, 20}, {49, 48}, {200, 48}}) {
            var result = parse("[window]\nstatus_bar_height = " + example[0] + "\n");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().statusBarHeight()).as("status_bar_height = %d", example[0]).isEqualTo(example[1]);
            assertDiagnostic(result, "window.status_bar_height", 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics().getFirst().message()).contains(String.valueOf(example[1]));
        }
        // The two heights and the visibility switches are independent keys.
        var both = parse("[window]\ntoolbar = \"hidden\"\ntoolbar_height = 40\nstatus_bar = false\nstatus_bar_height = 24\n").snapshot();
        assertThat(both.toolbar()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(both.toolbarHeight()).isEqualTo(40);
        assertThat(both.statusBar()).isFalse();
        assertThat(both.statusBarHeight()).isEqualTo(24);
        assertThat(ConfigSnapshot.defaults().toBuilder().statusBarHeight(40).build().statusBarHeight()).isEqualTo(40);
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.defaults().toBuilder().statusBarHeight(19).build());
        assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.defaults().toBuilder().statusBarHeight(49).build());
    }

    @Test void tabHeightDefaultsToThirtyAndClampsOutOfRangeValuesWithAWarning() {
        assertThat(parse("").snapshot().tabHeight()).isEqualTo(30);
        assertThat(ConfigSnapshot.defaults().tabHeight()).isEqualTo(30);
        for (int[] example : new int[][]{{12, 20}, {19, 20}, {73, 72}, {100, 72}}) {
            var result = parse("[window]\ntab_height = " + example[0] + "\n");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().tabHeight()).as("tab_height = %d", example[0]).isEqualTo(example[1]);
            assertDiagnostic(result, "window.tab_height", 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics().getFirst().message()).contains(String.valueOf(example[1]));
        }
    }

    @Test void invalidValuesDefaultOnlyTheirKeyWithExactPosition() {
        var result = parse("[window]\ntab_height = 44\n[terminal.font]\n  size = 900");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().tabHeight()).isEqualTo(44);
        assertThat(result.snapshot().fontSize()).isEqualTo(12f);
        assertDiagnostic(result, "terminal.font.size", 4, 3, ConfigDiagnostic.Severity.ERROR);
        for (String text : new String[]{"terminal.font.size=5.9",
                "terminal.font.size=72.1", "terminal.font.size=nan", "terminal.font.size=inf", "terminal.font.size=-inf",
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
                "terminal.font.size='secret-value'", "ui.appearance=1", "keybindings.copy=7", "terminal.font.size=[16]",
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
        var duplicate = parse("[terminal.font]\nsize=16\nsize=20");
        assertThat(duplicate.rejected()).isTrue();
        assertThat(duplicate.diagnostics().getFirst().line()).isEqualTo(3);
        var malformed = parse("[terminal.font]\nsize = secret-value");
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
            [terminal.font]
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
        assertDiagnostic(result, "terminal.font.future_font", 5, 1, ConfigDiagnostic.Severity.WARNING);
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

    /** Parent tabs spec 8's last bullet: the group ids are a clean break, so an old one is an unknown action. */
    @Test void aRemovedGroupIdIsReportedAsAnUnknownAction() {
        var result = parse("[keybindings]\nclose_group = \"cmd+w\"");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().keybindings()).doesNotContainKey("close_group");
        assertDiagnostic(result, "keybindings.close_group", 2, 1, ConfigDiagnostic.Severity.WARNING);
        assertThat(result.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));
    }

    /** Tabs and panes spec 10: the removed ids are a clean break, so each is an unknown action. */
    @Test void aRemovedIdIsReportedAsAnUnknownAction() {
        for (String id : new String[] {"pop_out_tab", "pop_out_pane", "pop_out_parent_tab", "bring_back", "move_tab_to_main_window",
                "new_tab_in_pane", "close_parent_tab", "next_parent_tab", "previous_parent_tab", "select_parent_tab_1",
                "select_parent_tab_9"}) {
            var result = parse("[keybindings]\n" + id + " = \"cmd+w\"");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().keybindings()).doesNotContainKey(id);
            assertDiagnostic(result, "keybindings." + id, 2, 1, ConfigDiagnostic.Severity.WARNING);
            assertThat(result.diagnostics()).singleElement().satisfies(d -> assertThat(d.message()).contains("Unknown action"));
        }
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
        var snapshot = ConfigSnapshot.builder().tabHeight(40).toolbar(ToolbarMode.HIDDEN).statusBar(false)
            .font(FontConfig.defaults().withSize(20f)).keybindings(bindings).columns(150).lines(45)
            .terminal(TerminalConfig.defaults()).build();
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
        for (int height : new int[]{0, 19, 73}) assertThatIllegalArgumentException().isThrownBy(() ->
            ConfigSnapshot.builder().tabHeight(height).toolbar(ToolbarMode.ICONS).statusBar(true)
                .font(FontConfig.defaults().withSize(16f)).keybindings(Map.of()).columns(150).lines(45)
                .terminal(TerminalConfig.defaults()).build());
        for (float size : new float[]{0, 5.9f, 72.1f, Float.NaN, Float.POSITIVE_INFINITY}) assertThatIllegalArgumentException().isThrownBy(() ->
            ConfigSnapshot.builder().tabHeight(38).toolbar(ToolbarMode.ICONS).statusBar(true)
                .font(FontConfig.defaults().withSize(size)).keybindings(Map.of()).columns(150).lines(45)
                .terminal(TerminalConfig.defaults()).build());
        assertThatNullPointerException().isThrownBy(() -> ConfigSnapshot.builder().tabHeight(38).toolbar(null).statusBar(true)
            .font(FontConfig.defaults().withSize(16f)).keybindings(Map.of()).columns(150).lines(45)
            .terminal(TerminalConfig.defaults()).build());
        for (Map<String,String> bindings : java.util.List.of(Map.of("unknown", "none"), Map.of("copy", "cmd+secret"))) {
            assertThatIllegalArgumentException().isThrownBy(() -> ConfigSnapshot.builder().tabHeight(38)
                .toolbar(ToolbarMode.ICONS).statusBar(true).font(FontConfig.defaults().withSize(16f)).keybindings(bindings)
                .columns(150).lines(45).terminal(TerminalConfig.defaults()).build());
        }
    }

    @Test void diagnosticsFormatPositionAndHandleUnavailableLocation() {
        assertThat(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, FILE, 4, 3, "terminal.font.size", "Expected a number.").formatted())
            .contains(FILE.toString() + ":4:3", "terminal.font.size", "Expected a number.");
        assertThat(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, FILE, 0, 0, "", "Cannot read file.").formatted())
            .contains(FILE.toString(), "Cannot read file.").doesNotContain(":0");
    }

    @Test void buddyEnabledParsesAndRejectsNonBooleans() {
        var off = parse("[buddy]\nenabled = false\n");
        assertThat(off.rejected()).isFalse();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(off.snapshot().buddyEnabled()).isFalse();
        assertThat(parse("").snapshot().buddyEnabled()).as("Jasper is opt-in").isFalse();
        assertThat(ConfigSnapshot.defaults().buddyEnabled()).isFalse();
        var on = parse("[buddy]\nenabled = true\n");
        assertThat(on.diagnostics()).isEmpty();
        assertThat(on.snapshot().buddyEnabled()).isTrue();
        var bad = parse("[buddy]\nenabled = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().buddyEnabled()).isFalse();
        assertDiagnostic(bad, "buddy.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[buddy]\nvisible = true\n");
        assertDiagnostic(unknown, "buddy.visible", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void savedResidencyChoicesAndAliasesSurviveTheNewDefault() {
        for (String key : List.of("system.run_in_background", "background.enabled")) {
            for (boolean value : new boolean[]{false, true}) {
                var loaded = parse(key + " = " + value + "\n");
                assertThat(loaded.rejected()).isFalse();
                assertThat(loaded.snapshot().backgroundEnabled()).isEqualTo(value);
            }
        }
        var both = parse("background.enabled = true\nsystem.run_in_background = false\n");
        assertThat(both.snapshot().backgroundEnabled()).isFalse();
    }

    @Test void backgroundEnabledFollowsThePlatformDefaultAndRejectsNonBooleans() {
        boolean platform = CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue();
        var on = parse("[system]\nrun_in_background = true\n");
        assertThat(on.rejected()).isFalse();
        assertThat(on.diagnostics()).isEmpty();
        assertThat(on.snapshot().backgroundEnabled()).isTrue();
        assertThat(parse("[system]\nrun_in_background = false\n").snapshot().backgroundEnabled()).isFalse();
        // An absent table and an empty file both mean the platform default.
        assertThat(ConfigSnapshot.defaults().backgroundEnabled()).isEqualTo(platform);
        assertThat(parse("# empty").snapshot().backgroundEnabled()).isEqualTo(platform);
        var bad = parse("[system]\nrun_in_background = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().backgroundEnabled()).isEqualTo(platform);
        assertDiagnostic(bad, "system.run_in_background", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[system]\nstart_at_login = true\n");
        assertDiagnostic(unknown, "system.start_at_login", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }

    @Test void paletteVisibleRowsParsesWithinRangeAndRejectsOthers() {
        var three = parse("[palette]\nvisible_rows = 3\n");
        assertThat(three.rejected()).isFalse();
        assertThat(three.diagnostics()).isEmpty();
        assertThat(three.snapshot().visibleRows()).isEqualTo(3);
        assertThat(ConfigSnapshot.defaults().visibleRows()).isEqualTo(5);
        var tooMany = parse("[palette]\nvisible_rows = 21\n");
        assertThat(tooMany.rejected()).isFalse();
        assertThat(tooMany.snapshot().visibleRows()).isEqualTo(5);
        assertDiagnostic(tooMany, "palette.visible_rows", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var zero = parse("[palette]\nvisible_rows = 0\n");
        assertThat(zero.snapshot().visibleRows()).isEqualTo(5);
        assertDiagnostic(zero, "palette.visible_rows", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var text = parse("[palette]\nvisible_rows = \"5\"\n");
        assertThat(text.rejected()).isTrue();
        assertDiagnostic(text, "palette.visible_rows", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[palette]\nrows = 5\n");
        assertDiagnostic(unknown, "palette.rows", 2, 1, ConfigDiagnostic.Severity.WARNING);
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

    /** History left the application for a plugin; its old table is reported as moved, not silently kept. */
    @Test void theOldHistoryTableIsReportedAsMoved() {
        var old = parse("[palette.scopes.history]\nenabled=false\ntrivial_commands=['ls']\n");
        assertThat(old.rejected()).isFalse();
        assertThat(old.diagnostics()).singleElement().satisfies(d -> {
            assertThat(d.key()).isEqualTo("palette.scopes.history");
            assertThat(d.message()).contains("dev.jasper.history");
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
        });
        assertThat(parse("[history]\nenabled=false\n").diagnostics()).as("the older top-level table stays unknown").isNotEmpty();
    }

    @Test void longCommandSecondsParsesItsRangeAndDefaultsToTen() {
        assertThat(parse("").snapshot().longCommandSeconds()).isEqualTo(10);
        assertThat(parse("[notifications]\nlong_command_seconds=0\n").snapshot().longCommandSeconds()).isZero();
        assertThat(parse("[notifications]\nlong_command_seconds=3600\n").snapshot().longCommandSeconds()).isEqualTo(3600);
        var high = parse("[notifications]\nlong_command_seconds=3601\n");
        assertThat(high.snapshot().longCommandSeconds()).as("out of range keeps the default").isEqualTo(10);
        assertThat(high.diagnostics()).isNotEmpty();
    }

        @Test void aPluginTableIsKeptForSeedingAndReportedAsMoved() {
            var result = parse("[plugins.\"dev.example.tool\"]\ngreeting = \"hi\"\n");
            assertThat(result.rejected()).isFalse();
            assertThat(result.snapshot().plugins()).containsEntry("dev.example.tool", Map.of("greeting", "hi"));
            assertThat(result.diagnostics()).singleElement().satisfies(d -> {
                assertThat(d.key()).isEqualTo("plugins.\"dev.example.tool\"");
                assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
                assertThat(d.message()).contains("plugins/dev.example.tool/dev.example.tool.toml");
            });
        }
    /** Review Focus 2: an older install's theme keys start Slate Cobalt and are reported, never applied or migrated. */
    @Test void theRemovedThemeKeysAreUnknownSettings() {
        var result = parse("[ui.theme]\nname = \"Nord\"\nfollow_system = true\nworkspace = \"Nord\"\n[terminal]\ncolors = \"light\"\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
            .containsExactlyInAnyOrder("ui.theme.name", "ui.theme.follow_system", "ui.theme.workspace", "terminal.colors");
        assertThat(result.diagnostics()).allSatisfy(d -> {
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).isEqualTo("Unknown setting or table; ignored.");
        });
    }

    @Test void appearanceAndTerminalSchemeReadTheirChoicesAndDefault() {
        var chosen = parse("[ui]\nappearance = \"light\"\n[terminal]\nscheme = \"slate-cobalt/high-contrast\"\n");
        assertThat(chosen.diagnostics()).isEmpty();
        assertThat(chosen.snapshot().appearance()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(chosen.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
        var defaults = parse("");
        assertThat(defaults.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
        assertThat(defaults.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES)
            assertThat(parse("[terminal]\nscheme = \"" + scheme.key() + "\"\n").snapshot().terminalScheme()).isEqualTo(scheme);
    }

    @Test void theThemePicksReadAnyThemeIdAndRejectOthers() {
        var chosen = parse("[ui.theme]\ndark = \"ocean\"\nlight = \"ocean-dawn\"\n");
        assertThat(chosen.diagnostics()).isEmpty();
        assertThat(chosen.snapshot().darkTheme()).isEqualTo("ocean");
        assertThat(chosen.snapshot().lightTheme()).isEqualTo("ocean-dawn");
        var bad = parse("[ui.theme]\ndark = \"Ocean Theme\"\n");
        assertThat(bad.snapshot().darkTheme()).isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
        assertDiagnostic(bad, "ui.theme.dark", 2, 1, ConfigDiagnostic.Severity.ERROR);
        assertThat(bad.diagnostics().getFirst().message()).isEqualTo("Use a theme id such as \"slate-cobalt\"; using the default.");
    }

    @Test void anOldSchemeValueIsUnknownNow() {
        var result = parse("[terminal]\nscheme = \"high-contrast\"\n");
        assertThat(result.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        assertThat(result.diagnostics().getFirst().message())
            .isEqualTo("Use \"match\" or a scheme id such as \"slate-cobalt/high-contrast\"; using the default.");
    }

    @Test void aMalformedSavedPatternFallsBackToTheDefault() {
        var good = parse("[terminal]\ntab_name = '{full} | {process}'");
        assertThat(good.diagnostics()).isEmpty();
        assertThat(good.snapshot().terminal().tabName()).isEqualTo("{full} | {process}");
        var bad = parse("[terminal]\ntab_name = '{dir'");
        assertThat(bad.diagnostics()).hasSize(1);
        assertThat(bad.snapshot().terminal().tabName()).isEqualTo("{dir} {process} {title}");
        assertThat(parse("[terminal]\ntab_name = '{dir} {host}'").diagnostics()).as("an unknown piece is not an error").isEmpty();
    }

    @Test void anUnknownAppearanceOrSchemeUsesTheDefaultAndSaysSo() {
        var result = parse("[ui]\nappearance = \"sepia\"\n[terminal]\nscheme = \"nord\"\n");
        assertThat(result.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
        assertThat(result.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        assertDiagnostic(result, "ui.appearance", 2, 1, ConfigDiagnostic.Severity.ERROR);
        assertDiagnostic(result, "terminal.scheme", 4, 1, ConfigDiagnostic.Severity.ERROR);
    }
}
