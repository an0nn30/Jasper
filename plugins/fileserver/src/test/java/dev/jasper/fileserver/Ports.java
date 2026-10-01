package dev.jasper.fileserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/** Test ports. */
public final class Ports {
    private Ports() { }

    /** A port that was free on 127.0.0.1 a moment ago. */
    public static int free() {
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { return socket.getLocalPort(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
