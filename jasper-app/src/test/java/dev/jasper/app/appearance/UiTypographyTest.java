package dev.jasper.app.appearance;

import dev.jasper.app.config.UiFontConfig;
import java.awt.Font;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UiTypographyTest {
    @Test void sizeOnlySystemSettingsKeepSharedRoleFacesAndExplicitFamiliesRemainExplicit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
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
                    themes.configure(Theme.DARK, config);
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
                themes.configure(Theme.DARK, new UiFontConfig("Helvetica Neue", 20));
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.BODY).getFamily()).isEqualTo(new Font("Helvetica Neue", Font.PLAIN, 20).getFamily());
                themes.configure(Theme.DARK, new UiFontConfig("Serif", 20));
                assertThat(context.typography().font(dev.jasper.ui.metrics.Typography.Role.HERO_TITLE).getFamily()).isEqualTo(Font.SERIF);
            } finally { themes.configure(Theme.DARK, UiFontConfig.defaults()); themes.close(); }
        });
    }

    @Test void configuredPointSizesAreExactAcrossTheWholeSupportedRange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            try {
                for (float size : new float[]{8, 12, 14, 18.5f, 24, 28, 32}) {
                    themes.configure(Theme.DARK, new UiFontConfig("system", size));
                    assertThat(new JLabel().getFont().getSize2D()).as("configured %s", size).isEqualTo(size);
                    assertThat(new JButton().getFont().getSize2D()).isEqualTo(size);
                }
            } finally { themes.configure(Theme.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void missingFamiliesFallBackWhenAThemeAndFontAreConfiguredTogether() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            String platformFamily = UIManager.getFont("Label.font").getFamily();
            try {
                themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
                themes.configure(Theme.DARK, new UiFontConfig("NonexistentJasperFont394", 17));
                assertThat(themes.current().chrome()).as("the theme just configured applies").isEqualTo(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK));
                assertThat(new JLabel().getFont().getFamily()).isEqualTo(platformFamily);
                assertThat(new JLabel().getFont().getSize2D()).isEqualTo(17);
            } finally { themes.configure(Theme.DARK, UiFontConfig.defaults()); }
        });
    }

    @Test void liveFontChangesReachControlsAndRetainStyleAcrossThemeChangesAndReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            var panel = new JPanel();
            var label = new JLabel("Host");
            var button = new JButton("Connect");
            var field = new JTextField("search");
            panel.add(label); panel.add(button); panel.add(field);
            Font original = label.getFont();
            var subscription = themes.subscribe((theme, delegates) -> { if (delegates) SwingUtilities.updateComponentTreeUI(panel); });
            try {
                themes.configure(Theme.DARK, new UiFontConfig("Serif", 18));
                for (var component : new JComponent[]{label, button, field}) {
                    assertThat(component.getFont().getFamily()).isEqualTo(Font.SERIF);
                    assertThat(component.getFont().getSize2D()).isEqualTo(18);
                }
                themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
                assertThat(label.getFont().getFamily()).isEqualTo(Font.SERIF);
                assertThat(label.getFont().getSize2D()).isEqualTo(18);
                themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, UiFontConfig.defaults());
                assertThat(label.getFont().getFamily()).isEqualTo(original.getFamily());
                assertThat(label.getFont().getSize2D()).isEqualTo(original.getSize2D());
            } finally { subscription.close(); themes.configure(Theme.DARK, UiFontConfig.defaults()); }
        });
    }
}
