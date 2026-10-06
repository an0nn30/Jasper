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
}
