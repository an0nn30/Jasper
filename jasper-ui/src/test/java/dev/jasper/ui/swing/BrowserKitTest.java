package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.MenuItem;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemePair;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Kit capabilities a file browser and a shortcut editor need: sorting, muted rows, row menus, tree columns, type-ahead,
 * ranges, revealing the host's selection, the overlay scroll policy and breadcrumbs. */
class BrowserKitTest {
    static final ControlState READY = ControlState.READY;

    static Table files(int count, Set<String> selected, SelectionMode mode, ScrollPolicy scroll, Optional<Boolean> nameSorted) {
        var rows = IntStream.range(0, count).mapToObj(i -> new TableRow("r" + i,
            Map.of("name", (char) ('a' + i % 26) + "file-" + i, "modified", "2026-10-0" + (i % 9 + 1), "size", i + " KB"),
            Optional.of("file"), i == 1, i == 2 ? Optional.of(List.of(new MenuItem("pin", "Pin", "bookmark", "pin", true, false)))
                : i == 3 ? Optional.of(List.of()) : Optional.empty())).toList();
        return new Table("files", READY, List.of(
                new Column("name", "Name", TextRole.BODY, false, 4.5, nameSorted),
                new Column("modified", "Modified", TextRole.DESCRIPTION, false, 1.75, Optional.empty()),
                new Column("size", "Size", TextRole.DESCRIPTION, true, 1, Optional.empty())),
            rows, selected, "open", List.of(new MenuItem("reveal", "Reveal", "", "reveal", true, false)), mode, scroll);
    }

