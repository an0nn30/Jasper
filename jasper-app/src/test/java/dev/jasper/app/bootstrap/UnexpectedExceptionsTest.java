package dev.jasper.app.bootstrap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class UnexpectedExceptionsTest {
    @Test void anUncaughtFailureIsForwardedWithItsThread() throws Exception {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        List<String> forwarded = new CopyOnWriteArrayList<>();
        try (var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler()) {
            exceptions.forwardTo((thread, failure) -> forwarded.add(thread.getName() + ": " + failure.getMessage()));
            Thread failing = Thread.ofPlatform().name("jasper-test-failing").unstarted(() -> { throw new IllegalStateException("thread broke"); });
            failing.start();
            failing.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
        assertThat(forwarded).containsExactly("jasper-test-failing: thread broke");
    }

    @Test void aForwardThatFailsIsContained() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        try (var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler()) {
            exceptions.forwardTo((thread, failure) -> { throw new IllegalStateException("recording broke"); });
            assertThatCode(() -> exceptions.installed.uncaughtException(Thread.currentThread(), new IllegalStateException("x")))
                .doesNotThrowAnyException();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
    }

    @Test void nothingIsForwardedAfterClose() {
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        List<Throwable> forwarded = new CopyOnWriteArrayList<>();
        try {
            var exceptions = ApplicationBootstrap.installUnexpectedExceptionHandler();
            exceptions.forwardTo((thread, failure) -> forwarded.add(failure));
            exceptions.close();
            exceptions.installed.uncaughtException(Thread.currentThread(), new IllegalStateException("late"));
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original);
        }
        assertThat(forwarded).isEmpty();
    }
}
