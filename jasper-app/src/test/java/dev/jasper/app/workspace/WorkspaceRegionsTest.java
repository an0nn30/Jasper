package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WorkspaceRegionsTest {
    private static Rectangle in(WorkspaceRegions regions, java.awt.Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), regions);
    }

    private static void layout(WorkspaceRegions regions) {
        regions.setSize(1000, 600);
        // Twice: a trailing split learns its width in the first pass and places its divider in the second.
        LayoutTestSupport.layoutTree(regions);
        LayoutTestSupport.layoutTree(regions);
    }

    @Test void theCenterKeepsItsRoundedFrameWhenPanelsOpenAndClose() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        regions.setRailVisible(true);
        deck.setMinimumSize(new java.awt.Dimension(420, 320));
        assertThat(regions.getMinimumSize()).as("terminal minimum includes the surface gutter")
            .isEqualTo(new java.awt.Dimension(426, 340));
        deck.setMinimumSize(new java.awt.Dimension(500, 360));
        assertThat(regions.getMinimumSize()).as("terminal font/split minimum changes propagate")
            .isEqualTo(new java.awt.Dimension(506, 380));
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 6, 994, 580));
        assertThat(regions.content(PanelRegion.LEFT)).isNull();
        assertThat(regions.size(PanelRegion.LEFT)).isEqualTo(WorkspaceRegions.DEFAULT_SIDE);

        var left = new JLabel("left");
        regions.show(PanelRegion.LEFT, left, 250);
        layout(regions);
        assertThat(regions.content(PanelRegion.LEFT)).isSameAs(left);
        assertThat(in(regions, left.getParent()).width).isEqualTo(250);
        assertThat(in(regions, left.getParent()).x).isZero();
        assertThat(in(regions, left)).as("content inset within its rounded host").isEqualTo(new Rectangle(0, 6, 250, 588));
        assertThat(in(regions, deck).x).isGreaterThan(250);
        assertThat(in(regions, deck).x + in(regions, deck).width).isEqualTo(994);

        regions.hide(PanelRegion.LEFT);
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 6, 994, 580));
        assertThat(regions.size(PanelRegion.LEFT)).as("the last size is remembered").isEqualTo(250);
    }

    @Test void bottomSitsUnderTheCenterOnlyBetweenTheSideRegions() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        regions.setRailVisible(true);
        var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
        regions.show(PanelRegion.LEFT, left, 200);
        regions.show(PanelRegion.RIGHT, right, 300);
        regions.show(PanelRegion.BOTTOM, bottom, 150);
        layout(regions);
        assertThat(in(regions, left.getParent()).width).isEqualTo(200);
        assertThat(in(regions, left.getParent()).height).isEqualTo(600);
        assertThat(in(regions, right.getParent()).width).isEqualTo(300);
        assertThat(in(regions, right.getParent()).x + 300).isEqualTo(1000);
        assertThat(in(regions, right.getParent()).height).isEqualTo(600);
        assertThat(in(regions, bottom.getParent()).height).isEqualTo(150);
        assertThat(in(regions, bottom.getParent()).y + 150).isEqualTo(600);
        assertThat(in(regions, bottom.getParent()).x).isEqualTo(in(regions, deck.getParent()).x);
        assertThat(in(regions, bottom.getParent()).width).isEqualTo(in(regions, deck.getParent()).width);
        assertThat(in(regions, deck).y).isEqualTo(6);

        var replacement = new JLabel("other");
        regions.show(PanelRegion.RIGHT, replacement, 320);
        layout(regions);
        assertThat(regions.content(PanelRegion.RIGHT)).isSameAs(replacement);
        assertThat(right.getParent()).isNull();
        assertThat(in(regions, replacement.getParent()).width).isEqualTo(320);
        assertThat(regions.size(PanelRegion.BOTTOM)).isEqualTo(150);
    }

    @Test void workspaceDividerIsAQuietDraggableGapAfterThemeChanges() throws Exception {
        var original = javax.swing.UIManager.getLookAndFeel();
        try {
            var regions = new WorkspaceRegions(new JPanel());
            var panel = new JPanel(); regions.show(PanelRegion.LEFT, panel, 250);
            for (var theme : java.util.List.of(dev.jasper.app.appearance.Theme.DARK, dev.jasper.app.appearance.Theme.INTELLIJ_LIGHT)) {
                dev.jasper.app.appearance.ThemeTestSupport.install(theme);
                SwingUtilities.updateComponentTreeUI(regions); regions.refreshTheme(); layout(regions);
                var split = (javax.swing.JSplitPane) regions.getComponent(0);
                var divider = ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
                var image = new java.awt.image.BufferedImage(divider.getWidth(), divider.getHeight(), 2);
                var g = image.createGraphics(); g.setColor(split.getBackground()); g.fillRect(0, 0, image.getWidth(), image.getHeight()); divider.paint(g); g.dispose();
                int chrome = javax.swing.UIManager.getColor("Jasper.titleBackground").getRGB();
                for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
                    assertThat(image.getRGB(x, y)).as("gap has no border or grip").isEqualTo(chrome);
                split.setDividerLocation(320); layout(regions);
                assertThat(regions.size(PanelRegion.LEFT)).isEqualTo(320);
                assertThat(divider.getCursor().getType()).isEqualTo(java.awt.Cursor.E_RESIZE_CURSOR);
            }
        } finally { javax.swing.UIManager.setLookAndFeel(original); }
    }

    @Test void sizesAreClampedToAUsableMinimum() {
        var regions = new WorkspaceRegions(new JPanel());
        regions.show(PanelRegion.LEFT, new JLabel("left"), 5);
        layout(regions);
        assertThat(regions.size(PanelRegion.LEFT)).isGreaterThanOrEqualTo(WorkspaceRegions.MINIMUM);
    }
}
