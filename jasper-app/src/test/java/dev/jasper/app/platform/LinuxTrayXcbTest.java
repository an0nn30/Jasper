package dev.jasper.app.platform;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class LinuxTrayXcbTest {
    @Test void acceptsOnlyLocalDisplayGrammar() {
        assertThat(LinuxTrayXcb.parseDisplay(":0")).isEqualTo(new LinuxTrayXcb.Display(0, 0));
        assertThat(LinuxTrayXcb.parseDisplay(":7.2")).isEqualTo(new LinuxTrayXcb.Display(7, 2));
        assertThat(LinuxTrayXcb.parseDisplay("unix:7")).isEqualTo(new LinuxTrayXcb.Display(7, 0));
        assertThat(LinuxTrayXcb.parseDisplay("unix/:7.2")).isEqualTo(new LinuxTrayXcb.Display(7, 2));
        assertThat(LinuxTrayXcb.parseDisplay(":65535.255")).isEqualTo(new LinuxTrayXcb.Display(65535, 255));
        for (String value : List.of("", "localhost:10.0", "host:0", "tcp/host:0", ":-1", ":65536",
                ":1.256", ":1.x", ":1.", ":1.2.3", ":+1", " :0", ":999999999999999999999", ":١"))
            assertThatThrownBy(() -> LinuxTrayXcb.parseDisplay(value)).as(value).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void validatesAuthorityFramingBeforeNativeDecoding() {
        assertThat(LinuxTrayXcb.authorityRecords(new byte[0])).isZero();
        assertThat(LinuxTrayXcb.authorityRecords(new byte[10])).isEqualTo(1);
        for (int length = 1; length < 10; length++) {
            byte[] truncated = new byte[length];
            assertThatThrownBy(() -> LinuxTrayXcb.authorityRecords(truncated)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> LinuxTrayXcb.authorityRecords(new byte[4097 * 10])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinuxTrayXcb.authorityRecords(new byte[1024 * 1024 + 1])).isInstanceOf(IllegalArgumentException.class);
        byte[] largeField = new byte[65545];
        largeField[2] = (byte) 255; largeField[3] = (byte) 255;
        assertThat(LinuxTrayXcb.authorityRecords(largeField)).isEqualTo(1);
    }

    @Test void setupRejectsInvalidScreenAndTruncation() {
        byte[] setup = setupBytes();
        var value = LinuxTrayXcb.decodeSetup(setup, 0);
        assertThat(value.root()).isEqualTo(0x100);
        assertThat(value.rootVisual()).isEqualTo(0x21);
        assertThat(value.rootDepth()).isEqualTo(24);
        assertThat(value.visuals()).containsExactly(new LinuxTrayXcb.Visual(0x21, 24, 0xff0000, 0xff00, 0xff));
        assertThat(value.formats()).containsExactly(new LinuxTrayXcb.PixmapFormat(24, 32, 32));
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(setup, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(new byte[8], 0)).isInstanceOf(IllegalArgumentException.class);
        byte[] bad = setup.clone(); bad[30] = 2;
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(bad, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void connectsOnlyToOwnedFixture() throws Exception {
        nativeOnly();
        for (boolean abstractAddress : List.of(false, true)) {
            try (var server = abstractAddress ? LinuxX11TestServer.abstractSocket() : LinuxX11TestServer.filesystem();
                 var cancel = new LinuxTrayXcb.Cancellation();
                 var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                assertThat(xcb.setup().root()).isEqualTo(0x100);
                assertThat(xcb.newId()).isNotZero();
                assertThat(xcb.internAtom("_TEST", deadline())).isEqualTo(77);
                assertThat(xcb.selectionOwner(77, deadline())).isEqualTo(0x123);
                xcb.barrier(deadline());
                assertThat(server.authNameLength).isZero();
                assertThat(server.authDataLength).isZero();
            }
        }
    }

    static void nativeOnly() {
        if (!System.getProperty("os.name").equals("Linux") && Boolean.getBoolean("jasper.tray.requireNative"))
            throw new AssertionError("Required Linux native gate cannot skip");
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").equals("Linux"));
    }
    static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(2); }

    @Test void authorityMatchesLocalThenWild() throws Exception {
        nativeOnly();
        for (boolean localMatch : List.of(true, false)) {
            try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
                byte[] host;
                try (var memory = new Memory(256)) {
                    libc(cancel).gethostname(memory, new NativeLong(256));
                    host = memory.getString(0).getBytes(StandardCharsets.UTF_8);
                }
                String number = server.address().substring(1);
                byte[] localCookie = new byte[16]; Arrays.fill(localCookie, (byte)1);
                byte[] wildCookie = new byte[16]; Arrays.fill(wildCookie, (byte)2);
                byte[] bytes = concat(authRecord(65535, new byte[0], number, "MIT-MAGIC-COOKIE-1", wildCookie),
                    authRecord(256, localMatch ? host : new byte[] {9}, number, "MIT-MAGIC-COOKIE-1", localCookie),
                    authRecord(256, host, number + "9", "MIT-MAGIC-COOKIE-1", new byte[16]));
                var path = Files.createTempFile("jasper-auth-", ".fixture");
                try {
                    Files.write(path, bytes);
                    var audit = new Audit(cancel);
                    try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel)) {
                        assertThat(xcb.setup().root()).isEqualTo(0x100);
                        assertThat(Arrays.equals(server.authData, localMatch ? localCookie : wildCookie)).isTrue();
                    }
                    assertThat(audit.authRecords.get()).isEqualTo(3);
                    assertThat(audit.authLive.get()).isZero();
                } finally { Files.delete(path); }
            }
        }
    }
    @Test void missingCookieUsesEmptyAuthAndServerDecides() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            server.mode = LinuxX11TestServer.Mode.BAD_AUTH;
            assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel))
                .isInstanceOf(LinuxTrayXcb.Unavailable.class);
            assertThat(server.authNameLength).isZero(); assertThat(server.authDataLength).isZero();
        }
    }
    @Test void authoritySnapshotRejectsFifoDirectorySymlinkRaceAndOversize() throws Exception {
        nativeOnly();
        var root = Files.createTempDirectory("jasper-auth-types-");
        try (var server = LinuxX11TestServer.filesystem()) {
            var file = root.resolve("file"); Files.write(file, new byte[10]);
            var link = root.resolve("link"); Files.createSymbolicLink(link, file);
            var fifo = root.resolve("fifo"); assertThat(LinuxX11TestServer.C.mkfifo(fifo.toString(), 0600)).isZero();
            var large = root.resolve("large");
            try (var output = new RandomAccessFile(large.toFile(), "rw")) { output.setLength(1024 * 1024 + 1); }
            for (var path : List.of(root, link, fifo, large)) {
                try (var cancel = new LinuxTrayXcb.Cancellation()) {
                    var audit = new Audit(cancel);
                    assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel))
                        .isInstanceOf(LinuxTrayXcb.Unavailable.class);
                    assertThat(audit.reads.get()).isZero();
                    assertThat(audit.connects.get()).isZero();
                }
            }
            try (var cancel = new LinuxTrayXcb.Cancellation()) {
                var audit = new Audit(cancel);
                audit.after = (name, args) -> {
                    if (name.equals("read") && audit.reads.get() == 1) {
                        try { Files.write(file, new byte[20]); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                };
                assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", file.toString()), deadline(), cancel))
                    .isInstanceOf(LinuxTrayXcb.Unavailable.class);
                assertThat(audit.connects.get()).isZero();
            }
        } finally {
            try (var entries = Files.list(root)) { for (var path : entries.toList()) Files.delete(path); }
            Files.delete(root);
        }
    }
    @Test void malformedAuthorityFreesEveryNativeRecord() throws Exception {
        nativeOnly();
        var path = Files.createTempFile("jasper-auth-malformed-", ".fixture");
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            // Framing-valid first record followed by native decoder failure: every acquired record is disposed.
            Files.write(path, new byte[20]);
            var audit = new Audit(cancel); audit.failSecondAuth = true;
            assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel))
                .isInstanceOf(LinuxTrayXcb.Unavailable.class);
            assertThat(audit.authRecords.get()).isEqualTo(1);
            assertThat(audit.authLive.get()).isZero();
            assertThat(audit.streams.get()).isZero();
        } finally { Files.delete(path); }
    }
    @Test void failedAttemptMustFinishBeforeRetry() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already owned");
            assertThat(xcb.internAtom("still-owned", deadline())).isEqualTo(77);
        }
    }
    @Test void idGenerationAlsoArmsSocketDeadline() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            var audit = new Audit(cancel);
            audit.before = (name, args) -> {
                if (name.equals("xcb_generate_id")) {
                    try { assertThat((Integer)field(cancel, "duplicate")).isGreaterThanOrEqualTo(0); }
                    catch (Exception e) { throw new AssertionError(e); }
                }
            };
            assertThat(xcb.newId()).isNotZero();
        }
    }

    @Test void barrierPreservesOrdinaryEventsForNextPoll() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            server.eventsBeforeReply = 1;
            xcb.barrier(deadline());
            var events = xcb.poll(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100));
            assertThat(events).hasSize(1); assertThat(events.getFirst().type()).isEqualTo(12);
        }
    }
    @Test void authorityRejectsSameSizeModificationDuringSnapshot() throws Exception {
        nativeOnly();
        var path = Files.createTempFile("jasper-auth-race-", ".fixture");
        Files.write(path, new byte[10]);
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            var audit = new Audit(cancel);
            audit.after = (name, args) -> {
                if (name.equals("read") && audit.reads.get() == 1) {
                    try {
                        Files.write(path, new byte[10]);
                        Files.setLastModifiedTime(path, FileTime.fromMillis(1000));
                    } catch (Exception e) { throw new AssertionError(e); }
                }
            };
            assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel))
                .isInstanceOf(LinuxTrayXcb.Unavailable.class);
            assertThat(audit.connects.get()).isZero();
        } finally { Files.delete(path); }
    }
    @Test void outstandingRepliesAreBoundedBeforeEnqueue() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            for (int i = 0; i < 64; i++) invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "pending", deadline());
            assertThatThrownBy(() -> invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "excess", deadline()))
                .isInstanceOf(LinuxTrayXcb.Unavailable.class).hasMessageContaining("Too many");
        }
    }

    @Test void idlePollTimeoutLeavesConnectionUsable() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            assertThat(xcb.poll(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(30))).isEmpty();
            assertThat(xcb.internAtom("after-idle", deadline())).isEqualTo(77);
        }
    }

    @Test void repeatedConnectionCloseCannotReleaseLaterAttempt() throws Exception {
        nativeOnly();
        try (var first = LinuxX11TestServer.filesystem(); var second = LinuxX11TestServer.filesystem();
             var third = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            var old = LinuxTrayXcb.connect(Map.of("DISPLAY", first.address()), deadline(), cancel); old.close();
            try (var current = LinuxTrayXcb.connect(Map.of("DISPLAY", second.address()), deadline(), cancel)) {
                old.close();
                assertThatThrownBy(() -> {
                    try (var extra = LinuxTrayXcb.connect(Map.of("DISPLAY", third.address()), deadline(), cancel)) { extra.setup(); }
                }).isInstanceOf(IllegalStateException.class).hasMessageContaining("already owned");
                assertThat(current.internAtom("current", deadline())).isEqualTo(77);
            }
        }
    }
    @Test void barrierRetainedEventsShareOneByteBudget() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            server.eventBytes = 160000; server.eventsBeforeReply = 1; xcb.barrier(deadline());
            server.eventsBeforeReply = 1;
            assertThatThrownBy(() -> xcb.barrier(deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class)
                .hasMessageContaining("budget");
        }
    }

    @Test void nativeLoadFailureIsScoped() {
        // Class loading and pure framing must remain available even when native construction is unavailable.
        String architecture = System.getProperty("os.arch");
        try {
            System.setProperty("os.arch", "unsupported-test-architecture");
            assertThatThrownBy(LinuxTrayXcb.Cancellation::new).isInstanceOf(LinuxTrayXcb.Unavailable.class);
            assertThat(LinuxTrayXcb.parseDisplay(":0")).isEqualTo(new LinuxTrayXcb.Display(0, 0));
        } finally { System.setProperty("os.arch", architecture); }
    }
    @Test void nativeAbiMatchesInstalledHeaders() throws Exception {
        nativeOnly(); LinuxX11TrayProbe.abi(Path.of("build/tray-gate/abi-test"));
    }
    @Test void deadlineShutdownCannotHitReusedFd() throws Exception {
        nativeOnly(); LinuxX11TrayProbe.reuse();
    }
    @Test void onlyWorkerDisconnectsAfterCallReturns() throws Exception {
        nativeOnly(); LinuxX11TrayProbe.stall("setup", false, true);
    }
    @Test void heldAuthorityDiscardsLateResultWithoutBlockingClose() throws Exception {
        nativeOnly(); LinuxX11TrayProbe.heldAuthority();
    }

    static byte[] authRecord(int family, byte[] host, String number, String name, byte[] data) throws Exception {
        var bytes = new ByteArrayOutputStream();
        var out = new DataOutputStream(bytes); out.writeShort(family);
        for (byte[] field : List.of(host, number.getBytes(StandardCharsets.US_ASCII), name.getBytes(StandardCharsets.US_ASCII), data)) {
            out.writeShort(field.length); out.write(field);
        }
        return bytes.toByteArray();
    }
    static byte[] concat(byte[]... arrays) {
        var out = new ByteArrayOutputStream(); for (var bytes : arrays) out.writeBytes(bytes); return out.toByteArray();
    }
    static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        var call = target.getClass().getDeclaredMethod(method, types); call.setAccessible(true);
        try { return call.invoke(target, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw e;
        }
    }
    static LinuxTrayXcb.Libc libc(LinuxTrayXcb.Cancellation cancel) throws Exception {
        return (LinuxTrayXcb.Libc)field(field(cancel, "bindings"), "libc");
    }
    /** Instrument the real native boundary; no fake server results except a deliberate decoder-allocation failure. */
    static final class Audit {
        final AtomicInteger authRecords = new AtomicInteger();
        final AtomicInteger authLive = new AtomicInteger();
        final AtomicInteger streams = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger disconnects = new AtomicInteger();
        final Set<Long> buffers = ConcurrentHashMap.newKeySet();
        final List<Memory> authMemory = new ArrayList<>();
        final Thread owner = Thread.currentThread();
        volatile BiConsumer<String, Object[]> before = (name, args) -> {};
        volatile BiConsumer<String, Object[]> after = (name, args) -> {};
        boolean failSecondAuth;
        Audit(LinuxTrayXcb.Cancellation cancellation) throws Exception {
            Object bindings = field(cancellation, "bindings");
            for (String name : List.of("libc", "xcb", "xau")) {
                var field = bindings.getClass().getDeclaredField(name); field.setAccessible(true);
                Object original = field.get(bindings);
                field.set(bindings, Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {field.getType()}, (proxy, method, args) -> {
                    String call = method.getName();
                    if (failSecondAuth && call.equals("XauReadAuth") && authRecords.get() == 1) return null;
                    if (call.equals("xcb_disconnect")) {
                        assertThat(active.get()).isZero(); assertThat(Thread.currentThread()).isSameAs(owner); disconnects.incrementAndGet();
                    }
                    boolean xcb = call.startsWith("xcb_") && !call.equals("xcb_disconnect");
                    if (xcb) { assertThat(Thread.currentThread()).isSameAs(owner); active.incrementAndGet(); }
                    if (call.equals("xcb_connect_to_fd")) {
                        var auth = (LinuxTrayXcb.Auth)args[1];
                        authMemory.add((Memory)auth.getPointer());
                        if (auth.name != null) authMemory.add((Memory)auth.name);
                        if (auth.data != null) authMemory.add((Memory)auth.data);
                    }
                    if (call.equals("fmemopen")) authMemory.add((Memory)args[0]);
                    before.accept(call, args);
                    Object result;
                    try { result = method.invoke(original, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                    finally { if (xcb) active.decrementAndGet(); }
                    if (call.equals("read")) reads.incrementAndGet();
                    if (call.equals("xcb_connect_to_fd")) connects.incrementAndGet();
                    if (call.equals("fmemopen") && result != null) streams.incrementAndGet();
                    if (call.equals("fclose")) streams.decrementAndGet();
                    if (call.equals("XauReadAuth") && result != null) { authRecords.incrementAndGet(); authLive.incrementAndGet(); }
                    if (call.equals("XauDisposeAuth")) authLive.decrementAndGet();
                    if (call.equals("xcb_poll_for_event") && result != null) buffers.add(Pointer.nativeValue((Pointer)result));
                    if (call.equals("xcb_poll_for_reply")) {
                        for (int i : new int[] {2, 3}) {
                            var p = ((PointerByReference)args[i]).getValue();
                            if (p != null) buffers.add(Pointer.nativeValue(p));
                        }
                    }
                    if (call.equals("free")) assertThat(buffers.remove(Pointer.nativeValue((Pointer)args[0]))).isTrue();
                    after.accept(call, args);
                    return result;
                }));
            }
        }
        void clean() {
            assertThat(active.get()).isZero(); assertThat(authLive.get()).isZero(); assertThat(streams.get()).isZero(); assertThat(buffers).isEmpty();
            for (var memory : authMemory) assertThat(memory.valid()).as("native auth allocation released").isFalse();
        }
    }

    static byte[] setupBytes() {
        ByteBuffer b = ByteBuffer.allocate(120).order(ByteOrder.nativeOrder());
        b.put(0, (byte)1); b.putShort(2, (short)11); b.putShort(6, (short)28);
        b.putInt(12, 0x200000); b.putInt(16, 0x1fffff); b.putShort(26, (short)65535);
        b.put(28, (byte)1); b.put(29, (byte)1); b.put(30, (byte)(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? 0 : 1));
        b.put(32, (byte)32); b.put(33, (byte)32); b.put(34, (byte)8); b.put(35, (byte)255);
        b.put(40, (byte)24); b.put(41, (byte)32); b.put(42, (byte)32);
        b.putInt(48, 0x100); b.putInt(80, 0x21); b.put(86, (byte)24); b.put(87, (byte)1);
        b.put(88, (byte)24); b.putShort(90, (short)1);
        b.putInt(96, 0x21); b.put(100, (byte)4); b.put(101, (byte)8); b.putShort(102, (short)256);
        b.putInt(104, 0xff0000); b.putInt(108, 0xff00); b.putInt(112, 0xff);
        return b.array();
    }
}
