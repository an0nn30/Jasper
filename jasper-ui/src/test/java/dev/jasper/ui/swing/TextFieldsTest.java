package dev.jasper.ui.swing;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.text.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.jasper.ui.swing.KitViewTest.*;

class TextFieldsTest {
    @Test void synchronousEditEchoUpdatesDescriptorWithoutMutatingInNotification() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var failures = new ArrayList<Throwable>(); var events = new ArrayList<UiEvent>();
            Thread thread = Thread.currentThread(); var old = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, failure) -> failures.add(failure)); KitView[] mounted = new KitView[1];
            try {
                mounted[0] = new KitView(context, stack(field("a", FieldKind.TEXT, "")), event -> {
                    events.add(event);
                    if (event instanceof UiEvent.Edited edit) mounted[0].update(stack(field("a", FieldKind.TEXT, edit.text())));
                });
                var input = editor(mounted[0], "a"); var document = input.getDocument(); input.replaceSelection("a");
                assertThat(failures).isEmpty();
                assertThat(((Field)((Stack)mounted[0].spec().root()).children().getFirst()).value()).isEqualTo("a");
                assertThat(input.getText()).isEqualTo("a"); assertThat(input.getDocument()).isSameAs(document);
                assertThat(events).containsExactly(new UiEvent.Edited("a", "a"));
            } finally { if (mounted[0] != null) mounted[0].close(); thread.setUncaughtExceptionHandler(old); release(context); }
        });
    }
    @Test void synchronousNormalizationDefersWritesAndLatestEditWinsWithoutDuplicateEvents() {
        var failures = new ArrayList<Throwable>(); var events = new ArrayList<UiEvent>();
        KitView[] mounted = new KitView[1]; KitContext[] contexts = new KitContext[1];
        Thread.UncaughtExceptionHandler[] handlers = new Thread.UncaughtExceptionHandler[1];
        Edt.run(() -> {
            contexts[0] = KitFixtures.context(); handlers[0] = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((t, failure) -> failures.add(failure));
            mounted[0] = new KitView(contexts[0], stack(field("a", FieldKind.TEXT, "")), event -> {
                events.add(event);
                if (event instanceof UiEvent.Edited edit) mounted[0].update(stack(field("a", FieldKind.TEXT, edit.text().toUpperCase(java.util.Locale.ROOT))));
            });
            var input = editor(mounted[0], "a"); input.replaceSelection("a"); input.replaceSelection("b");
        });
        Edt.run(() -> {
            try {
                assertThat(failures).isEmpty(); assertThat(editor(mounted[0], "a").getText()).isEqualTo("AB");
                assertThat(((Field)((Stack)mounted[0].spec().root()).children().getFirst()).value()).isEqualTo("AB");
                assertThat(events).containsExactly(new UiEvent.Edited("a", "a"), new UiEvent.Edited("a", "ab"));
            } finally { mounted[0].close(); Thread.currentThread().setUncaughtExceptionHandler(handlers[0]); release(contexts[0]); }
        });
    }
    @Test void callbackNormalizationCanRestoreAnUnchangedDescriptorValue() {
        KitView[] mounted = new KitView[1]; KitContext[] contexts = new KitContext[1]; var events = new ArrayList<UiEvent>();
        Edt.run(() -> {
            contexts[0] = KitFixtures.context(); mounted[0] = new KitView(contexts[0], stack(field("a", FieldKind.TEXT, "A")), event -> {
                events.add(event);
                if (event instanceof UiEvent.Edited edit) mounted[0].update(stack(field("a", FieldKind.TEXT, edit.text().strip())));
            });
            var input = editor(mounted[0], "a"); input.setCaretPosition(1); input.replaceSelection(" ");
        });
        Edt.run(() -> {
            try {
                assertThat(editor(mounted[0], "a").getText()).isEqualTo("A");
                assertThat(events).containsExactly(new UiEvent.Edited("a", "A "));
            } finally { mounted[0].close(); release(contexts[0]); }
        });
    }
    @Test void closingFieldCancelsPendingNormalizationWrite() {
        KitView[] mounted = new KitView[1]; KitContext[] contexts = new KitContext[1]; JTextComponent[] inputs = new JTextComponent[1];
        var failures = new ArrayList<Throwable>(); Thread.UncaughtExceptionHandler[] handlers = new Thread.UncaughtExceptionHandler[1];
        Edt.run(() -> {
            contexts[0] = KitFixtures.context(); handlers[0] = Thread.currentThread().getUncaughtExceptionHandler();
            Thread.currentThread().setUncaughtExceptionHandler((t, failure) -> failures.add(failure));
            mounted[0] = new KitView(contexts[0], stack(field("a", FieldKind.TEXT, "")), event -> {
                if (event instanceof UiEvent.Edited edit) mounted[0].update(stack(field("a", FieldKind.TEXT, edit.text().toUpperCase(java.util.Locale.ROOT))));
            });
            inputs[0] = editor(mounted[0], "a"); inputs[0].replaceSelection("a"); mounted[0].close();
        });
        Edt.run(() -> {
            try { assertThat(failures).isEmpty(); assertThat(inputs[0].getText()).isEqualTo("a"); }
            finally { Thread.currentThread().setUncaughtExceptionHandler(handlers[0]); release(contexts[0]); }
        });
    }
    @Test void allFieldKindsHaveAssociatedLabelsHintAndEditableInput() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            Node[] nodes = java.util.Arrays.stream(FieldKind.values()).map(k -> (Node)field(k.name(), k, k == FieldKind.PORT || k == FieldKind.NUMBER ? "22" : "abc")).toArray(Node[]::new);
            try (var view = new KitView(context, stack(nodes), events::add)) {
                for (FieldKind kind : FieldKind.values()) {
                    var input = editor(view, kind.name());
                    assertThat(input.getAccessibleContext().getAccessibleName()).isEqualTo("Name");
                    assertThat(find(view, kind.name() + ".label", JLabel.class).getLabelFor()).isSameAs(input);
                    assertThat(input.getAccessibleContext().getAccessibleDescription()).isEqualTo(kind == FieldKind.NUMBER ? "Example. From 0 to 100" : "Example");
                    assertThat(input.getClientProperty("JTextField.placeholderText")).as("kit owns hint paint").isNull();
                }
                assertThat(editor(view, "TEXTAREA")).isInstanceOf(JTextArea.class);
                assertThat(editor(view, "PATH").getFont().getFamily()).isEqualTo(Font.MONOSPACED);
                assertThat(find(view, "SEARCH.leading", JLabel.class).getIcon()).isNotNull();
                assertThat(find(view, "PORT.leading", JLabel.class).getText()).isEqualTo(":");
                editor(view, "TEXT").setText("edited"); assertThat(events).contains(new UiEvent.Edited("TEXT", "edited"));
            } finally { release(context); }
        });
    }
    @Test void portRejectsNonnumericReplaceWithoutLosingValidInputAndLeavesRangeToDomain() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(field("p", FieldKind.PORT, "22")), events::add)) {
                var input = editor(view, "p"); input.selectAll(); input.replaceSelection("invalid");
                assertThat(input.getText()).isEqualTo("22"); assertThat(events).isEmpty();
                input.selectAll(); input.replaceSelection("999999"); assertThat(input.getText()).isEqualTo("999999");
                assertThat(events).contains(new UiEvent.Edited("p", "999999"));
            } finally { release(context); }
        });
    }
    @Test void pathBrowseUsesActionAndValidationIsInlineLiteralData() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var node = new Field("p", new ControlState(true, true, false, "<html>Invalid path"), FieldKind.PATH, "~/.ssh", "Optional", "Path", "browse-path");
            try (var view = new KitView(context, stack(node), events::add)) {
                find(view, "p.browse", JButton.class).doClick(0); assertThat(events).containsExactly(new UiEvent.Action("browse-path"));
                var error = find(view, "p.error", JLabel.class); assertThat(error.isVisible()).isTrue();
                assertThat(error.getText()).isEqualTo("<html>Invalid path"); assertThat(error.getClientProperty("html")).isNull();
                view.update(stack(new Field("p", ControlState.READY, FieldKind.PATH, "~/.ssh", "Optional", "Path", "browse-path")));
                assertThat(error.isVisible()).isFalse();
            } finally { release(context); }
        });
    }
    @Test void browseIsNamedForItsFieldAcrossUpdatesAndThemeChanges() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Field("p", ControlState.READY, FieldKind.PATH, "", "", "Private key path", "browse-path")), e -> {})) {
                var browse = find(view, "p.browse", JButton.class);
                assertThat(browse.getText()).isEqualTo("Browse\u2026");
                assertThat(browse.getAccessibleContext().getAccessibleName()).isEqualTo("Browse for Private key path");
                view.update(stack(new Field("p", ControlState.READY, FieldKind.PATH, "", "", "Public key path", "browse-path")));
                assertThat(browse.getAccessibleContext().getAccessibleName()).isEqualTo("Browse for Public key path");
                context.binding().publish(new dev.jasper.ui.theme.ThemePair(KitFixtures.custom(), KitFixtures.custom()));
                assertThat(browse.getAccessibleContext().getAccessibleName()).isEqualTo("Browse for Public key path");
            } finally { release(context); }
        });
    }
    @Test void parentAvailabilityAlsoDisablesInlineBrowseAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(new Stack("root", new ControlState(true, false, false, ""), LayoutRole.VERTICAL,
                java.util.List.of(field("p", FieldKind.PATH, ""))));
            try (var view = new KitView(context, spec, events::add)) {
                var browse = find(view, "p.browse", JButton.class); assertThat(browse.isEnabled()).isFalse(); browse.doClick(0);
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }
    @Test void multilineViewportAndDocumentSurviveUnrelatedUpdates() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXTAREA, "line\n".repeat(30))), e -> {})) {
                var holder = find(view, "a", JComponent.class); var scroll = first(holder, JScrollPane.class);
                assertThat(scroll).isNotNull(); var input = editor(view, "a"); var document = input.getDocument();
                view.setSize(240, 160); layout(view); scroll.getViewport().setViewPosition(new Point(0, 50));
                var position = scroll.getViewport().getViewPosition();
                view.update(stack(field("a", FieldKind.TEXTAREA, "line\n".repeat(30)), field("b", FieldKind.TEXT, "")));
                assertThat(first(holder, JScrollPane.class)).isSameAs(scroll);
                assertThat(input.getDocument()).isSameAs(document); assertThat(scroll.getViewport().getViewPosition()).isEqualTo(position);
            } finally { release(context); }
        });
    }
    static void layout(Container component) {
        component.doLayout(); for (Component child : component.getComponents()) if (child instanceof Container container) layout(container);
    }
    @Test void multilinePlaceholderFitsInsideInputPadding() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(field("a", FieldKind.TEXTAREA, "")), e -> {})) {
                view.setSize(240, 160); layout(view);
                var surface = (TextFields.FieldSurface)editor(view, "a").getParent().getParent().getParent();
                var image = ButtonsTest.paint(surface);
                int placeholder = context.theme().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PLACEHOLDER).getRGB();
                int left = image.getWidth();
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == placeholder) left = Math.min(left, x);
                assertThat(left).isLessThan(image.getWidth()).isGreaterThanOrEqualTo(surface.getInsets().left);
            } finally { release(context); }
        });
    }
    @Test void disabledOrBusyFieldsAndBrowseCannotEmitEditsOrActions() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, stack(new Field("p", new ControlState(true, false, false, ""), FieldKind.PATH, "", "", "Path", "browse")), events::add)) {
                var input = editor(view, "p"); assertThat(input.isEnabled()).isFalse(); input.setText("programmatic");
                find(view, "p.browse", JButton.class).doClick(0); assertThat(events).isEmpty();
                view.update(stack(new Field("p", new ControlState(true, true, true, ""), FieldKind.PATH, "", "", "Path", "browse")));
                assertThat(input.isEnabled()).isFalse();
            } finally { release(context); }
        });
    }
}
