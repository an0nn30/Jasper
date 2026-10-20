package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsProcessTest {
    private static final boolean WINDOWS = WindowsProcess.supported(System.getProperty("os.name"));

    @Test void theFloorIsWhatTheProcessHoldsCappedAndNoneForATrimmedProcess() {
        assertThat(WindowsProcess.floor(300L << 20)).isEqualTo(300L << 20);
        assertThat(WindowsProcess.floor(900L << 20)).isEqualTo(WindowsProcess.FLOOR_CAP_BYTES);
        assertThat(WindowsProcess.floor(20L << 20)).as("already trimmed, or a wrong reading").isZero();
        assertThat(WindowsProcess.supported("Mac OS X")).isFalse();
        assertThat(WindowsProcess.supported(null)).isFalse();
    }

    @Test void elsewhereThereIsNothingToReadOrSet() {
        assumeTrue(!WINDOWS, "not Windows");
        assertThat(WindowsProcess.sample()).isNull();
        assertThatCode(WindowsProcess::keepResponsive).doesNotThrowAnyException();
    }

    @Test void onWindowsTheCountersAreThisProcesssAndTheRequestsAreAccepted() {
        assumeTrue(WINDOWS, "Windows only");
        WindowsProcess.Sample first = WindowsProcess.sample();
        assertThat(first).isNotNull();
        // A running JVM holds some megabytes and has faulted pages in; its counters only grow.
        assertThat(first.workingSetBytes()).isGreaterThan(8L << 20);
        assertThat(first.pageFaults()).isPositive();
        assertThatCode(WindowsProcess::keepResponsive).doesNotThrowAnyException();
        // Fresh native memory faults in page by page as it is first touched (a reused heap region might not).
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var touched = arena.allocate(32L << 20, 4096);
            for (long at = 0; at < touched.byteSize(); at += 4096) touched.set(java.lang.foreign.ValueLayout.JAVA_BYTE, at, (byte) 1);
            assertThat(WindowsProcess.sample().pageFaults()).isGreaterThan(first.pageFaults() + 1_000);
        }
    }
}
