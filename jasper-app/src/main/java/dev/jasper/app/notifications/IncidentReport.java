package dev.jasper.app.notifications;

import java.util.Objects;

/**
 * The text Copy puts on the clipboard for an incident (spec section 5): Jasper's version, the OS and the Java runtime,
 * the plugin and its version when a plugin failed, what it was doing and on which thread, then the trace. Pure.
 */
public final class IncidentReport {
    /**
     * Where Jasper runs.
     *
     * @param jasper Jasper's version
     * @param os the operating system, its version and architecture
     * @param java the Java runtime, its version and vendor
     */
    public record Environment(String jasper, String os, String java) {
        public Environment {
            Objects.requireNonNull(jasper, "jasper");
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(java, "java");
        }

        /** This process's environment: the packaged version, or "development build" from Gradle or an IDE. */
        public static Environment current() {
            return new Environment(System.getProperty("jpackage.app-version", "development build"),
                System.getProperty("os.name", "unknown OS") + " " + System.getProperty("os.version", "") + " ("
                    + System.getProperty("os.arch", "") + ")",
                System.getProperty("java.runtime.name", "Java") + " "
                    + System.getProperty("java.runtime.version", System.getProperty("java.version", "")) + " ("
                    + System.getProperty("java.vm.vendor", "") + ")");
        }
    }

    private IncidentReport() {}

    /**
     * @param incident what failed
     * @param pluginVersion the plugin's version, empty when unknown; unused for Jasper's own incidents
     * @param environment where Jasper runs
     * @return the report, ending with the trace and a line feed
     */
    public static String text(Incident incident, String pluginVersion, Environment environment) {
        var out = new StringBuilder()
            .append("Jasper ").append(environment.jasper()).append('\n')
            .append("OS: ").append(environment.os()).append('\n')
            .append("Java: ").append(environment.java()).append('\n');
        if (incident.fromPlugin())
            out.append("Plugin: ").append(incident.source()).append(pluginVersion.isEmpty() ? "" : " " + pluginVersion).append('\n');
        out.append("Operation: ").append(incident.operation()).append('\n')
            .append("Thread: ").append(incident.thread()).append('\n');
        if (incident.count() > 1) out.append("Occurrences: ").append(incident.count()).append('\n');
        return out.append('\n').append(incident.trace()).append('\n').toString();
    }
}
