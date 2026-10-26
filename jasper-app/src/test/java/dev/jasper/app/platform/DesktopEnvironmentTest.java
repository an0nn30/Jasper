package dev.jasper.app.platform;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DesktopEnvironmentTest {
    @Test void capturesOnlySupportedHintsWithoutNormalizingTheirValues() {
        var values = new HashMap<>(Map.of("XDG_CURRENT_DESKTOP", " vendor : KDE ", "KDE_SESSION_VERSION", " 5 ",
            "KDE_FULL_SESSION", " TRUE ", "QT_QPA_PLATFORMTHEME", "qt5ct:parameters", "QT_STYLE_OVERRIDE", "breeze"));
        var captured = DesktopEnvironment.from(values);
        values.put("KDE_SESSION_VERSION", "6");
        assertThat(captured).isEqualTo(new DesktopEnvironment(" vendor : KDE ", " 5 ", " TRUE ", "qt5ct:parameters"));
        assertThat(DesktopEnvironment.from(Map.of())).isEqualTo(new DesktopEnvironment("", "", "", ""));
    }
    @Test void rejectsNullMapsAndHintValues() {
        assertThatNullPointerException().isThrownBy(() -> DesktopEnvironment.from(null));
        assertThatNullPointerException().isThrownBy(() -> new DesktopEnvironment(null, "", "", ""));
        assertThatNullPointerException().isThrownBy(() -> new DesktopEnvironment("", null, "", ""));
        assertThatNullPointerException().isThrownBy(() -> new DesktopEnvironment("", "", null, ""));
        assertThatNullPointerException().isThrownBy(() -> new DesktopEnvironment("", "", "", null));
    }
    @Test void captureUsesTheProcessEnvironment() {
        assertThat(DesktopEnvironment.capture()).isEqualTo(DesktopEnvironment.from(System.getenv()));
    }
}
