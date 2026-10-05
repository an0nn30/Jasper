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
