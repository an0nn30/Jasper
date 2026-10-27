package dev.jasper.app.application;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

class JasperApplicationNotificationsTest {
    @TempDir Path home;

    private static Optional<PanelBadge> badge(JasperApplication application) {
        return application.contributions().panels().stream()
            .filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow().badge();
    }

    @Test void theApplicationOffersTheNotificationsPanelAndActions() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            var contributions = application[0].contributions();
            assertThat(contributions.panels()).extracting(PanelEntry::id).contains(NotificationsFeature.PANEL_ID);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).isPresent();
            assertThat(contributions.action(NotificationsFeature.DISMISS_CARDS)).isPresent();
            application[0].quitNow();
        });
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test void anErrorBecomesAnUnreadErrorAndShutdownStopsTheBadge() throws Exception {
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            assertThat(badge(application[0])).isEmpty();
            application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "Could not connect"));
        });
        edt(() -> assertThat(badge(application[0]))
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        edt(() -> application[0].notificationCenter().submit(NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "After shutdown")));
        edt(() -> assertThat(badge(application[0])).as("the feature stopped listening at shutdown")
            .contains(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 unread notification, 1 error")));
    }

    @Test void configurationProblemsAtStartupPostOneUnreadNotification() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "font.future = 1\n");
        var service = new ConfigService(file, false);
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        try {
            assertThat(service.initialState().diagnostics()).as("the fixture really has a problem").isNotEmpty();
            edt(() -> {
                application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
                assertThat(badge(application[0])).contains(new PanelBadge(1, PanelBadge.Tone.ACCENT, "1 unread notification"));
            });
        } finally {
            if (application[0] != null) {
                edt(application[0]::quit);
                assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
            }
            service.close();
        }
    }

    /** Full windows 2: a card clicked in any window raises the window holding its pane now. */
    @Test void clickingACardRaisesTheWindowHoldingItsPaneNow() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                var ran = new ArrayDeque<String>();
                UUID pane = fixture.paneOfA();
                UUID id = fixture.finished(pane, () -> ran.add("focused"));
                var layer = fixture.application.notificationViews().layer(fixture.b.id());
                assertThat(layer.showing()).as("the card shows in B").containsExactly(id);
                fixture.moveFirstTabToC();
                assertThat(fixture.application.terminals().tab(fixture.application.terminals().pane(pane).orElseThrow().tabId())
                    .orElseThrow().windowId()).as("the pane's tab moved to C").isEqualTo(fixture.c.id());
                fixture.frames.clear();
                dev.jasper.app.notificationui.CardLayerTestAccess.clickBody(layer, id);
                assertThat(fixture.frames).as("C, which holds the pane now, comes forward").containsExactly("front w2");
                assertThat(ran).as("and the pane is focused").containsExactly("focused");
            });
        } finally {
            fixture.close();
        }
    }

    @Test void clickingACardWhoseWindowClosedRunsItsActionInPlace() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                var ran = new ArrayDeque<String>();
                var request = NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "For A")
                    .withActivate(() -> ran.add("ran")).withTarget(fixture.a.id());
                UUID id = fixture.application.notificationCenter().post(request).map(fixture::shownEverywhere).orElseThrow();
                var layer = fixture.application.notificationViews().layer(fixture.b.id());
                assertThat(layer.showing()).containsExactly(id);
                fixture.a.requestClose();
                assertThat(fixture.application.windows().windows()).hasSize(2);
                fixture.frames.clear();
                dev.jasper.app.notificationui.CardLayerTestAccess.clickBody(layer, id);
                assertThat(ran).as("the action ran where it was clicked").containsExactly("ran");
                assertThat(fixture.frames).as("no window came forward and none opened").isEmpty();
                assertThat(fixture.application.windows().windows()).hasSize(2);
            });
        } finally {
            fixture.close();
        }
    }

    /**
     * Full windows 2, final review I1: a card's action that opens a tab in the most recently active window opens it in the
     * window the click raised, not behind it in the window clicked.
     */
    @Test void aCardsActionOpensItsTabInTheWindowTheClickRaised() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                fixture.b.events().activated().accept(true);
                var opened = new ArrayDeque<dev.jasper.app.contributions.Tab>();
                var request = NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.ERROR, "For A")
                    .withAction(dev.jasper.app.notifications.NotificationAction.of("Open", () -> opened.add(
                        fixture.application.contributions().tabs().open(dev.jasper.app.contributions.TabRegistry.APP_OWNER,
                            "probe", "Probe", null, new javax.swing.JPanel()))))
                    .withTarget(fixture.a.id());
                UUID id = fixture.application.notificationCenter().post(request).map(fixture::shownEverywhere).orElseThrow();
                fixture.frames.clear();
                dev.jasper.app.notificationui.CardLayerTestAccess.clickAction(
                    fixture.application.notificationViews().layer(fixture.b.id()), id, 0);
                assertThat(fixture.frames).as("A comes forward (and again as its new tab is selected); B never does").containsOnly("front w0");
                assertThat(fixture.application.windows().lastActive()).as("at once").contains(fixture.a);
                assertThat(opened).singleElement().satisfies(tab -> assertThat(tab.window()).as("the tab opens in A")
                    .isEqualTo(fixture.a.id()));
            });
        } finally {
            fixture.close();
        }
    }

    /** Final review I2: a click on a card about the window already active leaves it, and its keyboard focus, as they are. */
    @Test void clickingACardAboutTheActiveWindowRaisesNothing() throws Exception {
        var fixture = new PertainingWindowFixture();
        try {
            edt(() -> {
                fixture.open();
                assertThat(fixture.application.windows().lastActive()).contains(fixture.a);
                var request = NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "For A").withTarget(fixture.a.id());
                UUID id = fixture.application.notificationCenter().post(request).map(fixture::shownEverywhere).orElseThrow();
                fixture.frames.clear();
                dev.jasper.app.notificationui.CardLayerTestAccess.clickBody(fixture.application.notificationViews().layer(fixture.a.id()), id);
                assertThat(fixture.frames).as("A is already in front: nothing is raised, nothing refocused").isEmpty();
                assertThat(fixture.application.notificationCenter().find(id).orElseThrow().read()).isTrue();
                fixture.a.events().activated().accept(false);
                UUID again = fixture.application.notificationCenter().post(
                    NotificationRequest.of(NotificationsFeature.GENERAL.id(), Severity.INFO, "For A again").withTarget(fixture.a.id()))
                    .map(fixture::shownEverywhere).orElseThrow();
                dev.jasper.app.notificationui.CardLayerTestAccess.clickBody(fixture.application.notificationViews().layer(fixture.a.id()), again);
                assertThat(fixture.frames).as("used last but no longer active: A is raised").containsExactly("front w0");
            });
        } finally {
            fixture.close();
        }
    }
}
