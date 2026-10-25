package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class DesktopPortalTest {
    @Test void missingPreferencesRemainIndependent() {
        String wire = "{\"v\":1,\"kind\":\"portal\",\"owner\":4,\"appearance\":\"unknown\",\"accent\":\"0.2,0.4,0.6,1\",\"reason\":\"\"}";
        var message = (DesktopProtocol.PortalMessage)
            DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8));
        assertThat(message.value().appearance()).isEqualTo(DesktopObservation.Polarity.UNKNOWN);
        assertThat(message.value().accent()).isPresent();
        assertThat(message.value().highContrast()).isEmpty();
    }

    @Test void invalidAccentRetainsAppearanceAndContrast() {
        String wire = "{\"v\":1,\"kind\":\"portal\",\"owner\":5,\"appearance\":\"light\",\"accent\":\"NaN,0,0,1\",\"highContrast\":false,\"reason\":\"\"}";
        var value = ((DesktopProtocol.PortalMessage) DesktopProtocol.decode(wire.getBytes(StandardCharsets.UTF_8))).value();
        assertThat(value.appearance()).isEqualTo(DesktopObservation.Polarity.LIGHT);
        assertThat(value.accent()).isEmpty();
        assertThat(value.highContrast()).contains(false);
    }
}
