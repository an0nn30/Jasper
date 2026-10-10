package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("try")
class SearchChoiceRefreshTest {
    @Test void queuedEntryNormalizationUsesLatestRevisionAndStopsAtClose() {
        KitView[] views = {null, null}; KitContext[] contexts = {null}; JTextField[] fields = {null, null};
        var events = new ArrayList<UiEvent>();
        Edt.run(() -> {
            contexts[0] = KitFixtures.context();
            for (int index = 0; index < views.length; index++) {
                int at = index;
                views[at] = new KitView(contexts[0], entry(""), event -> {
                    events.add(event);
                    if (event instanceof UiEvent.Edited edit) views[at].update(entry(edit.text().toUpperCase(Locale.ROOT)));
                });
                fields[at] = ((SearchChoices.Combo)((Choices.ChoiceControl)views[at].control("font")).input).editorField();
                fields[at].replaceSelection("a"); fields[at].replaceSelection("b");
            }
            views[1].close();
        });
        Edt.run(() -> {
            try {
                assertThat(fields[0].getText()).isEqualTo("AB");
                assertThat(fields[1].getText()).isEqualTo("ab");
                assertThat(events).containsExactly(new UiEvent.Edited("font", "a"), new UiEvent.Edited("font", "ab"),
                    new UiEvent.Edited("font", "a"), new UiEvent.Edited("font", "ab"));
            } finally { views[0].close(); views[1].close(); KitViewTest.release(contexts[0]); }
        });
    }
    private static ViewSpec entry(String text) {
        return new ViewSpec(new Nodes.Choice("font", ControlState.READY, Nodes.ChoiceKind.SEARCHABLE,
            List.of(), "", "Font", "", "", Optional.of(new Nodes.ChoiceSearch(0, "None", Optional.of(text)))));
    }
    @Test void actualThemeInstallationPreservesPendingEditorDocumentSelectionAndAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var original = UIManager.getLookAndFeel();
            var events = new ArrayList<UiEvent>();
            try {
                FlatLafInstaller.install(context.theme());
                var node = new Nodes.Choice("font", ControlState.READY, Nodes.ChoiceKind.SEARCHABLE,
                    List.of(new Option("mono", "Monospaced", Optional.empty())), "", "Font", "add", "",
                    Optional.of(new Nodes.ChoiceSearch(0, "None", Optional.of("Monospaced"))));
                try (var view = new KitView(context, new ViewSpec(node), events::add)) {
                    var combo = (SearchChoices.Combo)((Choices.ChoiceControl)view.control("font")).input;
                    var editor = combo.editorField(); var document = editor.getDocument();
                    int keyListeners = editor.getKeyListeners().length;
                    var inputMethods = editor.getInputMethodListeners();
                    editor.setText("Pending font"); editor.getCaret().setDot(9); editor.getCaret().moveDot(2);
                    events.clear();
                    for (var theme : List.of(KitFixtures.light(), KitFixtures.dark(), KitFixtures.light())) {
                        FlatLafInstaller.install(theme); context.binding().publish(new ThemePair(theme, theme));
                        SwingUtilities.updateComponentTreeUI(view);
                        assertThat(combo.editorField()).isSameAs(editor);
                        assertThat(editor.getDocument()).isSameAs(document);
                        assertThat(editor.getKeyListeners()).hasSize(keyListeners);
                        assertThat(editor.getInputMethodListeners()).containsExactly(inputMethods);
                        assertThat(editor.getText()).isEqualTo("Pending font");
                        assertThat(editor.getCaret().getDot()).isEqualTo(2); assertThat(editor.getCaret().getMark()).isEqualTo(9);
                        assertThat(events).isEmpty();
                        assertThat(Arrays.stream(editor.getKeyListeners()).filter(k -> k == combo.searchPopup().keys)).hasSize(1);
                    }
                    var enter = new java.awt.event.KeyEvent(editor, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n');
                    combo.searchPopup().keys.keyPressed(enter);
                    assertThat(events).containsExactly(new UiEvent.Action("add"));
                    events.clear(); view.close(); editor.setText("after close"); assertThat(events).isEmpty();
                }
            } finally {
                KitViewTest.release(context);
                try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); }
            }
        });
    }
}
