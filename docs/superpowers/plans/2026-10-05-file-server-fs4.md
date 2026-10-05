# File Server FS-4: TFTP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a TFTP server type to the File Server plugin. It is hand-written on the JDK's `DatagramSocket`, following RFC 1350, and supports the `blksize` and `tsize` options (RFC 2347–2349). It handles reads, writes on writable shares, netascii, retransmission, and a limit of 16 transfers at once. Serve Current Folder over TFTP is added too.

**Architecture:**
- A new `engine.tftp` package uses the JDK only:
  - `TftpPacket` encodes and decodes the six packet types.
  - `NetAscii` translates line ends.
  - `TftpEngine` owns the server socket and a pool of up to 16 transfer threads.
  - `TftpTransfer` runs one read or write on its own ephemeral socket, with timeouts and retransmission.
- Every path goes through FS-3's `engine.ClientPaths` and `ShareRoot`, as for the other protocols. A write uses `ShareRoot.upload` and commits only after the final block.
- `Engines`, the plugin, the editor and the documentation gain TFTP.

**Tech Stack:** Java 25 (JBR), Gradle wrapper, Swing; the JDK's `java.net.DatagramSocket` only. Tests also use commons-net 3.13.0's `TFTPClient`, which is already a test dependency.

**Spec:** `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md` (approved 2026-09-30). This plan implements its "TFTP" section and the FS-4 line under "Delivery". It builds on FS-3 (SFTP), which must be merged first; see Global Constraints.

## Global Constraints

- **Branch and worktree.** Work on branch `claude/file-server-fs4` in `.worktrees/file-server-fs4`. Create it from the finished `claude/file-server-fs3` branch, or from `main` once FS-3 is merged. FS-3 provides `engine.ClientPaths`, `HostKey`, the five-argument `Engines.factory`, `ServerEditor.KeySources` and File Server 0.3.0.
- **Git safety.** Never commit on `main`. Verify the branch (`git branch --show-current`) before every commit. Never use `git stash`.
- **Commits.** End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- **Building and testing.** Use `./gradlew` only. The plugin's project is `:jasper-plugin-fileserver`. Tests are headless and bind only to `127.0.0.1`.
- **Hands off.** Never launch the GUI; GUI checks go to the user. Never push.
- **Plugin dependencies.** The plugin compiles against the SDK and, optionally, `dev.jasper.vault.api`. `engine.tftp` uses the JDK only: no new dependency.
- **Version.** File Server becomes `0.4.0`. Keep the `sdk` range that `plugin.toml` has on `main`.
- **TFTP behaviour (from the spec):**
  - Default port 6969, read-only, bind `loopback`, no login and no TLS.
  - Modes `octet` and `netascii`.
  - `blksize` 8–65464; a larger value is answered with 65464, and a smaller one is ignored.
  - A lost packet is resent after 1 s, up to 5 times.
  - At most 16 transfers at once; further requests get error 0 "Server busy".
  - A WRQ on a writable server replaces an existing file.
- **Error packets (RFC 1350 codes):**
  - 0 "Server busy" or "Transfer failed".
  - 1 "File not found".
  - 2 "Access violation", for a refused path, a read-only server, or a folder as the target.
  - 3 "Disk full or allocation exceeded".
  - 4 "Illegal TFTP operation", for a malformed packet or an unknown mode.
  - 5 "Unknown transfer ID".
  - Error messages never name host paths.
- **Panel placement.** Do not change `PanelSpec`, `Anchor` or the app's panel or dock code.
- **Source hygiene.** No raw control, private-use or unpaired-surrogate characters in Java source (AGENTS.md). Write NUL as `0`, and CR and LF as `'\r'` and `'\n'`.

## Decisions made while planning (record in `docs/STATUS.md` and this plan's banner)

1. **Transfer threads.** The spec says transfers run "on the plugin's executor". Engines are not given the plugin's executor; HTTP, FTP and SFTP own their threads too. So `TftpEngine` owns a fixed pool of 16 daemon threads, and a semaphore turns away a 17th transfer with "Server busy". `stop()` closes every transfer socket and shuts the pool down.
2. **Writes commit at the end.** A write goes to `ShareRoot.upload` and commits only when the final, short DATA block arrives. A transfer that times out, fails or is stopped leaves the old file. After the final ACK the server waits one timeout for a repeated final block, and answers it with the final ACK again, so a lost ACK does not fail the client.
3. **Options.** `blksize` and `tsize` are honoured, and unknown options are ignored (RFC 2347).
   - On a read, `tsize` reports the file's size for octet mode. It is not answered in netascii mode, where the size on the wire differs.
   - On a write, a `tsize` larger than the free space of the share's volume is refused with error 3.
   - The `timeout` option is not honoured; the server always uses 1 s.
4. **Strays.** A packet from an address other than the transfer's client gets error 5 "Unknown transfer ID", and the transfer goes on.
   - A packet other than RRQ or WRQ on the server's port gets error 4.
   - A duplicate ACK is ignored rather than answered with a resend, which avoids the "sorcerer's apprentice" doubling.
5. **Block numbers wrap** from 65535 to 0, so files larger than 65535 blocks can still be sent, as most clients expect.
6. **Activity.** Each read or write is one transfer in the activity log (TFTP has no login and no session). A refused request is logged as an error naming the client path, for example "Access violation: /docs".
7. **A free port for Serve Current Folder.** `FileServerPlugin.portFree` now checks both TCP and UDP on loopback, so Serve Current Folder over TFTP skips a busy UDP port.
8. **Test seams.** `TftpEngine` has a package-private constructor that takes the timeout, the retry count and the transfer limit, so tests run in milliseconds. The public constructor uses 1 s, 5 and 16.

## File map

- `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tftp/{package-info,TftpPacket,NetAscii,TftpTransfer,TftpEngine}.java` (new).
- `runtime/Engines.java`, `FileServerPlugin.java`, `src/main/resources/plugin.toml`.
- Tests:
  - `engine/tftp/{TftpPacketTest,RawClient,TftpReadTest,TftpWriteTest}.java` (new).
  - `runtime/EnginesTest.java`, `ui/ServerEditorTest.java` and `FileServerPluginTest.java` (updated).
- Docs: `docs/file-server.md`, `docs/README.md`, `docs/STATUS.md`.

---

