package dev.jasper.app.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowTriggerTest {
    @Test void theProcessesFirstPlainWindowIsTheColdOneAndLaterOnesAreMenuWindows() {
        assertThat(JasperApplication.windowTrigger(0, false)).isEqualTo("cold");
        assertThat(JasperApplication.windowTrigger(1, false)).isEqualTo("menu");
        assertThat(JasperApplication.windowTrigger(7, false)).isEqualTo("menu");
    }

    @Test void aProcessThatHasWarmedUpHasNoColdWindow() {
        // A --background process's first plain window comes from a menu or the Dock, not from a cold start.
        assertThat(JasperApplication.windowTrigger(0, true)).isEqualTo("menu");
        assertThat(JasperApplication.windowTrigger(1, true)).isEqualTo("menu");
    }
}
