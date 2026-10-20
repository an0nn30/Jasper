package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsForegroundTest {
    @Test void neverThrowsWhateverThePlatformOrProcess() {
        long self = ProcessHandle.current().pid();
        // Elsewhere, and for an unknown or impossible id, it does nothing at all.
        assertThatCode(() -> WindowsForeground.allow("Mac OS X", self)).doesNotThrowAnyException();
        assertThatCode(() -> WindowsForeground.allow(null, self)).doesNotThrowAnyException();
        assertThatCode(() -> WindowsForeground.allow("Windows 11", -1)).doesNotThrowAnyException();
        assertThatCode(() -> WindowsForeground.allow("Windows 11", 1L << 40)).doesNotThrowAnyException();
        // On a system without user32 the failed lookup is contained; on Windows this is the real call.
        assertThatCode(() -> WindowsForeground.allow("Windows 11", self)).doesNotThrowAnyException();
    }

    @Test void theCallResolvesOnWindows() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "Windows only");
        // Granted or declined depends on whether this test JVM holds the foreground right; either way it is callable.
        assertThatCode(() -> WindowsForeground.grant(ProcessHandle.current().pid())).doesNotThrowAnyException();
    }
}
