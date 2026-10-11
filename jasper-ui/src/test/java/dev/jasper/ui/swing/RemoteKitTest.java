package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.model.Nodes.Stack;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/**
 * Plan 2 Task 8-kit: what the Remote panels need from the kit. Row tooltips on item lists and tables, plain left clicks
 * on list rows, a list Enter action apart from activation, a Delete action, the star glyphs and an operable pending
 * switch. The last tests prove the earlier list consumers keep their behaviour.
 */
@SuppressWarnings("try")
class RemoteKitTest {
    static final ControlState READY = ControlState.READY;

    /** A tunnel-like row: a status dot, a title, a mapping, a star button and a switch. */
    static ListItem tunnel(String id, String tooltip, boolean pending) {
        return new ListItem(id, Optional.of(new StatusDot(id + ".dot", READY, StatusKind.SUCCESS)),
            List.of(new Text(id + ".name", READY, "Tunnel " + id, TextRole.BODY), new Text(id + ".via", READY, "via box", TextRole.DESCRIPTION)),
            List.of(new Text(id + ".mapping", READY, "8080 → localhost:80", TextRole.MONO_SMALL)),
            List.of(new Nodes.Button(id + ".star", READY, "", "star", ButtonKind.GHOST, "favorite:" + id, "Add to favorites", ButtonPresentation.EMBEDDED),
                new Toggle(id + ".run", READY, "Start tunnel", false, "toggle:" + id, true, "", pending)),
            false, Optional.empty(), tooltip);
    }

    static ItemList tunnels(List<ListItem> items, Set<String> selected, String enter, String delete) {
        return new ItemList("tunnels", READY, ListKind.DOUBLE, items, selected, "edit", List.of(), SelectionMode.SINGLE, Optional.empty(),
            OptionalInt.empty(), enter, delete);
    }

