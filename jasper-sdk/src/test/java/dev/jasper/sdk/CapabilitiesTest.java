package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionTwentyOneWithTheBrowserCapability() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.22.0");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.CLIPBOARD_READ).isEqualTo("clipboard.read");
        assertThat(Capabilities.TERMINAL_ENVIRONMENT).isEqualTo("terminal.environment");
        assertThat(Capabilities.EDITOR_PROVIDE).isEqualTo("editor.provide");
        assertThat(Capabilities.PRESENTATION_PROVIDE).isEqualTo("presentation.provide");
        assertThat(Capabilities.BROWSER_PROVIDE).isEqualTo("browser.provide");
        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE,
            Capabilities.PRESENTATION_PROVIDE, Capabilities.BROWSER_PROVIDE);
    }
}
