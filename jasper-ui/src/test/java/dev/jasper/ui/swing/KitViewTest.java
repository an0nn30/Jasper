package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("try")
class KitViewTest {
    static Field field(String id, FieldKind kind, String value) {
        return new Field(id, ControlState.READY, kind, value, "Example", "Name", kind == FieldKind.PATH ? "browse" : "");
    }
    static ViewSpec stack(Node... children) {
        return new ViewSpec(new Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(children)));
    }
    static <T extends Component> T find(Component component, String id, Class<T> type) {
        if (type.isInstance(component) && id.equals(component.getName())) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = find(child, id, type); if (found != null) return found;
        }
        return null;
    }
    static <T extends Component> T first(Component component, Class<T> type) {
        if (type.isInstance(component)) return type.cast(component);
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            T found = first(child, type); if (found != null) return found;
        }
        return null;
    }
    static JTextComponent editor(KitView view, String id) { return first(find(view, id, JComponent.class), JTextComponent.class); }
    static void release(KitContext context) { context.binding().close(); context.icons().close(); }

    @Test void stableIdsPreserveEditsDocumentCaretSelectionAndSiblingIdentity() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "initial"), field("b", FieldKind.TEXT, "sibling")), events::add)) {
                var a = editor(view, "a"); var b = editor(view, "b"); var document = a.getDocument();
                a.setText("user edited"); a.select(2, 6); events.clear();
                for (int i = 0; i < 10; i++) view.update(stack(field("a", FieldKind.TEXT, "initial"), field("b", FieldKind.TEXT, "sibling")));
                context.binding().publish(new ThemePair(KitFixtures.custom(), KitFixtures.custom()));
                assertThat(editor(view, "a")).isSameAs(a); assertThat(a.getDocument()).isSameAs(document);
                assertThat(a.getText()).isEqualTo("user edited"); assertThat(a.getSelectionStart()).isEqualTo(2);
                assertThat(a.getSelectionEnd()).isEqualTo(6); assertThat(editor(view, "b")).isSameAs(b);
                assertThat(a.getForeground()).isEqualTo(KitFixtures.custom().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(events).isEmpty();
                a.replaceSelection("X"); assertThat(events).isNotEmpty();
            } finally { release(context); }
        });
    }
    @Test void ancestorInversionRetainsControlsDocumentsAndUnmovedSiblingParent() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var inputNode = field("a", FieldKind.TEXT, "initial"); var siblingNode = field("sibling", FieldKind.TEXT, "sibling");
            var nested = new Stack("nested", ControlState.READY, LayoutRole.VERTICAL, List.of(inputNode));
            var before = new ViewSpec(new Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(nested, siblingNode)));
            try (var view = new KitView(context, before, e -> {})) {
                var input = editor(view, "a"); var document = input.getDocument(); input.setText("edited"); input.select(1, 4);
                var sibling = editor(view, "sibling"); var parent = find(view, "sibling", JComponent.class).getParent();
                var root = new Stack("root", ControlState.READY, LayoutRole.VERTICAL, List.of(inputNode, siblingNode));
                view.update(new ViewSpec(new Stack("nested", ControlState.READY, LayoutRole.VERTICAL, List.of(root))));
                assertThat(editor(view, "a")).isSameAs(input); assertThat(input.getDocument()).isSameAs(document);
                assertThat(input.getText()).isEqualTo("edited"); assertThat(input.getSelectionStart()).isEqualTo(1); assertThat(input.getSelectionEnd()).isEqualTo(4);
                assertThat(editor(view, "sibling")).isSameAs(sibling); assertThat(find(view, "sibling", JComponent.class).getParent()).isSameAs(parent);
                assertThat(find(view, "root", JComponent.class).getParent()).isSameAs(find(view, "nested", JComponent.class));
                view.update(before); assertThat(editor(view, "a")).isSameAs(input); assertThat(input.getDocument()).isSameAs(document);
            } finally { release(context); }
        });
    }
    @Test void changedFieldKindReplacesOnlyThatControlAndRemovedListenersCannotDispatch() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "x"), field("b", FieldKind.TEXT, "y")), events::add)) {
                var a = editor(view, "a"); var b = editor(view, "b");
                view.update(stack(field("a", FieldKind.TEXTAREA, "x"), field("b", FieldKind.TEXT, "y")));
                assertThat(editor(view, "a")).isNotSameAs(a).isInstanceOf(JTextArea.class);
                assertThat(editor(view, "b")).isSameAs(b);
                a.setText("detached"); assertThat(events).isEmpty();
                view.update(stack(field("a", FieldKind.TEXTAREA, "x"))); b.setText("removed"); assertThat(events).isEmpty();
                view.close(); view.close(); editor(view, "a");
            } finally { release(context); }
        });
    }
    @Test void onlyExplicitDescriptorValueChangesReplaceActualTextWithoutEvents() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "old")), events::add)) {
                var a = editor(view, "a"); var doc = a.getDocument(); a.setText("draft"); events.clear();
                view.update(stack(field("a", FieldKind.TEXT, "new")));
                assertThat(a.getText()).isEqualTo("new"); assertThat(a.getDocument()).isSameAs(doc); assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void fontOnlyRefreshRetainsInputAndGrowsFittingBoundsAndListenerCloses() {
        Edt.run(() -> {
            var defaults = UIManager.getDefaults(); Object old = UIManager.get("defaultFont");
            int count = defaults.getPropertyChangeListeners().length; var context = KitFixtures.context();
            try {
                UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 13));
                try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "abcdef")), event -> {})) {
                    var a = editor(view, "a"); var doc = a.getDocument(); a.select(1, 4); var pair = context.binding().current();
                    int height = find(view, "a", JComponent.class).getPreferredSize().height;
                    UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 28));
                    context.binding().publish(pair);
                    assertThat(a.getFont().getSize2D()).isGreaterThan(20);
                    assertThat(find(view, "a", JComponent.class).getPreferredSize().height).isGreaterThan(height);
                    assertThat(editor(view, "a")).isSameAs(a); assertThat(a.getDocument()).isSameAs(doc);
                    assertThat(a.getSelectionStart()).isEqualTo(1); assertThat(a.getSelectionEnd()).isEqualTo(4);
                }
                assertThat(defaults.getPropertyChangeListeners()).hasSize(count);
            } finally { UIManager.put("defaultFont", old); release(context); }
        });
    }
    @Test void literalLabelsIconsAndTextRolesRenderWithoutHtmlInterpretation() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Text("t", ControlState.READY, "<html><b>literal</b>", TextRole.HERO_TITLE),
                    new Nodes.Icon("i", ControlState.READY, "folder", Hue.BLUE, IconSize.CONTROL, "Folder")), e -> {})) {
                JLabel label = find(view, "t", JLabel.class);
                assertThat(label.getClientProperty("html")).isNull(); assertThat(label.getText()).startsWith("<html>");
                assertThat(label.getFont().getSize2D()).isGreaterThan(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D());
                assertThat(find(view, "i", JLabel.class).getAccessibleContext().getAccessibleName()).isEqualTo("Folder");
            } finally { release(context); }
        });
    }
    @Test void unsupportedUpdateFailsBeforeRemovingExistingTree() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "value")), e -> {})) {
                var a = editor(view, "a"); var spec = view.spec();
                assertThatThrownBy(() -> view.update(new ViewSpec(new Custom("custom", ControlState.READY, "content", CustomKind.EDITOR, "Editor"))))
                    .isInstanceOf(UnsupportedOperationException.class);
                assertThat(view.spec()).isSameAs(spec); assertThat(editor(view, "a")).isSameAs(a);
            } finally { release(context); }
        });
    }
    @Test void callbacksAreContainedAndDoNotPreventFurtherEditsOrActions() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var failures = new ArrayList<Throwable>();
            Thread thread = Thread.currentThread(); var old = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, failure) -> failures.add(failure));
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, ""), new Nodes.Button("b", ControlState.READY, "Run", "", ButtonKind.PRIMARY, "run", "")), e -> { throw new IllegalStateException("callback"); })) {
                editor(view, "a").setText("one"); find(view, "b", JButton.class).doClick(0);
                assertThat(failures).hasSize(2);
            } finally { thread.setUncaughtExceptionHandler(old); release(context); }
        });
    }
    @Test void repeatedMountAndCloseLeavesNoDefaultsOrDetachedEventListeners() {
        Edt.run(() -> {
            var context = KitFixtures.context(); int baseline = UIManager.getDefaults().getPropertyChangeListeners().length;
            var events = new ArrayList<UiEvent>();
            try {
                for (int i = 0; i < 10; i++) {
                    var view = new KitView(context, stack(field("a", FieldKind.TEXT, ""), new Nodes.Button("b", ControlState.READY, "Go", "", ButtonKind.GHOST, "go", "")), events::add);
                    var a = editor(view, "a"); var b = find(view, "b", JButton.class);
                    view.close(); view.close(); a.setText("detached"); b.doClick(0);
                    assertThat(b.getActionListeners()).isEmpty();
                }
                context.binding().publish(new ThemePair(KitFixtures.custom(), KitFixtures.custom()));
                assertThat(events).isEmpty(); assertThat(UIManager.getDefaults().getPropertyChangeListeners()).hasSize(baseline);
            } finally { release(context); }
        });
    }
    @Test void throwingDiagnosticHandlerIsAlsoContained() {
        Edt.run(() -> {
            var context = KitFixtures.context(); Thread thread = Thread.currentThread(); var old = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, failure) -> { throw new AssertionError("diagnostic handler"); });
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXT, "")), e -> { throw new IllegalStateException("callback"); })) {
                assertThatCode(() -> editor(view, "a").setText("safe")).doesNotThrowAnyException();
                assertThat(editor(view, "a").getText()).isEqualTo("safe");
            } finally { thread.setUncaughtExceptionHandler(old); release(context); }
        });
    }
    @Test void offEdtCloseEnqueuesIdempotentListenerDisposal() {
        KitView[] mounted = new KitView[1]; KitContext[] contexts = new KitContext[1]; JTextComponent[] inputs = new JTextComponent[1];
        var events = new ArrayList<UiEvent>(); int[] baseline = new int[1];
        Edt.run(() -> {
            baseline[0] = UIManager.getDefaults().getPropertyChangeListeners().length;
            contexts[0] = KitFixtures.context(); mounted[0] = new KitView(contexts[0], stack(field("a", FieldKind.TEXT, "")), events::add);
            inputs[0] = editor(mounted[0], "a");
        });
        mounted[0].close(); mounted[0].close();
        Edt.run(() -> {
            try {
                inputs[0].setText("detached"); assertThat(events).isEmpty();
                assertThat(UIManager.getDefaults().getPropertyChangeListeners()).hasSize(baseline[0]);
            } finally { release(contexts[0]); }
        });
    }
    @Test void disabledStackDisablesChildControlsAndActions() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var children = List.<Node>of(field("a", FieldKind.TEXT, ""), new Nodes.Button("b", ControlState.READY, "Go", "", ButtonKind.GHOST, "go", ""));
            try (var view = new KitView(context, new ViewSpec(new Stack("root", new ControlState(true, false, false, ""), LayoutRole.INLINE, children)), events::add)) {
                assertThat(editor(view, "a").isEnabled()).isFalse(); find(view, "b", JButton.class).doClick(0);
                assertThat(events).isEmpty(); view.update(new ViewSpec(new Stack("root", ControlState.READY, LayoutRole.INLINE, children)));
                assertThat(editor(view, "a").isEnabled()).isTrue(); find(view, "b", JButton.class).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("go"));
            } finally { release(context); }
        });
    }
    @Test void publicOperationsRequireEdtAndClosedViewsRejectUpdates() {
        assertThatThrownBy(() -> new KitView(null, null, null)).isInstanceOf(IllegalStateException.class);
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(), e -> {})) {
                view.close(); assertThatThrownBy(() -> view.update(stack())).isInstanceOf(IllegalStateException.class);
            } finally { release(context); }
        });
    }
}
