package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Plan 4b Task 1 (SDK 0.27.0): a field's suggestions show in a list under it, the first selected and "Loading
 * suggestions…" after them while more load; Up and Down move the selection, Tab and Enter report the selected one,
 * Escape hides the list and reports the cancel action, and the field keeps the keyboard focus throughout.
 */
class FieldSuggestionsTest {
    static final ControlState READY = ControlState.READY;
    static final Suggestion NAVIGATE = new Suggestion("navigate", "Navigate to “https://jcef”", "URL");
    static final Suggestion SEARCH = new Suggestion("search", "Search for “jcef”", "Web Search");
    static final Suggestion ENGINE = new Suggestion("engine-0", "jcef maven", "Search Suggestion");

    static Field address(String cancel, List<Suggestion> suggestions, boolean loading) {
        return new Field("address", READY, FieldKind.TEXT, "jcef", "Search or enter website name", "Address", "go", Optional.empty(),
            FieldPresentation.STANDARD, "", cancel, "", false, Optional.empty(), "", "", Optional.empty(), false, suggestions, loading);
    }

    static ViewSpec spec(Field field) { return new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(field))); }

    static ViewSpec spec(List<Suggestion> suggestions, boolean loading) { return spec(address("revert", suggestions, loading)); }

    static TextFields.FieldControl control(KitView view) { return (TextFields.FieldControl) view.control("address"); }

    /** The list the field shows now, as the kit publishes it on its input; null while it shows none. */
    static JList<?> shown(KitView view) { return (JList<?>) control(view).input.getClientProperty(KitOwnership.SUGGESTIONS); }

    /** The rows of the list the field shows now: each suggestion's title, then the loading line; empty while it shows none. */
    static List<String> rows(KitView view) {
        var list = shown(view);
        var rows = new ArrayList<String>();
        if (list == null) return rows;
        for (int row = 0; row < list.getModel().getSize(); row++)
            rows.add(list.getModel().getElementAt(row) instanceof Suggestion suggestion ? suggestion.title() : String.valueOf(list.getModel().getElementAt(row)));
        return rows;
    }

    /**
     * A key a person presses with the focus in {@code input}, as Swing delivers it: the input's key listeners first, then,
     * unless one consumed it, the key bindings. Whether anything took the key.
     */
    static boolean press(JTextComponent input, int code) {
        var event = new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED);
        for (var listener : input.getKeyListeners()) listener.keyPressed(event);
        return event.isConsumed() || SwingUtilities.processKeyBindings(event);
    }

    /** A person typing {@code text} over the field's text. */
    static void type(JTextComponent input, String text) {
        input.selectAll();
        new javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new java.awt.event.ActionEvent(input, java.awt.event.ActionEvent.ACTION_PERFORMED, text));
    }

    static boolean tabTraverses(JTextComponent input) {
        return input.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS).contains(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, 0));
    }

    @Test void theSuggestionsShowUnderTheFieldWithTheFirstSelectedUnderBothKits() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked(), kit);
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH), false), e -> {})) {
                KitFixtures.render(view, 480, 120, 1);
                assertThat(rows(view)).as("under %s", kit.id()).containsExactly(NAVIGATE.title(), SEARCH.title());
                var list = shown(view);
                assertThat(list.getSelectedIndex()).as("the first selected").isZero();
                var suggestions = control(view).suggestions;
                assertThat(suggestions.popup.isFocusable()).as("the list never takes the focus").isFalse();
                assertThat(list.isFocusable()).isFalse();
                assertThat(suggestions.popup.getPreferredSize().width).as("as wide as the field's box").isEqualTo(control(view).surface.getWidth());

                var popup = suggestions.popup;
                popup.setSize(popup.getPreferredSize());
                layout(popup);
                var image = new BufferedImage(popup.getWidth(), popup.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { popup.paint(g); } finally { g.dispose(); }
                var first = list.getCellBounds(0, 0); var second = list.getCellBounds(1, 1);
                var at = SwingUtilities.convertPoint(list, 2, first.y + first.height / 2, popup);
                assertThat(new Color(image.getRGB(at.x, at.y), true)).as("the selected row's fill").isEqualTo(ThemePainting.color(context, SemanticColor.SELECTION_BG));
                at = SwingUtilities.convertPoint(list, 2, second.y + second.height / 2, popup);
                assertThat(new Color(image.getRGB(at.x, at.y), true)).as("the menu's surface behind another row").isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_MENU));

                var rows = suggestions.list;
                var selected = (FieldSuggestions.Row) rows.getCellRenderer().getListCellRendererComponent(rows, NAVIGATE, 0, true, false);
                assertThat(selected.title.getText()).isEqualTo(NAVIGATE.title());
                assertThat(selected.detail.getText()).isEqualTo("URL");
                assertThat(selected.title.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION));
                assertThat(selected.detail.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_ONSELECTION_SECONDARY));
                var other = (FieldSuggestions.Row) rows.getCellRenderer().getListCellRendererComponent(rows, SEARCH, 1, false, false);
                assertThat(other.title.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
                assertThat(other.detail.getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                assertThat(other.getAccessibleContext().getAccessibleName()).isEqualTo("Search for “jcef”, Web Search");
            } finally { KitViewTest.release(context); }
        });
    }

    static void layout(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layout(nested);
    }

    @Test void upAndDownMoveTheSelectionAndTabAndEnterReportTheSelectedSuggestion() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH, ENGINE), false), events::add)) {
                var input = control(view).input;
                assertThat(press(input, KeyEvent.VK_DOWN)).isTrue();
                assertThat(shown(view).getSelectedIndex()).isEqualTo(1);
                press(input, KeyEvent.VK_KP_DOWN); press(input, KeyEvent.VK_DOWN);
                assertThat(shown(view).getSelectedIndex()).as("the last row stays selected").isEqualTo(2);
                press(input, KeyEvent.VK_UP); press(input, KeyEvent.VK_KP_UP); press(input, KeyEvent.VK_UP);
                assertThat(shown(view).getSelectedIndex()).as("and the first").isZero();
                assertThat(events).as("moving reports nothing").isEmpty();

                assertThat(press(input, KeyEvent.VK_TAB)).isTrue();
                assertThat(events).containsExactly(new UiEvent.SuggestionChosen("address", "navigate", true));
                assertThat(shown(view)).as("choosing hides the list").isNull();

                type(input, "jcef ");
                assertThat(rows(view)).as("an edit shows it again").hasSize(3);
                press(input, KeyEvent.VK_DOWN);
                events.clear();
                assertThat(press(input, KeyEvent.VK_ENTER)).isTrue();
                assertThat(events).as("Enter, not the trailing action").containsExactly(new UiEvent.SuggestionChosen("address", "search", false));
                assertThat(shown(view)).isNull();
                events.clear();
                assertThat(press(input, KeyEvent.VK_ENTER)).isTrue();
                assertThat(events).as("with the list hidden Enter is the field's own").containsExactly(new UiEvent.Action("go"));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void escapeHidesTheListAndReportsTheCancelActionOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH), false), events::add)) {
                var input = control(view).input;
                assertThat(press(input, KeyEvent.VK_ESCAPE)).isTrue();
                assertThat(shown(view)).isNull();
                assertThat(events).containsExactly(new UiEvent.Action("revert"));
                assertThat(press(input, KeyEvent.VK_ESCAPE)).isTrue();
                assertThat(events).as("the hidden list leaves Escape to the field").containsExactly(new UiEvent.Action("revert"), new UiEvent.Action("revert"));

                view.update(spec(address("", List.of(NAVIGATE, SEARCH), false)));
                type(input, "jcef");
                events.clear();
                assertThat(press(input, KeyEvent.VK_ESCAPE)).as("without a cancel action Escape still hides the list").isTrue();
                assertThat(shown(view)).isNull();
                assertThat(events).as("and reports nothing").noneMatch(UiEvent.Action.class::isInstance);
                assertThat(press(input, KeyEvent.VK_ESCAPE)).as("then it passes on").isFalse();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void whileMoreLoadTheLoadingRowFollowsTheSuggestionsAndIsNeverSelected() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of(), true), events::add)) {
                var input = control(view).input;
                assertThat(rows(view)).containsExactly("Loading suggestions…");
                assertThat(shown(view).getSelectedIndex()).as("nothing to choose").isEqualTo(-1);
                assertThat(tabTraverses(input)).as("Tab moves the focus on").isTrue();
                assertThat(press(input, KeyEvent.VK_ENTER)).isTrue();
                assertThat(events).as("Enter keeps its meaning").containsExactly(new UiEvent.Action("go"));
                var list = control(view).suggestions.list;
                var loading = (FieldSuggestions.Row) list.getCellRenderer().getListCellRendererComponent(list, FieldSuggestions.LOADING, 0, true, false);
                assertThat(loading.title.getForeground()).as("a quiet row").isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_TERTIARY));
                assertThat(loading.detail.isVisible()).isFalse();

                view.update(spec(List.of(NAVIGATE, SEARCH), true));
                assertThat(rows(view)).containsExactly(NAVIGATE.title(), SEARCH.title(), "Loading suggestions…");
                press(input, KeyEvent.VK_DOWN); press(input, KeyEvent.VK_DOWN);
                assertThat(shown(view).getSelectedIndex()).as("the loading row is not a suggestion").isEqualTo(1);
                view.update(spec(List.of(NAVIGATE, SEARCH, ENGINE), false));
                assertThat(rows(view)).containsExactly(NAVIGATE.title(), SEARCH.title(), ENGINE.title());
                assertThat(shown(view).getSelectedIndex()).as("the selection stays on its suggestion").isEqualTo(1);
                events.clear();
                assertThat(press(input, KeyEvent.VK_ESCAPE)).as("Escape hides a loading list too").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("revert"));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void anUpdateKeepsTheSelectionWhileItsSuggestionIsListedAndAnEditSelectsTheFirst() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH), false), e -> {})) {
                var input = control(view).input;
                press(input, KeyEvent.VK_DOWN);
                view.update(spec(List.of(SEARCH, ENGINE), false));
                assertThat(shown(view).getSelectedIndex()).as("search moved up a row").isZero();
                press(input, KeyEvent.VK_DOWN);
                view.update(spec(List.of(NAVIGATE, SEARCH), false));
                assertThat(shown(view).getSelectedIndex()).as("engine-0 is gone: the first").isZero();
                press(input, KeyEvent.VK_DOWN);
                type(input, "jce");
                assertThat(shown(view).getSelectedIndex()).as("an edit selects the first").isZero();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void whileTheListOffersSuggestionsTabIsNotAFocusTraversalKey() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, spec(List.of(NAVIGATE), false), e -> {})) {
                var input = control(view).input;
                assertThat(tabTraverses(input)).isFalse();
                assertThat(input.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS))
                    .as("Ctrl+Tab still moves the focus on").contains(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK));
                assertThat(input.getFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS))
                    .as("and Shift+Tab back").contains(AWTKeyStroke.getAWTKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
                press(input, KeyEvent.VK_ESCAPE);
                assertThat(tabTraverses(input)).as("a hidden list gives Tab back").isTrue();
                assertThat(input.areFocusTraversalKeysSet(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)).isFalse();
                type(input, "j");
                assertThat(tabTraverses(input)).isFalse();
                view.update(spec(List.of(), false));
                assertThat(tabTraverses(input)).as("no suggestions, no list").isTrue();
                assertThat(shown(view)).isNull();
                view.update(spec(List.of(NAVIGATE), false));
                assertThat(tabTraverses(input)).isFalse();
                var closing = new KitView(context, spec(List.of(NAVIGATE), false), e -> {});
                var closed = control(closing).input;
                assertThat(tabTraverses(closed)).isFalse();
                closing.close();
                assertThat(tabTraverses(closed)).as("closing gives Tab back").isTrue();
                assertThat(closed.getClientProperty(KitOwnership.SUGGESTIONS)).isNull();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theFocusLeavingTheFieldForGoodHidesTheListUntilThePersonEdits() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH), false), e -> {})) {
                var input = control(view).input;
                var other = new JButton();
                for (var listener : input.getFocusListeners()) listener.focusLost(new FocusEvent(input, FocusEvent.FOCUS_LOST, true, other));
                assertThat(rows(view)).as("a window going behind another keeps the list").hasSize(2);
                for (var listener : input.getFocusListeners()) listener.focusLost(new FocusEvent(input, FocusEvent.FOCUS_LOST, false, other));
                assertThat(shown(view)).isNull();
                view.update(spec(List.of(NAVIGATE, SEARCH, ENGINE), false));
                assertThat(shown(view)).as("new suggestions arriving late stay hidden").isNull();
                type(input, "jc");
                assertThat(rows(view)).hasSize(3);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aClickOnASuggestionChoosesItAsEnterDoes() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of(NAVIGATE, SEARCH), true), events::add)) {
                KitFixtures.render(view, 480, 120, 1);
                var popup = control(view).suggestions.popup;
                popup.setSize(popup.getPreferredSize()); layout(popup);
                var list = shown(view);
                // The kit's own pointer handling (Swing's list press handling needs a display).
                var pointer = control(view).suggestions.pointer;
                var second = list.getCellBounds(1, 1); var loading = list.getCellBounds(2, 2);
                long when = System.currentTimeMillis();
                pointer.mouseMoved(new MouseEvent(list, MouseEvent.MOUSE_MOVED, when, 0, 4, second.y + 2, 0, false));
                assertThat(list.getSelectedIndex()).as("the pointer selects the row it rests on").isEqualTo(1);
                list.setSelectedIndex(2);
                pointer.mousePressed(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 4, loading.y + 2, 1, false, MouseEvent.BUTTON1));
                assertThat(list.getSelectedIndex()).as("a press on the loading row leaves the selection on its suggestion").isEqualTo(1);
                pointer.mouseReleased(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, when, 0, 4, loading.y + 2, 1, false, MouseEvent.BUTTON1));
                assertThat(events).as("the loading row is not a suggestion").isEmpty();
                pointer.mouseReleased(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, when, 0, 4, second.y + 2, 1, false, MouseEvent.BUTTON3));
                assertThat(events).as("a right click chooses nothing").isEmpty();
                pointer.mousePressed(new MouseEvent(list, MouseEvent.MOUSE_PRESSED, when, InputEvent.BUTTON1_DOWN_MASK, 4, second.y + 2, 1, false, MouseEvent.BUTTON1));
                pointer.mouseReleased(new MouseEvent(list, MouseEvent.MOUSE_RELEASED, when, 0, 4, second.y + 2, 1, false, MouseEvent.BUTTON1));
                assertThat(events).containsExactly(new UiEvent.SuggestionChosen("address", "search", false));
                assertThat(shown(view)).isNull();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aFieldWithoutSuggestionsOrThatTakesNoInputShowsNoList() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of(), false), events::add)) {
                assertThat(control(view).suggestions).as("made only when needed").isNull();
                assertThat(shown(view)).isNull();
                var busy = new Field("address", new ControlState(true, true, true, ""), FieldKind.TEXT, "jcef", "", "Address", "go", Optional.empty(),
                    FieldPresentation.STANDARD, "", "revert", "", false, Optional.empty(), "", "", Optional.empty(), false, List.of(NAVIGATE), false);
                view.update(spec(busy));
                assertThat(shown(view)).as("a busy field").isNull();
                var input = control(view).input;
                assertThat(press(input, KeyEvent.VK_TAB)).isFalse();
                assertThat(events).isEmpty();
                view.update(spec(List.of(NAVIGATE), false));
                assertThat(rows(view)).containsExactly(NAVIGATE.title());
                var disabled = new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(new Nodes.Stack("holder", new ControlState(true, false, false, ""),
                    LayoutRole.VERTICAL, List.of(address("revert", List.of(NAVIGATE), false)))));
                view.update(new ViewSpec(disabled));
                assertThat(shown(view)).as("a field in a disabled container").isNull();
            } finally { KitViewTest.release(context); }
        });
    }
}
