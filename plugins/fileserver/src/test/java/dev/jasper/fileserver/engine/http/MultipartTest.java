package dev.jasper.fileserver.engine.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartTest {
    record Part(String fileName, String content) { }

    /** Hands out one byte per read, so every delimiter straddles reads. */
    static InputStream trickle(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            int at;
            @Override public int read() { return at < bytes.length ? bytes[at++] & 0xff : -1; }
            @Override public int read(byte[] target, int offset, int length) {
                if (at >= bytes.length) return -1;
                if (length == 0) return 0;
                target[offset] = bytes[at++];
                return 1;
            }
        };
    }

    static List<Part> read(String body) throws IOException {
        var parts = new ArrayList<Part>();
        Multipart.read(trickle(body), "XyZ", (name, content) -> parts.add(new Part(name, new String(content.readAllBytes(), StandardCharsets.UTF_8))));
        return parts;
    }

    @Test void readsFilePartsAndSkipsFields() throws IOException {
        String body = "preamble\r\n--XyZ\r\nContent-Disposition: form-data; name=\"note\"\r\n\r\nskip me\r\n--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"C:\\fakepath\\a.txt\"\r\nContent-Type: text/plain\r\n\r\n"
            + "line one\r\n--not the boundary\r\n--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"b %22q%22.bin\"\r\n\r\n\r\n--XyZ--\r\nepilogue";
        assertThat(read(body)).containsExactly(new Part("C:\\fakepath\\a.txt", "line one\r\n--not the boundary"), new Part("b \"q\".bin", ""));
        assertThat(read("--XyZ--\r\n")).isEmpty();
    }

    @Test void refusesMalformedForms() {
        assertThatThrownBy(() -> read("--XyZ\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a\"\r\n\r\nno end"))
            .isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("no boundary at all")).isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("--XyZ\r\n" + "X-Long: " + "a".repeat(20_000) + "\r\n\r\n")).isInstanceOf(Multipart.Malformed.class);
        assertThatThrownBy(() -> read("--XyZ??")).isInstanceOf(Multipart.Malformed.class);
    }

    @Test void findsTheBoundaryAndTheBaseName() {
        assertThat(Multipart.boundary("multipart/form-data; boundary=----WebKitFormBoundary7MA4")).isEqualTo("----WebKitFormBoundary7MA4");
        assertThat(Multipart.boundary("multipart/form-data; charset=utf-8; boundary=\"a b\"")).isEqualTo("a b");
        assertThatThrownBy(() -> Multipart.boundary("multipart/form-data")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Multipart.baseName("C:\\fakepath\\a.txt")).isEqualTo("a.txt");
        assertThat(Multipart.baseName("dir/sub/b.txt")).isEqualTo("b.txt");
        assertThat(Multipart.baseName("plain.txt")).isEqualTo("plain.txt");
    }
}
