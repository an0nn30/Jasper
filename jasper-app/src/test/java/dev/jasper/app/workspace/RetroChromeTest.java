package dev.jasper.app.workspace;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.*;
import org.junit.jupiter.api.*;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;
class RetroChromeTest {
 @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }
@Test void retroButtonsKeepMetalDelegatesAndToolbarModes() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.DARK));
        var toolbar = owner.toolbar();
        for (var child : toolbar.getComponents()) if (child instanceof javax.swing.JButton button) {
            assertThat(button.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalButtonUI.class);
            assertThat(button.getBorder()).isNotNull();
            assertThat(button.isContentAreaFilled()).isFalse();
            assertThat(button.isBorderPainted()).isFalse();
            assertThat(button.getIcon().getIconWidth()).isEqualTo(24);
            assertThat(button.getPreferredSize().height).isLessThanOrEqualTo(28);
            assertThat(button.getFont()).isEqualTo(javax.swing.UIManager.getFont("Button.font"));
        }
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.ICONS);
        assertThat(((javax.swing.JButton) toolbar.getComponent(0)).getText()).isNull();
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.HIDDEN);
        assertThat(toolbar.isVisible()).isFalse();
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.ICONS_AND_LABELS);
        assertThat(toolbar.isVisible()).isTrue();
        ((javax.swing.JButton) toolbar.getComponent(0)).doClick();
        assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
        assertThat(owner.status().configButton().isContentAreaFilled()).isFalse();
        assertThat(owner.status().configButton().getBorder().getBorderInsets(owner.status().configButton())).isEqualTo(new java.awt.Insets(0, 0, 0, 0));
        assertThat(owner.status().getBackground()).isEqualTo(javax.swing.UIManager.getColor("Panel.background"));
        assertThat(owner.windowCommands().view("view.appearance.dark").isEnabled()).isFalse();
        assertThat(owner.windowCommands().view("view.appearance.light").isEnabled()).isFalse();
        assertThat(owner.windowCommands().view("view.tab_height").isEnabled()).isFalse();
    });
}

    @Test void minimumToolbarWidthKeepsEveryIconAndBorderReachable() throws Exception {
        edt(() -> {
            var owner = content(launcher(new java.util.ArrayDeque<>()), new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
            var toolbar = owner.toolbar();
            toolbar.setSize(toolbar.getMinimumSize().width, toolbar.getPreferredSize().height);
            toolbar.doLayout();
            for (var child : toolbar.getComponents()) if (child instanceof javax.swing.JButton button) {
                var insets = button.getInsets();
                assertThat(button.getWidth()).isGreaterThanOrEqualTo(button.getIcon().getIconWidth() + insets.left + insets.right);
                assertThat(button.getX() + button.getWidth()).isLessThanOrEqualTo(toolbar.getWidth());
                assertThat(button.getHeight()).isGreaterThanOrEqualTo(button.getPreferredSize().height);
            }
        });
    }
}
