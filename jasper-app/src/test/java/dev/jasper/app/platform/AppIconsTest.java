package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
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
            new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
            try {
                for (String name : ICONS) {
                    FlatSVGIcon icon = (FlatSVGIcon) AppIcons.icon(name);
                    assertThat(icon.hasFound()).as(name).isTrue();
                    assertThat(icon.getName()).as(name).startsWith("dev/jasper/app/icons/intellij/");
                    assertThat(icon.getIconWidth()).isEqualTo(16);
                    assertThat(icon.getColorFilter()).as(name + " keeps its authored colours").isNull();
                }
                assertThat(Arrays.stream(IntellijIconsTest.pixels(AppIcons.icon("search")))
                    .anyMatch(pixel -> pixel == 0xff6e6e6e)).as("IntelliJ light grey").isTrue();
            } finally { new ThemeController(); }
        });
    }

    @Test void theSameHostIconFollowsALiveDarkSwitch() throws Exception {
        edt(() -> {
            var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
            try {
                var icon = AppIcons.icon("search");
                int[] light = IntellijIconsTest.pixels(icon);
                themes.selectAppearance(Appearance.DARK);
                int[] dark = IntellijIconsTest.pixels(icon);
                assertThat(dark).isNotEqualTo(light);
                assertThat(Arrays.stream(dark).anyMatch(pixel -> pixel == 0xff6e6e6e)).isFalse();
            } finally { new ThemeController(); }
        });
    }

    @Test void chromeResolvesIntellijNamesAndRejectsUnknownOnes() throws Exception {
        edt(() -> {
            assertThat(AppIcons.chrome("closeHovered").getIconWidth()).isEqualTo(16);
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.chrome("absent")).withMessageContaining("absent");
        });
    }
}
