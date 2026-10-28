package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.freedesktop.dbus.connections.IDisconnectCallback;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;

/** Owns discovery and one publication; all blocking library work stays on one bounded worker. */
final class LinuxStatusNotifier implements AutoCloseable {
    record Host(String service, String owner, boolean registered) { }
    private static final Host NONE = new Host("", "", false);
    private static final String WATCHER_PATH = "/StatusNotifierWatcher";
    private final List<String> addresses;
    private final Consumer<Host> changed;
    private final Runnable beforeHandoff;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(1), Thread.ofPlatform().daemon().name("jasper-tray-dbus").factory());
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
        Thread.ofPlatform().daemon().name("jasper-tray-deadline").factory());
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean busFailed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final List<AutoCloseable> subscriptions = new ArrayList<>();
    private volatile boolean closed;
    private volatile boolean withdrawRequested;
    private volatile Thread running;
    private volatile Map<String, String> owners = Map.of();
    private volatile DBusConnection discovery;
    private volatile DBusConnection publication;
    private String discoveryAddress;
    private String discoveryBusId;
    private Host observed;
    private Host pendingObservation;
    private final Object deadlineLock = new Object();
    private long attempt;

    LinuxStatusNotifier(String address, Consumer<Host> changed) { this(address, changed, () -> { }); }

    LinuxStatusNotifier(String address, Consumer<Host> changed, Runnable beforeHandoff) {
        this.beforeHandoff = beforeHandoff;
        this.addresses = addresses(address, System.getenv("XDG_RUNTIME_DIR"));
        this.changed = changed;
        deadlines.setRemoveOnCancelPolicy(true);
    }

    static List<String> addresses(String captured, String runtimeDirectory) {
        var result = new ArrayList<String>();
        if (captured != null && !captured.isBlank()) {
            for (String entry : captured.split(";")) {
                if (!entry.startsWith("unix:")) continue;
                var fields = List.of(entry.substring(5).split(","));
                boolean clientAddress = fields.stream().noneMatch(field -> field.startsWith("listen="));
                boolean socketAddress = fields.stream().anyMatch(field -> field.startsWith("path=/")
                    || (field.startsWith("abstract=") && field.length() > 9));
                if (clientAddress && socketAddress) result.add(entry);
            }
        } else if (runtimeDirectory != null) {
            try {
                Path directory = Path.of(runtimeDirectory);
                Path socket = directory.resolve("bus");
                if (directory.isAbsolute() && Files.exists(socket)
                    && Boolean.TRUE.equals(Files.getAttribute(socket, "unix:isOther")))
                    result.add("unix:path=" + socket);
            } catch (IOException | RuntimeException unavailable) { /* No usable captured session bus. */ }
        }
        return List.copyOf(result);
    }

    /** Called again by the controller's backoff after a bus failure. */
    void start() { requestSnapshot(); }

    private void requestSnapshot() {
        if (closed) return;
        dirty.set(true);
        submit(() -> { dirty.set(false); snapshot(); return true; });
    }

    /** The controller calls from its worker, never the EDT. */
    boolean publish(Host host, IntConsumer action) {
        if (closed || !host.registered()) return false;
        long requestedGeneration = generation.get();
        Future<Boolean> future = submit(() -> {
            if (closed || requestedGeneration != generation.get()) return false;
            closePublication();
            if (discovery == null || !discovery.isConnected() || !host.equals(currentHost())) return false;
            DBusConnection connection = connect(discoveryAddress);
            if (connection == null) return false;
            publication = connection;
            if (!discoveryBusId.equals(bus(connection).GetId())) {
                closePublication();
                return false;
            }
            long ticket = generation.incrementAndGet();
            IntConsumer guarded = id -> {
                if (!closed && generation.get() == ticket) action.accept(id);
            };
            connection.exportObject("/StatusNotifierItem", host.service().equals(LinuxTrayDbus.FREEDESKTOP_WATCHER)
                ? new LinuxTrayDbus.FreedesktopItemObject(guarded) : new LinuxTrayDbus.ItemObject(guarded));
            LinuxTrayDbus.exportMenu(connection, guarded);
            if (host.service().equals(LinuxTrayDbus.WATCHER))
                connection.getRemoteObject(host.owner(), WATCHER_PATH, LinuxTrayDbus.Watcher.class)
                    .RegisterStatusNotifierItem(connection.getUniqueName());
            else connection.getRemoteObject(host.owner(), WATCHER_PATH, LinuxTrayDbus.FreedesktopWatcher.class)
                    .RegisterStatusNotifierItem(connection.getUniqueName());
            if (Thread.currentThread().isInterrupted() || closed || !host.equals(currentHost())) {
                closePublication();
                return false;
            }
            return true;
        });
        if (future == null) return false;
        try { return future.get(2, TimeUnit.SECONDS); }
        catch (Exception failure) {
            withdraw();
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    void withdraw() {
        synchronized (deadlineLock) {
            if (closed) return;
            generation.incrementAndGet();
            withdrawRequested = true;
            Thread thread = running;
            if (thread != null) thread.interrupt();
            // An active operation drains this request before releasing ownership.
            submit(() -> false);
        }
    }

    private Future<Boolean> submit(Callable<Boolean> operation) {
        synchronized (deadlineLock) {
            if (closed || !active.compareAndSet(false, true)) return null;
            return worker.submit(() -> {
                final long ticket;
                synchronized (deadlineLock) { running = Thread.currentThread(); ticket = ++attempt; }
                var expiry = deadlines.schedule(() -> {
                    synchronized (deadlineLock) {
                        if (ticket == attempt && running != null) running.interrupt();
                    }
                }, 1900, TimeUnit.MILLISECONDS);
                boolean result = false;
                try {
                    result = operation.call();
                } catch (Exception failure) {
                    closePublication();
                    closeDiscovery();
                    report(NONE);
                } finally {
                    expiry.cancel(false);
                    synchronized (deadlineLock) { ++attempt; }
                    if (Thread.currentThread().isInterrupted()) {
                        result = false;
                        closePublication();
                        closeDiscovery();
                        report(NONE);
                    }
                    beforeHandoff.run();
                    Host notification;
                    while (true) {
                        boolean closeAll;
                        synchronized (deadlineLock) {
                            // Cleanup requests and release of worker ownership share one handoff.
                            // Close/withdraw either join this drain or submit work after its release.
                            if (withdrawRequested || (closed && (publication != null || discovery != null))) {
                                withdrawRequested = false;
                                closeAll = closed;
                            } else {
                                Thread.interrupted();
                                running = null;
                                notification = pendingObservation;
                                pendingObservation = null;
                                active.set(false);
                                if (closed) { worker.shutdown(); deadlines.shutdownNow(); }
                                break;
                            }
                        }
                        result = false;
                        closePublication();
                        if (closeAll) closeDiscovery();
                    }
                    if (!closed && notification != null) changed.accept(notification);
                    if (!closed && dirty.get()) requestSnapshot();
                }
                return result;
            });
        }
    }

    private DBusConnection connect(String address) throws Exception {
        if (Thread.currentThread().isInterrupted() || closed) return null;
        DBusConnection connection = null;
        try {
            // Do Hello separately so cancellation can always close a fully constructed connection.
            var owned = new java.util.concurrent.atomic.AtomicReference<DBusConnection>();
            var builder = DBusConnectionBuilder.forAddress(address).withShared(false)
                .withDisconnectCallback(new IDisconnectCallback() {
                    @Override public void disconnectOnError(IOException error) {
                        DBusConnection failed = owned.get();
                        if (failed != null && (failed == discovery || failed == publication)) {
                            busFailed.set(true);
                            requestSnapshot();
                        }
                    }
                });
            builder.transportConfig().withTimeout(1).withRegisterSelf(false);
            connection = builder.build();
            owned.set(connection);
            connection.register();
            if (Thread.currentThread().isInterrupted() || closed) {
                closeConnection(connection);
                return null;
            }
            return connection;
        } catch (Exception failure) {
            closeConnection(connection);
            if (Thread.currentThread().isInterrupted()) throw failure;
            return null;
        }
    }

    private static DBus bus(DBusConnection connection) throws org.freedesktop.dbus.exceptions.DBusException {
        return connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
    }

    private void snapshot() throws Exception {
        if (busFailed.getAndSet(false)) {
            closePublication(); closeDiscovery(); report(NONE);
            return;
        }
        if (discovery == null || !discovery.isConnected()) {
            closeDiscovery();
            for (String address : addresses) {
                discovery = connect(address);
                if (discovery != null) {
                    discoveryAddress = address;
                    discoveryBusId = bus(discovery).GetId();
                    break;
                }
            }
            if (discovery == null) { report(NONE); return; }
            // Subscribe before any owner/property query. Every signal schedules an authoritative snapshot.
            subscriptions.add(discovery.addSigHandler(DBus.NameOwnerChanged.class, event -> {
                if ("org.freedesktop.DBus".equals(event.getSource()) && "/org/freedesktop/DBus".equals(event.getPath())
                    && (event.name.equals(LinuxTrayDbus.WATCHER) || event.name.equals(LinuxTrayDbus.FREEDESKTOP_WATCHER))) requestSnapshot();
            }));
            subscriptions.add(discovery.addSigHandler(LinuxTrayDbus.Watcher.StatusNotifierHostRegistered.class, this::hostSignal));
            subscriptions.add(discovery.addSigHandler(LinuxTrayDbus.Watcher.StatusNotifierHostUnregistered.class, this::hostSignal));
            subscriptions.add(discovery.addSigHandler(LinuxTrayDbus.FreedesktopWatcher.StatusNotifierHostRegistered.class, this::hostSignal));
            subscriptions.add(discovery.addSigHandler(LinuxTrayDbus.FreedesktopWatcher.StatusNotifierHostUnregistered.class, this::hostSignal));
        }
        report(currentHost());
    }

    private void hostSignal(DBusSignal signal) {
        if (!WATCHER_PATH.equals(signal.getPath())) return;
        var current = owners;
        if (current.isEmpty() || current.containsValue(signal.getSource())) requestSnapshot();
    }

    private Host currentHost() throws Exception {
        var bus = bus(discovery);
        var found = new java.util.LinkedHashMap<String, String>();
        Host selected = NONE;
        for (String service : List.of(LinuxTrayDbus.WATCHER, LinuxTrayDbus.FREEDESKTOP_WATCHER)) {
            try {
                if (!bus.NameHasOwner(service)) continue;
                String owner = bus.GetNameOwner(service);
                found.put(service, owner);
                Properties properties = discovery.getRemoteObject(owner, WATCHER_PATH, Properties.class);
                Boolean host = properties.Get(service, "IsStatusNotifierHostRegistered");
                if (Boolean.TRUE.equals(host) && bus.NameHasOwner(service) && owner.equals(bus.GetNameOwner(service))) {
                    selected = new Host(service, owner, true);
                    break;
                }
            } catch (Exception unavailable) {
                if (Thread.currentThread().isInterrupted()) throw unavailable;
                // An incompatible watcher must not hide the other standard watcher.
            }
        }
        owners = Map.copyOf(found);
        return selected;
    }

    private void report(Host host) {
        if (!closed && !host.equals(observed)) { observed = host; pendingObservation = host; }
    }

    private void closePublication() {
        generation.incrementAndGet();
        DBusConnection connection = publication;
        publication = null;
        closeConnection(connection);
    }

    private void closeDiscovery() {
        // Closing the owned connection also removes bus subscriptions, without outbound RemoveMatch calls.
        subscriptions.clear();
        owners = Map.of();
        DBusConnection connection = discovery;
        discovery = null;
        discoveryAddress = null;
        discoveryBusId = null;
        closeConnection(connection);
    }

    private static void closeConnection(DBusConnection connection) {
        if (connection == null) return;
        // Interruptible library shutdown skips its ten-second graceful receiver wait when cancelling.
        try { connection.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        synchronized (deadlineLock) {
            if (closed) return;
            generation.incrementAndGet();
            withdrawRequested = true;
            // Reserve cleanup before closing admission. An already active operation performs it in finally.
            if (active.compareAndSet(false, true)) {
                closed = true;
                worker.execute(() -> {
                    Thread.currentThread().interrupt();
                    closePublication(); closeDiscovery();
                    Thread.interrupted();
                    active.set(false);
                    worker.shutdown(); deadlines.shutdownNow();
                });
            } else {
                closed = true;
                Thread thread = running;
                if (thread != null) thread.interrupt();
            }
        }
    }
}
