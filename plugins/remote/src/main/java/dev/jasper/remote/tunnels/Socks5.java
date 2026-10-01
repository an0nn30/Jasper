package dev.jasper.remote.tunnels;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/** The server side of a SOCKS5 handshake (RFC 1928): no authentication, CONNECT only. The caller owns the streams. */
final class Socks5 {
    static final int SUCCEEDED = 0, HOST_UNREACHABLE = 4, COMMAND_NOT_SUPPORTED = 7, ADDRESS_NOT_SUPPORTED = 8;

    /** Where the client asked to go. A domain name stays unresolved, so the server resolves it (the socks5h behaviour). */
    record Target(String host, int port) { }

    /** A request this proxy does not serve; the client has already been told why when the protocol allows it. */
    static final class Refused extends IOException {
        Refused(String message) { super(message); }
    }

    private Socks5() { }

    /** Reads the greeting and the request and answers the greeting; the caller sends the final {@link #reply}. */
    static Target accept(InputStream input, OutputStream out) throws IOException {
        var in = new DataInputStream(input);
        if (in.readUnsignedByte() != 5) throw new Refused("not SOCKS5");
        int methods = in.readUnsignedByte();
        boolean noAuthentication = false;
        for (int i = 0; i < methods; i++) if (in.readUnsignedByte() == 0) noAuthentication = true;
        if (!noAuthentication) {
            out.write(new byte[] {5, (byte) 0xFF});
            out.flush();
            throw new Refused("no acceptable authentication method");
        }
        out.write(new byte[] {5, 0});
        out.flush();
        if (in.readUnsignedByte() != 5) throw new Refused("not SOCKS5");
        int command = in.readUnsignedByte();
        in.readUnsignedByte();
        int type = in.readUnsignedByte();
        String host = switch (type) {
            case 1 -> { byte[] address = new byte[4]; in.readFully(address); yield InetAddress.getByAddress(address).getHostAddress(); }
            case 3 -> { byte[] name = new byte[in.readUnsignedByte()]; in.readFully(name); yield new String(name, StandardCharsets.US_ASCII); }
            case 4 -> { byte[] address = new byte[16]; in.readFully(address); yield InetAddress.getByAddress(address).getHostAddress(); }
            default -> { reply(out, ADDRESS_NOT_SUPPORTED); throw new Refused("address type " + type + " is not supported"); }
        };
        int port = in.readUnsignedShort();
        if (command != 1) { reply(out, COMMAND_NOT_SUPPORTED); throw new Refused("only CONNECT is supported"); }
        return new Target(host, port);
    }

    /** The answer to the request, with an unspecified bound address. */
    static void reply(OutputStream out, int code) throws IOException {
        out.write(new byte[] {5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        out.flush();
    }
}