### Task 1: TFTP packets and netascii

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tftp/{package-info,TftpPacket,NetAscii}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/TftpPacketTest.java`

**Interfaces:**
- Produces (package-private, `dev.jasper.fileserver.engine.tftp`):
  - `sealed interface TftpPacket` with the constants `RRQ = 1, WRQ = 2, DATA = 3, ACK = 4, ERROR = 5, OACK = 6`, `MAX_BLOCK = 65464`, `DEFAULT_BLOCK = 512` and `MAX_PACKET = 4 + MAX_BLOCK`.
    - `static TftpPacket parse(byte[] buffer, int length)` throws `IllegalArgumentException` for a malformed packet.
    - `byte[] encode()`.
  - Records:
    - `Request(boolean write, String filename, String mode, Map<String, String> options)`: mode and option names lower-cased, options in their original order.
    - `Data(int block, byte[] bytes)`
    - `Ack(int block)`
    - `Error(int code, String message)`
    - `OptionAck(Map<String, String> options)`
  - `NetAscii.encoding(InputStream)` turns LF into CR LF and CR into CR NUL. `NetAscii.decoding(OutputStream)` reverses that; its `close()` writes a trailing lone CR, then closes the stream.

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/TftpPacketTest.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TftpPacketTest {
    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    @Test void parsesARequestWithOptions() {
        byte[] wire = bytes(0, 1, 'a', '.', 't', 'x', 't', 0, 'O', 'c', 't', 'e', 't', 0, 'B', 'L', 'K', 'S', 'I', 'Z', 'E', 0, '1', '0', '2', '4', 0,
            't', 's', 'i', 'z', 'e', 0, '0', 0);
        var request = (TftpPacket.Request) TftpPacket.parse(wire, wire.length);
        assertThat(request.write()).isFalse();
        assertThat(request.filename()).isEqualTo("a.txt");
        assertThat(request.mode()).isEqualTo("octet");
        assertThat(request.options()).containsExactly(Map.entry("blksize", "1024"), Map.entry("tsize", "0"));
        assertThat(request.encode()).as("encoding round-trips the lower-cased form")
            .isEqualTo(new TftpPacket.Request(false, "a.txt", "octet", request.options()).encode());
        var write = (TftpPacket.Request) TftpPacket.parse(bytes(0, 2, 'b', 0, 'n', 'e', 't', 'a', 's', 'c', 'i', 'i', 0), 13);
        assertThat(write.write()).isTrue();
        assertThat(write.mode()).isEqualTo("netascii");
        assertThat(write.options()).isEmpty();
    }

    @Test void encodesAndParsesTheOtherPackets() {
        assertThat(new TftpPacket.Data(258, "hi".getBytes(StandardCharsets.US_ASCII)).encode()).isEqualTo(bytes(0, 3, 1, 2, 'h', 'i'));
        var data = (TftpPacket.Data) TftpPacket.parse(bytes(0, 3, 0xFF, 0xFF, 7), 5);
        assertThat(data.block()).isEqualTo(65535);
        assertThat(data.bytes()).containsExactly(7);
        assertThat(new TftpPacket.Ack(1).encode()).isEqualTo(bytes(0, 4, 0, 1));
        assertThat(TftpPacket.parse(bytes(0, 4, 0, 9), 4)).isEqualTo(new TftpPacket.Ack(9));
        assertThat(new TftpPacket.Error(1, "File not found").encode())
            .isEqualTo(bytes(0, 5, 0, 1, 'F', 'i', 'l', 'e', ' ', 'n', 'o', 't', ' ', 'f', 'o', 'u', 'n', 'd', 0));
        assertThat(TftpPacket.parse(bytes(0, 5, 0, 2, 'n', 'o', 0), 7)).isEqualTo(new TftpPacket.Error(2, "no"));
        var options = new LinkedHashMap<String, String>();
        options.put("blksize", "1024");
        assertThat(new TftpPacket.OptionAck(options).encode()).isEqualTo(bytes(0, 6, 'b', 'l', 'k', 's', 'i', 'z', 'e', 0, '1', '0', '2', '4', 0));
        assertThat(TftpPacket.parse(new TftpPacket.OptionAck(options).encode(), 15)).isEqualTo(new TftpPacket.OptionAck(options));
    }

    @Test void refusesMalformedPackets() {
        assertThatThrownBy(() -> TftpPacket.parse(bytes(0, 4, 0), 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TftpPacket.parse(bytes(0, 9, 0, 0), 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TftpPacket.parse(bytes(0, 1, 'a', 0, 'o'), 5)).as("unterminated mode").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TftpPacket.parse(bytes(0, 1, 'a', 0), 4)).as("no mode").isInstanceOf(IllegalArgumentException.class);
    }

    @Test void netasciiTranslatesLineEndsBothWays() throws Exception {
        byte[] local = "a\nb\rc\n".getBytes(StandardCharsets.US_ASCII);
        byte[] wire = NetAscii.encoding(new ByteArrayInputStream(local)).readAllBytes();
        assertThat(wire).isEqualTo(bytes('a', '\r', '\n', 'b', '\r', 0, 'c', '\r', '\n'));
        var decoded = new ByteArrayOutputStream();
        try (var out = NetAscii.decoding(decoded)) {
            out.write(wire, 0, 4);  // a CR LF b: a split pair must still decode
            out.write(wire, 4, wire.length - 4);
        }
        assertThat(decoded.toByteArray()).isEqualTo(local);
        var trailing = new ByteArrayOutputStream();
        try (var out = NetAscii.decoding(trailing)) { out.write(bytes('x', '\r')); }
        assertThat(trailing.toByteArray()).as("a lone CR at the end is kept").isEqualTo(bytes('x', '\r'));
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tftp.*'`
Expected: compilation fails, because the package does not exist.

- [ ] **Step 3: Write the package**

`engine/tftp/package-info.java`:

```java
/**
 * The TFTP engine (RFC 1350, with the blksize and tsize options of RFC 2347–2349), written on the JDK's
 * DatagramSocket: packets, netascii, the server socket and one ephemeral socket per transfer. It may depend on
 * {@code engine}, {@code share}, {@code definition} and the JDK.
 */
package dev.jasper.fileserver.engine.tftp;
```

`engine/tftp/TftpPacket.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TFTP packets (RFC 1350), with option acknowledgements (RFC 2347). Strings are NUL-terminated and read as UTF-8. Mode
 * and option names are lower-cased; option values are kept as sent.
 */
sealed interface TftpPacket {
    int RRQ = 1, WRQ = 2, DATA = 3, ACK = 4, ERROR = 5, OACK = 6;
    int DEFAULT_BLOCK = 512;
    int MAX_BLOCK = 65464;
    int MAX_PACKET = 4 + MAX_BLOCK;

    byte[] encode();

    /** A read (RRQ) or write (WRQ) request. */
    record Request(boolean write, String filename, String mode, Map<String, String> options) implements TftpPacket {
        @Override public byte[] encode() {
            var out = new ByteArrayOutputStream();
            opcode(out, write ? WRQ : RRQ);
            text(out, filename);
            text(out, mode);
            options.forEach((name, value) -> { text(out, name); text(out, value); });
            return out.toByteArray();
        }
    }

    record Data(int block, byte[] bytes) implements TftpPacket {
        @Override public byte[] encode() {
            byte[] out = new byte[4 + bytes.length];
            out[1] = DATA;
            out[2] = (byte) (block >> 8);
            out[3] = (byte) block;
            System.arraycopy(bytes, 0, out, 4, bytes.length);
            return out;
        }
        @Override public boolean equals(Object other) { return other instanceof Data data && data.block == block && Arrays.equals(data.bytes, bytes); }
        @Override public int hashCode() { return 31 * block + Arrays.hashCode(bytes); }
        @Override public String toString() { return "Data[block=" + block + ", " + bytes.length + " bytes]"; }
    }

    record Ack(int block) implements TftpPacket {
        @Override public byte[] encode() { return new byte[]{0, ACK, (byte) (block >> 8), (byte) block}; }
    }

    record Error(int code, String message) implements TftpPacket {
        @Override public byte[] encode() {
            var out = new ByteArrayOutputStream();
            opcode(out, ERROR);
            out.write(code >> 8);
            out.write(code);
            text(out, message);
            return out.toByteArray();
        }
    }

    /** The options a server accepts, with the values it will use (RFC 2347). */
    record OptionAck(Map<String, String> options) implements TftpPacket {
        @Override public byte[] encode() {
            var out = new ByteArrayOutputStream();
            opcode(out, OACK);
            options.forEach((name, value) -> { text(out, name); text(out, value); });
            return out.toByteArray();
        }
    }

    /** One packet from the first {@code length} bytes of {@code buffer}; IllegalArgumentException when malformed. */
    static TftpPacket parse(byte[] buffer, int length) {
        if (length < 4) throw new IllegalArgumentException("A TFTP packet has at least 4 bytes");
        int opcode = u16(buffer, 0);
        return switch (opcode) {
            case RRQ, WRQ -> {
                List<String> fields = strings(buffer, 2, length);
                if (fields.size() < 2) throw new IllegalArgumentException("A request names a file and a mode");
                yield new Request(opcode == WRQ, fields.get(0), fields.get(1).toLowerCase(Locale.ROOT), options(fields.subList(2, fields.size())));
            }
            case DATA -> new Data(u16(buffer, 2), Arrays.copyOfRange(buffer, 4, length));
            case ACK -> new Ack(u16(buffer, 2));
            case ERROR -> {
                List<String> fields = length > 4 ? strings(buffer, 4, length) : List.of("");
                yield new Error(u16(buffer, 2), fields.isEmpty() ? "" : fields.getFirst());
            }
            case OACK -> new OptionAck(options(strings(buffer, 2, length)));
            default -> throw new IllegalArgumentException("Unknown TFTP opcode " + opcode);
        };
    }

    private static int u16(byte[] buffer, int at) { return ((buffer[at] & 0xFF) << 8) | (buffer[at + 1] & 0xFF); }

    /** The NUL-terminated strings from {@code from} to {@code length}; the last must be terminated. */
    private static List<String> strings(byte[] buffer, int from, int length) {
        var fields = new ArrayList<String>();
        int start = from;
        for (int i = from; i < length; i++) {
            if (buffer[i] != 0) continue;
            fields.add(new String(buffer, start, i - start, StandardCharsets.UTF_8));
            start = i + 1;
        }
        if (start != length) throw new IllegalArgumentException("A TFTP string is not terminated");
        return fields;
    }

    /** Name and value pairs; names lower-cased, a trailing unpaired name ignored. */
    private static Map<String, String> options(List<String> fields) {
        var options = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < fields.size(); i += 2) options.put(fields.get(i).toLowerCase(Locale.ROOT), fields.get(i + 1));
        return options;
    }

    private static void opcode(ByteArrayOutputStream out, int opcode) {
        out.write(0);
        out.write(opcode);
    }

    private static void text(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }
}
```

