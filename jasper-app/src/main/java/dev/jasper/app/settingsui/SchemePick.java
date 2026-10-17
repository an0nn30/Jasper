package dev.jasper.app.settingsui;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code terminal.scheme}'s dropdown: "Same as UI theme", then every installed scheme, read again on each refresh. A
 * saved scheme that is not installed is shown as such until another is chosen. When the application supplies
 * {@link ThemeImports}, a gear beside it imports themes and removes the theme of the scheme it shows ({@link ThemeMenu}).
 * EDT only.
 */
final class SchemePick implements ValueEditor<String> {
    private final String label;
    private final Supplier<InstalledThemes> themes;
    private final Optional<ThemeMenu> menu;
    private List<InstalledThemes.SchemeOption> offered = List.of();
    private String value = "match";
    private Runnable render = () -> { };

    SchemePick(String label, Supplier<InstalledThemes> themes, Optional<ThemeImports> imports, PageContext context, String icon) {
        this.label = label;
        this.themes = themes;
        menu = imports.map(hooks -> new ThemeMenu(hooks, themes, context, ThemeMenu.Target.TERMINAL, () -> render.run(), icon));
        refresh();
    }

    private List<Option> options() {
        var options = new ArrayList<Option>();
        options.add(new Option("match", Editors.SAME_AS_UI_THEME, Optional.empty()));
        for (var scheme : offered) options.add(new Option(scheme.key(), scheme.label(), Optional.empty()));
        if (options.stream().noneMatch(option -> option.id().equals(value)))
            options.add(new Option(value, value + " (not installed)", Optional.empty()));
        return options;
    }

    @Override public Nodes.Node node(String id, ControlState state) {
        var pick = new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options(), value, label, "");
        return menu.<Nodes.Node>map(gear -> new Nodes.Stack(id + ":pick", ControlState.READY, Nodes.LayoutRole.INLINE,
            List.of(pick, gear.gear(id + ":gear", value)))).orElse(pick);
    }
    @Override public String value() { return value; }
    @Override public void show(String value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        // A gear's items act at once; they are never an edit of the pick.
        if (event instanceof UiEvent.Action) { menu.ifPresent(gear -> gear.event(id + ":gear", event, value)); return false; }
        if (!(event instanceof UiEvent.Selected picked) || !picked.id().equals(id)) return false;
        String next = picked.ids().stream().findFirst().orElse("");
        if (next.isEmpty() || next.equals(value) || options().stream().noneMatch(option -> option.id().equals(next))) return false;
        value = next;
        return true;
    }
    @Override public void refresh() { offered = List.copyOf(themes.get().schemes()); }
    @Override public void onChanged(Runnable render) { this.render = render; }
}
