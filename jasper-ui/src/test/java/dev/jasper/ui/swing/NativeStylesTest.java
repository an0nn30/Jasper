package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Native adapter recipes consume module colors without changing the host's existing geometry or button kind. */
class NativeStylesTest {
    private static final NativeStyles STYLES = new NativeStyles(Kit.DEFAULT);
    private static final ChromePainter PAINTER = new ChromePainter(Kit.DEFAULT);
    @Test void typographySubscriptionIsOwnedAndSuppressesQueuedDeliveryAfterClose() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();var handle=new java.util.concurrent.atomic.AtomicReference<AutoCloseable>();
        Object previous=UIManager.get("defaultFont");
        com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
        int listeners=UIManager.getDefaults().getPropertyChangeListeners().length;
        try {
            Edt.run(()->{
                handle.set(STYLES.observeTypography(()->{assertThat(EventQueue.isDispatchThread()).isTrue();calls.incrementAndGet();}));
                assertThat(calls.get()).as("registration never calls a partly constructed owner").isZero();
                UIManager.put("defaultFont",new Font(Font.DIALOG,Font.PLAIN,20));
                assertThat(calls.get()).isPositive();calls.set(0);
                var worker=new Thread(()->UIManager.put("defaultFont",new Font(Font.DIALOG,Font.PLAIN,32)));
                worker.start();try{worker.join();handle.get().close();handle.get().close();}catch(Exception failure){throw new RuntimeException(failure);}
            });
            Edt.run(()->assertThat(calls.get()).as("queued callback is suppressed after close").isZero());
            assertThat(UIManager.getDefaults().getPropertyChangeListeners().length).isEqualTo(listeners);
        }finally{Edt.run(()->UIManager.put("defaultFont",previous));if(handle.get()!=null)handle.get().close();}
    }

    @Test void contextFontUsesTheSameHostOverrideAndCustomScalePolicyAsKitLabels() {
        Edt.run(()->{
            Object previous=UIManager.get("defaultFont");var context=KitFixtures.context();
            try {
                for(float size:new float[]{20,32}) {
                    UIManager.put("defaultFont",new Font(Font.DIALOG,Font.PLAIN,1).deriveFont(size));
                    for(var role:dev.jasper.ui.metrics.Typography.Role.values())
                        assertThat(STYLES.font(context,role)).isEqualTo(ThemePainting.font(context,role));
                    assertThat(STYLES.font(context,dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()).isEqualTo(size);
                }
            }finally{UIManager.put("defaultFont",previous);KitViewTest.release(context);}
        });
    }

    @Test void ghostToolbarRecipeAddsRoleColorsButKeepsTheHostsArcAndMargin() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            var palette = theme.semantic();
            var button = new JButton();
            Insets margin = button.getMargin();
            STYLES.button(button, () -> theme);
            assertThat(button.getClientProperty("JButton.buttonType")).isEqualTo("toolBarButton");
            assertThat(button.getMargin()).as("toolbar margins stay the look and feel's").isEqualTo(margin).isInstanceOf(javax.swing.plaf.UIResource.class);
            assertThat(button.getClientProperty("FlatLaf.style")).isEqualTo(Map.of(
                "toolbar.hoverBackground", ColorMath.opacity(palette.color(SemanticColor.HOVER_OVERLAY), .08),
                "toolbar.pressedBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER),
                "toolbar.selectedBackground", palette.color(SemanticColor.SURFACE_CONTROL_SELECTED)));
            var rail = new JButton();
            STYLES.button(rail, () -> theme, DesignMetrics.Role.RAIL_BUTTON_ARC);
            assertThat(((Map<?, ?>) rail.getClientProperty("FlatLaf.style")).get("arc")).isEqualTo(DesignMetrics.DEFAULT.value(DesignMetrics.Role.RAIL_BUTTON_ARC));
        });
    }

    @Test void borderlessFieldKeepsZeroBorderAndFocusWidthsAcrossDelegateChanges() {
        Edt.run(() -> {
            var theme = KitFixtures.light();
            var palette = theme.semantic();
            var field = new JTextField("query");
            STYLES.borderlessField(field, () -> theme);
            SwingUtilities.updateComponentTreeUI(field);
            assertThat(field.getClientProperty("FlatLaf.style")).isEqualTo("borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
            assertThat(field.getBackground()).isEqualTo(palette.color(SemanticColor.SURFACE_INPUT));
            assertThat(field.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_PRIMARY));
            assertThat(field.getSelectionColor()).isEqualTo(palette.color(SemanticColor.SELECTION_BG));
        });
    }

    @Test void framedButtonIsAStandardSecondaryButtonWithTheHostsFontAndMargin() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            var palette = theme.semantic();
            var button = new JButton("Reconnect");
            Font font = button.getFont(); Insets margin = button.getMargin();
            STYLES.framedButton(button, () -> theme);
            assertThat(button.getClientProperty("JButton.buttonType")).as("framed, not a toolbar ghost").isNull();
            assertThat(button.isContentAreaFilled()).isTrue();
            assertThat(button.isBorderPainted()).isTrue();
            assertThat(button.getFont()).isSameAs(font);
            assertThat(button.getMargin()).isEqualTo(margin).isInstanceOf(javax.swing.plaf.UIResource.class);
            assertThat(button.getForeground()).isEqualTo(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
            assertThat(button.getClientProperty("FlatLaf.style")).isEqualTo(Map.of(
                "background", palette.color(SemanticColor.BUTTON_SECONDARY_BG),
                "hoverBackground", palette.color(SemanticColor.BUTTON_SECONDARY_BG_HOVER),
                "borderColor", palette.color(SemanticColor.BORDER_CONTROL),
                "focusedBorderColor", palette.color(SemanticColor.BORDER_FOCUS)));
        });
    }

    @Test void nativeMenuRecipeKeepsTheLookAndFeelMargins() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            var item = new JMenuItem("Hide");
            Insets margin = item.getMargin();
            STYLES.menu(item, () -> theme);
            assertThat(item.getMargin()).isEqualTo(margin).isInstanceOf(javax.swing.plaf.UIResource.class);
            assertThat(item.getForeground()).isEqualTo(theme.semantic().color(SemanticColor.TEXT_PRIMARY));
        });
    }

    @Test void toolbarCommandKeepsItsSixPixelHoverArcAndThirtyEightPercentDisabledContent() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            Icon icon = new Icon() {
                @Override public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(Color.RED); g.fillRect(x, y, 16, 16); }
                @Override public int getIconWidth() { return 16; }
                @Override public int getIconHeight() { return 16; }
            };
            var button = new JButton(icon);
            button.setSize(40, 24);
            button.getModel().setRollover(true);
            var hovered = paint(button, theme);
            var reference = new BufferedImage(40, 24, BufferedImage.TYPE_INT_ARGB);
            var g = reference.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ColorMath.opacity(theme.semantic().color(SemanticColor.HOVER_OVERLAY), .08));
                int arc = com.formdev.flatlaf.util.UIScale.scale(6);
                g.fillRoundRect(0, 0, 40, 24, arc, arc);
            } finally { g.dispose(); }
            for (int y = 0; y < 6; y++) for (int x = 0; x < 6; x++)
                assertThat(hovered.getRGB(x, y)).as("hover corner %d,%d", x, y).isEqualTo(reference.getRGB(x, y));
            button.getModel().setRollover(false);
            button.setEnabled(false);
            var disabled = paint(button, theme);
            var expected = new BufferedImage(40, 24, BufferedImage.TYPE_INT_ARGB);
            var e = expected.createGraphics();
            try { e.setComposite(AlphaComposite.SrcOver.derive(.38f)); e.setColor(Color.RED); e.fillRect(12, 4, 16, 16); }
            finally { e.dispose(); }
            assertThat(disabled.getRGB(20, 12)).as("disabled content at .38").isEqualTo(expected.getRGB(20, 12));
        });
    }

    private static BufferedImage paint(AbstractButton button, GeneratedTheme theme) {
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { PAINTER.action(button, g, theme, false, null); } finally { g.dispose(); }
        return image;
    }

    /** The seam: styles read only their own kit's metrics, scaled once, and the ghost arc unscaled for FlatLaf. */
    @Test void stylesReadOnlyTheirOwnKit() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            var styles = new NativeStyles(KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.HAIRLINE, 3)
                .with(DesignMetrics.Role.RAIL_BUTTON_ARC, 13)));
            assertThat(styles.metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
            assertThat(STYLES.metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(1));
            var rail = new JButton();
            styles.button(rail, () -> theme, DesignMetrics.Role.RAIL_BUTTON_ARC);
            assertThat(((Map<?, ?>) rail.getClientProperty("FlatLaf.style")).get("arc")).as("logical; FlatLaf scales style values").isEqualTo(13);
            assertThat(styles.padding(DesignMetrics.Role.HAIRLINE, DesignMetrics.Role.HAIRLINE).getBorderInsets(rail).top)
                .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
            assertThat(styles.font(dev.jasper.ui.metrics.Typography.Role.BODY)).as("the same type and faces")
                .isEqualTo(STYLES.font(dev.jasper.ui.metrics.Typography.Role.BODY));
        });
    }

    /** Review Focus 5: the installed styles and painter follow the installed kit at once, one instance per kit. */
    @Test void installedFollowsTheInstalledKitWithOneInstancePerKit() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            var theme = KitFixtures.dark();
            var derived = KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.HAIRLINE, 3));
            try {
                FlatLafInstaller.install(theme, Kit.DEFAULT);
                assertThat(NativeStyles.installed().kit()).isSameAs(Kit.DEFAULT);
                assertThat(NativeStyles.installed()).isSameAs(NativeStyles.installed());
                assertThat(ChromePainter.installed()).isSameAs(ChromePainter.installed());
                FlatLafInstaller.install(theme, derived);
                assertThat(NativeStyles.installed().kit()).isSameAs(derived);
                assertThat(ChromePainter.installed().kit()).isSameAs(derived);
                assertThat(NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
                FlatLafInstaller.install(theme, Kit.DEFAULT);
                assertThat(ChromePainter.installed().kit()).as("back to the default kit at once").isSameAs(Kit.DEFAULT);
                assertThat(NativeStyles.installed().kit()).isSameAs(Kit.DEFAULT);
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
}
