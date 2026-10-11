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
    @Test void authoredNativeIconsPrepareAtFirstConstructionAndActualScaleChangeBeforePaint() throws Exception {
        var themes=new ThemeController(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT);
        float scale=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
        var setter=com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);setter.setAccessible(true);
        try {
            var icon=AppIcons.semantic("settings");var presentation=new PresentationIcon("settings",null);
            var disabled=((com.formdev.flatlaf.FlatLaf.DisabledIconProvider)icon).getDisabledIcon();
            var label=new JLabel();var workspace=new JPanel();workspace.putClientProperty("Jasper.zone","workspace");workspace.add(label);
            try(var registry=new dev.jasper.ui.icons.IconRegistry()) {
                for(float next:new float[]{1f,1.5f,2.5f,1f}) {
                    setter.invoke(null,next,false);
                    var catalogClass=Class.forName("dev.jasper.ui.icons.BuiltInArtwork");var catalogMethod=catalogClass.getDeclaredMethod("catalog");catalogMethod.setAccessible(true);
                    var pair=((java.util.Map<?,?>)catalogMethod.invoke(null)).get("settings");
                    for(String sibling:java.util.List.of("light","dark")) {
                        var accessor=pair.getClass().getDeclaredMethod(sibling);accessor.setAccessible(true);var decoded=accessor.invoke(pair);
                        var snapshot=decoded.getClass().getDeclaredMethod("snapshot",int.class);snapshot.setAccessible(true);
                        for(int extent=60;extent<72;extent++)snapshot.invoke(decoded,extent);
                        var sizes=decoded.getClass().getDeclaredField("snapshots");sizes.setAccessible(true);
                        var cache=(java.util.Map<?,?>)sizes.get(decoded);var before=new java.util.LinkedHashMap<>(cache);
                        var canvas=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var paint=canvas.createGraphics();
                        try{icon.paintIcon(label,paint,0,0);presentation.paintIcon(label,paint,0,0);}finally{paint.dispose();}
                        assertThat(cache).as("native paint after unrelated size churn").isEqualTo(before);
                    }
                    // Paint directly, before a layout/dimension query could prepare missing rasters.
                    var image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
                    try{icon.paintIcon(label,graphics,0,0);}finally{graphics.dispose();}
                    assertThat(icon.getIconWidth()).isEqualTo(Math.round(16*next));
                    for(var theme:java.util.List.of(dev.jasper.app.appearance.ThemeManager.generated(Theme.DARK),dev.jasper.app.appearance.ThemeManager.generated(dev.jasper.app.appearance.ThemeFixtures.INTELLIJ_LIGHT))) {
                        workspace.putClientProperty("Jasper.workspaceTheme",theme);
                        var expected=registry.icon("settings",theme,dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,icon.getIconWidth());
                        assertThat(pixels(icon,label)).isEqualTo(pixels(expected,label));
                        assertThat(pixels(presentation,label)).isEqualTo(pixels(expected,label));
                        int[] enabled=pixels(icon,label),dimmed=pixels(disabled,label);
                        for(int i=0;i<enabled.length;i++)assertThat(dimmed[i]>>>24).isCloseTo(Math.round((enabled[i]>>>24)*.42f),within(1));
                    }
                }
            }
        } finally {setter.invoke(null,scale,false);themes.close();new ThemeController();}
    }
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
