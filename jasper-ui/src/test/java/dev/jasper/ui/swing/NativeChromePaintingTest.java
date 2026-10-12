package dev.jasper.ui.swing;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Plan 3 Task 10: the workspace chrome's former per-feature paint, now kit painters over named roles and themes. */
class NativeChromePaintingTest {
    @Test void tabSurfacesLiftTheSelectedTabAndSinkTheOthersByTheirNamedShares() {
        var base = new Color(0x22, 0x26, 0x29);
        // The former owner literals: .035/.3 for dark workspaces and .12/.06 for light ones.
        assertThat(NativeChromePainting.tabSurface(base, true, true)).isEqualTo(blend(base, .035, 255));
        assertThat(NativeChromePainting.tabSurface(base, true, false)).isEqualTo(blend(base, .3, 0));
        var light = new Color(0xf7, 0xf8, 0xfa, 200);
        assertThat(NativeChromePainting.tabSurface(light, false, true)).isEqualTo(blend(light, .12, 255));
        assertThat(NativeChromePainting.tabSurface(light, false, false)).isEqualTo(blend(light, .06, 0));
        assertThat(NativeChromePainting.tabSurface(light, false, false).getAlpha()).isEqualTo(200);
    }

    @Test void tabStripPaintsSlotsDividersAndTheLowerRuleInTheWorkspaceBorderRole() {
        var theme = KitFixtures.dark();
        var base = new Color(0x22, 0x26, 0x29);
        var strip = new JPanel(); strip.setSize(200, 28);
        var image = new BufferedImage(200, 28, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.tabStrip(strip, g, theme, base, List.of(new Rectangle(0, 0, 100, 28), new Rectangle(100, 0, 100, 28)), 1); }
        finally { g.dispose(); }
        int border = theme.semantic().color(SemanticColor.BORDER_WINDOW).getRGB();
        assertThat(image.getRGB(50, 10)).isEqualTo(NativeChromePainting.tabSurface(base, true, false).getRGB());
        assertThat(image.getRGB(150, 10)).isEqualTo(NativeChromePainting.tabSurface(base, true, true).getRGB());
        assertThat(image.getRGB(99, 10)).as("divider between slots").isEqualTo(border);
        assertThat(image.getRGB(150, 27)).as("lower rule").isEqualTo(border);
    }

    @Test void toolbarActionWidthKeepsItsPaddingIconSpanAndMenuSpace() {
        Edt.run(() -> {
            var button = new JButton("Split");
            button.setIcon(new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));
            int text = button.getFontMetrics(NativeControlStyles.font(dev.jasper.ui.metrics.Typography.Role.TOOLBAR_LABEL)).stringWidth("Split");
            int pad = NativeControlStyles.metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
            assertThat(NativeChromePainting.actionWidth(button, 28, false, false)).isEqualTo(28);
            assertThat(NativeChromePainting.actionWidth(button, 28, true, false))
                .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(24) + pad + text);
            assertThat(NativeChromePainting.actionWidth(button, 28, true, true))
                .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(24) + pad + text + NativeControlStyles.metric(DesignMetrics.Role.NATIVE_MENU_SPACE));
        });
    }

    @Test void popupShadowAndDropGuidesUseTheirNamedAlphas() {
        var theme = KitFixtures.dark();
        assertThat(NativeChromePainting.dropGuide(theme, false).getAlpha()).isEqualTo(DesignMetrics.DEFAULT.value(DesignMetrics.Role.DOCK_DROP_FILL_ALPHA));
        assertThat(NativeChromePainting.dropGuide(theme, true).getAlpha()).isEqualTo(DesignMetrics.DEFAULT.value(DesignMetrics.Role.DOCK_DROP_BORDER_ALPHA));
        var image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.popupShadow(g, new Rectangle(20, 20, 40, 40), theme, true); } finally { g.dispose(); }
        int spread = DesignMetrics.DEFAULT.value(DesignMetrics.Role.PALETTE_SHADOW_SPREAD);
        assertThat(image.getRGB(20 - spread, 30) >>> 24).as("outermost ring").isEqualTo(DesignMetrics.DEFAULT.value(DesignMetrics.Role.PALETTE_SHADOW_ALPHA_DARK));
        assertThat(image.getRGB(20 - spread - 1, 30) >>> 24).as("beyond the spread").isZero();
    }

    private static Color blend(Color base, double blend, int target) {
        return new Color((int) Math.round(base.getRed() * (1 - blend) + target * blend), (int) Math.round(base.getGreen() * (1 - blend) + target * blend),
            (int) Math.round(base.getBlue() * (1 - blend) + target * blend), base.getAlpha());
    }
}
