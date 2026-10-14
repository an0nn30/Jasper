package dev.jasper.sdk;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilitiesTest {
    @Test void theSdkIsVersionSeventeenOneWithTheEnvironmentAndEditorCapabilities() {
        assertThat(JasperSdk.VERSION).isEqualTo("0.17.1");
        assertThat(Capabilities.NETWORK_LISTEN).isEqualTo("network.listen");
        assertThat(Capabilities.CLIPBOARD_READ).isEqualTo("clipboard.read");
        assertThat(Capabilities.TERMINAL_ENVIRONMENT).isEqualTo("terminal.environment");
        assertThat(Capabilities.EDITOR_PROVIDE).isEqualTo("editor.provide");
        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE);
    }
}
