package dev.jasper.buddy.notice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BuddyNoticeTest {
    private static BuddyNotice of(BuddyNotice.Kind kind, BuddyNotice.State state) {
        return new BuddyNotice(new BuddyNoticeId("terminal", "k"), kind, "title", state, () -> "d", () -> { });
    }

    /** A tunnel never completes and a command is never "up"; an impossible notice cannot be built. */
    @Test void eachKindAcceptsOnlyItsOwnStates() {
        for (BuddyNotice.State state : BuddyNotice.State.values()) {
            boolean task = switch (state) {
                case RUNNING, NEEDS_INPUT -> true;
                case UP, DEGRADED, DOWN -> false;
            };
            assertThat(state.fits(BuddyNotice.Kind.TASK)).as("%s as a task", state).isEqualTo(task);
            assertThat(state.fits(BuddyNotice.Kind.CONNECTION)).as("%s as a connection", state).isEqualTo(!task);
        }
    }

    /** Buddy keeps live work only: work that ends is dismissed, and its outcome is a notification. */
    @Test void thereAreNoOutcomeStates() {
        assertThat(BuddyNotice.State.values()).extracting(Enum::name)
            .containsExactly("RUNNING", "NEEDS_INPUT", "UP", "DEGRADED", "DOWN");
    }

    @Test void aNoticeWhoseStateDoesNotFitItsKindIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> of(BuddyNotice.Kind.TASK, BuddyNotice.State.UP));
        assertThatIllegalArgumentException().isThrownBy(() -> of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.RUNNING));
    }

    /** Wants you specifically: it is waiting on you, or it is in trouble. */
    @Test void attentionIsInputOrTrouble() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.UP).wantsAttention()).isFalse();
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.NEEDS_INPUT).wantsAttention()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DEGRADED).wantsAttention()).isTrue();
        assertThat(of(BuddyNotice.Kind.CONNECTION, BuddyNotice.State.DOWN).wantsAttention()).isTrue();
    }

    /** A card always has somewhere to go: a producer whose origin is gone dismisses it instead. */
    @Test void aNoticeNeedsAnActivation() {
        assertThatNullPointerException().isThrownBy(() -> new BuddyNotice(new BuddyNoticeId("terminal", "k"),
            BuddyNotice.Kind.TASK, "sleep 600", BuddyNotice.State.RUNNING, () -> "d", null));
    }
    @Test void oldConstructorIsUngroupedAndGroupsValidateTheirValues() {
        assertThat(of(BuddyNotice.Kind.TASK, BuddyNotice.State.RUNNING).group()).isNull();
        var id = new BuddyNoticeId("tab", 1);
        assertThat(new BuddyNotice.Group(id, "Claude").id()).isEqualTo(id);
        assertThatNullPointerException().isThrownBy(() -> new BuddyNotice.Group(null, "title"));
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyNotice.Group(id, null));
        assertThatIllegalArgumentException().isThrownBy(() -> new BuddyNotice.Group(id, "  "));
    }
}
