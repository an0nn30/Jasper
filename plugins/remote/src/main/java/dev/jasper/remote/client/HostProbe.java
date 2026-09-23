package dev.jasper.remote.client;

import dev.jasper.remote.hosts.HostInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/** Fixed read-only commands on a separate exec channel; never injects commands into the user's shell. */
final class HostProbe {
    private HostProbe() {}
    static HostInfo read(ClientSession session, boolean direct) {
        String address = direct && session.getIoSession().getRemoteAddress() instanceof InetSocketAddress socket && socket.getAddress() != null
            ? socket.getAddress().getHostAddress() : "";
        String os = "";
        try {
            String kernel = command(session, "uname -s").strip();
            if (kernel.equals("Darwin")) os = "macOS";
            else if (kernel.equals("Linux")) {
                os = "Linux";
                try {
                    for (String line : command(session, "cat /etc/os-release").lines().toList()) if (line.startsWith("PRETTY_NAME=")) {
                        String name = line.substring(12).strip();
                        if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) name = name.substring(1, name.length() - 1);
                        if (!name.isBlank()) os = name;
                        break;
                    }
                } catch (IOException ignored) { /* generic Linux remains useful */ }
            } else if (kernel.startsWith("MINGW") || kernel.startsWith("MSYS") || kernel.startsWith("CYGWIN")) os = "Windows";
            else if (!kernel.isBlank() && kernel.length() < 40 && !kernel.contains("\n")) os = kernel;
            else if (command(session, "cmd /c ver").contains("Windows")) os = "Windows";
        } catch (IOException unavailable) { /* shells may be allowed while exec requests are forbidden */ }
        return new HostInfo(os, address);
    }
    private static String command(ClientSession session, String command) throws IOException {
        var output = new ByteArrayOutputStream();
        OutputStream bounded = new OutputStream() {
            @Override public synchronized void write(int value) { if (output.size() < 8192) output.write(value); }
            @Override public synchronized void write(byte[] bytes, int start, int length) { output.write(bytes, start, Math.min(length, Math.max(0, 8192 - output.size()))); }
        };
        var channel = session.createExecChannel(command);
        try {
            channel.setOut(bounded); channel.setErr(OutputStream.nullOutputStream());
            channel.open().verify(Duration.ofSeconds(2));
            if (!channel.waitFor(Set.of(ClientChannelEvent.CLOSED), Duration.ofSeconds(2)).contains(ClientChannelEvent.CLOSED)) throw new IOException("Host information timed out");
            if (channel.getExitStatus() == null || channel.getExitStatus() != 0) return "";
            synchronized (bounded) { return output.toString(StandardCharsets.UTF_8); }
        } finally { channel.close(true); }
    }
}
