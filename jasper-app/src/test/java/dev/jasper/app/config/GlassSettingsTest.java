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
        assertThat(GlassSettings.UNSET.blurChoice()).isEqualTo(WorkspaceBlur.THEME);
    }

    @Test void savedOpacityBecomesAFractionAndBlurAChoice() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 80\nblur = \"off\"\n", true);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().glass()).isEqualTo(new GlassSettings(80f, WorkspaceBlur.OFF));
        assertThat(result.snapshot().glass().opacity()).isEqualTo(Optional.of(0.8));
        assertThat(result.snapshot().glass().blur()).isEqualTo(Optional.of(false));
    }

    @Test void eachBlurChoiceMapsOntoTheTheme() {
        for (var entry : java.util.Map.of("theme", Optional.<Boolean>empty(), "on", Optional.of(true), "off", Optional.of(false)).entrySet()) {
            var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nblur = \"" + entry.getKey() + "\"\n", true);
            assertThat(result.diagnostics()).as(entry.getKey()).isEmpty();
            assertThat(result.snapshot().glass().blur()).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test void invalidValuesAreReportedAndDefer() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 5\nblur = 50\n", true);
        assertThat(result.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(result.diagnostics()).hasSize(2);
        var unknown = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nblur = \"strong\"\n", true);
        assertThat(unknown.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(unknown.diagnostics()).hasSize(1);
    }

    @Test void theRecordRejectsValuesTheLoaderWouldNot() {
        assertThatThrownBy(() -> new GlassSettings(5f, WorkspaceBlur.THEME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(Float.NaN, WorkspaceBlur.ON)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(GlassSettings.UNSET_OPACITY, null)).isInstanceOf(NullPointerException.class);
    }

    @Test void theBuilderCarriesGlass() {
        var snapshot = ConfigSnapshot.defaults().toBuilder().glass(new GlassSettings(90f, WorkspaceBlur.ON)).build();
        assertThat(snapshot.glass()).isEqualTo(new GlassSettings(90f, WorkspaceBlur.ON));
        assertThat(snapshot.toBuilder().build().glass()).isEqualTo(new GlassSettings(90f, WorkspaceBlur.ON));
    }

    @Test void blurIsAThreeWayChoiceDefaultingToTheTheme() {
        assertThat(CoreSettings.UI_WORKSPACE_BLUR.defaultValue()).isEqualTo(WorkspaceBlur.THEME);
        assertThat(CoreSettings.UI_WORKSPACE_BLUR.kind()).isInstanceOf(dev.jasper.app.settings.SettingKind.Choice.class);
        assertThat(((dev.jasper.app.settings.SettingKind.Choice<?>) CoreSettings.UI_WORKSPACE_BLUR.kind()).tokens())
            .extracting(java.util.Map.Entry::getKey).containsExactly("theme", "on", "off");
    }
}
