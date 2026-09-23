package dev.jasper.remote.hosts;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A small reader of OpenSSH client configuration: Host blocks with HostName, Port, User, ProxyJump and
 * IdentityFile; the first value wins per key, global lines and {@code Host *} supply defaults; one level of
 * Include. Wildcard Host patterns and Match blocks are skipped and listed.
 */
public final class SshConfig {
    public record Entry(String alias, Optional<String> hostname, OptionalInt port, Optional<String> user, Optional<String> proxyJump, Optional<String> identityFile) { }
    public record Parsed(List<Entry> entries, List<String> skipped) { }

    private SshConfig() { }

    /** Reads {@code config}; a missing file is empty. Includes resolve relative to the file's directory. */
    public static Parsed parse(Path config) throws IOException {
        if (!Files.isRegularFile(config)) return new Parsed(List.of(), List.of());
        Path base = config.toAbsolutePath().getParent();
        return parse(Files.readString(config), include -> {
            Path pattern = Path.of(include);
            Path root = pattern.isAbsolute() ? pattern.getParent() : base.resolve(pattern).getParent();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + (pattern.isAbsolute() ? pattern : base.resolve(pattern)));
            var texts = new ArrayList<String>();
            if (root == null || !Files.isDirectory(root)) return texts;
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : files.sorted().toList())
                    if (matcher.matches(file) && Files.isRegularFile(file)) texts.add(Files.readString(file));
            } catch (IOException unreadable) { return texts; }
            return texts;
        });
    }

    public static Parsed parse(String text, Function<String, List<String>> includes) {
        var blocks = new ArrayList<Map.Entry<List<String>, Map<String, String>>>();
        var skipped = new ArrayList<String>();
        var global = new LinkedHashMap<String, String>();
        Map<String, String> current = global;
        boolean skipping = false;
        for (String raw : expand(text, includes).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] kv = line.split("[\\s=]+", 2);
            String key = kv[0].toLowerCase(Locale.ROOT), value = kv.length > 1 ? kv[1].strip() : "";
            if (key.equals("host")) {
                List<String> aliases = new ArrayList<>();
                for (String alias : value.split("\\s+")) {
                    if (alias.isEmpty()) continue;
                    if (alias.equals("*")) aliases.add(alias);
                    else if (alias.contains("*") || alias.contains("?") || alias.startsWith("!")) skipped.add("Host " + alias + " (pattern)");
                    else aliases.add(alias);
                }
                current = new LinkedHashMap<>();
                blocks.add(Map.entry(aliases, current));
                skipping = false;
            } else if (key.equals("match")) {
                skipped.add("Match " + value + " (Match block)");
                current = new LinkedHashMap<>();
                skipping = true;
            } else if (!skipping) current.putIfAbsent(key, value);
        }
        var entries = new ArrayList<Entry>();
        var wildcard = new LinkedHashMap<String, String>();
        for (var block : blocks) if (block.getKey().contains("*")) wildcard.putAll(block.getValue());
        for (var block : blocks) {
            for (String alias : block.getKey()) {
                if (alias.equals("*")) continue;
                Map<String, String> values = new LinkedHashMap<>(block.getValue());
                global.forEach(values::putIfAbsent);
                wildcard.forEach(values::putIfAbsent);
                OptionalInt port = OptionalInt.empty();
                if (values.containsKey("port")) { try { port = OptionalInt.of(Integer.parseInt(values.get("port"))); } catch (NumberFormatException bad) { skipped.add("Host " + alias + " (bad Port)"); continue; } }
                entries.add(new Entry(alias, Optional.ofNullable(values.get("hostname")), port, Optional.ofNullable(values.get("user")),
                    Optional.ofNullable(values.get("proxyjump")), Optional.ofNullable(values.get("identityfile"))));
            }
        }
        return new Parsed(List.copyOf(entries), List.copyOf(skipped));
    }

    private static String expand(String text, Function<String, List<String>> includes) {
        var out = new StringBuilder();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.toLowerCase(Locale.ROOT).startsWith("include ")) {
                for (String pattern : line.substring(8).strip().split("\\s+")) for (String included : includes.apply(pattern)) out.append(included).append('\n');
            } else out.append(raw).append('\n');
        }
        return out.toString();
    }
}
