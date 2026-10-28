package dev.jasper.app.platform;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.jasper.app.platform.LinuxTrayXcbTest.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in headless transport gate. Each native case runs under a separate process watchdog. */
public final class LinuxX11TrayProbe {
    private LinuxX11TrayProbe() {}
    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--require-native")) options.put(args[i], "true");
            else if (Set.of("--gate", "--output", "--case", "--display", "--panel").contains(args[i]) && i + 1 < args.length)
                options.put(args[i], args[++i]);
            else throw new IllegalArgumentException("Unknown or incomplete probe option");
        }
        if (!System.getProperty("os.name").equals("Linux")) throw new IllegalStateException("Linux native gate required");
        Path output = Path.of(options.getOrDefault("--output", "build/tray-gate/transport")).toAbsolutePath();
        Files.createDirectories(output);
        if (options.containsKey("--case")) {
            String name = options.get("--case");
            warm();
            int fds = fdCount(); Set<String> threads = threads();
            if (name.equals("abi")) abi(output);
            else if (name.equals("authority")) heldAuthority();
            else if (name.equals("reuse")) reuse();
            else if (name.equals("cycles")) cycles();
            else {
                String[] parts = name.split("-"); stall(parts[0], parts[1].equals("abstract"), parts[2].equals("close"));
            }
            LinuxX11TestServer.await(() -> threads().equals(threads), "native threads did not return to baseline");
            assertThat(fdCount()).as("FD baseline after " + name).isEqualTo(fds);
            System.out.println("PASS " + name + " fdBaseline=" + fds + " fdAfter=" + fdCount()
                + " threadBaseline=" + threads.size() + " threadAfter=" + threads().size()
                + " liveDuplicates=0 liveAuthRecords=0 stuckWorkers=0 unexpectedExceptions=0");
            return;
        }
        if (!options.getOrDefault("--gate", "transport").equals("transport"))
            throw new IllegalArgumentException("Legacy/popup gate requires its later implementation task");
        var cases = new ArrayList<>(List.of("abi", "authority", "reuse", "cycles"));
        for (String phase : List.of("setup", "reply", "explicit", "implicit"))
            for (String socket : List.of("filesystem", "abstract"))
                for (String finish : List.of("timeout", "close")) cases.add(phase + "-" + socket + "-" + finish);
        var summary = new ArrayList<String>();
        for (String name : cases) {
            Path log = output.resolve(name + ".log");
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                LinuxX11TrayProbe.class.getName(), "--case", name, "--output", output.toString(), "--require-native")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            if (!child.waitFor(10, TimeUnit.SECONDS)) {
                child.destroyForcibly(); child.waitFor(2, TimeUnit.SECONDS);
                Files.writeString(output.resolve("FAILED"), name + ": watchdog expired; native case FAILED\n");
                throw new AssertionError("Native child stuck: " + name);
            }
            if (child.exitValue() != 0) {
                Files.writeString(output.resolve("FAILED"), name + ": child exit " + child.exitValue() + "\n");
                throw new AssertionError("Native child failed: " + name + "\n" + Files.readString(log));
            }
            summary.add(Files.readString(log).strip());
            System.out.println("PASS child=" + name);
        }
        Files.deleteIfExists(output.resolve("FAILED"));
        Files.write(output.resolve("summary.txt"), summary);
        System.out.println("PASS transport requiredCases=" + cases.size() + " requiredSkips=0 watchdogFailures=0");
    }

    private static void warm() throws Exception {
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            var audit = new Audit(cancel);
            try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                xcb.internAtom("warm", deadline()); xcb.barrier(deadline());
            }
            audit.clean();
        }
        var file = Files.createTempFile("jasper-xcb-warm-", ""); Files.delete(file);
        Thread.sleep(200);
    }
    private static int fdCount() {
        try (var files = Files.list(Path.of("/proc/self/fd"))) { return (int)files.count(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static Set<String> threads() {
        return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive)
            .map(Thread::getName).collect(java.util.stream.Collectors.toSet());
    }

    static void stall(String phase, boolean abstractAddress, boolean close) throws Exception {
        try (var server = abstractAddress ? LinuxX11TestServer.abstractSocket() : LinuxX11TestServer.filesystem()) {
            server.mode = phase.equals("setup") ? LinuxX11TestServer.Mode.NO_SETUP : LinuxX11TestServer.Mode.NO_REPLY;
            if (phase.equals("explicit") || phase.equals("implicit")) server.pauseReads();
            var holder = new AtomicReference<LinuxTrayXcb.Cancellation>();
            var auditHolder = new AtomicReference<Audit>();
            var failure = new AtomicReference<Throwable>();
            var entered = new CountDownLatch(1);
            var returned = new CountDownLatch(1);
            long[] operationStart = {0};
            var worker = Thread.ofPlatform().daemon().name("jasper-xcb-gate-worker").start(() -> {
                try (var cancel = new LinuxTrayXcb.Cancellation()) {
                    holder.set(cancel); var audit = new Audit(cancel); auditHolder.set(audit);
                    if (phase.equals("setup")) audit.before = (name, args) -> {
                        if (name.equals("xcb_connect_to_fd")) { operationStart[0] = System.nanoTime(); entered.countDown(); }
                    };
                    try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                        if (phase.equals("setup")) throw new AssertionError("setup unexpectedly returned");
                        if (phase.equals("reply")) {
                            operationStart[0] = System.nanoTime(); entered.countDown();
                            xcb.internAtom("unanswered", deadline());
                        } else {
                            server.awaitPaused();
                            int fd = (Integer)field(xcb, "socketFd");
                            fillSocket(fd);
                            String expected = phase.equals("explicit") ? "xcb_flush" : "xcb_intern_atom";
                            audit.before = (name, args) -> {
                                if (name.equals(expected)) { operationStart[0] = System.nanoTime(); entered.countDown(); }
                            };
                            if (phase.equals("explicit")) {
                                invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "queued", deadline());
                                invoke(xcb, "flush", new Class<?>[] {long.class}, deadline());
                            } else invoke(xcb, "requestAtom", new Class<?>[] {String.class, long.class}, "x".repeat(65535), deadline());
                        }
                        throw new AssertionError("stalled operation unexpectedly succeeded");
                    }
                } catch (Throwable e) { failure.set(e); }
                finally { returned.countDown(); }
            });
            try {
                if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("native phase not entered: " + phase, failure.get());
                server.awaitSetup();
                if (phase.equals("reply")) LinuxX11TestServer.await(() -> server.requests > 0, "reply request never reached fixture");
                Thread.sleep(100);
                assertThat(returned.getCount()).as("operation must be genuinely stalled").isEqualTo(1);
                if (!phase.equals("reply")) assertThat(auditHolder.get().active.get()).as("native call still active").isEqualTo(1);
                if (close) {
                    long start = System.nanoTime(); holder.get().requestClose();
                    assertThat(System.nanoTime() - start).as("nonblocking close nanos").isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
                }
                long remaining = operationStart[0] + TimeUnit.MILLISECONDS.toNanos(2500) - System.nanoTime();
                worker.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                assertThat(worker.isAlive()).as("native worker must actually terminate").isFalse();
                assertThat(failure.get()).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                Audit audit = auditHolder.get(); audit.clean();
                assertThat(audit.disconnects.get()).isEqualTo(1);
                assertThat((Integer)field(holder.get(), "duplicate")).isEqualTo(-1);
                System.out.println("phase=" + phase + " socket=" + (abstractAddress ? "abstract" : "filesystem") + " cancel=" + close
                    + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - operationStart[0]) + " nativeEntered=true workerCompleted=true");
            } finally {
                if (holder.get() != null) holder.get().requestClose(); server.drop();
                worker.join(500);
                if (worker.isAlive()) throw new AssertionError("native worker stranded after cleanup");
            }
        }
    }
    private static void fillSocket(int fd) {
        long total = 0;
        try (var bytes = new Memory(65536)) {
            bytes.clear();
            for (;;) {
                long sent = LinuxX11TestServer.C.send(fd, bytes, new NativeLong(bytes.size()), 0x40 | 0x4000).longValue();
                if (sent < 0) {
                    if (Native.getLastError() == 4) continue;
                    assertThat(Native.getLastError()).isEqualTo(11); break;
                }
                total += sent;
                if (total > 16 * 1024 * 1024) throw new AssertionError("socket never backpressured");
            }
        }
        assertThat(total).isPositive();
        System.out.println("backpressureBytes=" + total + " sendReturnedEagain=true");
    }

    static void reuse() throws Exception {
        try (var cancel = new LinuxTrayXcb.Cancellation(); var byteValue = new Memory(1)) {
            for (int i = 0; i < 32; i++) {
                int[] pair = {-1, -1}; assertThat(LinuxX11TestServer.C.socketpair(1, 1 | 0x80000, 0, pair)).isZero();
                invoke(cancel, "arm", new Class<?>[] {int.class, long.class}, pair[0], deadline());
                int oldDuplicate = (Integer)field(cancel, "duplicate"); long oldEpoch = (Long)field(cancel, "epoch");
                invoke(cancel, "disarm", new Class<?>[0]);
                LinuxX11TestServer.C.close(pair[0]); LinuxX11TestServer.C.close(pair[1]);
                int[] replacement = {-1, -1}; assertThat(LinuxX11TestServer.C.socketpair(1, 1 | 0x80000, 0, replacement)).isZero();
                assertThat(oldDuplicate).isNotEqualTo(replacement[1]);
                assertThat(LinuxX11TestServer.C.dup2(replacement[0], oldDuplicate)).isEqualTo(oldDuplicate);
                try {
                    var failure = new AtomicReference<Throwable>();
                    Thread callback = Thread.ofPlatform().name("jasper-old-deadline").start(() -> {
                        try { invoke(cancel, "expire", new Class<?>[] {long.class}, oldEpoch); }
                        catch (Throwable e) { failure.set(e); }
                    });
                    callback.join(1000); assertThat(callback.isAlive()).isFalse(); assertThat(failure.get()).isNull();
                    byteValue.setByte(0, (byte)42);
                    assertThat(LinuxX11TestServer.C.send(oldDuplicate, byteValue, new NativeLong(1), 0x4000).longValue()).isEqualTo(1);
                    assertThat(LinuxX11TestServer.C.read(replacement[1], byteValue, new NativeLong(1)).longValue()).isEqualTo(1);
                    assertThat(byteValue.getByte(0)).isEqualTo((byte)42);
                } finally {
                    LinuxX11TestServer.C.close(oldDuplicate);
                    if (replacement[0] != oldDuplicate) LinuxX11TestServer.C.close(replacement[0]);
                    LinuxX11TestServer.C.close(replacement[1]);
                }
            }
        }
        System.out.println("fdReuseIterations=32 delayedOldCallbacks=32 replacementSocketsUsable=32");
    }

    static void heldAuthority() throws Exception {
        for (String heldCall : List.of("open", "read")) {
            Path path = Files.createTempFile("jasper-held-authority-", ".fixture"); Files.write(path, new byte[10]);
            try (var server = LinuxX11TestServer.filesystem()) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var holder = new AtomicReference<LinuxTrayXcb.Cancellation>(); var auditHolder = new AtomicReference<Audit>();
                var failure = new AtomicReference<Throwable>();
                Thread worker = Thread.ofPlatform().daemon().name("jasper-held-authority").start(() -> {
                    try (var cancel = new LinuxTrayXcb.Cancellation()) {
                        holder.set(cancel); var audit = new Audit(cancel); auditHolder.set(audit);
                        audit.before = (name, args) -> {
                            if (name.equals(heldCall)) {
                                entered.countDown();
                                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("held authority fixture not released"); }
                                catch (InterruptedException e) { throw new AssertionError(e); }
                            }
                        };
                        try (var ignored = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address(), "XAUTHORITY", path.toString()), deadline(), cancel)) {
                            throw new AssertionError("late authority result connected to root " + ignored.setup().root());
                        }
                    } catch (Throwable e) { failure.set(e); }
                });
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); assertThat(worker.isDaemon()).isTrue();
                    long start = System.nanoTime(); holder.get().requestClose();
                    assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
                    assertThat(worker.isAlive()).isTrue();
                    assertThat(auditHolder.get().connects.get()).isZero();
                    assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), holder.get()))
                        .isInstanceOf(IllegalStateException.class); // owner/slot refuses a concurrent attempt
                    release.countDown(); worker.join(1000); assertThat(worker.isAlive()).isFalse();
                    assertThat(failure.get()).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                    auditHolder.get().clean(); assertThat(auditHolder.get().connects.get()).isZero();
                    assertThat(server.bytesRead()).isZero();
                    System.out.println("heldAuthority=" + heldCall + " pendingWorkers=1 closeResponsive=true lateConnects=0 cleanupAfterRelease=true");
                } finally { release.countDown(); worker.join(1000); if (worker.isAlive()) throw new AssertionError("held authority fixture stranded"); }
            } finally { Files.delete(path); }
        }
    }
    private static void cycles() throws Exception {
        for (int i = 0; i < 4; i++) {
            for (var mode : List.of(LinuxX11TestServer.Mode.BAD_AUTH, LinuxX11TestServer.Mode.BAD_WINDOW, LinuxX11TestServer.Mode.BAD_MATCH, LinuxX11TestServer.Mode.NORMAL)) {
                try (var server = i % 2 == 0 ? LinuxX11TestServer.filesystem() : LinuxX11TestServer.abstractSocket(); var cancel = new LinuxTrayXcb.Cancellation()) {
                    server.mode = mode; var audit = new Audit(cancel);
                    if (mode == LinuxX11TestServer.Mode.BAD_AUTH) {
                        assertThatThrownBy(() -> LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                    } else try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                        if (mode != LinuxX11TestServer.Mode.NORMAL)
                            assertThatThrownBy(() -> xcb.selectionOwner(77, deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                        else { assertThat(xcb.internAtom("reconnected", deadline())).isEqualTo(77); server.drop();
                            assertThatThrownBy(() -> xcb.barrier(deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class); }
                    }
                    audit.clean(); assertThat(audit.disconnects.get()).isEqualTo(1);
                }
            }
        }
        System.out.println("fastRecoveryCycles=4 errors=BadAuth,BadWindow,BadMatch,EOF reconnects=4");
    }

    static void abi(Path output) throws Exception {
        Files.createDirectories(output);
        Path source = Path.of("src/test/native/linux-tray-abi.c").toAbsolutePath();
        if (!Files.exists(source)) source = Path.of("jasper-app/src/test/native/linux-tray-abi.c").toAbsolutePath();
        Path binary = output.toAbsolutePath().resolve("linux-tray-abi");
        var compile = new ArrayList<>(List.of("cc", "-std=c11", "-Wall", "-Wextra", "-Werror"));
        Process flags = new ProcessBuilder("pkg-config", "--cflags", "xcb", "xcb-render", "xau").redirectErrorStream(true).start();
        if (!flags.waitFor(5, TimeUnit.SECONDS)) { flags.destroyForcibly(); throw new AssertionError("pkg-config stuck"); }
        String cflags = new String(flags.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (flags.exitValue() == 0 && !cflags.isEmpty()) compile.addAll(List.of(cflags.split("\\s+")));
        String include = System.getProperty("jasper.tray.headers"); if (include != null) compile.add("-I" + include);
        compile.addAll(List.of(source.toString(), "-o", binary.toString()));
        Path compilerLog = output.resolve("abi-compiler.log");
        Process compiler = new ProcessBuilder(compile).redirectErrorStream(true).redirectOutput(compilerLog.toFile()).start();
        if (!compiler.waitFor(5, TimeUnit.SECONDS)) { compiler.destroyForcibly(); throw new AssertionError("ABI compiler stuck"); }
        assertThat(compiler.exitValue()).withFailMessage("ABI compiler failed: %s", Files.readString(compilerLog)).isZero();
        Path cOutput = output.resolve("abi-c.txt");
        Process helper = new ProcessBuilder(binary.toString()).redirectErrorStream(true).redirectOutput(cOutput.toFile()).start();
        if (!helper.waitFor(5, TimeUnit.SECONDS)) { helper.destroyForcibly(); throw new AssertionError("ABI helper stuck"); }
        assertThat(helper.exitValue()).isZero();
        Map<String, Long> actual = values(Files.readString(cOutput));
        String expectedText = """
            void *=8
            long=8
            xcb_window_t=4
            xcb_atom_t=4
            xcb_void_cookie_t=4
            xcb_void_cookie_t.sequence=0
            xcb_generic_event_t=36
            xcb_generic_event_t.response_type=0
            xcb_generic_event_t.sequence=2
            xcb_generic_event_t.full_sequence=32
            xcb_generic_error_t=36
            xcb_generic_error_t.error_code=1
            xcb_generic_error_t.sequence=2
            xcb_generic_error_t.full_sequence=32
            xcb_client_message_event_t=32
            xcb_client_message_event_t.format=1
            xcb_client_message_event_t.window=4
            xcb_client_message_event_t.type=8
            xcb_client_message_event_t.data=12
            xcb_auth_info_t=32
            xcb_auth_info_t.namelen=0
            xcb_auth_info_t.name=8
            xcb_auth_info_t.datalen=16
            xcb_auth_info_t.data=24
            Xauth=64
            Xauth.family=0
            Xauth.address_length=2
            Xauth.address=8
            Xauth.number_length=16
            Xauth.number=24
            Xauth.name_length=32
            Xauth.name=40
            Xauth.data_length=48
            Xauth.data=56
            struct pollfd=8
            struct pollfd.fd=0
            struct pollfd.events=4
            struct pollfd.revents=6
            struct sockaddr_un=110
            struct sockaddr_un.sun_family=0
            struct sockaddr_un.sun_path=2
            struct stat.st_dev=0
            struct stat.st_ino=8
            struct stat.st_size=48
            struct stat.st_mtim.tv_sec=88
            struct stat.st_mtim.tv_nsec=96
            struct stat.st_ctim.tv_sec=104
            struct stat.st_ctim.tv_nsec=112
            xcb_setup_t=40
            xcb_setup_t.status=0
            xcb_setup_t.length=6
            xcb_setup_t.vendor_len=24
            xcb_setup_t.roots_len=28
            xcb_setup_t.pixmap_formats_len=29
            xcb_setup_t.image_byte_order=30
            xcb_screen_t=40
            xcb_screen_t.root=0
            xcb_screen_t.root_visual=32
            xcb_screen_t.root_depth=38
            xcb_screen_t.allowed_depths_len=39
            xcb_depth_t=8
            xcb_depth_t.depth=0
            xcb_depth_t.visuals_len=2
            xcb_visualtype_t=24
            xcb_visualtype_t.visual_id=0
            xcb_visualtype_t.red_mask=8
            xcb_visualtype_t.green_mask=12
            xcb_visualtype_t.blue_mask=16
            xcb_format_t=8
            xcb_format_t.depth=0
            xcb_format_t.bits_per_pixel=1
            xcb_format_t.scanline_pad=2
            xcb_generic_reply_t=8
            xcb_generic_reply_t.response_type=0
            xcb_generic_reply_t.sequence=2
            xcb_generic_reply_t.length=4
            xcb_intern_atom_reply_t.atom=8
            xcb_get_selection_owner_reply_t.owner=8
            AF_UNIX=1
            SOCK_STREAM=1
            SOCK_NONBLOCK=2048
            SOCK_CLOEXEC=524288
            SHUT_RDWR=2
            SOL_SOCKET=1
            SO_ERROR=4
            EINPROGRESS=115
            EAGAIN=11
            EINTR=4
            POLLIN=1
            POLLOUT=4
            POLLERR=8
            POLLHUP=16
            O_NONBLOCK=2048
            O_CLOEXEC=524288
            O_NOFOLLOW=131072
            F_DUPFD_CLOEXEC=1030
            S_IFMT=61440
            S_IFREG=32768
            FamilyLocal=256
            FamilyWild=65535
            """;
        Map<String, Long> expected = values(expectedText);
        boolean arm = Set.of("aarch64", "arm64").contains(System.getProperty("os.arch"));
        expected.put("O_NOFOLLOW", arm ? 32768L : 131072L);
        expected.put("struct stat", arm ? 128L : 144L); expected.put("struct stat.st_mode", arm ? 16L : 24L);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
        assertThat(Native.POINTER_SIZE).isEqualTo(8); assertThat(Native.LONG_SIZE).isEqualTo(8);
        var cookie = new LinuxTrayXcb.Cookie(); var auth = new LinuxTrayXcb.Auth();
        try {
            assertThat(cookie.size()).isEqualTo(4); assertThat(auth.size()).isEqualTo(32);
            var offset = Structure.class.getDeclaredMethod("fieldOffset", String.class); offset.setAccessible(true);
            assertThat(offset.invoke(cookie, "sequence")).isEqualTo(0);
            for (String name : List.of("namelen", "name", "datalen", "data"))
                assertThat(((Number)offset.invoke(auth, name)).longValue()).isEqualTo(actual.get("xcb_auth_info_t." + name));
        } finally { ((Memory)cookie.getPointer()).close(); ((Memory)auth.getPointer()).close(); }
        Files.writeString(output.resolve("abi-java.txt"), "PASS exact C values=" + actual.size() + " JNA cookie/auth layouts match\n" + expected);
        System.out.println("abiExactValues=" + actual.size() + " pointerBytes=8 longBytes=8 xidBytes=4");
    }
    private static Map<String, Long> values(String text) {
        var result = new LinkedHashMap<String, Long>();
        for (String line : text.strip().split("\\R")) {
            String[] entry = line.strip().split("=", 2); result.put(entry[0], Long.parseLong(entry[1]));
        }
        return result;
    }
}
