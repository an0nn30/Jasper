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
    static char[] storage(JPasswordField input) {
        var segment = new Segment();
        try { input.getDocument().getText(0, input.getDocument().getLength(), segment); return segment.array; }
        catch (javax.swing.text.BadLocationException impossible) { throw new AssertionError(impossible); }
    }
}
