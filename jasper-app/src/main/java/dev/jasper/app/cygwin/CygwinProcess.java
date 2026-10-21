package dev.jasper.app.cygwin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounded process output and deadlines, shared by provisioning and path conversion. */
public final class CygwinProcess {
    private CygwinProcess() { }
    public static int run(List<String> command, Path log) {
        try { return run(command, log, Duration.ofMinutes(10)); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    public static int run(List<String> command, Path log, Duration timeout) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var failure = new java.util.concurrent.atomic.AtomicReference<IOException>();
        Thread drain = Thread.ofVirtual().start(() -> {
            try (var input = process.getInputStream(); var out = Files.newOutputStream(log)) {
                byte[] buffer = new byte[8192]; int n; long remaining = 4 * 1024 * 1024;
                while ((n = input.read(buffer)) >= 0) { int keep = (int)Math.min(remaining, n); out.write(buffer, 0, keep); remaining -= keep; }
            } catch (IOException error) { failure.set(error); }
        });
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IOException("Cygwin process timed out: " + command.getFirst());
            drain.join(5000);
            if (drain.isAlive()) throw new IOException("Cygwin process output did not close");
            if (failure.get() != null) throw failure.get();
            return process.exitValue();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted Cygwin operation", interrupted); }
        finally { if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); } }
    }
}
