package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.release;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.TreeItem;
import dev.jasper.ui.model.UiEvent;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 12.1: the Settings navigation's ▼ sections, and their classic geometry (plan 9a Rulings 13). */
class CollapsibleNavigationTest {
    static ViewSpec navigation(Set<String> open) {
        var tree = new Nodes.Tree("pages", ControlState.READY, List.of(
            new TreeItem("ab", "Appearance & Behavior", Optional.empty(), false, List.of(new TreeItem("appearance", "Appearance", Optional.empty(), true, List.of()))),
            new TreeItem("keymap", "Keymap", Optional.empty(), true, List.of())), open, Set.of("appearance"), "");
        return new ViewSpec(new Nodes.Stack("settings-navigation", ControlState.READY, Nodes.LayoutRole.SETTINGS_NAVIGATION, List.of(tree)));
    }

    static KitView view(KitContext context, boolean collapsible, List<UiEvent> events) {
        var view = new KitView(context, navigation(Set.of("ab")), events::add);
        view.settingsChrome(false);
        view.collapsibleNavigation(collapsible);
        view.setSize(230, 300);
        InteractiveRowsTest.layout(view);
        return view;
    }

    @Test void aCollapsibleNavigationsTriangleTogglesAndItsKeysCloseAndOpen() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = view(context, true, events)) {
                var tree = (Trees.TreeControl) view.control("pages");
                assertThat(tree.collapsibleNavigation).isTrue();
                ((JButton) tree.mounted.get("ab").leading.getComponent(0)).doClick(0);
                assertThat(events).containsExactly(new UiEvent.Expanded("pages", "ab", false));
                events.clear();
                tree.select("ab", false);
                tree.canvas.getActionMap().get("LEFT").actionPerformed(new java.awt.event.ActionEvent(tree.canvas, 0, ""));
                assertThat(events).endsWith(new UiEvent.Expanded("pages", "ab", false));
                view.update(navigation(Set.of()));
                assertThat(tree.size()).as("closed: the section and Keymap").isEqualTo(2);
            } finally { release(context); }
        });
    }

    @Test void theDefaultNavigationHasNoTriangleAndStaysOpen() {
        Edt.run(() -> {
            var context = KitFixtures.context(); var events = new ArrayList<UiEvent>();
            try (var view = view(context, false, events)) {
                var tree = (Trees.TreeControl) view.control("pages");
                assertThat(tree.mounted.get("ab").leading.getComponentCount()).isZero();
                tree.select("ab", false);
                tree.canvas.getActionMap().get("LEFT").actionPerformed(new java.awt.event.ActionEvent(tree.canvas, 0, ""));
                assertThat(events).noneMatch(event -> event instanceof UiEvent.Expanded);
            } finally { release(context); }
        });
    }

    @Test void theClassicNavigationIsPlacedAsTheMocksPlaceIt() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = view(context, true, new ArrayList<>())) {
                var tree = (Trees.TreeControl) view.control("pages");
                for (int i = 0; i < 3; i++) InteractiveRowsTest.layout(view);
                var section = tree.mounted.get("ab"); var child = tree.mounted.get("appearance"); var top = tree.mounted.get("keymap");
                assertThat(section.leading.getX()).as("the triangle at the row side").isEqualTo(9);
                assertThat(section.leading.getWidth()).as("the glyph alone").isEqualTo(6);
                assertThat(section.title.getX()).as("the section title").isEqualTo(21);
                assertThat(child.title.getX()).as("a page under a section").isEqualTo(24);
                assertThat(top.title.getX()).as("a top-level page").isEqualTo(9);
                assertThat(child.getY() - section.getY()).as("22 rows, no group gap").isEqualTo(22);
                assertThat(top.getY() - child.getY()).isEqualTo(22);
                assertThat(section.getHeight()).isEqualTo(22);
                var image = new BufferedImage(230, 300, BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics(); try { view.printAll(g); } finally { g.dispose(); }
                int y = child.getY() + child.getHeight() / 2 + tree.canvas.getY();
                int selection = dev.jasper.ui.theme.SemanticColor.SELECTION_BG.color(context.theme()).getRGB() & 0xFFFFFF;
                assertThat(image.getRGB(0, y) & 0xFFFFFF).as("the selection reaches the left edge").isEqualTo(selection);
                assertThat(image.getRGB(229, y) & 0xFFFFFF).as("and the right edge").isEqualTo(selection);
            } finally { release(context); }
        });
    }
}
