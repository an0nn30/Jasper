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

/** Spec 5.2's tabs point: the classic face draws folder tabs, the active one joined to the content. */
@SuppressWarnings("try")
class ClassicTabsTest {
    static Nodes.Choice tabs() {
        return new Nodes.Choice("tabs", ControlState.READY, Nodes.ChoiceKind.TABS, List.of(new Option("overview", "Overview", Optional.empty()),
            new Option("details", "Details", Optional.empty())), "overview", "Plugin information", "");
    }

    static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics(); try { component.paint(g); } finally { g.dispose(); }
        return image;
    }

    @Test void folderTabsAreContentSizedAndTheActiveOneJoinsTheContent() {
        var palettes = new ArrayList<>(KitFixtures.themes()); palettes.addAll(KitFixtures.degenerate());
        for (var theme : palettes) Edt.run(() -> {
            var context = KitFixtures.context(theme, KitFixtures.enterprise()); var events = new ArrayList<UiEvent>();
            try (var view = new KitView(context, new ViewSpec(tabs()), events::add)) {
                if (theme == KitFixtures.light()) KitFixtures.capture(context, view.spec(), 420, 40, "folder-tabs");
                KitFixtures.render(view, 420, 40, 1);
                var overview = find(view, "tabs.option.overview", Choices.SegmentButton.class);
                var details = find(view, "tabs.option.details", Choices.SegmentButton.class);
                var pairs = context.faces().tabs.pairs();
                assertThat(overview.getHeight()).isEqualTo(ThemePainting.metric(context, DesignMetrics.Role.FOLDER_TAB_HEIGHT));
                assertThat(details.getX()).as("joined, no gap").isEqualTo(overview.getWidth());
                var active = paint(overview); var inactive = paint(details);
                int h = overview.getHeight(), w = overview.getWidth();
                String where = theme.name() + (theme.dark() ? " Dark" : " Light");
                assertThat(new Color(active.getRGB(w / 2, h - 1), true)).as(where + " no bottom edge under the active tab")
                    .isEqualTo(ThemePainting.color(context, SemanticColor.SURFACE_CARD));
                assertThat(new Color(inactive.getRGB(details.getWidth() / 2, h - 1), true)).as(where + " the baseline under the others")
                    .isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                assertThat(new Color(inactive.getRGB(3, h / 2), true)).as(where).isEqualTo(ThemePainting.role(context, DerivedColor.SURFACE_STRIPE));
                assertThat(new Color(active.getRGB(0, h / 2), true)).as(where + " side edge").isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                var track = (JComponent) overview.getParent(); var behind = paint(track);
                assertThat(new Color(behind.getRGB(track.getWidth() - 1, overview.getY() + h - 1), true)).as(where + " the baseline runs on at the tabs' foot")
                    .isEqualTo(ThemePainting.painted(context, pairs.get(2)));
                details.doClick(0);
                assertThat(events).containsExactly(new UiEvent.Selected("tabs", Set.of("details")));
            } finally { release(context); }
        });
    }

    @Test void theDefaultKitKeepsTheSegmentTrack() {
        Edt.run(() -> {
            var context = KitFixtures.context();
            assertThat(context.faces().tabs).isInstanceOf(DefaultChoiceFace.class);
            release(context);
        });
    }
}
