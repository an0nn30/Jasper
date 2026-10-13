package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_APPEARANCE;
import static dev.jasper.app.config.CoreSettings.UI_THEME_DARK;
import static dev.jasper.app.config.CoreSettings.UI_THEME_LIGHT;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings › Appearance's theme card: the Dark / Light / Match System tiles for {@code ui.appearance}, each previewing
 * the theme picked for it, then the Dark theme and Light theme rows ({@code ui.theme.dark}, {@code ui.theme.light}),
 * which list the installed themes with that variant. The picks are staged with the tiles; Reset returns all three to
 * their defaults. EDT only.
 */
final class AppearanceSelection implements ValueEditor<AppearanceMode> {
    private final AppearanceChoices choices;
    private final SettingsDraft draft;
    private final Subscription listening;
    private InstalledThemes themes;
    private AppearanceMode value = UI_APPEARANCE.defaultValue();
    private String dark = UI_THEME_DARK.defaultValue(), light = UI_THEME_LIGHT.defaultValue();
    private Runnable render = () -> { };

    AppearanceSelection(AppearanceChoices choices, SettingsDraft draft) {
        this.choices = choices;
        this.draft = draft;
        themes = choices.themes().get();
        listening = choices.onChanged().apply(() -> render.run());
        showRelated();
    }

    @Override public boolean cardRows() { return true; }
    @Override public boolean fullSpan() { return true; }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        Optional<Boolean> system = choices.systemDark().get();
        var darkPreview = preview(themes.dark(), dark);
        var lightPreview = preview(themes.light(), light);
        var tiles = new Nodes.Choice(id + ":policy", plain, Nodes.ChoiceKind.PREVIEWS, List.of(
            tile("dark", "Dark", label(themes.dark(), dark), new Option.Preview(darkPreview, Optional.empty())),
            tile("light", "Light", label(themes.light(), light), new Option.Preview(lightPreview, Optional.empty())),
            tile("system", "Match System", system.map(isDark -> "System appearance: " + (isDark ? "Dark" : "Light"))
                .orElse("System appearance unavailable; using Dark"), new Option.Preview(darkPreview, Optional.of(lightPreview)))),
            token(value), "Appearance", "");
        boolean modified = value != UI_APPEARANCE.defaultValue() || !dark.equals(UI_THEME_DARK.defaultValue())
            || !light.equals(UI_THEME_LIGHT.defaultValue());
        var reset = new Nodes.Button(id + ":reset", new ControlState(modified, true, false, ""), "",
            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " and its themes to their defaults");
        var darkPick = new Nodes.Choice(id + ":dark-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(themes.dark(), dark), dark,
            UI_THEME_DARK.label(), "");
        var lightPick = new Nodes.Choice(id + ":light-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(themes.light(), light), light,
            UI_THEME_LIGHT.label(), "");
        var darkControls = new Nodes.Stack(id + ":dark-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(darkPick, reset));
        var lightControls = new Nodes.Stack(id + ":light-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(lightPick));
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL, List.of(tiles,
            new Nodes.SettingRow("row:" + id, plain, UI_THEME_DARK.label(), "", darkControls, false, highlighted),
            new Nodes.SettingRow("row:" + id + ":light", plain, UI_THEME_LIGHT.label(), "", lightControls, false, highlighted)));
    }

    private static Option tile(String id, String label, String caption, Option.Preview preview) {
        return new Option(id, label, Optional.empty(), "", caption, Optional.of(preview));
    }

    /** The installed themes, plus the picked one marked when it is not installed. */
    private static List<Option> options(List<InstalledThemes.ThemeOption> installed, String picked) {
        var options = new ArrayList<Option>();
        for (var theme : installed) options.add(new Option(theme.id(), theme.label(), Optional.empty()));
        if (installed.stream().noneMatch(theme -> theme.id().equals(picked)))
            options.add(new Option(picked, picked + " (not installed)", Optional.empty()));
        return options;
    }

    /** The picked theme's preview; one that is not installed previews the first, Slate Steel, which is shown instead. */
    private static Option.Palette preview(List<InstalledThemes.ThemeOption> installed, String picked) {
        return installed.stream().filter(theme -> theme.id().equals(picked)).findFirst().orElse(installed.getFirst()).preview();
    }

    private static String label(List<InstalledThemes.ThemeOption> installed, String picked) {
        return installed.stream().filter(theme -> theme.id().equals(picked)).findFirst().map(InstalledThemes.ThemeOption::label)
            .orElse(picked + " (not installed)");
    }

    private static String token(AppearanceMode mode) {
        return switch (mode) { case DARK -> "dark"; case LIGHT -> "light"; case SYSTEM -> "system"; };
    }

    @Override public AppearanceMode value() { return value; }
    @Override public void show(AppearanceMode value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Selected selected)) return false;
        String picked = selected.ids().stream().findFirst().orElse("");
        if (selected.id().equals(id + ":dark-theme")) {
            if (picked.isEmpty() || picked.equals(dark) || themes.dark().stream().noneMatch(theme -> theme.id().equals(picked))) return false;
            dark = picked;
            return true;
        }
        if (selected.id().equals(id + ":light-theme")) {
            if (picked.isEmpty() || picked.equals(light) || themes.light().stream().noneMatch(theme -> theme.id().equals(picked))) return false;
            light = picked;
            return true;
        }
        if (!selected.id().equals(id + ":policy")) return false;
        AppearanceMode next = switch (picked) {
            case "dark" -> AppearanceMode.DARK;
            case "light" -> AppearanceMode.LIGHT;
            case "system" -> AppearanceMode.SYSTEM;
            default -> null;
        };
        if (next == null || next == value) return false;
        value = next;
        return true;
    }

    @Override public Map<Setting<?>, Optional<?>> companionValues() {
        return Map.of(UI_THEME_DARK, staged(UI_THEME_DARK, dark), UI_THEME_LIGHT, staged(UI_THEME_LIGHT, light));
    }

    /** The saved value when the pick is what the file already means, so an untouched pick never becomes an edit. */
    private Optional<String> staged(Setting<String> setting, String pick) {
        var saved = draft.saved(setting);
        if (saved.map(pick::equals).orElse(pick.equals(setting.defaultValue()))) return saved;
        return pick.equals(setting.defaultValue()) ? Optional.empty() : Optional.of(pick);
    }

    @Override public void showRelated() { dark = draft.value(UI_THEME_DARK); light = draft.value(UI_THEME_LIGHT); }
    @Override public List<Setting<?>> relatedSettings() { return List.of(UI_THEME_DARK, UI_THEME_LIGHT); }
    @Override public void refresh() { themes = choices.themes().get(); }
    @Override public void onChanged(Runnable render) { this.render = render; }
    @Override public void dispose() { listening.close(); }
}
