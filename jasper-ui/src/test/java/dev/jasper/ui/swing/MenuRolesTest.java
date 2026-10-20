package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.SemanticColor;
import org.junit.jupiter.api.Test;

/** spec 7: the palette is a menu surface; window bodies and sidebars take their roles. */
class MenuRolesTest {
    static int center(javax.swing.JComponent component) {
        var image = new java.awt.image.BufferedImage(component.getWidth(), component.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.paint(g); } finally { g.dispose(); }
        return image.getRGB(component.getWidth() / 2, component.getHeight() / 2);
    }

    @Test void popupsAreMenuSurfacesAndContentTakesTheRoleItIsGiven() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var popup = new SurfacePanel(context, SurfacePanel.Kind.POPUP); popup.setSize(200, 100);
                assertThat(center(popup)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_MENU).getRGB());
                var places = new SurfacePanel(context, SurfacePanel.Kind.CONTENT); places.setSize(200, 100);
                places.fill(SemanticColor.SURFACE_SIDEBAR);
                assertThat(center(places)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_SIDEBAR).getRGB());
            } finally { KitViewTest.release(context); }
        });
    }

    @Test void theSettingsWindowBodyIsTheDialogSurface() {
        Edt.run(() -> {
            var context = KitFixtures.context(KitFixtures.marked());
            try {
                var parts = new javax.swing.JComponent[6];
                for (int i = 0; i < parts.length; i++) parts[i] = new javax.swing.JPanel();
                try (var shell = new SettingsShell(context, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])) {
                    shell.setSize(900, 600); shell.doLayout();
                    var image = new java.awt.image.BufferedImage(900, 600, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics();
                    try { shell.paintComponent(g); } finally { g.dispose(); }
                    assertThat(image.getRGB(700, 300)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_DIALOG).getRGB());
                }
            } finally { KitViewTest.release(context); }
        });
    }
}
