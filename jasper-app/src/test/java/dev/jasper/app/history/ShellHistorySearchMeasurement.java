package dev.jasper.app.history;

import dev.jasper.app.palette.PaletteContext;
import dev.jasper.app.palette.PaletteTarget;
import dev.jasper.app.palette.builtin.ShellHistoryScope;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Opt-in: substring ranking over a synthetic 50,000-entry snapshot. Records medians; sets no threshold. */
public final class ShellHistorySearchMeasurement {
    private static final int ENTRIES = 50_000;
    private static final int WARMUP = 200;
    private static final int SAMPLES = 1_000;
    private static volatile long blackhole;

    private ShellHistorySearchMeasurement() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Provide an output directory");
        Path output = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(output);
        var entries = new ArrayList<ShellHistorySnapshot.Source>();
        var list = new ArrayList<ShellHistoryEntry>(ENTRIES);
        for (int i = 0; i < ENTRIES; i++)
            list.add(ShellHistoryEntry.of(String.format(Locale.ROOT, "git commit -m \"change %05d in module %d\"", i, i % 40),
                1_600_000_000L + i, i % 2 == 0 ? "zsh" : "bash"));
        entries.add(new ShellHistorySnapshot.Source(list, 1_600_000_000L + ENTRIES));
        var snapshot = ShellHistorySnapshot.build(entries, List.of(), ShellHistorySnapshot.MAX_ENTRIES);
        var index = new ShellHistoryIndex(List.of(), new java.util.concurrent.AbstractExecutorService() {
            @Override public void execute(Runnable task) { task.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long t, java.util.concurrent.TimeUnit u) { return true; }
        }, Runnable::run);
        for (ShellHistoryEntry entry : snapshot.entries()) index.record(entry);
        var scope = new ShellHistoryScope(index, null);
        var context = new PaletteContext(true, new PaletteTarget(text -> {}, () -> {}, Optional::empty, () -> "zsh", () -> true));
        var report = new StringBuilder("# Shell history search measurement\n\nEntries: ").append(ENTRIES).append("\n\n| Query | Median µs |\n|---|---|\n");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            for (String query : List.of("", "git commit", "module 7", "change 04999", "no such text")) {
                long[] samples = new long[SAMPLES];
                for (int i = 0; i < WARMUP; i++) blackhole += scope.search(query, context).rows().size();
                for (int i = 0; i < SAMPLES; i++) {
                    long start = System.nanoTime();
                    blackhole += scope.search(query, context).rows().size();
                    samples[i] = System.nanoTime() - start;
                }
                java.util.Arrays.sort(samples);
                report.append("| `").append(query.isEmpty() ? "(empty)" : query).append("` | ")
                    .append(String.format(Locale.ROOT, "%.1f", samples[SAMPLES / 2] / 1_000.0)).append(" |\n");
            }
        });
        Path file = output.resolve("history-search-measurement.md");
        Files.writeString(file, report.toString(), StandardCharsets.UTF_8);
        System.out.print(report);
    }
}
