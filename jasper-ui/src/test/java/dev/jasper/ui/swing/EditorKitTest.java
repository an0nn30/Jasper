package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 2 Task 9-kit: what the Editor's chrome needs from the kit. A DOCUMENT split whose specialized document fills the
 * height, field Shift+Enter and Escape, an Escape covering an inline bar, a field driving a list with its arrow keys,
 * pressed option buttons, a command line's caret and severity text, and a view re-routed to the workspace slot over an
 * opaque surface. The last tests prove earlier kit consumers keep their behaviour.
 */
@SuppressWarnings("try")
class EditorKitTest {
    static final ControlState READY = ControlState.READY;
    static final ControlState HIDDEN = new ControlState(false, true, false, "");

    static KeyEvent key(Component on, int code, int modifiers) {
        return new KeyEvent(on, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED);
    }

    static Field findField(String alternate, String cancel) {
        return new Field("find", READY, FieldKind.SEARCH, "needle", "Find", "Find", "next", Optional.empty(), FieldPresentation.EMBEDDED, alternate, cancel, "");
    }

    static Nodes.Button option(String id, String label, Optional<Boolean> selected) {
        return new Nodes.Button(id, READY, label, "", ButtonKind.GHOST, id, label, ButtonPresentation.STANDARD, Optional.empty(), selected);
    }

    static ViewSpec bar(Field field, String stackCancel) {
        return new ViewSpec(new Stack("bar", READY, LayoutRole.INLINE, List.of(field, option("next-button", "Next", Optional.empty())), stackCancel));
    }

