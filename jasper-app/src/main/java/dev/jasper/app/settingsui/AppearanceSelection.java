package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_APPEARANCE;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.*;
import java.util.List;
import java.util.Optional;

/**
 * Settings › Appearance's theme card: the Dark / Light / Match System tiles for {@code ui.appearance}, previewing
 * Slate Steel's variants, then the Theme row, whose one entry is Slate Steel (there is nothing else to choose). EDT only.
 */
final class AppearanceSelection implements ValueEditor<AppearanceMode> {
    /** The one theme. */
    static final String THEME = "Slate Steel";
    private final AppearanceChoices choices;
    private final Subscription listening;
    private AppearanceMode value = UI_APPEARANCE.defaultValue();
    private Runnable render = () -> { };

    AppearanceSelection(AppearanceChoices choices) {
        this.choices = choices;
        listening = choices.onChanged().apply(() -> render.run());
    }

    @Override public boolean cardRows() { return true; }
    @Override public boolean fullSpan() { return true; }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        Optional<Boolean> system = choices.systemDark().get();
        var tiles = new Nodes.Choice(id + ":policy", plain, Nodes.ChoiceKind.PREVIEWS, List.of(
            tile("dark", "Dark", THEME + " Dark", new Option.Preview(choices.dark(), Optional.empty())),
            tile("light", "Light", THEME + " Light", new Option.Preview(choices.light(), Optional.empty())),
            tile("system", "Match System", system.map(dark -> "System appearance: " + (dark ? "Dark" : "Light"))
                .orElse("System appearance unavailable; using Dark"), new Option.Preview(choices.dark(), Optional.of(choices.light())))),
            token(value), "Appearance", "");
        var theme = new Nodes.Choice(id + ":theme", plain, Nodes.ChoiceKind.DROPDOWN,
            List.of(new Option("slate-steel", THEME, Optional.empty())), "slate-steel", "Theme", "");
        var reset = new Nodes.Button(id + ":reset", new ControlState(value != UI_APPEARANCE.defaultValue(), true, false, ""), "",
            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " to default");
        var controls = new Nodes.Stack(id + ":controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(theme, reset));
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL,
            List.of(tiles, new Nodes.SettingRow("row:" + id, plain, "Theme", "", controls, false, highlighted)));
    }

    private static Option tile(String id, String label, String caption, Option.Preview preview) {
        return new Option(id, label, Optional.empty(), "", caption, Optional.of(preview));
    }

    private static String token(AppearanceMode mode) {
        return switch (mode) { case DARK -> "dark"; case LIGHT -> "light"; case SYSTEM -> "system"; };
    }

    @Override public AppearanceMode value() { return value; }
    @Override public void show(AppearanceMode value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Selected selected) || !selected.id().equals(id + ":policy")) return false;
        AppearanceMode next = switch (selected.ids().stream().findFirst().orElse("")) {
            case "dark" -> AppearanceMode.DARK;
            case "light" -> AppearanceMode.LIGHT;
            case "system" -> AppearanceMode.SYSTEM;
            default -> null;
        };
        if (next == null || next == value) return false;
        value = next;
        return true;
    }
    @Override public void onChanged(Runnable render) { this.render = render; }
    @Override public void dispose() { listening.close(); }
}
