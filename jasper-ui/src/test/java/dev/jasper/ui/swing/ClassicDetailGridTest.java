package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Plan 10b Task 5: a classic manager detail's Fields group is a grid of label and value rows (spec 14.8). */
class ClassicDetailGridTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Node row(String id, String label, String value) {
        return new Nodes.FormRow(id + ".row", READY, label, "", new Nodes.Text(id, READY, value, Nodes.TextRole.MONO), Optional.empty(), false);
    }

    static Nodes.Panel pane(String id, Nodes.Node body) {
        return new Nodes.Panel(id, READY, new Nodes.Header(id + ".header", READY, id, OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), body, Optional.empty());
    }

    /** The Vault's Details shape: a heading over a Fields group of two rows and a button, in a manager's detail column. */
    static ViewSpec manager() {
        var detail = new Nodes.Stack("detail", READY, Nodes.LayoutRole.DETAIL, List.of(
            new Nodes.Text("heading", READY, "amp", Nodes.TextRole.DETAIL_TITLE),
            new Nodes.Section("fields.section", READY, "Fields", new Nodes.Card("fields", READY, Nodes.CardKind.FORM,
                List.of(row("username", "Username", "ubuntu"), row("host", "Host", "example.invalid")))),
            new Nodes.Button("edit", READY, "Edit", "", Nodes.ButtonKind.SECONDARY, "edit", "")));
        return new ViewSpec(new Nodes.Split("columns", READY, Nodes.SplitKind.THREE_COLUMN, List.of(
            pane("rail", new Nodes.Text("rail.body", READY, "Rail", Nodes.TextRole.BODY)),
            pane("list", new Nodes.Text("list.body", READY, "List", Nodes.TextRole.BODY)), pane("details", detail))));
    }

    static int y(JComponent component, KitView view) { return SwingUtilities.convertPoint(component.getParent(), component.getLocation(), view).y; }

    @Test void theClassicFieldsGroupIsABoxOfEqualRows() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.enterpriseLight(), KitFixtures.enterprise());
            try (var view = new KitView(context, manager(), event -> { })) {
                var image = KitFixtures.render(view, 820, 400, 1);
                var card = (Cards.CardControl) view.control("fields");
                assertThat(card.grid).as("a manager detail's card of label and value rows").isTrue();
                int slot = ThemePainting.metric(context, DesignMetrics.Role.SETTING_HEIGHT), line = ThemePainting.metric(context, DesignMetrics.Role.HAIRLINE);
                var first = KitViewTest.find(view, "username.row", JComponent.class);
                var second = KitViewTest.find(view, "host.row", JComponent.class);
                int top = ThemePainting.metric(context, DesignMetrics.Role.DETAIL_GRID_TOP), inset = ThemePainting.metric(context, DesignMetrics.Role.DETAIL_GRID_INSET);
                assertThat(first.getY()).as("the first row is centred in its slot under the box's top line")
                    .isEqualTo(top + line + (slot - first.getHeight()) / 2);
                assertThat(second.getY() - first.getY()).as("rows a slot and a rule apart").isEqualTo(slot + line);
                assertThat(first.getX()).as("rows inside the box's side").isEqualTo(2 * inset + line);
                var surface = card.component;
                var origin = SwingUtilities.convertPoint(surface.getParent(), surface.getLocation(), view);
                assertThat(image.getRGB(origin.x + inset, origin.y + top + slot) & 0xFFFFFF).as("the box's edge")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_DIVIDER).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(origin.x + 2 * inset, origin.y + top + line + slot) & 0xFFFFFF).as("the rule between the rows")
                    .isEqualTo(ThemePainting.role(context, DerivedColor.BORDER_ROW).getRGB() & 0xFFFFFF);
                assertThat(image.getRGB(origin.x + inset + 2, origin.y + top + 2) & 0xFFFFFF).as("the box's ground")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD).getRGB() & 0xFFFFFF);
                assertThat(surface.getHeight()).as("the box stands its inset above the group's bottom line")
                    .isEqualTo(top + line + 2 * slot + line + inset + line);
                var heading = KitViewTest.find(view, "heading", JComponent.class);
                var section = (Cards.SectionControl) view.control("fields.section");
                int gap = ThemePainting.metric(context, DesignMetrics.Role.DETAIL_SECTION_GAP);
                assertThat(y(section.component, view) - (y(heading, view) + heading.getHeight()))
                    .as("the box's top line, not its title, stands the section gap below the heading")
                    .isEqualTo(gap - section.title.getPreferredSize().height / 2);
            }
        });
    }

    @Test void theDefaultLookKeepsItsCard() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), Kit.DEFAULT);
            try (var view = new KitView(context, manager(), event -> { })) {
                KitFixtures.render(view, 820, 400, 1);
                assertThat(((Cards.CardControl) view.control("fields")).grid).isFalse();
                var heading = KitViewTest.find(view, "heading", JComponent.class);
                var section = (Cards.SectionControl) view.control("fields.section");
                assertThat(y(section.component, view) - (y(heading, view) + heading.getHeight()))
                    .isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.DETAIL_SECTION_GAP));
            }
        });
    }
}
