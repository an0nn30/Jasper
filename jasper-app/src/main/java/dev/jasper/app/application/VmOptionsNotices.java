package dev.jasper.app.application;

import dev.jasper.app.notifications.DisplayMode;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationGroup;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Custom VM options notifications (spec sections 4.3, 5.1, 5.4 and 5.5) in their own group: a fallback start, a corrupted
 * file, a pending restart, failures and low memory. The instance methods run on the EDT only; {@link #lowMemory} builds a
 * request on any thread.
 */
final class VmOptionsNotices {
    static final NotificationGroup GROUP = new NotificationGroup("jasper.vmoptions", "Memory & VM options", NotificationRequest.JASPER, DisplayMode.CARD);

    private final NotificationCenter center;
    private final Runnable edit;
    private final Runnable restart;

    VmOptionsNotices(NotificationCenter center, Runnable edit, Runnable restart) {
        this.center = center;
        this.edit = edit;
        this.restart = restart;
        center.register(GROUP);
    }

    /** The launcher started Jasper once more without the user's options; {@code message} is the JVM's own, when known. */
    void fallbackStart(Optional<String> message) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.WARNING, "Jasper started without your custom VM options")
            .withContent(message.map(text -> "The JVM rejected them: " + text).orElse("The JVM rejected them, so Jasper started with the bundled options."))
            .withAction(new NotificationAction("Edit Custom VM Options\u2026", edit, false)));
    }

    /** The launcher ignored {@code file} because it is not valid text. */
    void corrupted(Path file) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.WARNING, "Custom VM options were ignored")
            .withContent(file.getFileName() + " contains a NUL character or is not valid UTF-8, so Jasper started with the bundled options.")
            .withAction(new NotificationAction("Edit Custom VM Options\u2026", edit, false)));
    }

    /** A restart would apply other options than this JVM's. */
    void restartPending() {
        center.post(NotificationRequest.of(GROUP.id(), Severity.INFO, "Restart Jasper to apply your VM options")
            .withContent("The new options take effect the next time Jasper starts.")
            .withAction(NotificationAction.of("Restart Now", restart)));
    }

    /** Restart could not work out how Jasper was started. */
    void restartUnavailable() {
        center.post(NotificationRequest.of(GROUP.id(), Severity.ERROR, "Jasper could not restart")
            .withContent("Jasper could not work out how it was started. Quit and reopen it to apply your changes."));
    }

    /** A file operation failed. */
    void failed(String title, String details) {
        center.post(NotificationRequest.of(GROUP.id(), Severity.ERROR, title).withContent(details));
    }

    /** Jasper's heap stays nearly full, or ran out (spec section 5.4); {@code adjust} opens the heap limit. Any thread. */
    static NotificationRequest lowMemory(Runnable adjust) {
        return NotificationRequest.of(GROUP.id(), Severity.WARNING, "Jasper is running low on memory")
            .withContent("Raise the maximum heap size so Jasper keeps up; the new limit applies after a restart.")
            .withAction(new NotificationAction("Adjust Memory Settings\u2026", adjust, false));
    }
}
