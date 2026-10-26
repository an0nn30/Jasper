package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ActivityNotifierTest {
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final UUID id = UUID.randomUUID();

    @Test void inputAndResumeKeepDismissalAndTrackAllRunningTasks() {
        var changes = new java.util.ArrayList<Boolean>();
        var notifier = new ActivityNotifier(deck.companion(), () -> {}, changes::add);
        UUID other = UUID.randomUUID();
        notifier.started("p", id, "first", "");
        notifier.started("p", other, "second", "");
        notifier.needsInput("p", id, "choose");
        assertThat(deck.notices()).filteredOn(n -> n.id().key().equals(id)).singleElement()
            .satisfies(n -> { assertThat(n.state()).isEqualTo(BuddyNotice.State.NEEDS_INPUT); assertThat(n.detail().get()).isEqualTo("choose"); });
        notifier.needsInput("p", other, "choose too");
        deck.companion().dismiss(new BuddyNoticeId("p", id));
        notifier.resumed("p", id, "working"); notifier.resumed("p", id, "still working");
        assertThat(deck.notices()).hasSize(1);
        notifier.ended("p", id); notifier.resumed("p", id, "late");
        notifier.connection("p", UUID.randomUUID(), "connected", ActivityNotifier.ConnectionState.UP, "", Optional.empty());
        assertThat(changes).containsExactly(true, false, true, false);
        notifier.resumed("p", other, "working"); notifier.close(); notifier.resumed("p", other, "late");
        assertThat(changes).containsExactly(true, false, true, false, true, false);
    }

    @Test void aRunningActivityIsOneCardWhoseDetailFollowsProgress() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.started("dev.jasper.sftp", id, "Upload\nsite.tar", "starting");
        notifier.progress("dev.jasper.sftp", id, "40% · 4 of 10 MB");
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(new BuddyNoticeId("dev.jasper.sftp", id));
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.TASK);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.RUNNING);
            assertThat(notice.title()).isEqualTo("Upload site.tar");
            assertThat(notice.detail().get()).isEqualTo("40% · 4 of 10 MB");
        });
    }

    /** The outcome is a notification the plugin host posts; Buddy keeps live work only. */
    @Test void anActivityThatEndsLeavesNoCardWhateverItsOutcome() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        UUID other = UUID.randomUUID();
        notifier.started("p.one", id, "Upload", "");
        notifier.started("p.one", other, "Download", "");
        notifier.ended("p.one", id);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("Download");
        notifier.ended("p.one", other);
        notifier.ended("p.one", UUID.randomUUID());
        assertThat(deck.notices()).isEmpty();
        notifier.progress("p.one", id, "late");
        assertThat(deck.notices()).as("progress after the end brings nothing back").isEmpty();
    }

    @Test void aConnectionIsOneCardWhoseNewStateIsNewsAndWhoseDetailIsNot() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        var key = new BuddyNoticeId("dev.jasper.remote", id);
        notifier.connection("dev.jasper.remote", id, "Tunnels via\ndb", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 1 s", Optional.empty());
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.id()).isEqualTo(key);
            assertThat(notice.kind()).isEqualTo(BuddyNotice.Kind.CONNECTION);
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DEGRADED);
            assertThat(notice.title()).isEqualTo("Tunnels via db");
        });
        int generation = deck.generation();
        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DEGRADED,
            "reconnecting in 2 s", Optional.empty());
        assertThat(deck.generation()).as("the same state is not new news").isEqualTo(generation);
        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("reconnecting in 2 s");

        notifier.connection("dev.jasper.remote", id, "Tunnels via db", ActivityNotifier.ConnectionState.DOWN, "gave up", Optional.empty());
        assertThat(deck.generation()).as("a new state is").isGreaterThan(generation);
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.DOWN);
            assertThat(notice.detail().get()).isEqualTo("gave up");
        });

        notifier.connectionClosed("dev.jasper.remote", id);
        assertThat(deck.notices()).isEmpty();
    }

    @Test void aClickRaisesTheApplicationAndRunsTheProducersAction() {
        var raised = new AtomicInteger();
        var ran = new AtomicInteger();
        var notifier = new ActivityNotifier(deck.companion(), raised::incrementAndGet);
        UUID other = UUID.randomUUID();
        notifier.connection("p.one", id, "Tunnels", ActivityNotifier.ConnectionState.DOWN, "gave up",
            Optional.<Runnable>of(ran::incrementAndGet));
        notifier.started("p.one", other, "Upload", "");
        var byId = deck.notices().stream().collect(Collectors.toMap(BuddyNotice::id, notice -> notice));

        byId.get(new BuddyNoticeId("p.one", id)).activate().run();
        assertThat(raised).hasValue(1);
        assertThat(ran).hasValue(1);

        byId.get(new BuddyNoticeId("p.one", other)).activate().run();
        assertThat(raised).hasValue(2);
        assertThat(ran).as("a card without an action only raises").hasValue(1);
        assertThat(deck.notices()).as("a click removes nothing: the work goes on").hasSize(2);
    }

    @Test void nothingIsPostedAfterClose() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.close();
        notifier.close();
        notifier.started("p.one", id, "Upload", "");
        notifier.progress("p.one", id, "x");
        notifier.ended("p.one", id);
        notifier.connection("p.one", id, "Tunnel", ActivityNotifier.ConnectionState.UP, "", Optional.empty());
        notifier.connectionClosed("p.one", id);
        assertThat(deck.notices()).isEmpty();
    }
}
