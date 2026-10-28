package dev.jasper.app.platform;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/** Composition of the serialized publication owner and independently cancellable concrete adapters. */
final class LinuxTrayRuntime implements AutoCloseable {
    record Observations(Consumer<LinuxStatusNotifier.Host> host, Consumer<Boolean> connected, Consumer<Boolean> legacy) { }
    record LegacyAdapter(LinuxResidentTray.Legacy binding, Runnable start, Runnable probe, Consumer<Boolean> discoveryEnabled) { }
    private static final System.Logger LOG = System.getLogger(LinuxTrayRuntime.class.getName());
    private final ScheduledThreadPoolExecutor scheduler;
    private final LinuxResidentTray controller;
    private final LinuxResidentTray.Modern modern;
    private final LegacyAdapter legacy;
    private final Runnable closePopup;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Object timers = new Object();
    private final AtomicReference<ScheduledFuture<?>> retryTimer = new AtomicReference<>();
    private ScheduledFuture<?> probeTimer;
    private boolean discoveryEnabled = true;
    // Factories may synchronously observe state. Retain the latest observation of each kind until admission exists.
    private final Object observations = new Object();
    private LinuxResidentTray recipient;
    private LinuxStatusNotifier.Host pendingHost;
    private boolean hostObserved;
    private Boolean pendingConnection, pendingLegacy;

    static Runnable install(ResidentTray.Actions actions) {
        Map<String, String> launch = Map.copyOf(System.getenv());
        var popup = new LinuxTrayPopup(SwingUtilities::invokeLater);
        var runtime = new LinuxTrayRuntime(actions, scheduler(), SwingUtilities::invokeLater, callbacks -> {
            var adapter = new LinuxStatusNotifier(launch.get("DBUS_SESSION_BUS_ADDRESS"), callbacks.host(), callbacks.connected());
            return new LinuxResidentTray.Modern(adapter::publish, adapter::withdrawAndAwait, adapter::close, adapter::start);
        }, callbacks -> {
            var adapter = new LinuxLegacyTray(launch, callbacks.legacy(), popup::request, popup::cancel);
            return new LegacyAdapter(new LinuxResidentTray.Legacy(adapter::install, adapter::removeAndAwait, adapter::close),
                adapter::start, adapter::probe, adapter::discoveryEnabled);
        }, popup::close);
        runtime.start();
        return runtime::close;
    }

    static ScheduledThreadPoolExecutor scheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("jasper-tray-controller").factory());
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    LinuxTrayRuntime(ResidentTray.Actions actions, ScheduledThreadPoolExecutor scheduler, Consumer<Runnable> edt,
                     Function<Observations, LinuxResidentTray.Modern> modernFactory,
                     Function<Observations, LegacyAdapter> legacyFactory, Runnable closePopup) {
        this.scheduler = scheduler;
        this.closePopup = closePopup;
        var callbacks = new Observations(this::hostChanged, this::connectionChanged, this::legacyChanged);
        var suppliedModern = modernFactory.apply(callbacks);
        modern = new LinuxResidentTray.Modern(suppliedModern.publish(), suppliedModern.withdraw(), once(suppliedModern.close()), suppliedModern.reconnect());
        var suppliedLegacy = legacyFactory.apply(callbacks);
        var binding = suppliedLegacy.binding();
        legacy = new LegacyAdapter(new LinuxResidentTray.Legacy(binding.install(), binding.remove(), once(binding.close())),
            suppliedLegacy.start(), suppliedLegacy.probe(), suppliedLegacy.discoveryEnabled());
        controller = new LinuxResidentTray(actions, modern, legacy.binding(), scheduler::execute, this::retry, edt,
            this::cancelRetry, this::modernPublished);
    }

    void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        synchronized (observations) {
            recipient = controller;
            if (pendingConnection != null) connectionChanged(pendingConnection);
            if (hostObserved) controller.hostChanged(pendingHost);
            if (pendingLegacy != null) controller.legacyChanged(pendingLegacy);
            pendingHost = null; pendingConnection = null; pendingLegacy = null;
        }
        synchronized (timers) { if (!closed.get() && discoveryEnabled) startProbe(); }
        modern.reconnect().run();
        legacy.start().run();
    }
    private void hostChanged(LinuxStatusNotifier.Host host) {
        synchronized (observations) {
            if (recipient == null) { pendingHost = host; hostObserved = true; }
            else recipient.hostChanged(host);
        }
    }
    private void connectionChanged(boolean connected) {
        synchronized (observations) {
            if (recipient == null) pendingConnection = connected;
            else if (connected) recipient.connectionRestored(); else recipient.connectionLost();
        }
    }
    private void legacyChanged(boolean supported) {
        synchronized (observations) {
            if (recipient == null) pendingLegacy = supported;
            else recipient.legacyChanged(supported);
        }
    }
    private void retry(Duration delay, Runnable task) {
        synchronized (timers) {
            cancelRetry();
            if (closed.get()) return;
            retryTimer.set(scheduler.schedule(() -> {
                synchronized (timers) { retryTimer.set(null); }
                task.run();
            }, delay.toNanos(), TimeUnit.NANOSECONDS));
        }
    }
    private void cancelRetry() {
        synchronized (timers) {
            var future = retryTimer.getAndSet(null);
            if (future != null) future.cancel(false);
        }
    }
    private void modernPublished(boolean published) {
        synchronized (timers) {
            if (closed.get() || discoveryEnabled == !published) return;
            discoveryEnabled = !published;
            if (published) {
                if (probeTimer != null) probeTimer.cancel(false);
                probeTimer = null;
                legacy.discoveryEnabled().accept(false);
            } else {
                legacy.discoveryEnabled().accept(true);
                legacy.probe().run();
                if (started.get()) startProbe();
            }
        }
    }
    private void startProbe() {
        if (probeTimer == null) probeTimer = scheduler.scheduleWithFixedDelay(legacy.probe(), 5, 5, TimeUnit.SECONDS);
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        // Invalidate Swing and controller admission before requesting asynchronous adapter teardown.
        try { closePopup.run(); }
        finally {
            controller.close();
            synchronized (timers) {
                cancelRetry();
                if (probeTimer != null) probeTimer.cancel(false);
                probeTimer = null;
            }
            try { modern.close().run(); }
            finally {
                try { legacy.binding().close().run(); }
                finally { scheduler.execute(scheduler::shutdown); }
            }
        }
    }
    private static Runnable once(Runnable cleanup) {
        var done = new AtomicBoolean();
        return () -> {
            if (!done.compareAndSet(false, true)) return;
            try { cleanup.run(); }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.DEBUG, "Linux tray cleanup unavailable", failure); }
        };
    }
}
