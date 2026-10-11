package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;
import dev.jasper.app.appearance.*;
import dev.jasper.app.config.*;
import dev.jasper.app.platform.SystemAppearance.Observation;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class SystemThemeApplicationTest {
    @TempDir Path home;
    @Test void ongoingPreferenceInstallsBothSlotsWithoutPublishingConfigOrWritingDrafts() throws Exception {
        Path file = home.resolve("config.toml");
        String text = "ui.theme.name='Nord'\nui.theme.follow_system=true\n"; Files.writeString(file, text);
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, true)).get();
        var themes = new ThemeController();
        var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), path -> {}, List::of, Observation.dark());
        var states = new ArrayList<ConfigService.State>(); controller.onState(states::add);
        try {
            assertThat(themes.choice()).isSameAs(Theme.DARK);
            controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.choice()).isSameAs(Theme.JASPER_LIGHT);
            assertThat(themes.current().matching()).isTrue();
            assertThat(states).isEmpty(); assertThat(Files.readString(file)).isEqualTo(text);
            controller.systemAppearanceChanged(Observation.unavailable("temporarily unavailable"));
            assertThat(themes.choice().name()).isEqualTo("Nord");
            assertThat(controller.shown().diagnostics()).anySatisfy(problem -> assertThat(problem.message()).contains("temporarily unavailable"));
            controller.systemAppearanceChanged(Observation.dark());
            assertThat(themes.choice()).isSameAs(Theme.DARK);
            assertThat(controller.shown().diagnostics()).isEmpty();
            controller.close(); controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.choice()).isSameAs(Theme.DARK);
        } finally { controller.close(); themes.close(); }
    }
    @Test void independentWorkspaceSurvivesSystemChanges() throws Exception {
        Path file = home.resolve("config.toml"); Files.writeString(file, "ui.theme.follow_system=true\nui.theme.workspace='Nord'\n");
        var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, true)).get(); var themes = new ThemeController();
        var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), path -> {}, List::of, Observation.dark());
        try {
            var workspace = themes.current().workspace();
            controller.systemAppearanceChanged(Observation.light());
            assertThat(themes.current().workspace()).isSameAs(workspace);
            assertThat(themes.choice()).isSameAs(Theme.JASPER_LIGHT);
        } finally { controller.close(); themes.close(); }
    }
}
