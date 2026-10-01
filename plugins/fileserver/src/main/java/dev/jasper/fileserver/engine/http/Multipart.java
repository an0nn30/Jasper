package dev.jasper.fileserver.engine.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A streaming reader of {@code multipart/form-data}: each file part reaches the sink as a stream that ends at the
 * next boundary, so uploads of any size never sit in memory. Fields without a file name are skipped.
 */
final class Multipart {
    /** Receives one file part; whatever it leaves unread is skipped. */
    @FunctionalInterface interface FileSink { void accept(String fileName, InputStream content) throws IOException; }

    /** A form this reader cannot make sense of; the handler answers 400. */
    static final class Malformed extends IOException {
        Malformed(String message) { super(message); }
    }

    private static final int MAX_HEADERS = 16 * 1024;
    private static final Pattern BOUNDARY = Pattern.compile("(?i)(?:^|;)\\s*boundary=(?:\"([^\"]{1,70})\"|([^;\\s]{1,70}))");
    private static final Pattern FILENAME = Pattern.compile("(?i)(?:^|;)\\s*filename=\"([^\"]*)\"");
    private final InputStream in;
    private final byte[] delimiter;
    private final byte[] buffer = new byte[64 * 1024];
    private int start, end;
    private boolean eof;

    private Multipart(InputStream in, String boundary) {
        this.in = in;
        this.delimiter = ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        // The body starts with "--boundary"; a virtual CRLF in front lets one delimiter match every boundary.
        buffer[0] = '\r';
        buffer[1] = '\n';
        end = 2;
    }

    static String boundary(String contentType) {
        Matcher match = BOUNDARY.matcher(contentType == null ? "" : contentType);
        if (!match.find()) throw new IllegalArgumentException("The form has no boundary");
        return match.group(1) != null ? match.group(1) : match.group(2);
    }

    /** The last component of a file name a browser sent, which may be a full Windows path. */
    static String baseName(String fileName) {
        int cut = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        return fileName.substring(cut + 1).strip();
    }

    static void read(InputStream in, String boundary, FileSink sink) throws IOException {
        var reader = new Multipart(in, boundary);
        if (!reader.skipPreamble()) return;
        while (true) {
            Map<String, String> headers = reader.headers();
            Optional<String> fileName = fileName(headers.getOrDefault("content-disposition", ""));
            var body = reader.new Body();
            if (fileName.isPresent()) sink.accept(fileName.get(), body);
            body.transferTo(OutputStream.nullOutputStream());
            if (!reader.afterDelimiter()) return;
        }
    }

    /** Browsers percent-encode a quote in a file name and leave backslashes alone. */
    static Optional<String> fileName(String disposition) {
        Matcher match = FILENAME.matcher(disposition);
        return match.find() ? Optional.of(match.group(1).replace("%22", "\"")) : Optional.empty();
    }

    private boolean skipPreamble() throws IOException {
        while (true) {
            int at = indexOf(start, end);
            if (at >= 0) { start = at + delimiter.length; return afterDelimiter(); }
            start = Math.max(start, end - (delimiter.length - 1));
            if (!fill()) throw new Malformed("The form has no parts");
        }
    }

    /** After a boundary: true when another part follows, false after the closing one. */
    private boolean afterDelimiter() throws IOException {
        need(2);
        byte first = buffer[start], second = buffer[start + 1];
        start += 2;
        if (first == '-' && second == '-') return false;
        if (first == '\r' && second == '\n') return true;
        throw new Malformed("The form is malformed");
    }

    private Map<String, String> headers() throws IOException {
        var headers = new HashMap<String, String>();
        int used = 0;
        while (true) {
            int eol = indexOfCrlf();
            while (eol < 0) {
                if (end - start > MAX_HEADERS) throw new Malformed("Form part headers are too long");
                if (!fill()) throw new Malformed("The form ended early");
                eol = indexOfCrlf();
            }
            String line = new String(buffer, start, eol - start, StandardCharsets.UTF_8);
            used += eol - start + 2;
            start = eol + 2;
            if (used > MAX_HEADERS) throw new Malformed("Form part headers are too long");
            if (line.isEmpty()) return headers;
            int colon = line.indexOf(':');
            if (colon > 0) headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), line.substring(colon + 1).strip());
        }
    }

    private boolean fill() throws IOException {
        if (eof) return false;
        if (start > 0) {
            System.arraycopy(buffer, start, buffer, 0, end - start);
            end -= start;
            start = 0;
        }
        if (end == buffer.length) throw new Malformed("Form part headers are too long");
        int read = in.read(buffer, end, buffer.length - end);
        if (read < 0) { eof = true; return false; }
        end += read;
        return true;
    }

    private void need(int count) throws IOException {
        while (end - start < count) if (!fill()) throw new Malformed("The form ended early");
    }

    private int indexOf(int from, int to) {
        outer:
        for (int i = from; i <= to - delimiter.length; i++) {
            for (int j = 0; j < delimiter.length; j++) if (buffer[i + j] != delimiter[j]) continue outer;
            return i;
        }
        return -1;
    }

    private int indexOfCrlf() {
        for (int i = start; i + 1 < end; i++) if (buffer[i] == '\r' && buffer[i + 1] == '\n') return i;
        return -1;
    }

    /** One part's content, ending just before the next boundary. */
    private final class Body extends InputStream {
        private boolean done;

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (done) return -1;
            if (length == 0) return 0;
            while (true) {
                int at = indexOf(start, end);
                if (at == start) { start += delimiter.length; done = true; return -1; }
                int available = at >= 0 ? at - start : end - start - (delimiter.length - 1);
                if (available > 0) {
                    int count = Math.min(length, available);
                    System.arraycopy(buffer, start, target, offset, count);
                    start += count;
                    return count;
                }
                if (!fill()) throw new Malformed("The form ended early");
            }
        }
    }
}
