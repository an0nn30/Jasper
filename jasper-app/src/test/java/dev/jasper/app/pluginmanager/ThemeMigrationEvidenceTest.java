package dev.jasper.app.pluginmanager;

import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesRealRemovalConfirmation() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            new dev.jasper.app.appearance.ThemeController().configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT);
            var consent = new ConsentView("Fixture plugin", "1.0.0", "Fixture vendor", java.util.List.of("palette.contribute"), false, "Allow", () -> { }, () -> { });
            save("plugin-manager", theme, "consent", consent, 680, 360);
            var confirm = new ConfirmView("Remove the fixture plugin? Its settings remain available.", "Remove", () -> { }, () -> { });
            save("plugin-manager", theme, "remove-confirmation", confirm, 600, 200);
            confirm.ok.setEnabled(false);
            save("plugin-manager", theme, "removal-busy", confirm, 600, 200);
        });
    }
}
