package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's ordered-list point: the classic side column acting on the selection; the default per-row commands. */
@SuppressWarnings("try")
class ClassicOrderedTest {
    static final ControlState READY = ControlState.READY;

    static ListItem marker(String id, String text) {
        return new ListItem(id, Optional.empty(), List.of(new Nodes.Text(id + "-text", READY, text, Nodes.TextRole.MONO)), List.of(), List.of());
    }

    static Nodes.ItemList markers(List<ListItem> items, Optional<Nodes.Button> add) {
        return new Nodes.ItemList("markers", READY, Nodes.ListKind.ORDERED, items, Set.of(), "", List.of(), Nodes.SelectionMode.SINGLE, add);
    }

    static List<ListItem> three() { return List.of(marker("csproj", "*.csproj"), marker("xcode", "*.xcodeproj"), marker("flake", "flake.nix")); }

    /** The Project Index composition: the list, then its entry row, in a VERTICAL stack. */
    static ViewSpec withEntry(List<ListItem> items) {
        var entry = new Nodes.Stack("entry", READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field("marker", READY, Nodes.FieldKind.TEXT, "", "Add…", "Marker", "", Optional.empty(), Nodes.FieldPresentation.EMBEDDED),
            new Nodes.Button("marker-add", READY, "Add", "", Nodes.ButtonKind.SECONDARY, "marker-add", "")));
        return new ViewSpec(new Nodes.Stack("group", READY, Nodes.LayoutRole.VERTICAL, List.of(markers(items, Optional.empty()), entry)));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y), true); }

    static Buttons.KitButton command(KitView view, String suffix) { return find(view, "@ordered/markers/side/" + suffix, Buttons.KitButton.class); }

    /** Final review: a list that keeps its id but stops being ORDERED gives back its side column, box and layout. */
    @Test void aListThatStopsBeingOrderedGivesBackItsSideColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, new ViewSpec(markers(three(), Optional.empty())), e -> {})) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var side = list.side;
                assertThat(side).isNotNull();
                view.update(new ViewSpec(new Nodes.ItemList("markers", READY, Nodes.ListKind.SINGLE, three(), Set.of(), "", List.of(),
                    Nodes.SelectionMode.SINGLE, Optional.empty())));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(view.control("markers")).as("the control is kept").isSameAs(list);
                assertThat(list.side).isNull();
                assertThat(side.getParent()).isNull();
                assertThat(list.scroll.getLayout()).isInstanceOf(ScrollPaneLayout.UIResource.class);
                assertThat(list.scroll.getInsets()).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
            } finally { release(context); }
        });
    }

    /** Final review: an ORDERED list without selection keeps its row commands under the classic face; it has nothing to select. */
    @Test void aPassiveOrderedListKeepsItsRowCommands() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            var passive = new Nodes.ItemList("markers", READY, Nodes.ListKind.ORDERED, three(), Set.of(), "", List.of(), Nodes.SelectionMode.NONE, Optional.empty());
            try (var view = new KitView(context, new ViewSpec(passive), events::add)) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(list.side).isNull();
                find(view, list.internalId("flake", "up"), AbstractButton.class).doClick(0);
                find(view, list.internalId("csproj", "remove"), AbstractButton.class).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Reordered("markers", "flake", 1), new UiEvent.Removed("markers", "csproj"));
            } finally { release(context); }
        });
    }

    /** Review Focus 2: nothing to act on disables every command; the ends disable Move Up and Move Down. */
    @Test void theSideColumnActsOnTheSelectionAndDisablesAtTheEnds() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, withEntry(three()), events::add)) {
                KitFixtures.capture(context, view.spec(), 600, 120, "ordered");
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(list.side).isNotNull();
                assertThat(List.of(command(view, "remove").isEnabled(), command(view, "up").isEnabled(), command(view, "down").isEnabled()))
                    .containsExactly(false, false, false);
                list.select("flake", false);
                KitFixtures.render(view, 600, 120, 1);
                assertThat(List.of(command(view, "remove").isEnabled(), command(view, "up").isEnabled(), command(view, "down").isEnabled()))
                    .containsExactly(true, true, false);
                assertThat(list.selectionShown(2)).as("the selection shows while a button has focus").isTrue();
                var caption = new Nodes.Text("index", READY, "3", Nodes.TextRole.MONO_CAPTION);
                assertThat(find(view, list.internalId("flake", "index"), JLabel.class).getForeground()).as("a selected row's index keeps its ink")
                    .isEqualTo(ThemePainting.color(context, Labels.TextControl.onSelection(caption, InteractiveRows.Ink.SELECTION)));
                events.clear();
                command(view, "up").doClick(0);
                assertThat(events).containsExactly(new UiEvent.Reordered("markers", "flake", 1));
                list.select("csproj", false);
                KitFixtures.render(view, 600, 120, 1);
                assertThat(command(view, "up").isEnabled()).isFalse();
                events.clear();
                command(view, "down").doClick(0);
                command(view, "remove").doClick(0);
                assertThat(events).containsExactly(new UiEvent.Reordered("markers", "csproj", 1), new UiEvent.Removed("markers", "csproj"));
                for (var row : list.mounted.values())
                    assertThat(row.controls.keySet()).as("no per-row commands").noneMatch(id -> id.endsWith("/up") || id.endsWith("/remove"));
            } finally { release(context); }
        });
    }

    /** Review Focus 2: after the host removes the selected row, no command acts on it. */
    @Test void aRowTheHostRemovedIsNoLongerTheTarget() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, withEntry(three()), events::add)) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                list.select("flake", false);
                events.clear();
                list.side.run("remove");
                assertThat(events).as("the commands' action path acts on the selection").containsExactly(new UiEvent.Removed("markers", "flake"));
                view.update(withEntry(List.of(marker("csproj", "*.csproj"), marker("xcode", "*.xcodeproj"))));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(list.side.target()).isNull();
                assertThat(List.of(command(view, "remove").isEnabled(), command(view, "up").isEnabled(), command(view, "down").isEnabled()))
                    .containsExactly(false, false, false);
                events.clear();
                // A disabled command's click fires nothing, so the action path itself must find no row to act on.
                for (var suffix : List.of("remove", "up", "down")) list.side.run(suffix);
                assertThat(events).isEmpty();
            } finally { release(context); }
        });
    }

    /** The Project Index entry row and an add action together, the entry's Add enabled or not (a host echoing each edit). */
    static ViewSpec withEntryAndAdd(boolean entryAdd) {
        var add = new Nodes.Button("key-add", READY, "Add Stored Key…", "add", Nodes.ButtonKind.LINK, "key-add", "");
        var entry = new Nodes.Stack("entry", READY, Nodes.LayoutRole.INLINE, List.of(
            new Nodes.Field("marker", READY, Nodes.FieldKind.TEXT, "", "Add…", "Marker", "", Optional.empty(), Nodes.FieldPresentation.EMBEDDED),
            new Nodes.Button("marker-add", entryAdd ? READY : new ControlState(true, false, false, ""), "Add", "", Nodes.ButtonKind.SECONDARY, "marker-add", "")));
        return new ViewSpec(new Nodes.Stack("group", READY, Nodes.LayoutRole.VERTICAL, List.of(markers(three(), Optional.of(add)), entry)));
    }

    /** An update leaves what the face placed where it is: a focused field or button that is detached loses focus and its IME. */
    @Test void anUpdateMovesNeitherTheAdoptedEntryRowNorTheAddAction() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, withEntryAndAdd(false), e -> {})) {
                KitFixtures.render(view, 600, 160, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var entry = find(view, "entry", JComponent.class);
                var add = find(view, "key-add", Buttons.KitButton.class);
                var field = editor(view, "marker");
                assertThat(entry.getParent()).isSameAs(list.scroll);
                assertThat(add.getParent()).isSameAs(list.side);
                var removed = new ArrayList<java.awt.Component>();
                var listener = new java.awt.event.ContainerAdapter() {
                    @Override public void componentRemoved(java.awt.event.ContainerEvent event) { removed.add(event.getChild()); }
                };
                for (var container : List.of(list.scroll, list.side, find(view, "group", JComponent.class), entry)) container.addContainerListener(listener);
                for (boolean enabled : List.of(false, true, true, false)) view.update(withEntryAndAdd(enabled));
                KitFixtures.render(view, 600, 160, 1);
                assertThat(removed).as("detaching a focused field or button transfers native focus and disrupts IME").isEmpty();
                assertThat(entry.getParent()).isSameAs(list.scroll);
                assertThat(list.side.getComponent(0)).isSameAs(add);
                assertThat(editor(view, "marker")).isSameAs(field);
            } finally { release(context); }
        });
    }

    /** An entry row stays adopted only while a VERTICAL parent re-confirms it; the list alone, or in an INLINE stack, gives it up. */
    @Test void aListThatLeavesItsVerticalParentGivesUpItsEntryRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, withEntry(three()), e -> {})) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                var entry = find(view, "entry", JComponent.class);
                assertThat(entry.getParent()).isSameAs(list.scroll);
                view.update(new ViewSpec(markers(three(), Optional.empty())));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(view.control("markers")).as("the list is kept").isSameAs(list);
                assertThat(entry.getParent()).as("the closed entry row left the pane").isNotSameAs(list.scroll);
                assertThat(list.entry).isNull();
                assertThat(list.scroll.getInsets().bottom).as("no room under the box").isEqualTo(line);
                view.update(withEntry(three()));
                KitFixtures.render(view, 600, 120, 1);
                var again = find(view, "entry", JComponent.class);
                assertThat(again.getParent()).isSameAs(list.scroll);
                view.update(new ViewSpec(new Nodes.Stack("group", READY, Nodes.LayoutRole.INLINE, List.of(markers(three(), Optional.empty())))));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(view.control("markers")).isSameAs(list);
                assertThat(again.getParent()).as("an INLINE parent adopts nothing").isNotSameAs(list.scroll);
                assertThat(list.entry).isNull();
                assertThat(list.scroll.getInsets().bottom).isEqualTo(line);
            } finally { release(context); }
        });
    }

    @Test void theSideColumnSpansTheListAndItsEntryRow() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, withEntry(three()), e -> {})) {
                KitFixtures.render(view, 600, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var entry = find(view, "entry", JComponent.class);
                assertThat(entry.getParent()).as("the entry row is adopted").isSameAs(list.scroll);
                var viewport = list.scroll.getViewport();
                int gap = ThemePainting.metric(context, DesignMetrics.Role.SIDE_COLUMN_GAP), width = ThemePainting.metric(context, DesignMetrics.Role.SIDE_COLUMN_WIDTH);
                assertThat(list.side.getX() + list.side.getWidth()).isEqualTo(list.scroll.getWidth());
                assertThat(list.side.getY()).isZero();
                assertThat(list.side.getWidth()).isGreaterThanOrEqualTo(width);
                assertThat(entry.getY()).isGreaterThanOrEqualTo(viewport.getY() + viewport.getHeight());
                assertThat(entry.getWidth()).isEqualTo(list.scroll.getWidth() - list.side.getWidth() - gap);
                assertThat(list.scroll.getHeight()).isGreaterThanOrEqualTo(list.side.getPreferredSize().height);
                assertThat(list.scroll.getMinimumSize().height).as("the minimum keeps Move Down").isGreaterThanOrEqualTo(list.side.getPreferredSize().height);
            } finally { release(context); }
        });
    }

    /**
     * Plan 9b Task 6 (the Shell, Font &amp; Colors and Project Index mocks): an empty list over an entry row keeps a box two
     * rows tall with a focus ring's room under them, the entry row 6 below it with its Add as wide as the side column at
     * its end and the field filling the rest; the side column, taller here, does not stretch the box.
     */
    @Test void anEntryRowSitsUnderItsBoxWithItsAddAsWideAsTheSideColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, withEntry(List.of()), e -> {})) {
                KitFixtures.render(view, 600, 200, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var entry = find(view, "entry", JComponent.class);
                var field = find(view, "marker", JComponent.class);
                var add = find(view, "marker-add", JComponent.class);
                int line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE), gap = ThemePainting.metric(context, DesignMetrics.Role.SIDE_COLUMN_GAP);
                int box = 2 * line + 2 * ThemePainting.metric(context, DesignMetrics.Role.ORDERED_ROW_HEIGHT) + ThemePainting.metric(context, DesignMetrics.Role.FOCUS_RING);
                assertThat(ClassicOrderedFace.box(list, list.scroll.getHeight())).isEqualTo(box);
                assertThat(list.scroll.getViewport().getHeight()).isEqualTo(box - 2 * line);
                assertThat(entry.getY()).isEqualTo(box + gap);
                assertThat(list.scroll.getHeight()).as("the side column sets the pane's height").isEqualTo(list.side.getPreferredSize().height);
                assertThat(add.getWidth()).isEqualTo(list.side.getWidth());
                assertThat(add.getX() + add.getWidth()).isEqualTo(entry.getWidth());
                assertThat(field.getX() + field.getWidth()).isEqualTo(add.getX() - gap);
            } finally { release(context); }
        });
    }

    /** Rulings 15: a LINK add action leads the side column as a standard button, without its glyph. */
    @Test void aLinkAddActionLeadsTheSideColumnAsAStandardButton() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var add = new Nodes.Button("key-add", READY, "Add Key…", "add", Nodes.ButtonKind.LINK, "key-add", "");
            var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(markers(List.of(), Optional.of(add))), events::add)) {
                KitFixtures.render(view, 420, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                var button = find(view, "key-add", Buttons.KitButton.class);
                assertThat(list.side.getComponent(0)).isSameAs(button);
                assertThat(button.sideColumn).isTrue();
                assertThat(button.getIcon()).isNull();
                assertThat(button.getForeground()).isEqualTo(ThemePainting.painted(context, context.faces().buttons.pairs().get(1)));
                assertThat(list.footer).isNull();
                button.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Action("key-add"));
            } finally { release(context); }
        });
    }

    /** Rulings 15 with plan 4b's Rulings 6: a disabled LINK add action is a flat disabled standard button, faded once (E1). */
    @Test void aDisabledLinkAddActionIsFlatAndFadedOnce() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var add = new Nodes.Button("key-add", new ControlState(true, false, false, ""), "Add Key…", "add", Nodes.ButtonKind.LINK, "key-add", "");
            try (var view = new KitView(context, new ViewSpec(markers(List.of(), Optional.of(add))), e -> {})) {
                KitFixtures.render(view, 420, 120, 1);
                var button = find(view, "key-add", Buttons.KitButton.class);
                assertThat(button.sideColumn).isTrue();
                assertThat(button.fadesDisabled()).as("the face draws its own disabled state").isFalse();
                var image = paint(button);
                assertThat(at(image, 3, button.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_TABLE_HEADER));
                assertThat(at(image, 0, button.getHeight() / 2)).isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER));
                assertThat(button.getForeground().getAlpha()).isEqualTo(Math.round(.42f * 255));
            } finally { release(context); }
        });
    }

    /** Review Focus 3: the box and the index as their guards make them, over every palette; the rows are striped. */
    @Test void theBoxAndIndexPaintTheirDeclaredPairsOverEveryPalette() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            var pairs = context.faces().ordered.pairs();
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            try (var view = new KitView(context, new ViewSpec(markers(three(), Optional.empty())), e -> {})) {
                KitFixtures.render(view, 420, 120, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(at(paint(list.scroll), 0, 5)).as(where + " box").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                var index = find(view, list.internalId("csproj", "index"), JLabel.class);
                assertThat(index.getForeground()).as(where + " index").isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                assertThat(index.getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.MONO_CAPTION));
                assertThat(find(view, list.internalId("xcode", "index"), JLabel.class).getForeground()).as(where + " index on the zebra")
                    .isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var odd = list.mounted.get("xcode");
                assertThat(at(paint(list.canvas), list.canvas.getWidth() - 2, odd.getY() + odd.getHeight() / 2)).as(where + " zebra")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_ZEBRA));
            } finally { release(context); }
        });
    }

    @Test void theDefaultKeepsItsRowCommandsAndFooter() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            var add = new Nodes.Button("key-add", READY, "Add Stored Key...", "add", Nodes.ButtonKind.LINK, "key-add", "");
            try (var view = new KitView(context, new ViewSpec(markers(three(), Optional.of(add))), e -> {})) {
                KitFixtures.render(view, 420, 200, 1);
                var list = (ItemLists.ListControl) view.control("markers");
                assertThat(list.side).isNull();
                assertThat(list.footer).isSameAs(find(view, "key-add", Buttons.KitButton.class));
                assertThat(list.mounted.get("csproj").controls).containsKey(list.internalId("csproj", "up"));
                assertThat(list.scroll).isInstanceOf(ItemLists.ListPane.class);
            } finally { release(context); }
        });
    }
}