    static MouseEvent click(Component on, int modifiers) {
        return new MouseEvent(on, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), modifiers, 2, 2, 1, false, MouseEvent.BUTTON1);
    }

    @Test void columnsShareTheWidthByWeightShowTheSortAndReportHeaderClicks() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(files(5, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(true))), events::add)) {
                view.setSize(725, 300); InteractiveRowsTest.layout(view);
                var name = find(view, "@table/files/header/name", JLabel.class);
                var modified = find(view, "@table/files/header/modified", JLabel.class);
                var size = find(view, "@table/files/header/size", JLabel.class);
                assertThat(name.getWidth()).as("name takes 4.5 shares").isGreaterThan(2 * modified.getWidth());
                assertThat(modified.getWidth()).isGreaterThan(size.getWidth());
                assertThat(name.getIcon()).as("sort indicator").isNotNull();
                assertThat(name.getAccessibleContext().getAccessibleName()).isEqualTo("Name, sorted ascending");
                assertThat(modified.getIcon()).isNull();
                var cell = find(view, "@table/files/r0/modified", JLabel.class);
                assertThat(SwingUtilities.convertPoint(cell, 0, 0, view).x).as("cells line up under their header")
                    .isEqualTo(SwingUtilities.convertPoint(modified, 0, 0, view).x);
                assertThat(cell.getWidth()).isEqualTo(modified.getWidth());
                for (var listener : modified.getMouseListeners()) listener.mouseClicked(click(modified, 0));
                assertThat(events).containsExactly(new UiEvent.Sorted("files", "modified"));
                view.update(new ViewSpec(files(5, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(false))));
                InteractiveRowsTest.layout(view);
                assertThat(find(view, "@table/files/header/name", JLabel.class).getAccessibleContext().getAccessibleName())
                    .isEqualTo("Name, sorted descending");
            } finally { release(context); }
        });
    }

    @Test void mutedRowsShowEveryCellDeEmphasizedAcrossThemes() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(files(5, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), e -> {})) {
                view.setSize(600, 300); InteractiveRowsTest.layout(view);
                var muted = context.theme().semantic().color(SemanticColor.TEXT_PLACEHOLDER);
                assertThat(find(view, "@table/files/r1/name", JLabel.class).getForeground()).isEqualTo(muted);
                assertThat(find(view, "@table/files/r1/size", JLabel.class).getForeground()).isEqualTo(muted);
                assertThat(find(view, "@table/files/r0/name", JLabel.class).getForeground())
                    .isEqualTo(context.theme().semantic().color(SemanticColor.TEXT_PRIMARY));
                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                InteractiveRowsTest.layout(view);
                assertThat(find(view, "@table/files/r1/name", JLabel.class).getForeground()).isEqualTo(light.semantic().color(SemanticColor.TEXT_PLACEHOLDER));
                assertThat(find(view, "@table/files/r0/name", JLabel.class).getForeground()).isEqualTo(light.semantic().color(SemanticColor.TEXT_PRIMARY));
            } finally { release(context); }
        });
    }

    @Test void aRowsOwnContextActionsReplaceTheTablesAndTreeItemsHaveMenus() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(files(5, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), events::add)) {
                view.setSize(600, 300); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                owner.context("r0", owner.canvas, 0, 0);
                assertThat(owner.popup.getComponentCount()).as("the table's shared menu acts on the selection, so an unselected row offers none").isZero();
                assertThat(events).as("a context press never changes the selection").isEmpty();
                owner.select("r0", false);
                events.clear();
                owner.context("r0", owner.canvas, 0, 0);
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).as("the table's menu on a selected row").isEqualTo("Reveal");
                owner.context("r2", owner.canvas, 0, 0);
                assertThat(owner.selected).as("the row under the pointer is the target, not selected").containsExactly("r0");
                assertThat(owner.popup.getComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).as("the row's own menu").isEqualTo("Pin");
                events.clear();
                ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                assertThat(events).as("a row's menu reports only its action").containsExactly(new UiEvent.Action("pin"));
                owner.context("r3", owner.canvas, 0, 0);
                assertThat(owner.popup.getComponentCount()).as("an empty list means no menu").isZero();
            } finally { release(context); }
            var treeEvents = new ArrayList<UiEvent>();
            var tree = new Tree("places", READY, List.of(new TreeItem("home", "Home", Optional.of("home"), true, List.of(), OptionalInt.empty(), false,
                "/Users/me", Optional.empty(), List.of(), Optional.of(List.of(new MenuItem("unpin", "Unpin", "", "unpin", true, false))))), Set.of(), Set.of(), "");
            var other = KitFixtures.context();
            try (var view = new KitView(other, new ViewSpec(tree), treeEvents::add)) {
                view.setSize(300, 200); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("places");
                owner.context("home", owner.canvas, 0, 0);
                assertThat(treeEvents).as("a right click does not select").isEmpty();
                assertThat(owner.selected).isEmpty();
                treeEvents.clear();
                ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                assertThat(treeEvents).as("never re-reports the row, so it cannot open it").containsExactly(new UiEvent.Action("unpin"));
            } finally { release(other); }
        });
    }

    static Tree keymap(boolean connected) {
        return new Tree("keys", READY, List.of(new TreeItem("tabs", "Tabs", Optional.of("folder"), false, List.of(
                new TreeItem("new", "New tab", Optional.empty(), true, List.of(), OptionalInt.empty(), true,
                    "Terminal window · Click to edit", Optional.empty(), List.of("Global", "⌘T"), Optional.empty()),
                new TreeItem("scrollback", "Clear Scrollback", Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                    "In a terminal pane", Optional.empty(), List.of("Terminal pane", "⌘⇧K"), Optional.empty())),
                OptionalInt.of(2), false, "Collapse section", connected ? Optional.of(StatusKind.SUCCESS) : Optional.empty(), List.of(), Optional.empty())),
            Set.of("tabs"), Set.of(), "edit");
    }

    @Test void treeRowsCarryTooltipsStatusAndAlignedTrailingColumnsAcrossThemes() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(keymap(true)), e -> {})) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("keys");
                assertThat(owner.mounted.get("new").getToolTipText()).isEqualTo("Terminal window · Click to edit");
                assertThat(owner.mounted.get("new").getAccessibleContext().getAccessibleDescription()).isEqualTo("Terminal window · Click to edit");
                assertThat(find(view, "@tree/keys/new/title", JLabel.class).getToolTipText()).as("the title shares the tooltip")
                    .isEqualTo("Terminal window · Click to edit");
                assertThat(find(view, "@tree/keys/tabs/status", JComponent.class)).as("status dot").isNotNull();
                assertThat(find(view, "@tree/keys/new/status", JComponent.class)).isNull();
                var where = find(view, "@tree/keys/new/trailing/0", JLabel.class);
                var binding = find(view, "@tree/keys/new/trailing/1", JLabel.class);
                assertThat(where.getText()).isEqualTo("Global");
                assertThat(binding.getText()).isEqualTo("⌘T");
                var otherWhere = find(view, "@tree/keys/scrollback/trailing/0", JLabel.class);
                var otherBinding = find(view, "@tree/keys/scrollback/trailing/1", JLabel.class);
                assertThat(where.getWidth()).as("columns share a width across rows").isEqualTo(otherWhere.getWidth()).isPositive();
                assertThat(SwingUtilities.convertPoint(where, 0, 0, view).x).isEqualTo(SwingUtilities.convertPoint(otherWhere, 0, 0, view).x);
                assertThat(SwingUtilities.convertPoint(binding, 0, 0, view).x).isEqualTo(SwingUtilities.convertPoint(otherBinding, 0, 0, view).x);
                assertThat(binding.getHorizontalAlignment()).isEqualTo(SwingConstants.RIGHT);
                assertThat(find(view, "@tree/keys/tabs/count", JLabel.class).getText()).isEqualTo("2");
                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                InteractiveRowsTest.layout(view);
                assertThat(find(view, "@tree/keys/new/trailing/1", JLabel.class).getForeground()).isEqualTo(light.semantic().color(SemanticColor.TEXT_SECONDARY));
                assertThat(owner.mounted.get("new").getToolTipText()).isEqualTo("Terminal window · Click to edit");
                view.update(new ViewSpec(keymap(false)));
                InteractiveRowsTest.layout(view);
                assertThat(find(view, "@tree/keys/tabs/status", JComponent.class)).isNull();
            } finally { release(context); }
        });
    }

    static KeyEvent typed(Component on, char c, long when) {
        return new KeyEvent(on, KeyEvent.KEY_TYPED, when, 0, KeyEvent.VK_UNDEFINED, c);
    }

    @Test void typingSelectsTheFirstRowStartingWithWhatWasTypedWithinASecond() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(files(40, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), events::add)) {
                view.setSize(600, 160); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed(owner.canvas, 'n', 1000));
                assertThat(events).containsExactly(new UiEvent.Selected("files", Set.of("r13")));
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed(owner.canvas, 'F', 1500));
                assertThat(events.getLast()).as("case-insensitive prefix 'nf'").isEqualTo(new UiEvent.Selected("files", Set.of("r13")));
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed(owner.canvas, 'o', 3000));
                assertThat(events.getLast()).as("a pause starts a new prefix").isEqualTo(new UiEvent.Selected("files", Set.of("r14")));
                assertThat(owner.scroll.getViewport().getViewPosition().y).as("the typed row is scrolled into view").isPositive();
                int before = events.size();
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed(owner.canvas, '\n', 5000));
                assertThat(events).as("control characters do not search").hasSize(before);
            } finally { release(context); }
            var trees = KitFixtures.context(); var treeEvents = new ArrayList<UiEvent>();
            try (var view = new KitView(trees, new ViewSpec(keymap(false)), treeEvents::add)) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("keys");
                for (var listener : owner.canvas.getKeyListeners()) listener.keyTyped(typed(owner.canvas, 'c', 1000));
                assertThat(treeEvents).containsExactly(new UiEvent.Selected("keys", Set.of("scrollback")));
            } finally { release(trees); }
        });
    }

    @Test void shiftExtendsAMultipleSelectionFromItsAnchor() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(files(10, Set.of(), SelectionMode.MULTIPLE, ScrollPolicy.STANDARD, Optional.empty())), events::add)) {
                view.setSize(600, 400); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                var first = owner.mounted.get("r2");
                for (var listener : first.getMouseListeners()) listener.mouseClicked(click(first, 0));
                var last = owner.mounted.get("r5");
                for (var listener : last.getMouseListeners()) listener.mouseClicked(click(last, InputEvent.SHIFT_DOWN_MASK));
                assertThat(events.getLast()).isEqualTo(new UiEvent.Selected("files", Set.of("r2", "r3", "r4", "r5")));
                owner.canvas.getActionMap().get("shift DOWN").actionPerformed(null);
                assertThat(events.getLast()).as("Shift+Down extends from the same anchor").isEqualTo(new UiEvent.Selected("files", Set.of("r2", "r3", "r4", "r5", "r6")));
                owner.canvas.getActionMap().get("shift UP").actionPerformed(null);
                owner.canvas.getActionMap().get("shift UP").actionPerformed(null);
                assertThat(events.getLast()).isEqualTo(new UiEvent.Selected("files", Set.of("r2", "r3", "r4")));
                var single = owner.mounted.get("r8");
                for (var listener : single.getMouseListeners()) listener.mouseClicked(click(single, 0));
                assertThat(events.subList(events.size() - 2, events.size())).as("a plain click starts over")
                    .containsExactly(new UiEvent.Selected("files", Set.of("r8")), new UiEvent.Clicked("files", "r8"));
            } finally { release(context); }
            var singleContext = KitFixtures.context(); var singleEvents = new ArrayList<UiEvent>();
            try (var view = new KitView(singleContext, new ViewSpec(files(10, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), singleEvents::add)) {
                view.setSize(600, 400); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                for (var listener : owner.mounted.get("r2").getMouseListeners()) listener.mouseClicked(click(owner.mounted.get("r2"), 0));
                for (var listener : owner.mounted.get("r5").getMouseListeners()) listener.mouseClicked(click(owner.mounted.get("r5"), InputEvent.SHIFT_DOWN_MASK));
                assertThat(singleEvents.getLast()).as("single selection ignores Shift").isEqualTo(new UiEvent.Selected("files", Set.of("r5")));
            } finally { release(singleContext); }
        });
    }

    @Test void aSelectionTheHostMakesIsScrolledIntoViewAndAThemeSwitchKeepsSelectionAndScroll() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(files(200, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), e -> {})) {
                view.setSize(600, 200); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                assertThat(owner.scroll.getViewport().getViewPosition().y).isZero();
                view.update(new ViewSpec(files(200, Set.of("r150"), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())));
                InteractiveRowsTest.layout(view);
                int top = owner.scroll.getViewport().getViewPosition().y, height = owner.scroll.getViewport().getExtentSize().height;
                assertThat(150 * owner.rowHeight).isBetween(top, top + height - owner.rowHeight);
                owner.scroll.getViewport().setViewPosition(new Point(0, top + 3 * owner.rowHeight));
                int scrolled = owner.scroll.getViewport().getViewPosition().y;
                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                InteractiveRowsTest.layout(view);
                assertThat(owner.scroll.getViewport().getViewPosition().y).as("a theme switch keeps the scroll position").isEqualTo(scrolled);
                assertThat(owner.selected).containsExactly("r150");
                view.update(new ViewSpec(files(200, Set.of("r150"), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.of(true))));
                InteractiveRowsTest.layout(view);
                assertThat(owner.scroll.getViewport().getViewPosition().y).as("an unchanged selection is not revealed again").isEqualTo(scrolled);
            } finally { release(context); }
        });
    }

    static long paintedPixels(JScrollBar bar) {
        var image = new BufferedImage(Math.max(1, bar.getWidth()), Math.max(1, bar.getHeight()), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { bar.paint(g); } finally { g.dispose(); }
        return Arrays.stream(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()))
            .filter(pixel -> pixel >>> 24 != 0).count();
    }

    /** Ported from the app's FileChooserScrollTest at equal strength: the overlay thumb is a kit scroll policy. */
    @Test void theOverlayPolicyShowsATransientThumbWithoutReflowAndHidesAfterScrollingAndRemoval() throws Exception {
        var context = new AtomicReference<KitContext>();
        var view = new AtomicReference<KitView>();
        var scroll = new AtomicReference<JScrollPane>();
        try {
            Edt.run(() -> {
                context.set(KitFixtures.context());
                view.set(new KitView(context.get(), new ViewSpec(files(80, Set.of(), SelectionMode.SINGLE, ScrollPolicy.OVERLAY, Optional.empty())), e -> {}));
                var pane = ((Tables.TableControl) view.get().control("files")).scroll;
                scroll.set(pane);
                pane.setSize(500, 180);
                view.get().addNotify();
                InteractiveRowsTest.layout(pane);
                var bar = pane.getVerticalScrollBar();
                assertThat(pane.isShowing()).isTrue();
                assertThat(paintedPixels(bar)).as("idle scrollbar is hidden").isZero();
                assertThat(bar.contains(2, 20)).as("hidden scrollbar does not intercept row clicks").isFalse();
                int width = pane.getViewport().getWidth();
                assertThat(width).as("the rows use the scrollbar's space").isEqualTo(pane.getWidth());
                bar.setValue(50);
                InteractiveRowsTest.layout(pane);
                assertThat(paintedPixels(bar)).as("scrolling reveals the thumb").isPositive();
                assertThat(pane.getViewport().getWidth()).isEqualTo(width);
                bar.setValueIsAdjusting(true);
            });
            // Let the real idle timer fire while a thumb drag is still held.
            Thread.sleep(1300);
            Edt.run(() -> {
                var bar = scroll.get().getVerticalScrollBar();
                assertThat(paintedPixels(bar)).as("a held drag stays visible").isPositive();
                bar.setValueIsAdjusting(false);
            });
            Thread.sleep(1300);
            Edt.run(() -> {
                var pane = scroll.get();
                var bar = pane.getVerticalScrollBar();
                assertThat(paintedPixels(bar)).as("idle after scrolling").isZero();
                bar.setValue(100);
                assertThat(paintedPixels(bar)).isPositive();
                pane.removeNotify();
                assertThat(paintedPixels(bar)).as("removal stops and hides the scrolling affordance").isZero();
            });
        } finally {
            Edt.run(() -> {
                if (view.get() != null) { if (view.get().isDisplayable()) view.get().removeNotify(); view.get().close(); }
                if (context.get() != null) release(context.get());
            });
        }
    }

    @Test void theStandardPolicyKeepsAReservedScrollBarAndPolicyChangesRemount() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(files(80, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), e -> {})) {
                var pane = ((Tables.TableControl) view.control("files")).scroll;
                pane.setSize(500, 180); InteractiveRowsTest.layout(pane);
                assertThat(pane.getViewport().getWidth()).as("a standard bar reserves its width").isLessThan(pane.getWidth());
                view.update(new ViewSpec(files(80, Set.of(), SelectionMode.SINGLE, ScrollPolicy.OVERLAY, Optional.empty())));
                var overlay = ((Tables.TableControl) view.control("files")).scroll;
                overlay.setSize(500, 180); InteractiveRowsTest.layout(overlay);
                assertThat(overlay.getViewport().getWidth()).isEqualTo(overlay.getWidth());
            } finally { release(context); }
        });
    }

    static Breadcrumbs crumbs(boolean editing, String value, String... labels) {
        var list = new ArrayList<Crumb>();
        for (int i = 0; i < labels.length; i++)
            list.add(new Crumb("c" + i, labels[i], i == 0 ? List.of() : List.of(new MenuItem("pin", "Pin", "bookmark", "pin", true, false))));
        return new Breadcrumbs("path", READY, list, "Location", editing, value, "edit", "go", "done");
    }

    @Test void breadcrumbsReportCrumbsMenusAndTheFreeAreaAndKeepTheLastCrumbVisible() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(crumbs(false, "", "Users", "me", "projects", "jasper", "src")), events::add)) {
                view.setSize(600, 60); InteractiveRowsTest.layout(view);
                var crumb = find(view, "@crumbs/path/c3", JButton.class);
                crumb.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("path", Set.of("c3")));
                assertThat(crumb.isFocusable()).isFalse();
                assertThat(crumb.getClientProperty(com.formdev.flatlaf.FlatClientProperties.MINIMUM_WIDTH)).as("no minimum width").isEqualTo(0);
                assertThat(crumb.getMargin().left + crumb.getMargin().right).as("tight side margins").isLessThanOrEqualTo(8 * com.formdev.flatlaf.util.UIScale.scale(1));
                var separator = find(view, "@crumbs/path/c3/separator", JLabel.class);
                assertThat(separator.getText()).isEqualTo("›");
                assertThat(separator.getInsets().left + separator.getInsets().right).isLessThanOrEqualTo(4 * com.formdev.flatlaf.util.UIScale.scale(1));
                assertThat(find(view, "@crumbs/path/c0/separator", JLabel.class)).as("no separator before the first crumb").isNull();
                var control = (BreadcrumbControls.BreadcrumbsControl) view.control("path");
                events.clear();
                control.context("c2", crumb, 0, 0);
                ((JMenuItem) control.popup.getComponent(0)).doClick(0);
                assertThat(events).as("a crumb's menu acts without navigating").containsExactly(new UiEvent.Action("pin"));
                control.context("c0", crumb, 0, 0);
                assertThat(control.popup.getComponentCount()).as("a crumb without actions has no menu").isZero();
                events.clear();
                var area = find(view, "@crumbs/path/crumbs", JComponent.class);
                for (var listener : area.getMouseListeners()) listener.mouseClicked(click(area, 0));
                assertThat(events).containsExactly(new UiEvent.Action("edit"));

                view.setSize(160, 60); InteractiveRowsTest.layout(view);
                var last = find(view, "@crumbs/path/c4", JButton.class);
                var first = find(view, "@crumbs/path/c0", JButton.class);
                assertThat(last.getX() + last.getWidth()).as("the current folder stays visible").isLessThanOrEqualTo(area.getWidth());
                assertThat(last.getWidth()).isEqualTo(last.getPreferredSize().width);
                assertThat(first.getX()).as("leading crumbs slide out on the left").isNegative();
            } finally { release(context); }
        });
    }

    @Test void breadcrumbsBecomeAFieldThatReportsTypingSubmitAndCancelAndSurviveAThemeSwitch() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(crumbs(false, "", "Users", "me")), events::add)) {
                view.setSize(400, 60); InteractiveRowsTest.layout(view);
                var box = find(view, "@crumbs/path/box", JComponent.class);
                assertThat(box.isOpaque()).as("it paints its own rounded field").isFalse();
                int height = box.getPreferredSize().height;
                assertThat(height).isGreaterThanOrEqualTo(new JTextField().getPreferredSize().height);
                box.setSize(300, height); InteractiveRowsTest.layout(box);
                var image = new BufferedImage(300, height, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { box.paint(g); } finally { g.dispose(); }
                assertThat(image.getRGB(299, height / 2) >>> 24).as("an outline at the right edge").isNotZero();
                assertThat(new Color(image.getRGB(296, height / 2), true)).as("the field's background inside")
                    .isEqualTo(context.theme().semantic().color(SemanticColor.SURFACE_INPUT));

                view.update(new ViewSpec(crumbs(true, "/Users/me", "Users", "me")));
                var field = find(view, "@crumbs/path/field", JTextField.class);
                assertThat(field.isVisible() && field.isShowing() || field.getParent().isVisible()).isTrue();
                assertThat(field.getText()).isEqualTo("/Users/me");
                assertThat(field.getSelectedText()).as("the path is selected for replacement").isEqualTo("/Users/me");
                assertThat(field.getAccessibleContext().getAccessibleName()).isEqualTo("Location");
                events.clear();
                field.setText("/tmp");
                assertThat(events).contains(new UiEvent.Edited("path", "/tmp"));
                var light = KitFixtures.light();
                context.binding().publish(new ThemePair(light, light));
                assertThat(field.getText()).as("a theme switch keeps the typed path").isEqualTo("/tmp");
                events.clear();
                field.getActionMap().get(field.getInputMap().get(KeyStroke.getKeyStroke("ENTER"))).actionPerformed(null);
                assertThat(events).containsExactly(new UiEvent.Action("go"));
                events.clear();
                field.getActionMap().get(field.getInputMap().get(KeyStroke.getKeyStroke("ESCAPE"))).actionPerformed(null);
                assertThat(events).containsExactly(new UiEvent.Action("done"));
                view.update(new ViewSpec(crumbs(false, "", "Users", "me", "tmp")));
                assertThat(find(view, "@crumbs/path/c2", JButton.class).getText()).isEqualTo("tmp");
                events.clear();
                for (var listener : field.getFocusListeners())
                    listener.focusLost(new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST));
                assertThat(events).as("leaving a field that is no longer editing says nothing").isEmpty();
            } finally { release(context); }
        });
    }

    @Test void actionsTooWideForOneRowWrapAtTheirNaturalWidthsInsteadOfBeingCutShort() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var buttons = List.<Node>of(new Nodes.Button("set", READY, "Set", "", ButtonKind.PRIMARY, "set", ""),
                new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", ""),
                new Nodes.Button("remove", READY, "Remove shortcut", "", ButtonKind.GHOST, "remove", ""),
                new Nodes.Button("restore", READY, "Restore default", "", ButtonKind.GHOST, "restore", ""));
            try (var view = new KitView(context, new ViewSpec(new Nodes.Stack("actions", READY, LayoutRole.ACTIONS, buttons)), e -> {})) {
                var row = find(view, "actions", JComponent.class);
                int natural = row.getPreferredSize().width;
                view.setSize(natural + 10, 200); InteractiveRowsTest.layout(view);
                var restore = find(view, "restore", JButton.class);
                assertThat(restore.getY()).as("wide enough: one row").isEqualTo(find(view, "set", JButton.class).getY());
                view.setSize(natural * 2 / 3, 200); row.setSize(natural * 2 / 3, 200); InteractiveRowsTest.layout(view);
                assertThat(restore.getY()).as("Restore wrapped to a second row").isGreaterThan(0);
                assertThat(restore.getWidth()).as("at its natural width").isEqualTo(restore.getPreferredSize().width);
                assertThat(Composition.height(row, natural * 2 / 3)).as("the stack grows to show both rows")
                    .isGreaterThanOrEqualTo(restore.getY() + restore.getHeight());
            } finally { release(context); }
        });
    }

    @Test void aLeftSingleClickReportsClickedWhileKeysRightClicksAndDoubleClicksDoNot() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(keymap(false)), events::add)) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("keys");
                var title = find(view, "@tree/keys/new/title", JLabel.class);
                for (var listener : title.getMouseListeners()) listener.mouseClicked(click(title, 0));
                assertThat(events).containsExactly(new UiEvent.Selected("keys", Set.of("new")), new UiEvent.Clicked("keys", "new"));
                events.clear();
                var right = new MouseEvent(title, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 2, 2, 1, true, MouseEvent.BUTTON3);
                var other = find(view, "@tree/keys/scrollback/title", JLabel.class);
                var rightOther = new MouseEvent(other, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_DOWN_MASK, 2, 2, 1, true, MouseEvent.BUTTON3);
                for (var listener : other.getMouseListeners()) listener.mousePressed(rightOther);
                assertThat(events).as("a right click neither selects nor clicks").isEmpty();
                assertThat(owner.selected).containsExactly("new");
                for (var listener : other.getMouseListeners())
                    listener.mouseClicked(new MouseEvent(other, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false, MouseEvent.BUTTON3));
                assertThat(events).as("nor does a right-button click").isEmpty();
                events.clear();
                owner.canvas.getActionMap().get("DOWN").actionPerformed(null);
                owner.canvas.getActionMap().get("ENTER").actionPerformed(null);
                assertThat(events).as("keys select and Enter activates; no click").noneMatch(event -> event instanceof UiEvent.Clicked)
                    .contains(new UiEvent.Action("edit"));
                events.clear();
                var twice = new MouseEvent(title, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 2, false, MouseEvent.BUTTON1);
                for (var listener : title.getMouseListeners()) listener.mouseClicked(twice);
                assertThat(events).as("the second click of a double click activates").containsExactly(
                    new UiEvent.Selected("keys", Set.of("new")), new UiEvent.Action("edit"));
                assertThat(right.isPopupTrigger()).isTrue();
            } finally { release(context); }
            var tables = KitFixtures.context(); var tableEvents = new ArrayList<UiEvent>();
            try (var view = new KitView(tables, new ViewSpec(files(5, Set.of(), SelectionMode.SINGLE, ScrollPolicy.STANDARD, Optional.empty())), tableEvents::add)) {
                view.setSize(600, 300); InteractiveRowsTest.layout(view);
                var row = ((Tables.TableControl) view.control("files")).mounted.get("r1");
                for (var listener : row.getMouseListeners()) listener.mouseClicked(click(row, 0));
                assertThat(tableEvents).containsExactly(new UiEvent.Selected("files", Set.of("r1")), new UiEvent.Clicked("files", "r1"));
            } finally { release(tables); }
            var lists = KitFixtures.context(); var listEvents = new ArrayList<UiEvent>();
            try (var view = new KitView(lists, new ViewSpec(InteractiveRowsTest.list(List.of(InteractiveRowsTest.item(0)), Set.of())), listEvents::add)) {
                view.setSize(400, 200); InteractiveRowsTest.layout(view);
                var row = ((ItemLists.ListControl) view.control("servers")).mounted.get("item0");
                for (var listener : row.getMouseListeners()) listener.mouseClicked(click(row, 0));
                // Plan 2 Task 8-kit (ruling G2): item-list rows report a plain left click too, after the selection.
                assertThat(listEvents).as("item lists report Clicked like tables and trees")
                    .containsExactly(new UiEvent.Selected("servers", Set.of("item0")), new UiEvent.Clicked("servers", "item0"));
            } finally { release(lists); }
        });
    }

    @Test void trailingTreeColumnsKeepTheirMinimumWidths() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var narrow = new Tree("keys", READY, List.of(new TreeItem("a", "Action", Optional.empty(), true, List.of(), OptionalInt.empty(), false,
                "", Optional.empty(), List.of("G", "K"), Optional.empty())), Set.of(), Set.of(), "");
            try (var view = new KitView(context, new ViewSpec(narrow), e -> {})) {
                view.setSize(500, 200); InteractiveRowsTest.layout(view);
                var detail = find(view, "@tree/keys/a/trailing/0", JLabel.class);
                var value = find(view, "@tree/keys/a/trailing/1", JLabel.class);
                assertThat(detail.getWidth()).as("detail column minimum").isGreaterThanOrEqualTo(ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.TREE_DETAIL_COLUMN));
                assertThat(value.getWidth()).as("value column minimum").isGreaterThanOrEqualTo(ThemePainting.metric(context, dev.jasper.ui.metrics.DesignMetrics.Role.TREE_VALUE_COLUMN));
                assertThat(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.TREE_DETAIL_COLUMN)).isEqualTo(88);
                assertThat(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.TREE_VALUE_COLUMN)).isEqualTo(96);
            } finally { release(context); }
        });
    }

    @Test void formFootersAndEmptyStateActionsStillLayOutInOneRowAtNormalWidths() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            var save = new Nodes.Button("save", READY, "Save", "", ButtonKind.PRIMARY, "save", "");
            var cancel = new Nodes.Button("cancel", READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", "");
            var form = new Form("form", READY, FormKind.SHEET, "Sheet", Optional.empty(),
                List.of(new Text("body", READY, "Body", TextRole.BODY)), Optional.empty(), List.of(cancel, save), "cancel");
            try (var view = new KitView(context, new ViewSpec(form), e -> {})) {
                view.setSize(480, 300); InteractiveRowsTest.layout(view);
                var a = find(view, "save", JButton.class); var b = find(view, "cancel", JButton.class);
                assertThat(a.getY()).as("one footer row").isEqualTo(b.getY());
                assertThat(a.getWidth()).isPositive(); assertThat(b.getWidth()).isPositive();
            } finally { release(context); }
            var other = KitFixtures.context();
            var empty = new Empty("empty", READY, new Nodes.Icon("i", READY, "folder", Hue.NONE, IconSize.EMPTY, "Empty"), "Nothing", "",
                List.of(new Nodes.Button("one", READY, "Retry", "", ButtonKind.PRIMARY, "one", ""), new Nodes.Button("two", READY, "Cancel connection", "", ButtonKind.SECONDARY, "two", "")));
            try (var view = new KitView(other, new ViewSpec(empty), e -> {})) {
                view.setSize(480, 300); InteractiveRowsTest.layout(view);
                var a = find(view, "one", JButton.class); var b = find(view, "two", JButton.class);
                assertThat(a.getY()).as("one action row").isEqualTo(b.getY());
                assertThat(a.getWidth()).isEqualTo(a.getPreferredSize().width);
                assertThat(b.getWidth()).isEqualTo(b.getPreferredSize().width);
            } finally { release(other); }
        });
    }

    @Test void aSingleLineFieldNeverClipsAnEnlargedFont() {
        Edt.run(() -> {
            Object old = UIManager.get("defaultFont");
            var context = KitFixtures.context();
            try {
                for (int size : new int[] {13, 18, 28}) {
                    UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, size));
                    try (var view = new KitView(context, KitViewTest.stack(KitViewTest.field("search", FieldKind.SEARCH, "Query")), e -> {})) {
                        view.setSize(400, view.getPreferredSize().height); InteractiveRowsTest.layout(view);
                        var input = editor(view, "search");
                        assertThat(input.getHeight()).as("input at %s pt gets its preferred height", size).isGreaterThanOrEqualTo(input.getPreferredSize().height);
                        if (size == 13) assertThat(input.getParent().getHeight()).as("normal fonts keep the designed field height")
                            .isEqualTo(ThemePainting.height(context, dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT, dev.jasper.ui.metrics.Typography.Role.BODY));
                    }
                }
            } finally { UIManager.put("defaultFont", old); release(context); }
        });
    }

    static Tree clickTree() {
        var keys = keymap(false);
        return new Tree(keys.id(), keys.state(), keys.roots(), keys.expandedIds(), keys.selectedIds(), "");
    }

    static MouseEvent clicked(Component on, int count, int modifiers) {
        return new MouseEvent(on, MouseEvent.MOUSE_CLICKED, 0, modifiers, 2, 2, count, false, MouseEvent.BUTTON1);
    }

    @Test void aDoubleClickWithoutAnActivateActionClicksOnceAndEnterPassesOnToTheHost() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(clickTree()), events::add)) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("keys");
                var title = find(view, "@tree/keys/tabs/title", JLabel.class);
                for (var listener : title.getMouseListeners()) listener.mouseClicked(clicked(title, 1, 0));
                for (var listener : title.getMouseListeners()) listener.mouseClicked(clicked(title, 2, 0));
                assertThat(events).as("the double click's second click only selects").containsExactly(
                    new UiEvent.Selected("keys", Set.of("tabs")), new UiEvent.Clicked("keys", "tabs"), new UiEvent.Selected("keys", Set.of("tabs")));
                var enter = owner.canvas.getActionMap().get(owner.canvas.getInputMap().get(KeyStroke.getKeyStroke("ENTER")));
                assertThat(enter.isEnabled()).as("with no activate action Enter is not the rows' to consume").isFalse();
                var host = new java.util.concurrent.atomic.AtomicInteger();
                view.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ENTER"), "host");
                view.getActionMap().put("host", new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) { host.incrementAndGet(); }
                });
                SwingUtilities.processKeyBindings(new java.awt.event.KeyEvent(owner.canvas, java.awt.event.KeyEvent.KEY_PRESSED, 0, 0, java.awt.event.KeyEvent.VK_ENTER, '\n'));
                assertThat(host.get()).as("Enter reaches the host's binding").isEqualTo(1);
            } finally { release(context); }
        });
    }

    @Test void aClickWhosePressOpenedTheContextMenuNeitherSelectsNorClicks() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(keymap(false)), events::add)) {
                view.setSize(500, 300); InteractiveRowsTest.layout(view);
                var owner = (Trees.TreeControl) view.control("keys");
                for (Component target : List.of(find(view, "@tree/keys/scrollback/title", JLabel.class), owner.mounted.get("scrollback"))) {
                    // macOS: Ctrl+click is the popup trigger on press, then arrives as a BUTTON1 click.
                    var press = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0, InputEvent.CTRL_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, true, MouseEvent.BUTTON1);
                    for (var listener : target.getMouseListeners()) listener.mousePressed(press);
                    for (var listener : target.getMouseListeners()) listener.mouseClicked(clicked(target, 1, InputEvent.CTRL_DOWN_MASK));
                    assertThat(events).as("no selection and no click after a context press").isEmpty();
                    assertThat(owner.selected).isEmpty();
                }
                var title = find(view, "@tree/keys/scrollback/title", JLabel.class);
                for (var listener : title.getMouseListeners()) listener.mouseClicked(clicked(title, 1, 0));
                assertThat(events).as("the next plain click works").containsExactly(new UiEvent.Selected("keys", Set.of("scrollback")), new UiEvent.Clicked("keys", "scrollback"));
            } finally { release(context); }
        });
    }

    @Test void onlyAPlainLeftClickReportsClicked() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(files(10, Set.of(), SelectionMode.MULTIPLE, ScrollPolicy.STANDARD, Optional.empty())), events::add)) {
                view.setSize(600, 400); InteractiveRowsTest.layout(view);
                var owner = (Tables.TableControl) view.control("files");
                var row = owner.mounted.get("r2");
                int menu = InputEvent.META_DOWN_MASK;
                for (int modifiers : new int[] {InputEvent.CTRL_DOWN_MASK, menu, InputEvent.ALT_DOWN_MASK}) {
                    events.clear();
                    for (var listener : row.getMouseListeners()) listener.mouseClicked(clicked(row, 1, modifiers));
                    assertThat(events).as("modifiers %s", modifiers).noneMatch(event -> event instanceof UiEvent.Clicked).isNotEmpty();
                }
                events.clear();
                for (var listener : row.getMouseListeners()) listener.mouseClicked(clicked(row, 1, InputEvent.CTRL_DOWN_MASK));
                assertThat(events).as("a deselecting toggle click").noneMatch(event -> event instanceof UiEvent.Clicked);
                events.clear();
                for (var listener : row.getMouseListeners()) listener.mouseClicked(clicked(row, 1, 0));
                assertThat(events).containsExactly(new UiEvent.Selected("files", Set.of("r2")), new UiEvent.Clicked("files", "r2"));
            } finally { release(context); }
        });
    }

    @Test void newPlaceGlyphsAreNamedKitArtwork() {
        try (var icons = new dev.jasper.ui.icons.IconRegistry()) {
            assertThat(icons.names()).contains("home", "desktop", "preview");
        }
    }
}
