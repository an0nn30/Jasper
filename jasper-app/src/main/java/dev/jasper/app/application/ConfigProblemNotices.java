package dev.jasper.app.application;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.notifications.NotificationRequest;
import dev.jasper.app.notifications.Severity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Configuration problems as one sticky notification (spec section 7, producer 2): posted when the problems gain one
 * not notified before, replacing the previous notice, and expired when every problem is gone. The status bar
 * indicator and its dialog remain the full list. EDT only.
 */
final class ConfigProblemNotices {
    private final NotificationCenter center;
    private final Runnable showProblems;
    private final Runnable openFile;
    private Set<String> notified = Set.of();
    private UUID current;

    ConfigProblemNotices(NotificationCenter center, Runnable showProblems, Runnable openFile) {
        this.center = center;
        this.showProblems = showProblems;
        this.openFile = openFile;
    }

    void accept(ConfigService.State state) {
        List<ConfigDiagnostic> problems = state.diagnostics();
        Set<String> now = new LinkedHashSet<>();
        for (ConfigDiagnostic problem : problems) now.add(problem.formatted());
        if (now.isEmpty()) {
            expireCurrent();
            notified = Set.of();
            return;
        }
        if (notified.containsAll(now)) {
            notified = now;
            return;
        }
        expireCurrent();
        boolean errors = problems.stream().anyMatch(problem -> problem.severity() == ConfigDiagnostic.Severity.ERROR);
        boolean oneFile = problems.stream().allMatch(problem -> problem.file().equals(state.file()));
        String subject = oneFile ? state.file().getFileName().toString() : "The configuration";
        String content = describe(problems.getFirst()) + (problems.size() == 1 ? "" : "\nand " + (problems.size() - 1) + " more");
        current = center.post(NotificationRequest.of(NotificationsFeature.CONFIG.id(), errors ? Severity.ERROR : Severity.WARNING,
                subject + (errors ? " has errors" : " has warnings"))
            .withContent(content)
            .withAction(new NotificationAction("Show Problems", showProblems, false))
            .withAction(new NotificationAction("Open " + state.file().getFileName(), openFile, false))).orElse(null);
        notified = now;
    }

    private void expireCurrent() {
        if (current == null) return;
        center.expire(current);
        current = null;
    }

    static String describe(ConfigDiagnostic problem) {
        return problem.file().getFileName() + (problem.line() == 0 ? "" : ":" + problem.line()) + ": "
            + (problem.key().isEmpty() ? "" : "[" + problem.key() + "] ") + problem.message();
    }
}
