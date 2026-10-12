package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeController;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class AppIconsTest {
    private static final String[] ICONS = {"square-plus", "app-window", "columns-2", "maximize", "search", "settings",
        "refresh", "command", "history", "bookmark", "close", "exit"};

    @Test void originalSearchAndFileReachNativeOwnersAtDeviceAndUserScales() throws Exception {
        edt(()->{
            var themes=ThemeTestSupport.controller(true);float previous=com.formdev.flatlaf.util.UIScale.getUserScaleFactor();
            try(var registry=new dev.jasper.ui.icons.IconRegistry()) {
                for (boolean themeDark : new boolean[]{true, false}) {
                    themes.configure(themeDark, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                    for(float scale:new float[]{1,2}) {
                        userScale(scale);
                        for(String meaning:java.util.List.of("SEARCH","FILE")) {
                            var icon=AppIcons.named(meaning);var expected=registry.icon(meaning.toLowerCase(java.util.Locale.ROOT),dev.jasper.ui.swing.FlatLafInstaller.installedTheme(),dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY,Math.round(16*scale));
                            for(int device:new int[]{1,2,3})assertThat(pixels(icon,device)).as("%s scale%s device%s",meaning,scale,device).isEqualTo(pixels(expected,device));
                            var disabled=((com.formdev.flatlaf.FlatLaf.DisabledIconProvider)icon).getDisabledIcon();
                            assertThat(pixels(disabled,2)).isNotEqualTo(pixels(icon,2));
                        }
                    }
                }
            }finally {userScale(previous);themes.close();ThemeTestSupport.controller(true);}
        });
    }
    private static void userScale(float scale){try{var setter=com.formdev.flatlaf.util.UIScale.class.getDeclaredMethod("setUserScaleFactor",float.class,boolean.class);setter.setAccessible(true);setter.invoke(null,scale,false);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
    private static int[] pixels(javax.swing.Icon icon,int scale){var image=new java.awt.image.BufferedImage(icon.getIconWidth()*scale,icon.getIconHeight()*scale,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();try{g.scale(scale,scale);icon.paintIcon(null,g,0,0);}finally{g.dispose();}return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());}

    @Test void modernApplicationIconsUseModuleNamedTinting() throws Exception {
        edt(() -> {
            ThemeTestSupport.controller(false);
            try {
                for (String name : ICONS) {
                    var icon = AppIcons.icon(name);
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getIconHeight()).isEqualTo(16);
                    assertThat(Arrays.stream(IntellijIconsTest.pixels(icon)).anyMatch(pixel -> (pixel >>> 24) != 0)).isTrue();
                }
                var expected = new dev.jasper.ui.icons.IconRegistry().icon("search", dev.jasper.ui.swing.FlatLafInstaller.installedTheme(), dev.jasper.ui.theme.SemanticColor.TEXT_PRIMARY, 16);
                assertThat(IntellijIconsTest.pixels(AppIcons.icon("search")))
                    .isEqualTo(IntellijIconsTest.pixels(expected));
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    @Test void theSameHostIconFollowsALiveDarkSwitch() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(false);
            try {
                var icon = AppIcons.icon("search");
                int[] light = IntellijIconsTest.pixels(icon);
                themes.configure(true, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                int[] dark = IntellijIconsTest.pixels(icon);
                assertThat(dark).isNotEqualTo(light);
                themes.configure(false, dev.jasper.app.config.TerminalSchemeChoice.MATCH);
                assertThat(IntellijIconsTest.pixels(icon)).isEqualTo(light);
                assertThat(AppIcons.dropdown().getIconWidth()).isEqualTo(8);
                assertThat(Arrays.stream(dark).anyMatch(pixel -> pixel == 0xff6c707e)).isFalse();
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    /**
     * Folder tabs paint on the independent terminal palette, not the chrome/workspace route. A named (module-tinted)
     * icon placed there is tinted for that surface: the surface's own foreground, held to the 3:1 non-text floor.
     */
    @Test void namedIconsOnAnIndependentlyColouredSurfaceAreTintedForThatSurface() throws Exception {
        edt(() -> {
            var themes = ThemeTestSupport.controller(true);
            try {
                var named = AppIcons.named("TERMINAL");
                int[] chrome = IntellijIconsTest.pixels(named);
                var lightTerminal = new java.awt.Color(0xfafafa);
                var terminalText = new java.awt.Color(0x383a42);
                for (var tinted : java.util.List.of(AppIcons.forSurface(named, lightTerminal, terminalText),
                        AppIcons.forBackground(named, lightTerminal))) {
                    int[] pixels = IntellijIconsTest.pixels(tinted);
                    assertThat(pixels).as("not the dark chrome route's light glyph").isNotEqualTo(chrome);
                    var stroke = stroke(pixels);
                    assertThat(dev.jasper.ui.theme.ColorMath.contrast(stroke, lightTerminal)).isGreaterThanOrEqualTo(3);
                }
                assertThat(stroke(IntellijIconsTest.pixels(AppIcons.forSurface(named, lightTerminal, terminalText))))
                    .as("the surface's own foreground when it already reads").isEqualTo(terminalText);
                assertThat(IntellijIconsTest.pixels(named)).as("the shared icon keeps the chrome route").isEqualTo(chrome);
                var disabled = (javax.swing.Icon) ((com.formdev.flatlaf.FlatLaf.DisabledIconProvider)
                    AppIcons.forSurface(named, lightTerminal, terminalText)).getDisabledIcon();
                assertThat(disabled.getIconWidth()).isEqualTo(16);
            } finally { themes.close(); ThemeTestSupport.controller(true); }
        });
    }

    private static java.awt.Color stroke(int[] pixels) {
        int best = 0;
        for (int pixel : pixels) if ((pixel >>> 24) > (best >>> 24)) best = pixel;
        assertThat(best >>> 24).as("the glyph paints").isPositive();
        return new java.awt.Color(best & 0xffffff);
    }

    @Test void bundledIconsKeepFlatLafDisabledRendering() throws Exception {
        edt(() -> {
            ThemeTestSupport.controller(false);
            try {
                var icon = AppIcons.icon("search");
                var disabled = javax.swing.UIManager.getLookAndFeel().getDisabledIcon(new javax.swing.JLabel(), icon);
                assertThat(disabled).isNotNull();
                assertThat(disabled.getIconWidth()).isEqualTo(16);
                assertThat(IntellijIconsTest.pixels(disabled)).isNotEqualTo(IntellijIconsTest.pixels(icon));
            } finally { ThemeTestSupport.controller(true); }
        });
    }

    @Test void chromeResolvesIntellijNamesAndRejectsUnknownOnes() throws Exception {
        edt(() -> {
            assertThat(AppIcons.named("CLOSE_HOVERED").getIconWidth()).isEqualTo(16);
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.named("absent")).withMessageContaining("absent");
        });
    }

    @Test void everyApplicationIconIsSixteenPixelIntellijArtworkAndUnknownNamesFail() throws Exception {
        edt(() -> {
            for (String name : new String[]{"square-plus", "app-window", "columns-2", "maximize", "search", "settings",
                    "refresh", "command", "history", "bookmark", "close", "exit"}) {
                var icon = AppIcons.icon(name);
                assertThat(icon).as(name).isInstanceOf(com.formdev.flatlaf.FlatLaf.DisabledIconProvider.class);
                assertThat(icon.getIconWidth()).as(name).isEqualTo(16);
            }
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.icon("unknown")).withMessageContaining("unknown");
            assertThat(java.util.Arrays.stream(AppIcons.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("toolbarIcon", "forToolbar", "bundled", "bundledIds", "plugin");
        });
    }
}
