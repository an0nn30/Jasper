package dev.jasper.app.settingsui;

import dev.jasper.app.config.TabNames;
import dev.jasper.app.config.TitleTemplate;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Settings › Terminal › Tab name (tab names spec 5): the pattern's field, then a live preview of the name it gives the
 * active Terminal tab, a note for each piece nothing provides, and the pieces there are.
 */
final class TabNameEditor implements ValueEditor<String> {
    /** What the preview names while no Terminal tab is open. */
    static final TabNameChoices.Preview SAMPLE = sample(System.getProperty("os.name", ""));
    private static final Map<String, String> BUILT_IN = Map.of("dir", "the current folder's name", "full", "its full path",
        "process", "the running program", "title", "the title the shell or a program set");
    private final String label;
    private final TabNameChoices choices;
    private String text = "";

    private TabNameEditor(String label, TabNameChoices choices) { this.label = label; this.choices = choices; }

    static Editors.Factory<String> factory(TabNameChoices choices) {
        return new Editors.Factory<>((setting, context) -> new TabNameEditor(setting.label(), choices),
            List.of("Preview", "{dir}", "{full}", "{process}", "{title}"));
    }

    private static TabNameChoices.Preview sample(String os) {
        String shell = os.startsWith("Mac") ? "zsh" : os.startsWith("Windows") ? "powershell" : "bash";
        return new TabNameChoices.Preview(Map.of("dir", "projects", "full", "~/projects", "process", shell), shell);
    }

    @Override public Nodes.Node node(String id, ControlState state) {
        var children = new ArrayList<Nodes.Node>();
        children.add(new Nodes.Field(id, state, Nodes.FieldKind.TEXT, text, "", label, "", Optional.empty(),
            Nodes.FieldPresentation.TECHNICAL));
        if (TabNames.valid(text)) {
            TitleTemplate pattern = TitleTemplate.parse(text);
            var tab = choices.preview().get().orElse(SAMPLE);
            children.add(new Nodes.Text(id + ":preview", ControlState.READY,
                "Preview: " + TabNames.format(pattern, tab.pieces(), tab.fallback()), Nodes.TextRole.SUPPORTING));
            for (String name : TabNames.unknown(pattern, Set.of()))
                children.add(new Nodes.Text(id + ":unknown-" + name, ControlState.READY,
                    "Nothing provides {" + name + "} right now, so it shows nothing", Nodes.TextRole.SUPPORTING,
                    Optional.of(Nodes.Severity.WARNING), OptionalInt.empty()));
        }
        children.add(new Nodes.Text(id + ":pieces", ControlState.READY, pieces(), Nodes.TextRole.SUPPORTING));
        return new Nodes.Stack(id + ":tab-name", ControlState.READY, Nodes.LayoutRole.VERTICAL, children);
    }

    /** One line per piece: {@code {name}} and what it shows. */
    private static String pieces() {
        var lines = new ArrayList<String>();
        for (String name : TabNames.BUILT_IN) lines.add("{" + name + "} " + BUILT_IN.get(name));
        return String.join("\n", lines);
    }

    @Override public String value() { return text; }
    @Override public void show(String value) { text = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Edited edited) || !edited.id().equals(id)) return false;
        text = edited.text();
        return true;
    }
}
