package dev.jasper.app.config;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GlassSettingsTest {
    @Test void omittedKeysDeferToTheTheme() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "", true);
        assertThat(result.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(GlassSettings.UNSET.opacity()).isEmpty();
        assertThat(GlassSettings.UNSET.blur()).isEmpty();
    }

    @Test void savedPercentagesBecomeFractions() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 80\nblur = 0\n", true);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().glass()).isEqualTo(new GlassSettings(80f, 0f));
        assertThat(result.snapshot().glass().opacity()).isEqualTo(Optional.of(0.8));
        assertThat(result.snapshot().glass().blur()).isEqualTo(Optional.of(0.0));
    }

    @Test void outOfRangeValuesAreReportedAndDefer() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 5\nblur = 101\n", true);
        assertThat(result.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(result.diagnostics()).hasSize(2);
    }

    @Test void theRecordRejectsValuesTheLoaderWouldNot() {
        assertThatThrownBy(() -> new GlassSettings(5f, GlassSettings.UNSET_BLUR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(GlassSettings.UNSET_OPACITY, 101f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(Float.NaN, 0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theBuilderCarriesGlass() {
        var snapshot = ConfigSnapshot.defaults().toBuilder().glass(new GlassSettings(90f, 10f)).build();
        assertThat(snapshot.glass()).isEqualTo(new GlassSettings(90f, 10f));
        assertThat(snapshot.toBuilder().build().glass()).isEqualTo(new GlassSettings(90f, 10f));
    }
}
