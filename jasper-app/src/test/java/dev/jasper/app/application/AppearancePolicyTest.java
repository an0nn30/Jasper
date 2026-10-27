package dev.jasper.app.application;

import dev.jasper.app.appearance.AppearancePolicy;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.platform.SystemAppearance.Observation;
import org.junit.jupiter.api.Test;

class AppearancePolicyTest {
    @Test void anExplicitAppearanceWinsAndSystemFollowsTheOperatingSystemOrDark() {
        assertThat(AppearancePolicy.dark(AppearanceMode.DARK, Observation.light())).isTrue();
        assertThat(AppearancePolicy.dark(AppearanceMode.LIGHT, Observation.dark())).isFalse();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.light())).isFalse();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.dark())).isTrue();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.unavailable("headless"))).as("unavailable: Dark").isTrue();
    }
}
