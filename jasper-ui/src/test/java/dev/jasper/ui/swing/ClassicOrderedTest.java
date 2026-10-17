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
                view.update(withEntry(List.of(marker("csproj", "*.csproj"), marker("xcode", "*.xcodeproj"))));
                KitFixtures.render(view, 600, 120, 1);
                assertThat(list.side.target()).isNull();
                events.clear();
                command(view, "remove").doClick(0);
                assertThat(events).isEmpty();
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