`engine/tftp/NetAscii.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** netascii line ends (RFC 764): on the wire LF is CR LF and CR is CR NUL. */
final class NetAscii {
    private NetAscii() { }

    /** A local file's bytes as netascii. */
    static InputStream encoding(InputStream local) {
        return new InputStream() {
            private int pending = -1;

            @Override public int read() throws IOException {
                if (pending >= 0) {
                    int next = pending;
                    pending = -1;
                    return next;
                }
                int value = local.read();
                if (value == '\n') { pending = '\n'; return '\r'; }
                if (value == '\r') { pending = 0; return '\r'; }
                return value;
            }

            @Override public void close() throws IOException { local.close(); }
        };
    }

    /** Netascii bytes written as a local file's; {@link OutputStream#close()} keeps a lone CR at the very end. */
    static OutputStream decoding(OutputStream local) {
        return new FilterOutputStream(local) {
            private boolean carriage;

            @Override public void write(int value) throws IOException {
                int b = value & 0xFF;
                if (carriage) {
                    carriage = false;
                    if (b == '\n') { out.write('\n'); return; }
                    if (b == 0) { out.write('\r'); return; }
                    out.write('\r');
                }
                if (b == '\r') { carriage = true; return; }
                out.write(b);
            }

            @Override public void close() throws IOException {
                if (carriage) {
                    carriage = false;
                    out.write('\r');
                }
                super.close();
            }
        };
    }
}
```

`FilterOutputStream.write(byte[], int, int)` calls `write(int)` for each byte, so the split-pair test passes. `super.close()` flushes, then closes the underlying stream once.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: TFTP packets and netascii, on the JDK alone

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The TFTP engine and reads

**Files:**
- Create: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tftp/{TftpTransfer,TftpEngine}.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/{RawClient,TftpReadTest}.java`

**Interfaces:**
- Consumes:
  - From Task 1: `TftpPacket` and `NetAscii`.
  - From FS-3: `engine.ClientPaths` (`absolute`, `relative`).
  - Existing: `ShareRoot.openRead`; `ServerEvents` (`transfer`, `error`); `ServerAddresses` (`bindAddress`, `reachable`, `urls`, `describe`, `hostText`); `EngineFailure.bind`.
- Produces:
  - `public TftpEngine(ServerDefinition definition, ShareRoot share, ServerEvents events, Supplier<List<ServerAddresses.Nic>> nics)` implements `ServerEngine`, with `public int port()`.
  - Package-private `TftpEngine(..., Duration timeout, int retries, int maxTransfers)`.
  - Package-private `TftpTransfer(ShareRoot, ServerEvents, InetSocketAddress client, TftpPacket.Request, Duration timeout, int retries)` with `void run(DatagramSocket)`.
  - The constants `TftpTransfer.BUSY`, `ILLEGAL`, `UNKNOWN_ID` and `DISK_FULL`, and `static TftpPacket.Error error(IOException)`.
  - Write requests are refused with error 2 until Task 3 adds them.

- [ ] **Step 1: Write the test client and the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/RawClient.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;

/** A TFTP client that sends exactly the packets a test asks for, so options, losses and strangers can be tested. */
final class RawClient implements AutoCloseable {
    /** A packet and the server port it came from (the transfer's own port, or the server's for refusals). */
    record Reply(int port, TftpPacket packet) { }

    final DatagramSocket socket;

    RawClient() throws IOException { socket = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)); }

    void send(int port, TftpPacket packet) throws IOException {
        byte[] bytes = packet.encode();
        socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getLoopbackAddress(), port));
    }

    Optional<Reply> receive(Duration wait) throws IOException {
        socket.setSoTimeout((int) Math.max(1, wait.toMillis()));
        byte[] buffer = new byte[TftpPacket.MAX_PACKET];
        var packet = new DatagramPacket(buffer, buffer.length);
        try { socket.receive(packet); }
        catch (SocketTimeoutException none) { return Optional.empty(); }
        return Optional.of(new Reply(packet.getPort(), TftpPacket.parse(packet.getData(), packet.getLength())));
    }

    Reply next() throws IOException {
        return receive(Duration.ofSeconds(5)).orElseThrow(() -> new AssertionError("no TFTP packet within 5 s"));
    }

    static TftpPacket.Request read(String file, String mode, String... options) { return new TftpPacket.Request(false, file, mode, options(options)); }
    static TftpPacket.Request write(String file, String mode, String... options) { return new TftpPacket.Request(true, file, mode, options(options)); }

    private static LinkedHashMap<String, String> options(String... pairs) {
        var options = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) options.put(pairs[i], pairs[i + 1]);
        return options;
    }

    @Override public void close() { socket.close(); }
}
```

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/TftpReadTest.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.abort;

class TftpReadTest {
    static final byte[] BIG = pattern(3000);
    @TempDir Path temp;
    Path shared, outside;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    TftpEngine engine;
    int port;

