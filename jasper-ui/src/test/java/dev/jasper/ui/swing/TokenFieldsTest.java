package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Plan 5 Task 6 (SDK 0.29.0): a token field shows its tokens as chips of the look's tag face, each with its remove
 * button, and the typed text after them. Enter, a comma and a permanent focus loss add the typed text; Backspace with
 * nothing typed and a remove button remove a token; text the token rule refuses is dropped; each change reports the
 * whole list once.
 */
class TokenFieldsTest {
    static final ControlState READY = ControlState.READY;

    static TokenField tags(List<String> tokens) { return tags(READY, tokens); }

    static TokenField tags(ControlState state, List<String> tokens) { return new TokenField("tags", state, "Tags", "Enter tags…", tokens); }

    static ViewSpec spec(TokenField field) { return new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(field))); }

    static ViewSpec spec(List<String> tokens) { return spec(tags(tokens)); }

    /** A sheet holding the field and its PRIMARY Save, which the form's Enter presses. */
    static ViewSpec sheet(List<String> tokens) {
        return new ViewSpec(new Form("form", READY, FormKind.SHEET, "Edit Bookmark", Optional.empty(), List.of(tags(tokens)), Optional.empty(),
            List.of(new Nodes.Button("save", READY, "Save", "", ButtonKind.PRIMARY, "save", "")), "cancel"));
    }

    static TokenFields.TokenControl control(KitView view) { return (TokenFields.TokenControl) view.control("tags"); }

    /** The tokens the chips show, by their accessible names, in order. */
    static List<String> shown(KitView view) {
        return control(view).chips.stream().map(token -> token.chip.component.getAccessibleContext().getAccessibleName()).toList();
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

    /** A person typing (or pasting) {@code text} over the typed text. */
    static void type(JTextComponent input, String text) {
        input.selectAll();
        new javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new java.awt.event.ActionEvent(input, java.awt.event.ActionEvent.ACTION_PERFORMED, text));
    }

    /** The focus leaving {@code input}: for good, or for a while (another window, a popup). */
    static void focusLost(JTextComponent input, boolean temporary) {
        for (var listener : input.getFocusListeners()) listener.focusLost(new FocusEvent(input, FocusEvent.FOCUS_LOST, temporary, null));
    }

    static UiEvent.TokensChanged changed(String... tokens) { return new UiEvent.TokensChanged("tags", List.of(tokens)); }

    /** The token whose chip the keyboard has selected, as the kit publishes it on the input; null for none. */
    static String selected(KitView view) { return (String) control(view).input.getClientProperty(KitOwnership.SELECTED_TOKEN); }

    /** The tokens whose chips show the focus ring. */
    static List<String> ringed(KitView view) {
        return control(view).chips.stream().filter(token -> token.chip.component.getClientProperty("Jasper.kit.halo") != null).map(token -> token.token).toList();
    }

    @Test void eachTokenIsAChipOfTheLooksTagFaceWithItsRemoveButtonAndTheTypedTextFollows() {
        for (var kit : List.of(Kit.DEFAULT, KitFixtures.enterprise())) Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), kit);
            boolean classic = context.faces().tag instanceof ClassicTagFace;
            try (var view = new KitView(context, spec(List.of("work", "\u00C4pfel", "\u00E4pfel")), e -> {})) {
                KitFixtures.render(view, 420, 90, 1);
                var control = control(view);
                assertThat(shown(view)).as("under %s", kit.id()).containsExactly("work", "\u00C4pfel", "\u00E4pfel");
                for (var token : control.chips) {
                    var chip = (Indicators.ChipLabel) token.chip.component;
                    assertThat(chip.getParent()).isSameAs(control.surface);
                    // H19: a status tag's classic face writes it in capitals; a token keeps the case the user typed, in every look.
                    assertThat(chip.getText()).as("as entered, under %s", kit.id()).isEqualTo(token.token);
                    assertThat(chip.edge != null).as("the classic tag's box").isEqualTo(classic);
                    var remove = token.remove;
                    assertThat(remove.getParent()).as("the remove button stands inside its chip").isSameAs(chip);
                    assertThat(remove.getAccessibleContext().getAccessibleName()).isEqualTo("Remove " + token.token);
                    assertThat(remove.getToolTipText()).isEqualTo("Remove " + token.token);
                    assertThat(remove.isFocusable()).as("Backspace is the keyboard's way to remove a token").isFalse();
                    assertThat(remove.getX() + remove.getWidth()).isLessThanOrEqualTo(chip.getWidth());
                    assertThat(remove.getWidth()).isPositive();
                    assertThat(remove.getY()).isGreaterThanOrEqualTo(0);
                    assertThat(remove.getY() + remove.getHeight()).isLessThanOrEqualTo(chip.getHeight());
                }
                var last = control.chips.getLast().chip.component;
                assertThat(control.input.getX()).as("the typed text follows the last chip").isGreaterThan(last.getX() + last.getWidth());
                assertThat(control.input.getY()).isLessThan(last.getY() + last.getHeight());
                assertThat(control.label.isVisible()).as("its label stands over it, as a standard field's").isTrue();
                assertThat(control.label.getText()).isEqualTo("Tags");
                assertThat(control.input.getAccessibleContext().getAccessibleName()).isEqualTo("Tags");
                assertThat(control.input.getAccessibleContext().getAccessibleDescription()).as("the description names the tokens, then the hint")
                    .isEqualTo("Tags: work, \u00C4pfel, \u00E4pfel. Enter tags\u2026");
                assertThat(control.surface.hint).as("the hint shows only while the field is empty").isEmpty();
                view.update(spec(List.of()));
                assertThat(control(view).surface.hint).isEqualTo("Enter tags\u2026");
                assertThat(control(view).input.getAccessibleContext().getAccessibleDescription()).isEqualTo("Enter tags\u2026");
                view.update(spec(tags(new ControlState(true, true, false, "Too many tags"), List.of("work"))));
                assertThat(control(view).input.getAccessibleContext().getAccessibleDescription()).as("an error in place of the hint").isEqualTo("Tags: work. Too many tags");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void enterACommaAndAPermanentFocusLossAddTheTypedTextEachChangeReportingTheWholeListOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("work")), events::add)) {
                var input = control(view).input;
                type(input, "news");
                assertThat(events).as("typing alone reports nothing").isEmpty();
                assertThat(press(input, KeyEvent.VK_ENTER)).isTrue();
                assertThat(events).containsExactly(changed("work", "news"));
                assertThat(input.getText()).isEmpty();
                assertThat(input.getAccessibleContext().getAccessibleDescription()).as("each change names the tokens again").isEqualTo("Tags: work, news. Enter tags\u2026");
                assertThat(shown(view)).containsExactly("work", "news");

                type(input, "reading,later,soon");
                assertThat(events).as("one change for a paste of several").containsExactly(changed("work", "news"), changed("work", "news", "reading", "later"));
                assertThat(input.getText()).isEqualTo("soon");
                focusLost(input, true);
                assertThat(events).as("a passing focus loss keeps the text").hasSize(2);
                assertThat(input.getText()).isEqualTo("soon");
                focusLost(input, false);
                assertThat(events.getLast()).isEqualTo(changed("work", "news", "reading", "later", "soon"));
                assertThat(input.getText()).isEmpty();

                type(input, "   ");
                focusLost(input, false);
                type(input, ",");
                assertThat(events).as("blank text reports nothing").hasSize(3);
                assertThat(input.getText()).isEmpty();
                assertThat(control(view).tokens).containsExactly("work", "news", "reading", "later", "soon");
            } finally { KitViewTest.release(context); }
        });
    }

    /**
     * A pasted list: a line break (LF or CR LF) and a tab separate tokens as a comma does, all added in one change, and the
     * text after the last separator stays typed. Once a document filter is installed, the field's document no longer
     * turns line breaks into spaces itself.
     */
    @Test void lineBreaksAndTabsInAPasteSeparateTokensAsCommasDo() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of()), events::add)) {
                var input = control(view).input;
                type(input, "work\nnews\r\nhome\tx");
                assertThat(events).containsExactly(changed("work", "news", "home"));
                assertThat(input.getText()).isEqualTo("x");
                press(input, KeyEvent.VK_ENTER);
                assertThat(events.getLast()).isEqualTo(changed("work", "news", "home", "x"));
            } finally { KitViewTest.release(context); }
        });
    }

    /**
     * H18: the field stays one Tab stop and the keyboard reaches every chip. Left with the caret at the start selects the
     * last chip, drawn with the focus ring and announced as the input's active descendant; Left, Right, Home and End move
     * among the chips; Backspace or Delete removes the selected one and selects its neighbour; Right past the last chip,
     * Escape, typing and the focus leaving return to the typed text.
     */
    @Test void theKeyboardSelectsAnyChipAndRemovesIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("a", "b", "c", "d", "e")), events::add)) {
                KitFixtures.render(view, 480, 90, 1);
                var control = control(view); var input = control.input;
                for (var token : control.chips) {
                    assertThat(token.chip.component.isFocusable()).as("no chip is a Tab stop").isFalse();
                    assertThat(token.remove.isFocusable()).isFalse();
                }
                var announced = new ArrayList<Object>();
                input.getAccessibleContext().addPropertyChangeListener(event -> {
                    if (javax.accessibility.AccessibleContext.ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY.equals(event.getPropertyName())) announced.add(event.getNewValue());
                });
                type(input, "zz");
                press(input, KeyEvent.VK_LEFT);
                assertThat(selected(view)).as("Left with the caret after typed text moves the caret").isNull();
                assertThat(input.getCaretPosition()).isEqualTo(1);
                input.setText("");

                assertThat(press(input, KeyEvent.VK_LEFT)).isTrue();
                assertThat(selected(view)).isEqualTo("e");
                assertThat(ringed(view)).as("drawn with the focus ring").containsExactly("e");
                assertThat(announced).as("announced as the input's active descendant").containsExactly(control.chips.getLast().chip.component);
                assertThat(((javax.accessibility.Accessible) announced.getLast()).getAccessibleContext().getAccessibleName()).isEqualTo("e");
                press(input, KeyEvent.VK_LEFT); press(input, KeyEvent.VK_LEFT); press(input, KeyEvent.VK_LEFT);
                assertThat(selected(view)).isEqualTo("b");
                press(input, KeyEvent.VK_HOME);
                assertThat(selected(view)).isEqualTo("a");
                press(input, KeyEvent.VK_LEFT);
                assertThat(selected(view)).as("the first chip stays selected").isEqualTo("a");
                press(input, KeyEvent.VK_END);
                assertThat(selected(view)).isEqualTo("e");
                press(input, KeyEvent.VK_HOME); press(input, KeyEvent.VK_RIGHT);
                assertThat(selected(view)).isEqualTo("b");
                assertThat(events).as("moving reports nothing").isEmpty();

                assertThat(press(input, KeyEvent.VK_BACK_SPACE)).isTrue();
                assertThat(events).containsExactly(changed("a", "c", "d", "e"));
                assertThat(selected(view)).as("Backspace selects the chip before").isEqualTo("a");
                assertThat(ringed(view)).containsExactly("a");
                assertThat(press(input, KeyEvent.VK_ESCAPE)).as("Escape stays with the field").isTrue();
                assertThat(selected(view)).isNull();
                assertThat(ringed(view)).isEmpty();
                type(input, "f");
                assertThat(input.getText()).isEqualTo("f");
                press(input, KeyEvent.VK_ENTER);
                assertThat(events.getLast()).isEqualTo(changed("a", "c", "d", "e", "f"));

                press(input, KeyEvent.VK_LEFT); press(input, KeyEvent.VK_LEFT); press(input, KeyEvent.VK_LEFT);
                assertThat(press(input, KeyEvent.VK_DELETE)).isTrue();
                assertThat(events.getLast()).isEqualTo(changed("a", "c", "e", "f"));
                assertThat(selected(view)).as("Delete selects the chip after").isEqualTo("e");
                press(input, KeyEvent.VK_RIGHT);
                assertThat(selected(view)).isEqualTo("f");
                press(input, KeyEvent.VK_RIGHT);
                assertThat(selected(view)).as("Right past the last chip returns to the typed text").isNull();
                press(input, KeyEvent.VK_LEFT);
                type(input, "g");
                assertThat(selected(view)).as("typing returns to the typed text").isNull();
                assertThat(input.getText()).isEqualTo("g");
                input.setText("");
                press(input, KeyEvent.VK_LEFT);
                focusLost(input, false);
                assertThat(selected(view)).as("and so does the focus leaving").isNull();
                assertThat(events).hasSize(3);
            } finally { KitViewTest.release(context); }
        });
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("x")), events::add)) {
                var input = control(view).input;
                press(input, KeyEvent.VK_LEFT);
                press(input, KeyEvent.VK_BACK_SPACE);
                assertThat(events).containsExactly(changed());
                assertThat(selected(view)).as("when the last chip goes, the typed text has the keys").isNull();
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void backspaceWithNothingTypedAndARemoveButtonRemoveATokenAndEnterWithNothingTypedPressesTheFormsPrimary() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, sheet(List.of("work", "news", "reading")), events::add)) {
                KitFixtures.render(view, 480, 240, 1);
                var input = control(view).input;
                assertThat(((Forms.FormControl) view.control("form")).firstInput()).as("the form's first input takes the focus").isSameAs(input);
                type(input, "x");
                press(input, KeyEvent.VK_BACK_SPACE);
                assertThat(events).as("Backspace deletes typed text first").isEmpty();
                input.setText("");
                assertThat(press(input, KeyEvent.VK_BACK_SPACE)).isTrue();
                assertThat(events).containsExactly(changed("work", "news"));
                control(view).chips.getFirst().remove.doClick(0);
                assertThat(events.getLast()).isEqualTo(changed("news"));
                assertThat(shown(view)).containsExactly("news");

                type(input, "later");
                press(input, KeyEvent.VK_ENTER);
                assertThat(events).as("Enter with text typed adds it, and the form keeps waiting").containsExactly(changed("work", "news"), changed("news"), changed("news", "later"));
                press(input, KeyEvent.VK_ENTER);
                assertThat(events.getLast()).as("Enter with nothing typed passes on to Save").isEqualTo(new UiEvent.Action("save"));
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void aDuplicateInAnotherCaseIsDroppedAndSoIsTextTheTokenRuleRefuses() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("work")), events::add)) {
                var input = control(view).input;
                for (String refused : List.of("Work", "WORK ", "x".repeat(65), "a\u007fb")) {
                    type(input, refused);
                    press(input, KeyEvent.VK_ENTER);
                    assertThat(input.getText()).as("%s is dropped", refused).isEmpty();
                }
                assertThat(events).isEmpty();
                type(input, "Äpfel,äpfel,");
                assertThat(events).as("only ASCII letters fold").containsExactly(changed("work", "Äpfel", "äpfel"));
            } finally { KitViewTest.release(context); }
        });
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var hundred = IntStream.range(0, 100).mapToObj(i -> "tag" + i).toList();
            try (var view = new KitView(context, spec(hundred), events::add)) {
                var input = control(view).input;
                type(input, "one more");
                press(input, KeyEvent.VK_ENTER);
                assertThat(events).as("a hundred and first is dropped").isEmpty();
                assertThat(input.getText()).isEmpty();
                assertThat(control(view).tokens).isEqualTo(hundred);
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void anUpdateThatChangesTheTokensReplacesThemAndKeepsTheTypedText() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("work")), events::add)) {
                var input = control(view).input;
                type(input, "news");
                press(input, KeyEvent.VK_ENTER);
                view.update(spec(List.of("work")));
                assertThat(shown(view)).as("an update that leaves the tokens as they were keeps the user's").containsExactly("work", "news");
                type(input, "dra");
                view.update(spec(List.of("a", "b")));
                assertThat(shown(view)).containsExactly("a", "b");
                assertThat(input.getText()).isEqualTo("dra");
                assertThat(control(view)).as("the same control").isSameAs(view.control("tags"));
                press(input, KeyEvent.VK_ENTER);
                assertThat(events.getLast()).isEqualTo(changed("a", "b", "dra"));
            } finally { KitViewTest.release(context); }
        });
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            KitView[] holder = {null};
            // An owner that keeps at most two tokens answers each change with the first two.
            try (var view = new KitView(context, spec(List.of("a", "b")), event -> {
                events.add(event);
                if (event instanceof UiEvent.TokensChanged change) holder[0].update(spec(change.tokens().subList(0, Math.min(2, change.tokens().size()))));
            })) {
                holder[0] = view;
                type(control(view).input, "c,");
                assertThat(events).containsExactly(changed("a", "b", "c"));
                assertThat(shown(view)).as("an update while the field reports replaces what it shows").containsExactly("a", "b");
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void oneLineOfChipsStandsInAFieldsHeightAndMoreWrapAndGrowIt() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var field = new Field("title", READY, FieldKind.TEXT, "Example", "", "Title", "");
            var many = IntStream.range(0, 12).mapToObj(i -> "reading-list-" + i).toList();
            var root = new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(field, tags(List.of("work"))));
            try (var view = new KitView(context, new ViewSpec(root), e -> {})) {
                KitFixtures.render(view, 360, 200, 1);
                var control = control(view);
                int one = ThemePainting.height(context, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY);
                assertThat(control.surface.getHeight()).isEqualTo(one);
                assertThat(control.surface.getHeight()).as("as tall as a text field").isEqualTo(((TextFields.FieldControl) view.control("title")).surface.getHeight());
                view.update(new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(field, tags(many)))));
                KitFixtures.render(view, 360, 400, 1);
                control = control(view);
                assertThat(control.surface.getHeight()).as("the chips wrap and the field grows").isGreaterThan(2 * one);
                assertThat(Composition.height(control.component, control.component.getWidth())).isEqualTo(control.component.getHeight());
                var chips = control.chips.stream().map(token -> token.chip.component).toList();
                assertThat(chips.getLast().getY()).isGreaterThan(chips.getFirst().getY());
                for (var chip : chips) assertThat(chip.getX() + chip.getWidth()).isLessThanOrEqualTo(control.surface.getWidth());
                assertThat(control.input.getY()).as("the typed text on the last line").isGreaterThanOrEqualTo(chips.getLast().getY());
            } finally { KitViewTest.release(context); }
        });
    }

    /**
     * Plan 5 (H17): where kit fields release Ctrl+H (Windows and Linux), the token field leaves it to the window's keymap,
     * as every kit text field does; elsewhere it deletes backward. Backspace deletes typed text on every platform.
     */
    @Test void ctrlHIsLeftToTheWindowWhereKitFieldsReleaseItWhileBackspaceStillDeletes() {
        for (boolean released : new boolean[] {false, true}) Edt.run(() -> {
            boolean prior = TextFields.ctrlHReleased;
            TextFields.ctrlHReleased = released;
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, spec(List.of("work")), events::add)) {
                var input = control(view).input;
                type(input, "abc");
                boolean took = SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                    java.awt.event.InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_H, KeyEvent.CHAR_UNDEFINED));
                assertThat(took).as("released %s: the field takes Ctrl+H", released).isEqualTo(!released);
                assertThat(input.getText()).isEqualTo(released ? "abc" : "ab");
                press(input, KeyEvent.VK_BACK_SPACE);
                assertThat(input.getText()).isEqualTo(released ? "ab" : "a");
                assertThat(events).as("deleting typed text reports nothing").isEmpty();
            } finally { TextFields.ctrlHReleased = prior; KitViewTest.release(context); }
        });
    }

    @Test void aFieldThatTakesNoInputChangesNothingAndARowLabelsIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var disabled = new ControlState(true, false, false, "");
            try (var view = new KitView(context, spec(tags(disabled, List.of("work", "news"))), events::add)) {
                var control = control(view);
                assertThat(control.input.isEditable()).isFalse();
                press(control.input, KeyEvent.VK_BACK_SPACE);
                control.chips.getFirst().remove.doClick(0);
                focusLost(control.input, false);
                assertThat(events).isEmpty();
                assertThat(control.tokens).containsExactly("work", "news");
            } finally { KitViewTest.release(context); }
            var rows = KitFixtures.context();
            var row = new FormRow("row", READY, "Tags", "", tags(List.of("work")), Optional.empty());
            try (var view = new KitView(rows, new ViewSpec(new Nodes.Stack("root", READY, LayoutRole.VERTICAL, List.of(row))), events::add)) {
                assertThat(control(view).label.isVisible()).as("the row's label names it").isFalse();
                assertThat(control(view).input.getAccessibleContext().getAccessibleName()).isEqualTo("Tags");
            } finally { KitViewTest.release(rows); }
        });
    }
}
