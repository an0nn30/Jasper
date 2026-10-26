package dev.jasper.app.appearance;

import com.formdev.flatlaf.util.UIScale;
import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.NativeStyles;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EnterpriseDisplayScaleTest {
    @Test void defaultEnterpriseNativeAndCustomControlsShareFractionalScaleAndDeviceTransformsStaySeparate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String previous = System.getProperty("flatlaf.uiScale");
            try {
                float[] scales = previous == null ? new float[]{1, 1.25f, 1.5f, 2} : new float[]{Float.parseFloat(previous)};
                for (float scale : scales) {
                    System.setProperty("flatlaf.uiScale", Float.toString(scale));
                    try (var themes = EnterpriseLook.controller(false)) {
                        assertThat(UIScale.getUserScaleFactor()).isEqualTo(scale);
                        var context = themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
                        var node = new dev.jasper.ui.model.Nodes.Button("action", dev.jasper.ui.model.ControlState.READY,
                            "Action", "add", dev.jasper.ui.model.Nodes.ButtonKind.PRIMARY, "action", "Action");
                        try (var view = new dev.jasper.ui.swing.KitView(context, new dev.jasper.ui.model.ViewSpec(node), event -> {})) {
                            var custom = dev.jasper.app.testsupport.KitTestSupport.require(view, "action", JButton.class);
                            var nativeButton = new JButton("Action");
                            var nativeField = new JTextField("value");
                            for (var component : new JComponent[]{custom, nativeButton, nativeField}) {
                                assertThat(component.getFont().getSize2D()).as("%s at %s", component.getClass().getName(), scale)
                                    .isEqualTo(12 * scale);
                                component.setSize(component.getPreferredSize());
                                assertThat(component.getHeight()).isGreaterThanOrEqualTo(Math.round((component instanceof JTextField ? 22 : 24) * scale));
                                assertThat(component.contains(component.getWidth() / 2, component.getHeight() / 2)).isTrue();
                            }
                            assertThat(custom.getIcon().getIconWidth()).isEqualTo(Math.round(14 * scale));
                            var image = new java.awt.image.BufferedImage(custom.getWidth() * 2, custom.getHeight() * 2,
                                java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            var graphics = image.createGraphics();
                            try {
                                graphics.scale(2, 2); custom.paint(graphics);
                                assertThat(custom.getFont().getSize2D()).isEqualTo(12 * scale);
                                assertThat(graphics.getTransform().getScaleX()).isEqualTo(2);
                            } finally { graphics.dispose(); }
                        }
                    }
                }
            } finally {
                if (previous == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", previous);
                EnterpriseLook.restoreDefault();
            }
        });
    }

    @Test void aFamilyOnlyOverrideKeepsTheDesktopScaleForNativeAndCustomControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String previous = System.getProperty("flatlaf.uiScale");
            System.clearProperty("flatlaf.uiScale");
            float divider = SystemInfo.isMacOS ? 13 : SystemInfo.isLinux ? (SystemInfo.isKDE ? 13 : 15) : 12;
            var themes = ThemeController.start(false, EnterpriseLook.selection(), EnterpriseLook.kit(), kit -> theme -> {
                dev.jasper.ui.swing.FlatLafInstaller.install(theme, kit);
                // The desktop font already carries the user's text scale. Enterprise's face must not become its source.
                UIManager.getLookAndFeelDefaults().put("defaultFont", new FontUIResource("Dialog", java.awt.Font.PLAIN, Math.round(divider * 2)));
                return true;
            }, failure -> { throw new AssertionError(failure); }).controller();
            try {
                assertThat(UIScale.getUserScaleFactor()).isEqualTo(2);
                var styles = new NativeStyles(themes.kit());
                int originalHeight = styles.metric(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT);
                assertThat(new JLabel().getFont().getSize2D()).isEqualTo(24);
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("Serif", 0));
                assertThat(UIScale.getUserScaleFactor()).as("family-only change preserves desktop scale").isEqualTo(2);
                assertThat(styles.metric(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(originalHeight);
                assertThat(styles.font(Typography.Role.BODY).getSize2D()).isEqualTo(24);
                for (var control : new JComponent[]{new JLabel(), new JButton("Action"), new JTextField("value"), new JMenuItem("Action")}) {
                    assertThat(control.getFont().getFamily()).isEqualTo("Serif");
                    assertThat(control.getFont().getSize2D()).isEqualTo(24);
                }
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
                assertThat(UIScale.getUserScaleFactor()).isEqualTo(2);
                assertThat(new JLabel().getFont().getFontName(java.util.Locale.ROOT)).isEqualTo("IBM Plex Sans");
            } finally {
                themes.close();
                if (previous == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", previous);
                EnterpriseLook.restoreDefault();
            }
        });
    }
}
