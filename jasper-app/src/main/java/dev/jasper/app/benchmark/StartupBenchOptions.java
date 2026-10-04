package dev.jasper.app.benchmark;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded inputs of the opt-in start-up benchmark. */
record StartupBenchOptions(Path app, Path output, String revision, String mode, int runs, int prime, boolean realHome,
                           int timeoutSeconds) {
    static StartupBenchOptions parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        boolean realHome = false;
        Set<String> keys = Set.of("app", "output", "revision", "mode", "runs", "prime", "timeout-seconds");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--real-home")) { realHome = true; continue; }
            if (!args[i].startsWith("--") || !keys.contains(args[i].substring(2)) || i + 1 == args.length
                || args[i + 1].startsWith("--") || values.putIfAbsent(args[i].substring(2), args[i + 1]) != null)
                throw new IllegalArgumentException("Expected unique --option value pairs; invalid option: " + args[i]);
            i++;
        }
        String app = values.get("app"), output = values.get("output"), revision = values.get("revision");
        if (app == null || output == null || revision == null || revision.isBlank())
            throw new IllegalArgumentException("--app PATH, --output PATH and --revision LABEL are required");
        String mode = values.getOrDefault("mode", "cold");
        if (!Set.of("cold", "warm").contains(mode)) throw new IllegalArgumentException("--mode must be cold or warm");
        if (realHome && mode.equals("warm"))
            throw new IllegalArgumentException("Warm mode runs in an isolated home, where it can turn residency on");
        return new StartupBenchOptions(Path.of(app).toAbsolutePath(), Path.of(output).toAbsolutePath(), revision, mode,
            number(values, "runs", 10, 1, 50), number(values, "prime", 1, 0, 5), realHome,
            number(values, "timeout-seconds", 45, 5, 300));
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        int result = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        if (result < min || result > max) throw new IllegalArgumentException("--" + key + " must be " + min + ".." + max);
        return result;
    }
}
