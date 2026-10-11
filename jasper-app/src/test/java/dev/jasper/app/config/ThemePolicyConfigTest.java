package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ThemePolicyConfigTest {
    @Test void sourceAndFollowSystemRoundTripWithoutRewritingTheRememberedName() throws Exception {
        var loaded = ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nname='Custom Night'\nsource='bundled'\nfollow_system=true\n", true);
        assertThat(loaded.diagnostics()).isEmpty();
        assertThat(loaded.snapshot().theme()).isEqualTo("Custom Night");
        assertThat(loaded.snapshot().followSystemTheme()).isEqualTo(true);
        assertThat(loaded.snapshot().themeSource().toString()).isEqualTo("BUNDLED");
        assertThat(loaded.snapshot().toBuilder().build()).isEqualTo(loaded.snapshot());
    }
    @Test void legacyAliasRetainsCatalogAndManualDefaults() throws Exception {
        var loaded = ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\nvariant='light'\n", true);
        assertThat(loaded.snapshot().theme()).isEqualTo("Jasper Light");
        assertThat(loaded.snapshot().followSystemTheme()).isEqualTo(false);
        assertThat(loaded.snapshot().themeSource().toString()).isEqualTo("CATALOG");
    }
}
