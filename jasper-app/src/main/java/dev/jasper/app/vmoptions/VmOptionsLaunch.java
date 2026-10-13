package dev.jasper.app.vmoptions;

import java.lang.management.ManagementFactory;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * What Jasper's launcher reported about this JVM (spec section 3.4). {@link #current()} reads only system properties,
 * so it is safe on the start-up path; the running heap and the physical memory are read on demand.
 *
 * @param file the options file the launcher read, or would have read; empty when Jasper's launcher did not start this
 *             JVM (for example {@code :jasper-app:run})
 * @param digest the digest of the lines it applied; empty when it applied none
 * @param corrupted whether it ignored the file as corrupted
 * @param skipped why it skipped the file ({@code launch-failed} after the guard's fallback start)
 */
public record VmOptionsLaunch(Optional<Path> file, Optional<String> digest, boolean corrupted, Optional<String> skipped) {
    /** This JVM's launch. */
    public static VmOptionsLaunch current() { return of(System.getProperties()); }

    private static final System.Logger LOG = System.getLogger(VmOptionsLaunch.class.getName());

    static VmOptionsLaunch of(Properties properties) {
        return new VmOptionsLaunch(file(properties), present(properties, "jasper.vmOptionsDigest"),
            "true".equals(properties.getProperty("jasper.vmOptionsFile.corrupted")), present(properties, "jasper.vmOptionsSkipped"));
    }

    /**
     * The launcher's options file. A value this platform cannot parse (the Windows launcher passes arguments through the
     * ANSI code page, so unrepresentable characters arrive as {@code ?}) leaves the launch unmanaged instead of stopping
     * Jasper from starting.
     */
    private static Optional<Path> file(Properties properties) {
        Optional<String> value = present(properties, "jasper.vmOptionsFile");
        if (value.isEmpty()) return Optional.empty();
        try {
            return Optional.of(Path.of(value.get()));
        } catch (InvalidPathException unparsable) {
            LOG.log(System.Logger.Level.WARNING, "jasper.vmOptionsFile is not a path this platform can parse, so the VM options are read-only: {0}", value.get());
            return Optional.empty();
        }
    }

    /** Whether Jasper's launcher started this JVM, so jasper.vmoptions applies to it. */
    public boolean launcherManaged() { return file.isPresent(); }

    /** The digest of the lines {@code parsed} applies, or empty when it applies none (corrupted or no option lines). */
    public static Optional<String> appliedDigest(VmOptionsSyntax.Parsed parsed) {
        return parsed.corrupted() || parsed.lines().isEmpty() ? Optional.empty() : Optional.of(VmOptionsSyntax.digest(parsed.lines()));
    }

    /**
     * Whether a restart would apply other options than this JVM's: {@code now} against the launch's digest. A skipped or
     * corrupted launch applied nothing on purpose, so it compares against {@code atStart}, the file as Jasper first read it.
     */
    public boolean restartNeeded(VmOptionsSyntax.Parsed now, VmOptionsSyntax.Parsed atStart) {
        Optional<String> baseline = skipped.isPresent() || corrupted ? appliedDigest(atStart) : digest;
        return !appliedDigest(now).equals(baseline);
    }

    /** This JVM's heap limit in MiB: the last {@code -Xmx} it was given, else the runtime's maximum. */
    public static long runningMaxHeapMiB() {
        return runningMaxHeapMiB(ManagementFactory.getRuntimeMXBean().getInputArguments(), Runtime.getRuntime().maxMemory());
    }

    static long runningMaxHeapMiB(List<String> inputArguments, long maxMemoryBytes) {
        return VmOptionsSyntax.lastMaxHeapMiB(inputArguments).orElse(maxMemoryBytes >> 20);
    }

    /** This computer's physical memory in MiB, when the JVM can tell. */
    public static OptionalLong physicalMemoryMiB() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean system) {
            long total = system.getTotalMemorySize();
            return total > 0 ? OptionalLong.of(total >> 20) : OptionalLong.empty();
        }
        return OptionalLong.empty();
    }

    private static Optional<String> present(Properties properties, String name) {
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
