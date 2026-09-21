package dev.jasper.buddy.notice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import dev.jasper.buddy.notice.BuddyNotice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyNoticeTest {
    private static BuddyNotice of(BuddyNotice.Kind kind, BuddyNotice.State state) {
        return new BuddyNotice(new BuddyNoticeId("terminal", "k"), kind, "title", state, () -> "d", () -> { });
    }

    /** A tunnel never completes and a command is never "up"; an impossible notice cannot be built. */
    @Test void eachKindAcceptsOnlyItsOwnStates() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            boolean task = switch (state) {
                case RUNNING, NEEDS_INPUT, DONE, FAILED -> true;
                case UP, DEGRADED, DOWN -> false;
            };
            assertThat(state.fits(BuddyNotice.Kind.TASK)).as("%s as a task", state).isEqualTo(task);
            assertThat(state.fits(BuddyNotice.Kind.CONNECTION)).as("%s as a connection", state).isEqualTo(!task);
        }
    }

    @Test void aNoticeWhoseStateDoesNotFitItsKindIsRefused() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> of(BuddyNotice.Kind.TASK, BuddyNotice.State.UP));
        assertThatIllegalArgumentException()
            .isThrownBy(() -> of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DONE));
    }

    /** Still happening, so it belongs above his head whether or not you have seen it. */
    @Test void liveIsTheThingsStillHappening() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.NEEDS_INPUT).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).live()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DEGRADED).live()).isTrue();

        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.DONE).live()).isFalse();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.FAILED).live()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DOWN).live()).isFalse();
    }

    /** Wants you specifically: it ended, it broke, or it is waiting on you. */
    @Test void attentionIsEverythingExceptQuietProgress() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).wantsAttention()).isFalse();

        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            if (state == BuddyNotice.State.RUNNING || state == BuddyNotice.State.UP) continue;
            BuddyNotice.Kind kind = state.fits(BuddyNotice.Kind.TASK)
                ? BuddyNotice.Kind.TASK : BuddyNotice.Kind.CONNECTION;
            assertThat(of(kind, state).wantsAttention()).as("%s", state).isTrue();
        }
    }

    /** Nothing is still happening once its origin is gone, whatever state it was left in. */
    @Test void anOrphanIsNeverLiveEvenIfItWasLeftRunning() {
        BuddyNotice orphan = new BuddyNotice(new BuddyNoticeId("terminal", "k"), BuddyNotice.Kind.TASK, "sleep 600", BuddyNotice.State.RUNNING, () -> "d", null);

        assertThat(orphan.orphaned()).isTrue();
        assertThat(orphan.live()).isFalse();
        assertThat(orphan.state()).as("but it still says what it was doing")
            .isEqualTo(BuddyNotice.State.RUNNING);
    }
}
