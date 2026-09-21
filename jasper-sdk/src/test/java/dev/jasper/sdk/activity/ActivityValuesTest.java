package dev.jasper.sdk.activity;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ActivityValuesTest {
    @Test void specRequiresATitleAndNonNullParts() {
        assertThat(ActivitySpec.of("Upload").detail()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ActivitySpec.of(" "));
        assertThatNullPointerException().isThrownBy(() -> new ActivitySpec("t", null, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ActivitySpec("t", "", null, Optional.empty()));
    }

    @Test void eventValidatesFractionAndKnowsTerminalStates() {
        UUID id = UUID.randomUUID();
        var progress = new ActivityEvent(id, "a.b", "Upload", ActivityEvent.State.PROGRESS,
            OptionalDouble.of(0.5), "half", Optional.empty());
        assertThat(progress.terminal()).isFalse();
        assertThat(new ActivityEvent(id, "a.b", "Upload", ActivityEvent.State.FAILED,
            OptionalDouble.empty(), "no", Optional.empty()).terminal()).isTrue();
        for (double bad : new double[]{-0.1, 1.1, Double.NaN})
            assertThatIllegalArgumentException().isThrownBy(() -> new ActivityEvent(id, "a.b", "Upload",
                ActivityEvent.State.PROGRESS, OptionalDouble.of(bad), "", Optional.empty()));
    }

    @Test void topicLivesInTheApplicationNamespace() {
        assertThat(Activities.TOPIC.id()).isEqualTo("jasper.activity");
        assertThat(Activities.TOPIC.payloadType()).isEqualTo(ActivityEvent.class);
    }
}
