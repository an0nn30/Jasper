package dev.jasper.app.application;

import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.SystemNotifier;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class SystemNoticesTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final UUID LAST_USED = UUID.randomUUID();

    private Delivery.Presence presence = Delivery.Presence.BACKGROUND;
    private boolean enabled = true;
    private final NotificationCenter center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> presence);
    private final List<String> sent = new ArrayList<>();
    private final List<String> withdrawn = new ArrayList<>();
    private final List<String> backendCalls = new ArrayList<>();
    private final List<Optional<UUID>> raised = new ArrayList<>();
    private final List<UUID> histories = new ArrayList<>();
    private final List<UUID> errors = new ArrayList<>();
    /** What raising a window returns: the window it raised, empty while Jasper quits. */
    private Optional<UUID> raises = Optional.of(LAST_USED);

    private SystemNotices notices() {
        center.register(CARDS);
        var notices = new SystemNotices(center, () -> enabled, new SystemNotices.Windows(target -> {
            raised.add(target);
            return raises.map(last -> target.orElse(last));
        }, histories::add, errors::add));
        notices.use(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
            message -> sent.add(message.level() + " " + message.title() + "|" + message.body()),
            withdrawn::add, () -> withdrawn.add("all"), () -> backendCalls.add("focused"), () -> backendCalls.add("close"))));
        return notices;
    }

    private UUID post(NotificationRequest request) { return center.post(request).orElseThrow(); }

    private static NotificationRequest request(Severity severity, String title) {
        return NotificationRequest.of(CARDS.id(), severity, title);
    }

    @Test void inTheBackgroundANotificationGoesToTheOperatingSystemWithItsLevelAndText() {
        notices();
        post(request(Severity.WARNING, "make").withContent("Exited 1 · 2m 03s"));
        assertThat(sent).containsExactly("WARNING make|Exited 1 · 2m 03s");
    }

    @Test void inTheForegroundTheCardSaysItAndTheOperatingSystemHearsNothing() {
        notices();
        presence = Delivery.Presence.FOCUSED;
        post(request(Severity.INFO, "make"));
        assertThat(sent).isEmpty();
    }

    /** An incident's details can name paths and hosts, and banners show on a locked screen (spec section 11). */
    @Test void anIncidentShowsOnlyItsTitle() {
        notices();
        post(request(Severity.ERROR, "Unexpected error in Jasper").withContent("IOException: /home/me/secret")
            .withIncident(UUID.randomUUID()));
        assertThat(sent).containsExactly("ERROR Unexpected error in Jasper|");
    }

    @Test void turningSystemNotificationsOffStopsSendingButStillWithdraws() {
        notices();
        UUID shown = post(request(Severity.INFO, "shown"));
        enabled = false;
        post(request(Severity.INFO, "quiet"));
        center.markRead(shown);
        assertThat(sent).containsExactly("INFO shown|");
        assertThat(withdrawn).containsExactly(shown.toString());
        assertThat(center.history()).as("history keeps both").hasSize(2);
    }

    @Test void readingExpiringRemovingAndClearingWithdrawWhatTheOperatingSystemShows() {
        notices();
        UUID read = post(request(Severity.INFO, "read"));
        UUID expired = post(request(Severity.INFO, "expired"));
        UUID removed = post(request(Severity.INFO, "removed"));
        center.markRead(read);
        center.expire(expired);
        center.remove(removed);
        center.markRead(read);
        post(request(Severity.INFO, "cleared"));
        center.clear();
        center.clear();
        assertThat(withdrawn).containsExactly(read.toString(), expired.toString(), removed.toString(), "all");
    }

    @Test void whatTheOperatingSystemNeverShowedIsNeverWithdrawn() {
        notices();
        presence = Delivery.Presence.FOCUSED;
        UUID card = post(request(Severity.INFO, "card"));
        center.markRead(card);
        center.clear();
        assertThat(withdrawn).isEmpty();
    }

    @Test void aClickRaisesItsWindowAndRunsItsDefaultAction() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        var ran = new ArrayList<String>();
        UUID id = post(request(Severity.INFO, "make").withTarget(window).withActivate(() -> ran.add("focus the pane")));
        notices.clicked(id);
        assertThat(raised).containsExactly(Optional.of(window));
        assertThat(ran).containsExactly("focus the pane");
        assertThat(center.find(id).orElseThrow().read()).isTrue();
        assertThat(withdrawn).containsExactly(id.toString());
        assertThat(histories).isEmpty();
    }

    @Test void aClickOnAnIncidentOpensTheErrorsWindowAtIt() {
        var notices = notices();
        UUID incident = UUID.randomUUID();
        UUID id = post(request(Severity.ERROR, "Unexpected error in Jasper").withIncident(incident));
        notices.clicked(id);
        assertThat(errors).containsExactly(incident);
        assertThat(center.find(id).orElseThrow().read()).isTrue();
        assertThat(histories).isEmpty();
    }

    @Test void aClickWithNothingToRunShowsTheHistoryInTheRaisedWindow() {
        var notices = notices();
        UUID id = post(request(Severity.INFO, "Theme installed"));
        notices.clicked(id);
        assertThat(raised).containsExactly(Optional.empty());
        assertThat(histories).containsExactly(LAST_USED);
        assertThat(center.find(id).orElseThrow().read()).isTrue();
    }

    @Test void aClickOnAGoneOrExpiredNotificationOnlyShowsTheHistory() {
        var notices = notices();
        var ran = new ArrayList<String>();
        UUID expired = post(request(Severity.INFO, "expired").withActivate(() -> ran.add("ran")));
        center.expire(expired);
        notices.clicked(expired);
        notices.clicked(UUID.randomUUID());
        assertThat(ran).isEmpty();
        assertThat(histories).containsExactly(LAST_USED, LAST_USED);
    }

    @Test void whileJasperQuitsAClickOpensNothing() {
        var notices = notices();
        raises = Optional.empty();
        var ran = new ArrayList<String>();
        UUID id = post(request(Severity.INFO, "late").withActivate(() -> ran.add("ran")));
        notices.clicked(id);
        assertThat(ran).isEmpty();
        assertThat(histories).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test void focusIsPassedOnAndCloseReleasesTheNotifierAndStopsListening() {
        var notices = notices();
        notices.focused();
        notices.close();
        notices.close();
        UUID after = post(request(Severity.INFO, "after"));
        notices.clicked(after);
        assertThat(backendCalls).containsExactly("focused", "close");
        assertThat(sent).isEmpty();
        assertThat(raised).isEmpty();
    }

    @Test void aNewNotifierReplacesTheOldOneWhichIsClosed() {
        var notices = notices();
        var later = new ArrayList<String>();
        notices.use(SystemNotifier.using(Runnable::run, clicks -> new SystemNotifier.Backend(
            message -> later.add(message.title()), id -> { }, () -> { }, () -> { }, () -> { })));
        post(request(Severity.INFO, "routed"));
        assertThat(later).containsExactly("routed");
        assertThat(backendCalls).containsExactly("close");
        assertThat(sent).isEmpty();
    }
}
