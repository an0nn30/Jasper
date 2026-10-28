package dev.jasper.app.platform;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.awt.image.BufferedImage;
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
    @Test void premultipliesNativePixelsWithoutChangingDbusEncoding() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80ff0000);
        assertThat(LinuxTrayXcb.pixels(image, 32, 32, 0, 0xff0000, 0xff00, 0xff, 0xff000000))
            .containsExactly((byte)0, (byte)0, (byte)128, (byte)128);
        assertThat(LinuxTrayMenu.argb(image)).containsExactly((byte)128, (byte)255, (byte)0, (byte)0);
    }
    @Test void nativePixelsArePremultipliedAndMaskEncoded() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff804020);
        assertThat(LinuxTrayXcb.pixels(image, 16, 16, 0, 0xf800, 0x7e0, 0x1f, 0))
            .containsExactly((byte)4, (byte)130);
        assertThat(LinuxTrayXcb.pixels(image, 16, 32, 1, 0xf800, 0x7e0, 0x1f, 0))
            .containsExactly((byte)130, (byte)4, (byte)0, (byte)0);
        assertThat(LinuxTrayXcb.pixels(image, 24, 32, 0, 0xff0000, 0xff00, 0xff, 0))
            .containsExactly((byte)32, (byte)64, (byte)128, (byte)0);
        assertThat(LinuxTrayXcb.pixels(image, 32, 32, 1, 0xff0000, 0xff00, 0xff, 0))
            .containsExactly((byte)0, (byte)128, (byte)64, (byte)32);
        image.setRGB(0, 0, 0x80ff0000);
        assertThat(LinuxTrayXcb.pixels(image, 32, 32, 1, 0xff, 0xff00, 0xff0000, 0xff000000))
            .containsExactly((byte)128, (byte)0, (byte)0, (byte)128);
    }
    @Test void unsupportedPixelLayoutsAreRejectedBeforeAllocating() {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        for (int[] layout : new int[][] {{8, 32, 0, 0xe0, 0x1c, 3, 0},
                {16, 8, 0, 0xf800, 0x7e0, 31, 0}, {32, 32, 2, 0xff0000, 0xff00, 255, 0},
                {32, 32, 0, 0xff0000, 0xff00, 511, 0}, {32, 32, 0, 0xff0000, 0xff00, 5, 0},
                {16, 16, 0, 0xff0000, 0xff00, 255, 0}})
            assertThatThrownBy(() -> LinuxTrayXcb.pixels(image, layout[0], layout[1], layout[2],
                layout[3], layout[4], layout[5], layout[6])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinuxTrayXcb.pixels(new BufferedImage(513, 1, BufferedImage.TYPE_INT_ARGB),
            32, 32, 0, 0xff0000, 0xff00, 255, 0)).isInstanceOf(IllegalArgumentException.class);
    }

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
        assertThat(value.formats()).containsExactly(new LinuxTrayXcb.PixmapFormat(24, 32, 32), new LinuxTrayXcb.PixmapFormat(32, 32, 32));
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(setup, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(new byte[8], 0)).isInstanceOf(IllegalArgumentException.class);
        byte[] bad = setup.clone(); bad[30] = 2;
        assertThatThrownBy(() -> LinuxTrayXcb.decodeSetup(bad, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void nativeWindowRequestsHaveExactWireEncodingAndBarrier() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            xcb.select(0x100, 1 << 17, deadline());
            xcb.property32(0x200001, 81, 82, new int[] {0, 1}, deadline());
            xcb.property8(0x200001, 83, 84, new byte[] {65, 0}, deadline());
            int icon = xcb.createIcon(0x100, xcb.setup().visuals().getFirst(), 0, 24, deadline());
            xcb.sendClientMessage(0x123, 0x123, 85, 0, 0, 0, icon, 0, 0);
            xcb.destroyIcon(deadline());
            var packets = server.packets.stream().map(bytes -> ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())).toList();
            var select = packets.stream().filter(b -> b.get(0) == 2).findFirst().orElseThrow();
            assertThat(select.getInt(4)).isEqualTo(0x100); assertThat(select.getInt(8)).isEqualTo(1 << 11);
            assertThat(select.getInt(12)).isEqualTo(1 << 17);
            var props = packets.stream().filter(b -> b.get(0) == 18).toList();
            assertThat(props.get(0).get(16)).isEqualTo((byte)32);
            assertThat(props.get(0).getInt(20)).isEqualTo(2);
            assertThat(props.get(0).getInt(24)).isZero(); assertThat(props.get(0).getInt(28)).isEqualTo(1);
            assertThat(props.get(1).get(16)).isEqualTo((byte)8); assertThat(props.get(1).getInt(20)).isEqualTo(2);
            var send = packets.stream().filter(b -> b.get(0) == 25).findFirst().orElseThrow();
            assertThat(send.capacity()).isEqualTo(44); assertThat(send.getInt(4)).isEqualTo(0x123);
            assertThat(send.get(12)).isEqualTo((byte)33); assertThat(send.get(13)).isEqualTo((byte)32);
            assertThat(send.getInt(16)).isEqualTo(0x123); assertThat(send.getInt(20)).isEqualTo(85);
            assertThat(send.getInt(32)).isEqualTo(icon);
            assertThat(packets.stream().filter(b -> b.get(0) == 8)).isEmpty();
            assertThat(packets.stream().filter(b -> b.get(0) == 4).count()).isEqualTo(1);
            assertThat(packets.getLast().get(0)).isEqualTo((byte)43);
        }
    }
    @Test void malformedPropertyReplyIsNotAcceptedAsVisual() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            for (int[] values : new int[][] {{32, 82, 2}, {8, 82, 1}, {32, 83, 1}}) {
                server.responder = packet -> {
                    if (packet[0] != 20) return null;
                    var reply = ByteBuffer.allocate(40).order(ByteOrder.nativeOrder());
                    reply.put(0, (byte)1); reply.put(1, (byte)values[0]); reply.putInt(4, 2);
                    reply.putInt(8, values[1]); reply.putInt(16, values[2]); return reply.array();
                };
                assertThatThrownBy(() -> xcb.property32(0x123, 81, 82, deadline()))
                    .isInstanceOf(LinuxTrayXcb.Unavailable.class);
            }
        }
    }

    @Test void renderUsesParentRelativePremultipliedSourceAndFreesEveryResource() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            server.responder = LinuxTrayXcbTest::renderReply;
            try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                var visual = xcb.setup().visuals().getFirst();
                xcb.createIcon(0x100, visual, 0, 24, deadline());
                var image = new BufferedImage(24, 40, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, 0x80ff0000);
                xcb.paint(image, 24, 40, visual, false, deadline());
                xcb.paint(image, 24, 40, visual, false, deadline());
                xcb.destroyIcon(deadline());
                var packets = server.packets.stream().map(bytes -> ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())).toList();
                assertThat(packets.stream().filter(b -> b.get(0) == 53).count()).isEqualTo(1);
                assertThat(packets.stream().filter(b -> b.get(0) == 54).count()).isEqualTo(1);
                assertThat(packets.stream().filter(b -> b.get(0) == 55).count()).isEqualTo(1);
                assertThat(packets.stream().filter(b -> b.get(0) == 60).count()).isEqualTo(1);
                assertThat(packets.stream().filter(b -> b.get(0) == (byte)140 && b.get(1) == 4).count()).isEqualTo(2);
                assertThat(packets.stream().filter(b -> b.get(0) == (byte)140 && b.get(1) == 7).count()).isEqualTo(2);
                var upload = packets.stream().filter(b -> b.get(0) == 72).findFirst().orElseThrow();
                assertThat(upload.getShort(12)).isEqualTo((short)24); assertThat(upload.getShort(14)).isEqualTo((short)40);
                assertThat(Arrays.copyOfRange(upload.array(), 24, 28)).containsExactly((byte)0, (byte)0, (byte)128, (byte)128);
                assertThat(packets.stream().filter(b -> b.get(0) == 61).count()).isEqualTo(2);
                assertThat(packets.stream().filter(b -> b.get(0) == (byte)140 && b.get(1) == 8)
                    .allMatch(b -> b.get(4) == 3)).isTrue();
            }
        }
    }
    @Test void uncheckedMutationErrorsFailBarrierAndOrdinaryEventsSurvive() throws Exception {
        nativeOnly();
        for (int badOpcode : new int[] {1, 4}) {
            try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
                 var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                server.responder = packet -> {
                    if (Byte.toUnsignedInt(packet[0]) != badOpcode) return null;
                    byte[] error = new byte[32]; error[1] = 3; error[10] = (byte)badOpcode; return error;
                };
                if (badOpcode == 1) assertThatThrownBy(() -> { xcb.createIcon(0x100, xcb.setup().visuals().getFirst(), 0, 24, deadline()); xcb.barrier(deadline()); })
                    .isInstanceOf(LinuxTrayXcb.Unavailable.class);
                else {
                    xcb.createIcon(0x100, xcb.setup().visuals().getFirst(), 0, 24, deadline());
                    assertThatThrownBy(() -> xcb.destroyIcon(deadline())).isInstanceOf(LinuxTrayXcb.Unavailable.class);
                }
            }
        }
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation();
             var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
            server.eventsBeforeReply = 3; xcb.barrier(deadline());
            assertThat(xcb.poll(deadline())).hasSize(3).allMatch(event -> event.type() == 12);
        }
    }
    @Test void putImageTilesScanlinesWithinServerMaximumRequest() throws Exception {
        nativeOnly();
        try (var server = LinuxX11TestServer.filesystem(); var cancel = new LinuxTrayXcb.Cancellation()) {
            ByteBuffer.wrap(server.setupReply).order(ByteOrder.nativeOrder()).putShort(26, (short)64);
            server.responder = LinuxTrayXcbTest::renderReply;
            try (var xcb = LinuxTrayXcb.connect(Map.of("DISPLAY", server.address()), deadline(), cancel)) {
                var visual = xcb.setup().visuals().getFirst(); xcb.createIcon(0x100, visual, 0, 24, deadline());
                xcb.paint(new BufferedImage(24, 40, BufferedImage.TYPE_INT_ARGB), 24, 40, visual, false, deadline());
                var uploads = server.packets.stream().filter(packet -> packet[0] == 72)
                    .map(bytes -> ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())).toList();
                assertThat(uploads).hasSize(20);
                for (int i = 0; i < uploads.size(); i++) {
                    var upload = uploads.get(i); assertThat(upload.capacity()).isLessThanOrEqualTo(256);
                    assertThat(upload.getShort(14)).isEqualTo((short)2); assertThat(upload.getShort(18)).isEqualTo((short)(i * 2));
                }
                xcb.destroyIcon(deadline());
            }
        }
    }

    static byte[] renderReply(byte[] packet) {
        int opcode = Byte.toUnsignedInt(packet[0]);
        var reply = ByteBuffer.allocate(32).order(ByteOrder.nativeOrder()); reply.put(0, (byte)1);
        if (opcode == 98) { reply.put(8, (byte)1); reply.put(9, (byte)140); return reply.array(); }
        if (opcode == 14) { reply.put(1, (byte)24); reply.putShort(16, (short)24); reply.putShort(18, (short)40); return reply.array(); }
        if (opcode == 15) { reply.putInt(8, 0x100); reply.putInt(12, 0x100); return reply.array(); }
        if (opcode != 140) return null;
        if (packet[1] == 0) { reply.putInt(12, 11); return reply.array(); }
        if (packet[1] != 1) return new byte[0];
        var formats = ByteBuffer.allocate(128).order(ByteOrder.nativeOrder());
        formats.put(0, (byte)1); formats.putInt(4, 24); formats.putInt(8, 2); formats.putInt(12, 1);
        formats.putInt(16, 2); formats.putInt(20, 2);
        for (int i = 0; i < 2; i++) {
            int p = 32 + 28 * i; formats.putInt(p, 90 + i); formats.put(p + 4, (byte)1);
            formats.put(p + 5, (byte)(i == 0 ? 24 : 32));
            formats.putShort(p + 8, (short)16); formats.putShort(p + 10, (short)255);
            formats.putShort(p + 12, (short)8); formats.putShort(p + 14, (short)255);
            formats.putShort(p + 18, (short)255);
            if (i == 1) { formats.putShort(p + 20, (short)24); formats.putShort(p + 22, (short)255); }
        }
        formats.putInt(88, 2); formats.put(96, (byte)24); formats.putShort(98, (short)1);
        formats.putInt(104, 0x21); formats.putInt(108, 90);
        formats.put(112, (byte)32); formats.putShort(114, (short)1); formats.putInt(120, 0x40); formats.putInt(124, 91); return formats.array();
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

    static byte[] alphaSetup() {
        byte[] bytes = Arrays.copyOf(setupBytes(), 160); var b = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
        b.putShort(6, (short)38); b.put(95, (byte)2); b.put(128, (byte)32); b.putShort(130, (short)1);
        b.putInt(136, 0x40); b.put(140, (byte)4); b.put(141, (byte)8); b.putShort(142, (short)256);
        b.putInt(144, 0xff0000); b.putInt(148, 0xff00); b.putInt(152, 0xff); return bytes;
    }
    static byte[] setupBytes() {
        ByteBuffer b = ByteBuffer.allocate(128).order(ByteOrder.nativeOrder());
        b.put(0, (byte)1); b.putShort(2, (short)11); b.putShort(6, (short)30);
        b.putInt(12, 0x200000); b.putInt(16, 0x1fffff); b.putShort(26, (short)65535);
        b.put(28, (byte)1); b.put(29, (byte)2); b.put(30, (byte)(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? 0 : 1));
        b.put(32, (byte)32); b.put(33, (byte)32); b.put(34, (byte)8); b.put(35, (byte)255);
        b.put(40, (byte)24); b.put(41, (byte)32); b.put(42, (byte)32);
        b.put(48, (byte)32); b.put(49, (byte)32); b.put(50, (byte)32);
        b.putInt(56, 0x100); b.putInt(88, 0x21); b.put(94, (byte)24); b.put(95, (byte)1);
        b.put(96, (byte)24); b.putShort(98, (short)1);
        b.putInt(104, 0x21); b.put(108, (byte)4); b.put(109, (byte)8); b.putShort(110, (short)256);
        b.putInt(112, 0xff0000); b.putInt(116, 0xff00); b.putInt(120, 0xff);
        return b.array();
    }
}
