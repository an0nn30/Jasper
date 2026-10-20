package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.util.List;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a native tool window header under flush frames is a header strip. */
@ExtendWith(EdtTestExtension.class)
class HeaderStripTest {
    private static ToolWindowHeader header(boolean flush) {
        EnterpriseLook.install(false);
        return new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { }, EnterpriseLook.kit(), flush);
    }

    private static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    @Test void aFlushHeaderIsAStripOverItsRuleThatFollowsFocus() throws Exception {
        try {
            var header = header(true);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            assertThat(header.getPreferredSize().height).as("24 and its rule").isEqualTo(metric(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + metric(DesignMetrics.Role.DOCK_RULE));
            var image = MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            assertThat(new Color(image.getRGB(150, 5))).isEqualTo(painter.headerSurface(theme, false));
            assertThat(new Color(image.getRGB(150, image.getHeight() - 1))).as("the rule").isEqualTo(painter.dockRule(theme));
            header.setFocused(true);
            image = MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            assertThat(new Color(image.getRGB(150, 5))).as("focused").isEqualTo(painter.headerSurface(theme, true));
            assertThat(header.titleLabel().getForeground()).isEqualTo(painter.headerTitle(theme, true));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aFlushHeadersDetailIsMutedAndADigitsOnlyOneParenthesised() throws Exception {
        try {
            var header = header(true);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            header.presentation(new PanelHeaderState("2", false, List.of()));
            var detail = (javax.swing.JLabel) header.dragSurfaces().get(2);
            assertThat(detail.getText()).isEqualTo("(2)");
            assertThat(detail.getForeground()).isEqualTo(painter.headerDetail(theme, false, true));
            header.presentation(new PanelHeaderState("Mac", true, List.of()));
            assertThat(detail.getText()).isEqualTo("Mac");
            assertThat(detail.getForeground()).isEqualTo(painter.headerDetail(theme, false, false));
            assertThat(detail.getIconTextGap()).isEqualTo(metric(DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aFlushHeadersActionsAreSixteenPixelSquaresAndHideIsTheMinus() throws Exception {
        try {
            var header = header(true);
            header.presentation(new PanelHeaderState("", false, List.of(new PanelHeaderState.Action("Add", AppIcons.semantic("add"), () -> { }))));
            int side = metric(DesignMetrics.Role.TOOL_HEADER_ACTION), glyph = metric(DesignMetrics.Role.PANEL_HEADER_CONTRIBUTED_GLYPH);
            assertThat(header.optionsButton().getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(header.hideButton().getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(AppIcons.kitName(header.hideButton().getIcon())).as("Hide is the minus").hasValue("remove");
            assertThat(AppIcons.kitName(header.optionsButton().getIcon())).hasValue("more");
            MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            var add = SwingFind.button(header, "Add");
            assertThat(AppIcons.kitName(add.getIcon())).hasValue("add");
            for (var button : List.of(add, header.optionsButton(), header.hideButton()))
                assertThat(button.getIcon().getIconWidth()).as(button.getToolTipText() + ": one glyph box for every action").isEqualTo(glyph).isLessThan(side);
            assertThat(add.getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(((java.awt.FlowLayout) add.getParent().getLayout()).getHgap()).as("2 apart, 18 between centres").isEqualTo(2);
            var addBounds = javax.swing.SwingUtilities.convertRectangle(add.getParent(), add.getBounds(), header);
            var hideBounds = javax.swing.SwingUtilities.convertRectangle(header.hideButton().getParent(), header.hideButton().getBounds(), header);
            assertThat(addBounds.getCenterY()).as("a contributed action is centred on the strip, as Hide is").isEqualTo(hideBounds.getCenterY());
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Spec 16.1.2: a flush header, focused and not, paints its title, a word detail, a digits-only detail and its action
     * glyphs in their declared pairs on the strip's painted surface, sampled from the 4x render inside each label and
     * action, over every palette.
     */
    @Test void aFlushHeaderPaintsItsTextInItsDeclaredPairsOverEveryPalette() throws Exception {
        var palettes = new java.util.ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
        palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
        try {
            for (var theme : palettes) for (boolean focused : List.of(false, true)) {
                dev.jasper.ui.swing.FlatLafInstaller.install(theme, EnterpriseLook.kit());
                var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { }, EnterpriseLook.kit(), true);
                header.setFocused(focused);
                String where = theme.name() + (theme.dark() ? " Dark" : " Light") + (focused ? ", focused" : "");
                var detail = (javax.swing.JLabel) header.dragSurfaces().get(2);
                header.presentation(new PanelHeaderState("Mac", false, List.of(new PanelHeaderState.Action("Add", AppIcons.semantic("add"), () -> { }))));
                var image = MockCrops.render(header, 300, header.getPreferredSize().height, 4);
                assertInk(image, header, header.titleLabel(), focused ? ChromePainter.FOCUSED_HEADER_TITLE : ChromePainter.HEADER_TITLE, theme, focused, where + " title");
                assertInk(image, header, detail, focused ? ChromePainter.FOCUSED_HEADER_DETAIL : ChromePainter.HEADER_DETAIL, theme, focused, where + " detail");
                for (var button : List.of(SwingFind.button(header, "Add"), header.optionsButton(), header.hideButton()))
                    assertInk(image, header, button, focused ? ChromePainter.FOCUSED_HEADER_ACTION : ChromePainter.HEADER_ACTION, theme, focused,
                        where + " " + button.getToolTipText());
                header.presentation(new PanelHeaderState("2", false, List.of()));
                image = MockCrops.render(header, 300, header.getPreferredSize().height, 4);
                assertInk(image, header, detail, focused ? ChromePainter.FOCUSED_HEADER_COUNT : ChromePainter.HEADER_COUNT, theme, focused, where + " count");
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Asserts that {@code label}'s bounds in {@code image}, a 4x render of {@code header}, hold the pair's colour as painted
     * on the strip's surface.
     */
    private static void assertInk(java.awt.image.BufferedImage image, ToolWindowHeader header, javax.swing.JComponent label,
                                  dev.jasper.ui.theme.PaintPair pair, dev.jasper.ui.theme.GeneratedTheme theme, boolean focused, String what) {
        Color expected = dev.jasper.ui.theme.SurfaceContrast.painted(theme, pair, new ChromePainter(EnterpriseLook.kit()).headerSurface(theme, focused));
        var bounds = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), header);
        assertThat(LabelledStripeTest.inks(image, bounds, 4, expected)).as(what + " " + dev.jasper.ui.theme.ColorMath.hex(expected)).isTrue();
    }

    @Test void aStandardHeaderIsUnchanged() throws Exception {
        try {
            ThemeTestSupport.install(true);
            var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { });
            assertThat(header.getBackground()).isEqualTo(ThemeTestSupport.color(true, SemanticColor.SURFACE_PANEL));
            assertThat(header.hideButton().getIcon()).isSameAs(AppIcons.semantic("collapse"));
            header.presentation(new PanelHeaderState("2", false, List.of()));
            assertThat(((javax.swing.JLabel) header.dragSurfaces().get(2)).getText()).isEqualTo("2");
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Finds a header's contributed button by its tooltip. */
    private static final class SwingFind {
        static javax.swing.JButton button(java.awt.Container root, String tip) {
            for (var child : root.getComponents()) {
                if (child instanceof javax.swing.JButton button && tip.equals(button.getToolTipText())) return button;
                if (child instanceof java.awt.Container nested) { var found = button(nested, tip); if (found != null) return found; }
            }
            return null;
        }
    }
}
