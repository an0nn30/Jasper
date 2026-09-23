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
 * IdentityFile; scalar first values win and identities accumulate, global lines and {@code Host *} supply defaults; one level of
 * Include. Wildcard Host patterns and Match blocks are skipped and listed.
 */
public final class SshConfig {
    public record Entry(String alias, Optional<String> hostname, OptionalInt port, Optional<String> user, Optional<String> proxyJump, List<String> identityFiles) { public Entry { identityFiles = List.copyOf(identityFiles); } }
    public record Parsed(List<Entry> entries, List<String> skipped) { }

    private static final class Block {
        final Map<String, String> values = new LinkedHashMap<>();
        final List<String> identities = new ArrayList<>();
    }
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
        var blocks = new ArrayList<Map.Entry<List<String>, Block>>();
        var skipped = new ArrayList<String>();
        var global = new Block();
        Block current = global;
        boolean skipping = false;
        for (String raw : expand(text, includes).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            List<String> tokens;
            try { tokens = tokens(line); }
            catch (IllegalArgumentException invalid) { skipped.add(line + " (invalid quoting)"); continue; }
            if (tokens.isEmpty()) continue;
            String key = tokens.getFirst().toLowerCase(Locale.ROOT), value = String.join(" ", tokens.subList(1, tokens.size()));
            if (key.equals("host")) {
                List<String> aliases = new ArrayList<>();
                for (String alias : tokens.subList(1, tokens.size())) {
                    if (alias.isEmpty()) continue;
                    if (alias.equals("*")) aliases.add(alias);
                    else if (alias.contains("*") || alias.contains("?") || alias.startsWith("!")) skipped.add("Host " + alias + " (pattern)");
                    else aliases.add(alias);
                }
                current = new Block();
                blocks.add(Map.entry(aliases, current));
                skipping = false;
            } else if (key.equals("match")) {
                skipped.add("Match " + value + " (Match block)");
                current = new Block();
                skipping = true;
            } else if (!skipping) {
                if (key.equals("identityfile")) { if (!value.equalsIgnoreCase("none")) current.identities.add(value); }
                else current.values.putIfAbsent(key, value);
            }
        }
        var entries = new ArrayList<Entry>();
        var aliases = new java.util.LinkedHashSet<String>();
        for (var block : blocks) for (String alias : block.getKey()) if (!alias.equals("*")) aliases.add(alias);
        for (String alias : aliases) {
            Map<String, String> values = new LinkedHashMap<>(global.values);
            var identities = new java.util.LinkedHashSet<>(global.identities);
            for (var block : blocks)
                if (block.getKey().stream().anyMatch(pattern -> pattern.equals("*") || pattern.equalsIgnoreCase(alias))) { block.getValue().values.forEach(values::putIfAbsent); identities.addAll(block.getValue().identities); }
            OptionalInt port = OptionalInt.empty();
            if (values.containsKey("port")) {
                try {
                    int number = Integer.parseInt(values.get("port"));
                    if (number < 1 || number > 65535) throw new NumberFormatException();
                    port = OptionalInt.of(number);
                } catch (NumberFormatException bad) { skipped.add("Host " + alias + " (bad Port)"); continue; }
            }
            entries.add(new Entry(alias, Optional.ofNullable(values.get("hostname")), port, Optional.ofNullable(values.get("user")),
                Optional.ofNullable(values.get("proxyjump")), List.copyOf(identities)));
        }
        return new Parsed(List.copyOf(entries), List.copyOf(skipped));
    }

    private static String expand(String text, Function<String, List<String>> includes) {
        var out = new StringBuilder();
        for (String raw : text.split("\n")) {
            List<String> parts;
            try { parts = tokens(raw.strip()); }
            catch (IllegalArgumentException invalid) { out.append(raw).append('\n'); continue; }
            if (!parts.isEmpty() && parts.getFirst().equalsIgnoreCase("include")) {
                for (String pattern : parts.subList(1, parts.size())) for (String included : includes.apply(pattern)) out.append(included).append('\n');
            } else out.append(raw).append('\n');
        }
        return out.toString();
    }
    private static List<String> tokens(String line) {
        var out = new ArrayList<String>();
        int at = 0;
        while (at < line.length() && !Character.isWhitespace(line.charAt(at)) && line.charAt(at) != '=') at++;
        if (at == 0 || line.charAt(0) == '#') return out;
        out.add(line.substring(0, at));
        while (at < line.length() && (Character.isWhitespace(line.charAt(at)) || line.charAt(at) == '=')) at++;
        var token = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '\\' && at + 1 < line.length() && (line.charAt(at + 1) == '\\' || line.charAt(at + 1) == '"' || line.charAt(at + 1) == '\'' || Character.isWhitespace(line.charAt(at + 1)))) { token.append(line.charAt(++at)); started = true; }
            else if (quote != 0) { if (c == quote) quote = 0; else token.append(c); }
            else if (c == '"' || c == '\'') { quote = c; started = true; }
            else if (c == '#' && !started) break;
            else if (Character.isWhitespace(c)) { if (started) { out.add(token.toString()); token.setLength(0); started = false; } }
            else { token.append(c); started = true; }
        }
        if (quote != 0) throw new IllegalArgumentException("Unclosed quote");
        if (started) out.add(token.toString());
        return out;
    }

}
