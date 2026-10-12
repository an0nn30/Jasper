package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeTestSupport;
import com.formdev.flatlaf.extras.FlatSVGIcon;
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
        var themes=ThemeTestSupport.controller(false);
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
                    for(var theme:java.util.List.of(dev.jasper.app.appearance.ThemeTestSupport.generated(true),dev.jasper.app.appearance.ThemeTestSupport.generated(false))) {
                        workspace.putClientProperty("Jasper.workspaceTheme",theme);
                        var expected=registry.icon("settings",theme,dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,icon.getIconWidth());
                        assertThat(pixels(icon,label)).isEqualTo(pixels(expected,label));
                        assertThat(pixels(presentation,label)).isEqualTo(pixels(expected,label));
                        int[] enabled=pixels(icon,label),dimmed=pixels(disabled,label);
                        for(int i=0;i<enabled.length;i++)assertThat(dimmed[i]>>>24).isCloseTo(Math.round((enabled[i]>>>24)*.42f),within(1));
                    }
                }
            }
        } finally {setter.invoke(null,scale,false);themes.close();ThemeTestSupport.controller(true);}
    }
    @Test void retainedArtworkFollowsReparentingBoundariesAndMatch() {
        var themes = ThemeTestSupport.controller(false);
        try {
            var label = new JLabel();
            var root = new JPanel();
            root.putClientProperty("Jasper.zone", "workspace");
            root.putClientProperty("Jasper.workspace.dark", true);
            root.putClientProperty("Jasper.workspaceTheme", dev.jasper.app.appearance.ThemeTestSupport.generated(true));
            // A tinted path glyph; original-art meanings (settings, search) select a theme sibling instead.
            var icon = AppIcons.semantic("home");
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
            themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            assertThat(pixels(icon, label)).isEqualTo(pixels(dark, label));
            root.putClientProperty("Jasper.workspace.dark", false);
            root.putClientProperty("Jasper.workspaceTheme", dev.jasper.app.appearance.ThemeTestSupport.generated(false));
            assertThat(pixels(icon, label)).isEqualTo(pixels(light, label));
            root.remove(label);
            assertThat(pixels(icon, label)).isEqualTo(pixels(dark, label));
        } finally { ThemeTestSupport.controller(true); }
    }

    @Test void keyUsesZoneTextWhilePluginArtworkKeepsItsAuthoredColor() {
        ThemeTestSupport.controller(false);
        try {
            var label = new JLabel();
            var root = new JPanel();
            var themes = ThemeTestSupport.controller(false);
            var split = ThemeTestSupport.mixed(ThemeTestSupport.generated(false), ThemeTestSupport.generated(true));
            dev.jasper.app.workspace.WorkspaceTheme.apply(root, split);
            var key = AppIcons.named("KEY");
            var plugin = new FlatSVGIcon("dev/jasper/app/icons/palette-probe.svg", 16, 16, getClass().getClassLoader());
            var original = pixels(plugin, label);
            var chromeKey = pixels(key, label);
            root.add(label);
            assertThat(pixels(key, label)).contains(split.workspace().semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY).getRGB());
            assertThat(pixels(plugin, label)).isEqualTo(original);
            assertThat(AppIcons.forBackground(plugin, Color.BLACK)).isSameAs(plugin);
            dev.jasper.app.workspace.WorkspaceTheme.apply(root, themes.current());
            assertThat(pixels(key, label)).isEqualTo(chromeKey);
            assertThat(root.getClientProperty("Jasper.workspace.foreground")).isNull();
            dev.jasper.app.workspace.WorkspaceTheme.clear(root);
            assertThat(ThemeZone.workspace(label)).isFalse();
            assertThat(root.getClientProperty("Jasper.workspace.foreground")).isNull();
        } finally { ThemeTestSupport.controller(true); }
    }

    private static Icon fixed(boolean dark) {
        var theme = ThemeTestSupport.generated(dark);
        return AppIcons.forSurface(AppIcons.semantic("home"), theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL),
            theme.semantic().color(dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY));
    }

    @Test void chromeReceiversAloneShowOriginalArtworkWithoutChangingIconValues() {
        var themes = ThemeTestSupport.controller(true);
        try (var registry = new dev.jasper.ui.icons.IconRegistry()) {
            var theme = dev.jasper.app.appearance.ThemeTestSupport.generated(true);
            var chrome = new JPanel(); AppIcons.chromeReceiver(chrome);
            var chromeGroup = new JPanel(); chrome.add(chromeGroup);
            var inside = new JLabel(); chromeGroup.add(inside);
            var plainGroup = new JPanel();
            var outside = new JLabel(); plainGroup.add(outside);
            for (var group : java.util.List.of(chromeGroup, plainGroup)) {
                group.putClientProperty("Jasper.zone", "workspace");
                group.putClientProperty("Jasper.workspaceTheme", theme);
            }
            for (var icon : java.util.List.<Icon>of(AppIcons.icon("square-plus"), AppIcons.named("FOLDER"), AppIcons.semantic("bell"),
                    new PresentationIcon("server", null), AppIcons.dropdown())) {
                int size = icon.getIconWidth();
                String name = icon instanceof PresentationIcon p ? p.name() : AppIcons.kitName(icon).orElseThrow();
                var tint = icon instanceof PresentationIcon ? dev.jasper.ui.theme.SemanticColor.TEXT_SECONDARY : dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY;
                assertThat(pixels(icon, inside)).as("chrome %s", name)
                    .isEqualTo(pixels(registry.chromeIcon(name, size).icon(theme, tint), inside))
                    .isNotEqualTo(pixels(icon, outside));
                assertThat(pixels(icon, outside)).as("ordinary %s", name)
                    .isEqualTo(pixels(registry.themedIcon(name, size).icon(theme, tint), outside));
            }
            // Meanings the chrome does not depict paint identically inside and outside a receiver.
            var settings = AppIcons.semantic("settings");
            assertThat(pixels(settings, inside)).isEqualTo(pixels(settings, outside));
        } finally { themes.close(); ThemeTestSupport.controller(true); }
    }

    private static int[] pixels(Icon icon, Component component) {
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(component, graphics, 0, 0); } finally { graphics.dispose(); }
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
