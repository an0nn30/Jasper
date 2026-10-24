package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.PluginJars;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationIncidentsTest {
    private static final String BOOM = """
        package fix.boom;
        public final class Main implements dev.jasper.sdk.plugin.Plugin {
            @Override public void start(dev.jasper.sdk.plugin.PluginContext context) { throw new IllegalStateException("boom at start"); }
        }
        """;

    @TempDir Path home;

    private static Optional<PanelBadge> badge(JasperApplication application) {
        return application.contributions().panels().stream()
            .filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow().badge();
    }

    private static JasperApplication application(CountDownLatch terminated) {
        return new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
    }

    @Test void anUncaughtFailureOnAnyThreadIsAnInternalError() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> application[0] = application(terminated));
        Thread worker = Thread.ofPlatform().name("jasper-test-worker")
            .start(() -> application[0].unexpected(Thread.currentThread(), new IllegalStateException("worker broke")));
        worker.join();
        edt(() -> {
            assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
                assertThat(incident.source()).isEqualTo("jasper");
                assertThat(incident.operation()).isEqualTo("thread jasper-test-worker");
                assertThat(incident.thread()).isEqualTo("jasper-test-worker");
            });
            assertThat(application[0].notificationCenter().history()).singleElement().satisfies(entry -> {
                assertThat(entry.request().group()).isEqualTo("jasper.errors");
                assertThat(entry.request().title()).isEqualTo("Unexpected error in Jasper");
                assertThat(entry.request().content()).isEqualTo("IllegalStateException: worker broke");
            });
            assertThat(badge(application[0])).contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error"));
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void aNotificationActionThatThrowsIsAJasperIncident() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            var center = application[0].notificationCenter();
            UUID id = center.post(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "Has a broken action")
                .withAction(NotificationAction.of("Break", () -> { throw new IllegalStateException("action broke"); }))).orElseThrow();
            center.runAction(id, 0);
        });
        edt(() -> assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
            assertThat(incident.operation()).isEqualTo("a notification action");
            assertThat(incident.summary()).isEqualTo("IllegalStateException: action broke");
        }));
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void aPluginThatFailsToStartIsAPluginProblemWithDisablePluginAndShowMore() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "boom.jar", PluginJars.descriptor("dev.example.boom", "1.0.0", "fix.boom.Main"),
            Map.of("fix.boom.Main", BOOM), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(() -> {
            assertThat(application[0].incidents().list()).singleElement().satisfies(incident -> {
                assertThat(incident.source()).isEqualTo("dev.example.boom");
                assertThat(incident.operation()).isEqualTo("start");
                assertThat(incident.summary()).isEqualTo("IllegalStateException: boom at start");
            });
            var notice = application[0].notificationCenter().history().stream()
                .filter(entry -> entry.request().group().equals("jasper.plugins")).findFirst().orElseThrow();
            assertThat(notice.request().title()).isEqualTo("dev.example.boom failed to start");
            assertThat(notice.request().actions()).extracting(NotificationAction::label).containsExactly("Disable Plugin", "Show more…");
            assertThat(application[0].contributions().action(ErrorsTab.SHOW_ERRORS)).isPresent();
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test void aPluginThatWasNotStartedIsAPluginProblemWithOpenPlugins() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "future.jar",
            "id = \"dev.example.future\"\nname = \"Future\"\nversion = \"1.0.0\"\nentry = \"fix.future.Main\"\nsdk = \">=9.0.0\"\n",
            Map.of("fix.future.Main", PluginJars.emptyPlugin("fix.future", "Main")), List.of());
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = application(terminated);
            application[0].startPlugins(null, dev, false, dirs);
            var notice = application[0].notificationCenter().history().stream()
                .filter(entry -> entry.request().group().equals("jasper.plugins")).findFirst().orElseThrow();
            assertThat(notice.request().title()).isEqualTo("Future was not started");
            assertThat(notice.request().actions()).extracting(NotificationAction::label).containsExactly("Open Plugins");
        });
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }
}
