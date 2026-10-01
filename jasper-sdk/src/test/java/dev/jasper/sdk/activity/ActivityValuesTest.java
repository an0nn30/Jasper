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

    @Test void connectionSpecNeedsATitleAndAnOpenFirstState() {
        assertThat(ConnectionSpec.of("Tunnels via db", ConnectionEvent.State.DEGRADED).detail()).isEmpty();
        assertThat(ConnectionSpec.of("Tunnels via db", ConnectionEvent.State.DEGRADED).activateActionId()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> ConnectionSpec.of(" ", ConnectionEvent.State.UP));
        assertThatIllegalArgumentException().isThrownBy(() -> ConnectionSpec.of("t", ConnectionEvent.State.CLOSED));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", null, ConnectionEvent.State.UP, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", "", null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionSpec("t", "", ConnectionEvent.State.UP, null));
    }

    @Test void connectionEventsRejectNullPartsAndTravelOnTheirOwnTopic() {
        UUID id = UUID.randomUUID();
        assertThatNullPointerException().isThrownBy(() -> new ConnectionEvent(id, "a.b", "t", null, "", Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new ConnectionEvent(id, "a.b", "t", ConnectionEvent.State.UP, null, Optional.empty()));
        assertThat(Activities.CONNECTIONS.id()).isEqualTo("jasper.connection");
        assertThat(Activities.CONNECTIONS.payloadType()).isEqualTo(ConnectionEvent.class);
        assertThat(ConnectionEvent.State.values()).containsExactly(ConnectionEvent.State.UP, ConnectionEvent.State.DEGRADED,
            ConnectionEvent.State.DOWN, ConnectionEvent.State.CLOSED);
    }
}
