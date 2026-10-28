package dev.jasper.app.platform;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Owned local X11 transport. Loading this class does not load a native library. */
final class LinuxTrayXcb implements AutoCloseable {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern DISPLAY = Pattern.compile("(?:unix/?)?:([0-9]+)(?:\\.([0-9]+))?");

    record Display(int number, int screen) {}
    record Visual(int id, int depth, int redMask, int greenMask, int blueMask) {}
    record PixmapFormat(int depth, int bitsPerPixel, int scanlinePad) {}
    record Setup(int root, int rootVisual, int rootDepth, int imageByteOrder,
                 List<Visual> visuals, List<PixmapFormat> formats) {
        Setup { visuals = List.copyOf(visuals); formats = List.copyOf(formats); }
    }
    record Reply(int sequence, byte[] bytes) {
        Reply { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    record Event(int type, int sequence, byte[] bytes) {
        Event { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }

    static Display parseDisplay(String value) {
        if (value == null) throw new IllegalArgumentException("Local DISPLAY is missing");
        var matcher = DISPLAY.matcher(value);
        if (!matcher.matches()) throw new IllegalArgumentException("Unsupported DISPLAY");
        try {
            int number = Integer.parseInt(matcher.group(1));
            int screen = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            if (number > 65535 || screen > 255) throw new IllegalArgumentException("DISPLAY out of range");
            return new Display(number, screen);
        } catch (NumberFormatException e) { throw new IllegalArgumentException("DISPLAY out of range"); }
    }

    /** Validate framing before Xau decoding so truncated input cannot look like EOF. */
    static int authorityRecords(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Authority snapshot too large");
        ByteBuffer data = ByteBuffer.wrap(bytes); // Xauthority fields use network byte order.
        int records = 0;
        while (data.hasRemaining()) {
            if (++records > 4096) throw new IllegalArgumentException("Too many authority records");
            require(data, 2); data.getShort();
            for (int field = 0; field < 4; field++) {
                require(data, 2);
                int length = Short.toUnsignedInt(data.getShort());
                require(data, length); data.position(data.position() + length);
            }
        }
        return records;
    }

    static Setup decodeSetup(byte[] bytes, int screen) {
        if (bytes.length < 40 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid X11 setup size");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
        if (b.get(0) != 1 || 8 + 4 * Short.toUnsignedInt(b.getShort(6)) != bytes.length)
            throw new IllegalArgumentException("Invalid X11 setup");
        int screens = Byte.toUnsignedInt(b.get(28));
        int order = Byte.toUnsignedInt(b.get(30));
        if (screen < 0 || screen >= screens || order > 1) throw new IllegalArgumentException("Unsupported X11 screen or format");
        int vendor = Short.toUnsignedInt(b.getShort(24));
        b.position(40); require(b, (vendor + 3) & ~3); b.position(40 + ((vendor + 3) & ~3));
        var formats = new ArrayList<PixmapFormat>();
        for (int i = 0; i < Byte.toUnsignedInt(b.get(29)); i++) {
            require(b, 8); int p = b.position();
            formats.add(new PixmapFormat(Byte.toUnsignedInt(b.get(p)), Byte.toUnsignedInt(b.get(p + 1)), Byte.toUnsignedInt(b.get(p + 2))));
            b.position(p + 8);
        }
        Setup selected = null;
        for (int i = 0; i < screens; i++) {
            require(b, 40); int p = b.position();
            int root = b.getInt(p), visual = b.getInt(p + 32), depth = Byte.toUnsignedInt(b.get(p + 38));
            int depths = Byte.toUnsignedInt(b.get(p + 39)); b.position(p + 40);
            var visuals = new ArrayList<Visual>();
            for (int d = 0; d < depths; d++) {
                require(b, 8); p = b.position(); int vd = Byte.toUnsignedInt(b.get(p));
                int count = Short.toUnsignedInt(b.getShort(p + 2)); b.position(p + 8);
                for (int v = 0; v < count; v++) {
                    require(b, 24); p = b.position();
                    visuals.add(new Visual(b.getInt(p), vd, b.getInt(p + 8), b.getInt(p + 12), b.getInt(p + 16)));
                    b.position(p + 24);
                }
            }
            if (i == screen) selected = new Setup(root, visual, depth, order, visuals, formats);
        }
        if (b.hasRemaining()) throw new IllegalArgumentException("Trailing X11 setup data");
        return selected;
    }

    private static void require(ByteBuffer b, int size) {
        if (size < 0 || b.remaining() < size) throw new IllegalArgumentException("Truncated native data");
    }
    @Structure.FieldOrder("sequence")
    public static class Cookie extends Structure implements Structure.ByValue {
        public int sequence;
    }
    @Structure.FieldOrder({"namelen", "name", "datalen", "data"})
    public static class Auth extends Structure {
        public int namelen; public Pointer name; public int datalen; public Pointer data;
    }
    public interface Xcb extends Library {
        Pointer xcb_connect_to_fd(int fd, Auth auth);
        Pointer xcb_get_setup(Pointer connection);
        int xcb_connection_has_error(Pointer connection);
        int xcb_get_file_descriptor(Pointer connection);
        int xcb_generate_id(Pointer connection);
        int xcb_flush(Pointer connection);
        Pointer xcb_poll_for_event(Pointer connection);
        int xcb_poll_for_reply(Pointer connection, int sequence,
            PointerByReference reply, PointerByReference error);
        Cookie xcb_get_input_focus(Pointer connection);
        Cookie xcb_intern_atom(Pointer connection, byte onlyIfExists, short length, byte[] name);
        Cookie xcb_get_selection_owner(Pointer connection, int selection);
        void xcb_disconnect(Pointer connection);
    }
    public interface Libc extends Library {
        int socket(int domain, int type, int protocol);
        int connect(int fd, Pointer address, int length);
        int getsockopt(int fd, int level, int option, IntByReference value, IntByReference length);
        int poll(Pointer fds, NativeLong count, int milliseconds);
        int fcntl(int fd, int command, int value);
        int dup(int fd);
        int shutdown(int fd, int how);
        int close(int fd);
        int pipe2(int[] fds, int flags);
        NativeLong read(int fd, Pointer bytes, NativeLong count);
        NativeLong write(int fd, Pointer bytes, NativeLong count);
        int open(String path, int flags);
        int fstat(int fd, Pointer stat);
        int gethostname(Pointer name, NativeLong size);
        Pointer fmemopen(Pointer bytes, NativeLong size, String mode);
        int fclose(Pointer stream);
        void free(Pointer memory);
    }
    public interface Xau extends Library {
        Pointer XauReadAuth(Pointer stream);
        void XauDisposeAuth(Pointer auth);
    }

    static final class Unavailable extends IllegalStateException {
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message, cause); }
    }
    private static final class Bindings {
        final Libc libc;
        final Xcb xcb;
        final Xau xau;
        // Keep the render library loaded for this connection's lifetime; operations are added with the icon adapter.
        final NativeLibrary render;
        Bindings() {
            if (!System.getProperty("os.name").equals("Linux")) throw new Unavailable("XCB requires Linux");
            NativeStat.modeOffset();
            if (Native.POINTER_SIZE != 8 || Native.LONG_SIZE != 8)
                throw new Unavailable("Unsupported native word size");
            try {
                libc = Native.load("c", Libc.class);
                xcb = Native.load("xcb", Xcb.class);
                xau = Native.load("Xau", Xau.class);
                render = NativeLibrary.getInstance("xcb-render");
                render.getFunction("xcb_render_query_pict_formats");
                // Resolve every boundary symbol before resources are acquired.
                for (var type : List.of(Libc.class, Xcb.class, Xau.class)) {
                    var library = NativeLibrary.getInstance(type == Libc.class ? "c" : type == Xcb.class ? "xcb" : "Xau");
                    for (var method : type.getDeclaredMethods()) library.getFunction(method.getName());
                }
            } catch (LinkageError | RuntimeException e) { throw new Unavailable("XCB native boundary unavailable", e); }
        }
    }

    /** Published before connect; only wakeup/requestClose may run on another thread. */
    static final class Cancellation implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final Bindings bindings = new Bindings();
        private final Object lock = new Object();
        private final int[] wake = {-1, -1};
        private final Memory wakeByte = new Memory(1);
        private boolean closed, disposed, wakePending, admitted;
        private long epoch;
        private int duplicate = -1;
        private ScheduledFuture<?> timer;

        Cancellation() {
            wakeByte.setByte(0, (byte)1);
            if (bindings.libc.pipe2(wake, 0x800 | 0x80000) != 0) {
                wakeByte.close(); throw new Unavailable("XCB wake pipe unavailable");
            }
        }
        private void worker() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("XCB worker ownership violated");
        }
        private void checkOpen() {
            worker();
            synchronized (lock) {
                if (closed || disposed) throw new Unavailable("XCB canceled");
            }
        }
        private void check(long deadline) {
            checkOpen();
            if (System.nanoTime() >= deadline) throw new Unavailable("XCB deadline elapsed");
        }
        private void admit(long deadline) {
            check(deadline);
            synchronized (lock) {
                if (admitted) throw new IllegalStateException("XCB attempt already owned");
                admitted = true;
            }
        }
        private void release() { worker(); synchronized (lock) { admitted = false; } }
        private void arm(int fd, long deadline) {
            check(deadline);
            synchronized (lock) {
                if (closed || disposed) throw new Unavailable("XCB closed");
                if (duplicate >= 0) throw new IllegalStateException("Nested XCB native operation");
                duplicate = bindings.libc.fcntl(fd, 1030, 0);
                if (duplicate < 0) throw new Unavailable("XCB cancellation FD unavailable");
                long ticket = ++epoch;
                timer = Deadlines.EXECUTOR.schedule(() -> expire(ticket), Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        }
        private void expire(long ticket) {
            synchronized (lock) {
                if (epoch == ticket && duplicate >= 0) {
                    closed = true;
                    bindings.libc.shutdown(duplicate, 2);
                    wakeLocked();
                }
            }
        }
        private void disarm() {
            worker();
            synchronized (lock) {
                ++epoch;
                if (timer != null) { timer.cancel(false); timer = null; }
                if (duplicate >= 0) { bindings.libc.close(duplicate); duplicate = -1; }
            }
        }
        void wakeup() { synchronized (lock) { wakeLocked(); } }
        private void wakeLocked() {
            if (disposed || wakePending) return;
            long n = bindings.libc.write(wake[1], wakeByte, new NativeLong(1)).longValue();
            if (n == 1 || Native.getLastError() == 11) wakePending = true;
        }
        void requestClose() {
            synchronized (lock) {
                if (disposed) return;
                closed = true; ++epoch;
                if (duplicate >= 0) bindings.libc.shutdown(duplicate, 2);
                wakeLocked();
            }
        }
        private void drainWake() {
            worker();
            synchronized (lock) {
                if (!disposed && wakePending) {
                    bindings.libc.read(wake[0], wakeByte, new NativeLong(1));
                    wakePending = false;
                }
            }
        }
        @Override public void close() {
            worker();
            synchronized (lock) {
                if (disposed) return;
                if (admitted) throw new IllegalStateException("Close the owned XCB connection first");
                disarm(); closed = true; disposed = true;
                bindings.libc.close(wake[0]); bindings.libc.close(wake[1]); wakeByte.close();
            }
        }
    }
    private static final class Deadlines {
        static final ScheduledThreadPoolExecutor EXECUTOR = create();
        private static ScheduledThreadPoolExecutor create() {
            var executor = new ScheduledThreadPoolExecutor(1,
                runnable -> Thread.ofPlatform().daemon().name("jasper-xcb-deadline").unstarted(runnable));
            executor.setRemoveOnCancelPolicy(true);
            executor.setKeepAliveTime(100, TimeUnit.MILLISECONDS);
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
    }
    private record NativeStat(long device, long inode, long size, int mode,
                              long modifiedSeconds, long modifiedNanos, long changedSeconds, long changedNanos) {
        static int noFollow() {
            return switch (System.getProperty("os.arch")) {
                case "amd64", "x86_64" -> 0x20000;
                case "aarch64", "arm64" -> 0x8000;
                default -> throw new Unavailable("Unsupported Linux open ABI");
            };
        }
        static int modeOffset() {
            return switch (System.getProperty("os.arch")) {
                case "amd64", "x86_64" -> 24;
                case "aarch64", "arm64" -> 16;
                default -> throw new Unavailable("Unsupported Linux stat ABI");
            };
        }
        static NativeStat read(Libc libc, int fd) {
            try (var memory = new Memory(256)) {
                memory.clear();
                if (libc.fstat(fd, memory) != 0) throw new Unavailable("Authority stat failed");
                var value = new NativeStat(memory.getLong(0), memory.getLong(8), memory.getLong(48), memory.getInt(modeOffset()),
                    memory.getLong(88), memory.getLong(96), memory.getLong(104), memory.getLong(112));
                if ((value.mode & 0170000) != 0100000 || value.size < 0 || value.size > MAX_BYTES)
                    throw new Unavailable("Authority must be a bounded regular file");
                return value;
            }
        }
    }
    private static byte[] snapshot(Map<String, String> launch, Cancellation cancel, long deadline) {
        String path = launch.get("XAUTHORITY");
        if (path == null || path.isEmpty()) {
            String home = launch.get("HOME");
            if (home == null || home.isEmpty()) return new byte[0];
            path = home + "/.Xauthority";
        }
        if (path.indexOf('\0') >= 0) throw new Unavailable("Invalid authority path");
        Libc libc = cancel.bindings.libc;
        cancel.check(deadline);
        int fd = libc.open(path, 0x800 | 0x80000 | NativeStat.noFollow());
        int error = Native.getLastError();
        if (fd < 0) {
            cancel.check(deadline);
            if (error == 2) return new byte[0];
            throw new Unavailable("Authority open failed");
        }
        try {
            cancel.check(deadline);
            NativeStat before = NativeStat.read(libc, fd); cancel.check(deadline);
            byte[] result = new byte[(int)before.size]; int offset = 0;
            try (var memory = new Memory(16384)) {
                while (offset < result.length) {
                    cancel.check(deadline);
                    long got = libc.read(fd, memory, new NativeLong(Math.min(16384, result.length - offset))).longValue();
                    error = Native.getLastError(); cancel.check(deadline);
                    if (got < 0 && error == 4) continue;
                    if (got <= 0) throw new Unavailable("Authority snapshot changed");
                    memory.read(0, result, offset, (int)got); offset += (int)got;
                }
                cancel.check(deadline);
                long extra = libc.read(fd, memory, new NativeLong(1)).longValue();
                cancel.check(deadline);
                if (extra != 0) throw new Unavailable("Authority snapshot changed");
            }
            NativeStat after = NativeStat.read(libc, fd); cancel.check(deadline);
            if (!before.equals(after)) throw new Unavailable("Authority snapshot changed");
            return result;
        } finally { libc.close(fd); }
    }
    private static final class Authority implements AutoCloseable {
        final Auth auth = new Auth();
        Memory name, data;
        Authority(byte[] cookie) {
            if (cookie != null) {
                byte[] protocol = "MIT-MAGIC-COOKIE-1".getBytes(StandardCharsets.US_ASCII);
                name = new Memory(protocol.length); data = new Memory(cookie.length);
                name.write(0, protocol, 0, protocol.length); data.write(0, cookie, 0, cookie.length);
                auth.namelen = protocol.length; auth.name = name; auth.datalen = cookie.length; auth.data = data;
            }
        }
        @Override public void close() {
            if (name != null) name.close();
            if (data != null) { data.clear(); data.close(); }
            auth.getPointer().clear(auth.size());
            ((Memory)auth.getPointer()).close();
        }
    }
    private static Authority authority(byte[] bytes, Display display, Cancellation cancel, long deadline) {
        int count = authorityRecords(bytes);
        if (count == 0) return new Authority(null);
        Libc libc = cancel.bindings.libc;
        byte[] local = null, wild = null;
        try (var hostMemory = new Memory(256); var input = new Memory(bytes.length)) {
            hostMemory.clear();
            if (libc.gethostname(hostMemory, new NativeLong(256)) != 0) throw new Unavailable("Local hostname unavailable");
            byte[] host = hostMemory.getString(0, "UTF-8").getBytes(StandardCharsets.UTF_8);
            byte[] number = Integer.toString(display.number()).getBytes(StandardCharsets.US_ASCII);
            byte[] protocol = "MIT-MAGIC-COOKIE-1".getBytes(StandardCharsets.US_ASCII);
            input.write(0, bytes, 0, bytes.length);
            Pointer stream = libc.fmemopen(input, new NativeLong(bytes.length), "rb");
            if (stream == null) throw new Unavailable("Authority decoder unavailable");
            try {
                for (int i = 0; i < count; i++) {
                    cancel.check(deadline);
                    Pointer record = cancel.bindings.xau.XauReadAuth(stream);
                    if (record == null) throw new Unavailable("Malformed authority record");
                    try {
                        int family = Short.toUnsignedInt(record.getShort(0));
                        if (Arrays.equals(field(record, 32, 40), protocol) && Short.toUnsignedInt(record.getShort(48)) == 16) {
                            boolean sameNumber = Arrays.equals(field(record, 16, 24), number);
                            if (sameNumber && family == 256 && Arrays.equals(field(record, 2, 8), host) && local == null)
                                local = field(record, 48, 56);
                            if (sameNumber && family == 65535 && wild == null) wild = field(record, 48, 56);
                        }
                    } finally { cancel.bindings.xau.XauDisposeAuth(record); }
                }
            } finally { libc.fclose(stream); }
        }
        cancel.check(deadline);
        return new Authority(local == null ? wild : local);
    }
    private static byte[] field(Pointer record, int lengthOffset, int pointerOffset) {
        int length = Short.toUnsignedInt(record.getShort(lengthOffset));
        if (length == 0) return new byte[0];
        Pointer value = record.getPointer(pointerOffset);
        if (value == null) throw new Unavailable("Invalid authority field");
        return value.getByteArray(0, length);
    }

    private final Cancellation cancellation;
    private final Bindings bindings;
    private Pointer connection;
    private int socketFd = -1;
    private Setup setup;
    private boolean disposed;
    private byte[] setupBytes;
    private final Set<Integer> outstanding = new HashSet<>();
    private final ArrayDeque<Event> pendingEvents = new ArrayDeque<>();

    private LinuxTrayXcb(Cancellation cancellation) { this.cancellation = cancellation; bindings = cancellation.bindings; }
    static LinuxTrayXcb connect(Map<String, String> launch, long deadline, Cancellation cancellation) {
        Display display = parseDisplay(launch.get("DISPLAY"));
        cancellation.admit(deadline);
        var result = new LinuxTrayXcb(cancellation);
        try {
            try (Authority auth = authority(snapshot(launch, cancellation, deadline), display, cancellation, deadline)) {
                cancellation.check(deadline);
                result.socketFd = result.openSocket(display, deadline);
                result.operation(deadline, () -> {
                    // Ownership is transferred even when XCB returns its static error connection.
                    int fd = result.socketFd;
                    result.connection = result.bindings.xcb.xcb_connect_to_fd(fd, auth.auth);
                    return null;
                });
                result.checkConnection();
                result.setupBytes = result.operation(deadline, () -> {
                    var pointer = result.bindings.xcb.xcb_get_setup(result.connection);
                    if (pointer == null) throw new Unavailable("Missing X11 setup");
                    int size = 8 + 4 * Short.toUnsignedInt(pointer.getShort(6));
                    if (size < 40 || size > MAX_BYTES) throw new Unavailable("Invalid X11 setup size");
                    return pointer.getByteArray(0, size);
                });
                result.setup = decodeSetup(result.setupBytes, display.screen());
            }
            return result;
        } catch (RuntimeException | LinkageError e) {
            result.close();
            if (e instanceof Unavailable unavailable) throw unavailable;
            throw new Unavailable("XCB connection unavailable", e);
        }
    }
    private int openSocket(Display display, long deadline) {
        byte[] path = ("/tmp/.X11-unix/X" + display.number()).getBytes(StandardCharsets.US_ASCII);
        for (boolean abstractAddress : new boolean[] {false, true}) {
            cancellation.check(deadline);
            int fd = bindings.libc.socket(1, 1 | 0x800 | 0x80000, 0);
            if (fd < 0) throw new Unavailable("X11 socket unavailable");
            boolean accepted = false;
            try (var address = new Memory(110)) {
                address.clear(); address.setShort(0, (short)1); address.write(abstractAddress ? 3 : 2, path, 0, path.length);
                int connected = bindings.libc.connect(fd, address, path.length + 3);
                int error = Native.getLastError();
                if (connected < 0 && error == 115) {
                    waitSocket(fd, 4, deadline);
                    var value = new IntByReference();
                    var length = new IntByReference(4);
                    try {
                        connected = bindings.libc.getsockopt(fd, 1, 4, value, length) == 0 && value.getValue() == 0 ? 0 : -1;
                    } finally {
                        ((Memory)value.getPointer()).close(); ((Memory)length.getPointer()).close();
                    }
                }
                if (connected == 0) { cancellation.check(deadline); accepted = true; return fd; }
            } finally { if (!accepted) bindings.libc.close(fd); }
        }
        throw new Unavailable("Local X11 connection unavailable");
    }
    private <T> T operation(long deadline, Supplier<T> action) {
        cancellation.arm(socketFd, deadline);
        try { T result = action.get(); cancellation.check(deadline); return result; }
        finally { cancellation.disarm(); }
    }
    private void checkConnection() {
        if (connection == null || bindings.xcb.xcb_connection_has_error(connection) != 0)
            throw new Unavailable("X11 connection failed");
    }
    Setup setup() { cancellation.worker(); return setup; }
    int newId() {
        cancellation.check(Long.MAX_VALUE); checkConnection();
        return operation(System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
            () -> bindings.xcb.xcb_generate_id(connection));
    }
    private int requestAtom(String name, long deadline) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) throw new IllegalArgumentException("Atom name too long");
        return request(deadline, () -> bindings.xcb.xcb_intern_atom(connection, (byte)0, (short)bytes.length, bytes));
    }
    private int request(long deadline, Supplier<Cookie> action) {
        cancellation.check(deadline); checkConnection();
        if (outstanding.size() >= 64) throw new Unavailable("Too many X11 replies outstanding");
        int sequence = operation(deadline, () -> {
            Cookie cookie = action.get();
            try { return cookie.sequence; }
            finally { ((Memory)cookie.getPointer()).close(); }
        });
        checkConnection();
        if (!outstanding.add(sequence)) throw new Unavailable("X11 sequence reused");
        return sequence;
    }
    int internAtom(String name, long deadline) { return integerReply(requestAtom(name, deadline), deadline); }
    int selectionOwner(int atom, long deadline) {
        return integerReply(request(deadline, () -> bindings.xcb.xcb_get_selection_owner(connection, atom)), deadline);
    }
    void barrier(long deadline) {
        reply(request(deadline, () -> bindings.xcb.xcb_get_input_focus(connection)), deadline);
        // Preserve non-error events for the worker; a barrier must not consume host/input observations.
        List<Event> events = drainEvents(deadline);
        int retainedBytes = events.stream().mapToInt(event -> event.bytes.length).sum()
            + pendingEvents.stream().mapToInt(event -> event.bytes.length).sum();
        if (events.size() == 64 || events.size() + pendingEvents.size() > 64 || retainedBytes > 256 * 1024)
            throw new Unavailable("X11 barrier event budget exceeded");
        for (Event event : events) {
            if (event.type == 0) throw new Unavailable("X11 request failed at sequence " + event.sequence);
            pendingEvents.add(event);
        }
    }
    private int integerReply(int sequence, long deadline) {
        return ByteBuffer.wrap(reply(sequence, deadline).bytes).order(ByteOrder.nativeOrder()).getInt(8);
    }
    private void flush(long deadline) {
        int sent = operation(deadline, () -> bindings.xcb.xcb_flush(connection));
        if (sent <= 0) throw new Unavailable("X11 flush failed");
    }
    private Reply reply(int sequence, long deadline) {
        flush(deadline);
        try {
            for (;;) {
                var reply = new PointerByReference();
                var error = new PointerByReference();
                int ready;
                try {
                    ready = operation(deadline, () -> bindings.xcb.xcb_poll_for_reply(connection, sequence, reply, error));
                    if (error.getValue() != null) throw new Unavailable("X11 request failed at sequence " + sequence);
                    if (reply.getValue() != null) {
                        var pointer = reply.getValue();
                        long size = 32L + 4L * Integer.toUnsignedLong(pointer.getInt(4));
                        if (size > MAX_BYTES) throw new Unavailable("X11 reply too large");
                        return new Reply(sequence, pointer.getByteArray(0, (int)size));
                    }
                } finally {
                    if (reply.getValue() != null) bindings.libc.free(reply.getValue());
                    if (error.getValue() != null) bindings.libc.free(error.getValue());
                    ((Memory)reply.getPointer()).close();
                    ((Memory)error.getPointer()).close();
                }
                checkConnection();
                if (ready != 0) throw new Unavailable("Missing X11 reply");
                waitSocket(socketFd, 1, deadline);
            }
        } finally { outstanding.remove(sequence); }
    }
    List<Event> poll(long deadline) {
        cancellation.checkOpen();
        if (System.nanoTime() >= deadline) return List.of();
        if (!pendingEvents.isEmpty()) {
            List<Event> events = List.copyOf(pendingEvents); pendingEvents.clear(); return events;
        }
        List<Event> events = drainEvents(deadline);
        if (!events.isEmpty()) return events;
        if (!waitSocket(socketFd, 1, deadline, true)) return List.of();
        return drainEvents(deadline);
    }
    private List<Event> drainEvents(long deadline) {
        var events = new ArrayList<Event>(); int bytes = 0;
        while (events.size() < 64 && bytes < 256 * 1024) {
            // Store the pointer before checking post-call cancellation so cleanup still owns it.
            final Pointer[] holder = new Pointer[1];
            try {
                operation(deadline, () -> { holder[0] = bindings.xcb.xcb_poll_for_event(connection); return null; });
                var event = holder[0];
                if (event == null) { checkConnection(); break; }
                int type = Byte.toUnsignedInt(event.getByte(0)) & 0x7f;
                long size = type == 35 ? 36L + 4L * Integer.toUnsignedLong(event.getInt(4)) : 36;
                if (size > 256 * 1024 - bytes) throw new Unavailable("X11 event batch too large");
                events.add(new Event(type, event.getInt(32), event.getByteArray(0, (int)size))); bytes += (int)size;
            } finally { if (holder[0] != null) bindings.libc.free(holder[0]); }
        }
        return List.copyOf(events);
    }
    private void waitSocket(int fd, int events, long deadline) {
        waitSocket(fd, events, deadline, false);
    }
    private boolean waitSocket(int fd, int events, long deadline, boolean idle) {
        cancellation.checkOpen();
        try (var poll = new Memory(16)) {
            poll.clear(); poll.setInt(0, fd); poll.setShort(4, (short)events);
            poll.setInt(8, cancellation.wake[0]); poll.setShort(12, (short)1);
            for (;;) {
                cancellation.checkOpen();
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    if (idle) return false;
                    throw new Unavailable("XCB deadline elapsed");
                }
                int milliseconds = (int)Math.min(Integer.MAX_VALUE, Math.max(1, (remaining + 999999) / 1000000));
                int ready = bindings.libc.poll(poll, new NativeLong(2), milliseconds);
                int error = Native.getLastError();
                cancellation.drainWake(); cancellation.checkOpen();
                if (System.nanoTime() >= deadline) {
                    if (idle) return false;
                    throw new Unavailable("XCB deadline elapsed");
                }
                if (ready < 0 && error == 4) continue;
                if (ready < 0) throw new Unavailable("X11 poll failed");
                return true;
            }
        }
    }
    void wakeup() { cancellation.wakeup(); }
    void requestClose() { cancellation.requestClose(); }
    @Override public void close() {
        cancellation.worker();
        if (disposed) return;
        disposed = true;
        cancellation.disarm();
        if (connection != null) { bindings.xcb.xcb_disconnect(connection); connection = null; socketFd = -1; }
        else if (socketFd >= 0) { bindings.libc.close(socketFd); socketFd = -1; }
        outstanding.clear(); pendingEvents.clear(); cancellation.release();
    }
}