    static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) (i * 7);
        return bytes;
    }

    @BeforeEach void files() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        outside = Files.createDirectories(temp.resolve("outside"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
        Files.write(shared.resolve("big.bin"), BIG);
        Files.writeString(shared.resolve("lines.txt"), "a\nb\n");
        Files.writeString(outside.resolve("secret.txt"), "secret");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    TftpEngine engine(Duration timeout, int retries, int maxTransfers) throws IOException {
        port = Ports.free();
        var base = ServerDefinition.create("tftp", Protocol.TFTP, shared);
        var definition = new ServerDefinition(base.id(), "tftp", Protocol.TFTP, shared, base.bind(), port, Optional.empty(), false,
            base.login(), List.of(), false, false, false);
        engine = new TftpEngine(definition, new ShareRoot(shared, true), new ServerEvents(Clock.systemUTC(), events::add), List::of,
            timeout, retries, maxTransfers);
        return engine;
    }

    void start() throws Exception { engine(Duration.ofMillis(300), 5, 16).start(); }

    byte[] fetch(String name, int mode) throws IOException {
        var client = new TFTPClient();
        client.setDefaultTimeout(Duration.ofSeconds(3));
        client.open();
        try {
            var out = new ByteArrayOutputStream();
            client.receiveFile(name, mode, out, InetAddress.getLoopbackAddress(), port);
            return out.toByteArray();
        } finally {
            client.close();
        }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @Test void readsFilesOfAnySize() throws Exception {
        start();
        assertThat(engine.addresses()).containsExactly(URI.create("tftp://127.0.0.1:" + port + "/"));
        assertThat(fetch("docs/a.txt", TFTP.BINARY_MODE)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(fetch("/big.bin", TFTP.BINARY_MODE)).isEqualTo(BIG);
        await(() -> transferred().size() == 2, "both reads end");
        assertThat(transferred()).extracting(ServerEvent.Transferred::path, ServerEvent.Transferred::bytes, ServerEvent.Transferred::complete)
            .containsExactly(tuple("/docs/a.txt", 5L, true), tuple("/big.bin", 3000L, true));
    }

    @Test void netasciiSendsCrLf() throws Exception {
        start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("lines.txt", "netascii"));
            var first = client.next();
            assertThat(first.packet()).isEqualTo(new TftpPacket.Data(1, "a\r\nb\r\n".getBytes(StandardCharsets.US_ASCII)));
            client.send(first.port(), new TftpPacket.Ack(1));
        }
        String separator = System.lineSeparator();
        assertThat(new String(fetch("lines.txt", TFTP.ASCII_MODE), StandardCharsets.US_ASCII)).isEqualTo("a" + separator + "b" + separator);
    }

    @Test void refusalsAreErrorPacketsWithoutHostPaths() throws Exception {
        try { Files.createSymbolicLink(shared.resolve("out"), outside); }
        catch (UnsupportedOperationException | IOException refused) { abort("symbolic links are not available: " + refused); }
        start();
        assertThatThrownBy(() -> fetch("missing.txt", TFTP.BINARY_MODE)).hasMessageContaining("Error code 1");
        assertThatThrownBy(() -> fetch("../outside/secret.txt", TFTP.BINARY_MODE)).as(".. stops at the root").hasMessageContaining("Error code 1");
        assertThatThrownBy(() -> fetch("out/secret.txt", TFTP.BINARY_MODE)).as("an outward link").hasMessageContaining("Error code 2");
        assertThatThrownBy(() -> fetch("docs", TFTP.BINARY_MODE)).as("a folder").hasMessageContaining("Error code 2");
        assertThatThrownBy(() -> fetch("CON", TFTP.BINARY_MODE)).as("a refused name").hasMessageContaining("Error code 2");
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "mail"));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(4, "Illegal TFTP operation"));
            client.send(port, new TftpPacket.Ack(1));
            assertThat(client.next()).as("not a request").isEqualTo(new RawClient.Reply(port, new TftpPacket.Error(4, "Illegal TFTP operation")));
        }
        await(() -> events.stream().anyMatch(e -> e instanceof ServerEvent.Failed f && f.message().equals("File not found: /missing.txt")), "logged");
        assertThat(events).filteredOn(e -> e instanceof ServerEvent.Failed)
            .noneMatch(e -> ((ServerEvent.Failed) e).message().contains(temp.toString()));
    }

    @Test void blksizeAndTsizeAreNegotiated() throws Exception {
        start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("big.bin", "octet", "blksize", "1024", "tsize", "0"));
            var oack = client.next();
            assertThat(oack.port()).as("a transfer has its own port").isNotEqualTo(port);
            var expected = new LinkedHashMap<String, String>();
            expected.put("blksize", "1024");
            expected.put("tsize", "3000");
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(expected));
            int[] lengths = new int[3];
            client.send(oack.port(), new TftpPacket.Ack(0));
            for (int block = 1; block <= 3; block++) {
                var data = (TftpPacket.Data) client.next().packet();
                assertThat(data.block()).isEqualTo(block);
                lengths[block - 1] = data.bytes().length;
                client.send(oack.port(), new TftpPacket.Ack(block));
            }
            assertThat(lengths).containsExactly(1024, 1024, 952);
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet", "blksize", "4"));
            assertThat(client.next().packet()).as("blksize below 8 is ignored").isEqualTo(new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet", "blksize", "100000"));
            var oack = (TftpPacket.OptionAck) client.next().packet();
            assertThat(oack.options()).containsExactly(Map.entry("blksize", "65464"));
        }
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("lines.txt", "netascii", "tsize", "0"));
            assertThat(client.next().packet()).as("no tsize in netascii").isInstanceOf(TftpPacket.Data.class);
        }
    }

    @Test void aLostPacketIsResentThenTheTransferGivesUp() throws Exception {
        engine(Duration.ofMillis(200), 2, 16).start();
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            var first = client.next();
            var again = client.next();
            assertThat(again).as("resent after the timeout, from the same port").isEqualTo(first);
            client.send(first.port(), new TftpPacket.Ack(1));
        }
        await(() -> transferred().size() == 1 && transferred().getFirst().complete(), "completes once acknowledged");
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            int sends = 0;
            while (client.receive(Duration.ofMillis(600)).isPresent()) sends++;
            assertThat(sends).as("the first send and two retries").isEqualTo(3);
        }
        await(() -> transferred().size() == 2, "the second transfer ends");
        assertThat(transferred().get(1).complete()).isFalse();
    }

    @Test void theSeventeenthTransferIsBusyAndStrangersAreTurnedAway() throws Exception {
        engine(Duration.ofSeconds(2), 5, 1).start();
        try (var holder = new RawClient(); var other = new RawClient()) {
            holder.send(port, RawClient.read("docs/a.txt", "octet"));
            var held = holder.next();
            other.send(port, RawClient.read("docs/a.txt", "octet"));
            assertThat(other.next()).isEqualTo(new RawClient.Reply(port, new TftpPacket.Error(0, "Server busy")));
            other.send(held.port(), new TftpPacket.Ack(1));
            assertThat(other.next()).isEqualTo(new RawClient.Reply(held.port(), new TftpPacket.Error(5, "Unknown transfer ID")));
            holder.send(held.port(), new TftpPacket.Ack(1));
            await(() -> transferred().size() == 1, "the held transfer ends");
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            TftpPacket reply;
            do {
                other.send(port, RawClient.read("docs/a.txt", "octet"));
                reply = other.next().packet();
            } while (!(reply instanceof TftpPacket.Data) && System.nanoTime() < deadline);
            assertThat(reply).as("a free slot serves again").isInstanceOf(TftpPacket.Data.class);
        }
    }

    @Test void aBusyPortFailsClearlyAndStopReleasesEverything() throws Exception {
        engine(Duration.ofSeconds(2), 5, 16);
        try (var taken = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))) {
            assertThatThrownBy(engine::start).isInstanceOf(EngineFailure.class).hasMessage("Port " + port + " is already in use on 127.0.0.1");
        }
        assertThat(engine.addresses()).isEmpty();
        engine.start();
        assertThat(engine.port()).isEqualTo(port);
        try (var client = new RawClient()) {
            client.send(port, RawClient.read("docs/a.txt", "octet"));
            client.next();
            engine.stop();
            engine.stop();
        }
        await(() -> transferred().size() == 1, "the stopped transfer ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(engine.addresses()).isEmpty();
        new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), port)).close();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tftp.*'`
Expected: compilation fails, because `TftpEngine` does not exist.

- [ ] **Step 3: Write `TftpTransfer`**

