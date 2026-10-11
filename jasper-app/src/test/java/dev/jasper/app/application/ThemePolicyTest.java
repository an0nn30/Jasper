package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemePolicyTest {
    @TempDir Path home;
    @Test void bundledOriginCannotBeShadowedByAnOppositePolarityInstalledName() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/shadow.toml"), "name='Jasper Dark'\nextends='Jasper Light'\n");
        var catalog = ThemeCatalog.beside(home.resolve("config.toml")); catalog.scan();
        var bundled = ConfigLoader.parse(home.resolve("config.toml"), "ui.theme.source='bundled'\n", true).snapshot();
        var resolved = ThemePolicy.resolve(bundled, catalog.latest(), dev.jasper.app.platform.SystemAppearance.Observation.light(), true);
        assertThat(resolved.theme()).isSameAs(Theme.DARK);
        var manual = ThemePolicy.resolve(bundled.toBuilder().themeSource(ThemeSource.CATALOG).build(), catalog.latest(), dev.jasper.app.platform.SystemAppearance.Observation.dark(), true);
        assertThat(manual.theme()).isNotSameAs(Theme.DARK);
        assertThat(manual.theme().dark()).isFalse();
    }
    @Test void systemUsesBundledObjectsAndUnavailableRetainsManualIntent() {
        var config = ConfigSnapshot.builder().theme("Nord").followSystemTheme(true).build();
        var catalog = ThemeCatalog.builtIn().latest();
        assertThat(ThemePolicy.resolve(config, catalog, dev.jasper.app.platform.SystemAppearance.Observation.light(), true).theme()).isSameAs(Theme.JASPER_LIGHT);
        assertThat(ThemePolicy.resolve(config, catalog, dev.jasper.app.platform.SystemAppearance.Observation.dark(), false).theme()).isSameAs(Theme.DARK);
        var fallback = ThemePolicy.resolve(config, catalog, dev.jasper.app.platform.SystemAppearance.Observation.unavailable("Bridge missing"), true);
        assertThat(fallback.theme().name()).isEqualTo("Nord");
        assertThat(fallback.problems()).anySatisfy(message -> assertThat(message).contains("Bridge missing"));
        assertThat(config.theme()).isEqualTo("Nord");
    }
    @Test void missingBundledNeverFallsThroughToTheCatalog() {
        var config = ConfigSnapshot.builder().theme("Custom").themeSource(ThemeSource.BUNDLED).build();
        var result = ThemePolicy.resolve(config, ThemeCatalog.builtIn().latest(), dev.jasper.app.platform.SystemAppearance.Observation.dark(), false);
        assertThat(result.theme()).isSameAs(Theme.JASPER_LIGHT);
        assertThat(result.problems()).anySatisfy(message -> assertThat(message).contains("bundled", "Custom"));
    }
}
