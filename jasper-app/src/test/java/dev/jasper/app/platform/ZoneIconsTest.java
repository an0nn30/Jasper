package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ZoneIconsTest {
    @Test void retainedArtworkFollowsReparentingBoundariesAndMatch() {
        var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
        try {
            var label = new JLabel();
            var root = new JPanel();
            root.putClientProperty("Jasper.zone", "workspace");
            root.putClientProperty("Jasper.workspace.dark", true);
            root.putClientProperty("Jasper.workspaceTheme", dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK));
            var icon = AppIcons.semantic("search");
            var light = fixed(false);
            var dark = fixed(true);
            assertThat(pixels(icon, label)).isEqualTo(pixels(light, label));
            root.add(label);
            assertThat(ThemeZone.workspace(label)).isTrue();
            assertThat(ThemeZone.dark(label, false)).isTrue();
            assertThat(pixels(icon, label)).isEqualTo(pixels(dark, label));
            var disabled = UIManager.getLookAndFeel().getDisabledIcon(label, icon);
            assertThat(pixels(disabled, label)).isEqualTo(pixels(((com.formdev.flatlaf.FlatLaf.DisabledIconProvider) dark).getDisabledIcon(), label));
            assertThat(pixels(disabled, label)).isNotEqualTo(pixels(icon, label));
            assertThat(icon.getIconWidth()).isEqualTo(16);
            assertThat(disabled.getIconHeight()).isEqualTo(16);
            assertThat(AppIcons.dropdown().getIconWidth()).isEqualTo(8);
            for (JComponent boundary : new JComponent[]{new JPopupMenu(), new JToolTip(), new JRootPane(), new JPanel()}) {
                if (boundary.getClass() == JPanel.class) boundary.putClientProperty("Jasper.zone", "chrome");
                root.add(boundary);
                boundary.add(label);
                assertThat(ThemeZone.workspace(label)).isFalse();
                assertThat(pixels(icon, label)).as(boundary.getClass().getSimpleName()).isEqualTo(pixels(light, label));
            }
            root.add(label);
            root.putClientProperty("Jasper.workspace.dark", null);
            root.putClientProperty("Jasper.workspaceTheme", null);
            assertThat(pixels(disabled, label)).isEqualTo(pixels(((com.formdev.flatlaf.FlatLaf.DisabledIconProvider) light).getDisabledIcon(), label));
            assertThat(pixels(icon, label)).isEqualTo(pixels(light, label));
            themes.configure(Theme.DARK);
            assertThat(pixels(icon, label)).isEqualTo(pixels(dark, label));
            root.putClientProperty("Jasper.workspace.dark", false);
            root.putClientProperty("Jasper.workspaceTheme", dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT));
            assertThat(pixels(icon, label)).isEqualTo(pixels(light, label));
            root.remove(label);
            assertThat(pixels(icon, label)).isEqualTo(pixels(dark, label));
        } finally { new ThemeController(); }
    }

    @Test void keyUsesZoneTextWhilePluginArtworkKeepsItsAuthoredColor() {
        new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
        try {
            var label = new JLabel();
            var root = new JPanel();
            var themes = new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
            themes.configure(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT, new dev.jasper.app.config.WorkspaceThemeChoice("Jasper Dark"),
                Theme.DARK, dev.jasper.app.config.UiFontConfig.defaults());
            dev.jasper.app.workspace.WorkspaceTheme.apply(root, themes.current());
            var key = AppIcons.named("KEY");
            var plugin = new FlatSVGIcon("dev/jasper/app/icons/palette-probe.svg", 16, 16, getClass().getClassLoader());
            var original = pixels(plugin, label);
            var chromeKey = pixels(key, label);
            root.add(label);
            assertThat(pixels(key, label)).contains(themes.current().workspace().tokens()
                .get(dev.jasper.ui.theme.Token.TEXT).getRGB());
            assertThat(pixels(plugin, label)).isEqualTo(original);
            assertThat(AppIcons.forBackground(plugin, Color.BLACK)).isSameAs(plugin);
            themes.configureTerminal(dev.jasper.app.config.TerminalColors.MATCH);
            dev.jasper.app.workspace.WorkspaceTheme.apply(root, themes.current());
            assertThat(pixels(key, label)).isEqualTo(chromeKey);
            assertThat(root.getClientProperty("Jasper.workspace.foreground")).isNull();
            dev.jasper.app.workspace.WorkspaceTheme.clear(root);
            assertThat(ThemeZone.workspace(label)).isFalse();
            assertThat(root.getClientProperty("Jasper.workspace.foreground")).isNull();
        } finally { new ThemeController(); }
    }

    private static Icon fixed(boolean dark) {
        var theme = dev.jasper.app.appearance.ThemeManager.generated(dark ? Theme.DARK : dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
        return AppIcons.forSurface(AppIcons.semantic("search"), theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL),
            theme.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
    }

    private static int[] pixels(Icon icon, Component component) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(component, graphics, 0, 0); } finally { graphics.dispose(); }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
