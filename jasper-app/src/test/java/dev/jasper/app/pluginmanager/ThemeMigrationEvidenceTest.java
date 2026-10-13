package dev.jasper.app.pluginmanager;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import org.junit.jupiter.api.Test;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @Test void capturesRealRemovalConfirmation() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var controller = ThemeTestSupport.controller(true);
            controller.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            var kit = controller.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME);
            var consent = new ConsentView(kit, "Fixture plugin", "1.0.0", "Fixture vendor", java.util.List.of("palette.contribute"), false, "Allow", () -> { }, () -> { });
            save("plugin-manager", theme, "consent", consent.view(), 680, 360);
            var confirm = new ConfirmView(kit, "Remove the fixture plugin?", "Its settings remain available.", "Remove", () -> { }, () -> { });
            save("plugin-manager", theme, "remove-confirmation", confirm.view(), 600, 200);
            confirm.busy(true);
            save("plugin-manager", theme, "removal-busy", confirm.view(), 600, 200);
            consent.close(); confirm.close(); controller.close();
        });
    }
}
