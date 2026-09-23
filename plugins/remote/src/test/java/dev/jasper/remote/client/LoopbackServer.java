package dev.jasper.remote.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.forward.AcceptAllForwardingFilter;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/**
 * An SSH server on 127.0.0.1 with an echo shell: it prints {@code READY <TERM> <COLUMNS>x<LINES>}, echoes
 * every byte, prints {@code WINCH <COLUMNS>x<LINES>} on a window change, and exits with 7 on {@code q}.
 * Password {@code deploy}/{@code s3cret}; public keys from {@link #allow}.
 */
public final class LoopbackServer implements AutoCloseable {
    public final SshServer server = SshServer.setUpDefaultServer();
    public final List<String> execCommands = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<PublicKey> allowed = new ArrayList<>();
    public final SimpleGeneratorHostKeyProvider hostKey = new SimpleGeneratorHostKeyProvider();

    public LoopbackServer() throws IOException { this(0); }

    public LoopbackServer(int port) throws IOException {
        hostKey.setAlgorithm("RSA"); hostKey.setKeySize(2048);
        server.setHost("127.0.0.1"); server.setPort(port);
        server.setKeyPairProvider(hostKey);
        server.setPasswordAuthenticator((user, password, session) -> user.equals("deploy") && password.equals("s3cret"));
        server.setPublickeyAuthenticator((user, key, session) -> KeyUtils.findMatchingKey(key, allowed) != null);
        server.setForwardingFilter(AcceptAllForwardingFilter.INSTANCE);
        server.setShellFactory(channel -> new Echo());
        server.start();
    }

    public int port() { return server.getPort(); }
    public PublicKey hostPublicKey() throws IOException, java.security.GeneralSecurityException { return hostKey.loadKeys(null).iterator().next().getPublic(); }
    public void allow(PublicKey key) { allowed.add(key); }
    @Override public void close() throws IOException { server.stop(true); }

    public void execReplies(java.util.Map<String, String> replies) { execReplies(replies, true); }
    public void execReplies(java.util.Map<String, String> replies, boolean finish) {
        server.setCommandFactory((channel, command) -> {
            execCommands.add(command);
            return new Command() {
                OutputStream out;
                ExitCallback exit;
                @Override public void setInputStream(InputStream in) {}
                @Override public void setOutputStream(OutputStream value) { out = value; }
                @Override public void setErrorStream(OutputStream err) {}
                @Override public void setExitCallback(ExitCallback value) { exit = value; }
                @Override public void start(ChannelSession channel, Environment env) throws IOException {
                    out.write(replies.getOrDefault(command, "").getBytes(StandardCharsets.UTF_8)); out.flush(); if (finish) exit.onExit(replies.containsKey(command) ? 0 : 1);
                }
                @Override public void destroy(ChannelSession channel) {}
            };
        });
    }

    static final class Echo implements Command {
        private InputStream in; private OutputStream out; private ExitCallback exit; private Thread thread;

        @Override public void setInputStream(InputStream in) { this.in = in; }
        @Override public void setOutputStream(OutputStream out) { this.out = out; }
        @Override public void setErrorStream(OutputStream err) { }
        @Override public void setExitCallback(ExitCallback callback) { this.exit = callback; }

        @Override public void start(ChannelSession channel, Environment env) {
            env.addSignalListener((ch, signal) -> write("WINCH " + env.getEnv().get(Environment.ENV_COLUMNS) + "x" + env.getEnv().get(Environment.ENV_LINES) + "\r\n"), Signal.WINCH);
            thread = Thread.ofPlatform().daemon().start(() -> {
                write("READY " + env.getEnv().get(Environment.ENV_TERM) + " " + env.getEnv().get(Environment.ENV_COLUMNS) + "x" + env.getEnv().get(Environment.ENV_LINES) + "\r\n");
                try {
                    int b;
                    while ((b = in.read()) >= 0) {
                        if (b == 'q') { exit.onExit(7); return; }
                        out.write(b); out.flush();
                    }
                } catch (IOException ended) { /* channel closed */ }
                exit.onExit(0);
            });
        }

        private synchronized void write(String text) { try { out.write(text.getBytes(StandardCharsets.UTF_8)); out.flush(); } catch (IOException ignored) { } }
        @Override public void destroy(ChannelSession channel) { if (thread != null) thread.interrupt(); }
    }
}
