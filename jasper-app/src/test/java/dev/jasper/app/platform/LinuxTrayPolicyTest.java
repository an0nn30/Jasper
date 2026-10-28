package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinuxTrayPolicyTest {
    @Test void modernWinsAndNoHostIsAnOrdinaryState() {
        assertThat(LinuxTrayPolicy.target(true, true)).isEqualTo(LinuxTrayPolicy.Target.MODERN);
        assertThat(LinuxTrayPolicy.target(true, false)).isEqualTo(LinuxTrayPolicy.Target.MODERN);
        assertThat(LinuxTrayPolicy.target(false, true)).isEqualTo(LinuxTrayPolicy.Target.LEGACY);
        assertThat(LinuxTrayPolicy.target(false, false)).isEqualTo(LinuxTrayPolicy.Target.NONE);
        assertThat(java.util.stream.IntStream.range(0, 8).map(LinuxTrayPolicy::retrySeconds).toArray())
            .containsExactly(1, 2, 4, 8, 16, 30, 30, 30);
    }
}
