package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.UiEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tab names spec 5: the Tab name field, its live preview, unknown pieces and the piece list. */
class TabNameEditorTest {
    private static final String ID = CoreSettings.TERMINAL_TAB_NAME.name();
    private static final TabNameChoices CLAUDE = new TabNameChoices(() -> Optional.of(new TabNameChoices.Values(
        Map.of("dir", "moray", "full", "~/projects/moray", "process", "claude", "title", "Fix tab names"), "zsh")));

    private static ValueEditor<String> editor(TabNameChoices choices, String value) {
        var editor = TabNameEditor.factory(choices).build().apply(CoreSettings.TERMINAL_TAB_NAME, null);
        editor.show(value);
        return editor;
    }

    private static List<String> texts(ValueEditor<String> editor) {
        var found = new ArrayList<String>();
        collect(editor.node(ID, ControlState.READY), found);
        return found;
    }

    private static void collect(Nodes.Node node, List<String> found) {
        if (node instanceof Nodes.Text text) found.add(text.text());
        if (node instanceof Nodes.Stack stack) for (var child : stack.children()) collect(child, found);
    }

    @Test void thePreviewFillsThePatternFromTheTabAndFollowsEdits() {
        var editor = editor(CLAUDE, "{dir} | {process} | {title}");
        assertThat(texts(editor)).contains("Preview: moray | claude | Fix tab names");
        assertThat(editor.event(ID, new UiEvent.Edited(ID, "{full} {process}"))).isTrue();
        assertThat(editor.value()).isEqualTo("{full} {process}");
        assertThat(texts(editor)).contains("Preview: ~/projects/moray claude");
    }

    @Test void anEmptyResultPreviewsTheShellsName() {
        var tab = new TabNameChoices(() -> Optional.of(new TabNameChoices.Values(Map.of("dir", "moray", "title", ""), "zsh")));
        assertThat(texts(editor(tab, "{title}"))).contains("Preview: zsh");
    }

    @Test void withNoTerminalTabThePreviewUsesSampleValues() {
        String shell = TabNameEditor.SAMPLE.fallback();
        assertThat(texts(editor(TabNameChoices.NONE, "{dir} {process} {title}"))).contains("Preview: projects " + shell);
    }

    @Test void anUnknownPieceIsNotedButTheValueStaysValid() {
        assertThat(texts(editor(CLAUDE, "{dir} {branch}")))
            .contains("Preview: moray", "Nothing provides {branch} right now, so it shows nothing");
        assertThat(CoreSettings.TERMINAL_TAB_NAME.kind().check("{dir} {branch}")).isEmpty();
    }

    @Test void aMalformedPatternShowsNoPreview() {
        assertThat(texts(editor(CLAUDE, "{dir"))).noneMatch(text -> text.startsWith("Preview:"));
    }

    @Test void thePieceListNamesEveryBuiltIn() {
        assertThat(String.join("\n", texts(editor(CLAUDE, "{dir}")))).contains("{dir}", "{full}", "{process}", "{title}");
    }
}
