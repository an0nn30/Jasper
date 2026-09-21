package dev.jasper.app.testsupport;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/** Holds the real cross-process endpoint lock until the test releases it. */
public final class EndpointLockChild implements AutoCloseable {
    private final Process process;
    public EndpointLockChild(Path lock) throws Exception {
        var classes = Path.of(EndpointLockChild.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", classes.toString(), EndpointLockChild.class.getName(), lock.toString()).start();
        var ready = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return process.inputReader().readLine(); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
        try {
            if (!"locked".equals(ready.get(5, TimeUnit.SECONDS))) throw new AssertionError("Lock child did not start");
        } catch (Exception | Error failure) { close(); throw failure; }
    }
    public static void main(String[] args) throws Exception {
        try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE); var lock = channel.lock()) {
            System.out.println("locked"); System.out.flush(); System.in.read();
        }
    }
    @Override public void close() throws Exception {
        process.getOutputStream().close();
        if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
    }
}