    @Test void shiftEnterInAFieldRunsItsAlternateActionAndPassesOnWithoutOne() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, bar(findField("previous", ""), ""), events::add)) {
                var input = editor(view, "find");
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("previous"), new UiEvent.Action("next"));
                events.clear();
                view.update(bar(findField("", ""), ""));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK))).as("blank passes Shift+Enter on").isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void escapeRunsTheFieldsCancelFirstThenTheEnclosingStacksFromAnyControlInside() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, bar(findField("", "field-close"), "bar-close"), events::add)) {
                var input = editor(view, "find");
                var next = find(view, "next-button", Buttons.KitButton.class);
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).as("a button inside the bar").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("field-close"), new UiEvent.Action("bar-close"));
                events.clear();
                view.update(bar(findField("", ""), "bar-close"));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).as("a field without one passes to the bar").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("bar-close"));
                events.clear();
                view.update(bar(findField("", ""), ""));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ESCAPE, 0))).as("nothing takes Escape").isFalse();
                assertThat(SwingUtilities.processKeyBindings(key(next, KeyEvent.VK_ESCAPE, 0))).isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void aDisabledStackDoesNotCloseOnEscape() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var disabled = new ViewSpec(new Stack("bar", new ControlState(true, false, false, ""), LayoutRole.INLINE, List.of(findField("", "")), "bar-close"));
            try (var view = new KitView(context, disabled, events::add)) {
                SwingUtilities.processKeyBindings(key(editor(view, "find"), KeyEvent.VK_ESCAPE, 0));
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    static ListItem language(String id, String name) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".name", READY, name, TextRole.BODY)), List.of(), List.of());
    }

    static ViewSpec picker(Set<String> selected, String listId) {
        var items = new ArrayList<ListItem>();
        for (int i = 0; i < 40; i++) items.add(language("l" + i, "Language " + i));
        var filter = new Field("filter", READY, FieldKind.SEARCH, "", "Filter", "Filter", "choose", Optional.empty(), FieldPresentation.STANDARD, "", "", listId);
        return new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(filter,
            new ItemList("languages", READY, ListKind.SINGLE, items, selected, "choose", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(10)))));
    }

    @Test void arrowKeysInAFieldMoveItsListsSelectionAndLeaveTheFocusInTheField() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, picker(Set.of("l0"), "languages"), events::add)) {
                view.setSize(400, 600); InteractiveRowsTest.layout(view);
                var input = editor(view, "filter");
                var list = (ItemLists.ListControl) view.control("languages");
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isTrue();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_UP, 0))).isTrue();
                assertThat(events).containsExactly(new UiEvent.Selected("languages", Set.of("l1")), new UiEvent.Selected("languages", Set.of("l2")),
                    new UiEvent.Selected("languages", Set.of("l1")));
                events.clear();
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_PAGE_DOWN, 0))).isTrue();
                int page = list.page();
                assertThat(page).isGreaterThan(1);
                assertThat(events).containsExactly(new UiEvent.Selected("languages", Set.of("l" + (1 + page))));
                events.clear();
                for (int i = 0; i < 10; i++) SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_PAGE_UP, 0));
                assertThat(events.getLast()).as("clamped at the first row").isEqualTo(new UiEvent.Selected("languages", Set.of("l0")));
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_ENTER, 0))).isTrue();
                assertThat(events.getLast()).as("Enter stays the field's own action").isEqualTo(new UiEvent.Action("choose"));
            } finally { release(context); }
        });
    }

    @Test void withoutAListTheFieldLeavesArrowKeysAlone() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, picker(Set.of("l0"), ""), events::add)) {
                var input = editor(view, "filter");
                assertThat(SwingUtilities.processKeyBindings(key(input, KeyEvent.VK_DOWN, 0))).isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    @Test void anOptionButtonShowsItsPressedStateReportsItToAssistiveTechnologyAndNeverFlipsByItself() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(option("case", "Aa", Optional.of(true)), option("word", "W", Optional.of(false)), option("plain", "Next", Optional.empty()))));
            try (var view = new KitView(context, spec, events::add)) {
                var pressed = find(view, "case", Buttons.KitButton.class); var released = find(view, "word", Buttons.KitButton.class); var plain = find(view, "plain", Buttons.KitButton.class);
                assertThat(pressed.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.TOGGLE_BUTTON);
                assertThat(pressed.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)).isTrue();
                assertThat(released.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.TOGGLE_BUTTON);
                assertThat(released.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.CHECKED)).isFalse();
                assertThat(plain.getAccessibleContext().getAccessibleRole()).isEqualTo(AccessibleRole.PUSH_BUTTON);
                released.doClick();
                assertThat(events).containsExactly(new UiEvent.Action("word"));
                assertThat(released.isSelected()).as("the owner confirms the new state").isFalse();
                assertThat(colourAt(released, 40, 30)).as("an unpressed ghost button has no fill").isNotEqualTo(colourAt(pressed, 40, 30));
                assertThat(colourAt(pressed, 40, 30)).isEqualTo(context.theme().semantic().color(SemanticColor.SEGMENT_SELECTED_BG).getRGB());
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(option("case", "Aa", Optional.of(false)), option("word", "W", Optional.of(true)), option("plain", "Next", Optional.empty())))));
                assertThat(find(view, "word", Buttons.KitButton.class)).as("same control").isSameAs(released);
                assertThat(released.isSelected()).isTrue(); assertThat(pressed.isSelected()).isFalse();
            } finally { release(context); }
        });
    }

    /** The colour painted at the centre of a lone component of this size, over a transparent backdrop. */
    static int colourAt(JComponent component, int width, int height) {
        component.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(width / 2, 2);
    }

    @Test void severityTextTakesItsColourAndAMonospacedCaretDrawsABarAfterItsCharacters() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                new Text("error", READY, "E492: Not an editor command", TextRole.MONO, Optional.of(Severity.DANGER), OptionalInt.empty()),
                new Text("bell", READY, "-- INSERT --", TextRole.MONO, Optional.of(Severity.INFO), OptionalInt.empty()),
                new Text("line", READY, ":abc", TextRole.MONO, Optional.empty(), OptionalInt.of(2)),
                new Text("plain", READY, ":abc", TextRole.MONO))));
            try (var view = new KitView(context, spec, e -> { })) {
                var semantic = context.theme().semantic();
                assertThat(find(view, "error", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.STATUS_DANGER));
                assertThat(find(view, "bell", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.ACCENT));
                assertThat(find(view, "plain", JLabel.class).getForeground()).isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                var line = find(view, "line", Labels.LiteralLabel.class); var plain = find(view, "plain", Labels.LiteralLabel.class);
                var withCaret = paint(line, 200, 24); var without = paint(plain, 200, 24);
                int x = line.getInsets().left + line.getFontMetrics(line.getFont()).stringWidth(":a");
                assertThat(columnInk(withCaret, x)).as("a bar after two characters").isGreaterThan(columnInk(without, x) + 10);
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                    new Text("error", READY, "E492: Not an editor command", TextRole.MONO),
                    new Text("bell", READY, "-- INSERT --", TextRole.MONO),
                    new Text("line", READY, ":abc", TextRole.MONO),
                    new Text("plain", READY, ":abc", TextRole.MONO)))));
                assertThat(find(view, "error", JLabel.class).getForeground()).as("the tint goes with the severity").isEqualTo(semantic.color(SemanticColor.TEXT_PRIMARY));
                assertThat(columnInk(paint(line, 200, 24), x)).isEqualTo(columnInk(without, x));
            } finally { release(context); }
        });
    }

    static BufferedImage paint(JComponent component, int width, int height) {
        component.setSize(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    /** Opaque pixels in one column, a count of how much ink is there. */
    static int columnInk(BufferedImage image, int x) {
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) if ((image.getRGB(x, y) >>> 24) > 128) ink++;
        return ink;
    }

    /** A document tab: a banner, the document (or its loading state), a find bar and a command line. */
    static ViewSpec documentTab(boolean loading, boolean findOpen) {
        return new ViewSpec(new Split("tab", READY, SplitKind.DOCUMENT, List.of(
            new Text("banner", READY, "notes.txt changed on disk.", TextRole.BODY),
            new Custom("document", loading ? HIDDEN : READY, "text", CustomKind.EDITOR, "Document"),
            new Empty("state", loading ? READY : HIDDEN, new Nodes.Icon("state.icon", READY, "file", Hue.NEUTRAL, IconSize.EMPTY, "File"), "Loading notes.txt…", "", List.of()),
            new Stack("bar", findOpen ? READY : HIDDEN, LayoutRole.INLINE, List.of(findField("previous", "close")), "close"),
            new Text("vim", READY, "-- NORMAL --", TextRole.MONO))));
    }

    @Test void aDocumentSplitGivesTheVisibleDocumentOrStateTheHeightTheBarsLeave() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var text = new JTextArea(String.join("\n", Collections.nCopies(400, "a long document line")));
            var scroll = new JScrollPane(text); int disposed[] = {0};
            try (var view = new KitView(context, documentTab(false, true), e -> { })) {
                view.registerContent("text", scroll, () -> disposed[0]++);
                view.setSize(600, 500); InteractiveRowsTest.layout(view);
                var banner = find(view, "banner", JComponent.class); var vim = find(view, "vim", JComponent.class);
                var document = view.control("document").component; var bar = view.control("bar").component;
                assertThat(banner.getY()).isZero();
                assertThat(document.getY()).isEqualTo(banner.getHeight());
                int bars = banner.getHeight() + bar.getHeight() + vim.getHeight();
                assertThat(document.getHeight()).as("the document fills what the bars leave, not its own preferred height").isEqualTo(500 - bars);
                assertThat(vim.getY() + vim.getHeight()).isEqualTo(500);
                assertThat(scroll.getHeight()).isEqualTo(document.getHeight());

                view.update(documentTab(true, false)); InteractiveRowsTest.layout(view);
                var state = view.control("state").component;
                assertThat(document.isVisible()).isFalse(); assertThat(bar.isVisible()).isFalse();
                assertThat(state.getHeight()).as("the loading state fills instead").isEqualTo(500 - banner.getHeight() - vim.getHeight());
                assertThat(disposed[0]).as("a hidden document keeps its registration").isZero();

                view.update(documentTab(false, false)); InteractiveRowsTest.layout(view);
                assertThat(document.getHeight()).isEqualTo(500 - banner.getHeight() - vim.getHeight());
                assertThat(scroll.getParent()).isNotNull();
            } finally { release(context); }
            assertThat(disposed[0]).isEqualTo(1);
        });
    }

    @Test void theOtherSplitKindsStillPlaceTheirChildrenSideBySide() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Split("split", READY, SplitKind.SIDEBAR_DETAIL, List.of(new Text("left", READY, "Sidebar", TextRole.BODY), new Text("right", READY, "Detail", TextRole.BODY))));
            try (var view = new KitView(context, spec, e -> { })) {
                view.setSize(600, 100); InteractiveRowsTest.layout(view);
                var left = find(view, "left", JComponent.class); var right = find(view, "right", JComponent.class);
                assertThat(left.getY()).isEqualTo(right.getY()); assertThat(right.getX()).isGreaterThan(left.getX() + left.getWidth() - 1);
            } finally { release(context); }
        });
    }

    /** Light chrome and a dark workspace, so a re-route is visible. */
    static KitContext mixed() {
        return new KitContext(new ThemeBinding(new ThemePair(KitFixtures.light(), KitFixtures.dark()), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.CHROME, DesignMetrics.DEFAULT, new IconRegistry(), () -> false);
    }

    @Test void reRoutingAViewRepaintsItsControlsFromTheWorkspaceSlotInPlaceAndLeavesOtherViewsAlone() {
        Edt.run(() -> {
            var context = mixed();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(new Text("title", READY, "Find", TextRole.BODY), findField("previous", "close"))));
            try (var view = new KitView(context, spec, e -> { }); var other = new KitView(context, spec, e -> { })) {
                var title = find(view, "title", JLabel.class); var input = editor(view, "find");
                input.setText("typed"); input.setCaretPosition(2);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(view.route()).isEqualTo(ThemeRoute.CHROME);
                view.route(ThemeRoute.WORKSPACE);
                assertThat(view.route()).isEqualTo(ThemeRoute.WORKSPACE);
                assertThat(find(view, "title", JLabel.class)).as("same control").isSameAs(title);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.dark().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(input.getText()).isEqualTo("typed"); assertThat(input.getCaretPosition()).isEqualTo(2);
                assertThat(other.route()).as("a view sharing the caller's context keeps its slot").isEqualTo(ThemeRoute.CHROME);
                assertThat(find(other, "title", JLabel.class).getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
                assertThat(context.route()).as("the caller's context is unchanged").isEqualTo(ThemeRoute.CHROME);
                view.route(ThemeRoute.CHROME);
                assertThat(title.getForeground()).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }

    @Test void aViewPaintsItsRoutesPanelSurfaceOpaqueOnlyWhenAsked() {
        Edt.run(() -> {
            var context = mixed();
            try (var view = new KitView(context, new ViewSpec(new Text("t", READY, "", TextRole.BODY)), e -> { })) {
                assertThat(view.isOpaque()).isFalse();
                assertThat(paint(view, 40, 40).getRGB(39, 39) >>> 24).as("transparent by default").isZero();
                view.route(ThemeRoute.WORKSPACE); view.paintsSurface(true);
                assertThat(view.isOpaque()).isTrue();
                assertThat(paint(view, 40, 40).getRGB(39, 39)).isEqualTo(KitFixtures.dark().semantic().color(SemanticColor.SURFACE_PANEL).getRGB());
                view.paintsSurface(false);
                assertThat(view.isOpaque()).isFalse();
            } finally { release(context); }
        });
    }

    @Test void aThemePublishAfterReRoutingFollowsTheWorkspaceSlot() {
        Edt.run(() -> {
            var context = mixed();
            try (var view = new KitView(context, new ViewSpec(new Text("t", READY, "x", TextRole.BODY)), e -> { })) {
                view.route(ThemeRoute.WORKSPACE);
                context.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.custom()));
                assertThat(find(view, "t", JLabel.class).getForeground()).isEqualTo(KitFixtures.custom().semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }
}
