package dev.jasper.app.bootstrap;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.platform.WindowsForeground;
import dev.jasper.app.residency.HandoffSocket;
import dev.jasper.app.residency.InstanceLock;
import dev.jasper.app.residency.LaunchRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Workspace-tabs spec 5.1 to 5.3, before AWT: a launch takes its home's instance lock and becomes the owner, hands its
 * request to the owner, exits, or waits for the lock as restart's replacement ({@link LaunchAction}). It never becomes a
 * second instance: an owner that does not answer, or a lock that cannot be taken, ends the launch with a message.
 */
final class SingleInstance {
    private static final System.Logger LOG = System.getLogger(SingleInstance.class.getName());

    private SingleInstance() { }

    /**
     * How long a launch keeps trying an owner that does not answer, how long a replacement waits, and how often both
     * try.
     *
     * @param handoff spec 5.3's 10 s
     * @param replacing spec 5.2's 30 s
     * @param poll the pause between tries
     */
    record Timing(Duration handoff, Duration replacing, Duration poll) {
        static final Timing STANDARD = new Timing(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMillis(100));
    }

    /** What became of the launch. */
    sealed interface Claim {
        /** This process owns the home; its endpoints are bound. */
        record Owner(Instance instance) implements Claim {
            public Owner { Objects.requireNonNull(instance, "instance"); }
        }

        /** Nothing more to do: exit with {@code code}. */
        record Done(int code) implements Claim { }
    }

    /**
     * Claims the home for this launch.
     *
     * @param options its arguments
     * @param dirs its home
     * @param timing its patience
     * @param failure shows the message a failed launch ends with
     * @return the claim
     */
    static Claim claim(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure) {
        Path source = HandoffSocket.codeLocation();
        long modified = HandoffSocket.lastModified(source);
        try {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            return switch (LaunchAction.of(lock.isPresent(), options)) {
                case OWN -> new Claim.Owner(Instance.open(lock.orElseThrow(), dirs, source, modified));
                case EXIT -> {
                    LOG.log(System.Logger.Level.INFO, "Started with " + (options.background() ? "--background" : "--no-window")
                        + " while a Jasper owns this home; exiting");
                    yield new Claim.Done(0);
                }
                case AWAIT_LOCK -> awaitLock(options, dirs, timing, failure, source, modified);
                case HAND_OFF -> handOff(options, dirs, timing, failure, source, modified);
            };
        } catch (IOException unavailable) {
            failure.accept("Jasper could not lock its home folder (" + dirs.instanceLock() + "): " + unavailable.getMessage());
            return new Claim.Done(1);
        }
    }

    /**
     * Restart's replacement (spec 5.2) waits for the lock while the process it replaces lives. When that process has gone
     * and the lock is still held, another launch took the home in between, or the operating system has not yet released
     * the lock, and the replacement hands its request on as any launch would (plan Ruling 27).
     */
    private static Claim awaitLock(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure, Path source,
                                   long modified) throws IOException {
        long replaced = options.replacing().getAsLong();
        long deadline = System.nanoTime() + timing.replacing().toNanos();
        while (true) {
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            if (lock.isPresent()) return new Claim.Owner(Instance.open(lock.get(), dirs, source, modified));
            if (!ProcessHandle.of(replaced).map(ProcessHandle::isAlive).orElse(false))
                return handOff(options, dirs, timing, failure, source, modified);
            if (System.nanoTime() >= deadline) {
                failure.accept(unresponsive(replaced));
                return new Claim.Done(1);
            }
            if (!pause(timing)) return new Claim.Done(1);
        }
    }

    private static Claim handOff(AppArguments options, AppDirs dirs, Timing timing, Consumer<String> failure, Path source,
                                 long modified) throws IOException {
        if (!HandoffSocket.pathFits(dirs.daemonSocket())) {
            // The owner could not bind either and runs unreachable (plan Ruling 15): say why at once.
            failure.accept(HandoffSocket.tooLong(dirs.daemonSocket()));
            return new Claim.Done(1);
        }
        LaunchRequest request = LaunchRequest.of(source, modified, options.workingDirectory(), options.paths(), options.startupFlags());
        if (!request.fits()) {
            failure.accept("Jasper cannot open that many paths at once.");
            return new Claim.Done(2);
        }
        long deadline = System.nanoTime() + timing.handoff().toNanos();
        while (true) {
            // Windows lets only this freshly launched process bring a window forward; the owner needs that right first.
            WindowsForeground.allow(System.getProperty("os.name"), InstanceLock.ownerPid(dirs.instanceLock()));
            LaunchRequest.Response answer = HandoffSocket.handOff(dirs.daemonSocket(), dirs.daemonToken(), request);
            if (answer == LaunchRequest.Response.OK || answer == LaunchRequest.Response.UPDATED) return new Claim.Done(0);
            // The owner may be between taking the lock and binding, quitting, or gone (plan Rulings 3 and 23).
            Optional<InstanceLock> lock = InstanceLock.tryAcquire(dirs.instanceLock());
            if (lock.isPresent()) return new Claim.Owner(Instance.open(lock.get(), dirs, source, modified));
            if (System.nanoTime() >= deadline) {
                failure.accept(unresponsive(InstanceLock.ownerPid(dirs.instanceLock())));
                return new Claim.Done(1);
            }
            if (!pause(timing)) return new Claim.Done(1);
        }
    }

    /** Waits one poll: false when interrupted. */
    private static boolean pause(Timing timing) {
        try {
            Thread.sleep(Math.max(1, timing.poll().toMillis()));
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Spec 5.3's message for an owner that does not answer.
     *
     * @param pid its process id, or -1 when none is known
     * @return the message
     */
    static String unresponsive(long pid) {
        return "Jasper is already running but not responding. Quit it" + (pid > 0 ? " (process " + pid + ")" : "") + " and try again.";
    }
}
