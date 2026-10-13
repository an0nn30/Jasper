package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** spec 6: the tiles write ui.appearance; the Theme row's one entry is Slate Steel, with no gear. */
class AppearanceSelectionTest {
    static final Option.Palette DARK = new Option.Palette(1, 2, 3, 4, 5), LIGHT = new Option.Palette(6, 7, 8, 9, 10);

    static List<Nodes.Node> parts(AppearanceSelection editor) {
        return ((Nodes.Stack) editor.node("ui.appearance", ControlState.READY)).children();
    }

    @Test void theTilesPreviewBothVariantsAndTheThemeRowOffersOnlySlateSteel() {
        var editor = new AppearanceSelection(new AppearanceChoices(DARK, LIGHT, 100f, false, () -> Optional.of(false), ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {})));
        editor.show(AppearanceMode.SYSTEM);
        var tiles = (Nodes.Choice) parts(editor).get(0);
        assertThat(tiles.id()).isEqualTo("ui.appearance:policy");
        assertThat(tiles.options()).extracting(Option::id).containsExactly("dark", "light", "system");
        assertThat(tiles.options()).extracting(Option::label).containsExactly("Dark", "Light", "Match System");
        assertThat(tiles.selectedId()).isEqualTo("system");
        assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(DARK, Optional.of(LIGHT)));
        assertThat(tiles.options().get(2).description()).isEqualTo("System appearance: Light");
        var row = (Nodes.SettingRow) parts(editor).get(1);
        assertThat(row.title()).isEqualTo("Theme");
        var controls = (Nodes.Stack) row.control();
        var theme = (Nodes.Choice) controls.children().get(0);
        assertThat(theme.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(theme.options()).extracting(Option::label).containsExactly("Slate Steel");
        assertThat(controls.children()).noneMatch(node -> node instanceof Nodes.MenuButton).as("no gear");
        assertThat(((Nodes.Button) controls.children().get(1)).state().visible()).as("Reset hidden at the default").isFalse();
    }

    @Test void choosingATileEditsTheValueAndResetShows() {
        var editor = new AppearanceSelection(AppearanceChoices.of(DARK, LIGHT));
        editor.show(AppearanceMode.SYSTEM);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).isTrue();
        assertThat(editor.value()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).as("the same tile again").isFalse();
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).isTrue();
        assertThat(reset.action()).isEqualTo("ui.appearance:reset");
        assertThat(((Nodes.Choice) parts(editor).get(0)).options().get(2).description()).isEqualTo("System appearance unavailable; using Dark");
    }

    @Test void theAppearancePageShowsTheTilesThenTheTerminalSchemeRow() {
        var system = new CorePages.SystemActions(() -> java.nio.file.Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true, Optional.empty());
        var specs = CorePages.specs(AppearanceChoices.of(DARK, LIGHT), system, ShellChoices.NONE, ignored -> { }, false);
        var appearance = specs.getFirst();
        assertThat(appearance.settings()).startsWith(CoreSettings.UI_APPEARANCE, CoreSettings.TERMINAL_SCHEME);
    }
}