```java
package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.engine.ClientPaths;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.IsFolderException;
import dev.jasper.fileserver.share.PathRefusedException;
import dev.jasper.fileserver.share.ReadOnlyShareException;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One TFTP read or write, on its own socket. Every packet is resent after the timeout, up to the retry count, and a
 * packet from anyone but the client gets error 5. A duplicate acknowledgement is ignored, never answered with a
 * resend. Refusals are error packets with fixed texts, never host paths.
 */
final class TftpTransfer {
    static final TftpPacket.Error BUSY = new TftpPacket.Error(0, "Server busy");
    static final TftpPacket.Error FAILED = new TftpPacket.Error(0, "Transfer failed");
    static final TftpPacket.Error NOT_FOUND = new TftpPacket.Error(1, "File not found");
    static final TftpPacket.Error ACCESS = new TftpPacket.Error(2, "Access violation");
    static final TftpPacket.Error DISK_FULL = new TftpPacket.Error(3, "Disk full or allocation exceeded");
    static final TftpPacket.Error ILLEGAL = new TftpPacket.Error(4, "Illegal TFTP operation");
    static final TftpPacket.Error UNKNOWN_ID = new TftpPacket.Error(5, "Unknown transfer ID");

    private final ShareRoot share;
    private final ServerEvents events;
    private final InetSocketAddress client;
    private final TftpPacket.Request request;
    private final Duration timeout;
    private final int retries;
    private final String remote;
    private final byte[] buffer = new byte[TftpPacket.MAX_PACKET];
    private DatagramSocket socket;
    private int blockSize = TftpPacket.DEFAULT_BLOCK;

    TftpTransfer(ShareRoot share, ServerEvents events, InetSocketAddress client, TftpPacket.Request request, Duration timeout, int retries) {
        this.share = share; this.events = events; this.client = client; this.request = request; this.timeout = timeout; this.retries = retries;
        this.remote = ServerAddresses.hostText(client.getAddress());
    }

    /** Runs the transfer on {@code transferSocket} and returns when it ends, however it ends. */
    void run(DatagramSocket transferSocket) {
        socket = transferSocket;
        try {
            boolean netascii = request.mode().equals("netascii");
            if (!netascii && !request.mode().equals("octet")) {
                send(ILLEGAL);
                return;
            }
            String path;
            try { path = ClientPaths.absolute("/", request.filename()); }
            catch (PathRefusedException refused) { refuse(refused, request.filename()); return; }
            if (request.write()) refuse(new ReadOnlyShareException(), path); // writes arrive in Task 3
            else read(path, netascii);
        } catch (IOException failure) {
            if (!socket.isClosed()) events.error(remote, "TFTP transfer failed: " + failure.getMessage());
        }
    }

    private void read(String path, boolean netascii) throws IOException {
        SeekableByteChannel channel;
        try { channel = share.openRead(ClientPaths.relative(path)); }
        catch (IOException refused) { refuse(refused, path); return; }
        try (channel; InputStream raw = Channels.newInputStream(channel);
             InputStream in = netascii ? NetAscii.encoding(new BufferedInputStream(raw)) : raw) {
            long size = channel.size();
            Map<String, String> accepted = negotiate(netascii ? -1 : size);
            var transfer = events.transfer(remote, ServerEvent.Direction.DOWNLOAD, path, netascii ? -1 : size, socket::close);
            boolean complete = false;
            try {
                if (!accepted.isEmpty() && !awaitAck(new TftpPacket.OptionAck(accepted).encode(), 0)) return;
                int block = 1;
                while (true) {
                    byte[] data = in.readNBytes(blockSize);
                    if (!awaitAck(new TftpPacket.Data(block, data).encode(), block)) return;
                    transfer.add(data.length);
                    if (data.length < blockSize) {
                        complete = true;
                        return;
                    }
                    block = (block + 1) & 0xFFFF;
                }
            } finally {
                transfer.finish(complete);
            }
        }
    }

    /**
     * The requested options this server accepts, with the values it uses (RFC 2347); sets the block size. tsize is
     * answered with {@code size}, or left out when {@code size} is -1.
     */
    private Map<String, String> negotiate(long size) {
        var accepted = new LinkedHashMap<String, String>();
        String block = request.options().get("blksize");
        if (block != null) {
            try {
                int asked = Integer.parseInt(block.strip());
                if (asked >= 8) {
                    blockSize = Math.min(asked, TftpPacket.MAX_BLOCK);
                    accepted.put("blksize", String.valueOf(blockSize));
                }
            } catch (NumberFormatException unusable) { /* left out, as RFC 2347 allows */ }
        }
        if (request.options().containsKey("tsize") && size >= 0) accepted.put("tsize", String.valueOf(size));
        return accepted;
    }

    /** Sends {@code outgoing} until the client acknowledges {@code block}; false when it gives up or the client sends an error. */
    private boolean awaitAck(byte[] outgoing, int block) throws IOException {
        for (int attempt = 0; attempt <= retries; attempt++) {
            send(outgoing);
            long deadline = System.nanoTime() + timeout.toNanos();
            for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline)) {
                if (reply instanceof TftpPacket.Ack ack && ack.block() == block) return true;
                if (reply instanceof TftpPacket.Error) return false;
            }
        }
        return false;
    }

    /** The client's next packet before the deadline, or null; anyone else gets error 5, and malformed packets are dropped. */
    TftpPacket receive(long deadline) throws IOException {
        while (true) {
            long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (left <= 0) return null;
            socket.setSoTimeout((int) Math.min(left, Integer.MAX_VALUE));
            var packet = new DatagramPacket(buffer, buffer.length);
            try { socket.receive(packet); }
            catch (SocketTimeoutException timedOut) { return null; }
            if (!client.equals(packet.getSocketAddress())) {
                reply(packet.getSocketAddress(), UNKNOWN_ID);
                continue;
            }
            try { return TftpPacket.parse(packet.getData(), packet.getLength()); }
            catch (IllegalArgumentException malformed) { /* dropped; a resend may follow */ }
        }
    }

    void send(TftpPacket packet) throws IOException { send(packet.encode()); }

    void send(byte[] bytes) throws IOException { socket.send(new DatagramPacket(bytes, bytes.length, client)); }

    private void reply(SocketAddress to, TftpPacket packet) {
        byte[] bytes = packet.encode();
        try { socket.send(new DatagramPacket(bytes, bytes.length, to)); }
        catch (IOException ignored) { /* a stranger's loss */ }
    }

    /** Answers a refusal with its error packet and logs it under the client path. */
    void refuse(IOException failure, String path) throws IOException {
        TftpPacket.Error error = error(failure);
        send(error);
        events.error(remote, error.message() + ": " + path);
    }

    /** The error packet for a failure; fixed texts only. */
    static TftpPacket.Error error(IOException failure) {
        if (failure instanceof NoSuchFileException) return NOT_FOUND;
        if (failure instanceof PathRefusedException || failure instanceof ReadOnlyShareException || failure instanceof IsFolderException
            || failure instanceof AccessDeniedException) return ACCESS;
        String text = String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT);
        if (text.contains("no space") || text.contains("disk full") || text.contains("not enough space")) return DISK_FULL;
        return FAILED;
    }
}
```

- [ ] **Step 4: Write `TftpEngine`**

