package dev.jasper.app.vmoptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs the bundled {@code java} with the user's options (spec sections 5.1 and 6.4): each applied line alone with
 * unrecognized options made fatal, then the line-up as launched, then, in a trained image, with the AOT cache required.
 * Blocking: run it on a worker, never on the EDT.
 */
public final class VmOptionsCheck {
    /** Runs a command and returns its exit status and merged output, or a timeout. */
    @FunctionalInterface
    public interface Runner {
        /** Runs {@code command} for at most {@code limit}. */
        Outcome run(List<String> command, Duration limit) throws IOException;
    }

    /** A run's exit status and merged output; {@code timedOut} runs were killed. */
    public record Outcome(int exit, String output, boolean timedOut) { }

    /**
     * A JVM that would not start.
     *
     * @param message the JVM's own explanation
     * @param recoverable whether the launcher recovers at launch (spec 4.5): HotSpot's initialization failures end with
     *                    {@code _exit} and are not recoverable
     */
    public record Fatal(String message, boolean recoverable) { }

    /**
     * What the check found.
     *
     * @param unrecognized applied lines the JVM would ignore
     * @param fatal why the JVM would not start with the line-up, if it would not
     * @param cacheRejected whether the options turn off the trained AOT cache
     */
    public record Findings(List<String> unrecognized, Optional<Fatal> fatal, boolean cacheRejected) {
        /** Nothing found, or nothing checked. */
        public static final Findings NONE = new Findings(List.of(), Optional.empty(), false);

        public Findings { unrecognized = List.copyOf(unrecognized); }
    }

    private static final System.Logger LOG = System.getLogger(VmOptionsCheck.class.getName());

    private final Path java;
    private final LauncherImage image;
    private final Runner runner;
    private final Duration limit;

    /** A check with {@code java}, the image's bundled options, and at most {@code limit} per run. */
    public VmOptionsCheck(Path java, LauncherImage image, Runner runner, Duration limit) {
        this.java = Objects.requireNonNull(java, "java");
        this.image = Objects.requireNonNull(image, "image");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.limit = Objects.requireNonNull(limit, "limit");
    }

    /** This runtime's own {@code java}: {@code <java.home>/bin/java}. */
    public static Path runtimeJava() {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
    }

    /**
     * Checks {@code parsed}'s applied lines; a corrupted file, or one without option lines, is not checked. A run over its
     * time limit ends the check with no findings at all (spec section 7).
     */
    public Findings check(VmOptionsSyntax.Parsed parsed) throws IOException {
        if (parsed.corrupted() || parsed.lines().isEmpty()) return Findings.NONE;
        var unrecognized = new ArrayList<String>();
        for (String line : parsed.lines()) {
            Outcome alone = run(List.of(java.toString(), "-XX:-IgnoreUnrecognizedVMOptions", line, "-version"));
            if (alone.timedOut()) return Findings.NONE;
            if (alone.exit() != 0 && unrecognized(alone.output())) unrecognized.add(line);
        }
        List<String> options = VmOptionsSyntax.merge(image.javaOptions(), parsed.lines());
        Outcome merged = run(command(options, List.of("-version")));
        if (merged.timedOut()) return Findings.NONE;
        Optional<Fatal> fatal = merged.exit() != 0 ? Optional.of(fatal(merged.output())) : Optional.empty();
        boolean cacheRejected = false;
        if (image.hasAotCache() && fatal.isEmpty()) {
            Outcome cache = run(command(options, List.of("-XX:AOTMode=on", "-cp", String.join(File.pathSeparator, image.classpath()), "-version")));
            if (cache.timedOut()) return Findings.NONE;
            cacheRejected = cache.exit() != 0;
        }
        return new Findings(unrecognized, fatal, cacheRejected);
    }

    /** One run; a run over its limit is logged (spec section 7). */
    private Outcome run(List<String> command) throws IOException {
        Outcome outcome = runner.run(command, limit);
        if (outcome.timedOut()) LOG.log(System.Logger.Level.WARNING, "The VM options check timed out after {0}: {1}", limit, String.join(" ", command));
        return outcome;
    }

    /**
     * Runs {@code command} as a process with its output merged, without the variables that add JVM options. An interrupt
     * kills the process, restores the interrupt and throws {@link InterruptedIOException}.
     */
    public static Outcome runProcess(List<String> command, Duration limit) throws IOException {
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"));
        Process process = builder.start();
        var output = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().name("jasper-vmoptions-check").start(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(output);
            } catch (IOException closed) {
                // The process was killed; what was read so far is enough.
            }
        });
        try {
            if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Outcome(-1, "", true);
            }
            reader.join(limit.toMillis());
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("The VM options check was interrupted");
        }
        synchronized (output) {
            return new Outcome(process.exitValue(), output.toString(StandardCharsets.UTF_8), false);
        }
    }

    /** Why a JVM did not start, from its output: the line after HotSpot's own marker, else the first message line. */
    static Fatal fatal(String output) {
        List<String> lines = output.lines().map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("[") && !line.contains("VM warning:") && !line.startsWith("Picked up "))
            .toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("Error occurred during initialization of")) {
                return new Fatal(i + 1 < lines.size() ? lines.get(i + 1) : lines.get(i), false);
            }
        }
        String message = lines.stream()
            .filter(line -> !line.startsWith("Error: Could not create the Java Virtual Machine") && !line.startsWith("Error: A fatal exception has occurred"))
            .findFirst()
            .orElse("The JVM did not start.");
        return new Fatal(message, true);
    }

    private static boolean unrecognized(String output) {
        return output.contains("Unrecognized VM option") || output.contains("Unrecognized option:");
    }

    private List<String> command(List<String> options, List<String> tail) {
        var command = new ArrayList<String>();
        command.add(java.toString());
        command.addAll(options);
        command.addAll(tail);
        return command;
    }
}
