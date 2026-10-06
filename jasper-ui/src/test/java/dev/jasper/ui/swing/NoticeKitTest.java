package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemePair;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Icon;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes.Stack;
import org.junit.jupiter.api.Test;

/**
 * Plan 2 Task 6c: what the plugin manager and the notification surfaces need from the kit. Row-owned item-list menus,
 * Shift+F10, Space operating a row's switch, switch tooltips, severity-tinted glyphs, flowing body text and pointer-only
 * views.
 */
@SuppressWarnings("try")
class NoticeKitTest {
    static final ControlState READY = ControlState.READY;
    static final ControlState OFF = new ControlState(true, false, false, "");

    static ListItem plugin(String id, boolean on, ControlState toggle, Optional<List<MenuItem>> menu) {
        return new ListItem(id, Optional.of(new Nodes.Icon(id + ".icon", READY, "plugin", Hue.NEUTRAL, IconSize.RAIL, "Plugin")),
            List.of(new Text(id + ".name", READY, "Plugin " + id, TextRole.BODY_STRONG)),
            List.of(new Text(id + ".meta", READY, "1.0  Vendor", TextRole.CAPTION)),
            List.of(new Toggle(id + ".enabled", toggle, "Enable " + id, on, id + ".toggle", true, "")), false, menu);
    }

    static ItemList plugins(List<ListItem> items, Set<String> selected) {
        return new ItemList("plugins", READY, ListKind.DOUBLE, items, selected, "", List.of(), SelectionMode.SINGLE, Optional.empty());
    }

    static List<MenuItem> menu(String id) {
        return List.of(new MenuItem(id + ".settings", "Open Settings", "", id + ":settings", true, false),
            new MenuItem(id + ".sep", "", "", "", false, true),
            new MenuItem(id + ".remove", "Remove…", "", id + ":remove", true, false));
    }

