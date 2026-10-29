package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.platform.SystemAppearance.Observation;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.TerminalScheme;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** spec 5.2, Review Focus 1: the OS appearance re-themes live and never writes configuration or reaches a draft. */
@ExtendWith(EdtTestExtension.class)
class SystemThemeApplicationTest {
    static final JasperTheme COBALT = JasperTheme.bundled();
    @TempDir Path home;

    @Test void withTheSystemAppearanceAnOsChangeReThemesWithoutPublishingOrWritingConfiguration() throws Exception {
        Path file = home.resolve("config.toml");
        String text = "[ui]\nappearance = 'system'\n[ui.theme]\ndark = 'jasper-meadow'\nlight = 'jasper-meadow'\n"; Files.writeString(file, text);
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, true)).get();
        var themes = new ThemeController(true, TerminalSchemeChoice.MATCH);
        var controller = new ConfigurationController(themes, service, path -> {}, Observation.dark());
        var states = new ArrayList<ConfigService.State>(); controller.onState(states::add);
        try {
            assertThat(themes.current().chrome()).isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(true));
            // Plan 2 finding 9: an open Settings draft keeps its unsaved edit through the flip.
            var draft = new dev.jasper.app.settingsui.SettingsDraft(service.initialState().values());
            draft.stage(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT, java.util.Optional.of(44));
            controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.current().chrome()).isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(false));
            assertThat(themes.current().palette().background()).isEqualTo(JasperTheme.builtIn("jasper-meadow").orElseThrow().matching(false).colors().background());
            assertThat(states).as("no configuration state reaches an open Settings draft").isEmpty();
            assertThat(Files.readString(file)).isEqualTo(text);
            assertThat(draft.modified()).isTrue();
            assertThat(draft.value(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            controller.systemAppearanceChanged(Observation.unavailable("temporarily unavailable"));
            assertThat(themes.current().chrome()).as("unavailable: Dark").isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(true));
            controller.close(); controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.current().chrome()).as("closed: no longer follows").isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(true));
        } finally { controller.close(); themes.close(); }
    }

    @Test void anExplicitAppearanceIgnoresTheOperatingSystemAndTheSchemeApplies() throws Exception {
        Path file = home.resolve("config.toml"); Files.writeString(file, "[ui]\nappearance = 'dark'\n[terminal]\nscheme = 'slate-cobalt/high-contrast'\n[ui.theme]\ndark = 'jasper-meadow'\nlight = 'jasper-meadow'\n");
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, true)).get();
        var themes = new ThemeController(false, TerminalSchemeChoice.MATCH);
        var controller = new ConfigurationController(themes, service, path -> {}, Observation.light());
        try {
            assertThat(themes.current().chrome()).isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(true));
            assertThat(themes.schemeChoice()).isEqualTo(TerminalSchemeChoice.parse("slate-cobalt/high-contrast").orElseThrow());
            controller.systemAppearanceChanged(Observation.dark());
            controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.current().chrome()).isSameAs(JasperTheme.builtIn("jasper-meadow").orElseThrow().generated(true));
        } finally { controller.close(); themes.close(); }
    }
}
