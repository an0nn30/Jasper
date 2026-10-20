package dev.jasper.ui.swing;

import static dev.jasper.ui.swing.KitViewTest.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.model.*;
import dev.jasper.ui.model.Nodes.*;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import java.util.*;
import org.junit.jupiter.api.Test;

/** spec 7: empty tiles, segmented controls, secondary buttons and dropdowns paint theme.md's roles. */
class ControlRolesTest {
    static int pixel(javax.swing.JComponent component, int x, int y) {
        var image = new java.awt.image.BufferedImage(component.getWidth(), component.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(x, y);
    }

    @Test void secondaryButtonsHaveTheirOwnEdgeAndStrongSecondaryLabels() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, stack(new Button("cancel", ControlState.READY, "Cancel", "", ButtonKind.SECONDARY, "cancel", "")), e -> {})) {
                var button = find(view, "cancel", Buttons.KitButton.class);
                assertThat(button.textRole(false)).isEqualTo(SemanticColor.TEXT_STRONG_SECONDARY);
                assertThat(button.border()).isEqualTo(KitFixtures.marker(SemanticColor.BUTTON_SECONDARY_BORDER));
            } finally { release(context); }
        });
    }

    @Test void theSegmentedTrackIsItsOwnSurfaceWithAnInputEdge() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var track = new Choices.SegmentTrack(context, context.faces().exclusive); track.setSize(120, 30);
                assertThat(pixel(track, 60, 15)).isEqualTo(KitFixtures.marker(SemanticColor.SEGMENT_TRACK).getRGB());
                assertThat(pixel(track, 60, 0)).isEqualTo(KitFixtures.marker(SemanticColor.BORDER_INPUT).getRGB());
            } finally { release(context); }
        });
    }

    /** A panel body's empty state (PanelSurfacesTest's fixture) takes the empty tile; a window's keeps the control surface. */
    @Test void panelEmptyTilesUseTheEmptyTileSurface() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try (var view = new KitView(context, new ViewSpec(PanelSurfacesTest.plain(PanelCompositionTest.empty(), Optional.empty())), e -> {});
                 var window = new KitView(context, stack(PanelCompositionTest.empty()), e -> {})) {
                KitFixtures.render(view, 260, 395, 1);
                KitFixtures.render(window, 400, 300, 1);
                var tile = find(view, "empty.tile", javax.swing.JComponent.class);
                assertThat(pixel(tile, tile.getWidth() / 2, 2)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_EMPTY_TILE).getRGB());
                var hero = find(window, "empty.tile", javax.swing.JComponent.class);
                assertThat(pixel(hero, 3, hero.getHeight() / 2)).as("the window's hero tile, inside its outline").isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_CONTROL).getRGB());
            } finally { release(context); }
        });
    }
}
