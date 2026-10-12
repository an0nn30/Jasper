package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** spec 7: captions and counts are tertiary, Settings row titles and the current crumb strong secondary. */
class TextRolesTest {
    @Test void captionsAndCountsAreTertiaryAndLabelsSecondary() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, stack(
                    new Text("caption", ControlState.READY, "3 items", TextRole.CAPTION),
                    new Text("count", ControlState.READY, "3", TextRole.COUNT),
                    new Text("label", ControlState.READY, "Name", TextRole.LABEL)), e -> {})) {
                assertThat(find(view, "caption", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_TERTIARY));
                assertThat(find(view, "count", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_TERTIARY));
                assertThat(find(view, "label", JLabel.class).getForeground()).isEqualTo(KitFixtures.marker(SemanticColor.TEXT_SECONDARY));
            } finally { release(context); }
        });
    }

    @Test void theCurrentCrumbIsStrongSecondary() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            var crumbs = new Breadcrumbs("crumbs", ControlState.READY, List.of(new Crumb("group", "Appearance & Behavior", List.of()),
                new Crumb("page", "Appearance", List.of())), "Location", false, "", "edit", "go", "done");
            try (var view = new KitView(context, stack(crumbs), e -> {})) {
                var current = find(view, "@crumbs/crumbs/page", Buttons.KitButton.class);
                var parent = find(view, "@crumbs/crumbs/group", Buttons.KitButton.class);
                assertThat(current.textRole(false)).isEqualTo(SemanticColor.TEXT_STRONG_SECONDARY);
                assertThat(parent.textRole(false)).isEqualTo(SemanticColor.TEXT_SECONDARY);
            } finally { release(context); }
        });
    }
}
