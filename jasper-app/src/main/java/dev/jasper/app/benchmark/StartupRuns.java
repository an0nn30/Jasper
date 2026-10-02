package dev.jasper.app.benchmark;

import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.startup.StartupTraceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Runs the start-up benchmark: each launch goes through LaunchServices ({@code open}), like a Dock click, with the trace
 * file and, by default, an isolated home passed in {@code JAVA_TOOL_OPTIONS}. Never part of the headless check.
 */
final class StartupRuns {
    /** Process control, injected so the planning code stays testable. */
    record Host(Function<List<String>, Integer> run, LongSupplier epochMillis, Predicate<Long> terminate,
                Function<Long, Long> residentKiB, Supplier<List<Long>> jasperPids) {
        static Host system() {
            return new Host(command -> {
                try { return new ProcessBuilder(command).inheritIO().start().waitFor(); }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return -1; }
            }, System::currentTimeMillis, pid -> ProcessHandle.of(pid).map(StartupRuns::stop).orElse(true), pid -> {
                try {
                    Process ps = new ProcessBuilder("ps", "-o", "rss=", "-p", Long.toString(pid)).start();
                    String text = new String(ps.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    ps.waitFor();
                    return parseRss(text);
                } catch (IOException failure) {
                    return -1L;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return -1L;
                }
            }, () -> ProcessHandle.allProcesses()
                .filter(p -> p.info().command().map(c -> c.endsWith("/Contents/MacOS/Jasper")).orElse(false))
                .map(ProcessHandle::pid).toList());
        }
    }

    /** {@code ps -o rss=} output in KiB, or -1 when there is none. */
    static long parseRss(String text) {
        try { return text.isBlank() ? -1 : Long.parseLong(text.strip()); }
        catch (NumberFormatException unexpected) { return -1; }
    }

    private final StartupBenchOptions options;
    private final Host host;

    StartupRuns(StartupBenchOptions options, Host host) {
        this.options = options;
        this.host = host;
    }

    static String launchOptions(Path traceFile, Path home) {
        return "-D" + StartupTrace.TRACE_FILE_PROPERTY + "=" + traceFile + (home == null ? "" : " -Duser.home=" + home);
    }

    static List<String> openCommand(Path app, String javaToolOptions, boolean background) {
        List<String> command = new ArrayList<>(List.of("open", "-n", "-a", app.toString(), "--env",
            "JAVA_TOOL_OPTIONS=" + javaToolOptions));
        if (background) command.addAll(List.of("--args", "--background"));
        return command;
    }

    static List<String> reopenCommand(Path app) { return List.of("open", "-a", app.toString()); }

    /**
     * Milliseconds from the click: the process start, every process milestone, or ({@code trigger} non-null) the
     * request and milestones of the latest window with that trigger.
     */
    static Map<String, Double> fromClick(Map<String, Object> trace, long clickMillis, String trigger) {
        double processStart = Instant.parse((String) trace.get("processStart")).toEpochMilli() - (double) clickMillis;
        Map<String, Double> result = new LinkedHashMap<>();
        if (trigger == null) {
            result.put("PROCESS_START", processStart);
            if (trace.get("milestones") instanceof Map<?, ?> milestones)
                milestones.forEach((name, at) -> result.put(name.toString(), processStart + ((Number) at).doubleValue()));
            return result;
        }
        Map<?, ?> window = null;
        if (trace.get("windows") instanceof List<?> windows)
            for (Object entry : windows) if (entry instanceof Map<?, ?> map && trigger.equals(map.get("trigger"))) window = map;
        if (window == null) return result;
        for (String name : List.of("requested", "SHOWN", "FIRST_PAINT", "OUTPUT_PAINTED"))
            if (window.get(name) instanceof Number at)
                result.put(name.equals("requested") ? "REQUESTED" : name, processStart + at.doubleValue());
        return result;
    }

    static Map<String, Map<String, Object>> summary(List<Map<String, Double>> runs) {
        Map<String, List<Double>> values = new TreeMap<>();
        for (Map<String, Double> run : runs) run.forEach((name, value) -> values.computeIfAbsent(name, key -> new ArrayList<>()).add(value));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        values.entrySet().stream()
            .sorted(Comparator.comparingDouble(entry -> median(entry.getValue())))
            .forEach(entry -> {
                List<Double> sorted = entry.getValue().stream().sorted().toList();
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("count", sorted.size());
                stats.put("median", median(sorted));
                stats.put("min", sorted.getFirst());
                stats.put("max", sorted.getLast());
                result.put(entry.getKey(), stats);
            });
        return result;
    }

