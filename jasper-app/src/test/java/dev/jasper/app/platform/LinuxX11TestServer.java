package dev.jasper.app.platform;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** A private core-X11 wire fixture. Never connects to an existing server. */
final class LinuxX11TestServer implements AutoCloseable {
    enum Mode { NORMAL, NO_SETUP, NO_REPLY, BAD_AUTH, BAD_WINDOW, BAD_MATCH }
    interface Libc extends Library {
        int socket(int domain, int type, int protocol);
        int bind(int fd, Pointer address, int length);
        int listen(int fd, int backlog);
        int accept4(int fd, Pointer address, Pointer length, int flags);
        int poll(Pointer fds, NativeLong count, int timeout);
        NativeLong read(int fd, Pointer bytes, NativeLong count);
        NativeLong write(int fd, Pointer bytes, NativeLong count);
        NativeLong send(int fd, Pointer bytes, NativeLong count, int flags);
        int shutdown(int fd, int how);
        int close(int fd);
        int socketpair(int domain, int type, int protocol, int[] fds);
        int dup2(int oldFd, int newFd);
        int mkfifo(String path, int mode);
    }
    static final Libc C = Native.load("c", Libc.class);
    private final int listener;
    private volatile int client = -1;
    private final int display;
    private final boolean abstractAddress;
    private final Path path;
    private final Thread worker;
    private final CountDownLatch setup = new CountDownLatch(1);
    private final AtomicLong bytes = new AtomicLong();
    private volatile boolean closed, paused, pauseObserved;
    private volatile Throwable failure;
    volatile Mode mode = Mode.NORMAL;
    volatile int authNameLength, authDataLength;
    volatile byte[] authData;
    volatile int requests;
    volatile int eventsBeforeReply;
    volatile int eventBytes = 32;

    static LinuxX11TestServer filesystem() throws Exception { return new LinuxX11TestServer(false); }
    static LinuxX11TestServer abstractSocket() throws Exception { return new LinuxX11TestServer(true); }

    private LinuxX11TestServer(boolean abstractAddress) throws Exception {
        this.abstractAddress = abstractAddress;
        Files.createDirectories(Path.of("/tmp/.X11-unix"));
        int fd = C.socket(1, 1 | 0x800 | 0x80000, 0);
        if (fd < 0) throw new IllegalStateException("fixture socket failed");
        int number = -1; Path candidate = null;
        try {
            for (int i = 0; i < 32; i++) {
                number = ThreadLocalRandom.current().nextInt(20000, 60000);
                candidate = Path.of("/tmp/.X11-unix/X" + number);
                if (abstractAddress) {
                    // Reserve the filesystem spelling too: connect must never fall through to a real display.
                    try { Files.createDirectory(candidate); }
                    catch (FileAlreadyExistsException occupied) { continue; }
                }
                byte[] name = candidate.toString().getBytes(StandardCharsets.US_ASCII);
                try (Memory address = new Memory(110)) {
                    address.clear(); address.setShort(0, (short)1);
                    address.write(abstractAddress ? 3 : 2, name, 0, name.length);
                    if (C.bind(fd, address, 2 + name.length + 1) == 0) break;
                    if (abstractAddress) Files.delete(candidate);
                    if (i == 31) throw new IllegalStateException("fixture bind failed");
                }
            }
            if (C.listen(fd, 1) != 0) throw new IllegalStateException("fixture listen failed");
        } catch (Throwable e) { C.close(fd); throw e; }
        listener = fd; display = number; path = candidate;
        worker = Thread.ofPlatform().daemon().name("jasper-x11-fixture").start(this::serve);
    }

    String address() { return ":" + display; }
    void awaitSetup() throws Exception {
        if (!setup.await(5, TimeUnit.SECONDS)) throw new AssertionError("fixture setup never arrived");
        check();
    }
    void pauseReads() { paused = true; }
    void awaitPaused() throws Exception { await(() -> pauseObserved, "fixture pause"); }
    void resumeReads() { paused = false; }
    void drop() { int fd = client; if (fd >= 0) C.shutdown(fd, 2); }
    long bytesRead() { return bytes.get(); }

