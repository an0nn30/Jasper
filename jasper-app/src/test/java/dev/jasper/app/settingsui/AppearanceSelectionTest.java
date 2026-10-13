package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** spec 4.1: the tiles write ui.appearance and preview each appearance's pick; the Dark and Light theme rows list the installed themes. */
class AppearanceSelectionTest {
    static final Option.Palette DARK = new Option.Palette(1, 2, 3, 4, 5), LIGHT = new Option.Palette(6, 7, 8, 9, 10),
        OCEAN = new Option.Palette(11, 12, 13, 14, 15);
    /** Slate Steel, and Ocean with a Dark variant only. */
    static final InstalledThemes THEMES = new InstalledThemes(
        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", DARK), new InstalledThemes.ThemeOption("ocean", "Ocean", OCEAN)),
        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", LIGHT)),
        InstalledThemes.slateSteel(DARK, LIGHT).schemes());

    static SettingsDraft draft(String config) { return new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values()); }
    static AppearanceSelection editor(SettingsDraft draft) {
        return new AppearanceSelection(new AppearanceChoices(100f, false, () -> Optional.of(false),
            ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}), () -> THEMES, Optional.empty()), draft);
    }
    static List<Nodes.Node> parts(AppearanceSelection editor) {
        return ((Nodes.Stack) editor.node("ui.appearance", ControlState.READY)).children();
    }
    static Nodes.Choice pick(AppearanceSelection editor, int row) {
        return (Nodes.Choice) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(row)).control()).children().get(0);
    }

    @Test void theTilesPreviewEachAppearancesPickAndTheRowsListTheInstalledThemes() {
        var editor = editor(draft(""));
        editor.show(AppearanceMode.SYSTEM);
        var tiles = (Nodes.Choice) parts(editor).get(0);
        assertThat(tiles.id()).isEqualTo("ui.appearance:policy");
        assertThat(tiles.options()).extracting(Option::id).containsExactly("dark", "light", "system");
        assertThat(tiles.options()).extracting(Option::label).containsExactly("Dark", "Light", "Match System");
        assertThat(tiles.selectedId()).isEqualTo("system");
        assertThat(tiles.options().get(0).description()).isEqualTo("Slate Steel");
        assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(DARK, Optional.of(LIGHT)));
        assertThat(tiles.options().get(2).description()).isEqualTo("System appearance: Light");
        assertThat(((Nodes.SettingRow) parts(editor).get(1)).title()).isEqualTo("Dark theme");
        assertThat(((Nodes.SettingRow) parts(editor).get(2)).title()).isEqualTo("Light theme");
        assertThat(pick(editor, 1).kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(pick(editor, 1).options()).extracting(Option::label).containsExactly("Slate Steel", "Ocean");
        assertThat(pick(editor, 2).options()).extracting(Option::label).containsExactly("Slate Steel");
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).as("Reset hidden at the defaults").isFalse();
        assertThat(reset.action()).isEqualTo("ui.appearance:reset");
    }

    @Test void pickingADarkThemeStagesItWithTheTilesAndPreviewsIt() {
        var editor = editor(draft(""));
        editor.show(AppearanceMode.SYSTEM);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:dark-theme", java.util.Set.of("ocean")))).isTrue();
        assertThat(editor.companionValues()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.of("ocean"))
            .containsEntry(CoreSettings.UI_THEME_LIGHT, Optional.empty());
        var tiles = (Nodes.Choice) parts(editor).get(0);
        assertThat(tiles.options().get(0).preview()).contains(new Option.Preview(OCEAN, Optional.empty()));
        assertThat(tiles.options().get(0).description()).isEqualTo("Ocean");
        assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(OCEAN, Optional.of(LIGHT)));
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:light-theme", java.util.Set.of("ocean"))))
            .as("Ocean has no Light variant").isFalse();
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).isTrue();
        assertThat(editor.relatedSettings()).containsExactly(CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT);
    }

    @Test void aSavedPickIsKeptAsSavedAndOneThatIsNotInstalledIsShownAsSuch() {
        var editor = editor(draft("[ui.theme]\ndark = \"ocean\"\nlight = \"dawn\"\n"));
        assertThat(editor.companionValues()).as("untouched picks stage what the file holds")
            .containsEntry(CoreSettings.UI_THEME_DARK, Optional.of("ocean")).containsEntry(CoreSettings.UI_THEME_LIGHT, Optional.of("dawn"));
        assertThat(pick(editor, 2).options()).extracting(Option::label).containsExactly("Slate Steel", "dawn (not installed)");
        assertThat(pick(editor, 2).selectedId()).isEqualTo("dawn");
        var tiles = (Nodes.Choice) parts(editor).get(0);
        assertThat(tiles.options().get(1).preview()).as("Slate Steel is shown instead").contains(new Option.Preview(LIGHT, Optional.empty()));
        assertThat(tiles.options().get(1).description()).isEqualTo("dawn (not installed)");
    }

    @Test void choosingATileEditsTheValueAndResetShows() {
        var editor = new AppearanceSelection(AppearanceChoices.of(DARK, LIGHT), draft(""));
        editor.show(AppearanceMode.SYSTEM);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).isTrue();
        assertThat(editor.value()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).as("the same tile again").isFalse();
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).isTrue();
        assertThat(((Nodes.Choice) parts(editor).get(0)).options().get(2).description()).isEqualTo("System appearance unavailable; using Dark");
    }

    @Test void theAppearancePageShowsTheTilesThenTheTerminalSchemeRow() {
        var system = new CorePages.SystemActions(() -> java.nio.file.Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true);
        var specs = CorePages.specs(AppearanceChoices.of(DARK, LIGHT), system, ShellChoices.NONE, ignored -> { }, false);
        var appearance = specs.getFirst();
        assertThat(appearance.settings()).startsWith(CoreSettings.UI_APPEARANCE, CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT,
            CoreSettings.TERMINAL_SCHEME);
    }
}