    @Test void aRowsOwnMenuOpensOnThatRowWithoutSelectingItAndReportsOnlyItsAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = new ViewSpec(plugins(List.of(heading("bundled"), plugin("a", true, READY, Optional.of(menu("a"))),
                plugin("b", false, READY, Optional.of(menu("b")))), Set.of("a")));
            try (var view = new KitView(context, spec, events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("plugins");
                owner.context("b", owner.canvas, 0, 0);
                assertThat(owner.popup.getComponentCount()).as("item, separator, item").isEqualTo(3);
                ((JMenuItem) owner.popup.getComponent(2)).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("b:remove"));
                assertThat(owner.selected).as("a context press never selects").containsExactly("a");
                events.clear();
                owner.context("bundled", owner.canvas, 0, 0);
                assertThat(owner.popup.getComponentCount()).as("a heading has no menu").isZero();
                // The menu key and Shift+F10 both open the focused row's own menu.
                assertThat(owner.canvas.getInputMap().get(KeyStroke.getKeyStroke("shift F10"))).isNotNull();
                assertThat(owner.canvas.getInputMap().get(KeyStroke.getKeyStroke("CONTEXT_MENU"))).isNotNull();
                owner.focusItem = "b";
                owner.canvas.getActionMap().get(owner.canvas.getInputMap().get(KeyStroke.getKeyStroke("shift F10"))).actionPerformed(null);
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).isEqualTo("Open Settings");
                assertThat(owner.selected).containsExactly("a");
                assertThat(events).isEmpty();
            } finally { release(context); }
            assertThatThrownBy(() -> new ListItem("h", Optional.empty(), List.of(new Text("h.t", READY, "H", TextRole.CAPTION)), List.of(), List.of(), true, Optional.of(List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        });
    }

    static ListItem heading(String id) {
        return new ListItem(id, Optional.empty(), List.of(new Text(id + ".title", READY, "Bundled (1 of 2 enabled)", TextRole.CAPTION)), List.of(), List.of(), true);
    }

    @Test void spaceOperatesARowsOnlySwitchOnceAndOtherwiseSelects() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var plain = new ListItem("c", Optional.empty(), List.of(new Text("c.name", READY, "Plain", TextRole.BODY)), List.of(), List.of());
            var spec = new ViewSpec(plugins(List.of(plugin("a", true, READY, Optional.empty()), plugin("b", false, OFF, Optional.empty()), plain), Set.of("a")));
            try (var view = new KitView(context, spec, events::add)) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("plugins");
                var space = owner.canvas.getActionMap().get(owner.canvas.getInputMap().get(KeyStroke.getKeyStroke("SPACE")));
                owner.focusItem = "a"; space.actionPerformed(null);
                assertThat(events).as("the switch reports once, the row is not re-selected").containsExactly(new UiEvent.Toggled("a.enabled", false));
                events.clear();
                owner.focusItem = "b"; space.actionPerformed(null);
                assertThat(events).as("a disabled switch ignores Space").isEmpty();
                assertThat(owner.selected).containsExactly("a");
                owner.focusItem = "c"; space.actionPerformed(null);
                assertThat(events).as("a row without a switch selects").containsExactly(new UiEvent.Selected("plugins", Set.of("c")));
            } finally { release(context); }
        });
    }

    @Test void theKeysContinueFromASelectionTheHostMade() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var rows = List.of(plugin("a", true, READY, Optional.empty()), plugin("b", true, READY, Optional.empty()), plugin("c", true, READY, Optional.empty()));
            try (var view = new KitView(context, new ViewSpec(plugins(rows, Set.of("a"))), events::add)) {
                view.setSize(420, 300); InteractiveRowsTest.layout(view);
                var owner = (ItemLists.ListControl) view.control("plugins");
                view.update(new ViewSpec(plugins(rows, Set.of("b"))));
                owner.canvas.getActionMap().get("DOWN").actionPerformed(null);
                assertThat(events).as("Down moves on from the host's row").containsExactly(new UiEvent.Selected("plugins", Set.of("c")));
            } finally { release(context); }
        });
    }

    @Test void aSwitchShowsItsTooltipUnlessAnErrorTakesPrecedence() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, stack(new Toggle("t", READY, "Enable X", false, "t", true, "Restart to apply")), e -> {})) {
                var button = find(view, "t", JButton.class);
                assertThat(button.getToolTipText()).isEqualTo("Restart to apply");
                assertThat(button.getAccessibleContext().getAccessibleName()).isEqualTo("Enable X");
                assertThat(button.getAccessibleContext().getAccessibleDescription()).isEqualTo("Restart to apply");
                view.update(stack(new Toggle("t", READY, "Enable X", false, "t", true)));
                assertThat(button.getToolTipText()).isEqualTo("Enable X");
                view.update(stack(new Toggle("t", new ControlState(true, true, false, "Failed"), "Enable X", false, "t", true, "Restart to apply")));
                assertThat(button.getToolTipText()).isEqualTo("Failed");
            } finally { release(context); }
        });
    }

    @Test void aSeverityTintsTheGlyphWithItsStatusColourAcrossThemes() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            assertThat(context.icons().names()).contains("error", "plugin", "info", "warning");
            Map<Severity, SemanticColor> expected = Map.of(Severity.INFO, SemanticColor.ACCENT, Severity.WARNING, SemanticColor.STATUS_WARNING,
                Severity.DANGER, SemanticColor.STATUS_DANGER);
            var spec = stack(new Nodes.Icon("i", READY, "info", Hue.NONE, IconSize.RAIL, "Information", Optional.of(Severity.INFO)),
                new Nodes.Icon("w", READY, "warning", Hue.NONE, IconSize.RAIL, "Warning", Optional.of(Severity.WARNING)),
                new Nodes.Icon("e", READY, "error", Hue.NONE, IconSize.RAIL, "Error", Optional.of(Severity.DANGER)));
            try (var view = new KitView(context, spec, e -> {})) {
                for (var theme : List.of(KitFixtures.dark(), KitFixtures.light())) {
                    context.binding().publish(new ThemePair(theme, theme));
                    for (var entry : Map.of("i", Severity.INFO, "w", Severity.WARNING, "e", Severity.DANGER).entrySet()) {
                        var label = find(view, entry.getKey(), JLabel.class);
                        assertThat(colours(label.getIcon())).as(entry.getKey()).containsOnly(theme.semantic().color(expected.get(entry.getValue())).getRGB() & 0xffffff);
                    }
                }
            } finally { release(context); }
        });
    }

    static Set<Integer> colours(Icon icon) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); icon.paintIcon(null, g, 0, 0); g.dispose();
        var result = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 255) result.add(argb & 0xffffff);
        }
        return result;
    }

    static final String LONG = "A plugin is unrestricted code running inside Jasper, with everything your account can reach. "
        + "The list above is what the plugin says it does; it is not a sandbox.";

    @Test void bodyTextFlowsInVerticalContainersKeepingLineBreaksAndInlineTextStaysOneLine() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                new Text("body", READY, "Fixture 1.0\nFrom Vendor\n\n" + LONG, TextRole.BODY),
                new Text("title", READY, "Title", TextRole.DETAIL_TITLE),
                new Text("strong", READY, LONG.replace(' ', '_'), TextRole.BODY_STRONG),
                new Stack("row", READY, LayoutRole.INLINE, List.of(new Text("inline", READY, LONG, TextRole.BODY))),
                new FormRow("form-row", READY, "Name", "", new Text("caption", READY, LONG, TextRole.CAPTION), Optional.empty()))));
            try (var view = new KitView(context, spec, e -> {})) {
                var body = find(view, "body", Labels.LiteralLabel.class);
                var inline = find(view, "inline", Labels.LiteralLabel.class);
                var title = find(view, "title", Labels.LiteralLabel.class);
                var strong = find(view, "strong", Labels.LiteralLabel.class);
                var caption = find(view, "caption", Labels.LiteralLabel.class);
                var metrics = body.getFontMetrics(body.getFont());
                int columns = DesignMetrics.DEFAULT.value(DesignMetrics.Role.TEXT_FLOW_COLUMNS);
                assertThat(columns).isEqualTo(44);
                assertThat(body.flow).isTrue();
                assertThat(body.getPreferredSize().width).as("no wider than 44 columns").isEqualTo(Math.min(metrics.stringWidth(LONG), columns * metrics.charWidth('m')));
                view.setSize(300, 600); InteractiveRowsTest.layout(view);
                int line = metrics.getHeight();
                assertThat(body.getHeight()).as("explicit breaks kept and the long paragraph wrapped").isGreaterThan(5 * line);
                assertThat(body.getHeight() % line).isZero();
                assertThat(inline.flow).as("inline stacks keep one truncated line").isFalse();
                assertThat(inline.getHeight()).isLessThanOrEqualTo(inline.getPreferredSize().height);
                assertThat(title.flow).as("titles are not body text").isFalse();
                assertThat(strong.flow).as("strong text keeps one line (review I2)").isFalse();
                assertThat(strong.getHeight()).isEqualTo(strong.getPreferredSize().height);
                assertThat(caption.wrap).as("FormRow captions keep their own wrapping").isTrue();
                assertThat(caption.flow).isFalse();
                // A theme change keeps the flow and the measured height.
                int before = body.getHeight();
                context.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.light()));
                InteractiveRowsTest.layout(view);
                assertThat(body.flow).isTrue();
                assertThat(body.getHeight()).isEqualTo(before);
            } finally { release(context); }
        });
    }

    @Test void aSheetsMessageWrapsInsteadOfWideningTheSheetAndItsActionsStayInOneRow() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var close = new Nodes.Button("close", READY, "Close", "", ButtonKind.PRIMARY, "close", "");
            var spec = new ViewSpec(new Form("message", READY, FormKind.SHEET, "Import failed", Optional.empty(),
                List.of(new Text("message-text", READY, LONG + " " + LONG, TextRole.DESCRIPTION)), Optional.empty(), List.of(close), "close"));
            try (var view = new KitView(context, spec, e -> {})) {
                var text = find(view, "message-text", Labels.LiteralLabel.class);
                var metrics = text.getFontMetrics(text.getFont());
                assertThat(view.getPreferredSize().width).as("the sheet no longer grows to the whole message")
                    .isLessThan(metrics.stringWidth(LONG + " " + LONG));
                view.setSize(view.getPreferredSize()); InteractiveRowsTest.layout(view);
                assertThat(text.getHeight()).as("several lines").isGreaterThan(metrics.getHeight());
                assertThat(text.getWidth() * (text.getHeight() / metrics.getHeight())).isGreaterThanOrEqualTo(metrics.stringWidth(LONG));
                var button = find(view, "close", JButton.class);
                assertThat(button.isShowing() || button.getWidth() > 0).isTrue();
            } finally { release(context); }
        });
    }

    /** Review I1/M4: an EMBEDDED button is content-sized for a host arrangement; STANDARD buttons are unchanged. */
    @Test void anEmbeddedButtonIsSizedToItsContentWithoutPaddingRingOrFill() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var spec = stack(
                new Nodes.Button("embedded", READY, "Open in Editor", "", ButtonKind.LINK, "open", "", ButtonPresentation.EMBEDDED),
                new Nodes.Button("standard", READY, "Open in Editor", "", ButtonKind.LINK, "open", ""),
                new Nodes.Button("close", READY, "", "close", ButtonKind.GHOST, "close", "Close", ButtonPresentation.EMBEDDED),
                new MenuButton("more", READY, "More ▾", "", ButtonKind.LINK, "", List.of(new MenuItem("four", "Four", "", "four", true, false)), "", ButtonPresentation.EMBEDDED));
            try (var view = new KitView(context, spec, events::add)) {
                var embedded = find(view, "embedded", JButton.class);
                var standard = find(view, "standard", JButton.class);
                var metrics = embedded.getFontMetrics(embedded.getFont());
                assertThat(embedded.getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
                assertThat(embedded.getPreferredSize()).isEqualTo(new Dimension(metrics.stringWidth("Open in Editor"), metrics.getHeight()));
                assertThat(standard.getPreferredSize().width).as("standard keeps its padding").isGreaterThan(embedded.getPreferredSize().width);
                assertThat(standard.getPreferredSize().height).as("and its minimum height").isGreaterThan(embedded.getPreferredSize().height);
                var close = find(view, "close", JButton.class);
                int glyph = com.formdev.flatlaf.util.UIScale.scale(context.metrics().value(DesignMetrics.Role.ICON_RAIL));
                assertThat(close.getIcon().getIconWidth()).isEqualTo(glyph);
                assertThat(close.getPreferredSize()).isEqualTo(new Dimension(glyph, glyph));
                var main = find(view, "more.main", JButton.class);
                var menuMetrics = main.getFontMetrics(main.getFont());
                assertThat(main.getInsets()).as("no chevron inset: the label carries its own").isEqualTo(new Insets(0, 0, 0, 0));
                assertThat(main.getPreferredSize().width).isEqualTo(menuMetrics.stringWidth("More ▾"));
                // No resting or hover fill: an embedded link paints only its text.
                embedded.setSize(embedded.getPreferredSize());
                embedded.getModel().setRollover(true);
                var image = new BufferedImage(embedded.getWidth(), embedded.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); embedded.paint(g); g.dispose();
                assertThat(image.getRGB(0, 0) >>> 24).as("corner untouched").isZero();
                embedded.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("open"));
            } finally { release(context); }
        });
    }

    /** Kit menu items (menu buttons and row menus) never render plugin-supplied markup, whatever look and feel installed them. */
    @Test void menuItemsNeverRenderMarkup() {
        Edt.run(() -> {
            // FlatLaf's menu item delegate parses markup as soon as text is set (the order dependence the app suite hit).
            var prior = UIManager.getLookAndFeel();
            FlatLafInstaller.install(KitFixtures.dark());
            var context = KitFixtures.context();
            var menu = List.of(new MenuItem("evil", "<html><b>Evil</b>", "", "evil", true, false));
            var spec = new ViewSpec(new Stack("root", READY, LayoutRole.VERTICAL, List.of(
                new MenuButton("more", READY, "More", "", ButtonKind.LINK, "", menu, ""),
                plugins(List.of(plugin("a", true, READY, Optional.of(menu))), Set.of()))));
            try (var view = new KitView(context, spec, e -> {})) {
                view.setSize(420, 240); InteractiveRowsTest.layout(view);
                var item = (JMenuItem) find(view, "more", JComponent.class).getComponentPopupMenu().getComponent(0);
                assertThat(item.getText()).isEqualTo("<html><b>Evil</b>");
                assertThat(item.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).isNull();
                var owner = (ItemLists.ListControl) view.control("plugins");
                owner.context("a", owner.canvas, 0, 0);
                var row = (JMenuItem) owner.popup.getComponent(0);
                assertThat(row.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey)).isNull();
            } finally {
                release(context);
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }

    @Test void aPointerOnlyViewNeverTakesFocusButItsControlsStillActAndAreNamed() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            var more = new MenuButton("more", READY, "More", "", ButtonKind.LINK, "", List.of(new MenuItem("four", "Four", "", "run:3", true, false)), "");
            var spec = new ViewSpec(new Stack("actions", READY, LayoutRole.INLINE, List.of(
                new Nodes.Button("one", READY, "Open", "", ButtonKind.LINK, "run:0", ""),
                new Nodes.Button("close", READY, "", "close", ButtonKind.GHOST, "close", "Close notification"), more)));
            try (var view = new KitView(context, spec, events::add, true)) {
                assertThat(view.pointerOnly()).isTrue();
                assertThat(focusable(view)).as("nothing in the view is focusable").isEmpty();
                find(view, "one", JButton.class).doClick(0);
                find(view, "close", JButton.class).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("run:0"), new UiEvent.Action("close"));
                assertThat(find(view, "close", JButton.class).getAccessibleContext().getAccessibleName()).isEqualTo("Close notification");
                view.update(new ViewSpec(new Stack("actions", READY, LayoutRole.INLINE, List.of(
                    new Nodes.Button("one", READY, "Open", "", ButtonKind.LINK, "run:0", ""),
                    new Nodes.Button("two", READY, "Retry", "", ButtonKind.LINK, "run:1", ""), more))));
                context.binding().publish(new ThemePair(KitFixtures.light(), KitFixtures.light()));
                assertThat(focusable(view)).as("controls mounted later and after a theme change stay unfocusable").isEmpty();
            } finally { release(context); }
            var ordinary = KitFixtures.context();
            try (var view = new KitView(ordinary, spec, e -> {})) {
                assertThat(view.pointerOnly()).isFalse();
                assertThat(focusable(view)).as("ordinary views are unchanged").isNotEmpty();
            } finally { release(ordinary); }
        });
    }

    static List<Component> focusable(Component component) {
        var result = new ArrayList<Component>();
        if (component instanceof AbstractButton && component.isFocusable()) result.add(component);
        if (component instanceof Container container) for (var child : container.getComponents()) result.addAll(focusable(child));
        return result;
    }
}
