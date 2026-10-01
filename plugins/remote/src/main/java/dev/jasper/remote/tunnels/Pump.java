package dev.jasper.remote.tunnels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.function.LongConsumer;
import org.apache.sshd.client.channel.ChannelDirectTcpip;

/** Copies bytes both ways between whoever connected and the far end, counting them and passing half-closes on. */
final class Pump {
    /** One side of a forwarded connection. */
    interface End {
        InputStream in() throws IOException;
        OutputStream out() throws IOException;
        /** Nothing more will be written to this side: a TCP half-close, or EOF on an SSH channel. */
        void finish() throws IOException;
        /** Closes this side; safe to call more than once and from any thread. */
        void close();
    }

    private static final int BUFFER = 32 * 1024;

    private Pump() { }

    static End of(Socket socket) {
        return new End() {
            @Override public InputStream in() throws IOException { return socket.getInputStream(); }
            @Override public OutputStream out() throws IOException { return socket.getOutputStream(); }
            @Override public void finish() throws IOException { socket.shutdownOutput(); }
            @Override public void close() { try { socket.close(); } catch (IOException ignored) { /* already gone */ } }
        };
    }

    static End of(ChannelDirectTcpip channel) {
        return new End() {
            @Override public InputStream in() { return channel.getInvertedOut(); }
            @Override public OutputStream out() { return channel.getInvertedIn(); }
            // Closing the channel's output stream sends SSH_MSG_CHANNEL_EOF; the server half-closes its socket.
            @Override public void finish() throws IOException { channel.getInvertedIn().close(); }
            @Override public void close() { channel.close(false); }
        };
    }

    /** Blocks until both directions end, then closes both ends. {@code near} is whoever connected; {@code far} the forwarded side. */
    static void run(End near, End far, Counters counters) {
        Runnable abort = () -> { near.close(); far.close(); };
        Thread upstream = Thread.ofVirtual().name("jasper-tunnel-pump").start(() -> copy(near, far, counters::sent, abort));
        copy(far, near, counters::received, abort);
        try { upstream.join(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        abort.run();
    }

    private static void copy(End from, End to, LongConsumer counted, Runnable abort) {
        byte[] buffer = new byte[BUFFER];
        try {
            InputStream in = from.in();
            OutputStream out = to.out();
            for (int read; (read = in.read(buffer)) >= 0; ) {
                if (read == 0) continue;
                out.write(buffer, 0, read);
                out.flush();
                counted.accept(read);
            }
            to.finish();
        } catch (IOException | RuntimeException broken) {
            // One side failed: closing both ends the other direction too.
            abort.run();
        }
    }
}
