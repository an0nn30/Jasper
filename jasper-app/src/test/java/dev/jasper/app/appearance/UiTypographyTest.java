package dev.jasper.app.appearance;

import dev.jasper.app.config.UiFontConfig;
import java.awt.Font;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiTypographyTest {
    static int m(dev.jasper.ui.metrics.DesignMetrics.Role role) { return dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role); }

    @Test void actualMountedAndAdapterSizesApplyConfiguredPointsAndDisplayScaleOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String previousScale = System.getProperty("flatlaf.uiScale");
            try {
                for (String scale : new String[]{null, "2"}) {
                    if (scale == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", scale);
                    var themes = ThemeTestSupport.controller(true);
                    var context = themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
                    var spec = new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Stack("root", dev.jasper.ui.model.ControlState.READY,
                        dev.jasper.ui.model.Nodes.LayoutRole.VERTICAL, java.util.List.of(
                            new dev.jasper.ui.model.Nodes.Text("body", dev.jasper.ui.model.ControlState.READY, "Body", dev.jasper.ui.model.Nodes.TextRole.BODY),
                            new dev.jasper.ui.model.Nodes.Text("description", dev.jasper.ui.model.ControlState.READY, "First\nSecond", dev.jasper.ui.model.Nodes.TextRole.DESCRIPTION),
                            new dev.jasper.ui.model.Nodes.Secret("password", dev.jasper.ui.model.ControlState.READY,"Password","Password",""))));
                    try (var view = new dev.jasper.ui.swing.KitView(context, spec, event -> {})) {
                        for (var config : java.util.List.of(UiFontConfig.defaults(), new UiFontConfig("system", 20),
                                new UiFontConfig("Serif", 20), new UiFontConfig("Helvetica Neue", 18.5f), UiFontConfig.defaults())) {
                            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, config);
                            float expected = config.size() == 0 ? 13 * com.formdev.flatlaf.util.UIScale.getUserScaleFactor() : config.size();
                            var body = dev.jasper.app.testsupport.KitTestSupport.require(view, "body", JLabel.class);
                            assertThat(body.getFont().getSize2D()).as("mounted config=%s scale=%s", config, scale).isEqualTo(expected);
                            var input = dev.jasper.app.testsupport.KitTestSupport.require(view,"password.input",JPasswordField.class);
                            assertThat(input.getFont().getSize2D()).isEqualTo(expected);
                            int minimumHeight = com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT));
                            int actualLine = (int)Math.ceil(input.getFont().getLineMetrics("Ag",new java.awt.font.FontRenderContext(null,true,true)).getHeight());
                            assertThat(input.getParent().getPreferredSize().height).as("field accommodates actual text plus scaled padding once")
                                .isEqualTo(Math.max(minimumHeight, actualLine + com.formdev.flatlaf.util.UIScale.scale(m(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_VERTICAL_PADDING))));
                            var description = dev.jasper.app.testsupport.KitTestSupport.require(view,"description",JLabel.class);
                            int expectedLineHeight = Math.max(Math.round(18*expected/13),description.getFontMetrics(description.getFont()).getHeight());
                            assertThat(description.getPreferredSize().height).as("two description lines sized once").isEqualTo(2*expectedLineHeight);
                            assertThat(dev.jasper.ui.swing.NativeControlStyles.font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()).isEqualTo(expected);
                            assertThat(dev.jasper.ui.swing.NativeControlStyles.font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getSize2D()).isCloseTo(expected * 15 / 13, within(.001f));
                            // Defaults leave native forms on the look and feel's own font, which FlatLaf scales from its platform divider
                            // (13 on macOS, 15 on Linux); a configured size is exact everywhere.
                            float nativeSize = config.size() == 0 ? UIManager.getLookAndFeelDefaults().getFont("Label.font").getSize2D() : expected;
                            assertThat(new JLabel().getFont().getSize2D()).as("native form policy").isEqualTo(nativeSize);
                            if (com.formdev.flatlaf.util.SystemInfo.isMacOS) assertThat(nativeSize).isEqualTo(expected);
                            if (config.family().equals("Serif")) assertThat(body.getFont().getFamily()).isEqualTo(Font.SERIF);
                            assertThat(dev.jasper.ui.swing.NativeControlStyles.metric(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT))
                                .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.FIELD_HEIGHT)));
                            var image = new java.awt.image.BufferedImage(200, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            var graphics = image.createGraphics();
                            try { graphics.scale(2, 2); view.setSize(100, 40); view.doLayout(); view.paint(graphics);
                                assertThat(graphics.getTransform().getScaleX()).isEqualTo(2);
                                assertThat(body.getFont().getSize2D()).as("device transform never mutates logical point size").isEqualTo(expected);
                            } finally { graphics.dispose(); }
                        }
                    } finally { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); themes.close(); }
                }
            } finally {
                if (previousScale == null) System.clearProperty("flatlaf.uiScale"); else System.setProperty("flatlaf.uiScale", previousScale);
                var reset = ThemeTestSupport.controller(true); reset.close();
            }
        });
    }

    @Test void sizeOnlySystemSettingsKeepSharedRoleFacesAndExplicitFamiliesRemainExplicit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = ThemeTestSupport.controller(true);
            var context = themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var defaults = new dev.jasper.ui.metrics.Typography();
            String bodyFace = defaults.font(dev.jasper.ui.metrics.Typography.Role.BODY).getFontName();
            String headingFace = defaults.font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getFontName();
            var spec = new dev.jasper.ui.model.ViewSpec(new dev.jasper.ui.model.Nodes.Stack("root", dev.jasper.ui.model.ControlState.READY,
                dev.jasper.ui.model.Nodes.LayoutRole.VERTICAL, java.util.List.of(
                    new dev.jasper.ui.model.Nodes.Text("body", dev.jasper.ui.model.ControlState.READY, "Body", dev.jasper.ui.model.Nodes.TextRole.BODY),
                    new dev.jasper.ui.model.Nodes.Text("heading", dev.jasper.ui.model.ControlState.READY, "Heading", dev.jasper.ui.model.Nodes.TextRole.HERO_TITLE))));
            try (var view = new dev.jasper.ui.swing.KitView(context, spec, event -> {})) {
                for (var config : java.util.List.of(new UiFontConfig("system", 13), new UiFontConfig("system", 20), UiFontConfig.defaults())) {
                    themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, config);
                    var body = dev.jasper.app.testsupport.KitTestSupport.require(view, "body", JLabel.class);
                    var heading = dev.jasper.app.testsupport.KitTestSupport.require(view, "heading", JLabel.class);
                    assertThat(body.getFont().getFontName()).as("system size-only body %s", config).isEqualTo(bodyFace);
                    assertThat(heading.getFont().getFontName()).as("system size-only heading %s", config).isEqualTo(headingFace);
                    assertThat(dev.jasper.ui.swing.NativeControlStyles.font(dev.jasper.ui.metrics.Typography.Role.BODY).getFontName()).isEqualTo(bodyFace);
                    assertThat(dev.jasper.ui.swing.NativeControlStyles.font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getFontName()).isEqualTo(headingFace);
                    if (config.size() != 0) {
                        assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getSize2D()).isEqualTo(config.size());
                        assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getSize2D()).isCloseTo(15 * config.size() / 13f, within(.001f));
                    }
                }
                // Choosing the platform's own family explicitly keeps that family (Helvetica Neue on macOS).
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig(defaults.family(), 20));
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getFamily()).isEqualTo(new Font(defaults.family(), Font.PLAIN, 20).getFamily());
                if (com.formdev.flatlaf.util.SystemInfo.isMacOS) assertThat(defaults.family()).isEqualTo("Helvetica Neue");
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("Serif", 20));
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getFamily()).isEqualTo(Font.SERIF);
            } finally { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); themes.close(); }
        });
    }

    @Test void configuredPointSizesAreExactAcrossTheWholeSupportedRange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = ThemeTestSupport.controller(true);
            try {
                for (float size : new float[]{8, 12, 14, 18.5f, 24, 28, 32}) {
                    themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("system", size));
                    assertThat(new JLabel().getFont().getSize2D()).as("configured %s", size).isEqualTo(size);
                    assertThat(new JButton().getFont().getSize2D()).isEqualTo(size);
                }
            } finally { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); }
        });
    }

    @Test void missingFamiliesFallBackWhenAThemeAndFontAreConfiguredTogether() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = ThemeTestSupport.controller(true);
            String platformFamily = UIManager.getFont("Label.font").getFamily();
            try {
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("NonexistentJasperFont394", 17));
                assertThat(themes.current().chrome()).as("the theme just configured applies").isEqualTo(dev.jasper.app.appearance.ThemeTestSupport.generated(true));
                assertThat(new JLabel().getFont().getFamily()).isEqualTo(platformFamily);
                assertThat(new JLabel().getFont().getSize2D()).isEqualTo(17);
            } finally { themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); }
        });
    }

    @Test void liveFontChangesReachControlsAndRetainStyleAcrossThemeChangesAndReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = ThemeTestSupport.controller(true);
            var panel = new JPanel();
            var label = new JLabel("Host");
            var button = new JButton("Connect");
            var field = new JTextField("search");
            panel.add(label); panel.add(button); panel.add(field);
            Font original = label.getFont();
            var subscription = themes.subscribe((theme, delegates) -> { if (delegates) SwingUtilities.updateComponentTreeUI(panel); });
            try {
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, new UiFontConfig("Serif", 18));
                for (var component : new JComponent[]{label, button, field}) {
                    assertThat(component.getFont().getFamily()).isEqualTo(Font.SERIF);
                    assertThat(component.getFont().getSize2D()).isEqualTo(18);
                }
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                assertThat(label.getFont().getFamily()).isEqualTo(Font.SERIF);
                assertThat(label.getFont().getSize2D()).isEqualTo(18);
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults());
                assertThat(label.getFont().getFamily()).isEqualTo(original.getFamily());
                assertThat(label.getFont().getSize2D()).isEqualTo(original.getSize2D());
            } finally { subscription.close(); themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH, UiFontConfig.defaults()); }
        });
    }
}