    static ListItem heading(String id) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".title", READY, "Quick forwards", TextRole.CAPTION)), List.of(), List.of(), true);
    }

    static void key(JComponent on, String stroke) {
        var action = on.getActionMap().get(on.getInputMap().get(KeyStroke.getKeyStroke(stroke)));
        assertThat(action).as("binding for %s", stroke).isNotNull();
        if (action.isEnabled()) action.actionPerformed(null);
    }

    static boolean bound(JComponent on, String stroke) {
        var name = on.getInputMap().get(KeyStroke.getKeyStroke(stroke));
        var action = name == null ? null : on.getActionMap().get(name);
        return action != null && action.isEnabled();
    }

    @Test void listRowsShowTheirTooltipOnTheRowAndItsPassiveContent() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(tunnels(List.of(tunnel("a", "web, 8080 → localhost:80, up 3 min, 12 kB", false), tunnel("b", "", false)), Set.of(), "", ""));
            try (var view = new KitView(context, spec, e -> {})) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                var row = owner.mounted.get("a");
                assertThat(row.getToolTipText()).isEqualTo("web, 8080 → localhost:80, up 3 min, 12 kB");
                assertThat(row.getAccessibleContext().getAccessibleDescription()).isEqualTo("web, 8080 → localhost:80, up 3 min, 12 kB");
                for (String part : List.of("a.name", "a.via", "a.mapping", "a.dot"))
                    assertThat(row.controls.get(part).component.getToolTipText()).as(part).isEqualTo(row.getToolTipText());
                assertThat(row.controls.get("a.star").component.getToolTipText()).as("a button keeps its own tooltip").isEqualTo("Add to favorites");
                assertThat(owner.mounted.get("b").getToolTipText()).as("blank is none").isNull();
                view.update(new ViewSpec(tunnels(List.of(tunnel("a", "up 4 min", false), tunnel("b", "", false)), Set.of(), "", "")));
                InteractiveRowsTest.layout(view);
                assertThat(owner.mounted.get("a").getToolTipText()).as("live text follows the descriptor").isEqualTo("up 4 min");
                assertThat(owner.mounted.get("a").controls.get("a.name").component.getToolTipText()).isEqualTo("up 4 min");
            } finally { release(context); }
            assertThatThrownBy(() -> new ListItem("h", Optional.empty(), List.of(new Text("h.t", READY, "H", TextRole.CAPTION)), List.of(), List.of(), true, Optional.empty(), "tip"))
                .as("a heading is never hovered, so it has no tooltip").isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test void tableRowsShowTheirTooltipOnEveryCell() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var table = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false), new Column("size", "Size", TextRole.DESCRIPTION, true)),
                List.of(new TableRow("notes", Map.of("name", "notes.txt", "size", "6 B"), Optional.of("file"), false, Optional.empty(), "notes.txt — 6 B — 2026-10-05 09:12"),
                    new TableRow("plain", Map.of("name", "plain"), Optional.empty())), Set.of(), "open", List.of());
            try (var view = new KitView(context, new ViewSpec(table), e -> {})) {
                view.setSize(500, 240); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                var row = owner.mounted.get("notes");
                assertThat(row.getToolTipText()).isEqualTo("notes.txt — 6 B — 2026-10-05 09:12");
                assertThat(row.getAccessibleContext().getAccessibleDescription()).isEqualTo("notes.txt — 6 B — 2026-10-05 09:12");
                for (var cell : row.cells) assertThat(cell.getToolTipText()).isEqualTo("notes.txt — 6 B — 2026-10-05 09:12");
                assertThat(owner.mounted.get("plain").getToolTipText()).isNull();
                for (var cell : owner.mounted.get("plain").cells) assertThat(cell.getToolTipText()).isNull();
            } finally { release(context); }
        });
    }

    @Test void aPlainLeftClickOnAListRowReportsClickedAfterSelectedButHeadingsKeysAndModifiersDoNot() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(tunnels(List.of(tunnel("a", "", false), heading("quick"), tunnel("b", "", false)), Set.of(), "", ""));
            try (var view = new KitView(context, spec, events::add)) {
                view.setSize(420, 300); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                var row = owner.mounted.get("a");
                for (var listener : row.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(row, 0));
                assertThat(events).containsExactly(new UiEvent.Selected("tunnels", Set.of("a")), new UiEvent.Clicked("tunnels", "a"));
                events.clear();
                var title = row.controls.get("a.name").component;
                for (var listener : title.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(title, 0));
                assertThat(events).as("passive content forwards the click").containsExactly(new UiEvent.Selected("tunnels", Set.of("a")), new UiEvent.Clicked("tunnels", "a"));
                events.clear();
                var heading = owner.mounted.get("quick");
                for (var listener : heading.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(heading, 0));
                assertThat(events).as("a heading is never clicked").isEmpty();
                var other = owner.mounted.get("b");
                for (var listener : other.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(other, InputEvent.META_DOWN_MASK));
                assertThat(events).as("a modified click selects without a plain click").containsExactly(new UiEvent.Selected("tunnels", Set.of("b")));
                events.clear();
                var right = new MouseEvent(other, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON3);
                for (var listener : other.getMouseListeners()) listener.mouseClicked(right);
                assertThat(events).as("nor does a right-button click").isEmpty();
                key(owner.canvas, "UP");
                assertThat(events).as("keys select only").noneMatch(event -> event instanceof UiEvent.Clicked);
                events.clear();
                var twice = new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 2, false, MouseEvent.BUTTON1);
                for (var listener : row.getMouseListeners()) listener.mouseClicked(twice);
                assertThat(events).as("the second click of a double click activates").containsExactly(
                    new UiEvent.Selected("tunnels", Set.of("a")), new UiEvent.Action("edit"));
            } finally { release(context); }
        });
    }

    @Test void aRowButtonReportsOnlyItsActionAndNeverSelectsClicksOrActivatesTheRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of("b"), "", "")), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                var star = (AbstractButton) owner.mounted.get("a").controls.get("a.star").component;
                var row = owner.mounted.get("a");
                assertThat(star.getMouseListeners()).as("the row's pointer handler is not on the button").doesNotContainAnyElementsOf(List.of(row.getMouseListeners()));
                star.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("favorite:a"));
                star.dispatchEvent(new MouseEvent(star, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 2, false, MouseEvent.BUTTON1));
                assertThat(events).as("a double click on the star does not activate the row").containsExactly(new UiEvent.Action("favorite:a"));
                assertThat(owner.selected).containsExactly("b");
                var toggle = (AbstractButton) owner.mounted.get("a").controls.get("a.run").component;
                toggle.doClick(0);
                assertThat(events).endsWith(new UiEvent.Toggled("a.run", true));
                assertThat(owner.selected).as("the switch is intent only").containsExactly("b");
            } finally { release(context); }
        });
    }

    @Test void enterRunsTheListsEnterActionWhileDoubleClickKeepsActivation() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of("a"), "toggle", "delete")), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                key(owner.canvas, "DOWN"); events.clear();
                key(owner.canvas, "ENTER");
                assertThat(events).containsExactly(new UiEvent.Selected("tunnels", Set.of("b")), new UiEvent.Action("toggle"));
                events.clear();
                var row = owner.mounted.get("a");
                var twice = new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 2, false, MouseEvent.BUTTON1);
                for (var listener : row.getMouseListeners()) listener.mouseClicked(twice);
                assertThat(events).containsExactly(new UiEvent.Selected("tunnels", Set.of("a")), new UiEvent.Action("edit"));
                events.clear();
                key(owner.canvas, "DELETE");
                assertThat(events).as("Delete acts on the selected focused row without reselecting").containsExactly(new UiEvent.Action("delete"));
                events.clear();
                key(owner.canvas, "BACK_SPACE");
                assertThat(events).containsExactly(new UiEvent.Action("delete"));
                events.clear();
                owner.focusItem = "b";
                key(owner.canvas, "DELETE");
                assertThat(events).as("an unselected focused row is selected first").containsExactly(new UiEvent.Selected("tunnels", Set.of("b")), new UiEvent.Action("delete"));
                events.clear();
                var toggle = owner.mounted.get("b").controls.get("b.run").component;
                assertThat(toggle.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("DELETE"))).as("inline controls never delete").isNull();
                view.update(new ViewSpec(tunnels(List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of("b"), "", "")));
                assertThat(bound(owner.canvas, "DELETE")).as("no delete action: the key passes on").isFalse();
                assertThat(bound(owner.canvas, "BACK_SPACE")).isFalse();
                key(owner.canvas, "ENTER");
                assertThat(events).as("no enter action: Enter activates as before").containsExactly(new UiEvent.Selected("tunnels", Set.of("b")), new UiEvent.Action("edit"));
            } finally { release(context); }
            assertThatThrownBy(() -> new ItemList("x", READY, ListKind.RESULTS, List.of(), Set.of(), "run", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "go", ""))
                .as("RESULTS rows never take focus, so they have no Enter action of their own").isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ItemList("x", READY, ListKind.RESULTS, List.of(), Set.of(), "run", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "", "delete"))
                .as("RESULTS rows never take focus, so they have no delete key").isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test void starGlyphsAreKitVocabulary() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try {
                for (String name : List.of("star", "star.filled")) {
                    var icon = context.icons().icon(name, context.theme().semantic(), Hue.AMBER, 16);
                    var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics(); icon.paintIcon(null, g, 0, 0); g.dispose();
                    int painted = 0;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) if ((image.getRGB(x, y) >>> 24) > 128) painted++;
                    assertThat(painted).as(name).isPositive();
                }
                int outline = coverage(context, "star"), filled = coverage(context, "star.filled");
                assertThat(filled).as("the favourite star reads as filled").isGreaterThan(outline * 13 / 10);
                assertThat(centre(context, "star")).as("the outline star is hollow").isFalse();
                assertThat(centre(context, "star.filled")).as("the filled star is solid at its centre").isTrue();
            } finally { release(context); }
        });
    }

    static int coverage(KitContext context, String name) {
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); context.icons().icon(name, context.theme().semantic(), Hue.AMBER, 32).paintIcon(null, g, 0, 0); g.dispose();
        int painted = 0;
        for (int x = 0; x < 32; x++) for (int y = 0; y < 32; y++) if ((image.getRGB(x, y) >>> 24) > 128) painted++;
        return painted;
    }

    static boolean centre(KitContext context, String name) {
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); context.icons().icon(name, context.theme().semantic(), Hue.AMBER, 32).paintIcon(null, g, 0, 0); g.dispose();
        return (image.getRGB(16, 18) >>> 24) > 128;
    }

    /** A context on one theme for both routes, as {@link KitFixtures#context()} does for dark. */
    static KitContext context(dev.jasper.ui.theme.GeneratedTheme theme) {
        return new KitContext(new dev.jasper.ui.theme.ThemeBinding(new dev.jasper.ui.theme.ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            dev.jasper.ui.theme.ThemeRoute.WORKSPACE, dev.jasper.ui.metrics.DesignMetrics.DEFAULT, new dev.jasper.ui.icons.IconRegistry(), () -> false);
    }

    /** Opaque glyph pixels within a small distance of {@code colour}. */
    static int near(java.awt.image.BufferedImage image, Color colour) {
        int count = 0;
        for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) {
            var c = new Color(image.getRGB(x, y), true);
            if (c.getAlpha() > 200 && Math.abs(c.getRed() - colour.getRed()) + Math.abs(c.getGreen() - colour.getGreen()) + Math.abs(c.getBlue() - colour.getBlue()) < 30) count++;
        }
        return count;
    }

    static Nodes.Button star(String id, boolean favourite) {
        return favourite
            ? new Nodes.Button(id, READY, "", "star.filled", ButtonKind.GHOST, "favorite", "Remove from favorites", ButtonPresentation.EMBEDDED, Optional.of(Severity.WARNING))
            : new Nodes.Button(id, READY, "", "star", ButtonKind.GHOST, "favorite", "Add to favorites", ButtonPresentation.EMBEDDED);
    }

    @Test void aFavouriteStarKeepsTheWarningColourAtRestAndOnHoverInBothThemes() {
        Edt.run(() -> {
            for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                var context = context(theme);
                try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(star("fav", true), star("plain", false)))), e -> {})) {
                    var warning = ThemePainting.color(context, SemanticColor.STATUS_WARNING);
                    var favourite = find(view, "fav", Buttons.KitButton.class);
                    var plain = find(view, "plain", Buttons.KitButton.class);
                    favourite.setSize(favourite.getPreferredSize()); plain.setSize(plain.getPreferredSize());
                    assertThat(near(ButtonsTest.paint(favourite), warning)).as("%s favourite at rest", theme.dark() ? "dark" : "light").isGreaterThan(20);
                    assertThat(near(ButtonsTest.paint(plain), warning)).as("%s plain star is untinted", theme.dark() ? "dark" : "light").isZero();
                    favourite.getModel().setRollover(true);
                    assertThat(near(ButtonsTest.paint(favourite), warning)).as("%s favourite on hover", theme.dark() ? "dark" : "light").isGreaterThan(20);
                    favourite.getModel().setRollover(false);
                    view.update(new ViewSpec(new Stack("root", READY, LayoutRole.INLINE, List.of(star("fav", false), star("plain", false)))));
                    assertThat(near(ButtonsTest.paint(favourite), warning)).as("%s no longer a favourite", theme.dark() ? "dark" : "light").isZero();
                } finally { release(context); }
            }
            assertThat(star("plain", false).iconSeverity()).isEmpty();
        });
    }

    /**
     * Plan 2 Task 8c (port of the deleted Remote PanelStyleTest): a PRIMARY action, as the Sessions card's Connect,
     * shows a visible keyboard focus ring without depending on the look and feel's border, and losing focus removes it.
     */
    @Test void aPrimaryActionShowsAVisibleKeyboardFocusRingAndLosesItWithFocus() {
        Edt.run(() -> {
            var priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            var manager = new DefaultKeyboardFocusManager() { Component target; @Override public Component getFocusOwner() { return target; } };
            var context = KitFixtures.context();
            try {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(manager);
                var connect = new Nodes.Button("connect", READY, "Connect", "terminal", ButtonKind.PRIMARY, "connect", "");
                try (var view = new KitView(context, new ViewSpec(new Stack("card", READY, LayoutRole.VERTICAL, List.of(connect))), e -> {})) {
                    int width = 240, height = 80;
                    var idle = KitFixtures.render(view, width, height, 1);
                    var button = find(view, "connect", Buttons.KitButton.class);
                    var bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), view);
                    manager.target = button;
                    var gained = new java.awt.event.FocusEvent(button, java.awt.event.FocusEvent.FOCUS_GAINED, false, null, java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
                    for (var listener : button.getFocusListeners()) listener.focusGained(gained);
                    assertThat(button.focus.visible()).as("keyboard focus shows").isTrue();
                    var focused = KitFixtures.render(view, width, height, 1);
                    int ring = Math.max(2, ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING));
                    var area = new Rectangle(bounds); area.grow(ring, ring);
                    assertThat(differing(idle, focused, area)).as("a visible focus ring around the primary action").isGreaterThan(0);
                    manager.target = null;
                    var lost = new java.awt.event.FocusEvent(button, java.awt.event.FocusEvent.FOCUS_LOST, false, null, java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
                    for (var listener : button.getFocusListeners()) listener.focusLost(lost);
                    assertThat(differing(idle, KitFixtures.render(view, width, height, 1), area)).as("no ring without focus").isZero();
                }
            } finally { KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus); release(context); }
        });
    }

    static int differing(java.awt.image.BufferedImage a, java.awt.image.BufferedImage b, Rectangle area) {
        int count = 0;
        for (int x = Math.max(0, area.x); x < Math.min(a.getWidth(), area.x + area.width); x++)
            for (int y = Math.max(0, area.y); y < Math.min(a.getHeight(), area.y + area.height); y++) if (a.getRGB(x, y) != b.getRGB(x, y)) count++;
        return count;
    }

    /**
     * Plan 2 Task 8c fix round 1 (I1, controller ruling): as the JTable's component popup did, a context press outside
     * every table row (below the rows, or in an empty table) opens the table's shared menu, which acts on the selection;
     * it never changes the selection, and a chosen item reports only its action. Rows keep their own menus; lists keep
     * offering nothing outside their rows; a table without a shared menu (the file chooser) shows none.
     */
    @Test void aContextPressOutsideTheRowsOpensTheTablesSharedMenuWithoutChangingTheSelection() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var shared = List.of(new MenuItem("up", "Up", "", "menu:up", true, false), new MenuItem("delete", "Delete selected…", "", "menu:delete", false, false));
            java.util.function.BiFunction<Integer, List<MenuItem>, Table> table = (count, menu) -> new Table("files", READY,
                List.of(new Column("name", "Name", TextRole.BODY, false), new Column("size", "Size", TextRole.DESCRIPTION, true)),
                java.util.stream.IntStream.range(0, count).mapToObj(i -> new TableRow("r" + i, Map.of("name", "f" + i, "size", i + " B"), Optional.of("file"), false,
                    Optional.of(List.of(new MenuItem("own", "Own", "", "row:" + i, true, false))))).toList(),
                count > 0 ? Set.of("r0") : Set.of(), "open", menu, SelectionMode.MULTIPLE, ScrollPolicy.STANDARD);
            try (var view = new KitView(context, new ViewSpec(table.apply(2, shared)), events::add)) {
                view.setSize(400, 300); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                var viewport = owner.scroll.getViewport();
                assertThat(owner.canvas.getHeight()).as("the rows leave free area below").isLessThan(viewport.getHeight());
                events.clear();
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 20, viewport.getHeight() - 5, 1, true, MouseEvent.BUTTON3));
                assertThat(owner.popup.getComponentCount()).as("the shared menu below the rows").isEqualTo(2);
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).isEqualTo("Up");
                assertThat(owner.popup.getComponent(1).isEnabled()).as("items keep their enabled state").isFalse();
                assertThat(events).as("a context press never selects").isEmpty();
                assertThat(owner.selected).containsExactly("r0");
                ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                assertThat(events).as("the item reports only its action").containsExactly(new UiEvent.Action("menu:up"));
                events.clear();
                var row = owner.mounted.get("r1");
                row.dispatchEvent(new MouseEvent(row, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 4, 4, 1, true, MouseEvent.BUTTON3));
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).as("a row keeps its own menu").isEqualTo("Own");
                view.update(new ViewSpec(table.apply(0, shared)));
                InteractiveRowsTest.layout(view);
                owner.canvas.dispatchEvent(new MouseEvent(owner.canvas, MouseEvent.MOUSE_RELEASED, 0, 0, 2, 2, 1, true, MouseEvent.BUTTON3));
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_RELEASED, 0, 0, 20, 20, 1, true, MouseEvent.BUTTON3));
                assertThat(owner.popup.getComponentCount()).as("an empty table still offers its shared menu").isEqualTo(2);
                assertThat(events).isEmpty();
                view.update(new ViewSpec(table.apply(2, List.of())));
                InteractiveRowsTest.layout(view);
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 20, viewport.getHeight() - 5, 1, true, MouseEvent.BUTTON3));
                assertThat(owner.popup.getComponentCount()).as("no shared menu, no menu (the file chooser)").isZero();
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 20, viewport.getHeight() - 5, 1, false, MouseEvent.BUTTON1));
                assertThat(events).isEmpty();
            } finally { release(context); }
            var lists = KitFixtures.context();
            try (var view = new KitView(lists, new ViewSpec(new ItemList("rows", READY, ListKind.SINGLE, List.of(new ListItem("a", Optional.empty(),
                    List.of(new Text("a.t", READY, "A", TextRole.BODY)), List.of(), List.of())), Set.of("a"), "", shared)), e -> {})) {
                view.setSize(400, 300); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("rows");
                var viewport = owner.scroll.getViewport();
                viewport.dispatchEvent(new MouseEvent(viewport, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 20, viewport.getHeight() - 5, 1, true, MouseEvent.BUTTON3));
                assertThat(owner.popup.getComponentCount()).as("lists offer nothing outside their rows").isZero();
            } finally { release(lists); }
        });
    }

    static final String HOSTILE = "<html><img src='http://127.0.0.1:9/x.png'>web";

    /** Every component under {@code root} (itself included) that shows a tooltip. */
    static List<JComponent> tipped(Component root) {
        var found = new ArrayList<JComponent>();
        if (root instanceof JComponent c && c.getToolTipText() != null) found.add(c);
        if (root instanceof Container parent) for (var child : parent.getComponents()) found.addAll(tipped(child));
        return found;
    }

    static void literalTips(Component root, String text) {
        var parts = tipped(root);
        assertThat(parts).as("parts showing the tooltip").isNotEmpty();
        for (var part : parts) {
            var tip = part.createToolTip(); tip.setTipText(text);
            assertThat(tip.getClientProperty("html.disable")).as("%s tooltip is literal", part.getClass().getName()).isEqualTo(Boolean.TRUE);
            assertThat(tip.getClientProperty("html")).as("%s builds no HTML view", part.getClass().getName()).isNull();
        }
    }

    @Test void everyRowPartShowingTheRowTooltipShowsItAsLiteralText() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var table = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false), new Column("size", "Size", TextRole.DESCRIPTION, true)),
                List.of(new TableRow("notes", Map.of("name", "notes.txt", "size", "6 B"), Optional.of("file"), false, Optional.empty(), HOSTILE)), Set.of(), "open", List.of());
            var row = new ListItem("a", Optional.of(new StatusDot("a.dot", READY, StatusKind.SUCCESS)),
                List.of(new Text("a.name", READY, "web", TextRole.BODY), new Nodes.Icon("a.icon", READY, "tunnel", Hue.NONE, IconSize.CAPTION, "Tunnel")),
                List.of(new Chip("a.chip", READY, "SSH", Hue.BLUE), new Progress("a.progress", READY, .5, "Half")),
                List.of(new StatusDot("a.state", READY, StatusKind.WARNING)), false, Optional.empty(), HOSTILE);
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(tunnels(List.of(row), Set.of(), "", ""), table)));
            try (var view = new KitView(context, spec, e -> {})) {
                view.setSize(480, 400); InteractiveRowsTest.layout(view);
                var list = ((ItemLists.ListControl) view.control("tunnels")).mounted.get("a");
                assertThat(list.controls.get("a.dot").component.getToolTipText()).as("the status dot shows the row tooltip").isEqualTo(HOSTILE);
                literalTips(list, HOSTILE);
                literalTips(((Tables.TableControl) view.control("files")).mounted.get("notes"), HOSTILE);
            } finally { release(context); }
        });
    }

    /**
     * Task 8a fix round 1 (controller ruling I4): every kit row has an accessible name from its title text, as a list
     * cell's name was its text, and keeps its tooltip as the accessible description.
     */
    @Test void everyRowIsNamedByItsTitleAndDescribedByItsTooltip() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var table = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false), new Column("size", "Size", TextRole.DESCRIPTION, true)),
                List.of(new TableRow("notes", Map.of("name", "notes.txt", "size", "6 B"), Optional.of("file"), false, Optional.empty(), "notes.txt — 6 B")), Set.of(), "open", List.of());
            var host = new ListItem("h", Optional.of(new StatusDot("h.dot", READY, StatusKind.SUCCESS)),
                List.of(new Text("h.name", READY, "prod", TextRole.BODY), new Text("h.address", READY, "api.example", TextRole.DESCRIPTION)),
                List.of(), List.of(), false, Optional.empty(), "prod, deploy@api.example:22, 1 session");
            var plain = new ListItem("p", Optional.empty(), List.of(new Text("p.name", READY, "staging", TextRole.BODY), new Chip("p.chip", READY, "WSL 2", Hue.NEUTRAL)),
                List.of(), List.of(), false, Optional.empty(), "");
            var tree = new Tree("places", READY, List.of(new TreeItem("home", "Home", Optional.of("home"), true, List.of(), OptionalInt.empty(), false,
                "/Users/me", Optional.empty(), List.of(), Optional.empty())), Set.of(), Set.of(), "");
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(tunnels(List.of(host, plain), Set.of(), "", ""), table, tree)));
            try (var view = new KitView(context, spec, e -> {})) {
                view.setSize(480, 500); InteractiveRowsTest.layout(view);
                var rows = ((ItemLists.ListControl) view.control("tunnels")).mounted;
                var named = rows.get("h").getAccessibleContext();
                assertThat(named.getAccessibleName()).isEqualTo("prod, api.example");
                assertThat(named.getAccessibleDescription()).isEqualTo("prod, deploy@api.example:22, 1 session");
                assertThat(rows.get("p").getAccessibleContext().getAccessibleName()).as("chips count as title text").isEqualTo("staging, WSL 2");
                assertThat(rows.get("p").getAccessibleContext().getAccessibleDescription()).as("no tooltip, no description").isNull();
                var file = ((Tables.TableControl) view.control("files")).mounted.get("notes").getAccessibleContext();
                assertThat(file.getAccessibleName()).as("a table row is named by its first column").isEqualTo("notes.txt");
                assertThat(file.getAccessibleDescription()).isEqualTo("notes.txt — 6 B");
                var place = ((Trees.TreeControl) view.control("places")).mounted.get("home").getAccessibleContext();
                assertThat(place.getAccessibleName()).isEqualTo("Home");
                assertThat(place.getAccessibleDescription()).isEqualTo("/Users/me");
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(tunnels(List.of(
                    new ListItem("h", Optional.empty(), List.of(new Text("h.name", READY, "prod-2", TextRole.BODY)), List.of(), List.of(), false, Optional.empty(), "")),
                    Set.of(), "", ""), table, tree))));
                InteractiveRowsTest.layout(view);
                assertThat(((ItemLists.ListControl) view.control("tunnels")).mounted.get("h").getAccessibleContext().getAccessibleName()).as("follows the descriptor").isEqualTo("prod-2");
            } finally { release(context); }
        });
    }

    /** The component a pointer at {@code point} (row coordinates) reaches: the deepest one listening for the mouse. */
    static JComponent receiver(JComponent row, Point point) {
        Component c = SwingUtilities.getDeepestComponentAt(row, point.x, point.y);
        while (c != null && c != row && c.getMouseListeners().length == 0) c = c.getParent();
        return (JComponent) c;
    }

    @Test void theRowTooltipShowsOverEveryPartOfTheRowIncludingEmptySlotArea() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "web, modified 2026-10-05 09:12", false)), Set.of(), "", "")), e -> {})) {
                view.setSize(480, 240); InteractiveRowsTest.layout(view);
                var row = ((ItemLists.ListControl) view.control("tunnels")).mounted.get("a");
                for (var part : List.of(row.leading, row.title, row.subtitle, row.trailing)) {
                    assertThat(part.getWidth()).as("laid out").isPositive();
                    for (var corner : List.of(new Point(part.getWidth() - 1, part.getHeight() - 1), new Point(part.getWidth() - 1, 0))) {
                        var point = SwingUtilities.convertPoint(part, corner, row);
                        var target = receiver(row, point);
                        var at = SwingUtilities.convertPoint(row, point, target);
                        var move = new MouseEvent(target, MouseEvent.MOUSE_MOVED, 0, 0, at.x, at.y, 0, false);
                        assertThat(target.getToolTipText(move)).as("tooltip at %s of %s", corner, target.getClass().getName()).isEqualTo("web, modified 2026-10-05 09:12");
                    }
                }
            } finally { release(context); }
        });
    }

    @Test void withoutAFocusedInteractiveRowEnterAndDeletePassOn() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of(), "toggle", "delete")), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                assertThat(owner.focusItem).isNull();
                assertThat(bound(owner.canvas, "DELETE")).as("no focused row: Delete passes on").isFalse();
                assertThat(bound(owner.canvas, "BACK_SPACE")).isFalse();
                assertThat(bound(owner.canvas, "ENTER")).as("no focused row: Enter passes on").isFalse();
                owner.focusItem = "a";
                assertThat(bound(owner.canvas, "DELETE")).isTrue();
                assertThat(bound(owner.canvas, "ENTER")).isTrue();
                var off = new ControlState(true, false, false, "");
                view.update(new ViewSpec(new ItemList("tunnels", off, ListKind.DOUBLE, List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of(), "edit", List.of(),
                    SelectionMode.SINGLE, Optional.empty(), OptionalInt.empty(), "toggle", "delete")));
                assertThat(owner.focusItem).isEqualTo("a");
                assertThat(bound(owner.canvas, "DELETE")).as("a disabled list passes Delete on").isFalse();
                assertThat(bound(owner.canvas, "ENTER")).as("and Enter").isFalse();
                assertThat(events).isEmpty();
            } finally { release(context); }
            var tables = KitFixtures.context();
            var files = new Table("files", READY, List.of(new Column("name", "Name", TextRole.BODY, false)), List.of(new TableRow("a", Map.of("name", "a"), Optional.empty())),
                Set.of(), "open", List.of());
            try (var view = new KitView(tables, new ViewSpec(files), e -> {})) {
                var owner = (Tables.TableControl) view.control("files");
                assertThat(owner.focusItem).isNull();
                assertThat(bound(owner.canvas, "ENTER")).as("a table keeps Enter without a focused row, as the JTable did").isTrue();
                assertThat(bound(owner.canvas, "DELETE")).as("but has no Delete action").isFalse();
            } finally { release(tables); }
        });
    }

    static KeyEvent press(Component on, int code) {
        return new KeyEvent(on, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test void aRealDeleteKeyOnAFocusedInlineControlNeverReachesTheListsDeleteAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "", false), tunnel("b", "", false)), Set.of("a"), "toggle", "delete")), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                owner.focusItem = "a";
                for (String part : List.of("a.run", "a.star")) for (int code : List.of(KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE)) {
                    SwingUtilities.processKeyBindings(press(owner.mounted.get("a").controls.get(part).component, code));
                    assertThat(events).as("%s key %s", part, code).isEmpty();
                }
                assertThat(SwingUtilities.processKeyBindings(press(owner.canvas, KeyEvent.VK_DELETE))).as("the rows themselves take it").isTrue();
                assertThat(events).containsExactly(new UiEvent.Action("delete"));
            } finally { release(context); }
        });
    }

    @Test void aPendingSwitchShowsAMidWarningTrackAndStaysOperable() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var pending = new Toggle("run", READY, "Stop tunnel", true, "toggle", false, "", true);
            try (var view = new KitView(context, new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(pending))), events::add)) {
                var button = find(view, "run", Switches.SwitchButton.class);
                assertThat(button.position).as("the knob waits in the middle").isEqualTo(.5);
                assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("Pending");
                button.setSize(button.getPreferredSize());
                var image = ButtonsTest.paint(button);
                var warning = ThemePainting.color(context, SemanticColor.STATUS_WARNING);
                int ring = ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.FOCUS_RING);
                var track = new Color(image.getRGB(ring + 3, button.getHeight() / 2), true);
                assertThat(Math.abs(track.getRed() - warning.getRed()) + Math.abs(track.getGreen() - warning.getGreen()) + Math.abs(track.getBlue() - warning.getBlue()))
                    .as("the track is the warning colour").isLessThan(24);
                button.doClick(0);
                assertThat(events).as("pending stays operable and reports the opposite of the confirmed state").containsExactly(new UiEvent.Toggled("run", false));
                view.update(new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(new Toggle("run", READY, "Start tunnel", false, "toggle", false)))));
                assertThat(button.position).as("motion settles headless").isZero();
                assertThat(button.getAccessibleContext().getAccessibleDescription()).isEmpty();
            } finally { release(context); }
        });
    }

    /**
     * Task 8a (ruling G7, review fix round 1): the connecting overlay's indeterminate progress replaces Remote's custom
     * ConnectionDots, and keeps its lifecycle: no animation before the view is displayable, none under a hidden ancestor
     * or once removed from the hierarchy, again when re-added; none while hidden, determinate or removed by the
     * descriptor, with motion off or after the view closes.
     */
    @Test void anIndeterminateProgressAnimatesOnlyWhileShowingAndStopsWhenHiddenDeterminateRemovedOrClosed() {
        Edt.run(() -> {
            var enabled = new java.util.concurrent.atomic.AtomicBoolean(true); var base = KitFixtures.context();
            var context = new KitContext(base.binding(), base.route(), base.metrics(), base.icons(), enabled::get);
            java.util.function.Function<ControlState, ViewSpec> working = state -> stack(new Progress("progress", state, -1, "Connecting"),
                new Text("status", READY, "Opening shell…", TextRole.BODY));
            var host = new javax.swing.JPanel(new java.awt.BorderLayout());
            try (var view = new KitView(context, working.apply(READY), e -> {})) {
                var bar = find(view, "progress", Indicators.ProgressBar.class);
                host.add(view);
                assertThat(bar.animation.isRunning()).as("not displayable yet: no timer").isFalse();
                host.addNotify();
                try {
                    assertThat(bar.animation.isRunning()).as("showing").isTrue();
                    host.setVisible(false);
                    assertThat(bar.animation.isRunning()).as("an ancestor hidden").isFalse();
                    host.setVisible(true);
                    assertThat(bar.animation.isRunning()).as("the ancestor shown again").isTrue();
                    host.remove(view);
                    assertThat(bar.animation.isRunning()).as("removed from the hierarchy").isFalse();
                    host.add(view);
                    assertThat(bar.animation.isRunning()).as("re-added").isTrue();
                    view.update(working.apply(new ControlState(false, true, false, "")));
                    assertThat(bar.animation.isRunning()).as("hidden after a failure").isFalse();
                    view.update(working.apply(READY));
                    assertThat(bar.animation.isRunning()).as("shown again by Retry").isTrue();
                    view.update(stack(new Progress("progress", READY, .5, "Connecting")));
                    assertThat(bar.animation.isRunning()).as("determinate").isFalse();
                    view.update(working.apply(READY));
                    assertThat(bar.animation.isRunning()).isTrue();
                    view.update(stack(new Text("status", READY, "Done", TextRole.BODY)));
                    assertThat(bar.animation.isRunning()).as("removed by the descriptor").isFalse();
                    view.update(working.apply(READY));
                    var again = find(view, "progress", Indicators.ProgressBar.class);
                    assertThat(again.animation.isRunning()).isTrue();
                    enabled.set(false);
                    view.update(working.apply(READY));
                    assertThat(again.animation.isRunning()).as("motion off").isFalse();
                    assertThat(again.phase).isZero();
                    enabled.set(true);
                    view.update(working.apply(READY));
                    assertThat(again.animation.isRunning()).isTrue();
                    view.close();
                    assertThat(again.animation.isRunning()).as("closed").isFalse();
                    host.remove(view); host.add(view);
                    assertThat(again.animation.isRunning()).as("a closed view never restarts").isFalse();
                } finally { host.removeNotify(); }
            } finally { release(context); }
            var fresh = KitFixtures.context();
            var off = new KitContext(fresh.binding(), fresh.route(), fresh.metrics(), fresh.icons(), () -> false);
            var shown = new javax.swing.JPanel(new java.awt.BorderLayout());
            try (var view = new KitView(off, working.apply(READY), e -> {})) {
                shown.add(view); shown.addNotify();
                try { assertThat(find(view, "progress", Indicators.ProgressBar.class).animation.isRunning()).as("never starts with motion off").isFalse(); }
                finally { shown.removeNotify(); }
            } finally { release(off); }
        });
    }

    /**
     * Task 8b fix round 1 (M2): a real double click on a row's switch is two operations of that switch and nothing else:
     * exactly two {@code Toggled} reports (the Tunnels panel ignores the second as the second click of a double click),
     * never the row's selection, {@code Clicked} or its double-click activation.
     */
    @Test void aRealDoubleClickOnARowSwitchReportsTwoToggledAndNeverActivatesTheRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tunnels(List.of(tunnel("a", "row a", false), tunnel("b", "row b", false)), Set.of("b"), "toggle", "delete")), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("tunnels");
                var button = (AbstractButton) owner.mounted.get("a").controls.get("a.run").component;
                assertThat(button.getWidth()).as("laid out").isPositive();
                int x = button.getWidth() / 2, y = button.getHeight() / 2;
                for (int count = 1; count <= 2; count++) {
                    for (int id : new int[] {MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED})
                        button.dispatchEvent(new MouseEvent(button, id, System.currentTimeMillis(), id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED ? 0
                            : InputEvent.BUTTON1_DOWN_MASK, x, y, count, false, MouseEvent.BUTTON1));
                }
                assertThat(events).as("two operations of the switch, nothing on the row")
                    .containsExactly(new UiEvent.Toggled("a.run", true), new UiEvent.Toggled("a.run", true));
            } finally { release(context); }
        });
    }

    // ---- earlier consumers keep their behaviour

    @Test void paletteResultsReportNoClickedAndKeepTheirEnter() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var items = List.of(new ListItem("new", Optional.empty(), List.of(new Text("new.t", READY, "New tab", TextRole.BODY)), List.of(), List.of()),
                new ListItem("close", Optional.empty(), List.of(new Text("close.t", READY, "Close tab", TextRole.BODY)), List.of(), List.of()));
            var results = new ItemList("results", READY, ListKind.RESULTS, items, Set.of("new"), "run", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(5));
            try (var view = new KitView(context, new ViewSpec(results), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("results");
                var row = owner.mounted.get("close");
                var press = new MouseEvent(row, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1);
                for (var listener : row.getMouseListeners()) { listener.mousePressed(press); listener.mouseClicked(BrowserKitTest.click(row, 0)); }
                assertThat(events).containsExactly(new UiEvent.Selected("results", Set.of("close")), new UiEvent.Action("run"));
                assertThat(bound(owner.canvas, "DELETE")).isFalse();
            } finally { release(context); }
        });
    }

    @Test void pluginRowsKeepEnterPassingOnAndTheirSwitchSpace() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var plugins = NoticeKitTest.plugins(List.of(NoticeKitTest.heading("bundled"), NoticeKitTest.plugin("a", true, READY, Optional.of(NoticeKitTest.menu("a")))), Set.of("a"));
            try (var view = new KitView(context, new ViewSpec(plugins), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("plugins");
                assertThat(bound(owner.canvas, "ENTER")).as("no activate or enter action: Enter reaches the dialog").isFalse();
                assertThat(bound(owner.canvas, "DELETE")).isFalse();
                assertThat(bound(owner.canvas, "BACK_SPACE")).isFalse();
                owner.focusItem = "a";
                key(owner.canvas, "SPACE");
                assertThat(events).containsExactly(new UiEvent.Toggled("a.enabled", false));
                events.clear();
                var row = owner.mounted.get("a");
                for (var listener : row.getMouseListeners()) listener.mouseClicked(BrowserKitTest.click(row, 0));
                assertThat(events).as("a plain click now also reports Clicked, which the manager ignores")
                    .containsExactly(new UiEvent.Selected("plugins", Set.of("a")), new UiEvent.Clicked("plugins", "a"));
            } finally { release(context); }
        });
    }

    @Test void orderedSettingsListsKeepTheirRowCommandsAndEnterPassingOn() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var rows = List.of(new ListItem("one", Optional.empty(), List.of(new Text("one.t", READY, "one", TextRole.BODY)), List.of(), List.of()),
                new ListItem("two", Optional.empty(), List.of(new Text("two.t", READY, "two", TextRole.BODY)), List.of(), List.of()));
            try (var view = new KitView(context, new ViewSpec(new ItemList("paths", READY, ListKind.ORDERED, rows, Set.of(), "", List.of())), events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("paths");
                assertThat(bound(owner.canvas, "ENTER")).isFalse();
                assertThat(bound(owner.canvas, "DELETE")).as("ordered lists remove through their row command only").isFalse();
                ((AbstractButton) owner.mounted.get("two").controls.get(owner.internalId("two", "remove")).component).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Removed("paths", "two"));
            } finally { release(context); }
        });
    }

    @Test void vaultManagerRowsKeepEnterAsTheirActivateAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var rows = List.of(new ListItem("login", Optional.of(new Nodes.Icon("login.icon", READY, "login", Hue.BLUE, IconSize.RAIL, "Login")),
                List.of(new Text("login.t", READY, "GitHub", TextRole.BODY_STRONG)), List.of(new Text("login.s", READY, "me", TextRole.DESCRIPTION)), List.of()));
            var list = new ItemList("rows", READY, ListKind.DOUBLE, rows, Set.of("login"), "edit", List.of(), SelectionMode.SINGLE, Optional.empty(), OptionalInt.of(11));
            try (var view = new KitView(context, new ViewSpec(list), events::add)) {
                view.setSize(420, 400); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("rows");
                owner.focusItem = "login";
                key(owner.canvas, "ENTER");
                assertThat(events).containsExactly(new UiEvent.Selected("rows", Set.of("login")), new UiEvent.Action("edit"));
                assertThat(bound(owner.canvas, "DELETE")).isFalse();
                var typed = new KeyEvent(owner.canvas, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, '\b');
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed);
                assertThat(events).as("Backspace is not type-ahead").hasSize(2);
            } finally { release(context); }
        });
    }
    static ListItem contextual(String id) {
        return new ListItem(id,Optional.empty(),List.of(new Text(id+".title",READY,id,TextRole.BODY)),List.of(),
            List.of(new Nodes.Button(id+".action",READY,"","star",ButtonKind.GHOST,id,"Independent action",ButtonPresentation.CONTEXTUAL_EMBEDDED)));
    }
    static int ink(Buttons.KitButton button) {
        var image=ButtonsTest.paint(button);int result=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)>0)result++;
        return result;
    }
    @Test void contextualActionsReserveGeometryAndDispatchWhileInkTracksRealRowContext() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var events=new ArrayList<UiEvent>();
            var spec=new ViewSpec(new ItemList("list",READY,ListKind.SINGLE,List.of(contextual("first"),contextual("second")),Set.of("first"),"",List.of()));
            try(var view=new KitView(ctx,spec,events::add)) {
                view.setSize(260,100);InteractiveRowsTest.layout(view);
                var rows=(ItemLists.ListControl)view.control("list");var row=rows.mounted.get("second");
                var button=((Buttons.ButtonControl)row.controls.get("second.action")).button;
                var size=button.getPreferredSize();
                assertThat(button.isVisible()).isTrue();assertThat(button.isFocusable()).isTrue();
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Independent action");
                assertThat(ink(button)).as("inactive nonselected row reserves an unpainted action").isZero();
                var entered=new MouseEvent(row,MouseEvent.MOUSE_ENTERED,1,0,0,0,0,false);
                for(var listener:row.getMouseListeners())listener.mouseEntered(entered);
                assertThat(ink(button)).isPositive();
                for(var listener:row.getMouseListeners())listener.mouseExited(new MouseEvent(row,MouseEvent.MOUSE_EXITED,2,0,0,0,0,false));
                assertThat(ink(button)).isZero();
                rows.focusItem="second";
                assertThat(ink(button)).as("logical focus alone is insufficient").isZero();
                var manager=RepaintManager.currentManager(view);
                var dirty=new java.util.ArrayList<JComponent>();
                RepaintManager.setCurrentManager(new RepaintManager(){@Override public void addDirtyRegion(JComponent c,int x,int y,int w,int h){dirty.add(c);}});
                try {
                    for(var listener:rows.canvas.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(rows.canvas,java.awt.event.FocusEvent.FOCUS_GAINED));
                    assertThat(dirty).contains(rows.canvas);dirty.clear();
                    for(var listener:rows.canvas.getFocusListeners())listener.focusLost(new java.awt.event.FocusEvent(rows.canvas,java.awt.event.FocusEvent.FOCUS_LOST));
                    assertThat(dirty).contains(rows.canvas);dirty.clear();
                    for(var listener:button.getFocusListeners())listener.focusGained(new java.awt.event.FocusEvent(button,java.awt.event.FocusEvent.FOCUS_GAINED));
                    assertThat(dirty).contains(row);dirty.clear();
                    for(var listener:button.getFocusListeners())listener.focusLost(new java.awt.event.FocusEvent(button,java.awt.event.FocusEvent.FOCUS_LOST));
                    assertThat(dirty).contains(row);
                }finally{RepaintManager.setCurrentManager(manager);}
                var previous=KeyboardFocusManager.getCurrentKeyboardFocusManager();
                try {
                    KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Component getFocusOwner(){return rows.canvas;}});
                    assertThat(ink(button)).as("actual keyboard-active row").isPositive();
                    KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager(){@Override public Component getFocusOwner(){return button;}});
                    assertThat(ink(button)).as("direct button focus").isPositive();
                }finally{KeyboardFocusManager.setCurrentKeyboardFocusManager(previous);}
                assertThat(button.getPreferredSize()).isEqualTo(size);
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("second"));
                assertThat(rows.selected).containsExactly("first");
                view.update(spec);InteractiveRowsTest.layout(view);
                assertThat(((Buttons.ButtonControl)rows.mounted.get("second").controls.get("second.action")).button).isSameAs(button);
                assertThat(ink(button)).isZero();
            }finally{release(ctx);}
        });
    }

    @Test void contextualActionHoverAndFocusDoNotLeakAcrossRecycledRowsOrThemeRefresh() {
        Edt.run(() -> {
            var ctx=KitFixtures.context();var events=new ArrayList<UiEvent>();
            var items=java.util.stream.IntStream.range(0,200).mapToObj(i->contextual("item"+i)).toList();
            var spec=new ViewSpec(new ItemList("list",READY,ListKind.SINGLE,items,Set.of(),"",List.of()));
            try(var view=new KitView(ctx,spec,events::add)) {
                view.setSize(260,100);InteractiveRowsTest.layout(view);
                var rows=(ItemLists.ListControl)view.control("list");var oldRow=rows.mounted.get("item0");
                var old=((Buttons.ButtonControl)oldRow.controls.get("item0.action")).button;
                oldRow.hover=true;assertThat(ink(old)).isPositive();
                rows.scroll.getViewport().setViewPosition(new Point(0,4000));InteractiveRowsTest.layout(view);
                assertThat(rows.mounted).doesNotContainKey("item0");
                assertThat(old.getActionListeners()).isEmpty();old.doClick(0);assertThat(events).isEmpty();
                for(var row:rows.mounted.values()) {
                    assertThat(row.hover).isFalse();
                    for(var control:row.controls.values())if(control instanceof Buttons.ButtonControl b)assertThat(ink(b.button)).isZero();
                }
                rows.scroll.getViewport().setViewPosition(new Point());InteractiveRowsTest.layout(view);
                var current=((Buttons.ButtonControl)rows.mounted.get("item0").controls.get("item0.action")).button;
                assertThat(current).isNotSameAs(old);assertThat(ink(current)).isZero();
                ctx.binding().publish(new dev.jasper.ui.theme.ThemePair(KitFixtures.light(),KitFixtures.light()));InteractiveRowsTest.layout(view);
                assertThat(((Buttons.ButtonControl)rows.mounted.get("item0").controls.get("item0.action")).button).isSameAs(current);
                assertThat(ink(current)).isZero();current.doClick(0);assertThat(events).containsExactly(new UiEvent.Action("item0"));
            }finally{release(ctx);}
        });
    }

}
