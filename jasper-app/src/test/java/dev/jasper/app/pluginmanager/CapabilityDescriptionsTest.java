package dev.jasper.app.pluginmanager;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityDescriptionsTest {
    @Test void networkListenIsDescribedAndUnknownCapabilitiesAreShownByName() {
        assertThat(Capabilities.describe("network.listen"))
            .isEqualTo("Accept connections from other programs or computers, such as serving files (network.listen)");
        assertThat(Capabilities.describe("made.up")).isEqualTo("made.up");
    }

    @Test void clipboardReadIsDescribed() {
        assertThat(Capabilities.describe("clipboard.read")).isEqualTo("Read text you copied to the clipboard (clipboard.read)");
    }
}
