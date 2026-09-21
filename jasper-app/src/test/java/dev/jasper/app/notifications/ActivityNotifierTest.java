package dev.jasper.app.notifications;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ActivityNotifierTest {
    private final BuddyTestSupport deck = new BuddyTestSupport();
    private final UUID id = UUID.randomUUID();

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
            assertThat(notice.live()).isTrue();
        });
    }

    @Test void theOutcomeReplacesTheCardAndCancellationRemovesIt() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        UUID other = UUID.randomUUID();
        notifier.started("p.one", id, "Upload", "");
        notifier.started("p.one", other, "Download", "");
        notifier.finished("p.one", id, "Upload", false, "connection reset");
        notifier.cancelled("p.one", other);
        assertThat(deck.notices()).singleElement().satisfies(notice -> {
            assertThat(notice.state()).isEqualTo(BuddyNotice.State.FAILED);
            assertThat(notice.detail().get()).isEqualTo("connection reset");
        });
        notifier.finished("p.one", UUID.randomUUID(), "Never started", true, "done");
        assertThat(deck.notices()).as("an outcome with no running card still records the result").hasSize(2);
    }

    @Test void nothingIsPostedAfterClose() {
        var notifier = new ActivityNotifier(deck.companion(), () -> { });
        notifier.close();
        notifier.close();
        notifier.started("p.one", id, "Upload", "");
        notifier.progress("p.one", id, "x");
        notifier.finished("p.one", id, "Upload", true, "done");
        notifier.cancelled("p.one", id);
        assertThat(deck.notices()).isEmpty();
    }
}
