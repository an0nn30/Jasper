package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.config.ConfigService;
import dev.jasper.sdk.JasperSdk;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review Focus 4: a fresh home with Enterprise turned on starts with its kit's defaults and offers its theme. */
class EnterpriseHomeTest {
    @TempDir Path root;

    @Test void aFreshHomeUnderEnterpriseStartsWithTheKitsDefaults() throws Exception {
        var options = new PluginRuntime.Options(Path.of(System.getProperty("jasper.stagedPlugins")), root.resolve("user"), null, false,
            root.resolve("plugins.toml"), root.resolve("plugins.lock"));
        var off = PluginSelection.scan(options, "", Version.parse(JasperSdk.VERSION), PluginSelection.host());
        assertThat(off.presentation().look()).as("off by default").isEqualTo("default");
        var offLibrary = ThemeLibrary.of(off.themes());
        assertThat(offLibrary.theme("enterprise")).as("its theme is dormant while it is off").isEmpty();
        assertThat(offLibrary.dormant()).contains("enterprise");

        new PluginStateStore(options.stateFile(), options.lockFile(), Duration.ofSeconds(2))
            .transact(PluginStateStore.enabling("dev.jasper.enterprise", true, true));
        var on = PluginSelection.scan(options, "", Version.parse(JasperSdk.VERSION), PluginSelection.host());
        assertThat(on.presentation().look()).isEqualTo("dev.jasper.enterprise");
        assertThat(on.presentation().provider().orElseThrow().name()).isEqualTo("Enterprise");
        assertThat(on.presentation().droppedDefaults()).isEmpty();
        assertThat(on.presentation().kit().faces().uiFace(400)).isEqualTo("IBM Plex Sans");
        try (var service = new ConfigService(root.resolve("config.toml"), false, on.presentation().defaults())) {
            var snapshot = service.initialState().snapshot();
            assertThat(snapshot.visibleRows()).isEqualTo(8);
            assertThat(snapshot.toolbarHeight()).isEqualTo(34);
            assertThat(snapshot.lightTheme()).isEqualTo("enterprise");
            assertThat(snapshot.darkTheme()).isEqualTo("enterprise");
        }
        var library = ThemeLibrary.of(on.themes());
        assertThat(library.theme("enterprise")).hasValueSatisfying(theme -> assertThat(theme.name()).isEqualTo("Enterprise"));
        assertThat(library.provider("enterprise")).contains("Enterprise");
        assertThat(library.themes(true)).extracting(theme -> theme.id()).contains("enterprise");
        assertThat(library.themes(false)).extracting(theme -> theme.id()).contains("enterprise");
    }
}
