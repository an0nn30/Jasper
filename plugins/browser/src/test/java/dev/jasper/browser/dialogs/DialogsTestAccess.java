package dev.jasper.browser.dialogs;

import java.util.function.LongSupplier;

/** Test-only access to the {@code dialogs} package, for tests outside it. */
public final class DialogsTestAccess {
    private DialogsTestAccess() { }

    /**
     * Has the permission prompts an item asks for read {@code clock} until the answer is closed, as their flood guard
     * and their double-click interval do: for item tests that answer a prompt as soon as it shows. UI thread or before
     * any prompt is asked.
     *
     * @param clock a monotonic clock's milliseconds
     * @return puts the system clock back
     */
    public static AutoCloseable promptsTimedBy(LongSupplier clock) {
        PermissionPrompt.useClock(clock);
        return PermissionPrompt::useSystemClock;
    }
}
