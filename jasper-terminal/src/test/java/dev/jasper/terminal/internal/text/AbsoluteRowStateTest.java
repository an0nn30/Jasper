package dev.jasper.terminal.internal.text;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AbsoluteRowStateTest {
    @Test void discardsPrunePromptsWhileInvalidationOnlyChangesIdentity() {
        var state = new AbsoluteRowState();
        assertThat(state.recordPrompt(1)).isTrue();
        assertThat(state.recordPrompt(1)).isFalse();
        assertThat(state.recordPrompt(3)).isTrue();
        var before = state.prompts();
        state.discard(2);
        assertThat(state.discarded()).isEqualTo(2);
        assertThat(state.prompts()).containsExactly(3L);
        assertThat(before).containsExactly(1L, 3L);
        state.invalidate();
        assertThat(state.epoch()).isEqualTo(1);
        assertThat(state.prompts()).containsExactly(3L);
        state.clearPrompts();
        assertThat(state.prompts()).isEmpty();
    }
}
