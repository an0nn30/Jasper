package dev.jasper.app.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowTriggerTest {
    @Test void theProcessesFirstPlainWindowIsTheColdOneAndLaterOnesAreMenuWindows() {
        assertThat(JasperApplication.windowTrigger(0)).isEqualTo("cold");
        assertThat(JasperApplication.windowTrigger(1)).isEqualTo("menu");
        assertThat(JasperApplication.windowTrigger(7)).isEqualTo("menu");
    }
}
