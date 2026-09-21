package dev.jasper.app.benchmark;

import dev.jasper.app.application.JasperApplication;
import dev.jasper.app.launch.ShellLauncher;
import dev.jasper.app.workspace.SplitTree;
import dev.jasper.app.workspace.TerminalPane;
import dev.jasper.app.workspace.TerminalTab;
import dev.jasper.app.workspace.TerminalWindow;
import dev.jasper.app.workspace.WindowContent;
import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.search.SearchQuery;
import dev.jasper.terminal.session.SessionLaunchOptions;

import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.session.TerminalSessionListener;
import javax.swing.*;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native benchmark orchestration. Never reached from production Main or headless check. */
@SuppressWarnings("try") // Cleanup intentionally preserves failures, including interrupted bounded waits.
final class BenchmarkRun implements AutoCloseable {
    private final BenchmarkOptions options;
    private final Path directory;
    private final Path data;
    private final int scrollback;
    private final ExecutorService launches = Executors.newCachedThreadPool();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor();
    private final List<Child> children = new CopyOnWriteArrayList<>();
    private final Set<Long> retiredChildPids = new LinkedHashSet<>();
    private final List<Map<String, Object>> samples = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean closing = new AtomicBoolean();
    private final BenchmarkRendering rendering = new BenchmarkRendering();
    private final BenchmarkLifetime deadline;
    private JasperApplication application;
    private TerminalWindow window;
    private RepaintManager previousRepaintManager;
    private volatile String phase = "startup";
    private volatile Throwable samplingFailure;
    private long launchStart;

    private static final class Child {
        final Path control;
        volatile TerminalSession session;
        volatile Throwable failure;
        volatile Long pid;
        volatile long streamStart, streamEnd;
        Child(Path control) { this.control = control; }
        Long pid() {
            if (pid == null) try { pid = Long.parseLong(Files.readString(control.resolve("pid"))); }
            catch (IOException | NumberFormatException ignored) { /* Child is not ready yet. */ }
            return pid;
        }
    }

    private BenchmarkRun(BenchmarkOptions options, Path directory, Path data, int scrollback, long remainingMillis) {
        this.options = options; this.directory = directory; this.data = data; this.scrollback = scrollback;
        deadline = new BenchmarkLifetime(Math.min(options.timeoutSeconds() * 1000L, remainingMillis), () -> {});
    }

    static void run(BenchmarkOptions options) throws Exception {
        long entireStart = System.nanoTime();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema", "jasper-benchmark"); report.put("schemaVersion", 1);
        report.put("status", "running"); report.put("startedAt", Instant.now().toString());
        report.put("environment", environment(options));
        report.put("options", Map.ofEntries(Map.entry("panes", options.panes()), Map.entry("scrollbacks", options.scrollbacks()),
            Map.entry("scenarios", options.scenarios()), Map.entry("repeats", options.repeats()), Map.entry("requestedBytes", options.bytes()),
            Map.entry("warmupMillis", options.warmupMillis()), Map.entry("settleMillis", options.settleMillis()),
            Map.entry("sampleMillis", options.sampleMillis()), Map.entry("cycles", options.cycles()),
            Map.entry("timeoutSecondsPerConfiguration", options.timeoutSeconds()), Map.entry("maxSeconds", options.maxSeconds())));
        List<Map<String, Object>> runs = new ArrayList<>(); report.put("runs", runs);
        Path temporary = Files.createTempDirectory("jasper-benchmark-");
        Exception failure = null;
        try {
            if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Native benchmarks require a display; check is headless");
            Path data = temporary.resolve("seed42-ansi-utf8.txt");
            report.put("preparation", BenchmarkFixture.prepare(data, options.bytes(), Math.min(120000L, options.maxSeconds() * 1000L)));
            long bytes = Files.size(data);
            report.put("workload", Map.of("seed", 42, "encoding", "UTF-8", "stagedBytesPerPane", bytes,
                "timingBoundary", "OSC title markers parsed on reader thread; payload excludes markers and PTY newline expansion"));
            for (int repeat = 1; repeat <= options.repeats(); repeat++) for (int panes : options.panes()) for (int scrollback : options.scrollbacks()) {
                long remaining = options.maxSeconds() * 1000L - (System.nanoTime() - entireStart) / 1_000_000;
                if (remaining <= 0) throw new TimeoutException("Total benchmark deadline");
                Map<String, Object> run = new LinkedHashMap<>(); runs.add(run);
                run.put("repeat", repeat); run.put("panes", panes); run.put("scrollbackPerPane", scrollback);
                run.put("coldScope", runs.size() == 1 ? "first application window in this JVM; payload prepared by exited child" : "new window in already warm JVM");
                run.put("startedAt", Instant.now().toString()); run.put("status", "running");
                BenchmarkReport.write(options.output(), report);
                try (BenchmarkRun owned = new BenchmarkRun(options, temporary, data, scrollback, remaining)) {
                    try { owned.execute(panes, bytes, run); }
                    finally { run.put("samples", owned.copySamples()); run.put("rendering", owned.rendering.snapshot());
                        run.put("childPids", owned.childPids()); }
                } catch (Exception error) {
                    run.put("status", "failed"); run.put("failure", error.toString()); throw error;
                } finally { run.put("finishedAt", Instant.now().toString()); }
                run.put("cleanup", BenchmarkMetrics.sample(List.of()));
                run.put("status", "complete");
                BenchmarkReport.write(options.output(), report);
            }
            report.put("status", "complete");
        } catch (Exception error) {
            failure = error; report.put("status", "failed"); report.put("failure", error.toString());
        } finally {
            report.put("finishedAt", Instant.now().toString());
            report.put("elapsedSeconds", (System.nanoTime() - entireStart) / 1e9);
            report.put("throughputMBpsSummary", BenchmarkReport.summary(runs.stream()
                .map(r -> (Double) r.get("streamMBps")).toList()));
            failure = finishReport(options.output(), report, failure, () -> deleteTemporary(temporary));
        }
        System.out.println("Benchmark " + report.get("status") + ": " + options.output());
        if (failure != null) throw failure;
    }

