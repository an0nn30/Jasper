package dev.jasper.app.platform;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Owner-package access to {@link DefaultBrowser}'s bound on its first answer, for other packages' tests (browser plan 4c). */
public final class DefaultBrowserTestAccess {
    private DefaultBrowserTestAccess() { }

    /**
     * A check whose first answer waits up to {@code firstAnswerWithin} instead of 3 seconds, so a test that holds its
     * first check back never races the bound.
     */
    public static DefaultBrowser withFirstAnswerWithin(Executor worker, Clock clock, Supplier<Optional<Boolean>> check,
                                                       Duration firstAnswerWithin) {
        return new DefaultBrowser(worker, clock, check, firstAnswerWithin);
    }
}
