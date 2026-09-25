package dev.jasper.app.platform;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class AppIconsPluginTest {
    @Test void pluginSvgRendersAsAuthoredWithPaletteRemappingAndRejectsBadArguments() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        try {
            var loader = getClass().getClassLoader();
            var icon = AppIcons.plugin(loader, "dev/jasper/app/icons/palette-probe.svg");
            assertThat(icon.getIconWidth()).isEqualTo(16);
            assertThat(IntellijIconsTest.centre(icon)).as("not tinted to the chrome foreground").isEqualTo(0xff389fd6);
            themes.selectAppearance(Appearance.DARK);
            assertThat(IntellijIconsTest.centre(icon)).isEqualTo(UIManager.getColor("Actions.Blue").getRGB());
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.plugin(loader, "dev/jasper/app/icons/absent.svg"))
                .withMessageContaining("absent.svg");
            assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.plugin(loader, null));
            assertThatNullPointerException().isThrownBy(() -> AppIcons.plugin(null, "x.svg"));
        } finally { new ThemeController(); }
    }
}
