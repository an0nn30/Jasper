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
