package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class NotificationCenterTest {
    private static final NotificationGroup CARDS = new NotificationGroup("test.cards", "Cards", "jasper", DisplayMode.CARD);
    private static final NotificationGroup STICKY = new NotificationGroup("test.sticky", "Sticky", "jasper", DisplayMode.STICKY);
    private static final NotificationGroup QUIET = new NotificationGroup("test.quiet", "Quiet", "jasper", DisplayMode.HISTORY);
    private static final NotificationGroup MUTED = new NotificationGroup("test.muted", "Muted", "jasper", DisplayMode.OFF);

    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private Delivery.Presence presence = Delivery.Presence.FOCUSED;
    private final List<NotificationCenter.Change> changes = new ArrayList<>();

    private NotificationCenter center() {
        var center = new NotificationCenter(Runnable::run, clock, () -> presence);
        for (var group : List.of(CARDS, STICKY, QUIET, MUTED)) center.register(group);
        center.onChange(changes::add);
        return center;
    }

    private static NotificationRequest request(NotificationGroup group, String title) {
        return NotificationRequest.of(group.id(), Severity.INFO, title);
    }

    private List<String> kinds() { return changes.stream().map(change -> change.getClass().getSimpleName()).toList(); }

    @Test void aFocusedCardIsRecordedThenRequested() {
        var center = center();
        UUID id = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(kinds()).containsExactly("Upserted", "CardRequested");
        assertThat(center.history()).extracting(Notification::id).containsExactly(id);
        assertThat(center.unread()).isEqualTo(1);
    }

    @Test void backgroundCardsShowNoCardAndAskTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(CARDS, "a"));
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested");
    }

    @Test void stickyCardsWaitForFocusOnce() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(STICKY, "s"));
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested");
        presence = Delivery.Presence.FOCUSED;
        center.focusReturned();
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "CardRequested");
    }

    @Test void cardsPostedWhileJasperOpensWaitForTheFirstFocus() {
        var center = center();
        presence = Delivery.Presence.OPENING;
        center.post(request(CARDS, "early"));
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "CardRequested");
    }

    @Test void aRepeatThatShowsItsCardNoLongerWaitsForFocus() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID id = center.post(request(STICKY, "s")).orElseThrow();
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested");
        presence = Delivery.Presence.FOCUSED;
        clock.advance(Duration.ofSeconds(5));
        assertThat(center.post(request(STICKY, "s"))).contains(id);
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "Upserted", "CardRequested");
        center.focusReturned();
        assertThat(kinds()).as("the card was requested once; focus does not request it again")
            .containsExactly("Upserted", "SystemRequested", "Upserted", "CardRequested");
    }

    @Test void aWaitingCardReadMeanwhileNeverShows() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID id = center.post(request(STICKY, "s")).orElseThrow();
        center.markRead(id);
        center.focusReturned();
        assertThat(kinds()).doesNotContain("CardRequested");
    }

    @Test void historyOnlyMutedAndUndeclaredGroups() {
        var center = center();
        center.post(request(QUIET, "q"));
        assertThat(kinds()).containsExactly("Upserted");
        assertThat(center.post(request(MUTED, "m"))).isEmpty();
        assertThat(center.post(NotificationRequest.of("test.unknown", Severity.INFO, "u"))).isEmpty();
        assertThat(center.history()).hasSize(1);
        assertThat(center.mode("test.unknown")).isEqualTo(DisplayMode.OFF);
    }

    @Test void registerKeepsTheFirstDeclaration() {
        var center = center();
        var again = center.register(new NotificationGroup("test.cards", "Other", "dev.example", DisplayMode.STICKY));
        assertThat(again).isSameAs(CARDS);
        assertThat(center.groups()).containsExactly(CARDS, STICKY, QUIET, MUTED);
    }

    @Test void identicalPostsWithinAMinuteCoalesce() {
        var center = center();
        UUID first = center.post(request(CARDS, "a")).orElseThrow();
        center.markRead(first);
        clock.advance(Duration.ofSeconds(30));
        UUID second = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(second).isEqualTo(first);
        assertThat(center.find(first).orElseThrow()).satisfies(entry -> {
            assertThat(entry.count()).isEqualTo(2);
            assertThat(entry.read()).isFalse();
            assertThat(entry.posted()).isEqualTo(clock.instant());
        });
        assertThat(center.history()).hasSize(1);
        assertThat(kinds().stream().filter("CardRequested"::equals)).hasSize(2);
        clock.advance(Duration.ofSeconds(61));
        UUID third = center.post(request(CARDS, "a")).orElseThrow();
        assertThat(third).isNotEqualTo(first);
        assertThat(center.history()).extracting(Notification::id).containsExactly(third, first);
    }

    @Test void aRepeatMovesToTheTop() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        UUID b = center.post(request(CARDS, "b")).orElseThrow();
        clock.advance(Duration.ofSeconds(10));
        center.post(request(CARDS, "a"));
        assertThat(center.history()).extracting(Notification::id).containsExactly(a, b);
    }

    @Test void expiredOrDifferentEntriesAreNotRepeats() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.expire(a);
        assertThat(center.post(request(CARDS, "a")).orElseThrow()).isNotEqualTo(a);
        assertThat(center.post(request(CARDS, "a").withContent("other")).orElseThrow()).isNotEqualTo(a);
        assertThat(center.history()).hasSize(3);
    }

    @Test void historyKeepsTheNewest500() {
        var center = center();
        UUID oldest = center.post(request(CARDS, "title 0")).orElseThrow();
        for (int i = 1; i <= NotificationCenter.HISTORY_LIMIT; i++) center.post(request(CARDS, "title " + i));
        assertThat(center.history()).hasSize(NotificationCenter.HISTORY_LIMIT);
        assertThat(center.find(oldest)).isEmpty();
        assertThat(changes).contains(new NotificationCenter.Change.Removed(oldest));
    }

    @Test void unreadCountsFollowReadState() {
        var center = center();
        center.post(request(CARDS, "info"));
        center.post(NotificationRequest.of(CARDS.id(), Severity.ERROR, "error"));
        assertThat(center.unread()).isEqualTo(2);
        assertThat(center.unreadErrors()).isEqualTo(1);
        center.markAllRead();
        assertThat(center.unread()).isZero();
        assertThat(center.unreadErrors()).isZero();
    }

    @Test void actionsRunReadAndExpire() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID id = center.post(request(CARDS, "act")
            .withAction(NotificationAction.of("Fix", () -> ran.add("fix")))
            .withAction(new NotificationAction("Keep", () -> ran.add("keep"), false))).orElseThrow();
        center.runAction(id, 1);
        assertThat(ran).containsExactly("keep");
        assertThat(center.find(id).orElseThrow()).satisfies(entry -> { assertThat(entry.read()).isTrue(); assertThat(entry.expired()).isFalse(); });
        center.runAction(id, 0);
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
        center.runAction(id, 0);
        center.runAction(id, 1);
        assertThat(ran).as("an expired entry's actions no longer run").containsExactly("keep", "fix");
    }

    @Test void aFailingActionIsContainedAndStillExpires() {
        var center = center();
        UUID id = center.post(request(CARDS, "boom").withAction(NotificationAction.of("Boom", () -> { throw new IllegalStateException("boom"); })))
            .orElseThrow();
        assertThatCode(() -> center.runAction(id, 0)).doesNotThrowAnyException();
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
    }

    @Test void activateReadsTheEntryAndRunsItsDefaultActionWhenThereIsOne() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID withDefault = center.post(request(CARDS, "go").withActivate(() -> ran.add("go"))).orElseThrow();
        UUID plain = center.post(request(CARDS, "plain")).orElseThrow();
        center.activate(withDefault);
        center.activate(plain);
        assertThat(ran).containsExactly("go");
        assertThat(center.find(withDefault).orElseThrow().read()).isTrue();
        assertThat(center.find(plain).orElseThrow().read()).as("clicking the body marks it read").isTrue();
    }

    @Test void removeAndClear() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.post(request(CARDS, "b"));
        center.remove(a);
        assertThat(changes).contains(new NotificationCenter.Change.Removed(a));
        center.clear();
        assertThat(center.history()).isEmpty();
        assertThat(changes.getLast()).isEqualTo(new NotificationCenter.Change.Cleared());
        int before = changes.size();
        center.clear();
        assertThat(changes).hasSize(before);
    }

    @Test void aListenersChangeArrivesAfterTheOneItReactsToAndNoCardShowsForAReadEntry() {
        var center = center();
        center.onChange(change -> {
            if (change instanceof NotificationCenter.Change.Upserted upserted && !upserted.notification().read())
                center.markRead(upserted.notification().id());
        });
        center.post(request(CARDS, "seen at once"));
        assertThat(changes).extracting(change -> change instanceof NotificationCenter.Change.Upserted upserted
            ? "Upserted read=" + upserted.notification().read() : change.getClass().getSimpleName())
            .containsExactly("Upserted read=false", "Upserted read=true");
    }

    @Test void theCenterRefusesOtherThreads() throws Exception {
        var center = center();
        var thrown = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { center.history(); } catch (Throwable failure) { thrown.set(failure); } });
        worker.start();
        worker.join();
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }

    @Test void aRepeatCountsReStampsMarksUnreadAndMovesToTheTop() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        center.post(request(CARDS, "b"));
        center.markRead(a);
        clock.advance(Duration.ofMinutes(5));
        changes.clear();
        assertThat(center.repeat(a, false)).isTrue();
        Notification entry = center.find(a).orElseThrow();
        assertThat(entry.count()).isEqualTo(2);
        assertThat(entry.read()).isFalse();
        assertThat(entry.posted()).isEqualTo(Instant.parse("2026-10-02T14:10:00Z"));
        assertThat(center.history().getFirst().id()).isEqualTo(a);
        assertThat(kinds()).as("no card unless asked").containsExactly("Upserted");
    }

    @Test void aRepeatShowsItsCardWhenAskedAndItsGroupRoutesOne() {
        var center = center();
        UUID card = center.post(request(CARDS, "card")).orElseThrow();
        UUID quiet = center.post(request(QUIET, "quiet")).orElseThrow();
        changes.clear();
        center.repeat(card, true);
        center.repeat(quiet, true);
        assertThat(kinds()).containsExactly("Upserted", "CardRequested", "Upserted");
        presence = Delivery.Presence.BACKGROUND;
        UUID sticky = center.post(request(STICKY, "sticky")).orElseThrow();
        center.repeat(sticky, true);
        changes.clear();
        presence = Delivery.Presence.FOCUSED;
        center.focusReturned();
        assertThat(kinds()).as("a sticky repeat in the background waits for focus").containsExactly("CardRequested");
        assertThat(((NotificationCenter.Change.CardRequested) changes.getFirst()).notification().count()).isEqualTo(2);
    }

    @Test void aRepeatCanCountSeveralSightingsAtOnce() {
        var center = center();
        UUID a = center.post(request(CARDS, "a")).orElseThrow();
        changes.clear();
        assertThat(center.repeat(a, false, 9_999)).isTrue();
        assertThat(center.find(a).orElseThrow().count()).isEqualTo(10_000);
        assertThat(kinds()).as("one update for them all").containsExactly("Upserted");
        assertThatThrownBy(() -> center.repeat(a, false, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(center.repeat(a, true)).as("the short form counts one").isTrue();
        assertThat(center.find(a).orElseThrow().count()).isEqualTo(10_001);
    }

    @Test void aRepeatOfAGoneOrExpiredEntryIsRefused() {
        var center = center();
        UUID expired = center.post(request(CARDS, "expired")).orElseThrow();
        UUID removed = center.post(request(CARDS, "removed")).orElseThrow();
        center.expire(expired);
        center.remove(removed);
        changes.clear();
        assertThat(center.repeat(expired, true)).isFalse();
        assertThat(center.repeat(removed, true)).isFalse();
        assertThat(center.repeat(UUID.randomUUID(), true)).isFalse();
        assertThat(changes).isEmpty();
    }

    @Test void aFailingActionOrDefaultActionReachesTheFailureSink() {
        var center = center();
        var failures = new ArrayList<Throwable>();
        center.reportActionFailuresTo(failures::add);
        var action = new IllegalStateException("action broke");
        var activation = new IllegalArgumentException("activation broke");
        UUID id = center.post(request(CARDS, "boom")
            .withAction(new NotificationAction("Boom", () -> { throw action; }, false))
            .withActivate(() -> { throw activation; })).orElseThrow();
        center.runAction(id, 0);
        center.activate(id);
        assertThat(failures).containsExactly(action, activation);
    }

    @Test void aFailureSinkThatThrowsIsContained() {
        var center = center();
        center.reportActionFailuresTo(failure -> { throw new IllegalStateException("sink broke"); });
        UUID id = center.post(request(CARDS, "boom").withAction(NotificationAction.of("Boom", () -> { throw new IllegalStateException("boom"); })))
            .orElseThrow();
        assertThatCode(() -> center.runAction(id, 0)).doesNotThrowAnyException();
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
    }

    @Test void aRepeatCarriesTheNewestPostsActionsDefaultActionAndTarget() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        UUID id = center.post(request(CARDS, "same").withTarget(first).withActivate(() -> ran.add("first body"))
            .withAction(NotificationAction.of("Open first", () -> ran.add("first")))).orElseThrow();
        clock.advance(Duration.ofSeconds(5));
        assertThat(center.post(request(CARDS, "same").withTarget(second).withActivate(() -> ran.add("second body"))
            .withAction(NotificationAction.of("Open second", () -> ran.add("second"))))).contains(id);
        Notification entry = center.find(id).orElseThrow();
        assertThat(entry.count()).isEqualTo(2);
        assertThat(entry.request().target()).contains(second);
        assertThat(entry.request().actions()).extracting(NotificationAction::label).containsExactly("Open second");
        center.activate(id);
        center.runAction(id, 0);
        assertThat(ran).containsExactly("second body", "second");
    }

    @Test void whatAnExpiringActionPostsIsNotFoldedIntoTheEntryItEnds() {
        var center = center();
        var posted = new ArrayList<UUID>();
        var retry = new AtomicReference<Runnable>();
        UUID id = center.post(request(CARDS, "Could not copy").withAction(NotificationAction.of("Retry", () -> retry.get().run())))
            .orElseThrow();
        retry.set(() -> center.post(request(CARDS, "Could not copy")).ifPresent(posted::add));
        center.runAction(id, 0);
        assertThat(center.find(id).orElseThrow().expired()).isTrue();
        assertThat(posted).singleElement().isNotEqualTo(id);
        assertThat(center.find(posted.getFirst()).orElseThrow()).satisfies(entry -> {
            assertThat(entry.expired()).isFalse();
            assertThat(entry.count()).isEqualTo(1);
        });
    }

    @Test void presenceIsWhatDeliverySees() {
        var center = center();
        assertThat(center.presence()).isEqualTo(Delivery.Presence.FOCUSED);
        presence = Delivery.Presence.BACKGROUND;
        assertThat(center.presence()).isEqualTo(Delivery.Presence.BACKGROUND);
    }

    /**
     * History keeps an expired entry's text and action labels, never its poster's callbacks: those capture the poster
     * (a closed pane and its window, a stopped plugin), which must not stay reachable for as long as the entry does.
     */
    @Test void anExpiredEntryKeepsItsLabelsButLetsGoOfItsPostersCallbacks() {
        var center = center();
        var ran = new ArrayList<String>();
        UUID id = center.post(request(CARDS, "built").withActivate(() -> ran.add("activate"))
            .withAction(NotificationAction.of("Open", () -> ran.add("open")))).orElseThrow();
        center.expire(id);
        NotificationRequest kept = center.find(id).orElseThrow().request();
        assertThat(kept.title()).isEqualTo("built");
        assertThat(kept.actions()).extracting(NotificationAction::label).containsExactly("Open");
        assertThat(kept.activate()).isEmpty();
        kept.actions().getFirst().run().run();
        assertThat(ran).isEmpty();
    }

    @Test void inTheBackgroundCardAndStickyGroupsAskTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.post(request(CARDS, "c"));
        center.post(request(STICKY, "s"));
        center.post(request(QUIET, "q"));
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "Upserted", "SystemRequested", "Upserted");
    }

    @Test void withNoWindowOpenTheOperatingSystemHearsAndTheCardStillWaits() {
        var center = center();
        presence = Delivery.Presence.NO_WINDOW;
        center.post(request(CARDS, "c"));
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested");
        center.focusReturned();
        assertThat(kinds()).containsExactly("Upserted", "SystemRequested", "CardRequested");
    }

    @Test void whileJasperOpensNothingGoesToTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.OPENING;
        center.post(request(CARDS, "c"));
        center.post(request(STICKY, "s"));
        assertThat(kinds()).containsExactly("Upserted", "Upserted");
    }

    /** A listener that reads the entry as it is posted must not leave the operating system showing it. */
    @Test void anEntryReadBeforeItsRequestIsDeliveredIsNotSentToTheOperatingSystem() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        center.onChange(change -> {
            if (change instanceof NotificationCenter.Change.Upserted upserted && !upserted.notification().read())
                center.markRead(upserted.notification().id());
        });
        center.post(request(CARDS, "c"));
        assertThat(kinds()).containsExactly("Upserted", "Upserted");
    }

    @Test void aRepeatInTheBackgroundAsksTheOperatingSystemAgainForTheSameEntry() {
        var center = center();
        presence = Delivery.Presence.BACKGROUND;
        UUID first = center.post(request(CARDS, "c")).orElseThrow();
        assertThat(center.post(request(CARDS, "c"))).contains(first);
        assertThat(changes).filteredOn(NotificationCenter.Change.SystemRequested.class::isInstance)
            .extracting(change -> ((NotificationCenter.Change.SystemRequested) change).notification().id())
            .containsExactly(first, first);
    }
}
