package dev.jasper.app.platform;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Serial publication owner; observations invalidate stale I/O and actions at admission. */
final class LinuxResidentTray implements AutoCloseable {
    record Modern(BiPredicate<LinuxStatusNotifier.Host, IntConsumer> publish,
                  Runnable withdraw, Runnable close, Runnable reconnect) { }
    record Legacy(Predicate<IntConsumer> install, Runnable remove, Runnable close) { }
    private enum Retry { REGISTRATION, LEGACY, CLEANUP, RECONNECT }
    private static final System.Logger LOG = System.getLogger(LinuxResidentTray.class.getName());
    private final ResidentTray.Actions actions;
    private final Modern modern;
    private final Legacy legacy;
    private final Consumer<Runnable> enqueue;
    private final BiConsumer<Duration, Runnable> retry;
    private final Consumer<Runnable> edt;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final Object admission = new Object();
    private volatile LinuxStatusNotifier.Host desiredHost;
    private volatile boolean legacySupported;
    private boolean queued;
    // Admission reads these only while holding admission. Publication state below belongs to the worker.
    private LinuxTrayPolicy.Target actionBackend = LinuxTrayPolicy.Target.NONE;
    private LinuxStatusNotifier.Host actionHost;
    private LinuxStatusNotifier.Host publishedHost;
    private boolean modernNeedsWithdrawal;
    private boolean legacyInstalled;
    private boolean legacyNeedsRemoval;
    private long publishedGeneration;
    private long legacyGeneration;
    private boolean disconnected;
    private Object retryTicket;
    private Retry retryPurpose;
    private int failures;
    private final Set<String> failedStates = new HashSet<>();
    private boolean cleaned;

    LinuxResidentTray(ResidentTray.Actions actions, Modern modern, Legacy legacy, Consumer<Runnable> enqueue,
                      BiConsumer<Duration, Runnable> retry, Consumer<Runnable> edt) {
        this.actions = Objects.requireNonNull(actions);
        this.modern = Objects.requireNonNull(modern);
        this.legacy = Objects.requireNonNull(legacy);
        this.enqueue = Objects.requireNonNull(enqueue);
        this.retry = Objects.requireNonNull(retry);
        this.edt = Objects.requireNonNull(edt);
    }

    void hostChanged(LinuxStatusNotifier.Host host) {
        if (host != null && !host.registered()) host = null;
        synchronized (admission) {
            if (closed.get()) return;
            if (!Objects.equals(host, desiredHost)) {
                if ((actionBackend == LinuxTrayPolicy.Target.MODERN && !Objects.equals(host, actionHost))
                    || (actionBackend == LinuxTrayPolicy.Target.LEGACY && host != null)) generation.incrementAndGet();
                desiredHost = host;
                if (retryPurpose != Retry.RECONNECT) cancelRetry();
            }
            requestReconcile();
        }
    }

    void legacyChanged(boolean supported) {
        synchronized (admission) {
            if (closed.get()) return;
            if (legacySupported != supported && !supported && actionBackend == LinuxTrayPolicy.Target.LEGACY)
                generation.incrementAndGet();
            legacySupported = supported;
            requestReconcile();
        }
    }

    void connectionLost() {
        synchronized (admission) {
            if (closed.get()) return;
            if (!disconnected) {
                disconnected = true;
                desiredHost = null;
                if (actionBackend == LinuxTrayPolicy.Target.MODERN) generation.incrementAndGet();
                cancelRetry();
            }
            requestReconcile();
        }
    }

    void connectionRestored() {
        synchronized (admission) {
            if (closed.get()) return;
            disconnected = false;
            if (retryPurpose == Retry.RECONNECT) cancelRetry();
            failures = 0;
            requestReconcile();
        }
    }

    private void requestReconcile() {
        if (queued) return;
        queued = true;
        enqueue.accept(() -> {
            synchronized (admission) { queued = false; }
            if (closed.get()) cleanup(); else reconcile();
        });
    }

