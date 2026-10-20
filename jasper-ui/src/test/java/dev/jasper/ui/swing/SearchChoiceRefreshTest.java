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
    @Test void editableChoiceRecoversItsKitDelegateWhenAnotherUiWasInstalled() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>(); var oldLaf = UIManager.getLookAndFeel();
            FlatLafInstaller.install(context.theme(), dev.jasper.ui.kit.Kit.DEFAULT);
            try (var view = new KitView(context, entry("Monospaced"), events::add)) {
                var combo = (SearchChoices.Combo)((Choices.ChoiceControl)view.control("font")).input;
                var editor = combo.editorField(); editor.select(2, 6);
                combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
                assertThat(combo.getUI()).isNotInstanceOf(Choices.ChoiceCombo.KitUi.class);
                SwingUtilities.updateComponentTreeUI(view);
                assertThat(combo.getUI()).isInstanceOf(Choices.ChoiceCombo.KitUi.class);
                assertThat(combo.editorField()).isSameAs(editor); assertThat(editor.getSelectedText()).isEqualTo("nosp");
                assertThat(editor.getText()).isEqualTo("Monospaced"); assertThat(events).isEmpty();
                assertThat(Arrays.stream(editor.getKeyListeners()).filter(k -> k == combo.searchPopup().keys)).hasSize(1);
            } finally {
                KitViewTest.release(context);
                try { UIManager.setLookAndFeel(oldLaf); } catch (Exception failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void editableThemeRefreshNeverDetachesTheEditorOrChangesTheFocusOwner() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var oldLaf = UIManager.getLookAndFeel();
            var oldFocus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var focus = new SettingsKitTest.Focus();
            FlatLafInstaller.install(context.theme(), dev.jasper.ui.kit.Kit.DEFAULT);
            var other = new JTextField("Search settings");
            try (var view = new KitView(context, entry("Monospaced"), event -> {})) {
                var combo = (SearchChoices.Combo)((Choices.ChoiceControl)view.control("font")).input;
                var editor = combo.editorField();
                var detached = new ArrayList<java.awt.Component>();
                combo.addContainerListener(new java.awt.event.ContainerAdapter() {
                    @Override public void componentRemoved(java.awt.event.ContainerEvent event) {
                        if (event.getChild() == editor) detached.add(editor);
                    }
                });
                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
                for (var target : List.of(editor, other)) {
                    focus.focus(target); editor.select(2, 6);
                    for (var theme : List.of(KitFixtures.light(), KitFixtures.dark())) {
                        assertThat(editor.getSelectedText()).as("before install").isEqualTo("nosp");
                        FlatLafInstaller.install(theme, dev.jasper.ui.kit.Kit.DEFAULT);
                        assertThat(editor.getSelectedText()).as("after install").isEqualTo("nosp");
                        context.binding().publish(new ThemePair(theme, theme));
                        assertThat(editor.getSelectedText()).as("after binding publication").isEqualTo("nosp");
                        SwingUtilities.updateComponentTreeUI(view);
                        assertThat(detached).as("detaching the focused editor transfers native focus and disrupts IME").isEmpty();
                        assertThat(focus.getFocusOwner()).isSameAs(target);
                        assertThat(editor.getParent()).isSameAs(combo);
                        assertThat(editor.getSelectedText()).isEqualTo("nosp");
                        assertThat(editor.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PRIMARY));
                        assertThat(combo.searchPopup().getList().getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PRIMARY));
                    }
                }
                focus.focus(other); view.close(); SwingUtilities.updateComponentTreeUI(view);
                assertThat(focus.getFocusOwner()).isSameAs(other); assertThat(detached).isEmpty();
            } finally {
                java.awt.KeyboardFocusManager.setCurrentKeyboardFocusManager(oldFocus); KitViewTest.release(context);
                try { UIManager.setLookAndFeel(oldLaf); } catch (Exception failure) { throw new AssertionError(failure); }
            }
        });
    }

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
                FlatLafInstaller.install(context.theme(), dev.jasper.ui.kit.Kit.DEFAULT);
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
                        FlatLafInstaller.install(theme, dev.jasper.ui.kit.Kit.DEFAULT); context.binding().publish(new ThemePair(theme, theme));
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
