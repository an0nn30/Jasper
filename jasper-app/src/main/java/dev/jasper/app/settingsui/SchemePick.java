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
 * saved scheme that is not installed is shown as such until another is chosen. EDT only.
 */
final class SchemePick implements ValueEditor<String> {
    private final String label;
    private final Supplier<List<InstalledThemes.SchemeOption>> schemes;
    private List<InstalledThemes.SchemeOption> offered = List.of();
    private String value = "match";

    SchemePick(String label, Supplier<List<InstalledThemes.SchemeOption>> schemes) {
        this.label = label;
        this.schemes = schemes;
        refresh();
    }

    /** The entries' labels, in order. */
    List<String> labels() { return options().stream().map(Option::label).toList(); }

    private List<Option> options() {
        var options = new ArrayList<Option>();
        options.add(new Option("match", Editors.SAME_AS_UI_THEME, Optional.empty()));
        for (var scheme : offered) options.add(new Option(scheme.key(), scheme.label(), Optional.empty()));
        if (options.stream().noneMatch(option -> option.id().equals(value)))
            options.add(new Option(value, value + " (not installed)", Optional.empty()));
        return options;
    }

    @Override public Nodes.Node node(String id, ControlState state) {
        return new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options(), value, label, "");
    }
    @Override public String value() { return value; }
    @Override public void show(String value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Selected picked) || !picked.id().equals(id)) return false;
        String next = picked.ids().stream().findFirst().orElse("");
        if (next.isEmpty() || next.equals(value) || options().stream().noneMatch(option -> option.id().equals(next))) return false;
        value = next;
        return true;
    }
    @Override public void refresh() { offered = List.copyOf(schemes.get()); }
}
