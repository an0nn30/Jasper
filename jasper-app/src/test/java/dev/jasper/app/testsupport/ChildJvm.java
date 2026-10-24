package dev.jasper.app.testsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A test {@code main} in a child JVM, for the single-instance tests (workspace-tabs spec 17): headless, with the test
 * classes and the application's classes on its class path. What it prints arrives line by line through {@link #line};
 * closing its standard input ({@link #finish}) asks it to end, and {@link #close} makes sure it has.
 */
public final class ChildJvm implements AutoCloseable {
    private final Process process;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    private ChildJvm(Process process) {
        this.process = process;
        Thread.ofVirtual().name("child-jvm-output").start(() -> {
            try (BufferedReader reader = process.inputReader()) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) lines.add(line);
            } catch (IOException ended) {
                // The child went away; its lines so far are kept.
            }
        });
    }

    /**
     * Starts {@code main} with {@code arguments}; {@code anchor} is a production class whose classes join the class path.
     *
     * @param main a test class with a {@code main} method
     * @param anchor a class from the application's main classes
     * @param arguments the program's arguments
     * @return the running child
     * @throws IOException when the JVM cannot be started
     */
    public static ChildJvm start(Class<?> main, Class<?> anchor, String... arguments) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classPath = Stream.of(main, anchor).map(ChildJvm::codeSource).distinct().collect(Collectors.joining(File.pathSeparator));
        var command = Stream.concat(Stream.of(java, "-Djava.awt.headless=true", "-cp", classPath, main.getName()),
            Stream.of(arguments)).toList();
        return new ChildJvm(new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start());
    }

    private static String codeSource(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException failure) {
            throw new UncheckedIOException(new IOException(failure));
        }
    }

    /** The next line it printed, waiting up to {@code seconds}; null when none came. */
    public String line(long seconds) throws InterruptedException { return lines.poll(seconds, TimeUnit.SECONDS); }

    /** Its process id. */
    public long pid() { return process.pid(); }

    /** Whether it still runs. */
    public boolean alive() { return process.isAlive(); }

    /** Closes its standard input, which asks it to end. */
    public void finish() throws IOException { process.getOutputStream().close(); }

    /** Waits up to {@code seconds} for it to exit: its exit code, or -1 when it still runs. */
    public int exit(long seconds) throws InterruptedException {
        return process.waitFor(seconds, TimeUnit.SECONDS) ? process.exitValue() : -1;
    }

    /** Kills it at once, as a crash would. */
    public void kill() throws InterruptedException {
        process.destroyForcibly();
        process.waitFor(10, TimeUnit.SECONDS);
    }

    @Override public void close() throws Exception {
        try { finish(); } catch (IOException gone) { }
        if (!process.waitFor(5, TimeUnit.SECONDS)) kill();
    }
}
