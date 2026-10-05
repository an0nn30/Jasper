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
                slider(page).setValue(1200);
                save("sample", theme, "settings-draft", page, 680, 280);
            }
        });
    }
    private static javax.swing.JSlider slider(java.awt.Container root) {
        for (var child : root.getComponents()) {
            if (child instanceof javax.swing.JSlider found) return found;
            if (child instanceof java.awt.Container nested) { var found = slider(nested); if (found != null) return found; }
        }
        return null;
    }
}
