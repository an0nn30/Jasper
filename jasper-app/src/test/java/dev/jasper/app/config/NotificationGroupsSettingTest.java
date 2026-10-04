package dev.jasper.app.config;

import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationGroup;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** notifications.groups: what it accepts agrees with the notification model, and what it does not is a warning. */
class NotificationGroupsSettingTest {
    @Test void theModesAreTheDisplayModesTokens() {
        assertThat(CoreSettings.NOTIFICATION_MODES)
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(DisplayMode.values()).map(DisplayMode::token).toList());
    }

    @Test void aGroupIdIsWhatANotificationGroupAccepts() {
        for (String id : List.of("jasper.commands", "dev.jasper.remote.activities", "a", "x_y-z.9")) {
            assertThat(CoreSettings.notificationGroupId(id)).as(id).isTrue();
            assertThatCode(() -> new NotificationGroup(id, "Name", "jasper", DisplayMode.CARD)).as(id).doesNotThrowAnyException();
        }
        for (String id : List.of("", "Jasper.commands", "9lives", "has space", "a".repeat(161))) {
            assertThat(CoreSettings.notificationGroupId(id)).as(id).isFalse();
            assertThatThrownBy(() -> new NotificationGroup(id, "Name", "jasper", DisplayMode.CARD)).as(id)
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void unusableEntriesAreWarningsAndTheRestApplies() {
        var result = ConfigLoader.parse(Path.of("config.toml"), """
            [notifications.groups]
            "jasper.commands" = "history"
            "jasper.config" = "Sticky"
            "Not An Id" = "off"
            "jasper.general" = 3
            """, true);
        assertThat(result.rejected()).isFalse();
        assertThat(result.values().get(CoreSettings.NOTIFICATIONS_GROUPS)).isEqualTo(Map.of("jasper.commands", "history"));
        assertThat(result.diagnostics()).hasSize(3)
            .allSatisfy(diagnostic -> assertThat(diagnostic.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING));
    }
}