    private void reconcile() {
        if (closed.get()) return;
        var candidate = desiredHost;
        var target = LinuxTrayPolicy.target(candidate != null, legacySupported);
        if (publishedHost != null && candidate != null && candidate.equals(publishedHost)
            && publishedGeneration == generation.get()) return;
        if (modernNeedsWithdrawal && !withdrawModern()) return;
        if (legacyNeedsRemoval && (!legacyInstalled || legacyGeneration != generation.get()) && !removeLegacy()) return;
        if (closed.get()) return;
        if (target == LinuxTrayPolicy.Target.MODERN && !waiting(Retry.REGISTRATION)) {
            if (!removeLegacy()) return;
            long ticket = begin(LinuxTrayPolicy.Target.MODERN, candidate);
            if (ticket < 0) return;
            boolean added = false;
            modernNeedsWithdrawal = true;
            try { added = modern.publish().test(candidate, id -> dispatch(id, ticket)); }
            catch (RuntimeException failure) { failed("modern registration", failure); }
            if (closed.get() || ticket != generation.get() || !candidate.equals(desiredHost)) {
                withdrawModern();
                return;
            }
            if (added) {
                publishedHost = candidate;
                publishedGeneration = ticket;
                restored();
                synchronized (admission) { disconnected = false; failures = 0; cancelRetry(); }
                return;
            }
            failed("modern registration", null);
            if (!withdrawModern()) return;
            scheduleRetry(Retry.REGISTRATION);
        }
        if (closed.get()) return;
        if (!legacySupported) {
            if (!removeLegacy()) return;
        } else if (!legacyInstalled && !waiting(Retry.LEGACY)) {
            long ticket = begin(LinuxTrayPolicy.Target.LEGACY, null);
            if (ticket < 0) return;
            boolean added;
            try { added = legacy.install().test(id -> dispatch(id, ticket)); }
            catch (RuntimeException failure) {
                failed("legacy installation", failure);
                legacyNeedsRemoval = true;
                if (!removeLegacy()) return;
                scheduleRetry(Retry.LEGACY);
                return;
            }
            legacyInstalled = added;
            legacyNeedsRemoval = added;
            legacyGeneration = ticket;
            if (closed.get() || ticket != generation.get() || !legacySupported || !Objects.equals(desiredHost, candidate)) {
                removeLegacy();
                return;
            }
            if (!added) { failed("legacy installation", null); scheduleRetry(Retry.LEGACY); }
            else if (candidate == null && !isDisconnected()) restored();
        }
        if (isDisconnected()) scheduleRetry(Retry.RECONNECT);
    }

    private long begin(LinuxTrayPolicy.Target backend, LinuxStatusNotifier.Host host) {
        synchronized (admission) {
            if (closed.get()) return -1;
            actionBackend = backend;
            actionHost = host;
            return generation.incrementAndGet();
        }
    }

    private boolean withdrawModern() {
        generation.incrementAndGet();
        try { modern.withdraw().run(); }
        catch (RuntimeException failure) { failed("modern withdrawal", failure); scheduleRetry(Retry.CLEANUP); return false; }
        modernNeedsWithdrawal = false;
        publishedHost = null;
        return true;
    }

    private boolean removeLegacy() {
        if (!legacyNeedsRemoval) return true;
        generation.incrementAndGet();
        try { legacy.remove().run(); }
        catch (RuntimeException failure) { failed("legacy removal", failure); scheduleRetry(Retry.CLEANUP); return false; }
        legacyInstalled = false;
        legacyNeedsRemoval = false;
        return true;
    }

    private void dispatch(int id, long ticket) {
        edt.accept(() -> { if (!closed.get() && ticket == generation.get()) LinuxTrayMenu.invoke(id, actions); });
    }

    private boolean waiting(Retry purpose) {
        synchronized (admission) { return retryTicket != null && (retryPurpose == purpose || retryPurpose == Retry.CLEANUP); }
    }

    private boolean isDisconnected() { synchronized (admission) { return disconnected; } }

    private void scheduleRetry(Retry purpose) {
        synchronized (admission) {
            if (closed.get() || retryTicket != null) return;
            Object ticket = new Object();
            retryTicket = ticket;
            Retry scheduledPurpose = disconnected ? Retry.RECONNECT : purpose;
            retryPurpose = scheduledPurpose;
            Duration delay = Duration.ofSeconds(LinuxTrayPolicy.retrySeconds(failures++));
            retry.accept(delay, () -> {
                synchronized (admission) {
                    if (closed.get() || retryTicket != ticket) return;
                    cancelRetry();
                    // The timer admits work to the same serial worker; it never performs I/O itself.
                    enqueue.accept(() -> {
                        if (closed.get()) return;
                        if (scheduledPurpose == Retry.RECONNECT && isDisconnected()) {
                            try { modern.reconnect().run(); }
                            catch (RuntimeException failure) { failed("bus connection", failure); scheduleRetry(Retry.RECONNECT); }
                        }
                        reconcile();
                    });
                }
            });
        }
    }

    private void cancelRetry() { retryTicket = null; retryPurpose = null; }

    private void failed(String state, RuntimeException failure) {
        if (!failedStates.add(state)) return;
        LOG.log(System.Logger.Level.DEBUG, "Linux tray " + state + " unavailable", failure);
    }

    private void restored() {
        if (failedStates.isEmpty()) return;
        failedStates.clear();
        LOG.log(System.Logger.Level.DEBUG, "Linux tray restored");
    }

    @Override public void close() {
        synchronized (admission) {
            if (!closed.compareAndSet(false, true)) return;
            generation.incrementAndGet();
            cancelRetry();
            requestReconcile();
        }
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        try {
            if (modernNeedsWithdrawal) withdrawModern();
        } finally {
            try { removeLegacy(); }
            finally {
                try { modern.close().run(); }
                catch (RuntimeException failure) { failed("modern close", failure); }
                finally {
                    try { legacy.close().run(); }
                    catch (RuntimeException failure) { failed("legacy close", failure); }
                }
            }
        }
    }
}
