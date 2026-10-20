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
 * which list the installed themes with that variant; a pick that lacks it shows as {@code Dawn (no Dark variant)}, and one
 * that nothing installed has as {@code dawn (not installed)}. The picks are staged with the tiles; Reset returns all three
 * to their defaults. When the application supplies {@link ThemeImports}, a gear beside each dropdown imports and removes
 * themes ({@link ThemeMenu}). EDT only.
 */
final class AppearanceSelection implements ValueEditor<AppearanceMode> {
    private final AppearanceChoices choices;
    private final SettingsDraft draft;
    private final PageContext context;
    private final Subscription listening;
    private final Optional<ThemeMenu> darkMenu, lightMenu;
    private InstalledThemes themes;
    private AppearanceMode value = UI_APPEARANCE.defaultValue();
    private String dark = UI_THEME_DARK.defaultValue(), light = UI_THEME_LIGHT.defaultValue();
    private Runnable render = () -> { };

    AppearanceSelection(AppearanceChoices choices, PageContext context) {
        this.choices = choices;
        this.draft = context.draft();
        this.context = context;
        // Spec 12.2: the classic composition's theme menus are a ⋯ beside each dropdown, with the gear's items.
        String icon = Editors.menuIcon(context);
        darkMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.DARK, () -> render.run(), icon));
        lightMenu = choices.imports().map(hooks -> new ThemeMenu(hooks, choices.themes(), context, ThemeMenu.Target.LIGHT, () -> render.run(), icon));
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
        var darkPreview = preview(themes.dark(), dark, UI_THEME_DARK.defaultValue());
        var lightPreview = preview(themes.light(), light, UI_THEME_LIGHT.defaultValue());
        var tiles = new Nodes.Choice(id + ":policy", plain, Nodes.ChoiceKind.PREVIEWS, List.of(
            tile("dark", "Dark", label(true, dark), new Option.Preview(darkPreview, Optional.empty())),
            tile("light", "Light", label(false, light), new Option.Preview(lightPreview, Optional.empty())),
            tile("system", "Match System", system.map(isDark -> "System appearance: " + (isDark ? "Dark" : "Light"))
                .orElse("System appearance unavailable; using Dark"), new Option.Preview(darkPreview, Optional.of(lightPreview)))),
            token(value), "Appearance", "");
        boolean modified = value != draft.defaultValue(UI_APPEARANCE) || !dark.equals(draft.defaultValue(UI_THEME_DARK))
            || !light.equals(draft.defaultValue(UI_THEME_LIGHT));
        var reset = new Nodes.Button(id + ":reset", new ControlState(modified, true, false, ""), "",
            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " and its themes to their defaults");
        var darkPick = new Nodes.Choice(id + ":dark-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(true, dark), dark,
            UI_THEME_DARK.label(), "");
        var lightPick = new Nodes.Choice(id + ":light-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(false, light), light,
            UI_THEME_LIGHT.label(), "");
        // The Light row holds a hidden copy of Reset, so its dropdown and gear line up with the Dark row's and the Terminal scheme row's.
        var lane = new Nodes.Button(id + ":light-reset", new ControlState(false, true, false, ""), "", "reset", Nodes.ButtonKind.GHOST,
            id + ":reset", reset.tooltip());
        boolean inline = context.chrome().inlineReset();
        var darkControls = new Nodes.Stack(id + ":dark-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            inline ? List.of(withGear(id + ":dark", plain, darkPick, darkMenu, dark), reset) : List.of(withGear(id + ":dark", plain, darkPick, darkMenu, dark)));
        var lightControls = new Nodes.Stack(id + ":light-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING,
            inline ? List.of(withGear(id + ":light", plain, lightPick, lightMenu, light), lane) : List.of(withGear(id + ":light", plain, lightPick, lightMenu, light)));
        // Spec 12.1: without an inline Reset, both theme rows offer Reset to Default for the policy and its two themes.
        var menu = SchemaPage.resetMenu(context, id + ":reset", modified);
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL, List.of(tiles,
            new Nodes.SettingRow("row:" + id, plain, UI_THEME_DARK.label(), "", darkControls, false, highlighted, "", menu),
            new Nodes.SettingRow("row:" + id + ":light", plain, UI_THEME_LIGHT.label(), "", lightControls, false, highlighted, "", menu)));
    }

    /** {@code pick}, followed by its gear {@code id + "-gear"} when there is one. */
    private static Nodes.Node withGear(String id, ControlState state, Nodes.Choice pick, Optional<ThemeMenu> menu, String shown) {
        return menu.<Nodes.Node>map(gear -> new Nodes.Stack(id + "-pick", state, Nodes.LayoutRole.INLINE,
            List.of(pick, gear.gear(id + "-gear", shown)))).orElse(pick);
    }

    private static Option tile(String id, String label, String caption, Option.Preview preview) {
        return new Option(id, label, Optional.empty(), "", caption, Optional.of(preview));
    }

    /** The installed themes with the variant, plus the picked one marked when it is not among them. */
    private List<Option> options(boolean dark, String picked) {
        var installed = dark ? themes.dark() : themes.light();
        var options = new ArrayList<Option>();
        for (var theme : installed) options.add(new Option(theme.id(), theme.label(), Optional.empty(), "",
            theme.provider().isEmpty() ? "" : "From the " + theme.provider() + " plugin", Optional.empty()));
        if (installed.stream().noneMatch(theme -> theme.id().equals(picked))) options.add(new Option(picked, unavailable(dark, picked), Optional.empty()));
        return options;
    }

    /** The picked theme's preview, falling back to the same appearance default as the application. */
    private static Option.Palette preview(List<InstalledThemes.ThemeOption> installed, String picked, String defaultId) {
        return installed.stream().filter(theme -> theme.id().equals(picked)).findFirst()
            .orElseGet(() -> installed.stream().filter(theme -> theme.id().equals(defaultId)).findFirst()
                .orElse(installed.getFirst())).preview();
    }

    private String label(boolean dark, String picked) {
        return (dark ? themes.dark() : themes.light()).stream().filter(theme -> theme.id().equals(picked)).findFirst()
            .map(InstalledThemes.ThemeOption::label).orElseGet(() -> unavailable(dark, picked));
    }

    /** What a pick with no entry in the Dark or Light list is called: the installed theme that lacks the variant, or one that is not installed. */
    private String unavailable(boolean dark, String picked) {
        String name = themes.names().get(picked);
        return name == null ? picked + " (not installed)" : name + " (no " + (dark ? "Dark" : "Light") + " variant)";
    }

    private static String token(AppearanceMode mode) {
        return switch (mode) { case DARK -> "dark"; case LIGHT -> "light"; case SYSTEM -> "system"; };
    }

    @Override public AppearanceMode value() { return value; }
    @Override public void show(AppearanceMode value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        // A gear's items act at once; they are never an edit of the picks.
        if (event instanceof UiEvent.Action) {
            if (!darkMenu.map(menu -> menu.event(id + ":dark-gear", event, dark)).orElse(false))
                lightMenu.ifPresent(menu -> menu.event(id + ":light-gear", event, light));
            return false;
        }
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
        String effective = draft.defaultValue(setting);
        if (saved.map(pick::equals).orElse(pick.equals(effective))) return saved;
        // Picking the core default while the look overrides it is a choice of its own, so it is saved.
        return pick.equals(effective) ? Optional.empty() : Optional.of(pick);
    }

    @Override public void showRelated() { dark = draft.value(UI_THEME_DARK); light = draft.value(UI_THEME_LIGHT); }
    @Override public List<Setting<?>> relatedSettings() { return List.of(UI_THEME_DARK, UI_THEME_LIGHT); }
    @Override public void refresh() { themes = choices.themes().get(); }
    @Override public void onChanged(Runnable render) { this.render = render; }
    @Override public void dispose() { listening.close(); }
}
