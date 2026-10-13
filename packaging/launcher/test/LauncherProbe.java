import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** The launcher image test's application: appends what the launcher handed the JVM to $PROBE_OUTPUT, then "end". */
public final class LauncherProbe {
    private LauncherProbe() {
    }

    public static void main(String[] arguments) throws IOException {
        StringBuilder report = new StringBuilder();
        line(report, "vmOptionsFile", System.getProperty("jasper.vmOptionsFile"));
        line(report, "digest", System.getProperty("jasper.vmOptionsDigest"));
        line(report, "corrupted", System.getProperty("jasper.vmOptionsFile.corrupted"));
        line(report, "skipped", System.getProperty("jasper.vmOptionsSkipped"));
        line(report, "skipVariable", System.getenv("JASPER_VM_OPTIONS_SKIP"));
        line(report, "marker", System.getProperty("probe.marker"));
        for (String input : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            line(report, "input", input);
        }
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            line(report, "gc", collector.getName());
        }
        for (String argument : arguments) {
            line(report, "argument", argument);
        }
        report.append("end\n");
        Files.writeString(Path.of(System.getenv("PROBE_OUTPUT")), report, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void line(StringBuilder report, String key, String value) {
        report.append(key).append('=').append(value).append('\n');
    }
}
