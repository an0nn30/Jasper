package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

/**
 * Plan 2 Task 9-kit fix round 1 (review I1): with the real look and feel (FlatLaf) installed, a kit key binding whose
 * action is blank must never hide the look and feel's own binding for the same key. An entry in a component's own input
 * map, even a disabled one, stops Swing from consulting the look and feel's parent map, so the kit installs such keys
 * only while they have an action, or binds keys the look and feel leaves free. Each case presses a key on a kit control
 * and on a plain Swing control of the same class in the same look and feel, and expects the same outcome.
 */
@SuppressWarnings("try")
class KitKeyBindingAuditTest {
    static final ControlState READY = ControlState.READY;
    static final String TEXT = "hello world";

    /** Runs the body on the EDT with FlatLaf installed, restoring the prior look and feel afterwards. */
    static void withFlatLaf(Runnable body) {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(KitFixtures.dark());
                body.run();
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    static KeyEvent key(Component on, int code, int modifiers) {
        return new KeyEvent(on, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    /** What a key does to a text component: whether a binding took it, and the text and caret afterwards. */
    static String outcome(JTextComponent input, int code, int modifiers) {
        // Line-end moves need a sized text view, as a shown field has.
        input.setSize(240, input instanceof JTextArea ? 80 : 28);
        input.setText(TEXT); input.setCaretPosition(5);
        boolean consumed = SwingUtilities.processKeyBindings(key(input, code, modifiers));
        return consumed + "|" + input.getText() + "|" + input.getCaretPosition();
    }

    static final int[][] KEYS = {
        {KeyEvent.VK_ENTER, 0}, {KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK}, {KeyEvent.VK_ESCAPE, 0},
        {KeyEvent.VK_UP, 0}, {KeyEvent.VK_DOWN, 0}, {KeyEvent.VK_KP_UP, 0}, {KeyEvent.VK_KP_DOWN, 0},
        {KeyEvent.VK_PAGE_UP, 0}, {KeyEvent.VK_PAGE_DOWN, 0}, {KeyEvent.VK_DELETE, 0}, {KeyEvent.VK_BACK_SPACE, 0}};

    static Field blank(String id, FieldKind kind) {
        return new Field(id, READY, kind, TEXT, "", "Label " + id, "", Optional.empty(), FieldPresentation.STANDARD, "", "", "");
    }

    @Test void downAndUpInAFieldWithoutAListMoveTheCaretAsTheLookAndFeelDoes() {
        withFlatLaf(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(blank("find", FieldKind.SEARCH)), "")), e -> { })) {
                var input = editor(view, "find"); var plain = new JTextField();
                assertThat(outcome(input, KeyEvent.VK_DOWN, 0)).isEqualTo(outcome(plain, KeyEvent.VK_DOWN, 0));
                assertThat(outcome(input, KeyEvent.VK_UP, 0)).isEqualTo(outcome(plain, KeyEvent.VK_UP, 0));
                if (SystemInfo.isMacOS) {
                    assertThat(outcome(input, KeyEvent.VK_DOWN, 0)).as("FlatLaf's macOS Down goes to the end").isEqualTo("true|" + TEXT + "|" + TEXT.length());
                    assertThat(outcome(input, KeyEvent.VK_UP, 0)).as("and Up to the start").isEqualTo("true|" + TEXT + "|0");
                }
            } finally { release(context); }
        });
    }

    static ViewSpec picker(String listId) {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < 5; i++) items.add(new ListItem("l" + i, Optional.empty(), List.of(new Text("l" + i + ".name", READY, "Language " + i, TextRole.BODY)), List.of(), List.of()));
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", listId);
        return new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(filter,
            new ItemList("languages", READY, ListKind.SINGLE, items, Set.of("l0"), "choose", List.of()))));
    }

    @Test void aFieldNamingAListMovesItAndGivesTheKeysBackWhenTheListIdGoes() {
        withFlatLaf(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, picker("languages"), events::add)) {
                view.setSize(300, 300); InteractiveRowsTest.layout(view);
                var input = editor(view, "filter"); input.setText("ja"); input.setCaretPosition(1); events.clear();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(events).containsExactly(new UiEvent.Selected("languages", Set.of("l1")));
                assertThat(input.getCaretPosition()).as("the list moved, not the caret").isEqualTo(1);
                view.update(picker(""));
                var plain = new JTextField();
                assertThat(outcome(input, KeyEvent.VK_DOWN, 0)).as("without a list the look and feel's Down is back").isEqualTo(outcome(plain, KeyEvent.VK_DOWN, 0));
                view.update(picker("languages")); events.clear();
                input.setCaretPosition(1);
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(events).as("and the list keys return with it").hasSize(1);
            } finally { release(context); }
        });
    }

    static ViewSpec picker(ControlState listState, ControlState holderState) {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < 5; i++) items.add(new ListItem("l" + i, Optional.empty(), List.of(new Text("l" + i + ".name", READY, "Language " + i, TextRole.BODY)), List.of(), List.of()));
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", "languages");
        var list = new ItemList("languages", listState, ListKind.SINGLE, items, Set.of("l0"), "choose", List.of());
        return new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(filter, new Stack("holder", holderState, LayoutRole.VERTICAL, List.of(list)))));
    }

    /**
     * Plan 2 Task 9a carried fix: a field that names a list which is hidden, disabled, busy or inside a hidden container
     * gives every list key to the look and feel, exactly as a plain field does, and takes them back when the list returns.
     */
    @Test void aFieldWhoseListIsHiddenDisabledOrUnmountedLeavesEveryListKeyToTheLookAndFeel() {
        withFlatLaf(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var hidden = new ControlState(false, true, false, ""); var disabled = new ControlState(true, false, false, ""); var busy = new ControlState(true, true, true, "");
            try (var view = new KitView(context, picker(READY, READY), events::add)) {
                view.setSize(300, 300); InteractiveRowsTest.layout(view);
                var input = editor(view, "filter");
                var states = new LinkedHashMap<String, ViewSpec>();
                var active = (Stack) picker(READY, READY).root();
                var holder = (Stack) active.children().get(1);
                var list = (ItemList) holder.children().getFirst();
                var passive = new ItemList(list.id(), READY, list.kind(), list.items(), Set.of(), list.activateAction(), list.contextActions(),
                    SelectionMode.NONE, list.addAction(), list.visibleRows());
                states.put("passive list", new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(active.children().getFirst(), passive))));
                states.put("hidden list", picker(hidden, READY));
                states.put("disabled list", picker(disabled, READY));
                states.put("busy list", picker(busy, READY));
                states.put("list in a hidden container", picker(READY, hidden));
                for (var state : states.entrySet()) {
                    view.update(state.getValue()); InteractiveRowsTest.layout(view); events.clear();
                    for (int code : new int[] {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_KP_UP, KeyEvent.VK_KP_DOWN, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN}) {
                        var plain = new JTextField();
                        assertThat(outcome(input, code, 0)).as("%s: %s as a plain field", state.getKey(), KeyEvent.getKeyText(code))
                            .isEqualTo(outcome(plain, code, 0));
                    }
                    assertThat(events).as("%s: the list never moved", state.getKey()).noneMatch(UiEvent.Selected.class::isInstance);
                    var own = input.getInputMap(JComponent.WHEN_FOCUSED);
                    for (KeyStroke stroke : own.keys()) {
                        if (own.getParent().get(stroke) == null) continue;
                        if ("notify-field-accept".equals(own.getParent().get(stroke))) continue;
                        var action = input.getActionMap().get(own.get(stroke));
                        assertThat(action != null && action.isEnabled()).as("%s: %s is live, not a dead shadow", state.getKey(), stroke).isTrue();
                    }
                }
                view.update(picker(READY, READY)); InteractiveRowsTest.layout(view); events.clear();
                input.setCaretPosition(0);
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(events).as("the list keys come back with the list").containsExactly(new UiEvent.Selected("languages", Set.of("l1")));
            } finally { release(context); }
        });
    }

    @Test void noBlankKitKeyHidesALookAndFeelBindingOnAnyFieldKindOrSecret() {
        withFlatLaf(() -> {
            var context = KitFixtures.context();
            var kinds = List.of(FieldKind.TEXT, FieldKind.SEARCH, FieldKind.PATH, FieldKind.PORT, FieldKind.TEXTAREA);
            var nodes = new ArrayList<Node>();
            for (var kind : kinds) nodes.add(blank("f-" + kind, kind));
            nodes.add(new Secret("secret", READY, "", "Password", "", SecretKind.INPUT, "", ""));
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, nodes, "")), e -> { })) {
                for (var kind : kinds) {
                    var input = editor(view, "f-" + kind);
                    JTextComponent plain = kind == FieldKind.TEXTAREA ? new JTextArea() : new JTextField();
                    for (int[] k : KEYS) {
                        // A PORT field only admits digits; compare on text both accept.
                        if (kind == FieldKind.PORT) continue;
                        // Page keys in a multiline field scroll its viewport, whose geometry differs unshown; the
                        // structural audit below covers them.
                        if (kind == FieldKind.TEXTAREA && (k[0] == KeyEvent.VK_PAGE_UP || k[0] == KeyEvent.VK_PAGE_DOWN)) continue;
                        assertThat(outcome(input, k[0], k[1])).as("%s %s", kind, KeyEvent.getKeyText(k[0]) + (k[1] != 0 ? "+shift" : ""))
                            .isEqualTo(outcome(plain, k[0], k[1]));
                    }
                }
                var port = editor(view, "f-PORT"); var plainPort = new JTextField();
                for (int[] k : KEYS) {
                    port.setSize(240, 28); plainPort.setSize(240, 28);
                    port.setText("8080"); port.setCaretPosition(2); plainPort.setText("8080"); plainPort.setCaretPosition(2);
                    boolean a = SwingUtilities.processKeyBindings(key(port, k[0], k[1])), b = SwingUtilities.processKeyBindings(key(plainPort, k[0], k[1]));
                    assertThat(a + "|" + port.getText() + "|" + port.getCaretPosition()).as("PORT %s", KeyEvent.getKeyText(k[0]))
                        .isEqualTo(b + "|" + plainPort.getText() + "|" + plainPort.getCaretPosition());
                }
                var secret = first(find(view, "secret", JComponent.class), JPasswordField.class);
                var plainSecret = new JPasswordField(); secret.setSize(240, 28); plainSecret.setSize(240, 28);
                for (int[] k : KEYS) {
                    boolean a = SwingUtilities.processKeyBindings(key(secret, k[0], k[1])), b = SwingUtilities.processKeyBindings(key(plainSecret, k[0], k[1]));
                    assertThat(a).as("secret %s", KeyEvent.getKeyText(k[0])).isEqualTo(b);
                }
            } finally { release(context); }
        });
    }

    @Test void rowsAndStacksWithoutActionsPassTheirKeysOnAsAPlainPanelDoes() {
        withFlatLaf(() -> {
            var context = KitFixtures.context();
            var items = List.of(new ListItem("a", Optional.empty(), List.of(new Text("a.name", READY, "A", TextRole.BODY)), List.of(), List.of()));
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                new ItemList("rows", READY, ListKind.SINGLE, items, Set.of("a"), "", List.of()),
                new Nodes.Button("button", READY, "Next", "", ButtonKind.SECONDARY, "next", "")), ""));
            try (var view = new KitView(context, spec, e -> { })) {
                var rows = (ItemLists.ListControl) view.control("rows"); rows.focusItem = "a";
                var plain = new JPanel();
                for (int code : new int[] {KeyEvent.VK_ENTER, KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE, KeyEvent.VK_ESCAPE})
                    assertThat(SwingUtilities.processKeyBindings(key(rows.canvas, code, 0))).as("row %s", KeyEvent.getKeyText(code))
                        .isEqualTo(SwingUtilities.processKeyBindings(key(plain, code, 0)));
                var button = find(view, "button", Buttons.KitButton.class); var plainButton = new JButton("Next");
                for (int[] k : KEYS)
                    assertThat(SwingUtilities.processKeyBindings(key(button, k[0], k[1]))).as("button in a stack %s", KeyEvent.getKeyText(k[0]))
                        .isEqualTo(SwingUtilities.processKeyBindings(key(plainButton, k[0], k[1])));
            } finally { release(context); }
        });
    }

    /**
     * Structural audit over every kit text input (all field kinds, a NUMBER field and secrets, blank actions): a key in the
     * input's own map that the look and feel also binds hides the look and feel's binding, so the kit's action for it
     * must be live. The only such keys are a NUMBER field's arrows, which step its value by design.
     */
    @Test void everyKitKeyThatShadowsALookAndFeelBindingIsLive() {
        withFlatLaf(() -> {
            var context = KitFixtures.context();
            var nodes = new ArrayList<Node>();
            for (var kind : FieldKind.values()) nodes.add(kind == FieldKind.NUMBER
                ? new Field("f-" + kind, READY, kind, "1", "", "Number", "", Optional.of(new Numeric(0, 9, 1, 0)))
                : blank("f-" + kind, kind));
            for (var kind : SecretKind.values()) nodes.add(new Secret("s-" + kind, READY, "", "Secret " + kind, "", kind, "", ""));
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, nodes, "")), e -> { })) {
                var shadowing = new ArrayList<String>();
                var inputs = new ArrayList<JTextComponent>();
                collect(view, inputs);
                assertThat(inputs).as("every field kind and at least the input secret").hasSizeGreaterThanOrEqualTo(FieldKind.values().length + 1);
                for (var input : inputs) {
                    var own = input.getInputMap(JComponent.WHEN_FOCUSED);
                    var laf = own.getParent();
                    if (own.keys() == null || laf == null) continue;
                    for (KeyStroke stroke : own.keys()) {
                        if (laf.get(stroke) == null) continue;
                        // Swing's field-accept fires a field's ActionListeners and is inert without one; kit inputs add none.
                        if ("notify-field-accept".equals(laf.get(stroke)) && input instanceof JTextField field && field.getActionListeners().length == 0) continue;
                        var action = input.getActionMap().get(own.get(stroke));
                        if (action == null || !action.isEnabled()) shadowing.add(input.getClass().getSimpleName() + " " + stroke);
                        else if (!String.valueOf(own.get(stroke)).startsWith("number.step.")) shadowing.add("live non-number " + input.getClass().getSimpleName() + " " + stroke);
                    }
                }
                assertThat(shadowing).as("own-map keys hiding a look and feel binding").isEmpty();
            } finally { release(context); }
        });
    }

    static void collect(java.awt.Component component, List<JTextComponent> into) {
        if (component instanceof JTextComponent text) into.add(text);
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) collect(child, into);
    }

    /**
     * The same structural audit over every component of every kit gallery fixture (all node kinds and states), in both
     * focus conditions the kit binds: a kit key in a component's own map that its look and feel also binds must be live.
     */
    @Test void noGalleryControlHidesALookAndFeelBindingWithADeadKitKey() {
        withFlatLaf(() -> {
            var base = KitFixtures.context();
            var findings = new java.util.TreeSet<String>();
            try (var icons = dev.jasper.ui.gallery.KitGallery.icons()) {
                var context = new KitContext(base.binding(), dev.jasper.ui.theme.ThemeRoute.WORKSPACE, dev.jasper.ui.metrics.DesignMetrics.DEFAULT, icons, () -> false);
                for (var fixture : dev.jasper.ui.gallery.KitGallery.fixtures()) {
                    try (var view = new KitView(context, fixture.spec(), e -> { })) {
                        view.setSize(fixture.width(), fixture.height()); InteractiveRowsTest.layout(view);
                        var all = new ArrayList<JComponent>(); components(view, all);
                        for (var component : all) for (int condition : new int[] {JComponent.WHEN_FOCUSED, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}) {
                            var own = component.getInputMap(condition); var laf = own.getParent();
                            if (own.keys() == null || laf == null) continue;
                            for (KeyStroke stroke : own.keys()) {
                                Object lafName = laf.get(stroke);
                                if (lafName == null) continue;
                                if ("notify-field-accept".equals(lafName) && component instanceof JTextField field && field.getActionListeners().length == 0) continue;
                                var action = component.getActionMap().get(own.get(stroke));
                                if (action != null && action.isEnabled() && String.valueOf(own.get(stroke)).startsWith("number.step.")) continue;
                                if (action == null || !action.isEnabled()) findings.add(fixture.name() + ": " + component.getClass().getSimpleName() + " " + stroke + " hides " + lafName);
                            }
                        }
                    }
                }
            } finally { release(base); }
            assertThat(findings).isEmpty();
        });
    }

    static void components(java.awt.Component component, List<JComponent> into) {
        if (component instanceof JComponent owned) into.add(owned);
        if (component instanceof java.awt.Container container) for (var child : container.getComponents()) components(child, into);
    }
}
