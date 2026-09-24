package dev.jasper.remote.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;

/** Runs the fixed directory-probe.sh as {@code sh -s} on an exec channel; the user's shell parses only that command. */
final class DirectoryProbe {
    static final String COMMAND = "sh -s";
    static final int UNSUPPORTED = 3;
    private static final int LIMIT = 8192;
    private static final byte[] SCRIPT = load();

    private DirectoryProbe() { }

    private static byte[] load() {
        try (InputStream in = DirectoryProbe.class.getResourceAsStream("directory-probe.sh")) {
            if (in == null) throw new IllegalStateException("directory-probe.sh is missing");
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static byte[] script() { return SCRIPT.clone(); }

    static Optional<String> read(ClientSession session, Duration timeout) throws IOException {
        var output = new ByteArrayOutputStream();
        OutputStream bounded = new OutputStream() {
            @Override public synchronized void write(int value) { if (output.size() < LIMIT) output.write(value); }
            @Override public synchronized void write(byte[] bytes, int start, int length) { output.write(bytes, start, Math.min(length, Math.max(0, LIMIT - output.size()))); }
        };
        var channel = session.createExecChannel(COMMAND);
        try {
            channel.setIn(new ByteArrayInputStream(SCRIPT));
            channel.setOut(bounded);
            channel.setErr(OutputStream.nullOutputStream());
            channel.open().verify(timeout);
            if (!channel.waitFor(Set.of(ClientChannelEvent.CLOSED), timeout).contains(ClientChannelEvent.CLOSED)) throw new IOException("Directory probe timed out");
            Integer status = channel.getExitStatus();
            if (status != null && status == UNSUPPORTED) throw new ShellFolder.Unsupported();
            if (status == null || status != 0) throw new IOException("Directory probe failed");
            synchronized (bounded) { return parse(output.toByteArray()); }
        } finally {
            channel.close(true);
        }
    }

    /** The first NUL-terminated field when it is an absolute UTF-8 path. */
    static Optional<String> parse(byte[] output) {
        int end = -1;
        for (int i = 0; i < output.length; i++) if (output[i] == 0) { end = i; break; }
        if (end <= 0) return Optional.empty();
        try {
            String path = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output, 0, end)).toString();
            return path.startsWith("/") ? Optional.of(path) : Optional.empty();
        } catch (CharacterCodingException malformed) {
            return Optional.empty();
        }
    }
}
