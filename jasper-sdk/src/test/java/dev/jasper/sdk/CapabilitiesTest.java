package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionSixteenOneWithTheClipboardReadCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.17.0");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.CLIPBOARD_READ).isEqualTo("clipboard.read");
        assertThat(Capabilities.ALL).endsWith(Capabilities.NETWORK_LISTEN, Capabilities.CLIPBOARD_READ);
    }
}
