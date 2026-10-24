package dev.jasper.app.residency;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A child JVM for the single-instance tests. {@code hold <lock>} takes the lock (which records this process's id in
 * {@code instance.pid}), prints "owner" or "held", and keeps it until its standard input closes. {@code await <lock>
 * <millis>} tries every 20 ms until it takes the lock, then prints "owner" and holds it the same way, or prints
 * "timeout": a lock a dead process held is released asynchronously on Windows.
 */
public final class InstanceLockProbe {
    private InstanceLockProbe() { }

    public static void main(String[] args) throws Exception {
        boolean await = args[0].equals("await");
        Path file = Path.of(args[1]);
        long deadline = System.nanoTime() + (await ? Long.parseLong(args[2]) : 0) * 1_000_000L;
        Optional<InstanceLock> lock = InstanceLock.tryAcquire(file);
        while (await && lock.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
            lock = InstanceLock.tryAcquire(file);
        }
        System.out.println(lock.isPresent() ? "owner" : await ? "timeout" : "held");
        System.out.flush();
        if (lock.isEmpty()) return;
        while (System.in.read() >= 0) { }
        lock.get().close();
    }
}
