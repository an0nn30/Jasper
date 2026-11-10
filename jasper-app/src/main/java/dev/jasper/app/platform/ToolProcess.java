package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs a desktop tool such as {@code xdg-settings} or {@code xdg-mime} and reads what it prints. */
final class ToolProcess {
    private ToolProcess() { }

    /**
     * Runs {@code command} with no input and no shell, its error output discarded.
     *
     * @return its output when it exits with 0 within {@code timeout}; empty when it cannot start, exits with another
     *     code, or is still running at the time-out, when it and every process it started are ended
     */
    static Optional<String> run(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException missing) {
            return Optional.empty();
        }
        try {
            process.getOutputStream().close();
            long deadline = System.nanoTime() + timeout.toNanos();
            // Read while waiting: a child that filled its pipe would otherwise stall until the time-out.
            var output = new FutureTask<>(() -> process.getInputStream().readAllBytes());
            Thread.ofVirtual().name("jasper-tool-output").start(output);
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) return Optional.empty();
            byte[] bytes = output.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return process.exitValue() == 0 ? Optional.of(new String(bytes, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | ExecutionException | TimeoutException failure) {
            return Optional.empty();
        } finally {
            // xdg-settings and xdg-mime are shell scripts: what they started goes too. Once the tool has exited, its
            // children belong to init and are no longer its descendants, and a reused process id could be another program's.
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
