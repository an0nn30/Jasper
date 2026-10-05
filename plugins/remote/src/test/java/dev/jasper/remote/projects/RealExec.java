package dev.jasper.remote.projects;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;

/** A server-side exec command that runs the requested command with this machine's {@code sh}, as an SSH server would. */
final class RealExec implements Command {
    private final String command;
    private final Path home;
    private InputStream in;
    private OutputStream out;
    private ExitCallback exit;
    private Process process;

    RealExec(String command, Path home) { this.command = command; this.home = home; }

    @Override public void setInputStream(InputStream value) { in = value; }
    @Override public void setOutputStream(OutputStream value) { out = value; }
    @Override public void setErrorStream(OutputStream value) { }
    @Override public void setExitCallback(ExitCallback value) { exit = value; }

    @Override public void start(ChannelSession channel, Environment env) throws IOException {
        var builder = new ProcessBuilder("sh", "-c", command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("HOME", home.toString());
        process = builder.start();
        Thread.ofVirtual().start(() -> {
            try (OutputStream stdin = process.getOutputStream()) { in.transferTo(stdin); }
            catch (IOException closed) { /* the process ended first */ }
        });
        Thread.ofVirtual().start(() -> {
            int status = 1;
            try (InputStream stdout = process.getInputStream()) {
                stdout.transferTo(out);
                out.flush();
                status = process.waitFor();
            } catch (IOException | InterruptedException ended) {
                // the channel closed
            } finally {
                exit.onExit(status);
            }
        });
    }

    @Override public void destroy(ChannelSession channel) { if (process != null) process.destroyForcibly(); }
}
