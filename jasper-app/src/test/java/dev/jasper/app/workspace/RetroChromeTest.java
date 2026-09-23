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
        var starting = (javax.swing.JLabel) owner.currentPane().getComponent(0);
        assertThat(starting.getForeground()).isEqualTo(owner.theme().palette().foreground());
        var toolbar = owner.toolbar();
        for (var child : toolbar.getComponents()) if (child instanceof javax.swing.JButton button) {
            assertThat(button.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalButtonUI.class);
            assertThat(button.getBorder()).isNotNull();
            assertThat(button.isContentAreaFilled()).isFalse();
            assertThat(button.isBorderPainted()).isFalse();
            assertThat(button.getIcon().getIconWidth()).isEqualTo(28);
            assertThat(button.getHorizontalTextPosition()).isEqualTo(javax.swing.SwingConstants.CENTER);
            assertThat(button.getVerticalTextPosition()).isEqualTo(javax.swing.SwingConstants.BOTTOM);
            assertThat(button.getPreferredSize().height).isGreaterThan(48).isLessThanOrEqualTo(64);
            assertThat(button.getFont().getStyle()).isEqualTo(java.awt.Font.PLAIN);
            assertThat(button.getFont().getSize2D()).isEqualTo(javax.swing.UIManager.getFont("Button.font").getSize2D() - 1f);
            assertThat(button.getInsets()).isEqualTo(new java.awt.Insets(3, 5, 3, 5));
        }
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.ICONS);
        assertThat(((javax.swing.JButton) toolbar.getComponent(0)).getText()).isNull();
        assertThat(((javax.swing.JButton) toolbar.getComponent(0)).getPreferredSize().height).isLessThanOrEqualTo(40);
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

    @Test void retroTrailingControlsUseSettingsAndQuitActions() throws Exception {
        edt(() -> {
            for (var style : ThemeStyle.values()) {
                var settingsCalls = new java.util.concurrent.atomic.AtomicInteger();
                var quitCalls = new java.util.concurrent.atomic.AtomicInteger();
                try (var owner = new WindowContent(launcher(new java.util.ArrayDeque<>()), HOME,
                        path -> {}, quitCalls::incrementAndGet, () -> {}, new ThemeController(style, Appearance.LIGHT))) {
                    var toolbar = owner.toolbar();
                    var buttons = java.util.Arrays.stream(toolbar.getComponents())
                        .filter(javax.swing.JButton.class::isInstance).map(javax.swing.JButton.class::cast).toList();
                    if (style != ThemeStyle.RETRO) {
                        assertThat(buttons).noneMatch(button -> button.getAction() == owner.action(dev.jasper.app.commands.ActionId.QUIT)
                            || button.getAction() == owner.action(dev.jasper.app.commands.ActionId.OPEN_SETTINGS));
                        continue;
                    }
                    var settings = buttons.get(buttons.size() - 2);
                    var exit = buttons.getLast();
                    assertThat(settings.getText()).isEqualTo("Settings");
                    assertThat(exit.getText()).isEqualTo("Exit");
                    assertThat(settings.getAction()).isSameAs(owner.action(dev.jasper.app.commands.ActionId.OPEN_SETTINGS));
                    assertThat(exit.getAction()).isSameAs(owner.action(dev.jasper.app.commands.ActionId.QUIT));
                    assertThat(settings.isEnabled()).isFalse();
                    owner.connectConfiguration(settingsCalls::incrementAndGet, () -> {}, () -> {});
                    settings.doClick(); exit.doClick();
                    assertThat(settingsCalls.get()).isEqualTo(1);
                    assertThat(quitCalls.get()).isEqualTo(1);
                    toolbar.setSize(960, toolbar.getPreferredSize().height); toolbar.doLayout();
                    assertThat(settings.getX()).isGreaterThan(800);
                    assertThat(exit.getX() + exit.getWidth()).isEqualTo(toolbar.getWidth() - toolbar.getInsets().right);
                    owner.disconnectConfiguration();
                    assertThat(settings.isEnabled()).isFalse();
                }
            }
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
            toolbar.setSize(960, 64); toolbar.doLayout();
            for (var child : toolbar.getComponents()) if (child instanceof javax.swing.JButton button) {
                assertThat(button.getText()).isEqualTo(button.getClientProperty("label"));
                assertThat(button.getHeight()).isGreaterThanOrEqualTo(button.getPreferredSize().height);
            }
        });
    }

    @Test void renderWorkspaceAndStandardFormsAtBothScales() throws Exception {
        edt(() -> {
            for (var choice : java.util.List.of(java.util.Map.entry(ThemeStyle.MODERN, Appearance.DARK),
                    java.util.Map.entry(ThemeStyle.MODERN, Appearance.LIGHT), java.util.Map.entry(ThemeStyle.RETRO, Appearance.LIGHT))) {
                try (var owner = content(launcher(new java.util.ArrayDeque<>()), new ThemeController(choice.getKey(), choice.getValue()))) {
                    owner.currentTab().rename("<html>A long literal terminal title & development"); owner.update();
                    owner.newTab(HOME);
                    var model = new dev.jasper.app.contributions.Contributions();
                    owner.connectContributions(model);
                    model.addStatus("dev.preview.vault", false, 0).setText("Vault");
                    var preview = new javax.swing.JPanel(new java.awt.BorderLayout());
                    preview.add(owner);
                    var form = new javax.swing.JPanel(new java.awt.FlowLayout());
                    form.add(new javax.swing.JLabel("Profile")); form.add(new javax.swing.JTextField("Retro terminal", 12));
                    form.add(new javax.swing.JPasswordField("password", 10));
                    form.add(new javax.swing.JComboBox<>(new String[]{"Local", "Remote"}));
                    var save = new javax.swing.JButton("Save"); form.add(save);
                    var disabled = new javax.swing.JButton("Unavailable"); disabled.setEnabled(false); form.add(disabled);
                    var root = new javax.swing.JRootPane(); root.setContentPane(form); root.setDefaultButton(save);
                    preview.add(root, java.awt.BorderLayout.SOUTH);
                    for (int scale : new int[]{1, 2}) saveRender(preview, choice.getKey()+"-"+choice.getValue()+"-workspace-"+scale, scale);
                }
            }
        });
    }
private static void layoutTree(java.awt.Container container) {
    container.doLayout();
    for (var child : container.getComponents())
        if (child instanceof java.awt.Container nested) layoutTree(nested);
}
private static void saveRender(javax.swing.JComponent component, String name, int scale) {
    component.setSize(960, 640);
    layoutTree(component);
    var image = new java.awt.image.BufferedImage(960 * scale, 640 * scale,
        java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try { graphics.scale(scale, scale); component.printAll(graphics); }
    finally { graphics.dispose(); }
    try {
        var folder = java.nio.file.Path.of("build/retro-preview");
        java.nio.file.Files.createDirectories(folder);
        javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
}

}
