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
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
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
    record Setup(int root, int rootVisual, int rootDepth, int imageByteOrder, int width, int height,
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

    /** Premultiplied device pixels in the server's channel masks, byte order and row alignment. */
    static byte[] pixels(BufferedImage image, int bitsPerPixel, int scanlinePad, int byteOrder,
                         int redMask, int greenMask, int blueMask, int alphaMask) {
        int width = image.getWidth(), height = image.getHeight();
        if (width < 1 || height < 1 || width > 512 || height > 512
                || (bitsPerPixel != 16 && bitsPerPixel != 24 && bitsPerPixel != 32)
                || (scanlinePad != 16 && scanlinePad != 32) || (byteOrder != 0 && byteOrder != 1))
            throw new IllegalArgumentException("Unsupported native pixel layout");
        int occupied = 0;
        for (int mask : new int[] {redMask, greenMask, blueMask, alphaMask}) {
            if (mask == 0) continue;
            long unsigned = Integer.toUnsignedLong(mask);
            long normalized = unsigned >>> Integer.numberOfTrailingZeros(mask);
            if ((normalized & (normalized + 1)) != 0 || (occupied & mask) != 0
                    || (unsigned >>> bitsPerPixel) != 0)
                throw new IllegalArgumentException("Unsupported native channel masks");
            occupied |= mask;
        }
        if (redMask == 0 || greenMask == 0 || blueMask == 0)
            throw new IllegalArgumentException("Missing native colour mask");
        int stride = ((width * bitsPerPixel + scanlinePad - 1) / scanlinePad) * (scanlinePad / 8);
        if ((long)width * height * 4 + (long)stride * height > 4 * 1024 * 1024)
            throw new IllegalArgumentException("Native image budget exceeded");
        byte[] result = new byte[stride * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int argb = image.getRGB(x, y), alpha = argb >>> 24;
            int pixel = channel(((argb >>> 16 & 255) * alpha + 127) / 255, redMask)
                | channel(((argb >>> 8 & 255) * alpha + 127) / 255, greenMask)
                | channel(((argb & 255) * alpha + 127) / 255, blueMask) | channel(alpha, alphaMask);
            int bytes = bitsPerPixel / 8;
            for (int i = 0; i < bytes; i++)
                result[y * stride + x * bytes + i] = (byte)(pixel >>> (8 * (byteOrder == 0 ? i : bytes - 1 - i)));
        }
        return result;
    }
    private static int channel(int value, int mask) {
        if (mask == 0) return 0;
        int shift = Integer.numberOfTrailingZeros(mask);
        long maximum = Integer.toUnsignedLong(mask) >>> shift;
        return (int)(((value * maximum + 127) / 255) << shift);
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
            int width = Short.toUnsignedInt(b.getShort(p + 20)), height = Short.toUnsignedInt(b.getShort(p + 22));
            if (width == 0 || height == 0) throw new IllegalArgumentException("Empty X11 screen");
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
            if (i == screen) selected = new Setup(root, visual, depth, order, width, height, visuals, formats);
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
        Cookie xcb_change_window_attributes(Pointer c, int window, int mask, int[] values);
        Cookie xcb_change_property(Pointer c, byte mode, int window, int property, int type, byte format, int length, byte[] data);
        Cookie xcb_get_property(Pointer c, byte delete, int window, int property, int type, int offset, int length);
        Cookie xcb_query_tree(Pointer c, int window);
        Cookie xcb_get_geometry(Pointer c, int drawable);
        Cookie xcb_translate_coordinates(Pointer c, int source, int destination, short x, short y);
        Cookie xcb_create_window(Pointer c, byte depth, int window, int parent, short x, short y,
            short width, short height, short border, short windowClass, int visual, int mask, int[] values);
        Cookie xcb_map_window(Pointer c, int window);
        Cookie xcb_unmap_window(Pointer c, int window);
        Cookie xcb_destroy_window(Pointer c, int window);
        Cookie xcb_create_colormap(Pointer c, byte allocation, int colormap, int window, int visual);
        Cookie xcb_free_colormap(Pointer c, int colormap);
        Cookie xcb_send_event(Pointer c, byte propagate, int destination, int mask, byte[] event);
        Cookie xcb_get_keyboard_mapping(Pointer c, byte first, byte count);
        Cookie xcb_clear_area(Pointer c, byte exposures, int window, short x, short y, short width, short height);
        Cookie xcb_create_pixmap(Pointer c, byte depth, int pixmap, int drawable, short width, short height);
        Cookie xcb_free_pixmap(Pointer c, int pixmap);
        Cookie xcb_create_gc(Pointer c, int gc, int drawable, int mask, int[] values);
        Cookie xcb_free_gc(Pointer c, int gc);
        Cookie xcb_put_image(Pointer c, byte format, int drawable, int gc, short width, short height,
            short x, short y, byte leftPad, byte depth, int length, byte[] data);
        void xcb_disconnect(Pointer connection);
    }
    public interface Render extends Library {
        Cookie xcb_render_query_version(Pointer c, int major, int minor);
        Cookie xcb_render_query_pict_formats(Pointer c);
        Cookie xcb_render_create_picture(Pointer c, int picture, int drawable, int format, int mask, int[] values);
        Cookie xcb_render_free_picture(Pointer c, int picture);
        Cookie xcb_render_composite(Pointer c, byte op, int source, int mask, int destination,
            short sx, short sy, short mx, short my, short dx, short dy, short width, short height);
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
        final Render render;
        Bindings() {
            if (!System.getProperty("os.name").equals("Linux")) throw new Unavailable("XCB requires Linux");
            NativeStat.modeOffset();
            if (Native.POINTER_SIZE != 8 || Native.LONG_SIZE != 8)
                throw new Unavailable("Unsupported native word size");
            try {
                libc = Native.load("c", Libc.class);
                xcb = Native.load("xcb", Xcb.class);
                xau = Native.load("Xau", Xau.class);
                render = Native.load("xcb-render", Render.class);
                // Resolve every boundary symbol before resources are acquired.
                for (var type : List.of(Libc.class, Xcb.class, Xau.class, Render.class)) {
                    var library = NativeLibrary.getInstance(type == Libc.class ? "c" : type == Xcb.class ? "xcb" : type == Xau.class ? "Xau" : "xcb-render");
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
        private boolean socketPhase;
        boolean socketCleanupOwed() { synchronized (lock) { return admitted && socketPhase; } }
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
        private void release() { worker(); synchronized (lock) { admitted = false; socketPhase = false; } }
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
    private int icon, ownedColormap;
    private final Drawing drawing = new Drawing();
    private long operationDeadline;
    private final Set<Integer> mutations = new HashSet<>();
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
                synchronized (cancellation.lock) { cancellation.socketPhase = true; }
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
        operationDeadline = deadline;
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
    private int newId(long deadline) {
        cancellation.check(deadline); checkConnection();
        return operation(deadline, () -> bindings.xcb.xcb_generate_id(connection));
    }
    private void mutate(long deadline, Supplier<Cookie> action) {
        cancellation.check(deadline); checkConnection();
        if (mutations.size() >= 256) barrier(deadline);
        int sequence = operation(deadline, () -> {
            Cookie cookie = action.get();
            try { return cookie.sequence; }
            finally { ((Memory)cookie.getPointer()).close(); }
        });
        if (!mutations.add(sequence)) throw new Unavailable("X11 mutation sequence reused");
        checkConnection();
    }
    void select(int window, int mask, long deadline) {
        mutate(deadline, () -> bindings.xcb.xcb_change_window_attributes(connection, window, 1 << 11, new int[] {mask}));
    }
    int property32(int window, int atom, int type, long deadline) {
        int[] value = property32s(window, atom, type, 1, deadline);
        return value.length == 0 ? 0 : value[0];
    }
    int[] property32s(int window, int atom, int type, int max, long deadline) {
        if (max < 1 || max > 262136) throw new IllegalArgumentException("Property budget");
        var b = buffer(reply(request(deadline, () -> bindings.xcb.xcb_get_property(connection,
            (byte)0, window, atom, type, 0, max)), deadline).bytes);
        int actual = b.getInt(8), count = b.getInt(16);
        if (actual == 0 && count == 0 && b.get(1) == 0 && b.getInt(12) == 0) return new int[0];
        if (actual != type || b.get(1) != 32 || count < 0 || count > max || b.getInt(12) != 0
                || b.capacity() != 32 + count * 4) throw new Unavailable("Unexpected X11 property");
        int[] result = new int[count]; for (int i = 0; i < count; i++) result[i] = b.getInt(32 + i * 4);
        return result;
    }
    void property32(int window, int atom, int type, int[] data, long deadline) {
        if (data.length > 262136) throw new IllegalArgumentException("Property budget");
        var bytes = ByteBuffer.allocate(data.length * 4).order(ByteOrder.nativeOrder());
        for (int value : data) bytes.putInt(value);
        property(window, atom, type, (byte)32, data.length, bytes.array(), deadline);
    }
    void property8(int window, int atom, int type, byte[] data, long deadline) {
        property(window, atom, type, (byte)8, data.length, data, deadline);
    }
    private void property(int window, int atom, int type, byte format, int count, byte[] data, long deadline) {
        if (data.length > maximumRequestBytes() - 24) throw new Unavailable("X11 property request too large");
        mutate(deadline, () -> bindings.xcb.xcb_change_property(connection, (byte)0, window, atom, type, format, count, data));
    }
    /** The caller leases its own packed popup peer until this barrier or connection termination. */
    void popupHints(int window, long deadline) {
        int state = internAtom("_NET_WM_STATE", deadline);
        var values = new java.util.LinkedHashSet<Integer>();
        for (int value : property32s(window, state, 4, 64, deadline)) values.add(value);
        values.add(internAtom("_NET_WM_STATE_SKIP_TASKBAR", deadline));
        values.add(internAtom("_NET_WM_STATE_SKIP_PAGER", deadline));
        property32(window, state, 4, values.stream().mapToInt(Integer::intValue).toArray(), deadline);
        barrier(deadline);
    }
    boolean activatePopup(int window, long userTime, long deadline) { return activatePopup(window, userTime, () -> true, deadline); }
    boolean activatePopup(int window, long userTime, java.util.function.BooleanSupplier valid, long deadline) {
        if (userTime <= 0 || userTime > 0xffffffffL || !valid.getAsBoolean()) return false;
        int active = internAtom("_NET_ACTIVE_WINDOW", deadline);
        int supported = internAtom("_NET_SUPPORTED", deadline);
        if (java.util.Arrays.stream(property32s(setup.root, supported, 4, 4096, deadline)).noneMatch(value -> value == active)
                || !valid.getAsBoolean()) return false;
        operationDeadline(deadline);
        sendClientMessage(setup.root, window, active, (1 << 19) | (1 << 20), 1, (int)userTime, 0, 0, 0);
        barrier(deadline);
        return true;
    }
    int parent(int window, long deadline) {
        return buffer(reply(request(deadline, () -> bindings.xcb.xcb_query_tree(connection, window)), deadline).bytes).getInt(12);
    }
    int depth(int window, long deadline) { return Byte.toUnsignedInt(geometry(window, deadline).get(1)); }
    private ByteBuffer geometry(int window, long deadline) {
        return buffer(reply(request(deadline, () -> bindings.xcb.xcb_get_geometry(connection, window)), deadline).bytes);
    }
    Rectangle geometryOnRoot(int window, long deadline) {
        var geometry = geometry(window, deadline);
        var translated = buffer(reply(request(deadline, () -> bindings.xcb.xcb_translate_coordinates(connection,
            window, setup.root, (short)0, (short)0)), deadline).bytes);
        if (translated.get(1) == 0) throw new Unavailable("X11 icon on another screen");
        return new Rectangle(translated.getShort(12), translated.getShort(14),
            Short.toUnsignedInt(geometry.getShort(16)), Short.toUnsignedInt(geometry.getShort(18)));
    }
    int createIcon(int parent, Visual visual, int colormap, int size, long deadline) {
        if (icon != 0 || ownedColormap != 0) throw new IllegalStateException("X11 icon cleanup owed");
        if (size < 1 || size > 512 || !setup.visuals.contains(visual)) throw new Unavailable("Unsupported icon geometry/visual");
        if (colormap == 0 && visual.id != setup.rootVisual) {
            ownedColormap = newId(deadline); colormap = ownedColormap;
            int map = colormap;
            mutate(deadline, () -> bindings.xcb.xcb_create_colormap(connection, (byte)0, map, parent, visual.id));
        }
        icon = newId(deadline);
        int mask = (1 << 1) | (1 << 3) | (1 << 11), eventMask = 1 | 4 | 8 | (1 << 15) | (1 << 17) | (1 << 21) | (1 << 22);
        int[] values = colormap == 0 ? new int[] {0, 0, eventMask} : new int[] {0, 0, eventMask, colormap};
        if (colormap != 0) mask |= 1 << 13;
        int attributes = mask;
        mutate(deadline, () -> bindings.xcb.xcb_create_window(connection, (byte)visual.depth, icon, parent,
            (short)0, (short)0, (short)size, (short)size, (short)0, (short)1, visual.id, attributes, values));
        return icon;
    }
    void sendClientMessage(int destination, int window, int type, int eventMask, int... data) {
        if (data.length != 5) throw new IllegalArgumentException("ClientMessage needs five words");
        var event = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder());
        event.put(0, (byte)33); event.put(1, (byte)32); event.putInt(4, window); event.putInt(8, type);
        for (int i = 0; i < 5; i++) event.putInt(12 + 4 * i, data[i]);
        mutate(operationDeadline, () -> bindings.xcb.xcb_send_event(connection, (byte)0, destination, eventMask, event.array()));
    }
    void mapIcon(long deadline) {
        if (icon == 0) throw new IllegalStateException("Missing icon");
        mutate(deadline, () -> bindings.xcb.xcb_map_window(connection, icon));
        barrier(deadline);
    }
    void destroyIcon(long deadline) {
        drawing.free(deadline);
        if (icon != 0) mutate(deadline, () -> bindings.xcb.xcb_destroy_window(connection, icon));
        if (ownedColormap != 0) mutate(deadline, () -> bindings.xcb.xcb_free_colormap(connection, ownedColormap));
        barrier(deadline); icon = 0; ownedColormap = 0;
    }
    boolean alpha(Visual visual, long deadline) {
        drawing.formats(deadline);
        var format = drawing.visualFormats.get(visual.id);
        return format != null && format.alpha != 0;
    }
    boolean supports(Visual visual, long deadline) { return supports(visual, false, deadline); }
    boolean supports(Visual visual, boolean alpha, long deadline) {
        drawing.formats(deadline);
        var destination = drawing.visualFormats.get(visual.id);
        if (destination == null || destination.depth != visual.depth || (destination.alpha != 0) != alpha
                || destination.red != visual.redMask || destination.green != visual.greenMask
                || destination.blue != visual.blueMask || drawing.sourceFormat == null) return false;
        var format = setup.formats.stream().filter(f -> f.depth == visual.depth).findFirst().orElse(null);
        if (format == null) return false;
        try {
            pixels(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), format.bitsPerPixel,
                format.scanlinePad, setup.imageByteOrder, destination.red, destination.green, destination.blue, destination.alpha);
            var source = setup.formats.stream().filter(f -> f.depth == 32).findFirst().orElse(null);
            if (source == null) return false;
            pixels(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), source.bitsPerPixel, source.scanlinePad,
                setup.imageByteOrder, drawing.sourceFormat.red, drawing.sourceFormat.green, drawing.sourceFormat.blue, drawing.sourceFormat.alpha);
            return true;
        } catch (IllegalArgumentException e) { return false; }
    }
    void paint(BufferedImage image, int width, int height, Visual visual, boolean alpha, long deadline) {
        if (icon == 0 || width < 1 || height < 1 || width > 512 || height > 512
                || image.getWidth() != width || image.getHeight() != height || !supports(visual, alpha, deadline))
            throw new Unavailable("Unsupported icon rendering");
        if (!alpha && depth(parent(icon, deadline), deadline) != visual.depth)
            throw new Unavailable("ParentRelative depth mismatch");
        drawing.paint(image, width, height, visual, alpha, deadline);
    }
    private record PictureFormat(int id, int depth, int red, int green, int blue, int alpha) {}
    /** All native drawing resources share the enclosing connection's worker and deadline ownership. */
    private final class Drawing {
        final Map<Integer, PictureFormat> visualFormats = new HashMap<>();
        PictureFormat sourceFormat;
        int pixmap, gc, source, destination, width, height, visualId;
        boolean loaded;
        void formats(long deadline) {
            if (loaded) return;
            var version = buffer(reply(request(deadline,
                () -> bindings.render.xcb_render_query_version(connection, 0, 11)), deadline).bytes);
            if (version.getInt(8) == 0 && version.getInt(12) < 1) throw new Unavailable("RENDER version unavailable");
            var b = buffer(reply(request(deadline, () -> bindings.render.xcb_render_query_pict_formats(connection)), deadline).bytes);
            int formatCount = b.getInt(8), screens = b.getInt(12), depths = b.getInt(16), visuals = b.getInt(20);
            if (formatCount < 1 || formatCount > 4096 || screens < 1 || screens > 256
                    || depths < 0 || depths > 65536 || visuals < 0 || visuals > 65536)
                throw new Unavailable("Invalid RENDER formats");
            var formats = new HashMap<Integer, PictureFormat>(); b.position(32);
            for (int i = 0; i < formatCount; i++) {
                require(b, 28); int p = b.position();
                if (b.get(p + 4) == 1) {
                    var f = new PictureFormat(b.getInt(p), Byte.toUnsignedInt(b.get(p + 5)),
                        mask(b, p + 8), mask(b, p + 12), mask(b, p + 16), mask(b, p + 20));
                    formats.put(f.id, f);
                    if (f.depth == 32 && f.alpha != 0 && Integer.bitCount(f.red) == 8
                            && Integer.bitCount(f.green) == 8 && Integer.bitCount(f.blue) == 8
                            && Integer.bitCount(f.alpha) == 8) sourceFormat = f;
                }
                b.position(p + 28);
            }
            int seenDepths = 0, seenVisuals = 0;
            for (int screen = 0; screen < screens; screen++) {
                require(b, 8); int count = b.getInt(); b.getInt();
                if (count < 0 || count > depths - seenDepths) throw new Unavailable("Invalid RENDER depths");
                seenDepths += count;
                for (int d = 0; d < count; d++) {
                    require(b, 8); int p = b.position(), n = Short.toUnsignedInt(b.getShort(p + 2)); b.position(p + 8);
                    if (n > visuals - seenVisuals) throw new Unavailable("Invalid RENDER visuals");
                    seenVisuals += n;
                    for (int v = 0; v < n; v++) {
                        require(b, 8); int visual = b.getInt(), format = b.getInt();
                        var f = formats.get(format);
                        if (f != null && setup.visuals.stream().anyMatch(value -> value.id == visual)) visualFormats.put(visual, f);
                    }
                }
            }
            if (seenDepths != depths || seenVisuals != visuals) throw new Unavailable("Incomplete RENDER formats");
            int subpixel = b.getInt(24);
            if (subpixel < 0 || b.remaining() != subpixel * 4L) throw new Unavailable("Invalid RENDER trailing data");
            loaded = true;
        }
        private int mask(ByteBuffer b, int offset) {
            int shift = Short.toUnsignedInt(b.getShort(offset)), mask = Short.toUnsignedInt(b.getShort(offset + 2));
            if (shift > 31 || ((long)mask << shift) > 0xffffffffL) throw new Unavailable("Invalid RENDER channel");
            return mask << shift;
        }
        void paint(BufferedImage image, int w, int h, Visual visual, boolean alpha, long deadline) {
            var format = setup.formats.stream().filter(f -> f.depth == 32).findFirst()
                .orElseThrow(() -> new Unavailable("ARGB32 upload unavailable"));
            byte[] pixels = pixels(image, format.bitsPerPixel, format.scanlinePad, setup.imageByteOrder,
                sourceFormat.red, sourceFormat.green, sourceFormat.blue, sourceFormat.alpha);
            if (w != width || h != height || visual.id != visualId || source == 0) {
                free(deadline); barrier(deadline);
                width = w; height = h; visualId = visual.id;
                pixmap = newId(deadline);
                mutate(deadline, () -> bindings.xcb.xcb_create_pixmap(connection, (byte)32, pixmap, icon, (short)w, (short)h));
                gc = newId(deadline);
                mutate(deadline, () -> bindings.xcb.xcb_create_gc(connection, gc, pixmap, 0, new int[0]));
                source = newId(deadline);
                mutate(deadline, () -> bindings.render.xcb_render_create_picture(connection, source, pixmap, sourceFormat.id, 0, new int[0]));
                destination = newId(deadline);
                mutate(deadline, () -> bindings.render.xcb_render_create_picture(connection, destination, icon,
                    visualFormats.get(visual.id).id, 0, new int[0]));
                if (!alpha) mutate(deadline, () -> bindings.xcb.xcb_change_window_attributes(connection, icon, 1, new int[] {1}));
                barrier(deadline);
            }
            int stride = pixels.length / h, rows = (maximumRequestBytes() - 24) / stride;
            if (rows < 1) throw new Unavailable("X11 request cannot fit one scanline");
            for (int y = 0; y < h; y += rows) {
                int count = Math.min(rows, h - y), offset = y;
                byte[] tile = Arrays.copyOfRange(pixels, y * stride, (y + count) * stride);
                mutate(deadline, () -> bindings.xcb.xcb_put_image(connection, (byte)2, pixmap, gc,
                    (short)w, (short)count, (short)0, (short)offset, (byte)0, (byte)32, tile.length, tile));
            }
            if (alpha) mutate(deadline, () -> bindings.render.xcb_render_composite(connection, (byte)0, source, 0, destination,
                (short)0, (short)0, (short)0, (short)0, (short)0, (short)0, (short)w, (short)h));
            else mutate(deadline, () -> bindings.xcb.xcb_clear_area(connection, (byte)0, icon, (short)0, (short)0, (short)w, (short)h));
            mutate(deadline, () -> bindings.render.xcb_render_composite(connection, (byte)3, source, 0, destination,
                (short)0, (short)0, (short)0, (short)0, (short)0, (short)0, (short)w, (short)h));
            barrier(deadline);
        }
        void free(long deadline) {
            if (source != 0) { int id = source; mutate(deadline, () -> bindings.render.xcb_render_free_picture(connection, id)); source = 0; }
            if (destination != 0) { int id = destination; mutate(deadline, () -> bindings.render.xcb_render_free_picture(connection, id)); destination = 0; }
            if (gc != 0) { int id = gc; mutate(deadline, () -> bindings.xcb.xcb_free_gc(connection, id)); gc = 0; }
            if (pixmap != 0) { int id = pixmap; mutate(deadline, () -> bindings.xcb.xcb_free_pixmap(connection, id)); pixmap = 0; }
        }
    }

    int[] keysyms(int firstKeycode, int count, long deadline) {
        if (firstKeycode < 8 || firstKeycode > 255 || count < 1 || count > 255 || firstKeycode + count > 256)
            throw new Unavailable("Invalid keyboard range");
        var b = buffer(reply(request(deadline, () -> bindings.xcb.xcb_get_keyboard_mapping(connection,
            (byte)firstKeycode, (byte)count)), deadline).bytes);
        int perCode = Byte.toUnsignedInt(b.get(1));
        if (perCode < 1 || perCode > 32 || b.capacity() != 32 + count * perCode * 4)
            throw new Unavailable("Invalid keyboard mapping");
        int[] result = new int[count * perCode];
        for (int i = 0; i < result.length; i++) result[i] = b.getInt(32 + i * 4);
        return result;
    }
    int firstKeycode() { return Byte.toUnsignedInt(setupBytes[34]); }
    int lastKeycode() { return Byte.toUnsignedInt(setupBytes[35]); }
    int maximumRequestBytes() { return Short.toUnsignedInt(buffer(setupBytes).getShort(26)) * 4; }
    private static ByteBuffer buffer(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()); }

    void operationDeadline(long deadline) { cancellation.check(deadline); operationDeadline = deadline; }
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
        mutations.clear();
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
