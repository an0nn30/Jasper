package dev.jasper.app.platform;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static dev.jasper.app.platform.DesktopObservation.*;

/** Owner-package bridge: real supervisor and decoder, controlled helper pipes and virtual deadlines. */
public final class DesktopColorsHarness implements AutoCloseable {
    private final DesktopProcessTest.Fixture fixture = new DesktopProcessTest.Fixture();
    private final Map<String, PipeProcess> processes = new ConcurrentHashMap<>();
    private final List<String> launches = new CopyOnWriteArrayList<>();
    public final DesktopColors service = new DesktopColors(Path.of("test", "helpers").toAbsolutePath(), fixture.time,
        fixture.queue::add, fixture.time::now, command -> {
            String name = Path.of(command.getFirst()).getFileName().toString();
            var process = new PipeProcess(); processes.put(name, process); launches.add(name); return process;
        });

    public void pump() { fixture.pump(); }
    public List<String> launches() { fixture.await(() -> !launches.isEmpty()); return List.copyOf(launches); }
    public void portal(long owner, boolean dark, String extra) {
        emit("portal", "{\"v\":1,\"kind\":\"portal\",\"owner\":" + owner + ",\"appearance\":\"" + (dark ? "dark" : "light") + "\"" + extra + ",\"reason\":\"\"}");
        fixture.await(() -> service.current().portal().owner() == owner);
        publish();
    }
    public void palette(Source source, boolean dark, String background, String reason) {
        palette(source, dark, background, reason, true);
    }
    public void reply(Source source, boolean dark, String background, String reason) {
        palette(source, dark, background, reason, false);
    }
    private void palette(Source source, boolean dark, String background, String reason, boolean accepted) {
        pump();
        var process = processes.get("jasper-desktop-" + source.name().toLowerCase(Locale.ROOT));
        fixture.await(() -> process.commands().contains("sample"));
        String commands = process.commands();
        String last = commands.lines().filter(line -> line.contains("sample")).reduce((a, b) -> b).orElseThrow();
        long generation = Long.parseLong(last.replaceAll(".*\"generation\":([0-9]+).*", "$1"));
        emit(source.name().toLowerCase(Locale.ROOT), "{\"v\":1,\"kind\":\"palette\",\"generation\":" + generation
            + ",\"source\":\"" + source.name().toLowerCase(Locale.ROOT) + "\",\"appearance\":\"" + (dark ? "dark" : "light")
            + "\",\"window.bg\":\"" + background + "\",\"window.fg\":\"" + (dark ? "1,1,1,1" : "0,0,0,1")
            + "\",\"implementation\":\"fixture\",\"libraryVersion\":\"1\",\"reason\":\"" + reason + "\"}");
        if (accepted) fixture.await(() -> service.current().palettes().get(source) != null
            && service.current().palettes().get(source).generation() == generation
            && service.current().palettes().get(source).reason().equals(reason));
        publish();
    }
    public void fail(Source source) {
        pump(); processes.get("jasper-desktop-" + source.name().toLowerCase(Locale.ROOT)).destroy();
        fixture.await(() -> service.current().palettes().containsKey(source) && !service.current().palettes().get(source).reason().isBlank());
        publish();
    }
    public void awaitToolkit(Source source, boolean alive) { fixture.await(() -> toolkitAlive(source) == alive); }
    public void awaitRequested(Source source, boolean dark) {
        fixture.await(() -> {
            var process = processes.get("jasper-desktop-" + source.name().toLowerCase(Locale.ROOT));
            if (process == null) return false;
            String last = process.commands().lines().filter(line -> line.contains("sample")).reduce((a, b) -> b).orElse("");
            return last.contains("\"appearance\":\"" + (dark ? "dark" : "light") + "\"");
        });
    }
    public void advance(long seconds) { fixture.time.advance(seconds, TimeUnit.SECONDS); pump(); }
    public long sampleCount(Source source) {
        pump();
        var process = processes.get("jasper-desktop-" + source.name().toLowerCase(Locale.ROOT));
        return process == null ? 0 : process.commands().lines().filter(line -> line.contains("sample")).count();
    }
    public void retry() { fixture.time.advance(1, TimeUnit.SECONDS); pump(); }
    public boolean toolkitAlive(Source source) {
        pump(); var process = processes.get("jasper-desktop-" + source.name().toLowerCase(Locale.ROOT));
        return process != null && process.isAlive();
    }
    private void emit(String name, String json) {
        fixture.await(() -> processes.containsKey("jasper-desktop-" + name));
        try { processes.get("jasper-desktop-" + name).emit(json); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private void publish() { fixture.time.advance(100, TimeUnit.MILLISECONDS); pump(); }
    @Override public void close() { service.close(); pump(); fixture.close(); }

    private static final class PipeProcess extends Process {
        private final PipedInputStream input = new PipedInputStream(65536);
        private final PipedOutputStream output;
        private final ByteArrayOutputStream commands = new ByteArrayOutputStream();
        private volatile boolean alive = true;
        PipeProcess() { try { output = new PipedOutputStream(input); } catch (IOException e) { throw new UncheckedIOException(e); } }
        String commands() { synchronized (commands) { return commands.toString(StandardCharsets.UTF_8); } }
        void emit(String line) throws IOException { output.write((line + "\n").getBytes(StandardCharsets.UTF_8)); output.flush(); }
        @Override public OutputStream getOutputStream() { return commands; }
        @Override public InputStream getInputStream() { return input; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public synchronized int waitFor() throws InterruptedException { while (alive) wait(); return 1; }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 1; }
        @Override public synchronized void destroy() { alive = false; try { output.close(); } catch (IOException ignored) { } notifyAll(); }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public boolean isAlive() { return alive; }
    }
}
