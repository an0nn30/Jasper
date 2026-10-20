package dev.jasper.sample;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesRealSampleSettingsAndPendingDraft() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            try (var host = new FakePluginHost()) {
                host.start(new PluginInfo("dev.jasper.sample", "Sample", "0.1.0", Set.of()), Set.of(), Set.of(), new SamplePlugin());
                var page = host.settingsPanel("dev.jasper.sample").orElseThrow();
                save("sample", theme, "settings", page, 680, 280);
                host.settingsPanelView("dev.jasper.sample").orElseThrow().emit(
                    new dev.jasper.sdk.ui.UiEvent.Edited("settings:demo_step_millis", "1200"));
                save("sample", theme, "settings-draft", page, 680, 280);
            }
        });
    }
}
