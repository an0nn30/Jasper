package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 5.2's group and form-label points, with 6.4's composition rules for classic forms. */
@SuppressWarnings("try")
class ClassicGroupsTest {
    static final ControlState READY = ControlState.READY;

    static Nodes.Field field(String id, String hint, Nodes.FieldKind kind) {
        return new Nodes.Field(id, READY, kind, "", hint, id, "", Optional.empty());
    }

    /** New File Server's Server box: Name, Reachable from with its Port, Passive ports. */
    static ViewSpec server() {
        var reachable = new Nodes.Choice("reachable", READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("local", "This computer only", Optional.empty())),
            "local", "Reachable from", "reachable");
        return new ViewSpec(new Nodes.Section("server", READY, "Server", new Nodes.Card("server-card", READY, Nodes.CardKind.FORM, List.of(
            new Nodes.FormRow("name-row", READY, "Name", "", field("name", "e.g. Downloads", Nodes.FieldKind.TEXT), Optional.empty()),
            new Nodes.FormRow("reachable-row", READY, "Reachable from", "", reachable, Optional.of(field("port", "", Nodes.FieldKind.PORT))),
            new Nodes.FormRow("passive-row", READY, "Passive ports", "", field("passive", "Empty uses the range in Settings", Nodes.FieldKind.TEXT), Optional.empty())))));
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void aSectionAndItsCardAreOneTitledBoxWhoseTitleBreaksTheEdge() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                if (theme == KitFixtures.light()) KitFixtures.capture(context, view.spec(), 488, 150, "titled-box");
                KitFixtures.render(view, 488, 150, 1);
                var section = find(view, "server", Cards.SectionSurface.class);
                var title = find(view, "server.title", JLabel.class);
                var pairs = context.faces().group.pairs();
                var image = paint(section);
                int line = title.getHeight() / 2;
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(new Color(image.getRGB(0, section.getHeight() / 2), true)).as(where + " left edge").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(new Color(image.getRGB(section.getWidth() - 10, line), true)).as(where + " top edge").isEqualTo(ThemePainting.painted(context, pairs.get(0)));
                assertThat(image.getRGB(title.getX() + 1, line) >>> 24 == 0 || !new Color(image.getRGB(title.getX() + 1, line), true).equals(ThemePainting.painted(context, pairs.get(0))))
                    .as(where + " the title breaks the edge").isTrue();
                assertThat(title.getX()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_INSET) + ThemePainting.metric(context, DesignMetrics.Role.GROUP_TITLE_PADDING));
                assertThat(title.getForeground()).isEqualTo(ThemePainting.painted(context, pairs.get(1)));
                var card = find(view, "server-card", Cards.CardSurface.class);
                assertThat(paint(card).getRGB(card.getWidth() / 2, card.getHeight() - 2) >>> 24).as(where + " the card is transparent").isZero();
            } finally { release(context); }
        });
    }

    /** Rulings 16 and Review Focus 4: the label column is the widest label, at least the floor; labels end with a colon. */
    @Test void theLabelColumnIsTheWidestLabelOrTheFloor() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 150, 1);
                var name = find(view, "name-row.label", JLabel.class);
                assertThat(name.getText()).isEqualTo("Name:");
                var rows = List.of("name-row", "reachable-row", "passive-row");
                int widest = rows.stream().mapToInt(id -> find(view, id + ".label", JLabel.class).getPreferredSize().width).max().orElseThrow();
                int column = Math.max(widest, ThemePainting.metric(context, DesignMetrics.Role.FORM_LABEL_WIDTH));
                for (var id : rows) assertThat(((FormRows.RowControl) view.control(id)).column).as(id).isEqualTo(column);
                var nameField = find(view, "name", JComponent.class);
                assertThat(SwingUtilities.convertPoint(nameField.getParent(), nameField.getLocation(), find(view, "name-row", JComponent.class)).x)
                    .isEqualTo(column + ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP));
            } finally { release(context); }
        });
    }

    /** Review Focus 4: a trailing PORT field gets its own "Port:" label beside it. */
    @Test void aTrailingPortFieldHasItsOwnLabel() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 150, 1);
                var port = find(view, "reachable-row.port", JLabel.class);
                assertThat(port.getText()).isEqualTo("Port:");
                assertThat(port.isVisible()).isTrue();
                var field = find(view, "port", JComponent.class);
                assertThat(field.getX()).isGreaterThan(port.getX() + port.getWidth());
            } finally { release(context); }
        });
    }

    /** Rulings 17 and 18: a non-Toggle setting row in a form card takes the label column; a bare EXCLUSIVE choice is inset to it. */
    @Test void aSettingRowAndABareChoiceTakeTheFieldColumn() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light(), KitFixtures.enterprise());
            var group = new Nodes.Choice("group", READY, Nodes.ChoiceKind.DROPDOWN, List.of(new Option("other", "Other", Optional.empty())), "other", "Group", "group");
            var mode = new Nodes.Choice("mode", READY, Nodes.ChoiceKind.EXCLUSIVE, List.of(new Option("vault", "Vault Credential", Optional.empty()),
                new Option("agent", "SSH Agent", Optional.empty())), "vault", "Authentication", "mode");
            var spec = new ViewSpec(new Nodes.Card("card", READY, Nodes.CardKind.FORM, List.of(mode,
                new Nodes.FormRow("user-row", READY, "Username", "", field("user", "root", Nodes.FieldKind.TEXT), Optional.empty()),
                new Nodes.SettingRow("group-row", READY, "Group", "", group, false))));
            try (var view = new KitView(context, spec, e -> {})) {
                KitFixtures.render(view, 488, 140, 1);
                int column = ((FormRows.RowControl) view.control("user-row")).column, gap = ThemePainting.metric(context, DesignMetrics.Role.COLUMN_GAP);
                var row = (SettingRows.RowControl) view.control("group-row");
                assertThat(row.formLabel).isTrue();
                assertThat(find(view, "group-row.title", JComponent.class).getAccessibleContext().getAccessibleName()).isEqualTo("Group:");
                var combo = find(view, "group", JComponent.class);
                assertThat(SwingUtilities.convertPoint(combo.getParent(), combo.getLocation(), row.component).x)
                    .isEqualTo(row.component.getInsets().left + column + gap);
                assertThat(((javax.swing.border.EmptyBorder) find(view, "mode", JComponent.class).getBorder()).getBorderInsets().left).isEqualTo(column + gap);
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsPlainLabelsAndCards() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.light());
            try (var view = new KitView(context, server(), e -> {})) {
                KitFixtures.render(view, 488, 200, 1);
                assertThat(find(view, "name-row.label", JLabel.class).getText()).isEqualTo("Name");
                assertThat(((FormRows.RowControl) view.control("name-row")).column).isEqualTo(-1);
                assertThat(find(view, "reachable-row.port", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }
}
