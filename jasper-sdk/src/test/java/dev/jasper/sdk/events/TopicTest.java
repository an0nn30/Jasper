package dev.jasper.sdk.events;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TopicTest {
    record Ping(int n) {}

    @Test void equalityUsesIdAndPayloadType() {
        assertThat(Topic.of("a.b.ping", Ping.class)).isEqualTo(Topic.of("a.b.ping", Ping.class))
            .hasSameHashCodeAs(Topic.of("a.b.ping", Ping.class))
            .isNotEqualTo(Topic.of("a.b.ping", String.class))
            .isNotEqualTo(Topic.of("a.b.pong", Ping.class));
        assertThat(Topic.of("a.b.ping", Ping.class).toString()).contains("a.b.ping");
    }

    @Test void rejectsMalformedIdsAndNulls() {
        for (String id : new String[]{"", "Upper.case", "9start", "sp ace"})
            assertThatIllegalArgumentException().as(id).isThrownBy(() -> Topic.of(id, Ping.class));
        assertThatNullPointerException().isThrownBy(() -> Topic.of(null, Ping.class));
        assertThatNullPointerException().isThrownBy(() -> Topic.of("a.b", null));
    }
}
