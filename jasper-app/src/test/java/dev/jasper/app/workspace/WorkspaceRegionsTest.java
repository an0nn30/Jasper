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

    @Test void theCenterFillsEverythingUntilARegionIsShownAndAgainAfterItIsHidden() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 0, 1000, 600));
        assertThat(regions.content(PanelRegion.LEFT)).isNull();
        assertThat(regions.size(PanelRegion.LEFT)).isEqualTo(WorkspaceRegions.DEFAULT_SIDE);

        var left = new JLabel("left");
        regions.show(PanelRegion.LEFT, left, 250);
        layout(regions);
        assertThat(regions.content(PanelRegion.LEFT)).isSameAs(left);
        assertThat(in(regions, left).width).isEqualTo(250);
        assertThat(in(regions, left).x).isZero();
        assertThat(in(regions, deck).x).isGreaterThan(250);
        assertThat(in(regions, deck).x + in(regions, deck).width).isEqualTo(1000);

        regions.hide(PanelRegion.LEFT);
        layout(regions);
        assertThat(in(regions, deck)).isEqualTo(new Rectangle(0, 0, 1000, 600));
        assertThat(regions.size(PanelRegion.LEFT)).as("the last size is remembered").isEqualTo(250);
    }

    @Test void bottomSitsUnderTheCenterOnlyBetweenTheSideRegions() {
        var deck = new JPanel();
        var regions = new WorkspaceRegions(deck);
        var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
        regions.show(PanelRegion.LEFT, left, 200);
        regions.show(PanelRegion.RIGHT, right, 300);
        regions.show(PanelRegion.BOTTOM, bottom, 150);
        layout(regions);
        assertThat(in(regions, left).width).isEqualTo(200);
        assertThat(in(regions, left).height).isEqualTo(600);
        assertThat(in(regions, right).width).isEqualTo(300);
        assertThat(in(regions, right).x + 300).isEqualTo(1000);
        assertThat(in(regions, right).height).isEqualTo(600);
        assertThat(in(regions, bottom).height).isEqualTo(150);
        assertThat(in(regions, bottom).y + 150).isEqualTo(600);
        assertThat(in(regions, bottom).x).isEqualTo(in(regions, deck).x);
        assertThat(in(regions, bottom).width).isEqualTo(in(regions, deck).width);
        assertThat(in(regions, deck).y).isZero();

        var replacement = new JLabel("other");
        regions.show(PanelRegion.RIGHT, replacement, 320);
        layout(regions);
        assertThat(regions.content(PanelRegion.RIGHT)).isSameAs(replacement);
        assertThat(right.getParent()).isNull();
        assertThat(in(regions, replacement).width).isEqualTo(320);
        assertThat(regions.size(PanelRegion.BOTTOM)).isEqualTo(150);
    }

    @Test void sizesAreClampedToAUsableMinimum() {
        var regions = new WorkspaceRegions(new JPanel());
        regions.show(PanelRegion.LEFT, new JLabel("left"), 5);
        layout(regions);
        assertThat(regions.size(PanelRegion.LEFT)).isGreaterThanOrEqualTo(WorkspaceRegions.MINIMUM);
    }
}