    /** Actual finalization boundary, with the owned scratch cleanup supplied by the caller. */
    static Exception finishReport(Path output, Map<String, Object> report, Exception failure, AutoCloseable cleanup) {
        try { cleanup.close(); } catch (Exception error) { failure = accumulate(failure, error); }
        finalStatus(report, failure);
        try { BenchmarkReport.write(output, report); }
        catch (Exception error) {
            failure = accumulate(failure, error);
            finalStatus(report, failure);
            // Correct each writable artifact independently if publishing its sibling failed.
            try { BenchmarkReport.writeCompanion(output, report); }
            catch (Exception companion) { failure = accumulate(failure, companion); finalStatus(report, failure); }
            try { BenchmarkReport.writeJson(output, report); }
            catch (Exception persistence) { failure = accumulate(failure, persistence); finalStatus(report, failure); }
        }
        return failure;
    }

    private static void finalStatus(Map<String, Object> report, Exception failure) {
        report.put("status", failure == null ? "complete" : "failed");
        if (failure != null) {
            report.put("failure", failure.toString());
            List<String> errors = new ArrayList<>(); collectFailures(failure, errors); report.put("failures", errors);
        }
    }
    private static void collectFailures(Throwable failure, List<String> errors) {
        errors.add(failure.toString());
        for (Throwable suppressed : failure.getSuppressed()) collectFailures(suppressed, errors);
    }
    private static Exception accumulate(Exception previous, Exception next) {
        if (previous == null) return next;
        if (previous != next) previous.addSuppressed(next);
        return previous;
    }
    private static void deleteTemporary(Path directory) throws IOException {
        List<IOException> failures = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            private void delete(Path path) { try { Files.deleteIfExists(path); } catch (IOException error) { failures.add(error); } }
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) {
                delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException error) {
                failures.add(error); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) {
                if (error != null) failures.add(error);
                delete(dir); return FileVisitResult.CONTINUE;
            }
        });
        if (!failures.isEmpty()) {
            IOException first = failures.getFirst();
            for (IOException error : failures.subList(1, failures.size())) first.addSuppressed(error);
            throw first;
        }
    }

    private static Map<String, Object> environment(BenchmarkOptions options) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("revision", options.revision()); env.put("mode", options.memory() ? "application-memory" : "application-throughput");
        env.put("ownership", "JasperApplication / TerminalWindow / WindowContent / TerminalPane / TerminalSession");
        env.put("jvmVendor", System.getProperty("java.vendor")); env.put("jvmVersion", System.getProperty("java.runtime.version"));
        env.put("javaHome", System.getProperty("java.home")); env.put("classpath", System.getProperty("java.class.path"));
        env.put("jvmOptions", ManagementFactory.getRuntimeMXBean().getInputArguments());
        env.put("jvmUptimeAtStartMillis", ManagementFactory.getRuntimeMXBean().getUptime());
        env.put("os", System.getProperty("os.name")); env.put("osVersion", System.getProperty("os.version"));
        env.put("arch", System.getProperty("os.arch")); env.put("pid", ProcessHandle.current().pid());
        env.put("heapAndGcSource", "JDK management beans; cumulative GC milliseconds / allocation bytes; no forced GC");
        env.put("residentSource", BenchmarkMetrics.residentSource()); env.put("forcedGc", false);
        env.put("fontFamily", dev.jasper.terminal.config.TerminalOptions.defaults().fontFamily()); env.put("fontSize", 16);
        env.put("layout", "single visible tab; alternating right/down splits of focused pane; window 1100x850 logical pixels");
        return env;
    }

    private void execute(int panes, long bytes, Map<String, Object> result) throws Exception {
        sample();
        ShellLauncher launcher = new ShellLauncher(launches, ignored -> launchFixture(), "benchmark fixture");
        launchStart = System.nanoTime();
        edt(() -> {
            previousRepaintManager = RepaintManager.currentManager(null); RepaintManager.setCurrentManager(rendering);
            application = new JasperApplication(null, launcher);
            window = application.newWindow(directory);
            window.content().onError = message -> samplingFailure = new IllegalStateException(message);
            window.resize(new Dimension(1100, 850));
            return null;
        });
        sampler.scheduleWithFixedDelay(() -> {
            try { rendering.probe(); sample(); } catch (Throwable error) { samplingFailure = error; }
        }, options.sampleMillis(), options.sampleMillis(), TimeUnit.MILLISECONDS);
        ready(window, 1);
        for (int i = 1; i < panes; i++) {
            int index = i;
            edt(() -> { window.content().currentTab().split(index % 2 == 0 ? SplitTree.Axis.DOWN : SplitTree.Axis.RIGHT); return null; });
            ready(window, i + 1);
        }
        drain(window);
        result.put("paneLayout", layout());
        result.put("windowSize", edt(() -> Map.of("width", window.size().width, "height", window.size().height)));
        phase = "cold-ready"; sample();
        if (options.memory()) { phase = "warm-idle"; pause(options.warmupMillis()); sample(); }
        // Interactive-only still needs populated scrollback; idle and cycles alone never emit a payload.
        if (options.scenarios().contains("output") || options.scenarios().contains("interactive")) {
            phase = "output"; rendering.reset();
            long gateStart = System.nanoTime();
            List<Child> outputChildren = List.copyOf(children);
            for (Child child : outputChildren) Files.createFile(child.control.resolve("go"));
            await(() -> outputChildren.stream().allMatch(c -> c.streamEnd > c.streamStart && c.streamStart > 0), "payload parser completion");
            long first = outputChildren.stream().mapToLong(c -> c.streamStart).min().orElseThrow();
            long last = outputChildren.stream().mapToLong(c -> c.streamEnd).max().orElseThrow();
            drain(window);
            double streamSeconds = (last - first) / 1e9;
            result.put("payloadBytes", bytes * outputChildren.size()); result.put("streamSeconds", streamSeconds);
            result.put("streamMBps", bytes * outputChildren.size() / 1e6 / streamSeconds);
            result.put("gateToFinalPaintSeconds", (System.nanoTime() - gateStart) / 1e9);
            if (!options.memory()) {
                double startup = (System.nanoTime() - launchStart) / 1e9;
                result.put("startupInclusiveSeconds", startup); result.put("startupInclusiveMBps", bytes / 1e6 / startup);
            }
            result.put("outputRendering", rendering.snapshot()); sample();
            phase = "output-settle"; pause(options.settleMillis()); sample();
        }
        if (options.scenarios().contains("interactive")) interactive();
        if (options.scenarios().contains("cycles")) cycles();
        phase = "final-settle"; pause(options.settleMillis()); sample();
        result.put("finalPaneLayout", layout());
        result.put("heapUsedBytesSummary", BenchmarkReport.summary(copySamples().stream()
            .map(s -> ((Number) s.get("heapUsedBytes")).doubleValue()).toList()));
        result.put("residentBytesSummary", BenchmarkReport.summary(copySamples().stream()
            .map(s -> s.get("residentBytes") == null ? null : ((Number) s.get("residentBytes")).doubleValue()).toList()));
    }

    private TerminalSession launchFixture() {
        Child child = null;
        try {
            if (closing.get()) throw new IllegalStateException("Benchmark closing");
            child = new Child(Files.createTempDirectory(directory, "child-")); children.add(child);
            TerminalSession session = TerminalSession.start(SessionLaunchOptions.builder().command(BenchmarkFixture.command(data, child.control, options.timeoutSeconds())).environment(BenchmarkFixture.environment()).workingDirectory(directory).grid(new GridSize(120, 36)).scrollback(scrollback).build());
            Child captured = child;
            session.addListener(new TerminalSessionListener() {
                @Override public void titleChanged(String title) {
                    if (title.equals(BenchmarkFixture.START)) captured.streamStart = System.nanoTime();
                    if (title.equals(BenchmarkFixture.END)) captured.streamEnd = System.nanoTime();
                }
            });
            child.session = session;
            if (closing.get()) session.close();
            return session;
        } catch (IOException | RuntimeException failure) {
            if (child != null) child.failure = failure;
            throw failure instanceof RuntimeException runtime ? runtime : new UncheckedIOException((IOException) failure);
        }
    }

    private void interactive() throws Exception {
        phase = "search-resize-font";
        for (int i = 0; i < options.cycles(); i++) {
            AtomicInteger completed = new AtomicInteger();
            int count = edt(() -> {
                var panes = window.content().currentTab().panes();
                for (TerminalPane pane : panes) pane.view().findAsync(new SearchQuery("jasper", false, true), ignored -> completed.incrementAndGet());
                return panes.size();
            });
            await(() -> completed.get() == count, "search callbacks");
            int cycle = i;
            edt(() -> {
                for (TerminalPane pane : window.content().currentTab().panes()) pane.view().setFontSize(cycle % 2 == 0 ? 17 : 16);
                window.resize(new Dimension(cycle % 2 == 0 ? 1050 : 1100, 850)); return null;
            });
            drain(window); pause(100);
        }
        edt(() -> { for (TerminalPane pane : window.content().currentTab().panes()) pane.view().setFontSize(16);
            window.resize(new Dimension(1100, 850)); return null; });
        drain(window); sample();
    }

    private void cycles() throws Exception {
        phase = "tab-split-window-cycles";
        for (int i = 0; i < options.cycles(); i++) {
            Set<Child> retained = Set.copyOf(children);
            TerminalTab temporaryTab = edt(() -> { window.content().newTab(directory); return window.content().currentTab(); });
            ready(window, 1);
            edt(() -> { temporaryTab.split(SplitTree.Axis.RIGHT); return null; }); ready(window, 2);
            edt(() -> { window.content().closeTab(temporaryTab); return null; });
            retireChildrenExcept(retained);
            TerminalWindow temporaryWindow = edt(() -> {
                TerminalWindow created = application.newWindow(directory);
                created.content().onError = message -> samplingFailure = new IllegalStateException(message);
                return created;
            });
            ready(temporaryWindow, 1); drain(temporaryWindow);
            edt(() -> { temporaryWindow.close(); return null; });
            retireChildrenExcept(retained);
            sample();
        }
    }

    private void retireChildrenExcept(Set<Child> retained) throws Exception {
        List<Child> retired = children.stream().filter(c -> !retained.contains(c)).toList();
        Exception failure = null;
        for (Child child : retired) {
            if (child.pid() != null) retiredChildPids.add(child.pid());
            try { closeChild(child); children.remove(child); }
            catch (Exception error) { failure = accumulate(failure, error); }
        }
        // Successfully retired sessions are released; failed children remain owned for final cleanup retry.
        if (failure != null) throw failure;
    }

    private List<Long> childPids() {
        Set<Long> pids = new LinkedHashSet<>(retiredChildPids);
        children.stream().map(Child::pid).filter(Objects::nonNull).forEach(pids::add);
        return List.copyOf(pids);
    }

    private List<Map<String, Object>> layout() throws Exception {
        return edt(() -> window.content().currentTab().panes().stream().map(p -> Map.<String, Object>of(
            "columns", p.session().columns(), "rows", p.session().rows(), "visible", p.view().isShowing(),
            "widthPixels", p.view().getWidth(), "heightPixels", p.view().getHeight(), "fontSize", p.view().fontSize())).toList());
    }
    private void ready(TerminalWindow owner, int paneCount) throws Exception {
        while (true) {
            check();
            boolean ready = edt(() -> {
                if (owner.closed()) throw new IllegalStateException("Benchmark window closed by user");
                var panes = owner.content().currentTab().panes();
                return panes.size() == paneCount && panes.stream().allMatch(p -> p.view() != null);
            });
            if (ready && children.stream().allMatch(c -> c.session != null && c.pid() != null)) return;
            Thread.sleep(10);
        }
    }
    private void drain(TerminalWindow owner) throws Exception {
        // Two EDT barriers allow queued resize/repaint invalidations to run; paintImmediately finishes the final image.
        edt(() -> null);
        edt(() -> { if (owner.closed()) throw new IllegalStateException("Benchmark window closed by user");
            for (TerminalPane pane : owner.content().currentTab().panes()) if (pane.view() != null)
                pane.view().paintImmediately(0, 0, pane.view().getWidth(), pane.view().getHeight()); return null; });
    }
    private void check() throws Exception {
        deadline.check("Configuration deadline during " + phase);
        if (samplingFailure != null) throw new IllegalStateException("Metric sampling failed", samplingFailure);
        for (Child child : children) {
            if (child.failure != null) throw new IllegalStateException("Fixture launch failed", child.failure);
            if (child.session != null && child.session.exitFuture().isDone()) throw new IllegalStateException("Fixture exited before cleanup");
        }
    }
    private void await(java.util.function.BooleanSupplier condition, String label) throws Exception {
        while (!condition.getAsBoolean()) { check(); Thread.sleep(10); }
        deadline.check(label);
    }
    private void pause(int millis) throws Exception {
        long end = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < end) { check(); Thread.sleep(Math.min(50, Math.max(1, millis))); }
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeLater(task);
        try { return task.get(5, TimeUnit.SECONDS); }
        catch (TimeoutException error) { task.cancel(false); throw error; }
    }
    private void sample() {
        String sampledPhase = phase;
        Map<String, Object> value = BenchmarkMetrics.sample(children.stream().map(Child::pid).filter(Objects::nonNull).toList());
        value.put("phase", sampledPhase); samples.add(value);
    }
    private List<Map<String, Object>> copySamples() { synchronized (samples) { return List.copyOf(samples); } }

    @Override public void close() throws Exception {
        closing.set(true); sampler.shutdownNow(); launches.shutdown();
        Exception failure = null;
        try {
            edt(() -> {
                try { if (application != null) application.quit(); }
                finally { if (previousRepaintManager != null) RepaintManager.setCurrentManager(previousRepaintManager); }
                return null;
            });
        } catch (Exception error) { failure = error; }
        try {
            if (!launches.awaitTermination(5, TimeUnit.SECONDS)) {
                launches.shutdownNow(); throw new TimeoutException("Fixture launch cleanup");
            }
        } catch (Exception error) { launches.shutdownNow(); failure = accumulate(failure, error); }
        for (Child child : children) try { closeChild(child); }
        catch (Exception error) { failure = accumulate(failure, error); }
        try {
            if (!sampler.awaitTermination(3, TimeUnit.SECONDS)) throw new TimeoutException("Metric sampler cleanup");
        } catch (Exception error) { failure = accumulate(failure, error); }
        children.clear(); application = null; window = null;
        if (failure != null) throw failure;
    }
    private static void closeChild(Child child) throws Exception {
        TerminalSession session = child.session;
        closeChild(child.control, child.pid(), session == null ? () -> {} : session::close,
            session == null ? CompletableFuture.completedFuture(0) : session.exitFuture());
        child.session = null;
    }

    /** Actual retirement boundary; tests inject session ownership without starting a PTY or window. */
    static void closeChild(Path control, Long pid, AutoCloseable closeSession, CompletableFuture<Integer> exit) throws Exception {
        Exception failure = null;
        try { Files.writeString(control.resolve("stop"), "stop"); }
        catch (Exception error) { failure = error; }
        try { closeSession.close(); } catch (Exception error) { failure = accumulate(failure, error); }
        try {
            if (pid != null) {
                Optional<ProcessHandle> process = ProcessHandle.of(pid);
                if (process.isPresent() && process.get().isAlive()) {
                    try { process.get().onExit().get(3, TimeUnit.SECONDS); }
                    catch (TimeoutException timeout) { process.get().destroyForcibly(); process.get().onExit().get(3, TimeUnit.SECONDS); }
                }
            }
        } catch (Exception error) { failure = accumulate(failure, error); }
        try { exit.get(3, TimeUnit.SECONDS); } catch (Exception error) { failure = accumulate(failure, error); }
        if (failure != null) throw failure;
    }
}
