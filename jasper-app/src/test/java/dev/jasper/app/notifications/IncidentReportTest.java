package dev.jasper.app.notifications;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentReportTest {
    private static final IncidentReport.Environment HERE = new IncidentReport.Environment("1.4.0", "Mac OS X 15.6 (aarch64)",
        "OpenJDK Runtime Environment 25.0.4 (JetBrains s.r.o.)");

    private static Incident incident(String source, int count) {
        Instant at = Instant.parse("2026-10-02T14:05:00Z");
        return new Incident(UUID.randomUUID(), source, "panel remote.sftp", "AWT-EventQueue-0", "java.lang.IllegalStateException",
            "broken", "java.lang.IllegalStateException: broken\n\tat dev.example.Tool.run(Tool.java:3)", count, at, at);
    }

    @Test void aJasperReportNamesTheEnvironmentWhatItWasDoingAndTheTrace() {
        assertThat(IncidentReport.text(incident("jasper", 1), "", HERE)).isEqualTo("""
            Jasper 1.4.0
            OS: Mac OS X 15.6 (aarch64)
            Java: OpenJDK Runtime Environment 25.0.4 (JetBrains s.r.o.)
            Operation: panel remote.sftp
            Thread: AWT-EventQueue-0

            java.lang.IllegalStateException: broken
            \tat dev.example.Tool.run(Tool.java:3)
            """);
    }

    @Test void aPluginReportNamesThePluginItsVersionAndTheCount() {
        assertThat(IncidentReport.text(incident("dev.jasper.remote", 3), "2.1.0", HERE))
            .contains("Plugin: dev.jasper.remote 2.1.0\nOperation: panel remote.sftp\n", "Occurrences: 3\n");
        assertThat(IncidentReport.text(incident("dev.jasper.remote", 1), "", HERE))
            .contains("Plugin: dev.jasper.remote\n").doesNotContain("Occurrences");
    }

    @Test void theCurrentEnvironmentDescribesThisProcess() {
        var current = IncidentReport.Environment.current();
        assertThat(current.jasper()).isEqualTo(System.getProperty("jpackage.app-version", "development build"));
        assertThat(current.os()).startsWith(System.getProperty("os.name"));
        assertThat(current.java()).contains(System.getProperty("java.runtime.version"));
    }
}