    private void serve() {
        try {
            while (!closed) {
                client = C.accept4(listener, null, null, 0x800 | 0x80000);
                if (client >= 0) break;
                ready(listener, 1);
            }
            if (closed) return;
            byte[] prefix = read(12);
            ByteOrder order = prefix[0] == 'l' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            if (order != ByteOrder.nativeOrder()) throw new AssertionError("unexpected client byte order");
            ByteBuffer b = ByteBuffer.wrap(prefix).order(order);
            authNameLength = Short.toUnsignedInt(b.getShort(6)); authDataLength = Short.toUnsignedInt(b.getShort(8));
            byte[] auth = read(((authNameLength + 3) & ~3) + ((authDataLength + 3) & ~3));
            authData = Arrays.copyOfRange(auth, (authNameLength + 3) & ~3, ((authNameLength + 3) & ~3) + authDataLength);
            setup.countDown();
            if (mode == Mode.NO_SETUP) { while (!closed) ready(client, 1); return; }
            if (mode == Mode.BAD_AUTH) {
                write(new byte[] {0, 0, 11, 0, 0, 0, 0, 0}); return;
            }
            write(LinuxTrayXcbTest.setupBytes());
            while (!closed) {
                while (paused && !closed) { pauseObserved = true; Thread.sleep(2); }
                pauseObserved = false;
                if (closed) return;
                byte[] header = read(4);
                int length = Short.toUnsignedInt(ByteBuffer.wrap(header).order(order).getShort(2)) * 4;
                if (length < 4 || length > 262140) throw new AssertionError("invalid request length");
                read(length - 4); requests++;
                if (mode == Mode.NO_REPLY) continue;
                int opcode = Byte.toUnsignedInt(header[0]);
                if (opcode == 16 || opcode == 23 || opcode == 43) {
                    for (int event = 0; event < eventsBeforeReply; event++) {
                        ByteBuffer notification = ByteBuffer.allocate(eventBytes).order(order);
                        notification.put(0, (byte)(eventBytes == 32 ? 12 : 35)); notification.putShort(2, (short)requests);
                        if (eventBytes != 32) notification.putInt(4, (eventBytes - 32) / 4);
                        if (eventBytes == 32) notification.putInt(4, 0x100); write(notification.array());
                    }
                    eventsBeforeReply = 0;
                    ByteBuffer reply = ByteBuffer.allocate(32).order(order);
                    reply.put(0, (byte)1); reply.putShort(2, (short)requests);
                    reply.putInt(8, opcode == 16 ? 77 : opcode == 23 ? 0x123 : 0x100);
                    if (mode == Mode.BAD_WINDOW || mode == Mode.BAD_MATCH) {
                        reply.put(0, (byte)0); reply.put(1, (byte)(mode == Mode.BAD_WINDOW ? 3 : 8));
                        reply.put(10, (byte)opcode);
                    }
                    write(reply.array());
                }
            }
        } catch (Throwable e) {
            if (!closed && !(e instanceof End)) failure = e;
        } finally { setup.countDown(); }
    }
    private byte[] read(int size) throws Exception {
        byte[] result = new byte[size]; int offset = 0;
        try (Memory data = new Memory(Math.max(1, size))) {
            while (offset < size && !closed) {
                long got = C.read(client, data, new NativeLong(size - offset)).longValue();
                if (got == 0) throw new End();
                if (got < 0) { if (Native.getLastError() != 11 && Native.getLastError() != 4) throw new End(); ready(client, 1); continue; }
                data.read(0, result, offset, (int)got); offset += (int)got; bytes.addAndGet(got);
            }
        }
        if (offset != size) throw new End();
        return result;
    }
    private void write(byte[] bytes) throws Exception {
        try (Memory data = new Memory(bytes.length)) {
            data.write(0, bytes, 0, bytes.length); int offset = 0;
            while (offset < bytes.length && !closed) {
                long n = C.send(client, data.share(offset), new NativeLong(bytes.length - offset), 0x4000).longValue();
                if (n < 0) { if (Native.getLastError() != 11 && Native.getLastError() != 4) throw new End(); ready(client, 4); }
                else offset += (int)n;
            }
        }
    }
    private void ready(int fd, int events) {
        try (Memory poll = new Memory(8)) {
            poll.clear(); poll.setInt(0, fd); poll.setShort(4, (short)events); C.poll(poll, new NativeLong(1), 20);
        }
    }
    void check() { if (failure != null) throw new AssertionError("fixture failed", failure); }
    static void await(BooleanSupplier condition, String message) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= end) throw new AssertionError(message);
            Thread.sleep(2);
        }
    }
    @Override public void close() throws IOException {
        if (closed) return;
        closed = true; drop(); C.shutdown(listener, 2); worker.interrupt();
        try { worker.join(5000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Fixture cleanup interrupted", e); }
        if (worker.isAlive()) throw new AssertionError("fixture worker stuck");
        if (client >= 0) C.close(client); C.close(listener);
        Files.deleteIfExists(path);
        check();
    }
    private static final class End extends Exception {}
}
