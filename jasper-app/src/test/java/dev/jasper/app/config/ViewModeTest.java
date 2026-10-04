package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewModeTest {
    private static ConfigLoader.Result parse(String text) {
        return ConfigLoader.parse(Path.of("config.toml"), text, true);
    }

    @Test void absentViewModeDefaultsToNormal() {
        var result = parse("");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().viewMode()).isEqualTo(ViewMode.NORMAL);
    }

    @Test void acceptedViewModesParseWithoutDiagnostics() {
        var normal = parse("window.view_mode = 'normal'");
        var compact = parse("window.view_mode = 'compact'");
        assertThat(normal.rejected()).isFalse();
        assertThat(normal.diagnostics()).isEmpty();
        assertThat(normal.snapshot().viewMode()).isEqualTo(ViewMode.NORMAL);
        assertThat(compact.rejected()).isFalse();
        assertThat(compact.diagnostics()).isEmpty();
        assertThat(compact.snapshot().viewMode()).isEqualTo(ViewMode.COMPACT);
    }

    @Test void anInvalidTokenDiagnosesTheKeyAndDefaultsOnlyThatSetting() {
        var result = parse("[window]\nview_mode = 'unknown'\ntoolbar_height = 40\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().viewMode()).isEqualTo(ViewMode.NORMAL);
        assertThat(result.snapshot().toolbarHeight()).isEqualTo(40);
        assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("window.view_mode");
            assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(problem.line()).isEqualTo(2);
        });
    }

    @Test void aWrongTypeRejectsTheCandidateFollowingParserPolicy() {
        var result = parse("[window]\nview_mode = true\ntoolbar_height = 40\n");
        assertThat(result.rejected()).isTrue();
        assertThat(result.snapshot().viewMode()).isEqualTo(ViewMode.NORMAL);
        assertThat(result.snapshot().toolbarHeight()).isEqualTo(40);
        assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("window.view_mode");
            assertThat(problem.severity()).isEqualTo(ConfigDiagnostic.Severity.ERROR);
            assertThat(problem.line()).isEqualTo(2);
        });
    }

    @Test void changingToolbarHeightRetainsTheSavedCompactModeAndToolbarPreference() {
        var compact = parse("window.view_mode = 'compact'\nwindow.toolbar = 'icons'").snapshot();
        var changed = compact.toBuilder().toolbarHeight(40).build();
        assertThat(changed.viewMode()).isEqualTo(ViewMode.COMPACT);
        assertThat(changed.toolbar()).isEqualTo(ToolbarMode.ICONS);
        assertThat(changed.toolbarHeight()).isEqualTo(40);
        assertThat(changed.toBuilder().toolbarHeight(compact.toolbarHeight()).build()).isEqualTo(compact);
    }

    @Test void theBuilderSetsTheModeAndRejectsNull() {
        assertThat(ConfigSnapshot.builder().viewMode(ViewMode.COMPACT).build().viewMode()).isEqualTo(ViewMode.COMPACT);
        assertThatThrownBy(() -> ConfigSnapshot.builder().viewMode(null).build())
            .isInstanceOf(NullPointerException.class).hasMessage("viewMode");
    }

    @Test void everyPriorConstructorDefaultsToNormal() {
        var d = ConfigSnapshot.defaults();
        var snapshots = List.of(
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(),
                d.backgroundEnabled(), d.plugins(), d.uiFont(), d.terminalColors(), d.railWidth(), d.toolbarHeight(),
                d.statusBarHeight(), d.nativeDecorations(), d.workspaceTheme()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(),
                d.backgroundEnabled(), d.plugins(), d.uiFont(), d.terminalColors(), d.railWidth(), d.toolbarHeight(),
                d.statusBarHeight(), d.nativeDecorations()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(),
                d.backgroundEnabled(), d.plugins(), d.uiFont(), d.terminalColors()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(),
                d.backgroundEnabled(), d.plugins(), d.uiFont()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(),
                d.backgroundEnabled(), d.plugins()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows(), d.longCommandSeconds(), d.backgroundEnabled()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled(), d.visibleRows()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal(), d.buddyEnabled()),
            new ConfigSnapshot(d.tabHeight(), d.toolbar(), d.statusBar(), d.font(), d.theme(), d.keybindings(),
                d.columns(), d.lines(), d.terminal()));
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.viewMode()).isEqualTo(ViewMode.NORMAL));
    }
}
