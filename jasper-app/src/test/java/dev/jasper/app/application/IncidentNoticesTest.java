package dev.jasper.app.application;

import dev.jasper.app.notifications.Capture;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.Incident;
import dev.jasper.app.notifications.Incidents;
import dev.jasper.app.notifications.Notification;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(EdtTestExtension.class)
class IncidentNoticesTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** A failure whose message, and so its toString and trace, cannot be read. */
    private static final class Unreadable extends RuntimeException {
        @Override public String getMessage() { throw new IllegalStateException("unreadable"); }
    }

    /** A failure whose cause cannot be read. */
    private static final class Causeless extends RuntimeException {
        Causeless() { super("no cause to read"); }
        @Override public synchronized Throwable getCause() { throw new IllegalStateException("unreadable cause"); }
    }

    /** A failure whose stack trace reads as nothing at all. */
    private static final class Traceless extends RuntimeException {
        Traceless() { super("no trace to read"); }
        @Override public StackTraceElement[] getStackTrace() { return null; }
    }

    /** Delayed tasks a test runs by hand, as the application's Swing timers would run them. */
    private static final class ManualScheduler implements BiFunction<Duration, Runnable, Runnable> {
        private record Scheduled(Duration delay, Runnable task, AtomicBoolean cancelled) {}
        private final List<Scheduled> scheduled = new ArrayList<>();

        @Override public Runnable apply(Duration delay, Runnable task) {
            var entry = new Scheduled(delay, task, new AtomicBoolean());
            scheduled.add(entry);
            return () -> entry.cancelled().set(true);
        }

        /** The delays of the tasks still waiting. */
        List<Duration> waiting() { return scheduled.stream().filter(entry -> !entry.cancelled().get()).map(Scheduled::delay).toList(); }

        /** Runs every task still waiting. */
        void fire() {
            List<Scheduled> due = List.copyOf(scheduled);
            scheduled.clear();
            for (Scheduled entry : due) if (!entry.cancelled().get()) entry.task().run();
        }

        /** Runs every task, cancelled or not, as a timer that had already fired would. */
        void fireEvenCancelled() {
            List<Scheduled> due = List.copyOf(scheduled);
            scheduled.clear();
            due.forEach(entry -> entry.task().run());
        }
    }

    private final MutableClock clock = new MutableClock();
    private final NotificationCenter center = new NotificationCenter(Runnable::run, clock, () -> Delivery.Presence.FOCUSED);
    private final Incidents incidents = new Incidents(clock);
    private final List<NotificationCenter.Change> changes = new ArrayList<>();
    private final List<UUID> opened = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    private Optional<String> disableProblem = Optional.empty();
    private Executor ui = Runnable::run;
    private final ManualScheduler scheduler = new ManualScheduler();

    private IncidentNotices notices() {
        center.register(NotificationsFeature.GENERAL);
        center.onChange(changes::add);
        return new IncidentNotices(center, incidents, task -> ui.execute(task), scheduler,
            id -> id.equals("dev.jasper.remote") ? "Remote" : id,
            (id, done) -> { disabled.add(id); done.accept(disableProblem); }, opened::add);
    }

    private static List<String> labels(Notification notification) {
        return notification.request().actions().stream().map(NotificationAction::label).toList();
    }

    private long cards() { return changes.stream().filter(NotificationCenter.Change.CardRequested.class::isInstance).count(); }

    private List<String> kinds() { return changes.stream().map(change -> change.getClass().getSimpleName()).toList(); }

    /** The notification targeted at {@code window}. */
    private Notification noticeIn(UUID window) {
        return center.history().stream().filter(entry -> entry.request().target().equals(Optional.of(window))).findFirst().orElseThrow();
    }

    /** Failures made on one line, so they share a signature. */
    private static List<IllegalStateException> sameSite(int count) {
        var failures = new ArrayList<IllegalStateException>();
        for (int i = 0; i < count; i++) failures.add(new IllegalStateException("broken " + i));
        return failures;
    }

    /** A failure whose stack starts in Swing, then runs through each of {@code jasperClasses}. */
    private static IllegalStateException thrownIn(String... jasperClasses) {
        var frames = new ArrayList<StackTraceElement>();
        frames.add(new StackTraceElement("javax.swing.JComponent", "paint", "JComponent.java", 1));
        for (int i = 0; i < jasperClasses.length; i++) frames.add(new StackTraceElement(jasperClasses[i], "run", "Source.java", 10 + i));
        var failure = new IllegalStateException("from " + jasperClasses[0]);
        failure.setStackTrace(frames.toArray(StackTraceElement[]::new));
        return failure;
    }

    @Test void declaresThePluginProblemsAndInternalErrorsGroups() {
        notices();
        assertThat(center.group("jasper.plugins")).contains(new NotificationGroup("jasper.plugins", "Plugin problems", "jasper", DisplayMode.CARD));
        assertThat(center.group("jasper.errors")).contains(new NotificationGroup("jasper.errors", "Internal errors", "jasper", DisplayMode.CARD));
    }

    @Test void aJasperFailureIsAnIncidentAndAnInternalError() {
        var notices = notices();
        notices.jasperFailure("thread worker", new IllegalStateException("broken\nsecond line"));
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("jasper");
        assertThat(incident.operation()).isEqualTo("thread worker");
        assertThat(incident.thread()).isEqualTo(Thread.currentThread().getName());
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.errors");
        assertThat(notice.request().severity()).isEqualTo(Severity.ERROR);
        assertThat(notice.request().title()).isEqualTo("Unexpected error in Jasper");
        assertThat(notice.request().content()).isEqualTo("IllegalStateException: broken");
        assertThat(labels(notice)).containsExactly("Show more…");
        assertThat(cards()).isEqualTo(1);
        center.runAction(notice.id(), 0);
        assertThat(opened).containsExactly(incident.id());
        assertThat(center.find(notice.id()).orElseThrow().expired()).as("Show more… keeps working").isFalse();
    }

    @Test void aPluginFailureNamesThePluginAndOffersDisablePlugin() {
        var notices = notices();
        notices.pluginFailure("dev.jasper.remote", "panel remote.sftp",
            new java.io.UncheckedIOException("no route", new java.io.IOException("no route")));
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.plugins");
        assertThat(notice.request().title()).isEqualTo("Remote failed in panel remote.sftp");
        assertThat(notice.request().content()).isEqualTo("UncheckedIOException: no route");
        assertThat(notice.request().source()).as("Jasper posts it, not the plugin").isEqualTo("jasper");
        assertThat(labels(notice)).as("Show more… last (spec section 3)").containsExactly("Disable Plugin", "Show more…");
        center.runAction(notice.id(), 1);
        assertThat(opened).containsExactly(incidents.list().getFirst().id());
        center.runAction(notice.id(), 0);
        assertThat(disabled).containsExactly("dev.jasper.remote");
        Notification outcome = center.history().getFirst();
        assertThat(outcome.request().title()).isEqualTo("Remote will be disabled when Jasper restarts");
        assertThat(outcome.request().severity()).isEqualTo(Severity.INFO);
        assertThat(outcome.request().group()).isEqualTo("jasper.plugins");
        assertThat(center.find(notice.id()).orElseThrow().expired()).isFalse();
    }

    @Test void startAndStopFailuresReadNaturallyAndAFailedDisableIsAnError() {
        assertThat(IncidentNotices.pluginTitle("Remote", "start")).isEqualTo("Remote failed to start");
        assertThat(IncidentNotices.pluginTitle("Remote", "stop")).isEqualTo("Remote failed to stop");
        assertThat(IncidentNotices.pluginTitle("Remote", "action remote.connect")).isEqualTo("Remote failed in action remote.connect");
        var notices = notices();
        disableProblem = Optional.of("The plugin state is locked");
        notices.disablePlugin("dev.jasper.remote");
        Notification outcome = center.history().getFirst();
        assertThat(outcome.request().title()).isEqualTo("Could not disable Remote");
        assertThat(outcome.request().content()).isEqualTo("The plugin state is locked");
        assertThat(outcome.request().group()).isEqualTo("jasper.plugins");
        assertThat(outcome.request().severity()).isEqualTo(Severity.ERROR);
    }

    @Test void aRepeatUpdatesTheSameNotificationAndShowsItsCardAtMostOnceAMinute() {
        var notices = notices();
        List<IllegalStateException> burst = sameSite(4);
        notices.jasperFailure("thread worker", burst.get(0));
        clock.advance(Duration.ofSeconds(10));
        notices.jasperFailure("thread worker", burst.get(1));
        clock.advance(Duration.ofSeconds(10));
        notices.jasperFailure("thread worker", burst.get(2));
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.count()).isEqualTo(3);
            assertThat(entry.read()).isFalse();
        });
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(3));
        assertThat(cards()).as("one card in the first minute").isEqualTo(1);
        clock.advance(Duration.ofSeconds(41));
        notices.jasperFailure("thread worker", burst.get(3));
        assertThat(cards()).as("a minute after the last card").isEqualTo(2);
        assertThat(center.history().getFirst().count()).isEqualTo(4);
    }

    @Test void aBurstFromAnotherThreadIsOneIncidentAndOneCard() throws Exception {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        ui = queued::add;
        var notices = notices();
        List<IllegalStateException> burst = sameSite(50);
        Thread worker = Thread.ofPlatform().name("jasper-test-burst")
            .start(() -> burst.forEach(failure -> notices.pluginFailure("dev.jasper.remote", "background task", failure)));
        worker.join();
        for (Runnable task = queued.poll(); task != null; task = queued.poll()) task.run();
        assertThat(incidents.list()).singleElement().satisfies(incident -> {
            assertThat(incident.count()).isEqualTo(50);
            assertThat(incident.thread()).isEqualTo("jasper-test-burst");
        });
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(50));
        assertThat(cards()).isEqualTo(1);
    }

    @Test void failuresWaitingForTheEdtAreMergedIntoOneRecord() throws Exception {
        var queued = new ConcurrentLinkedQueue<Runnable>();
        ui = queued::add;
        var notices = notices();
        List<IllegalStateException> flood = sameSite(10_001);
        Thread worker = Thread.ofPlatform().name("jasper-test-flood").start(() -> flood.subList(0, 10_000)
            .forEach(failure -> notices.pluginFailure("dev.jasper.remote", "background task", failure)));
        worker.join();
        assertThat(queued).as("one record task, however many failures wait").hasSize(1);
        queued.remove().run();
        assertThat(incidents.list()).singleElement().satisfies(incident -> {
            assertThat(incident.count()).isEqualTo(10_000);
            assertThat(incident.thread()).isEqualTo("jasper-test-flood");
            assertThat(incident.message()).as("the first failure's text").isEqualTo("broken 0");
        });
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(10_000));
        assertThat(cards()).isEqualTo(1);
        notices.pluginFailure("dev.jasper.remote", "background task", flood.getLast());
        assertThat(queued).as("once recorded, the next failure waits anew").hasSize(1);
        clock.advance(IncidentNotices.UPDATE_INTERVAL);
        queued.remove().run();
        assertThat(incidents.list().getFirst().count()).isEqualTo(10_001);
        assertThat(center.history().getFirst().count()).isEqualTo(10_001);
    }

    @Test void repeatsWithinASecondUpdateTheNotificationOnceAndItsCountCatchesUp() {
        var notices = notices();
        List<IllegalStateException> burst = sameSite(5);
        notices.jasperFailure("thread worker", burst.get(0));
        UUID notice = center.history().getFirst().id();
        changes.clear();
        for (int i = 1; i < 5; i++) {
            clock.advance(Duration.ofMillis(200));
            notices.jasperFailure("thread worker", burst.get(i));
        }
        assertThat(incidents.list().getFirst().count()).as("the Errors window counts each at once").isEqualTo(5);
        assertThat(changes).as("the notification waits for the second to pass").isEmpty();
        assertThat(scheduler.waiting()).as("one update, due when the second is over").containsExactly(Duration.ofMillis(800));
        clock.advance(Duration.ofMillis(200));
        scheduler.fire();
        assertThat(center.find(notice).orElseThrow().count()).isEqualTo(5);
        assertThat(kinds()).as("one update, without a card within the minute").containsExactly("Upserted");
    }

    @Test void theWaitingUpdateDecidesItsCardWhenItIsPushed() {
        var notices = notices();
        List<IllegalStateException> burst = sameSite(3);
        notices.jasperFailure("thread worker", burst.get(0));
        clock.advance(Duration.ofMillis(59_500));
        notices.jasperFailure("thread worker", burst.get(1));
        assertThat(cards()).as("no second card within the minute").isEqualTo(1);
        assertThat(center.history().getFirst().count()).isEqualTo(2);
        clock.advance(Duration.ofMillis(400));
        notices.jasperFailure("thread worker", burst.get(2));
        assertThat(center.history().getFirst().count()).as("within a second of the last update").isEqualTo(2);
        clock.advance(Duration.ofMillis(600));
        scheduler.fire();
        assertThat(cards()).as("the minute is over when the update is pushed").isEqualTo(2);
        assertThat(center.history().getFirst().count()).isEqualTo(3);
    }

    @Test void aFailureAfterTheSecondTakesTheWaitingCountWithItWhenTheTimerIsLate() {
        var notices = notices();
        List<IllegalStateException> burst = sameSite(3);
        notices.jasperFailure("thread worker", burst.get(0));
        clock.advance(Duration.ofMillis(500));
        notices.jasperFailure("thread worker", burst.get(1));
        clock.advance(Duration.ofMillis(700));
        notices.jasperFailure("thread worker", burst.get(2));
        assertThat(center.history().getFirst().count()).isEqualTo(3);
        assertThat(scheduler.waiting()).as("the late update is cancelled").isEmpty();
        scheduler.fireEvenCancelled();
        assertThat(center.history().getFirst().count()).as("and counts nothing twice").isEqualTo(3);
    }

    @Test void closeCancelsAWaitingUpdate() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        notices.jasperFailure("thread worker", twice.get(0));
        clock.advance(Duration.ofMillis(100));
        notices.jasperFailure("thread worker", twice.get(1));
        assertThat(scheduler.waiting()).hasSize(1);
        notices.close();
        assertThat(scheduler.waiting()).isEmpty();
        scheduler.fireEvenCancelled();
        assertThat(center.history().getFirst().count()).isEqualTo(1);
    }

    @Test void theTraceIsTakenWhenTheFailureIsCaughtNotWhenItIsRecorded() {
        var queued = new ArrayDeque<Runnable>();
        ui = queued::add;
        var notices = notices();
        var failure = new IllegalStateException("as caught");
        notices.jasperFailure("thread worker", failure);
        failure.setStackTrace(new StackTraceElement[0]);
        failure.addSuppressed(new IllegalStateException("added later"));
        assertThat(incidents.list()).as("recording waits for the EDT").isEmpty();
        queued.remove().run();
        assertThat(incidents.list().getFirst().trace())
            .contains("at dev.jasper.app.application.IncidentNoticesTest").doesNotContain("added later");
    }

    @Test void aClearedIncidentExpiresItsNotificationAndTheNextFailurePostsANewOne() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        notices.jasperFailure("thread worker", twice.get(0));
        UUID first = center.history().getFirst().id();
        incidents.clear();
        assertThat(center.find(first).orElseThrow().expired()).isTrue();
        notices.jasperFailure("thread worker", twice.get(1));
        assertThat(center.history()).hasSize(2);
        assertThat(center.history().getFirst()).satisfies(entry -> {
            assertThat(entry.id()).isNotEqualTo(first);
            assertThat(entry.expired()).isFalse();
            assertThat(entry.count()).isEqualTo(1);
        });
    }

    @Test void anIncidentDroppedForNewerOnesExpiresItsNotification() {
        var notices = notices();
        notices.jasperFailure("thread worker", new IllegalStateException("oldest"));
        UUID oldest = center.history().getFirst().id();
        for (int i = 0; i < Incidents.LIMIT; i++) incidents.record("jasper", "filler", "t", new Capture("x.Filler" + i, "", "x.Filler" + i, ""));
        assertThat(center.find(oldest).orElseThrow().expired()).isTrue();
    }

    @Test void aRepeatAfterItsNotificationWasRemovedPostsANewOne() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        notices.jasperFailure("thread worker", twice.get(0));
        center.remove(center.history().getFirst().id());
        notices.jasperFailure("thread worker", twice.get(1));
        assertThat(center.history()).singleElement()
            .satisfies(entry -> assertThat(entry.request().content()).isEqualTo("IllegalStateException: broken 0"));
        assertThat(incidents.list().getFirst().count()).isEqualTo(2);
    }

    @Test void aFailureOfTheNotificationSystemItselfIsOnlyLogged() {
        var notices = notices();
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.notificationui.CardLayer"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.notifications.NotificationCenter"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.IncidentNotices"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.ErrorsWindow$1"));
        notices.jasperFailure("a notification action",
            new IllegalStateException("wrapped", thrownIn("dev.jasper.app.application.NotificationsFeature")));
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).isEmpty();
        notices.jasperFailure("thread AWT-EventQueue-0",
            thrownIn("dev.jasper.app.workspace.WindowContent", "dev.jasper.app.notifications.NotificationCenter"));
        notices.jasperFailure("thread AWT-EventQueue-0", thrownIn("dev.jasper.app.application.IncidentNoticesTest"));
        assertThat(incidents.list()).as("the first Jasper frame decides, and only the named classes count").hasSize(2);
    }

    @Test void aFailureRaisedWhileAnIncidentIsAnnouncedIsNotRecordedAgain() {
        var notices = notices();
        center.onChange(change -> notices.jasperFailure("nested", new IllegalStateException("raised while announcing")));
        notices.jasperFailure("thread worker", new IllegalStateException("outer"));
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.message()).isEqualTo("outer"));
    }

    @Test void aFailureThatCannotDescribeItselfIsStillAnIncident() {
        var notices = notices();
        notices.pluginFailure("dev.jasper.remote", "background task", new Unreadable());
        assertThat(incidents.list().getFirst().trace()).isEqualTo(Unreadable.class.getName());
        assertThat(center.history().getFirst().request().content()).isEqualTo("IncidentNoticesTest$Unreadable");
    }

    @Test void aFailureWhoseCauseOrTraceCannotBeReadIsStillAnIncident() {
        var notices = notices();
        notices.jasperFailure("thread worker", new Causeless());
        notices.pluginFailure("dev.jasper.remote", "background task", new Traceless());
        assertThat(incidents.list()).hasSize(2);
        assertThat(center.history()).extracting(entry -> entry.request().content()).containsExactly(
            "IncidentNoticesTest$Traceless: no trace to read", "IncidentNoticesTest$Causeless: no cause to read");
    }

    @Test void aShellThatCannotStartIsAnIncidentAnnouncedInItsWindow() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "Could not start terminal in /tmp:\nshell executable unavailable",
            new IllegalStateException("shell executable unavailable"));
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("jasper");
        assertThat(incident.operation()).isEqualTo("starting a shell");
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("jasper.general");
        assertThat(notice.request().title()).isEqualTo("Could not start the terminal");
        assertThat(notice.request().content()).isEqualTo("Could not start terminal in /tmp:\nshell executable unavailable");
        assertThat(notice.request().target()).contains(window);
        assertThat(labels(notice)).containsExactly("Show more…");
    }

    @Test void eachLaunchFailureIsAnnouncedInItsOwnWindowEvenWhenTheyShareAnIncident() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        notices.launchFailure(first, "Could not start terminal in /tmp:\nno shell", twice.get(0));
        notices.launchFailure(second, "Could not start terminal in /home:\nno shell", twice.get(1));
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(2));
        UUID incident = incidents.list().getFirst().id();
        assertThat(center.history()).hasSize(2);
        Notification inFirst = noticeIn(first);
        Notification inSecond = noticeIn(second);
        assertThat(inFirst.request().content()).isEqualTo("Could not start terminal in /tmp:\nno shell");
        assertThat(inSecond.request().content()).isEqualTo("Could not start terminal in /home:\nno shell");
        assertThat(inFirst.request().title()).isEqualTo("Could not start the terminal");
        assertThat(inSecond.request().title()).isEqualTo("Could not start the terminal");
        assertThat(labels(inFirst)).containsExactly("Show more…");
        assertThat(labels(inSecond)).containsExactly("Show more…");
        assertThat(cards()).as("a card for each window").isEqualTo(2);
        center.runAction(inFirst.id(), 0);
        center.runAction(inSecond.id(), 0);
        assertThat(opened).as("both open the one incident").containsExactly(incident, incident);
    }

    @Test void launchFailuresWaitingForTheEdtAreNotMerged() {
        var queued = new ArrayDeque<Runnable>();
        ui = queued::add;
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        notices.launchFailure(first, "in /tmp", twice.get(0));
        notices.launchFailure(second, "in /home", twice.get(1));
        assertThat(queued).as("each keeps its own window and details").hasSize(2);
        while (!queued.isEmpty()) queued.remove().run();
        assertThat(noticeIn(first).request().content()).isEqualTo("in /tmp");
        assertThat(noticeIn(second).request().content()).isEqualTo("in /home");
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(2));
    }

    @Test void clearingAnIncidentExpiresEveryLaunchNotificationItAnnounced() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        notices.launchFailure(first, "in /tmp", twice.get(0));
        notices.launchFailure(second, "in /home", twice.get(1));
        incidents.clear();
        assertThat(center.find(noticeIn(first).id()).orElseThrow().expired()).isTrue();
        assertThat(center.find(noticeIn(second).id()).orElseThrow().expired()).isTrue();
    }

    @Test void aRemovedLaunchNotificationIsForgottenAndTheOthersStillExpire() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        notices.launchFailure(first, "in /tmp", twice.get(0));
        notices.launchFailure(second, "in /home", twice.get(1));
        center.remove(noticeIn(first).id());
        incidents.clear();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.expired()).isTrue());
    }

    @Test void identicalLaunchFailuresInOneWindowShareAnEntryThatExpiresWithTheIncident() {
        var notices = notices();
        List<IllegalStateException> twice = sameSite(2);
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "in /tmp", twice.get(0));
        notices.launchFailure(window, "in /tmp", twice.get(1));
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(entry.count()).isEqualTo(2));
        assertThat(incidents.list().getFirst().count()).isEqualTo(2);
        incidents.clear();
        assertThat(center.history().getFirst().expired()).isTrue();
    }

    @Test void aLaunchFailureWithoutAWindowIsAnnouncedWithoutATargetAndNeverThrows() {
        var notices = notices();
        assertThatCode(() -> notices.launchFailure(null, "details", new IllegalStateException("boom"))).doesNotThrowAnyException();
        Notification notice = center.history().getFirst();
        assertThat(notice.request().title()).isEqualTo("Could not start the terminal");
        assertThat(notice.request().content()).isEqualTo("details");
        assertThat(notice.request().target()).isEmpty();
        assertThat(labels(notice)).containsExactly("Show more…");
        assertThat(incidents.list()).hasSize(1);
        assertThatCode(() -> notices.launchFailure(null, null, null)).doesNotThrowAnyException();
        assertThat(center.history()).hasSize(2);
        assertThat(center.history().getFirst().request().target()).isEmpty();
    }

    @Test void aLaunchFailureRejectedAsTheNotificationSystemsOwnStillTellsTheUser() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "Could not start terminal in /tmp:\nno shell",
            thrownIn("dev.jasper.app.notificationui.CardLayer"));
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).singleElement().satisfies(entry -> {
            assertThat(entry.request().group()).isEqualTo("jasper.general");
            assertThat(entry.request().title()).isEqualTo("Could not start the terminal");
            assertThat(entry.request().content()).isEqualTo("Could not start terminal in /tmp:\nno shell");
            assertThat(entry.request().target()).contains(window);
            assertThat(entry.request().actions()).isEmpty();
        });
    }

    @Test void aLaunchFailureRaisedWhileAnIncidentIsAnnouncedStillTellsTheUser() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        boolean[] raised = {false};
        center.onChange(change -> {
            if (raised[0]) return;
            raised[0] = true;
            notices.launchFailure(window, "nested details", new IllegalStateException("raised while announcing"));
        });
        notices.jasperFailure("thread worker", new IllegalStateException("outer"));
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.message()).isEqualTo("outer"));
        Notification nested = noticeIn(window);
        assertThat(nested.request().title()).isEqualTo("Could not start the terminal");
        assertThat(nested.request().content()).isEqualTo("nested details");
        assertThat(nested.request().actions()).isEmpty();
    }

    @Test void nothingIsRecordedAfterClose() {
        var notices = notices();
        notices.close();
        notices.jasperFailure("thread worker", new IllegalStateException("late"));
        notices.launchFailure(UUID.randomUUID(), "details", new IllegalStateException("late"));
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).isEmpty();
    }

    private static NotificationRequest pluginRequest(String group, String content) {
        return NotificationRequest.of(group, Severity.ERROR, "Could not copy the address").withContent(content)
            .withSource("dev.jasper.remote").withAction(new NotificationAction("Retry", () -> { }, true));
    }

    private void declareRemote() {
        center.register(new NotificationGroup("dev.jasper.remote", "Remote", "dev.jasper.remote", DisplayMode.CARD));
    }

    @Test void aPluginsNotificationWithACauseIsItsIncidentAndOffersShowMoreLast() {
        var notices = notices();
        declareRemote();
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "Clipboard busy"),
            new IllegalStateException("clipboard busy"), posted::add);
        Incident incident = incidents.list().getFirst();
        assertThat(incident.source()).isEqualTo("dev.jasper.remote");
        assertThat(incident.operation()).isEqualTo("Could not copy the address");
        Notification notice = center.history().getFirst();
        assertThat(notice.request().group()).isEqualTo("dev.jasper.remote");
        assertThat(notice.request().source()).isEqualTo("dev.jasper.remote");
        assertThat(notice.request().content()).isEqualTo("Clipboard busy");
        assertThat(labels(notice)).containsExactly("Retry", "Show more…");
        assertThat(posted).containsExactly(Optional.of(notice.id()));
        center.runAction(notice.id(), 1);
        assertThat(opened).containsExactly(incident.id());
    }

    @Test void eachOfAPluginsNotificationsWithTheSameCauseIsPostedOnItsOwn() {
        var notices = notices();
        declareRemote();
        List<IllegalStateException> twice = sameSite(2);
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "first"), twice.get(0), id -> { });
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "second"), twice.get(1), id -> { });
        assertThat(center.history()).extracting(entry -> entry.request().content()).containsExactly("second", "first");
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(2));
    }

    @Test void aPluginsNotificationWhoseCauseCannotBeRecordedIsStillPosted() {
        var notices = notices();
        declareRemote();
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "own"),
            thrownIn("dev.jasper.app.notificationui.CardLayer"), posted::add);
        assertThat(incidents.list()).isEmpty();
        assertThat(center.history()).singleElement().satisfies(entry -> assertThat(labels(entry)).containsExactly("Retry"));
        assertThat(posted).containsExactly(Optional.of(center.history().getFirst().id()));
    }

    @Test void aCauseWrappedByAFutureIsRecordedAsTheFailureBehindIt() {
        var notices = notices();
        declareRemote();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "wrapped"),
            new java.util.concurrent.CompletionException(new java.io.IOException("connection reset")), id -> { });
        assertThat(incidents.list()).singleElement().satisfies(incident -> {
            assertThat(incident.exceptionClass()).isEqualTo("java.io.IOException");
            assertThat(incident.message()).isEqualTo("connection reset");
        });
    }

    @Test void aPluginsNotificationInAMutedGroupIsRecordedButReportsNothingPosted() {
        var notices = notices();
        center.register(new NotificationGroup("dev.jasper.remote.quiet", "Quiet", "dev.jasper.remote", DisplayMode.OFF));
        var posted = new ArrayList<Optional<UUID>>();
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote.quiet", "quiet"),
            new IllegalStateException("x"), posted::add);
        assertThat(incidents.list()).hasSize(1);
        assertThat(center.history()).isEmpty();
        assertThat(posted).containsExactly(Optional.empty());
    }

    @Test void everyNotificationAnIncidentAnnouncesNamesThatIncident() {
        var notices = notices();
        declareRemote();
        notices.jasperFailure("thread worker", new IllegalStateException("broken"));
        notices.pluginFailure("dev.jasper.remote", "panel remote.sftp", new UnsupportedOperationException("no route"));
        notices.launchFailure(UUID.randomUUID(), "in /tmp", new IllegalArgumentException("no shell"));
        notices.pluginNotice("dev.jasper.remote", pluginRequest("dev.jasper.remote", "Clipboard busy"),
            new ArithmeticException("busy"), id -> { });
        assertThat(center.history()).hasSize(4).allSatisfy(entry -> assertThat(entry.request().incident()).isPresent());
        assertThat(center.history()).extracting(entry -> entry.request().incident().orElseThrow())
            .containsExactlyInAnyOrderElementsOf(incidents.list().stream().map(Incident::id).toList());
    }

    /** Two incidents are two notifications, however alike they read: each Show more… opens its own. */
    @Test void differentIncidentsThatReadTheSameAreNeverOneNotification() {
        var notices = notices();
        UUID window = UUID.randomUUID();
        notices.launchFailure(window, "in /tmp", new IllegalStateException("no shell"));
        notices.launchFailure(window, "in /tmp", new IllegalArgumentException("no shell"));
        assertThat(incidents.list()).hasSize(2);
        assertThat(center.history()).hasSize(2).allSatisfy(entry -> assertThat(entry.count()).isEqualTo(1));
    }
}
