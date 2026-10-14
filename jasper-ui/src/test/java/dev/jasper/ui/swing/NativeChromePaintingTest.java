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
    @Test void tabSurfacesAreTheStripAndTerminalRolesAtTheWorkspacesAlpha() {
        var theme = KitFixtures.marked();
        var glass = new Color(0x22, 0x26, 0x29, 200);
        var idle = NativeChromePainting.tabSurface(theme, glass, false);
        var chosen = NativeChromePainting.tabSurface(theme, glass, true);
        assertThat(idle.getRGB() & 0xFFFFFF).isEqualTo(SurfaceContrast.apart(KitFixtures.marker(SemanticColor.SURFACE_TABSTRIP),
            KitFixtures.marker(SemanticColor.SURFACE_TERMINAL), SurfaceContrast.TAB_LIGHTNESS).getRGB() & 0xFFFFFF);
        assertThat(chosen.getRGB() & 0xFFFFFF).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TERMINAL).getRGB() & 0xFFFFFF);
        assertThat(idle.getAlpha()).isEqualTo(200);
        assertThat(chosen.getAlpha()).isEqualTo(200);
    }

    @Test void anInactiveTabStaysClearlyDarkerThanTheActiveOneWhenTheThemeGivesThemOneSurface() {
        var base = KitFixtures.dark();
        var colors = new java.util.EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, base.semantic().color(role));
        colors.put(SemanticColor.SURFACE_TERMINAL, new Color(0x303446));
        colors.put(SemanticColor.SURFACE_TABSTRIP, new Color(0x303446));
        var theme = KitFixtures.withSemantic(base, new SemanticPalette(colors));
        var opaque = new Color(0x30, 0x34, 0x46);
        Color idle = NativeChromePainting.tabSurface(theme, opaque, false), chosen = NativeChromePainting.tabSurface(theme, opaque, true);
        assertThat(chosen).isEqualTo(new Color(0x303446));
        assertThat(ColorMath.lightness(chosen) - ColorMath.lightness(idle)).isGreaterThanOrEqualTo(SurfaceContrast.TAB_LIGHTNESS);
    }

    @Test void tabStripPaintsSlotsAHoveredSlotDividersAndTheLowerRule() {
        var theme = KitFixtures.marked();
        var base = new Color(0x22, 0x26, 0x29);
        var strip = new JPanel(); strip.setSize(300, 28);
        var image = new BufferedImage(300, 28, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.tabStrip(strip, g, theme, base, List.of(new Rectangle(0, 0, 100, 28), new Rectangle(100, 0, 100, 28), new Rectangle(200, 0, 100, 28)), 1, 2); }
        finally { g.dispose(); }
        int border = KitFixtures.marker(SemanticColor.BORDER_WINDOW).getRGB();
        int idle = NativeChromePainting.tabSurface(theme, base, false).getRGB();
        assertThat(image.getRGB(50, 10)).isEqualTo(idle);
        assertThat(image.getRGB(150, 10)).isEqualTo(KitFixtures.marker(SemanticColor.SURFACE_TERMINAL).getRGB());
        assertThat(image.getRGB(250, 10)).as("a hovered inactive tab takes the hover overlay")
            .isNotEqualTo(idle);
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
        // theme.md's popover shadow: black, lowered 12 and blurred 32, so it reaches 48 beyond the face and no further.
        var image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { NativeChromePainting.popupShadow(g, new Rectangle(100, 80, 40, 40), theme, true); } finally { g.dispose(); }
        var below = new Color(image.getRGB(120, 122), true);
        assertThat(below.getAlpha()).as("below the card").isPositive();
        assertThat(below.getRGB() & 0xFFFFFF).as("black").isZero();
        assertThat(image.getRGB(100 - 49, 100) >>> 24).as("nothing beyond the blur").isZero();
    }
}
