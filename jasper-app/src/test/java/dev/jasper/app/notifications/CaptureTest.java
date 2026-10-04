package dev.jasper.app.notifications;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CaptureTest {
    private static String printed(Throwable failure) {
        var out = new StringWriter();
        failure.printStackTrace(new PrintWriter(out));
        return out.toString().replace("\r", "").stripTrailing();
    }

    private static StackTraceElement frame(String method, int line) {
        return new StackTraceElement("dev.example.Tool", method, "Tool.java", line);
    }

    private static IllegalStateException at(StackTraceElement... frames) {
        var failure = new IllegalStateException("broken");
        failure.setStackTrace(frames);
        return failure;
    }

    /** A failure whose message, and so its toString and trace, cannot be read. */
    private static final class Unreadable extends RuntimeException {
        @Override public String getMessage() { throw new IllegalStateException("no message for you"); }
    }

    /** A failure whose cause cannot be read. */
    private static final class Causeless extends RuntimeException {
        Causeless() { super("no cause"); }
        @Override public synchronized Throwable getCause() { throw new IllegalStateException("cause unreadable"); }
    }

    @Test void theTraceIsPrintStackTraceWithCausesAndSuppressed() {
        var root = new IllegalArgumentException("root cause");
        var failure = new IllegalStateException("outer\nsecond line", root);
        failure.addSuppressed(new UnsupportedOperationException("cleanup failed"));
        Capture capture = Capture.of(failure);
        assertThat(capture.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
        assertThat(capture.message()).isEqualTo("outer\nsecond line");
        assertThat(capture.trace()).isEqualTo(printed(failure))
            .contains("Caused by: java.lang.IllegalArgumentException: root cause",
                "Suppressed: java.lang.UnsupportedOperationException: cleanup failed");
    }

    @Test void theTraceIsTakenOnceAndLaterChangesDoNotReachIt() {
        var failure = new IllegalStateException("as caught");
        Capture capture = Capture.of(failure);
        String taken = capture.trace();
        failure.addSuppressed(new IllegalStateException("added later"));
        failure.setStackTrace(new StackTraceElement[0]);
        assertThat(capture.trace()).isEqualTo(taken).doesNotContain("added later")
            .contains("at dev.jasper.app.notifications.CaptureTest");
    }

    @Test void aHugeMessageIsCutAndTheTraceStaysWithinItsLimit() {
        Capture capture = Capture.of(new IllegalStateException("x".repeat(400_000)));
        assertThat(capture.message()).hasSize(Capture.MESSAGE_LIMIT).endsWith(NotificationText.ELLIPSIS);
        assertThat(capture.trace()).hasSize(Capture.TRACE_LIMIT).endsWith(NotificationText.ELLIPSIS)
            .startsWith("java.lang.IllegalStateException: xxx");
    }

    @Test void aThrowableThatCannotDescribeItselfIsCapturedByItsClass() {
        Capture capture = Capture.of(new Unreadable());
        assertThat(capture.exceptionClass()).isEqualTo(Unreadable.class.getName());
        assertThat(capture.message()).isEmpty();
        assertThat(capture.trace()).isEqualTo(Unreadable.class.getName());
    }

    @Test void aCauseThatCannotBeReadLeavesAnEmptySignature() {
        Capture capture = Capture.of(new Causeless());
        assertThat(capture.message()).isEqualTo("no cause");
        assertThat(capture.signature()).isEmpty();
        assertThat(capture.trace()).isEqualTo(Causeless.class.getName());
    }

    @Test void aCauseCycleEnds() {
        var first = new IllegalStateException("first");
        var second = new IllegalArgumentException("second", first);
        first.initCause(second);
        Capture capture = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> Capture.of(first));
        assertThat(capture.trace()).contains("CIRCULAR REFERENCE");
        assertThat(capture.signature()).isNotEmpty();
    }

    @Test void theSignatureIsTheRootCausesTopFiveFrames() {
        var a = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5), frame("six", 6));
        var b = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5), frame("other", 60));
        var c = at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("changed", 50), frame("six", 6));
        assertThat(Capture.of(a).signature()).isEqualTo(Capture.of(b).signature()).isEqualTo(
            "dev.example.Tool.one:1\ndev.example.Tool.two:2\ndev.example.Tool.three:3\ndev.example.Tool.four:4\ndev.example.Tool.five:5");
        assertThat(Capture.of(c).signature()).isNotEqualTo(Capture.of(a).signature());
        var wrapped = new RuntimeException("wrapper", at(frame("one", 1), frame("two", 2), frame("three", 3), frame("four", 4), frame("five", 5)));
        assertThat(Capture.of(wrapped).signature()).as("a wrapper is keyed by what it wraps").isEqualTo(Capture.of(a).signature());
    }
}