```java
package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.EngineFailure;
import dev.jasper.fileserver.engine.ServerAddresses;
import dev.jasper.fileserver.engine.ServerEngine;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * A TFTP server for one share, on the JDK: the server's port takes requests, and each transfer runs on its own
 * ephemeral socket. At most 16 run at once; another request gets error 0 "Server busy". A lost packet is resent after
 * one second, up to five times. TFTP has no login: anyone who can reach the port can use it.
 */
public final class TftpEngine implements ServerEngine {
    static final Duration TIMEOUT = Duration.ofSeconds(1);
    static final int RETRIES = 5;
    static final int MAX_TRANSFERS = 16;
    private final ServerDefinition definition;
    private final ShareRoot share;
    private final ServerEvents events;
    private final Supplier<List<ServerAddresses.Nic>> nics;
    private final Duration timeout;
    private final int retries, maxTransfers;
    private final Set<DatagramSocket> transferSockets = ConcurrentHashMap.newKeySet();
    private DatagramSocket socket;
    private ExecutorService transfers;
    private Semaphore slots;
    private Thread listener;
    private InetAddress address;
    private volatile boolean running;
    private int port;

    public TftpEngine(ServerDefinition definition, ShareRoot share, ServerEvents events, Supplier<List<ServerAddresses.Nic>> nics) {
        this(definition, share, events, nics, TIMEOUT, RETRIES, MAX_TRANSFERS);
    }

    TftpEngine(ServerDefinition definition, ShareRoot share, ServerEvents events, Supplier<List<ServerAddresses.Nic>> nics,
               Duration timeout, int retries, int maxTransfers) {
        this.definition = definition; this.share = share; this.events = events; this.nics = nics;
        this.timeout = timeout; this.retries = retries; this.maxTransfers = maxTransfers;
    }

    @Override public synchronized void start() throws EngineFailure {
        if (socket != null) return;
        InetAddress bindTo = ServerAddresses.bindAddress(definition.bind(), nics.get());
        DatagramSocket bound;
        try { bound = new DatagramSocket(new InetSocketAddress(bindTo, definition.port())); }
        catch (SocketException failure) { throw EngineFailure.bind(failure, definition.port(), ServerAddresses.describe(bindTo), definition.protocol()); }
        address = bindTo;
        socket = bound;
        port = bound.getLocalPort();
        slots = new Semaphore(maxTransfers);
        transfers = Executors.newFixedThreadPool(maxTransfers, Thread.ofPlatform().daemon().name("file-server-tftp-transfer-", 0).factory());
        running = true;
        DatagramSocket listening = bound;
        listener = Thread.ofPlatform().daemon().name("file-server-tftp-" + port).start(() -> listen(listening));
    }

    /** Takes requests until the socket closes; each gets a slot and its own socket, or an error packet. */
    private void listen(DatagramSocket server) {
        byte[] buffer = new byte[TftpPacket.MAX_PACKET];
        while (running) {
            var received = new DatagramPacket(buffer, buffer.length);
            try { server.receive(received); }
            catch (IOException closed) {
                if (!running) return;
                continue;
            }
            var client = (InetSocketAddress) received.getSocketAddress();
            TftpPacket packet;
            try { packet = TftpPacket.parse(received.getData(), received.getLength()); }
            catch (IllegalArgumentException malformed) { reply(server, client, TftpTransfer.ILLEGAL); continue; }
            if (!(packet instanceof TftpPacket.Request request)) { reply(server, client, TftpTransfer.ILLEGAL); continue; }
            if (!slots.tryAcquire()) { reply(server, client, TftpTransfer.BUSY); continue; }
            try { transfers.execute(() -> serve(client, request)); }
            catch (RejectedExecutionException stopping) { slots.release(); }
        }
    }

    private void serve(InetSocketAddress client, TftpPacket.Request request) {
        try (DatagramSocket transferSocket = new DatagramSocket(new InetSocketAddress(address, 0))) {
            transferSockets.add(transferSocket);
            try {
                if (running) new TftpTransfer(share, events, client, request, timeout, retries).run(transferSocket);
            } finally {
                transferSockets.remove(transferSocket);
            }
        } catch (SocketException noSocket) {
            if (running) events.error(ServerAddresses.hostText(client.getAddress()), "TFTP transfer failed: " + noSocket.getMessage());
        } finally {
            slots.release();
        }
    }

    private static void reply(DatagramSocket server, InetSocketAddress client, TftpPacket packet) {
        byte[] bytes = packet.encode();
        try { server.send(new DatagramPacket(bytes, bytes.length, client)); }
        catch (IOException ignored) { /* the client will time out */ }
    }

    @Override public synchronized void stop() {
        if (socket == null) return;
        running = false;
        socket.close();
        transferSockets.forEach(DatagramSocket::close);
        transfers.shutdownNow();
        try { listener.join(1000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        socket = null;
        transfers = null;
    }

    @Override public synchronized List<URI> addresses() {
        if (socket == null) return List.of();
        return ServerAddresses.urls(definition.protocol(), false, ServerAddresses.reachable(definition.bind(), nics.get()), port, Optional.empty());
    }

    /** The bound port; 0 before the first start. */
    public synchronized int port() { return port; }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tftp.*'`, twice (the tests use real sockets; check that they are steady), then `./gradlew :jasper-plugin-fileserver:check`.
Expected: PASS each time.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: a TFTP engine on the JDK, with reads, options, retransmission and a transfer limit

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: TFTP writes

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/engine/tftp/TftpTransfer.java`
- Test: `plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/TftpWriteTest.java`

**Interfaces:**
- Consumes: `ShareRoot.upload` (and FS-3's `Upload.stream()`), `NetAscii.decoding`, and Task 2's `TftpTransfer` helpers.
- Produces:
  - A WRQ on a writable share uploads to a temporary file. The file replaces the target only after the final, short block.
  - A read-only share answers error 2.
  - A `tsize` larger than the free space answers error 3.
  - A repeated final block within one timeout is answered with the final ACK again.

- [ ] **Step 1: Write the failing tests**

`plugins/fileserver/src/test/java/dev/jasper/fileserver/engine/tftp/TftpWriteTest.java`:

```java
package dev.jasper.fileserver.engine.tftp;

import dev.jasper.fileserver.Ports;
import dev.jasper.fileserver.definition.Protocol;
import dev.jasper.fileserver.definition.ServerDefinition;
import dev.jasper.fileserver.engine.ServerEvent;
import dev.jasper.fileserver.engine.ServerEvents;
import dev.jasper.fileserver.share.ShareRoot;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TftpWriteTest {
    static final byte[] BIG = TftpReadTest.pattern(2000);
    @TempDir Path temp;
    Path shared;
    final List<ServerEvent> events = new CopyOnWriteArrayList<>();
    TftpEngine engine;
    int port;

    @BeforeEach void files() throws IOException {
        shared = Files.createDirectories(temp.resolve("shared"));
        Files.createDirectories(shared.resolve("docs"));
        Files.writeString(shared.resolve("docs/a.txt"), "hello");
    }

    @AfterEach void stop() { if (engine != null) engine.stop(); }

    void start(boolean writable, Duration timeout) throws Exception {
        port = Ports.free();
        var base = ServerDefinition.create("tftp", Protocol.TFTP, shared);
        var definition = new ServerDefinition(base.id(), "tftp", Protocol.TFTP, shared, base.bind(), port, Optional.empty(), writable,
            base.login(), List.of(), false, false, false);
        engine = new TftpEngine(definition, new ShareRoot(shared, !writable), new ServerEvents(Clock.systemUTC(), events::add), List::of,
            timeout, 5, 16);
        engine.start();
    }

    void put(String name, int mode, byte[] content) throws IOException {
        var client = new TFTPClient();
        client.setDefaultTimeout(Duration.ofSeconds(3));
        client.open();
        try { client.sendFile(name, mode, new ByteArrayInputStream(content), InetAddress.getLoopbackAddress(), port); }
        finally { client.close(); }
    }

    List<String> docs() throws IOException {
        try (var listing = Files.list(shared.resolve("docs"))) { return listing.map(p -> p.getFileName().toString()).sorted().toList(); }
    }

    List<ServerEvent.Transferred> transferred() {
        return events.stream().filter(e -> e instanceof ServerEvent.Transferred).map(e -> (ServerEvent.Transferred) e).toList();
    }

    static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertThat(condition.getAsBoolean()).as(what).isTrue();
    }

    @Test void aWritableShareTakesNewAndReplacedFiles() throws Exception {
        start(true, Duration.ofMillis(300));
        put("docs/new.bin", TFTP.BINARY_MODE, BIG);
        assertThat(shared.resolve("docs/new.bin")).hasBinaryContent(BIG);
        put("/docs/a.txt", TFTP.BINARY_MODE, "replaced".getBytes(StandardCharsets.UTF_8));
        assertThat(shared.resolve("docs/a.txt")).hasContent("replaced");
        assertThat(docs()).as("no temporary files remain").containsExactly("a.txt", "new.bin");
        await(() -> transferred().size() == 2, "both writes end");
        assertThat(transferred()).allSatisfy(done -> {
            assertThat(done.direction()).isEqualTo(ServerEvent.Direction.UPLOAD);
            assertThat(done.complete()).isTrue();
        });
        assertThat(transferred().getFirst().bytes()).isEqualTo(2000);
    }

    @Test void netasciiWritesBecomeLocalLineEnds() throws Exception {
        start(true, Duration.ofMillis(300));
        put("docs/lines.txt", TFTP.ASCII_MODE, ("a" + System.lineSeparator() + "b" + System.lineSeparator()).getBytes(StandardCharsets.US_ASCII));
        assertThat(shared.resolve("docs/lines.txt")).hasContent("a\nb\n");
    }

    @Test void aReadOnlyShareAndAFolderAreRefused() throws Exception {
        start(false, Duration.ofMillis(300));
        assertThatThrownBy(() -> put("docs/new.txt", TFTP.BINARY_MODE, new byte[]{1})).hasMessageContaining("Error code 2");
        assertThat(shared.resolve("docs/new.txt")).doesNotExist();
        engine.stop();
        start(true, Duration.ofMillis(300));
        assertThatThrownBy(() -> put("docs", TFTP.BINARY_MODE, new byte[]{1})).hasMessageContaining("Error code 2");
    }

    @Test void anUnfinishedWriteLeavesTheOriginal() throws Exception {
        start(true, Duration.ofMillis(100));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/a.txt", "octet"));
            var ack0 = client.next();
            assertThat(ack0.packet()).isEqualTo(new TftpPacket.Ack(0));
            client.send(ack0.port(), new TftpPacket.Data(1, new byte[512]));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
        }
        await(() -> transferred().size() == 1, "the abandoned write ends");
        assertThat(transferred().getFirst().complete()).isFalse();
        assertThat(shared.resolve("docs/a.txt")).hasContent("hello");
        assertThat(docs()).containsExactly("a.txt");
    }

    @Test void optionsAndALostFinalAck() throws Exception {
        start(true, Duration.ofMillis(500));
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/b.txt", "octet", "blksize", "1024", "tsize", "5"));
            var oack = client.next();
            var expected = new LinkedHashMap<String, String>();
            expected.put("blksize", "1024");
            expected.put("tsize", "5");
            assertThat(oack.packet()).isEqualTo(new TftpPacket.OptionAck(expected));
            client.send(oack.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Ack(1));
            client.send(oack.port(), new TftpPacket.Data(1, "hello".getBytes(StandardCharsets.UTF_8)));
            assertThat(client.next().packet()).as("the final ACK again, for a client that lost it").isEqualTo(new TftpPacket.Ack(1));
        }
        assertThat(shared.resolve("docs/b.txt")).hasContent("hello");
        try (var client = new RawClient()) {
            client.send(port, RawClient.write("docs/huge.bin", "octet", "tsize", String.valueOf(Long.MAX_VALUE)));
            assertThat(client.next().packet()).isEqualTo(new TftpPacket.Error(3, "Disk full or allocation exceeded"));
        }
        assertThat(shared.resolve("docs/huge.bin")).doesNotExist();
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tftp.TftpWriteTest'`
Expected: FAIL. Every write is still answered with error 2.

