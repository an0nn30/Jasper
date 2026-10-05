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

    /**
     * Whether the first {@code length} bytes of {@code buffer} carry the ERROR opcode, read from the raw bytes so a
     * malformed error counts too. An error is never answered (RFC 1350): answering one lets a single forged packet
     * start an endless exchange between two servers (CVE-2024-2169).
     */
    static boolean isError(byte[] buffer, int length) { return length >= 2 && buffer[0] == 0 && buffer[1] == ERROR; }

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
