package dev.jasper.app.benchmark;

import java.nio.file.Path;
import java.util.*;

/** Explicit bounded inputs shared by the two opt-in entry points. */
record BenchmarkOptions(boolean memory, Path output, String revision, long bytes, List<Integer> panes,
                        List<Integer> scrollbacks, List<String> scenarios, int repeats, int warmupMillis,
                        int settleMillis, int sampleMillis, int cycles, int timeoutSeconds, int maxSeconds) {
    static BenchmarkOptions parse(boolean memory, String[] args) {
        Map<String, String> values = new HashMap<>();
        Set<String> keys = Set.of("output", "revision", "bytes", "panes", "scrollback", "scenario", "repeat",
            "warmup-ms", "settle-ms", "sample-ms", "cycles", "timeout-seconds", "max-seconds");
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || !keys.contains(args[i].substring(2)) || i + 1 == args.length
                || args[i + 1].startsWith("--") || values.putIfAbsent(args[i].substring(2), args[i + 1]) != null)
                throw new IllegalArgumentException("Expected unique --option value pairs; invalid option: " + args[i]);
        }
        String output = values.get("output"), revision = values.get("revision");
        if (output == null || output.isBlank() || revision == null || revision.isBlank())
            throw new IllegalArgumentException("--output PATH and --revision LABEL are required");
        List<String> scenarios = List.of(values.getOrDefault("scenario", memory ? "idle,output,cycles,interactive" : "output").split(",", -1));
        if (scenarios.isEmpty() || !Set.of("idle", "output", "cycles", "interactive").containsAll(scenarios)
            || new HashSet<>(scenarios).size() != scenarios.size()) throw new IllegalArgumentException("Invalid --scenario");
        List<Integer> panes = choices(values.getOrDefault("panes", memory ? "1,4,8" : "1"), Set.of(1, 4, 8));
        if (!memory && (!panes.equals(List.of(1)) || !scenarios.equals(List.of("output"))))
            throw new IllegalArgumentException("Throughput uses one pane and the output scenario");
        return new BenchmarkOptions(memory, Path.of(output).toAbsolutePath(), revision,
            number(values, "bytes", 100L * 1024 * 1024, 1, 100L * 1024 * 1024), panes,
            choices(values.getOrDefault("scrollback", memory ? "0,10000,100000" : "10000"), Set.of(0, 10000, 100000)),
            scenarios, (int) number(values, "repeat", 1, 1, 10), (int) number(values, "warmup-ms", 2000, 0, 10000),
            (int) number(values, "settle-ms", 2000, 0, 10000), (int) number(values, "sample-ms", 250, 100, 5000),
            (int) number(values, "cycles", 5, 1, 20), (int) number(values, "timeout-seconds", 120, 1, 600),
            (int) number(values, "max-seconds", 900, 1, 3600));
    }

    private static List<Integer> choices(String text, Set<Integer> allowed) {
        List<Integer> result = Arrays.stream(text.split(",", -1)).map(Integer::valueOf).toList();
        if (result.isEmpty() || !allowed.containsAll(result) || new HashSet<>(result).size() != result.size())
            throw new IllegalArgumentException("Unsupported or repeated matrix value: " + text);
        return result;
    }
    private static long number(Map<String, String> values, String key, long fallback, long min, long max) {
        long result = Long.parseLong(values.getOrDefault(key, Long.toString(fallback)));
        if (result < min || result > max) throw new IllegalArgumentException("--" + key + " must be " + min + ".." + max);
        return result;
    }
}
