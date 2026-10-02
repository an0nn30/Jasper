package dev.jasper.app.application;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ConfigProblemNoticesTest {
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
    private final Path file = Path.of("config.toml").toAbsolutePath();
    private final List<String> ran = new ArrayList<>();

    private ConfigProblemNotices notices() {
        center.register(NotificationsFeature.CONFIG);
        return new ConfigProblemNotices(center, () -> ran.add("show"), () -> ran.add("open"));
    }

    private ConfigService.State state(ConfigDiagnostic... problems) {
        return new ConfigService.State(ConfigSnapshot.defaults(), List.of(problems), file, true);
    }

    private ConfigDiagnostic warning(int line, String key, String message) {
        return new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file, line, line == 0 ? 0 : 1, key, message);
    }

    @Test void aCleanConfigurationPostsNothing() {
        notices().accept(state());
        assertThat(center.history()).isEmpty();
    }

    @Test void aNewProblemPostsOneStickyNotificationWithItsActions() {
        var notices = notices();
        notices.accept(state(warning(3, "font.size", "Expected a number")));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.config");
            assertThat(entry.request().severity()).isEqualTo(Severity.WARNING);
            assertThat(entry.request().title()).isEqualTo("config.toml has warnings");
            assertThat(entry.request().content()).isEqualTo("config.toml:3: [font.size] Expected a number");
            assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Show Problems", "Open config.toml");
        });
        UUID id = center.history().getFirst().id();
        center.runAction(id, 0);
        assertThat(ran).containsExactly("show");
        assertThat(center.find(id).orElseThrow().expired()).as("the problems are still there").isFalse();
        notices.accept(state(warning(3, "font.size", "Expected a number")));
        assertThat(center.history()).as("the same problems post nothing").hasSize(1);
    }

    @Test void aNewerProblemReplacesTheNoticeAndAFixedFileExpiresIt() {
        var notices = notices();
        var first = warning(3, "font.size", "Expected a number");
        notices.accept(state(first));
        UUID earlier = center.history().getFirst().id();
        notices.accept(state(first, new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, file, 7, 1, "", "Unclosed table")));
        assertThat(center.find(earlier).orElseThrow().expired()).isTrue();
        Notification current = center.history().getFirst();
        assertThat(current.request().severity()).isEqualTo(Severity.ERROR);
        assertThat(current.request().title()).isEqualTo("config.toml has errors");
        assertThat(current.request().content()).isEqualTo("config.toml:3: [font.size] Expected a number\nand 1 more");
        notices.accept(state());
        assertThat(center.find(current.id()).orElseThrow().expired()).isTrue();
    }

    @Test void problemsInOtherFilesNameTheConfigurationInstead() {
        var notices = notices();
        notices.accept(state(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, file.resolveSibling("themes/broken.toml"), 2, 1, "", "Unknown key")));
        assertThat(center.history().getFirst().request().title()).isEqualTo("The configuration has warnings");
        assertThat(center.history().getFirst().request().content()).isEqualTo("broken.toml:2: Unknown key");
    }
}
