package dev.moray.app;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** All calls run off the EDT. Resident sizes are process measurements, never heap or package size. */
final class BenchmarkMetrics {
    private BenchmarkMetrics() {}
    static Map<String, Object> sample(List<Long> children) {
        long started = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", Instant.now().toString());
        var memory = ManagementFactory.getMemoryMXBean();
        result.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        result.put("heapCommittedBytes", memory.getHeapMemoryUsage().getCommitted());
        result.put("nonHeapUsedBytes", memory.getNonHeapMemoryUsage().getUsed());
        result.put("heapPoolPeakUsedBytes", ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP)
            .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum());
        result.put("gcCount", sumAvailable(ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> b.getCollectionCount()).toArray()));
        result.put("gcTimeMillis", sumAvailable(ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> b.getCollectionTime()).toArray()));
        Long allocated = null;
        var threads = ManagementFactory.getThreadMXBean();
        if (threads instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
            && bean.isThreadAllocatedMemoryEnabled()) {
            long bytes = bean.getTotalThreadAllocatedBytes(); if (bytes >= 0) allocated = bytes;
        }
        result.put("jvmAllocatedBytes", allocated);
        List<Long> pids = new ArrayList<>(); pids.add(ProcessHandle.current().pid()); pids.addAll(children);
        Map<Long, Long> resident = residentBytes(pids);
        result.put("pid", ProcessHandle.current().pid());
        result.put("residentBytes", resident.get(ProcessHandle.current().pid()));
        result.put("residentSource", residentSource());
        List<Map<String, Object>> childSamples = new ArrayList<>();
        for (Long pid : children) {
            Map<String, Object> child = new LinkedHashMap<>(); child.put("pid", pid); child.put("residentBytes", resident.get(pid));
            childSamples.add(child);
        }
        result.put("children", childSamples);
        result.put("collectionMillis", (System.nanoTime() - started) / 1e6);
        return result;
    }
    private static Long sumAvailable(long[] values) {
        return values.length == 0 || Arrays.stream(values).anyMatch(v -> v < 0) ? null : Arrays.stream(values).sum();
    }
    static String platform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.startsWith("mac") ? "darwin" : os.startsWith("windows") ? "windows" : os.startsWith("linux") ? "linux" : "unknown";
    }
    static String residentSource() {
        return switch (platform()) {
            case "darwin", "linux" -> "ps RSS (KiB converted to bytes)";
            case "windows" -> "PowerShell Get-Process WorkingSet64 (bytes)";
            default -> "unavailable";
        };
    }
    static Long parseResidentBytes(String platform, String text) {
        try {
            long value = Long.parseLong(text.strip());
            if (value < 0) return null;
            return switch (platform) {
                case "darwin", "linux" -> Math.multiplyExact(value, 1024);
                case "windows" -> value;
                default -> null;
            };
        } catch (IllegalArgumentException | ArithmeticException ignored) { return null; }
    }
    private static Map<Long, Long> residentBytes(List<Long> pids) {
        String ids = String.join(",", pids.stream().map(Object::toString).toList());
        List<String> command = switch (platform()) {
            case "darwin", "linux" -> List.of("ps", "-o", "pid=,rss=", "-p", ids);
            case "windows" -> List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                "Get-Process -Id " + ids + " -ErrorAction SilentlyContinue | ForEach-Object { '{0} {1}' -f $_.Id,$_.WorkingSet64 }");
            default -> List.of();
        };
        Map<Long, Long> result = new HashMap<>();
        if (command.isEmpty()) return result;
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) return result;
            String output = new String(process.getInputStream().readNBytes(8192), StandardCharsets.UTF_8);
            for (String line : output.lines().toList()) {
                String[] parts = line.strip().split("\\s+");
                if (parts.length == 2) result.put(Long.parseLong(parts[0]), parseResidentBytes(platform(), parts[1]));
            }
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally { if (process != null) { process.destroyForcibly(); try { process.getInputStream().close(); } catch (java.io.IOException ignored) {} } }
        return result;
    }
}
