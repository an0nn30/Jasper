package dev.jasper.app.platform;

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

    @Test void modernApplicationIconsAreUntintedIntellijArtwork() throws Exception {
        edt(() -> {
            new ThemeController(Theme.INTELLIJ_LIGHT);
            try {
                for (String name : ICONS) {
                    var icon = AppIcons.icon(name);
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getIconHeight()).isEqualTo(16);
                    assertThat(Arrays.stream(IntellijIconsTest.pixels(icon)).anyMatch(pixel -> (pixel >>> 24) != 0)).isTrue();
                }
                var expected = new FlatSVGIcon("dev/jasper/app/icons/intellij/general/search.svg", 16, 16);
                assertThat(IntellijIconsTest.pixels(AppIcons.icon("search")))
                    .isEqualTo(IntellijIconsTest.pixels(expected));
            } finally { new ThemeController(); }
        });
    }

    @Test void theSameHostIconFollowsALiveDarkSwitch() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            try {
                var icon = AppIcons.icon("search");
                int[] light = IntellijIconsTest.pixels(icon);
                themes.configure(Theme.DARK);
                int[] dark = IntellijIconsTest.pixels(icon);
                assertThat(dark).isNotEqualTo(light);
                themes.configure(Theme.INTELLIJ_LIGHT);
                assertThat(IntellijIconsTest.pixels(icon)).isEqualTo(light);
                assertThat(AppIcons.dropdown().getIconWidth()).isEqualTo(8);
                assertThat(Arrays.stream(dark).anyMatch(pixel -> pixel == 0xff6c707e)).isFalse();
            } finally { new ThemeController(); }
        });
    }

    @Test void selectedSurfacesChooseUntintedVariantsWithoutChangingSharedIcons() throws Exception {
        edt(() -> {
            var themes = new ThemeController(Theme.INTELLIJ_LIGHT);
            try {
                var original = AppIcons.named("TERMINAL");
                int[] light = IntellijIconsTest.pixels(original);
                themes.configure(Theme.DARK);
                int[] dark = IntellijIconsTest.pixels(original);
                assertThat(dark).isNotEqualTo(light);
                assertThat(IntellijIconsTest.pixels(AppIcons.forBackground(original, java.awt.Color.WHITE))).isEqualTo(light);
                assertThat(IntellijIconsTest.pixels(original)).isEqualTo(dark);
                themes.configure(Theme.INTELLIJ_LIGHT);
                assertThat(IntellijIconsTest.pixels(AppIcons.forBackground(original, java.awt.Color.BLACK))).isEqualTo(dark);
                assertThat(IntellijIconsTest.pixels(original)).isEqualTo(light);
                var custom = new javax.swing.ImageIcon(new java.awt.image.BufferedImage(16, 16, 2));
                assertThat(AppIcons.forBackground(custom, java.awt.Color.BLACK)).isSameAs(custom);
            } finally { new ThemeController(); }
        });
    }

    @Test void bundledIconsKeepFlatLafDisabledRendering() throws Exception {
        edt(() -> {
            new ThemeController(Theme.INTELLIJ_LIGHT);
            try {
                var icon = AppIcons.icon("search");
                var disabled = javax.swing.UIManager.getLookAndFeel().getDisabledIcon(new javax.swing.JLabel(), icon);
                assertThat(disabled).isNotNull();
                assertThat(disabled.getIconWidth()).isEqualTo(16);
                assertThat(IntellijIconsTest.pixels(disabled)).isNotEqualTo(IntellijIconsTest.pixels(icon));
            } finally { new ThemeController(); }
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
                .doesNotContain("toolbarIcon", "forToolbar");
        });
    }
}