    int run() throws IOException {
        if (!host.jasperPids().get().isEmpty()) {
            System.err.println("A Jasper process is running; quit every Jasper (including a resident one) first.");
            return 3;
        }
        // Short root: the handoff socket lives under the home, and macOS caps socket paths at 104 bytes.
        Path root = Files.createTempDirectory(Path.of("/tmp"), "jsb");
        try {
            Path home = options.realHome() ? null : Files.createDirectories(root.resolve("home"));
            if (home != null && options.mode().equals("warm")) {
                Path config = Files.createDirectories(home.resolve(".config/jasper")).resolve("config.toml");
                Files.writeString(config, "[system]\nrun_in_background = true\n", StandardCharsets.UTF_8);
            }
            if (options.realHome()) warnAboutTerminalMultiplexers();
            List<Map<String, Object>> runs = new ArrayList<>();
            List<Map<String, Double>> measured = new ArrayList<>();
            String status = "ok";
            for (int index = 0; index < options.prime() + options.runs(); index++) {
                Map<String, Object> result = options.mode().equals("warm") ? warm(root, home, index) : cold(root, home, index);
                if (index < options.prime()) continue;
                runs.add(result);
                String outcome = (String) result.get("outcome");
                if ("ok".equals(outcome)) {
                    if (result.get("fromClick") instanceof Map<?, ?> values) {
                        Map<String, Double> copy = new LinkedHashMap<>();
                        values.forEach((name, value) -> copy.put(name.toString(), ((Number) value).doubleValue()));
                        measured.add(copy);
                    }
                } else {
                    status = "incomplete";
                }
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", 1);
            report.put("benchmark", "startup");
            report.put("status", status);
            report.put("revision", options.revision());
            report.put("mode", options.mode());
            report.put("app", options.app().toString());
            report.put("realHome", options.realHome());
            report.put("runs", runs);
            report.put("summary", summary(measured));
            BenchmarkReport.writeJson(options.output(), report);
            writeCompanion(report);
            return status.equals("ok") ? 0 : 1;
        } finally {
            for (long pid : host.jasperPids().get()) host.terminate().test(pid);
        }
    }

    private Map<String, Object> cold(Path root, Path home, int index) throws IOException {
        Path traceFile = root.resolve("trace-" + index + ".json");
        long click = host.epochMillis().getAsLong();
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), false));
        Map<String, Object> trace = await(traceFile, document -> Boolean.TRUE.equals(document.get("settled")));
        return finish(index, trace, trace == null ? Map.of() : fromClick(trace, click, null));
    }

    private Map<String, Object> warm(Path root, Path home, int index) throws IOException {
        Path traceFile = root.resolve("trace-" + index + ".json");
        host.run().apply(openCommand(options.app(), launchOptions(traceFile, home), true));
        Map<String, Object> resident = await(traceFile, document -> Boolean.TRUE.equals(document.get("settled")));
        if (resident == null) return finish(index, null, Map.of());
        // Idle memory of the warm, windowless resident: what residency costs while it waits.
        try { Thread.sleep(2_000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        long idle = resident.get("pid") instanceof Number pid ? host.residentKiB().apply(pid.longValue()) : -1;
        long click = host.epochMillis().getAsLong();
        host.run().apply(reopenCommand(options.app()));
        Map<String, Object> trace = await(traceFile, document -> fromClick(document, click, "reopen").containsKey("OUTPUT_PAINTED"));
        Map<String, Object> result = finish(index, trace == null ? resident : trace,
            trace == null ? Map.of() : fromClick(trace, click, "reopen"));
        result.put("idleResidentKiB", idle);
        return result;
    }

    private Map<String, Object> finish(int index, Map<String, Object> trace, Map<String, Double> fromClick) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", index - options.prime() + 1);
        String outcome;
        if (trace == null) {
            outcome = "timeout";
        } else if (fromClick.containsKey("OUTPUT_PAINTED")) {
            outcome = "ok";
        } else {
            outcome = "incomplete";
        }
        if (trace != null) {
            result.put("kind", trace.get("kind"));
            result.put("jvm", trace.get("jvm"));
            result.put("phases", trace.get("phases"));
            if (trace.get("pid") instanceof Number pid && !host.terminate().test(pid.longValue()))
                outcome = "did-not-exit";
        } else {
            for (long pid : host.jasperPids().get()) host.terminate().test(pid);
        }
        result.put("outcome", outcome);
        result.put("fromClick", fromClick);
        return result;
    }

    private Map<String, Object> await(Path traceFile, Predicate<Map<String, Object>> ready) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.timeoutSeconds());
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(traceFile)) {
                try {
                    Map<String, Object> document = StartupTraceFile.read(traceFile);
                    if (ready.test(document)) return document;
                } catch (IOException | RuntimeException notYet) {
                    // A file mid-replacement or a document not ready yet: poll again.
                }
            }
            try { Thread.sleep(25); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    private static boolean stop(ProcessHandle process) {
        process.destroy();
        try {
            process.onExit().get(15, TimeUnit.SECONDS);
            return true;
        } catch (Exception notYet) {
            process.destroyForcibly();
            try {
                process.onExit().get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // Ignore the outcome; process did not exit cleanly
            }
            return false;
        }
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return (sorted.get((n - 1) / 2) + sorted.get(n / 2)) / 2;
    }

    private void warnAboutTerminalMultiplexers() {
        Path config = Path.of(System.getProperty("user.home"), ".config/jasper/config.toml");
        try {
            String text = Files.isRegularFile(config) ? Files.readString(config) : "";
            if (text.contains("tmux") || text.contains("screen"))
                System.err.println("Warning: the configured terminal program looks like tmux or screen; each launch leaves a "
                    + "session in its server. Remove those sessions after the run.");
        } catch (IOException unreadable) {
            System.err.println("Warning: could not read " + config + " to check for tmux or screen.");
        }
    }

    private void writeCompanion(Map<String, Object> report) throws IOException {
        StringBuilder out = new StringBuilder("# Jasper start-up benchmark\n\nStatus: " + report.get("status")
            + ". Mode: " + report.get("mode") + ". Revision: " + report.get("revision")
            + ". Milliseconds from the click (`open`), medians of the measured runs.\n\n| Milestone | Runs | Median | Min | Max |\n|---|---|---|---|---|\n");
        if (report.get("summary") instanceof Map<?, ?> summary)
            summary.forEach((name, stats) -> {
                Map<?, ?> values = (Map<?, ?>) stats;
                out.append("| ").append(name).append(" | ").append(values.get("count")).append(" | ")
                    .append(Math.round(((Number) values.get("median")).doubleValue())).append(" | ")
                    .append(Math.round(((Number) values.get("min")).doubleValue())).append(" | ")
                    .append(Math.round(((Number) values.get("max")).doubleValue())).append(" |\n");
            });
        Files.writeString(Path.of(options.output() + ".md"), out, StandardCharsets.UTF_8);
    }

}
