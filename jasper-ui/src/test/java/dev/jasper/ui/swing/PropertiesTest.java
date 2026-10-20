package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SurfaceContrast;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;

/** Spec 8.2's Properties: a heading over a card of label and value rows. */
@SuppressWarnings("try")
class PropertiesTest {
    static final ControlState READY = ControlState.READY;
    static Nodes.Properties properties(String title, List<PropertyRow> rows) { return new Nodes.Properties("properties", READY, title, rows); }
    static List<PropertyRow> host() {
        return List.of(new PropertyRow("Host", "192.168.1.124", true), new PropertyRow("User", "dustin", true),
            new PropertyRow("Credentials", "Vault locked", false, Optional.of(Severity.WARNING)));
    }
    static JLabel label(KitView view, String id) { return find(view, id, JLabel.class); }

    @Test void propertiesShowAHeadingOverALabelColumnBesideTheirValues() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(properties("Properties — minecraft-local", host())), e -> {})) {
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.title").isVisible()).isTrue();
                assertThat(label(view, "properties.title").getText()).isEqualTo("Properties — minecraft-local");
                int widest = 0;
                for (int i = 0; i < 3; i++) widest = Math.max(widest, label(view, "properties.label." + i).getPreferredSize().width);
                for (int i = 0; i < 3; i++) {
                    var name = label(view, "properties.label." + i); var value = label(view, "properties.value." + i);
                    assertThat(value.getX()).as("one value column").isEqualTo(label(view, "properties.value.0").getX());
                    assertThat(value.getX()).isGreaterThanOrEqualTo(name.getX() + widest);
                    assertThat(value.getY()).isEqualTo(name.getY());
                    assertThat(name.getLabelFor()).isSameAs(value);
                }
                assertThat(label(view, "properties.label.0").getText()).isEqualTo("Host");
                assertThat(label(view, "properties.value.0").getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.MONO));
                assertThat(label(view, "properties.value.2").getFont()).isEqualTo(ThemePainting.font(context, Typography.Role.BODY));
                assertThat(label(view, "properties.value.0").getForeground()).isEqualTo(ThemePainting.color(context, SemanticColor.TEXT_PRIMARY));
                assertThat(label(view, "properties.value.2").getForeground())
                    .isEqualTo(SurfaceContrast.painted(context.theme(), PropertyLists.valuePair(Severity.WARNING)));
            } finally { release(context); }
        });
    }

    @Test void aBlankTitleShowsNoHeadingAndTheRowsFollowTheDescriptor() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            try (var view = new KitView(context, new ViewSpec(properties("", host())), e -> {})) {
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.title").isVisible()).isFalse();
                view.update(new ViewSpec(properties("", List.of(new PropertyRow("Port", "22", true)))));
                KitFixtures.render(view, 320, 200, 1);
                assertThat(label(view, "properties.value.0").getText()).isEqualTo("22");
                assertThat(find(view, "properties.label.1", JLabel.class)).isNull();
            } finally { release(context); }
        });
    }

    @Test void aLongValueIsShortenedAndKeepsItsWholeTextAsItsTooltip() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            String path = "/Users/dustin/projects/jasper/a/rather/long/synthetic/folder/name";
            var rows = List.of(new PropertyRow("Folder", path, true), new PropertyRow("Note", "plain", false));
            try (var view = new KitView(context, new ViewSpec(properties("", rows)), e -> {})) {
                KitFixtures.render(view, 200, 100, 1);
                var value = label(view, "properties.value.0");
                assertThat(value.getWidth()).isLessThan(value.getPreferredSize().width);
                assertThat(value.getToolTipText()).isEqualTo(path);
                assertThat(value.getClientProperty("Jasper.leadingEllipsis")).isEqualTo(true);
                assertThat(label(view, "properties.value.1").getClientProperty("Jasper.leadingEllipsis")).isEqualTo(false);
            } finally { release(context); }
        });
    }
}
