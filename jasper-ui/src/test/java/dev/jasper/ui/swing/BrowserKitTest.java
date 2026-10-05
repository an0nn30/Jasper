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
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).as("the table's menu").isEqualTo("Reveal");
                owner.context("r2", owner.canvas, 0, 0);
                assertThat(owner.popup.getComponentCount()).isEqualTo(1);
                assertThat(((JMenuItem) owner.popup.getComponent(0)).getText()).as("the row's own menu").isEqualTo("Pin");
                events.clear();
                ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("files", Set.of("r2")), new UiEvent.Action("pin"));
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
                assertThat(treeEvents).as("the right-clicked row is selected first").containsExactly(new UiEvent.Selected("places", Set.of("home")));
                treeEvents.clear();
                ((JMenuItem) owner.popup.getComponent(0)).doClick(0);
                assertThat(treeEvents).containsExactly(new UiEvent.Selected("places", Set.of("home")), new UiEvent.Action("unpin"));
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
                assertThat(events.getLast()).as("a plain click starts over").isEqualTo(new UiEvent.Selected("files", Set.of("r8")));
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
                assertThat(events).containsExactly(new UiEvent.Selected("path", Set.of("c2")), new UiEvent.Action("pin"));
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

    @Test void newPlaceGlyphsAreNamedKitArtwork() {
        try (var icons = new dev.jasper.ui.icons.IconRegistry()) {
            assertThat(icons.names()).contains("home", "desktop", "preview");
        }
    }
}
