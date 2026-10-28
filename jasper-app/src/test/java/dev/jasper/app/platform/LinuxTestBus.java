package dev.jasper.app.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** A private session bus; never connects to the desktop session bus. */
final class LinuxTestBus implements AutoCloseable {
    private final Process process;
    private final Thread reader;
    private final String address;

    private LinuxTestBus(Process process, Thread reader, String address) {
        this.process = process;
        this.reader = reader;
        this.address = address;
    }

    static LinuxTestBus start(Path directory, boolean abstractAddress) throws Exception {
        String requested = abstractAddress ? "unix:abstract=jasper-test-" + UUID.randomUUID()
            : "unix:path=" + directory.resolve("bus");
        Process process = new ProcessBuilder("dbus-daemon", "--session", "--nofork", "--nopidfile",
            "--print-address=1", "--address=" + requested)
            .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var address = new FutureTask<String>(() -> new BufferedReader(new InputStreamReader(
            process.getInputStream(), StandardCharsets.UTF_8)).readLine());
        Thread reader = Thread.ofPlatform().daemon().name("jasper-test-bus-address").start(address);
        try {
            String reported = address.get(5, TimeUnit.SECONDS);
            if (reported == null || !reported.startsWith("unix:"))
                throw new IllegalStateException("Test bus did not report a Unix address");
            return new LinuxTestBus(process, reader, reported);
        } catch (Exception failure) {
            try {
                new LinuxTestBus(process, reader, "").close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    String address() { return address; }

    @Override public void close() throws IOException {
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                if (!process.waitFor(3, TimeUnit.SECONDS))
                    throw new IllegalStateException("Test bus did not stop");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping test bus", interrupted);
        } finally {
            process.destroyForcibly();
            process.getInputStream().close();
            reader.interrupt();
            try {
                reader.join(1_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping test bus address reader", interrupted);
            }
            if (reader.isAlive()) throw new IllegalStateException("Test bus address reader did not stop");
        }
    }
}
