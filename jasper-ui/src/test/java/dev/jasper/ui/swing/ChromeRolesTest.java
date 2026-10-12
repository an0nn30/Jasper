package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** spec 7: the title bar's command field, rails and tool windows paint theme.md's roles. */
class ChromeRolesTest {
    static final Icon NONE = new Icon() {
        @Override public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) { }
        @Override public int getIconWidth() { return 0; }
        @Override public int getIconHeight() { return 0; }
    };

    static BufferedImage search(boolean hover) {
        var field = new JPanel(); field.setSize(200, 24);
        var image = new BufferedImage(200, 24, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.search(field, g, KitFixtures.marked(), hover, false, "", "", NONE, null); } finally { g.dispose(); }
        return image;
    }

    @Test void theCommandFieldIsARaisedFieldWhoseEdgeAloneAnswersHover() {
        for (boolean hover : new boolean[] {false, true}) {
            var image = search(hover);
            assertThat(image.getRGB(100, 12)).as("fill, hover %s", hover).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_FIELD_RAISED).getRGB());
            assertThat(image.getRGB(100, 0)).as("edge, hover %s", hover).isEqualTo(KitFixtures.marker(
                hover ? SemanticColor.BORDER_FIELD_RAISED_HOVER : SemanticColor.BORDER_FIELD_RAISED).getRGB());
        }
    }

    @Test void selectedToolbarActionsAndRailRowsUseTheSelectedControlSurface() {
        var button = new javax.swing.JToggleButton(); button.setIcon(NONE); button.setSize(28, 28); button.setSelected(true);
        var image = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.action(button, g, KitFixtures.marked(), false, null); } finally { g.dispose(); }
        assertThat(image.getRGB(14, 14)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_CONTROL_SELECTED).getRGB());
    }
}
