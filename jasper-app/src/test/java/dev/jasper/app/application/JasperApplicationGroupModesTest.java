package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.settingsui.NotificationGroupChoices;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationGroupModesTest {
    @TempDir Path home;

    @Test void configuredTokensBecomeModesAndAnythingElseIsLeftOut() {
        assertThat(JasperApplication.groupModes(Map.of("jasper.commands", "history", "jasper.config", "off", "jasper.errors", "Loud")))
            .isEqualTo(Map.of("jasper.commands", DisplayMode.HISTORY, "jasper.config", DisplayMode.OFF));
    }

    @Test void settingsListsJaspersGroupsAsJasperAndAPluginsByItsName() {
        var groups = List.of(new NotificationGroup("jasper.commands", "Long-running commands", "jasper", DisplayMode.CARD),
            new NotificationGroup("dev.jasper.remote.activities", "Remote activities", "dev.jasper.remote", DisplayMode.CARD),
            new NotificationGroup("dev.example.tool", "Tool", "dev.example.tool", DisplayMode.STICKY));
        assertThat(JasperApplication.groupChoices(groups, id -> id.equals("dev.jasper.remote") ? "Remote" : id)).containsExactly(
            new NotificationGroupChoices.Group("jasper.commands", "Long-running commands", "Jasper", "card"),
            new NotificationGroupChoices.Group("dev.jasper.remote.activities", "Remote activities", "Remote", "card"),
            new NotificationGroupChoices.Group("dev.example.tool", "Tool", "dev.example.tool", "sticky"));
    }

    /** notifications.groups applies when Jasper starts and again when the file changes, without a restart. */
    @Test void aConfiguredModeAppliesAtStartAndLive() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "[notifications.groups]\n\"jasper.general\" = \"off\"\n");
        var service = new ConfigService(file, false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                assertThat(application[0].notificationCenter().mode(NotificationsFeature.GENERAL.id())).isEqualTo(DisplayMode.OFF);
            });
            service.save(new SettingsEdit(Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.general", "history"))),
                Map.of(CoreSettings.NOTIFICATIONS_GROUPS, Optional.of(Map.of("jasper.general", "off"))))).get(5, TimeUnit.SECONDS);
            // The save queued its new state on the EDT before it finished, so this runs after the application heard it.
            edt(() -> assertThat(application[0].notificationCenter().mode(NotificationsFeature.GENERAL.id()))
                .isEqualTo(DisplayMode.HISTORY));
        } finally {
            if (application[0] != null) {
                edt(application[0]::quit);
                assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            }
            service.close();
        }
    }
}
