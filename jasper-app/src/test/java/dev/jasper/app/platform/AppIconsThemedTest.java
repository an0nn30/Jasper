package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AppIconsThemedTest {
    @Test void loadsAnSvgFromAGivenLoaderAtChromeSizeAndRejectsMissingResources() {
        var icon = AppIcons.themed(AppIcons.class.getClassLoader(), "dev/jasper/app/icons/search.svg");
        assertThat(icon.getIconWidth()).isEqualTo(icon.getIconHeight()).isPositive();
        assertThatIllegalArgumentException().isThrownBy(() ->
            AppIcons.themed(AppIcons.class.getClassLoader(), "dev/jasper/app/icons/absent.svg")).withMessageContaining("absent.svg");
    }
}
