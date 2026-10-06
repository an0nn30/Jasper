package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.text.Segment;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

@SuppressWarnings("try")
class SecretFieldsTest {
    static Secret secret(String id, String group) { return new Secret(id, ControlState.READY, "Password", "Password", group); }
    @Test void storedDetailTransfersCopiesRevealsAndCopiesWithoutImmutableValues() {
        Edt.run(() -> {
            var context=KitFixtures.context(); var events=new ArrayList<UiEvent>(); char[][] copied={null}; var holder=new KitView[1];
            var descriptor=new Secret("detail",ControlState.READY,"","Passphrase","",SecretKind.DETAIL,"copy-detail");
            try(var view=new KitView(context,stack(descriptor),event -> {events.add(event); if(event.equals(new UiEvent.Action("copy-detail")))holder[0].withSecret("detail",chars -> {copied[0]=chars; assertThat(chars).containsExactly('x','y');});})) {
                holder[0]=view;char[] source={'x','y'}; view.replaceSecret("detail",source); source[0]='z';
                assertThat(events).isEmpty(); assertThat(descriptor.toString()).doesNotContain("xy");
                var control=(SecretFields.SecretControl)view.control("detail");
                assertThat(control.input.getParent()).isNull(); assertThat(control.input.isEditable()).isFalse();
                var masked=KitFixtures.render(view,240,70,1);
                find(view,"detail.reveal",JButton.class).doClick(0); assertThat(control.revealed).isTrue();
                var revealed=KitFixtures.render(view,240,70,1); assertThat(masked.getRGB(0,0,240,70,null,0,240)).isNotEqualTo(revealed.getRGB(0,0,240,70,null,0,240));
                find(view,"detail.copy",JButton.class).doClick(0);assertThat(events).containsExactly(new UiEvent.Action("copy-detail"));assertThat(copied[0]).containsOnly((char)0);
                char[] owned=control.detailValue;view.replaceSecret("detail",new char[]{'n'});assertThat(owned).containsOnly((char)0); assertThat(control.revealed).isFalse();
                owned=control.detailValue; view.update(stack());assertThat(owned).containsOnly((char)0);
                view.update(stack(descriptor));view.withSecret("detail",chars -> assertThat(chars).isEmpty());view.replaceSecret("detail",new char[]{'a'});
                owned=((SecretFields.SecretControl)view.control("detail")).detailValue;view.close();assertThat(owned).containsOnly((char)0);
            }finally{release(context);}
        });
    }
    @Test void programmaticInputTransferPreservesCallerOwnershipAndEmitsNoEdit() {
        Edt.run(() -> {
            var context=KitFixtures.context();var events=new ArrayList<UiEvent>();
            try(var view=new KitView(context,stack(secret("a","")),events::add)) {
                char[] value={'a','b'};view.replaceSecret("a",value);assertThat(value).containsExactly('a','b');assertThat(events).isEmpty();
                view.withSecret("a",chars -> assertThat(chars).containsExactly('a','b'));
            }finally{release(context);}
        });
    }
    @Test void temporarySecretCopyIsErasedAfterCallbackFailure() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, KitFixtures.form("unlock"), e -> {})) {
                var pending = new java.util.ArrayDeque<java.awt.Component>(); pending.add(view);
                JPasswordField input = null;
                while (!pending.isEmpty()) {
                    var component = pending.removeFirst();
                    if (component instanceof JPasswordField password) { input = password; break; }
                    if (component instanceof java.awt.Container container) java.util.Collections.addAll(pending, container.getComponents());
                }
                assertThat(input).isNotNull(); input.setText("synthetic-secret"); char[][] captured = {null};
                assertThatThrownBy(() -> view.withSecret("password", value -> {
                    captured[0] = value; throw new IllegalStateException("consumer failed");
                })).isInstanceOf(IllegalStateException.class);
                assertThat(captured[0]).hasSize(16).containsOnly((char) 0);
            } finally { release(context); }
        });
    }
    @Test void pairedRevealAndRethemePreserveLiveDocumentAndSelectionWithoutSecretOutputs() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(secret("a", "pair"), secret("b", "pair"), secret("c", "")), events::add)) {
                var a = find(view, "a.input", JPasswordField.class); var b = find(view, "b.input", JPasswordField.class);
                var c = find(view, "c.input", JPasswordField.class); var doc = a.getDocument();
                a.setText("synthetic-secret"); a.select(1, 5);
                assertThat(events).containsExactly(new UiEvent.SecretChanged("a"));
                assertThat(events.toString()).doesNotContain("synthetic-secret"); assertThat(view.spec().toString()).doesNotContain("synthetic-secret");
                find(view, "a.reveal", JButton.class).doClick(0);
                assertThat((int)a.getEchoChar()).isZero(); assertThat((int)b.getEchoChar()).isZero(); assertThat((int)c.getEchoChar()).isNotZero();
                context.binding().publish(new ThemePair(KitFixtures.custom(), KitFixtures.custom()));
                view.update(stack(secret("a", "pair"), secret("b", "pair"), secret("c", "")));
                assertThat(find(view, "a.input", JPasswordField.class)).isSameAs(a); assertThat(a.getDocument()).isSameAs(doc);
                assertThat(a.getSelectionStart()).isEqualTo(1); assertThat(a.getSelectionEnd()).isEqualTo(5);
                char[][] temporary = {null}; view.withSecret("a", value -> { temporary[0] = value; assertThat(value).containsExactly("synthetic-secret".toCharArray()); });
                assertThat(temporary[0]).containsOnly((char)0);
                find(view, "b.reveal", JButton.class).doClick(0); assertThat((int)a.getEchoChar()).isNotZero(); assertThat((int)b.getEchoChar()).isNotZero();
            } finally { release(context); }
        });
    }
    @Test void replacedRemovedAndClosedStorageIsErasedAndUndoCannotRecoverIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(secret("a", ""), secret("b", "")), events::add)) {
                var a = find(view, "a.input", JPasswordField.class); var b = find(view, "b.input", JPasswordField.class);
                var undo = new javax.swing.undo.UndoManager(); a.getDocument().addUndoableEditListener(undo);
                a.setText("synthetic-secret"); char[] before = storage(a); a.setText("replacement"); assertThat(before).containsOnly((char)0);
                char[] active = storage(a); view.clearSecret("a"); assertThat(active).containsOnly((char)0); assertThat(a.getPassword()).isEmpty(); assertThat(undo.canUndo()).isFalse();
                a.setText("synthetic-secret"); char[] removed = storage(a); view.update(stack(secret("b", "")));
                assertThat(removed).containsOnly((char)0); assertThat(a.getPassword()).isEmpty();
                b.setText("synthetic-secret"); char[] closed = storage(b); view.close(); assertThat(closed).containsOnly((char)0); assertThat(b.getPassword()).isEmpty();
                events.clear(); assertThatThrownBy(() -> a.setText("after-close")).isInstanceOf(IllegalStateException.class); assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void callbackCanUpdateClearOrRemoveSecretDuringDocumentNotification() {
        Edt.run(() -> {
            var context = KitFixtures.context(); KitView[] owner = {null}; int[] changes = {0};
            try (var view = new KitView(context, stack(secret("a", "")), event -> {
                changes[0]++; owner[0].clearSecret("a"); owner[0].update(stack());
            })) {
                owner[0] = view; var input = find(view, "a.input", JPasswordField.class); input.setText("synthetic-secret");
                assertThat(input.getPassword()).isEmpty(); assertThat(changes[0]).isEqualTo(1);
                assertThat(find(view, "a", JComponent.class)).isNull();
            } finally { release(context); }
        });
    }
    @Test void revealedGroupIncludesAddedFieldsAndSurvivesRemovingItsOriginalField() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(secret("a", "pair")), e -> {})) {
                find(view, "a.reveal", JButton.class).doClick(0);
                view.update(stack(secret("a", "pair"), secret("b", "pair")));
                assertThat((int)find(view, "b.input", JPasswordField.class).getEchoChar()).isZero();
                view.update(stack(secret("b", "pair")));
                assertThat((int)find(view, "b.input", JPasswordField.class).getEchoChar()).isZero();
                find(view, "b.reveal", JButton.class).doClick(0);
                assertThat((int)find(view, "b.input", JPasswordField.class).getEchoChar()).isNotZero();
            } finally { release(context); }
        });
    }
    @Test void closeDuringSecretChangedImmediatelyErasesExposedStorageAndTemporaryCopy() {
        Edt.run(() -> {
            var context = KitFixtures.context(); KitView[] owner = {null}; JPasswordField[] input = {null}; char[][] captured = {null}; boolean[] wipedDuringCallback = {false};
            var view = new KitView(context, stack(secret("a", "")), e -> {
                char[] storage = storage(input[0]);
                owner[0].withSecret("a", value -> { captured[0] = value; owner[0].close(); wipedDuringCallback[0] = true; for (char c : storage) if (c != 0 && c != '\n') wipedDuringCallback[0] = false; });
            });
            try {
                owner[0] = view; input[0] = find(view, "a.input", JPasswordField.class); input[0].setText("synthetic-secret");
                assertThat(input[0].getPassword()).isEmpty(); assertThat(captured[0]).containsOnly((char)0); assertThat(wipedDuringCallback[0]).isTrue();
            } finally { view.close(); release(context); }
        });
    }
    static Secret note(String id) { return new Secret(id, ControlState.READY, "", "Secure note", "", SecretKind.MULTILINE, ""); }
    @Test void partialDeletionKeepsTheRemainingCharactersAndWipesTheOldStorage() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(secret("a", "")), e -> {})) {
                var a = find(view, "a.input", JPasswordField.class);
                char[] value = "secret".toCharArray(); view.replaceSecret("a", value); java.util.Arrays.fill(value, (char) 0);
                char[] before = storage(a);
                a.getDocument().remove(1, 3);
                assertThat(before).containsOnly((char) 0);
                view.withSecret("a", chars -> assertThat(chars).containsExactly('s', 'e', 't'));
                view.clearSecret("a"); view.withSecret("a", chars -> assertThat(chars).isEmpty());
            } catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
            finally { release(context); }
        });
    }
    @Test void multilineSecretEditsInClearWrapsAndHasNoRevealOrCopy() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(note("note")), events::add)) {
                var area = find(view, "note.input", JTextArea.class);
                assertThat(area).isNotNull(); assertThat(area.getLineWrap()).isTrue(); assertThat(area.getWrapStyleWord()).isTrue();
                assertThat(area.getRows()).isEqualTo(SecretFields.MULTILINE_ROWS);
                assertThat(find(view, "note.reveal", JButton.class)).as("no reveal control is mounted").isNull();
                assertThat(find(view, "note.copy", JButton.class)).as("no copy control is mounted").isNull();
                assertThat(area.getAccessibleContext().getAccessibleName()).isEqualTo("Secure note");
                area.setText("first line\nsecond \u03bb");
                assertThat(events).containsOnly(new UiEvent.SecretChanged("note"));
                assertThat(events.toString()).doesNotContain("first line"); assertThat(view.spec().toString()).doesNotContain("first line");
                char[][] temporary = {null};
                view.withSecret("note", chars -> { temporary[0] = chars; assertThat(chars).containsExactly("first line\nsecond \u03bb".toCharArray()); });
                assertThat(temporary[0]).containsOnly((char) 0);
                events.clear(); char[] owned = "replaced\ntext".toCharArray(); view.replaceSecret("note", owned);
                assertThat(events).isEmpty(); assertThat(owned).containsExactly("replaced\ntext".toCharArray());
                assertThat(area.getText()).isEqualTo("replaced\ntext");
                char[] live = storage(area); view.clearSecret("note"); assertThat(live).containsOnly((char) 0); assertThat(area.getDocument().getLength()).isZero();
                var undo = new javax.swing.undo.UndoManager(); area.getDocument().addUndoableEditListener(undo);
                area.setText("again"); live = storage(area);
                view.update(stack(secret("note", "")));
                assertThat(live).containsOnly((char) 0);
                assertThat(find(view, "note.input", JPasswordField.class)).as("a kind change replaces the control").isNotNull();
                view.withSecret("note", chars -> assertThat(chars).isEmpty());
                view.update(stack(note("note"))); var next = find(view, "note.input", JTextArea.class);
                next.setText("closing"); char[] closing = storage(next); view.close();
                assertThat(closing).containsOnly((char) 0); assertThat(undo.canUndo()).isFalse();
            } finally { release(context); }
        });
    }
    @Test void multilineSecretRejectsRevealGroupsAndCopyActions() {
        assertThatThrownBy(() -> new Secret("n", ControlState.READY, "", "Note", "pair", SecretKind.MULTILINE, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Secret("n", ControlState.READY, "", "Note", "", SecretKind.MULTILINE, "copy")).isInstanceOf(IllegalArgumentException.class);
    }
    static char[] storage(javax.swing.text.JTextComponent input) {
        var segment = new Segment();
        try { input.getDocument().getText(0, input.getDocument().getLength(), segment); return segment.array; }
        catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
    }
}
