package dev.jasper.app.platform;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.SwingUtilities;

/** One independently owned XEmbed connection; the constructor performs no native work. */
final class LinuxLegacyTray implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(LinuxLegacyTray.class.getName());
    private static final long BOUND = TimeUnit.SECONDS.toNanos(2);
    private static final long CLEANUP_RESERVE = TimeUnit.MILLISECONDS.toNanos(250);
    record MenuRequest(long epoch, Rectangle rootBounds, Rectangle rootScreenBounds, boolean pointer, IntConsumer action, BooleanSupplier valid, Consumer<PopupHint> hints, long userTime) {
        MenuRequest(long epoch, Rectangle rootBounds, Rectangle rootScreenBounds, boolean pointer, IntConsumer action, BooleanSupplier valid) {
            this(epoch, rootBounds, rootScreenBounds, pointer, action, valid, hint -> hint.complete.accept(false), 0);
        }
        MenuRequest(long epoch, Rectangle rootBounds, Rectangle rootScreenBounds, boolean pointer, IntConsumer action, BooleanSupplier valid, Consumer<PopupHint> hints) {
            this(epoch, rootBounds, rootScreenBounds, pointer, action, valid, hints, 0);
        }
        MenuRequest { rootBounds = new Rectangle(rootBounds); rootScreenBounds = new Rectangle(rootScreenBounds); }
        @Override public Rectangle rootBounds() { return new Rectangle(rootBounds); }
        @Override public Rectangle rootScreenBounds() { return new Rectangle(rootScreenBounds); }
    }
    record PopupHint(long window, boolean activation, BooleanSupplier valid, Consumer<Boolean> complete) {
        PopupHint(long window, Consumer<Boolean> complete) { this(window, false, () -> true, complete); }
    }
    private record HintWork(long token, PopupHint hint, long userTime, long deadline, long connection, long host, int icon) { }
    private HintWork pendingHint, activeHint;
    private static final class Install {
        final long deadline;
        final IntConsumer action;
        Boolean result;
        boolean canceled;
        Install(long deadline, IntConsumer action) { this.deadline = deadline; this.action = action; }
    }
    private final Object lock = new Object();
    private final Map<String, String> launch;
    private final Consumer<Boolean> available;
    private final Consumer<MenuRequest> menu;
    private final Runnable cancelPopup;
    private Thread worker;
    private LinuxTrayXcb.Cancellation cancellation;
    private boolean started, closed, enabled = true, connected, cleanup, installed;
    private Boolean advertised;
    private long admission, cleanupRequested, cleanupFinished;
    private Install pending, active;
    // Below: native-worker-only connection state. No XID escapes this package.
    private LinuxTrayXcb xcb;
    private final Map<String, Integer> atoms = new HashMap<>();
    private int contextPressTime; // guarded by lock; retired with admission
    private int owner, compositor, icon, embedder, announcedEmbedder, serverTime, screen, lastInputTime, lastInputType, lastInputDetail;
    private long connectionEpoch, hostEpoch;
    private volatile boolean probeRequested;
    private LinuxTrayXcb.Visual visual;
    private boolean alpha, dirty, activated = true, modality, focused;
    private volatile boolean mapped, hostMapped;
    private int[] keysyms = new int[0];
    private Rectangle bounds = new Rectangle();
    private IntConsumer action;

    LinuxLegacyTray(Map<String, String> launch, Consumer<Boolean> available, Consumer<MenuRequest> menu, Runnable cancelPopup) {
        this.launch = Map.copyOf(launch); this.available = available; this.menu = menu; this.cancelPopup = cancelPopup;
    }
    void start() {
        synchronized (lock) {
            if (started || closed) return;
            started = true;
            worker = Thread.ofPlatform().daemon().name("jasper-tray-xcb").start(this::run);
        }
    }
    boolean install(IntConsumer action) {
        offEdt(); long end = System.nanoTime() + BOUND;
        Install request;
        synchronized (lock) {
            if (closed || !Boolean.TRUE.equals(advertised)) request = null;
            else {
                if (cleanup) throw new IllegalStateException("Legacy icon cleanup is owed");
                if (installed) return true;
                if (pending != null || active != null) throw new IllegalStateException("Legacy install already pending");
                request = new Install(end - CLEANUP_RESERVE, action); pending = request; wake();
                while (request.result == null && System.nanoTime() < request.deadline) await(request.deadline);
                if (request.result != null) return request.result;
                request.canceled = true; ++admission; contextPressTime = 0; installed = false; cleanup = true; ++cleanupRequested;
                if (cancellation != null) cancellation.requestClose();
                while (socketOwed() && System.nanoTime() < end) await(end);
                if (socketOwed()) throw new IllegalStateException("Legacy icon cleanup remains unconfirmed");
            }
        }
        if (request == null) removeAndAwait(end);
        else cancelPopup.run();
        return false;
    }
    void removeAndAwait() {
        offEdt(); removeAndAwait(System.nanoTime() + BOUND);
    }
    private void removeAndAwait(long end) {
        long ticket;
        synchronized (lock) {
            ++admission; contextPressTime = 0; installed = false;
            if (pending != null) pending.canceled = true;
            if (active != null) active.canceled = true;
            cleanup = true; ticket = ++cleanupRequested; wake();
        }
        cancelPopup.run();
        synchronized (lock) {
            while (cleanupFinished < ticket && socketOwed() && System.nanoTime() < end - CLEANUP_RESERVE) await(end - CLEANUP_RESERVE);
            if (cleanupFinished < ticket && socketOwed() && cancellation != null) cancellation.requestClose();
            while (cleanupFinished < ticket && socketOwed() && System.nanoTime() < end) await(end);
            if (cleanupFinished < ticket && socketOwed()) throw new IllegalStateException("Legacy icon cleanup remains unconfirmed");
        }
    }
    void discoveryEnabled(boolean value) {
        synchronized (lock) {
            if (closed || enabled == value) return;
            enabled = value; probeRequested = value;
            if (!value && !connected && cancellation != null) cancellation.requestClose();
            wake();
        }
    }
    /** Coalesces a fresh owner snapshot on the native worker; production owns the periodic timer. */
    void probe() {
        synchronized (lock) {
            if (closed || !enabled) return;
            probeRequested = true; wake();
        }
    }
    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true; ++admission; contextPressTime = 0; installed = false; cleanup = true; ++cleanupRequested;
            if (pending != null) pending.canceled = true;
            if (active != null) active.canceled = true;
            if (cancellation != null) cancellation.requestClose();
            wake();
        }
        cancelPopup.run();
    }
    private boolean socketOwed() { return connected || cancellation != null && cancellation.socketCleanupOwed(); }
    private void wake() { lock.notifyAll(); if (cancellation != null) cancellation.wakeup(); }
    private void await(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) return;
        try { TimeUnit.NANOSECONDS.timedWait(lock, remaining); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Legacy operation interrupted", e); }
    }
    private static void offEdt() {
        if (SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Legacy native work cannot block EDT");
    }
    private void run() {
        advertise(false);
        for (;;) {
            synchronized (lock) {
                while (!closed && !enabled) await(System.nanoTime() + BOUND);
                if (closed) return;
            }
            try {
                var cancel = new LinuxTrayXcb.Cancellation();
                synchronized (lock) { cancellation = cancel; if (closed || !enabled) cancel.requestClose(); }
                try (cancel) {
                    long deadline = System.nanoTime() + BOUND - CLEANUP_RESERVE;
                    try (var connection = LinuxTrayXcb.connect(launch, deadline, cancel)) {
                        xcb = connection;
                        synchronized (lock) { connected = true; ++connectionEpoch; }
                        initialize(deadline);
                        loop();
                    } finally { xcb = null; }
                }
            } catch (LinuxTrayXcb.Unavailable | LinkageError | IllegalArgumentException failure) {
                LOG.log(System.Logger.Level.DEBUG, "Owned XEmbed connection unavailable", failure);
            } finally {
                HintWork hintPending, hintActive;
                synchronized (lock) {
                    ++admission; contextPressTime = 0; installed = false;
                    hintPending = pendingHint; hintActive = activeHint; pendingHint = null; activeHint = null;
                    connected = false; cancellation = null; cleanup = false; cleanupFinished = cleanupRequested;
                    finish(active, false); finish(pending, false); active = null; pending = null; lock.notifyAll();
                }
                try {
                    invalidate(); owner = 0; icon = 0; embedder = 0; announcedEmbedder = 0; atoms.clear(); advertise(false);
                } finally {
                    // The connection has terminated and admission is retired even if another callback fails.
                    if (hintPending != null) hintPending.hint.complete.accept(false);
                    if (hintActive != null) hintActive.hint.complete.accept(false);
                }
            }
            synchronized (lock) {
                if (closed) return;
                await(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            }
        }
    }
    private void initialize(long deadline) {
        screen = LinuxTrayXcb.parseDisplay(launch.get("DISPLAY")).screen();
        xcb.select(xcb.setup().root(), 1 << 17, deadline);
        for (String name : List.of("_NET_SYSTEM_TRAY_S" + screen, "_NET_WM_CM_S" + screen, "MANAGER", "_NET_SYSTEM_TRAY_OPCODE",
                "_NET_SYSTEM_TRAY_VISUAL", "_NET_SYSTEM_TRAY_ORIENTATION", "_XEMBED", "_XEMBED_INFO",
                "WM_NAME", "WM_CLASS", "_NET_WM_NAME", "UTF8_STRING", "_NET_WM_ICON", "WM_NORMAL_HINTS",
                "WM_SIZE_HINTS", "VISUALID", "CARDINAL")) atoms.put(name, xcb.internAtom(name, deadline));
        keyboard(deadline); snapshot(deadline);
    }
    private void loop() {
        for (;;) {
            Install request;
            synchronized (lock) {
                if (closed) return;
                request = pending; pending = null;
                if (request != null) active = request;
            }
            if (cleanupRequested()) {
                remove(System.nanoTime() + BOUND - CLEANUP_RESERVE);
                synchronized (lock) {
                    cleanup = false; cleanupFinished = cleanupRequested;
                    finish(active, false); active = null; lock.notifyAll();
                }
                continue;
            }
            if (request != null) {
                boolean result = dock(request);
                synchronized (lock) { finish(request, result); active = null; }
                continue;
            }
            HintWork hint;
            synchronized (lock) { hint = pendingHint; pendingHint = null; activeHint = hint; }
            if (hint != null) {
                boolean success = valid(hint.token) && connectionEpoch == hint.connection && hostEpoch == hint.host
                    && icon == hint.icon && hint.hint.valid.getAsBoolean() && System.nanoTime() < hint.deadline;
                if (success) {
                    if (hint.hint.activation) success = xcb.activatePopup((int)hint.hint.window, hint.userTime,
                        () -> valid(hint.token) && hint.hint.valid.getAsBoolean(), hint.deadline);
                    else xcb.popupHints((int)hint.hint.window, hint.deadline);
                }
                synchronized (lock) { activeHint = null; }
                hint.hint.complete.accept(success);
                continue;
            }
            boolean discover;
            synchronized (lock) { discover = enabled; }
            long deadline = System.nanoTime() + BOUND - CLEANUP_RESERVE;
            if (discover && probeRequested) { probeRequested = false; snapshot(deadline); }
            drain(Math.min(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100)), deadline);
            if (dirty && icon != 0 && mapped) paint(deadline);
        }
    }
    private boolean cleanupRequested() { synchronized (lock) { return cleanup || closed; } }
    private void finish(Install request, boolean result) {
        if (request != null && request.result == null) request.result = result;
        lock.notifyAll();
    }
    private int atom(String name) { return atoms.get(name); }
    private void advertise(boolean value) {
        synchronized (lock) { if (closed || Boolean.valueOf(value).equals(advertised)) return; advertised = value; }
        available.accept(value);
    }
    private void invalidate() {
        synchronized (lock) { ++admission; contextPressTime = 0; ++hostEpoch; installed = false; }
        resetXembed(); action = null; cancelPopup.run();
    }
    private void snapshot(long deadline) {
        probeRequested = false;
        int current = xcb.selectionOwner(atom("_NET_SYSTEM_TRAY_S" + screen), deadline);
        int advertisedVisual = current == 0 ? 0 : xcb.property32(current, atom("_NET_SYSTEM_TRAY_VISUAL"), atom("VISUALID"), deadline);
        int visualId = advertisedVisual == 0 ? xcb.setup().rootVisual() : advertisedVisual;
        var candidate = current == 0 ? null : xcb.setup().visuals().stream().filter(v -> v.id() == visualId).findFirst().orElse(null);
        boolean wantsAlpha = candidate != null && xcb.alpha(candidate, deadline);
        int currentCompositor = wantsAlpha ? xcb.selectionOwner(atom("_NET_WM_CM_S" + screen), deadline) : 0;
        if (current == owner && currentCompositor == compositor && (current == 0 || visual != null && visual.id() == visualId)) return;
        invalidate(); advertise(false); remove(deadline); owner = 0; compositor = 0; visual = null;
        if (current == 0 || candidate == null || wantsAlpha && currentCompositor == 0 || !xcb.supports(candidate, wantsAlpha, deadline)) return;
        if (currentCompositor != 0) xcb.select(currentCompositor, 1 << 17, deadline);
        xcb.select(current, (1 << 17) | (1 << 22), deadline); xcb.barrier(deadline);
        if (xcb.selectionOwner(atom("_NET_SYSTEM_TRAY_S" + screen), deadline) != current) { probeRequested = true; return; }
        if (wantsAlpha && xcb.selectionOwner(atom("_NET_WM_CM_S" + screen), deadline) != currentCompositor) { probeRequested = true; return; }
        owner = current; compositor = currentCompositor; alpha = wantsAlpha; visual = candidate; advertise(true);
    }
    private boolean dock(Install request) {
        long deadline = request.deadline;
        synchronized (lock) { if (closed || request.canceled || cleanup || !enabled) return false; }
        snapshot(deadline);
        if (owner == 0 || visual == null) return false;
        int capturedOwner = owner, capturedCompositor = compositor; long capturedHost = hostEpoch, capturedConnection = connectionEpoch;
        icon = xcb.createIcon(xcb.setup().root(), visual, 0, 24, deadline);
        xcb.property8(icon, atom("WM_NAME"), 31, "Jasper".getBytes(StandardCharsets.UTF_8), deadline);
        xcb.property8(icon, atom("_NET_WM_NAME"), atom("UTF8_STRING"), "Jasper".getBytes(StandardCharsets.UTF_8), deadline);
        xcb.property8(icon, atom("WM_CLASS"), 31, "jasper\0Jasper\0".getBytes(StandardCharsets.US_ASCII), deadline);
        var image = raster(24, 24);
        int[] netIcon = new int[2 + 24 * 24]; netIcon[0] = 24; netIcon[1] = 24;
        image.getRGB(0, 0, 24, 24, netIcon, 2, 24);
        xcb.property32(icon, atom("_NET_WM_ICON"), atom("CARDINAL"), netIcon, deadline);
        int[] hints = new int[18]; hints[0] = (1 << 3) | (1 << 4) | (1 << 5); hints[3] = 24; hints[4] = 24;
        hints[5] = 2; hints[6] = 2; hints[7] = 512; hints[8] = 512;
        xcb.property32(icon, atom("WM_NORMAL_HINTS"), atom("WM_SIZE_HINTS"), hints, deadline);
        xcb.property32(icon, atom("_XEMBED_INFO"), atom("_XEMBED_INFO"), new int[] {0, 1}, deadline);
        xcb.sendClientMessage(capturedOwner, capturedOwner, atom("_NET_SYSTEM_TRAY_OPCODE"), 0, serverTime, 0, icon, 0, 0);
        xcb.barrier(deadline);
        while (System.nanoTime() < deadline) {
            boolean canceled;
            synchronized (lock) { canceled = request.canceled || cleanup || closed; }
            if (canceled) { remove(deadline); return false; }
            if (owner != capturedOwner || hostEpoch != capturedHost || connectionEpoch != capturedConnection || icon == 0) {
                remove(deadline); return false;
            }
            if (announcedEmbedder != 0 && xcb.parent(icon, deadline) == announcedEmbedder) {
                if (xcb.selectionOwner(atom("_NET_SYSTEM_TRAY_S" + screen), deadline) != capturedOwner
                        || alpha && xcb.selectionOwner(atom("_NET_WM_CM_S" + screen), deadline) != capturedCompositor) {
                    invalidate(); advertise(false); remove(deadline); probeRequested = true; return false;
                }
                embedder = announcedEmbedder;
                xcb.select(embedder, (1 << 17) | (1 << 22), deadline);
                bounds = xcb.geometryOnRoot(icon, deadline); validateSlot(bounds);
                if (!alpha && xcb.depth(embedder, deadline) != visual.depth()) throw new LinuxTrayXcb.Unavailable("Parent depth mismatch");
                xcb.mapIcon(deadline); mapped = true; hostMapped = true; dirty = true; paint(deadline);
                synchronized (lock) {
                    canceled = closed || request.canceled || cleanup;
                    if (!canceled) { installed = true; action = request.action; }
                }
                if (canceled) { remove(deadline); return false; }
                return true;
            }
            drain(Math.min(deadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50)), deadline);
        }
        throw new LinuxTrayXcb.Unavailable("XEmbed handshake deadline elapsed");
    }
    private void remove(long deadline) {
        if (icon != 0) xcb.destroyIcon(deadline);
        icon = 0; resetXembed();
        synchronized (lock) { installed = false; }
    }
    private void resetXembed() {
        embedder = 0; announcedEmbedder = 0; mapped = false; hostMapped = false; dirty = false;
        activated = true; focused = false; modality = false;
        lastInputTime = 0; lastInputType = 0; lastInputDetail = 0;
    }
    private void drain(long pollDeadline, long operationDeadline) {
        List<LinuxTrayXcb.Event> events = xcb.poll(pollDeadline, operationDeadline);
        for (int batch = 0; batch < 256; batch++) {
            for (var event : events) event(event, operationDeadline);
            if (events.size() < 64 || cleanupRequested()) return;
            events = xcb.poll(Math.min(operationDeadline, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1)), operationDeadline);
        }
    }
    private void event(LinuxTrayXcb.Event event, long deadline) {
        var b = ByteBuffer.wrap(event.bytes()).order(ByteOrder.nativeOrder()); int type = event.type();
        if (type == 0) throw new LinuxTrayXcb.Unavailable("X11 asynchronous request failure");
        if (type == 34) { keyboard(deadline); return; }
        if (type == 33 && b.get(1) == 32) {
            int target = b.getInt(4), message = b.getInt(8);
            if (target == xcb.setup().root() && message == atom("MANAGER") && (b.getInt(16) == atom("_NET_SYSTEM_TRAY_S" + screen) || b.getInt(16) == atom("_NET_WM_CM_S" + screen))) {
                serverTime = b.getInt(12); snapshot(deadline); return;
            }
            if (target != icon || icon == 0 || message != atom("_XEMBED")) return;
            serverTime = b.getInt(12); int opcode = b.getInt(16);
            switch (opcode) {
                case 0 -> {
                    if (b.getInt(24) != 0 && b.getInt(28) == 0) {
                        if (b.getInt(24) == xcb.setup().root()) throw new LinuxTrayXcb.Unavailable("Root is not an XEmbed socket");
                        announcedEmbedder = b.getInt(24);
                    }
                }
                case 1 -> activated = true;
                case 2 -> activated = false;
                case 4 -> focused = true;
                case 5 -> focused = false;
                case 10 -> modality = true;
                case 11 -> modality = false;
                default -> { }
            }
            return;
        }
        if (type == 17 || type == 18 || type == 19 || type == 21 || type == 22) {
            int window = b.getInt(8);
            if (window == owner || compositor != 0 && window == compositor) {
                if (window == owner && type == 18) pauseInteraction();
                if (window == owner && type == 19) hostMapped = true;
                snapshot(deadline); return;
            }
            if (window == icon && icon != 0) {
                if (type == 17) throw new LinuxTrayXcb.Unavailable("Icon destroyed by host");
                if (type == 18) { mapped = false; pauseInteraction(); }
                if (type == 19) { mapped = true; hostMapped = true; dirty = true; }
                if (type == 21 && embedder != 0 && b.getInt(12) != embedder) {
                    invalidate(); advertise(false); remove(deadline); owner = 0; probeRequested = true;
                }
                if (type == 22) dirty = true;
            }
            if (window == embedder) {
                if (type == 18) pauseInteraction();
                if (type == 19) hostMapped = true;
                dirty = true;
            }
            return;
        }
        if (type == 28) {
            int window = b.getInt(4); serverTime = b.getInt(12);
            if (window == owner) snapshot(deadline);
            if (window == embedder) dirty = true;
            if (window == icon && b.getInt(8) == atom("_XEMBED_INFO")) {
                int[] info = xcb.property32s(icon, atom("_XEMBED_INFO"), atom("_XEMBED_INFO"), 2, deadline);
                if (info.length != 2 || info[0] != 0) throw new LinuxTrayXcb.Unavailable("Invalid XEmbed info");
                boolean wasMapped = mapped;
                mapped = (info[1] & 1) != 0; dirty = mapped;
                if (wasMapped && !mapped) {
                    synchronized (lock) { ++admission; contextPressTime = 0; }
                    cancelPopup.run();
                }
            }
            return;
        }
        if (type == 29) { snapshot(deadline); return; }
        if (type == 12 && b.getInt(4) == icon) { dirty = true; return; }
        if ((type == 2 || type == 4 || type == 5) && icon != 0 && b.getInt(12) == icon && mapped && hostMapped && !modality) input(b, type, deadline);
    }
    private void keyboard(long deadline) {
        int first = xcb.firstKeycode(), last = xcb.lastKeycode();
        keysyms = xcb.keysyms(first, last - first + 1, deadline);
    }
    private void input(ByteBuffer b, int type, long deadline) {
        synchronized (lock) { if (!installed || closed || cleanup) return; }
        int detail = Byte.toUnsignedInt(b.get(1)), time = b.getInt(4), modifiers = Short.toUnsignedInt(b.getShort(28));
        if (time == lastInputTime && type == lastInputType && detail == lastInputDetail) return;
        lastInputTime = time; lastInputType = type; lastInputDetail = detail; serverTime = time;
        if (type == 4) {
            if (detail == 3) {
                int x = b.getShort(24), y = b.getShort(26);
                synchronized (lock) { contextPressTime = installed && !closed && !cleanup && x >= 0 && y >= 0 && x < bounds.width && y < bounds.height ? time : 0; }
            }
            return;
        }
        if (type == 5) {
            int pressed = 0;
            if (detail == 3) synchronized (lock) { pressed = contextPressTime; contextPressTime = 0; }
            int x = b.getShort(24), y = b.getShort(26);
            if (x < 0 || y < 0 || x >= bounds.width || y >= bounds.height) return;
            if (detail == 1) { focus(3, deadline); dispatch(1); }
            else if (detail == 3) {
                if (pressed != 0) menu(true, Integer.toUnsignedLong(pressed), deadline);
            }
            return;
        }
        int first = xcb.firstKeycode(), count = xcb.lastKeycode() - first + 1;
        if (detail < first || detail >= first + count) return;
        int perCode = keysyms.length / count, offset = (detail - first) * perCode;
        int symbol = keysyms[offset + ((modifiers & 1) != 0 && perCode > 1 && keysyms[offset + 1] != 0 ? 1 : 0)];
        switch (symbol) {
            case 0xff0d, 0x20 -> { focus(3, deadline); dispatch(1); }
            case 0xff67 -> menu(false, Integer.toUnsignedLong(time), deadline);
            case 0xffc7 -> { if ((modifiers & 1) != 0) menu(false, Integer.toUnsignedLong(time), deadline); }
            case 0xff09, 0xfe20 -> focus(symbol == 0xfe20 || (modifiers & 1) != 0 ? 7 : 6, deadline);
            default -> { }
        }
    }
    private void focus(int opcode, long deadline) {
        if (embedder == 0) return;
        // Refresh the operation deadline before queuing a message with an intentionally narrow signature.
        xcb.operationDeadline(deadline);
        xcb.sendClientMessage(embedder, embedder, atom("_XEMBED"), 0, serverTime, opcode, 0, 0, 0);
        xcb.barrier(deadline);
    }
    private void pauseInteraction() {
        synchronized (lock) { hostMapped = false; ++admission; contextPressTime = 0; }
        cancelPopup.run();
    }
    private boolean valid(long token) { synchronized (lock) { return !closed && installed && !cleanup && mapped && hostMapped && admission == token; } }
    private void dispatch(int id) {
        long token; IntConsumer callback;
        synchronized (lock) { token = admission; callback = action; }
        SwingUtilities.invokeLater(() -> { if (valid(token) && callback != null) callback.accept(id); });
    }
    private void menu(boolean pointer, long userTime, long deadline) {
        bounds = xcb.geometryOnRoot(icon, deadline); validateSlot(bounds);
        long token; IntConsumer callback;
        synchronized (lock) { token = admission; callback = action; }
        var request = new MenuRequest(token, bounds, new Rectangle(0, 0, xcb.setup().width(), xcb.setup().height()), pointer,
            id -> SwingUtilities.invokeLater(() -> { if (valid(token) && callback != null) callback.accept(id); }), () -> valid(token), hint -> hints(token, userTime, hint), userTime);
        SwingUtilities.invokeLater(() -> { if (valid(token)) menu.accept(request); });
    }
    private void hints(long token, long userTime, PopupHint hint) {
        boolean accepted;
        synchronized (lock) {
            accepted = userTime > 0 && userTime <= 0xffffffffL && hint.window > 0 && hint.window <= 0xffffffffL && valid(token) && pendingHint == null && activeHint == null && hint.valid.getAsBoolean();
            if (accepted) { pendingHint = new HintWork(token, hint, userTime, System.nanoTime() + BOUND - CLEANUP_RESERVE, connectionEpoch, hostEpoch, icon); wake(); }
        }
        if (!accepted) hint.complete.accept(false);
    }
    private void paint(long deadline) {
        bounds = xcb.geometryOnRoot(icon, deadline); validateSlot(bounds);
        if (alpha && xcb.selectionOwner(atom("_NET_WM_CM_S" + screen), deadline) != compositor)
            throw new LinuxTrayXcb.Unavailable("Compositor changed before paint");
        xcb.paint(raster(bounds.width, bounds.height), bounds.width, bounds.height, visual, alpha, deadline); dirty = false;
    }
    private static void validateSlot(Rectangle bounds) {
        if (bounds.width < 1 || bounds.height < 1 || bounds.width > 512 || bounds.height > 512)
            throw new LinuxTrayXcb.Unavailable("Unsupported tray slot");
    }
    static BufferedImage raster(int width, int height) {
        validateSlot(new Rectangle(0, 0, width, height));
        var target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var source = ResidentTray.image(ApplicationIcon.images("linux"), new Dimension(width, height));
        double ratio = Math.min((double)width / source.getWidth(null), (double)height / source.getHeight(null));
        int w = Math.max(1, (int)Math.round(source.getWidth(null) * ratio)), h = Math.max(1, (int)Math.round(source.getHeight(null) * ratio));
        var graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
        } finally { graphics.dispose(); }
        return target;
    }
}
