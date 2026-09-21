package dev.jasper.app.commands;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;

/** Opt-in measurement of the pure, pre-indexed command matcher. */
public final class CommandSearchMeasurement {
    private static final int CATALOG_SIZE = 1_000;
    private static final int WARMUP_CALLS = 5_000;
    private static final int SAMPLE_CALLS = 10_000;
    private static volatile long blackhole;

    private CommandSearchMeasurement() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        List<CommandSearch.Entry> catalog = catalog();
        List<Query> queries = List.of(
            new Query("exact", "Open Workspace 0042"),
            new Query("prefix", "Open Work"),
            new Query("fuzzy", "opwks0042"),
            new Query("zero-match", "no-such-command-omega"));

        var results = new ArrayList<Result>();
        for (Query query : queries) results.add(measure(catalog, query));
        String report = report(results, queries);
        Path file = output.resolve("search-measurement.md");
        Files.writeString(file, report, StandardCharsets.UTF_8);
        System.out.println(file);
        System.out.print(report);
    }

    private static List<CommandSearch.Entry> catalog() {
        var entries = new ArrayList<CommandSearch.Entry>(CATALOG_SIZE);
        for (int i = 0; i < CATALOG_SIZE; i++) {
            String title = String.format(Locale.ROOT, "Open Workspace %04d", i);
            var action = new AbstractAction(title) {
                @Override public void actionPerformed(ActionEvent event) {}
            };
            var command = new Command(String.format(Locale.ROOT, "measurement.command.%04d", i), action,
                List.of("workspace", "project " + i, "group " + (i % 25), "operation " + (i % 17)));
            entries.add(CommandSearch.entry(command));
        }
        return List.copyOf(entries);
    }

    private static Result measure(List<CommandSearch.Entry> catalog, Query query) {
        long checksum = blackhole;
        for (int i = 0; i < WARMUP_CALLS; i++) checksum = consume(checksum,
            CommandSearch.find(catalog, query.text, List.of(), 5));

        var allocation = allocationBean();
        long threadId = Thread.currentThread().threadId();
        long allocatedBefore = allocation == null ? -1 : allocation.getThreadAllocatedBytes(threadId);
        long[] samples = new long[SAMPLE_CALLS];
        int resultCount = -1;
        for (int i = 0; i < SAMPLE_CALLS; i++) {
            long started = System.nanoTime();
            List<Command> matches = CommandSearch.find(catalog, query.text, List.of(), 5);
            samples[i] = System.nanoTime() - started;
            resultCount = matches.size();
            checksum = consume(checksum, matches);
        }
        long allocatedAfter = allocation == null ? -1 : allocation.getThreadAllocatedBytes(threadId);
        blackhole = checksum;
        Arrays.sort(samples);
        long allocatedPerCall = allocatedBefore < 0 || allocatedAfter < allocatedBefore
            ? -1 : (allocatedAfter - allocatedBefore) / SAMPLE_CALLS;
        return new Result(query.name, query.text, resultCount, samples[SAMPLE_CALLS / 2],
            samples[(int) Math.ceil(SAMPLE_CALLS * 0.95) - 1], samples[SAMPLE_CALLS - 1], allocatedPerCall);
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean platform = ManagementFactory.getThreadMXBean();
        if (!(platform instanceof com.sun.management.ThreadMXBean bean)
            || !bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean.isThreadAllocatedMemoryEnabled() ? bean : null;
    }

    private static long consume(long checksum, List<Command> matches) {
        long next = checksum * 31 + matches.size();
        for (Command command : matches) next = next * 31 + command.id().hashCode();
        return next;
    }

    private static String report(List<Result> results, List<Query> queries) {
        StringBuilder text = new StringBuilder("# Command search measurement\n\n")
            .append("This opt-in measurement covers matching only. The 1,000 commands are normalized and indexed once ")
            .append("before warmup; it does not measure native key-to-paint latency. Workstation timing is descriptive, ")
            .append("with no CI threshold.\n\n")
            .append("- OS: ").append(property("os.name")).append(' ').append(property("os.version")).append('\n')
            .append("- CPU architecture: ").append(property("os.arch")).append('\n')
            .append("- Java: ").append(property("java.vendor")).append(' ').append(property("java.version")).append('\n')
            .append("- Commit: ").append(commit()).append('\n')
            .append("- Catalog: ").append(CATALOG_SIZE).append(" indexed commands\n")
            .append("- Query set: ");
        for (int i = 0; i < queries.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(queries.get(i).name).append(" (`").append(queries.get(i).text).append("`)");
        }
        text.append("\n- Warmup: ").append(WARMUP_CALLS).append(" calls per query\n")
            .append("- Samples: ").append(SAMPLE_CALLS).append(" calls per query\n")
            .append("- Checksum: `").append(blackhole).append("`\n\n")
            .append("| Query | Results | Median | p95 | Max | Allocated/call |\n")
            .append("|---|---:|---:|---:|---:|---:|\n");
        for (Result result : results) {
            text.append("| ").append(result.name).append(" | ").append(result.count)
                .append(" | ").append(nanos(result.median)).append(" | ").append(nanos(result.p95))
                .append(" | ").append(nanos(result.max)).append(" | ")
                .append(result.allocatedPerCall < 0 ? "unsupported" : result.allocatedPerCall + " B")
                .append(" |\n");
        }
        return text.toString();
    }

    private static String nanos(long value) {
        return String.format(Locale.ROOT, "%.3f µs", value / 1_000.0);
    }

    private static String property(String name) {
        return System.getProperty(name, "unknown").replace('\n', ' ');
    }

    private static String commit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--short=12", "HEAD")
                .redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.waitFor() == 0 && !value.isEmpty() ? "`" + value + "` (Task 6 working tree)" : "unavailable";
        } catch (IOException failure) {
            return "unavailable (" + failure.getClass().getSimpleName() + ")";
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return "unavailable (interrupted)";
        }
    }

    private record Query(String name, String text) {}
    private record Result(String name, String query, int count, long median, long p95, long max,
                          long allocatedPerCall) {}
}