- [ ] **Step 3: Add writes to `TftpTransfer`**

Add the imports `java.io.OutputStream` and `java.nio.file.Files`. In `run`, replace the dispatch line with:

```java
            if (request.write()) write(path, netascii);
            else read(path, netascii);
```

Then add:

```java
    private void write(String path, boolean netascii) throws IOException {
        if (share.readOnly()) {
            refuse(new ReadOnlyShareException(), path);
            return;
        }
        long announced = announcedSize();
        if (announced > usableSpace()) {
            send(DISK_FULL);
            events.error(remote, DISK_FULL.message() + ": " + path);
            return;
        }
        ShareRoot.Upload upload;
        try { upload = share.upload(ClientPaths.relative(path)); }
        catch (IOException refused) { refuse(refused, path); return; }
        Map<String, String> accepted = negotiate(netascii ? -1 : announced);
        var transfer = events.transfer(remote, ServerEvent.Direction.UPLOAD, path, announced, socket::close);
        boolean complete = false;
        try (upload) {
            OutputStream out = netascii ? NetAscii.decoding(upload.stream()) : upload.stream();
            byte[] lastSent = (accepted.isEmpty() ? new TftpPacket.Ack(0) : new TftpPacket.OptionAck(accepted)).encode();
            int block = 1;
            for (TftpPacket.Data data = awaitData(lastSent, block); data != null; data = awaitData(lastSent, block)) {
                try { out.write(data.bytes()); }
                catch (IOException failed) { send(error(failed)); return; }
                transfer.add(data.bytes().length);
                lastSent = new TftpPacket.Ack(block).encode();
                if (data.bytes().length < blockSize) {
                    try {
                        out.close();
                        upload.commit();
                    } catch (IOException failed) {
                        send(error(failed));
                        return;
                    }
                    complete = true;
                    send(lastSent);
                    dally(lastSent, block);
                    return;
                }
                block = (block + 1) & 0xFFFF;
            }
        } finally {
            transfer.finish(complete);
        }
    }

    /** Sends {@code lastSent} until block {@code expected} arrives; a repeat of the previous block is answered at once. Null when it gives up. */
    private TftpPacket.Data awaitData(byte[] lastSent, int expected) throws IOException {
        int previous = (expected - 1) & 0xFFFF;
        for (int attempt = 0; attempt <= retries; attempt++) {
            send(lastSent);
            long deadline = System.nanoTime() + timeout.toNanos();
            for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline)) {
                if (reply instanceof TftpPacket.Data data && data.block() == expected) return data;
                if (reply instanceof TftpPacket.Data data && data.block() == previous) send(lastSent);
                if (reply instanceof TftpPacket.Error) return null;
            }
        }
        return null;
    }

    /** After the final ACK, answers a repeated final block for one timeout, for a client whose ACK was lost. */
    private void dally(byte[] finalAck, int block) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (TftpPacket reply = receive(deadline); reply != null; reply = receive(deadline))
            if (reply instanceof TftpPacket.Data data && data.block() == block) send(finalAck);
    }

    /** The client's tsize for a write, or -1 when it sent none or an unusable one. */
    private long announcedSize() {
        String tsize = request.options().get("tsize");
        if (tsize == null) return -1;
        try { return Math.max(-1, Long.parseLong(tsize.strip())); }
        catch (NumberFormatException unusable) { return -1; }
    }

    private long usableSpace() {
        try { return Files.getFileStore(share.root()).getUsableSpace(); }
        catch (IOException unknown) { return Long.MAX_VALUE; }
    }
```

`try (upload)` abandons the upload on every early return. After a commit, its `close()` does nothing.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.engine.tftp.*'`, twice, then `./gradlew :jasper-plugin-fileserver:check`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add plugins/fileserver/src
git commit -m "File Server: TFTP writes, committed only after the final block

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: TFTP in the plugin — factory, Serve Current Folder, version 0.4.0

**Files:**
- Modify: `plugins/fileserver/src/main/java/dev/jasper/fileserver/runtime/Engines.java`, `FileServerPlugin.java`, `src/main/resources/plugin.toml`
- Test: `runtime/EnginesTest.java`, `ui/ServerEditorTest.java`, `FileServerPluginTest.java`

**Interfaces:**
- Consumes: `TftpEngine` (Task 2), and FS-3's five-argument `Engines.factory` and `ServerEditor.KeySources`.
- Produces:
  - `Engines.AVAILABLE = Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP)`.
  - `FileServerPlugin.SERVE_TFTP = ID + ".serve-tftp"`, titled "Serve Current Folder over TFTP".
  - `FileServerPlugin.portFree(int)` checks UDP as well as TCP.

- [ ] **Step 1: Write the failing tests**

In `EnginesTest.java`, add the import `dev.jasper.fileserver.engine.tftp.TftpEngine`, rename the test to `theFactoryBuildsEveryEngine`, change the `AVAILABLE` assertion to `containsExactlyInAnyOrder(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP)`, and replace the TFTP refusal at its end with:

```java
        assertThat(factory.create(ServerDefinition.create("t", Protocol.TFTP, folder), share, ResolvedLogin.anonymous(), events)).isInstanceOf(TftpEngine.class);
```

Remove the `EngineFailure` import if nothing else in the file uses it.

In `ServerEditorTest.java`, add:

```java
    @Test void tftpHasNoLoginNoTlsAndNoKeys() {
        var editor = new ServerEditor(List.of(), Optional.empty(), false, Set.of(Protocol.values()), List.of(), false,
            id -> Optional.empty(), () -> CompletableFuture.completedFuture(Optional.empty()), () -> Optional.of(folder),
            ServerEditor.KeySources.NONE, false, saved::add, () -> cancelled = true);
        editor.protocol.setSelectedItem(Protocol.TFTP);
        assertThat(editor.port.getText()).isEqualTo("6969");
        assertThat(editor.anonymous.getText()).isEqualTo("No login (TFTP has none)");
        assertThat(editor.anonymous.isSelected()).isTrue();
        assertThat(editor.session.isEnabled()).isFalse();
        assertThat(editor.useTls.isEnabled()).isFalse();
        assertThat(editor.addKeyFile.isEnabled()).isFalse();
        editor.writable.doClick();
        assertThat(editor.warnings.getText()).contains("TFTP has no login: anyone who can reach it can change files");
        editor.name.setText("boot");
        editor.folder.setText(folder.toString());
        editor.save.doClick();
        assertThat(saved).singleElement().satisfies(definition -> {
            assertThat(definition.protocol()).isEqualTo(Protocol.TFTP);
            assertThat(definition.login()).isEqualTo(Login.ANONYMOUS);
            assertThat(definition.writable()).isTrue();
        });
    }
```

