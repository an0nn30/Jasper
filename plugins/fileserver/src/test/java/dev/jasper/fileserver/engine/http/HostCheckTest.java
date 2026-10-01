package dev.jasper.fileserver.engine.http;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HostCheckTest {
    @Test void aLoopbackServerAnswersOnlyLoopbackNames() {
        var check = new HostCheck(Set.of("localhost"), true);
        assertThat(check.allows("127.0.0.1:8080")).isTrue();
        assertThat(check.allows("localhost:8080")).isTrue();
        assertThat(check.allows("[::1]:8080")).isTrue();
        assertThat(check.allows("192.168.1.20:8080")).isFalse();
        assertThat(check.allows("evil.example:8080")).isFalse();
        assertThat(check.allows(null)).isFalse();
    }

    @Test void aNetworkServerAnswersAddressesAndThisComputersNames() {
        var check = new HostCheck(Set.of("localhost", "studio", "studio.local"), false);
        assertThat(check.allows("192.168.1.20:8080")).isTrue();
        assertThat(check.allows("[2001:db8::5]:8080")).isTrue();
        assertThat(check.allows("Studio.local")).isTrue();
        assertThat(check.allows("evil.example")).isFalse();
        assertThat(check.allows("999.1.1.1")).isFalse();
    }
}
