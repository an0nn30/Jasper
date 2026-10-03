package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class IncidentsTest {
    /** A clock a test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-02T14:05:00Z");
        void advance(Duration by) { now = now.plus(by); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final List<Incidents.Change> changes = new ArrayList<>();

    private Incidents incidents() {
        var incidents = new Incidents(clock);
        incidents.onChange(changes::add);
        return incidents;
    }

    private static Capture capture(String type, String message, String signature) {
        return new Capture(type, message, type + ": " + message + "\n\tat somewhere", signature);
    }

    private List<String> kinds() { return changes.stream().map(change -> change.getClass().getSimpleName()).toList(); }

    @Test void aNewFailureIsAnIncidentAndTheNewestComesFirst() {
        var incidents = incidents();
        Incident first = incidents.record("jasper", "thread main", "main", capture("java.lang.IllegalStateException", "first", "a"));
        clock.advance(Duration.ofSeconds(1));
        Incident second = incidents.record("dev.example.tool", "action tool.run", "AWT-EventQueue-0",
            capture("java.io.UncheckedIOException", "second", "b"));
        assertThat(incidents.list()).extracting(Incident::id).containsExactly(second.id(), first.id());
        assertThat(first.source()).isEqualTo("jasper");
        assertThat(first.operation()).isEqualTo("thread main");
        assertThat(first.thread()).isEqualTo("main");
        assertThat(first.count()).isEqualTo(1);
        assertThat(first.firstSeen()).isEqualTo(first.lastSeen()).isEqualTo(Instant.parse("2026-10-02T14:05:00Z"));
        assertThat(first.fromPlugin()).isFalse();
        assertThat(second.fromPlugin()).isTrue();
        assertThat(kinds()).containsExactly("Added", "Added");
        assertThat(incidents.find(first.id())).contains(first);
    }

    @Test void aRepeatCountsKeepsTheFirstTraceAndComesFirstAgain() {
        var incidents = incidents();
        Incident first = incidents.record("jasper", "thread main", "main", capture("java.lang.IllegalStateException", "first message", "same"));
        incidents.record("jasper", "thread other", "other", capture("java.lang.IllegalArgumentException", "unrelated", "else"));
        clock.advance(Duration.ofMinutes(3));
        Incident again = incidents.record("jasper", "thread worker", "worker",
            capture("java.lang.IllegalStateException", "a different message", "same"));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.count()).isEqualTo(2);
        assertThat(again.firstSeen()).isEqualTo(first.firstSeen());
        assertThat(again.lastSeen()).isEqualTo(first.firstSeen().plus(Duration.ofMinutes(3)));
        assertThat(again.message()).isEqualTo("first message");
        assertThat(again.trace()).isEqualTo(first.trace());
        assertThat(again.operation()).isEqualTo("thread main");
        assertThat(incidents.list().getFirst()).isEqualTo(again);
        assertThat(changes.getLast()).isEqualTo(new Incidents.Change.Repeated(again));
    }

    @Test void oneRecordCanCountSeveralSightings() {
        var incidents = incidents();
        Incident first = incidents.record("jasper", "thread worker", "worker", capture("x.Boom", "m", "same"), 3);
        assertThat(first.count()).isEqualTo(3);
        assertThat(first.firstSeen()).isEqualTo(first.lastSeen());
        clock.advance(Duration.ofSeconds(1));
        Incident again = incidents.record("jasper", "thread worker", "worker", capture("x.Boom", "m", "same"), 4);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.count()).isEqualTo(7);
        assertThat(again.lastSeen()).isEqualTo(first.firstSeen().plusSeconds(1));
        assertThat(incidents.record("jasper", "a", "t", capture("x.Boom", "m", "same")).count()).as("the short form counts one").isEqualTo(8);
        assertThatThrownBy(() -> incidents.record("jasper", "a", "t", capture("x.Boom", "m", "same"), 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(incidents.list()).singleElement().satisfies(incident -> assertThat(incident.count()).isEqualTo(8));
        assertThat(Incidents.key("jasper", capture("x.Boom", "m", "same")))
            .as("the key callers coalesce by is the store's")
            .isEqualTo(Incidents.key("jasper", capture("x.Boom", "other message", "same")))
            .isNotEqualTo(Incidents.key("dev.example.tool", capture("x.Boom", "m", "same")))
            .isNotEqualTo(Incidents.key("jasper", capture("x.Other", "m", "same")))
            .isNotEqualTo(Incidents.key("jasper", capture("x.Boom", "m", "else")));
    }

    @Test void theSameFailureFromAnotherSourceOrOfAnotherClassIsAnotherIncident() {
        var incidents = incidents();
        incidents.record("jasper", "a", "t", capture("java.lang.IllegalStateException", "m", "same"));
        incidents.record("dev.example.tool", "a", "t", capture("java.lang.IllegalStateException", "m", "same"));
        incidents.record("jasper", "a", "t", capture("java.lang.IllegalArgumentException", "m", "same"));
        assertThat(incidents.list()).hasSize(3).allSatisfy(incident -> assertThat(incident.count()).isEqualTo(1));
    }

    @Test void onlyTheNewest100AreKept() {
        var incidents = incidents();
        Incident oldest = incidents.record("jasper", "a", "t", capture("x.Boom0", "m", "s"));
        for (int i = 1; i <= Incidents.LIMIT; i++) incidents.record("jasper", "a", "t", capture("x.Boom" + i, "m", "s"));
        assertThat(incidents.list()).hasSize(Incidents.LIMIT).extracting(Incident::exceptionClass).doesNotContain("x.Boom0");
        assertThat(incidents.find(oldest.id())).isEmpty();
        assertThat(changes.getLast()).isEqualTo(new Incidents.Change.Removed(oldest.id()));
        assertThat(kinds().subList(kinds().size() - 2, kinds().size())).as("the dropped one goes after the new one is added")
            .containsExactly("Added", "Removed");
    }

    @Test void clearEmptiesTheListOnce() {
        var incidents = incidents();
        incidents.record("jasper", "a", "t", capture("x.Boom", "m", "s"));
        incidents.clear();
        incidents.clear();
        assertThat(incidents.list()).isEmpty();
        assertThat(kinds()).containsExactly("Added", "Cleared");
    }

    @Test void aFailingListenerStopsNeitherTheOthersNorTheRecord() {
        var incidents = new Incidents(clock);
        incidents.onChange(change -> { throw new IllegalStateException("listener broke"); });
        incidents.onChange(changes::add);
        assertThatCode(() -> incidents.record("jasper", "a", "t", capture("x.Boom", "m", "s"))).doesNotThrowAnyException();
        assertThat(kinds()).containsExactly("Added");
    }

    @Test void summariesAreOneShortLine() {
        var incidents = incidents();
        Incident incident = incidents.record("jasper", "a", "t", new Capture("java.lang.IllegalStateException", "first line\nsecond line", "trace", "s1"));
        assertThat(incident.simpleName()).isEqualTo("IllegalStateException");
        assertThat(incident.firstLine()).isEqualTo("first line");
        assertThat(incident.summary()).isEqualTo("IllegalStateException: first line");
        Incident bare = incidents.record("jasper", "a", "t", new Capture("Boom", "", "trace", "s2"));
        assertThat(bare.summary()).isEqualTo("Boom");
        Incident wide = incidents.record("jasper", "a", "t", new Capture("x.Wide", "w".repeat(500), "trace", "s3"));
        assertThat(wide.summary()).hasSize(Incident.SUMMARY_LIMIT).endsWith(NotificationText.ELLIPSIS);
    }

    @Test void incidentsBelongToTheEdt() throws Exception {
        var incidents = incidents();
        var thrown = new AtomicReference<Throwable>();
        var worker = new Thread(() -> { try { incidents.list(); } catch (Throwable failure) { thrown.set(failure); } });
        worker.start();
        worker.join();
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
    }
}
