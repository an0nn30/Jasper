import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;

/**
 * The launcher image test's application: appends what the launcher handed the JVM to $PROBE_OUTPUT, then "end". It
 * loads the two classes of the sealed package sealedprobe when their jar is on the class path, so an AOT cache trained
 * with it holds both, and reports the jar its own code source names.
 */
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
        line(report, "aotCacheSkipped", System.getProperty("jasper.aotCacheSkipped"));
        line(report, "aotCacheRecord", System.getProperty("jasper.aotCacheRecord"));
        line(report, "aotSkipVariable", System.getenv("JASPER_AOT_CACHE_SKIP"));
        line(report, "diagnoseVariable", System.getenv("JASPER_LAUNCH_DIAGNOSE"));
        line(report, "sealed", sealedClasses());
        line(report, "codeSource", codeSource());
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

    private static String sealedClasses() {
        try {
            return Class.forName("sealedprobe.First").getSimpleName() + "+"
                + Class.forName("sealedprobe.Second").getSimpleName();
        } catch (ClassNotFoundException absent) {
            return "absent";
        }
    }

    private static String codeSource() {
        CodeSource source = LauncherProbe.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) return "none";
        try {
            return Path.of(source.getLocation().toURI()).toString();
        } catch (URISyntaxException | IllegalArgumentException unusable) {
            return "unusable " + source.getLocation();
        }
    }

    private static void line(StringBuilder report, String key, String value) {
        report.append(key).append('=').append(value).append('\n');
    }
}