In `FileServerPluginTest.java`, change `INFO`'s version to `"0.4.0"`, and add:

```java
    @Test void servesTheFocusedFolderOverTftp() throws Exception {
        Path project = Files.createDirectories(home.resolve("project"));
        try (var host = new FakePluginHost(root)) {
            var plugin = plugin(port -> port != 6969);
            host.start(INFO, Set.of(), Set.of(), plugin);
            settle(host);
            assertThat(host.actions()).contains("dev.jasper.fileserver.serve-tftp|Serve Current Folder over TFTP|true");
            UUID window = host.addTerminalWindow(), tab = host.addTerminalTab(window, "zsh");
            UUID local = host.addTerminalPane(tab, pane(Optional.of(project)));
            host.openPanel(FileServerPlugin.PANEL, window);
            assertThat(host.invoke(FileServerPlugin.SERVE_TFTP, window, local)).isTrue();
            await(host, () -> plugin.registry().servers().stream().noneMatch(v -> v.state().phase() == Phase.STARTING), "the start settles");
            var served = plugin.registry().servers().getFirst();
            assertThat(served.temporary()).isTrue();
            assertThat(served.definition().name()).isEqualTo("project (TFTP)");
            assertThat(served.definition().protocol()).isEqualTo(Protocol.TFTP);
            assertThat(served.definition().port()).as("6969 was taken").isEqualTo(6970);
            assertThat(served.definition().writable()).isFalse();
            assertThat(served.definition().login()).isEqualTo(Login.ANONYMOUS);
            plugin.registry().stopAll();
            await(host, () -> plugin.registry().active() == 0, "everything stops");
        }
    }

    @Test void aPortIsFreeOnlyWhenBothTcpAndUdpAre() throws Exception {
        int port = Ports.free();
        assertThat(FileServerPlugin.portFree(port)).isTrue();
        try (var udp = new java.net.DatagramSocket(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port))) {
            assertThat(FileServerPlugin.portFree(port)).as("UDP taken").isFalse();
        }
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :jasper-plugin-fileserver:test --tests 'dev.jasper.fileserver.runtime.EnginesTest' --tests 'dev.jasper.fileserver.ui.ServerEditorTest' --tests 'dev.jasper.fileserver.FileServerPluginTest'`
Expected: compilation fails, because there is no `SERVE_TFTP`. Once that exists, the UDP port test and the TFTP factory test fail.

- [ ] **Step 3: Serve TFTP**

In `runtime/Engines.java`, add the import `dev.jasper.fileserver.engine.tftp.TftpEngine`, change `AVAILABLE` to `Set.of(Protocol.HTTP, Protocol.FTP, Protocol.SFTP, Protocol.TFTP)`, and replace the TFTP case with:

```java
            case TFTP -> new TftpEngine(definition, share, events, nics);
```

The switch now covers every protocol, so `EngineFailure` is no longer imported here (keep the import if anything else still uses it). Update the class comment if it says some protocols are unavailable.

In `FileServerPlugin.java`:
1. Add `public static final String SERVE_TFTP = ID + ".serve-tftp";` and, after the `SERVE_SFTP` registration:

```java
        context.actions().register(ActionSpec.of(SERVE_TFTP, "Serve Current Folder over TFTP").withIcon(icon)
                .withKeywords(List.of("file server", "share", "serve", "tftp", "boot", "folder")),
            invoked -> serve(invoked, Protocol.TFTP));
```

2. Replace `portFree` with the following, and add the import `java.net.DatagramSocket`:

```java
    /** Whether a port is free on loopback for both TCP and UDP, so Serve Current Folder can use it for any protocol. */
    static boolean portFree(int port) {
        var loopback = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
        try (var tcp = new ServerSocket(); var udp = new DatagramSocket(null)) {
            tcp.setReuseAddress(false);
            tcp.bind(loopback);
            udp.setReuseAddress(false);
            udp.bind(loopback);
            return true;
        } catch (IOException taken) {
            return false;
        }
    }
```

`portFree` stays package-private and static; the test in the same package calls it.

- [ ] **Step 4: Version and description**

In `plugin.toml`, set `version = "0.4.0"` and:

```toml
description = "Share a local folder with other computers over HTTP, HTTPS, FTP, FTPS, SFTP or TFTP: named servers in a File Servers panel, read-only and local-only by default, with an activity log, Vault logins and Serve Current Folder."
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-plugin-fileserver:check`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add plugins/fileserver
git commit -m "File Server 0.4.0: TFTP servers, and Serve Current Folder over TFTP

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Documentation, status and the full check

**Files:**
- Modify: `docs/file-server.md`, `docs/README.md`, `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-10-05-file-server-fs4.md` (status banner)

- [ ] **Step 1: Update the user guide**

In `docs/file-server.md`:
- The opening names all six (HTTP, HTTPS, FTP, FTPS, SFTP and TFTP). Remove "TFTP follows".
- In "Safe by default", add TFTP's default port 6969 (UDP).
- Add this section after "SFTP":

```markdown
## TFTP

TFTP serves network boot files and firmware to devices that speak nothing else (PXE clients, switches, phones,
embedded boards). It has **no login and no encryption**: anyone who can reach the port can read the folder, and on a
writable server can replace its files. Keep it on This computer only or on a private network, and read-only unless a
device must upload.

- Reads work on any server; writes (WRQ) only on a writable one, where they replace a file only once it has arrived
  completely.
- Modes `octet` and `netascii`; options `blksize` (8–65464) and `tsize`. Other options are ignored.
- Each transfer gets its own UDP port; at most 16 run at once, and a further request is told "Server busy". A lost
  packet is resent after a second, up to five times.
- `..` stops at the shared folder, links that lead outside are refused, and errors never name paths on this
  computer.
- TFTP runs over UDP: open the port for UDP in your firewall. Each transfer also uses a random UDP port, so a strict
  firewall must allow the replies too.
```

In `docs/README.md`, change the File Server line to: "share a folder over HTTP, HTTPS, FTP, FTPS, SFTP and TFTP from the File Servers panel."

- [ ] **Step 2: Update `docs/STATUS.md`**

Add a top entry under the current-state header, with today's date in the header:

```markdown
- **File Server FS-4: TFTP** (branch `claude/file-server-fs4`, plan `docs/superpowers/plans/2026-10-05-file-server-fs4.md`):
  File Server 0.4.0 serves TFTP with a hand-written engine on the JDK's `DatagramSocket` (`engine.tftp`: `TftpPacket`,
  `NetAscii`, `TftpTransfer`, `TftpEngine`). It handles RRQ, and WRQ when writable (committed after the final block),
  octet and netascii, `blksize`/`tsize` through OACK, a resend after 1 s up to 5 times, at most 16 transfers ("Server
  busy" beyond), error 5 for strangers, and fixed error texts that never name host paths. Serve Current Folder over
  TFTP; `portFree` checks UDP too. Decisions: see the plan header. GUI checks pending: `tftp` / `curl tftp://` from
  another computer, a PXE or firmware client, the macOS firewall prompt for UDP.
```

Add the exact test counts after Step 3.

- [ ] **Step 3: Run the full check and the hygiene script**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts from the XML reports. Then run the AGENTS.md source-hygiene script, and expect no output.

- [ ] **Step 4: Update the plan banner**

Under the plan's title, add a status line: "**Status:** executed on `claude/file-server-fs4` (date), `./gradlew check` green." List any deviations from the plan text, with the reason for each.

- [ ] **Step 5: Commit**

```bash
git add docs
git commit -m "File Server FS-4: documentation and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
