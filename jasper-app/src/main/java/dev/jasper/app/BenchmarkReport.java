package dev.jasper.app;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

final class BenchmarkReport {
    private BenchmarkReport() {}
    static Map<String, Object> summary(List<Double> input) {
        List<Double> values = input.stream().filter(Objects::nonNull).sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        int n = values.size();
        result.put("count", n);
        result.put("median", n == 0 ? null : (values.get((n - 1) / 2) + values.get(n / 2)) / 2);
        result.put("max", n == 0 ? null : values.get(n - 1));
        result.put("p95", n == 0 ? null : values.get((int) Math.ceil(n * .95) - 1));
        return result;
    }
    static void write(Path file, Map<String, Object> result) throws IOException {
        // Publish the JSON checkpoint last; a companion error cannot leave a new successful JSON behind.
        writeCompanion(file, result);
        writeJson(file, result);
    }
    static void writeJson(Path file, Map<String, Object> result) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), "benchmark-report-", ".tmp");
        try {
            Files.writeString(temporary, json(result) + "\n");
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    static void writeCompanion(Path file, Map<String, Object> result) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        StringBuilder human = new StringBuilder("# Jasper benchmark\n\nStatus: " + result.get("status")
            + "\n\nSizes are bytes, rates use decimal MB/s, timing uses seconds or explicitly named milliseconds. "
            + "Unavailable metrics are null. No forced GC is performed.\n\n");
        if (result.get("runs") instanceof List<?> runs) {
            human.append("| Repeat | Panes | Scrollback | Status | Stream MB/s |\n|---|---|---|---|---|\n");
            for (Object value : runs) if (value instanceof Map<?, ?> run)
                human.append("| ").append(run.get("repeat")).append(" | ").append(run.get("panes"))
                    .append(" | ").append(run.get("scrollbackPerPane")).append(" | ").append(run.get("status"))
                    .append(" | ").append(run.get("streamMBps")).append(" |\n");
        }
        human.append("\nThe adjacent JSON contains environment, exact options, phase samples, child PIDs and rendering evidence.\n");
        Files.writeString(Path.of(file + ".md"), human);
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number n) return Double.isFinite(n.doubleValue()) ? n.toString() : "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return "{" + String.join(",", map.entrySet().stream()
            .map(e -> json(e.getKey().toString()) + ":" + json(e.getValue())).toList()) + "}";
        if (value instanceof Collection<?> list) return "[" + String.join(",", list.stream().map(BenchmarkReport::json).toList()) + "]";
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) switch (ch) {
            case '\"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (ch < 32) out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch)); else out.append(ch); }
        }
        return out.append('\"').toString();
    }
}
