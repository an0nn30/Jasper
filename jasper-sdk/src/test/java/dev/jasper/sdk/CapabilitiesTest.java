package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionElevenWithTheNetworkListenCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.16.0");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.ALL).endsWith(Capabilities.NETWORK_LISTEN);
    }
}
