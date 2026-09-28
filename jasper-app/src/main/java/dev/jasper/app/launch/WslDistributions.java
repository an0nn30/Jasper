package dev.jasper.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The WSL distributions installed for this user, read from the {@code Lxss} registry keys through {@code reg.exe}
 * (no native access in the app), with {@code wsl.exe -l -v} as the fallback when {@code reg.exe} gives no answer. Parsing is pure; only {@code run}
 * starts processes, and nothing runs off Windows. Docker Desktop's own distributions and ones that are not fully
 * installed are left out.
 */
public final class WslDistributions {
    private static final System.Logger LOG = System.getLogger(WslDistributions.class.getName());
    static final List<String> REGISTRY_QUERY = List.of("reg.exe", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Lxss", "/s");
    static final List<String> LIST_QUERY = List.of("wsl.exe", "-l", "-v");
    static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** WSL's own rule for distribution names; anything else never reaches a command line or a path. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern VALUE = Pattern.compile("^\\s+(\\S.*?)\\s{4}(REG_[A-Z_]+)(?:\\s{4}(.*))?$");
    private static final Pattern LIST_ROW = Pattern.compile("^\\s*(\\*)?\\s*(\\S+)\\s+.*\\s([12])\\s*$");

    private WslDistributions() { }

    /** The distributions on this machine; empty off Windows or without WSL. Blocks for up to 20 seconds: never call it on the EDT. */
    public static List<WslDistro> detect(String osName) {
        return detect(osName, command -> run(command, TIMEOUT, successes(command)));
    }

    /** As above with {@code runner} standing in for the process: a command in, its standard output (or nothing) out. */
    public static List<WslDistro> detect(String osName, Function<List<String>, Optional<byte[]>> runner) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("windows")) return List.of();
        // Distribution names follow NAME, which is ASCII, so the console code page does not matter.
        // An answer from the registry is final, even with no usable distribution (no Lxss key, or only Docker's):
        // wsl.exe is asked only when reg.exe could not run, failed or took too long.
        Optional<byte[]> registry = output(runner, REGISTRY_QUERY);
        if (registry.isPresent()) return parseRegistry(new String(registry.get(), StandardCharsets.ISO_8859_1));
        return output(runner, LIST_QUERY).map(WslDistributions::parseList).orElse(List.of());
    }

    private static Optional<byte[]> output(Function<List<String>, Optional<byte[]>> runner, List<String> command) {
        try {
            Optional<byte[]> result = runner.apply(command);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.DEBUG, "WSL detection could not run " + command.getFirst(), failure);
            return Optional.empty();
        }
    }

    /**
     * {@code reg.exe query ...\Lxss /s}: one key per distribution named by its GUID, holding {@code DistributionName},
     * {@code State} (1 when installed) and {@code Flags} (bit 0x8 is WSL 2; {@code Version} is the filesystem version,
     * not the WSL one). The parent key's {@code DefaultDistribution} names the default's GUID.
     */
    static List<WslDistro> parseRegistry(String output) {
        String defaultKey = null;
        boolean inParent = false;
        String current = null;
        Map<String, Map<String, String>> keys = new LinkedHashMap<>();
        for (String raw : output.split("\r?\n")) {
            String line = raw.stripTrailing();
            if (line.isBlank()) continue;
            if (line.startsWith("HKEY_")) {
                int lxss = line.toLowerCase(Locale.ROOT).lastIndexOf("\\lxss");
                String tail = lxss < 0 ? null : line.substring(lxss + "\\lxss".length());
                inParent = "".equals(tail);
                current = tail != null && tail.startsWith("\\{") && tail.endsWith("}") && tail.indexOf('\\', 1) < 0
                    ? tail.substring(1).toLowerCase(Locale.ROOT) : null;
                if (current != null) keys.put(current, new HashMap<>());
                continue;
            }
            Matcher value = VALUE.matcher(line);
            if (!value.matches()) continue;
            String data = value.group(3) == null ? "" : value.group(3).strip();
            if (inParent && value.group(1).equals("DefaultDistribution")) defaultKey = data.toLowerCase(Locale.ROOT);
            else if (current != null) keys.get(current).put(value.group(1), data);
        }
        var distros = new ArrayList<WslDistro>();
        for (var entry : keys.entrySet()) {
            Map<String, String> values = entry.getValue();
            String name = values.getOrDefault("DistributionName", "");
            if (!usable(name)) continue;
            String state = values.get("State");
            if (state != null && dword(state) != 1) continue;
            boolean vm = (dword(values.getOrDefault("Flags", "0x0")) & 0x8) != 0;
            distros.add(new WslDistro(name, vm ? 2 : 1, entry.getKey().equals(defaultKey)));
        }
        return sorted(distros);
    }

    /**
     * {@code wsl.exe -l -v}: a header, then one row per distribution, the default marked with {@code *}. It writes
     * UTF-16LE, or UTF-8 when {@code WSL_UTF8=1} is set.
     */
    static List<WslDistro> parseList(byte[] output) {
        boolean utf16 = output.length >= 2 && (output[1] == 0 || output[0] == (byte) 0xFF && output[1] == (byte) 0xFE);
        String text = new String(output, utf16 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        var distros = new ArrayList<WslDistro>();
        for (String raw : text.split("\r?\n")) {
            Matcher row = LIST_ROW.matcher(raw);
            if (row.matches() && usable(row.group(2)))
                distros.add(new WslDistro(row.group(2), Integer.parseInt(row.group(3)), row.group(1) != null));
        }
        return sorted(distros);
    }

    /**
     * Runs {@code command} with no input and returns its standard output when it exits with 0 within
     * {@code timeout}; empty when it cannot start, fails or takes too long (then it is killed). The system runner
     * accepts the exit codes in {@link #successes} instead.
     */
    static Optional<byte[]> run(List<String> command, Duration timeout) {
        return run(command, timeout, Set.of(0));
    }

    /**
     * The exit codes that count as an answer. reg.exe exits with 1 when the {@code Lxss} key does not exist, which
     * means no distribution was ever registered: that is "no WSL", not "could not ask", so it must not lead to
     * {@code wsl.exe}. (reg.exe also exits with 1 for other failures such as access denied; those are rare enough for
     * HKCU that the cost of treating them as "no WSL" is lower than starting wsl.exe on every machine without WSL.)
     */
    static Set<Integer> successes(List<String> command) {
        return command.equals(REGISTRY_QUERY) ? Set.of(0, 1) : Set.of(0);
    }

    /** As above, returning the output when the exit code is one of {@code successes}. */
    static Optional<byte[]> run(List<String> command, Duration timeout, Set<Integer> successes) {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException | RuntimeException unavailable) {
            return Optional.empty();
        }
        try {
            process.getOutputStream().close();
            // Read while waiting: a full pipe would otherwise stall the child until the timeout.
            var reading = new FutureTask<>(() -> process.getInputStream().readAllBytes());
            Thread.ofVirtual().name("jasper-wsl-detect").start(reading);
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            byte[] bytes = reading.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return successes.contains(process.exitValue()) ? Optional.of(bytes) : Optional.empty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return Optional.empty();
        } catch (IOException | ExecutionException | TimeoutException failure) {
            process.destroyForcibly();
            return Optional.empty();
        }
    }

    private static boolean usable(String name) {
        return NAME.matcher(name).matches() && !name.toLowerCase(Locale.ROOT).startsWith("docker-desktop");
    }

    private static long dword(String text) {
        try {
            return Long.decode(text.strip());
        } catch (NumberFormatException malformed) {
            return -1;
        }
    }

    private static List<WslDistro> sorted(List<WslDistro> distros) {
        distros.sort(Comparator.comparing(WslDistro::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(distros);
    }
}
