package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

/** Spec 8.2's {@code Field.readOnly}: focusable, selectable and copyable, never edited, so it reports no Edited. */
@SuppressWarnings("try")
class ReadOnlyFieldsTest {
    static Field readOnlyField(String id, FieldKind kind, String value, boolean readOnly) {
        return new Field(id, ControlState.READY, kind, value, "", "Login", "",
            kind == FieldKind.NUMBER ? Optional.of(new Numeric(0, 10, 1, 0)) : Optional.empty(), FieldPresentation.STANDARD, "", "", "", readOnly);
    }

    @Test void aReadOnlyFieldKeepsFocusSelectionAndCopyButTakesNoEdit() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            String fact = "No login (TFTP has none)";
            try (var view = new KitView(context, new ViewSpec(readOnlyField("login", FieldKind.TEXT, fact, true)), events::add)) {
                var input = find(view, "login.input", JTextField.class);
                assertThat(input.isEditable()).isFalse();
                assertThat(input.isEnabled()).isTrue();
                assertThat(input.isFocusable()).isTrue();
                assertThat(input.getAccessibleContext().getAccessibleStateSet().contains(javax.accessibility.AccessibleState.EDITABLE)).isFalse();
                new DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "x"));
                input.getActionMap().get(DefaultEditorKit.deletePrevCharAction).actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "delete"));
                view.selectAll("login");
                assertThat(input.getSelectedText()).isEqualTo(fact);
                assertThat(input.getText()).isEqualTo(fact);
                assertThat(events).isEmpty();
                var document = input.getDocument();
                view.update(new ViewSpec(readOnlyField("login", FieldKind.TEXT, fact, false)));
                assertThat(input.getDocument()).isSameAs(document);
                assertThat(input.isEditable()).isTrue();
                new DefaultEditorKit.DefaultKeyTypedAction().actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "x"));
                assertThat(events).isNotEmpty().last().isEqualTo(new UiEvent.Edited("login", "x"));
            } finally { release(context); }
        });
    }

    @Test void aReadOnlyFieldCopiesItsTextAndNoPasteRouteEditsIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            String fact = "No login (TFTP has none)";
            var spec = new ViewSpec(new Nodes.Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(
                readOnlyField("login", FieldKind.TEXT, fact, true), readOnlyField("source", FieldKind.TEXT, "PASTED", false),
                readOnlyField("target", FieldKind.TEXT, "", false))));
            try (var view = new KitView(context, spec, events::add)) {
                var input = find(view, "login.input", JTextField.class);
                input.selectAll();
                var clipboard = new Clipboard("read-only-copy");
                input.getTransferHandler().exportToClipboard(input, clipboard, TransferHandler.COPY);
                try { assertThat(clipboard.getContents(null).getTransferData(DataFlavor.stringFlavor)).isEqualTo(fact); }
                catch (Exception failure) { throw new AssertionError(failure); }
                // Headless, the copy and paste actions share Swing's sandbox clipboard, never the desktop's.
                var source = find(view, "source.input", JTextField.class);
                source.selectAll(); source.copy();
                var paste = TransferHandler.getPasteAction();
                var target = find(view, "target.input", JTextField.class);
                paste.actionPerformed(new ActionEvent(target, ActionEvent.ACTION_PERFORMED, "paste"));
                assertThat(target.getText()).as("an editable field takes the paste").isEqualTo("PASTED");
                events.clear();
                int bound = 0;
                for (var stroke : input.getInputMap(JComponent.WHEN_FOCUSED).allKeys()) {
                    Object action = input.getInputMap(JComponent.WHEN_FOCUSED).get(stroke);
                    if (stroke.getKeyEventType() != KeyEvent.KEY_PRESSED || !(DefaultEditorKit.pasteAction.equals(action) || "paste".equals(action))) continue;
                    bound++;
                    SwingUtilities.processKeyBindings(new KeyEvent(input, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), stroke.getModifiers(), stroke.getKeyCode(), KeyEvent.CHAR_UNDEFINED));
                }
                assertThat(bound).as("the look and feel binds a paste key").isPositive();
                paste.actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, "paste"));
                assertThat(input.getText()).isEqualTo(fact);
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void aReadOnlyNumberIgnoresItsStepKeys() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(readOnlyField("count", FieldKind.NUMBER, "5", true)), events::add)) {
                var input = find(view, "count.input", JTextField.class);
                for (String key : List.of("number.step.UP", "number.step.DOWN"))
                    input.getActionMap().get(key).actionPerformed(new ActionEvent(input, ActionEvent.ACTION_PERFORMED, key));
                assertThat(input.getText()).isEqualTo("5");
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void stepperAndSliderAreNeverReadOnlyAndFieldsDefaultToEditable() {
        for (var presentation : List.of(FieldPresentation.STEPPER, FieldPresentation.SLIDER))
            assertThatIllegalArgumentException().isThrownBy(() -> new Field("n", ControlState.READY, FieldKind.NUMBER, "1", "", "N", "",
                Optional.of(new Numeric(0, 10, 1, 0)), presentation, "", "", "", true)).withMessageContaining("never read-only");
        assertThat(new Field("t", ControlState.READY, FieldKind.TEXT, "", "", "T", "").readOnly()).isFalse();
    }
}
