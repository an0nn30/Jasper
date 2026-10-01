package dev.jasper.remote.tunnels;

import dev.jasper.remote.hosts.HostFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The tunnels.toml format: an array of [[tunnel]] tables. Lenient per entry, strict per file. */
public final class TunnelFile {
    public static final String HEADER = "# Jasper Remote tunnels. Edit freely; Jasper rewrites this file when you save from the panel.\n";
    public static final int MAX_BYTES = 1024 * 1024;

    /** The good tunnels, and one line for each entry that was skipped. */
    public record Parsed(List<Tunnel> tunnels, List<String> warnings) { }

    private TunnelFile() { }

    /** Throws, with the TOML parser's first error as the message, when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException(toml.errors().getFirst().toString());
        Object value = toml.get("tunnel");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'tunnel' must be an array of tables"));
        var tunnels = new ArrayList<Tunnel>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "tunnel " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                Tunnel tunnel = tunnel(table);
                if (!names.add(tunnel.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + tunnel.name()); continue; }
                if (!ids.add(tunnel.id())) { warnings.add(where + "duplicate id " + tunnel.id()); continue; }
                tunnels.add(tunnel);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(tunnels), List.copyOf(warnings));
    }

    private static Tunnel tunnel(TomlTable table) {
        String name = text(table, "name").orElse("");
        // A missing id is derived from the name (names are unique per file), so the entry keeps one identity across reads.
        UUID id = text(table, "id").map(TunnelFile::uuid).orElseGet(() -> derivedId(name));
        if (name.isBlank()) throw new IllegalArgumentException("a tunnel needs a name");
        UUID host = uuid(text(table, "host").orElseThrow(() -> new IllegalArgumentException("'host' is required")));
        TunnelKind kind = switch (text(table, "kind").orElse("local")) {
            case "local" -> TunnelKind.LOCAL;
            case "remote" -> TunnelKind.REMOTE;
            case "dynamic" -> TunnelKind.DYNAMIC;
            default -> throw new IllegalArgumentException("'kind' must be \"local\", \"remote\" or \"dynamic\"");
        };
        TunnelStart start = switch (text(table, "start").orElse("manual")) {
            case "manual" -> TunnelStart.MANUAL;
            case "launch" -> TunnelStart.LAUNCH;
            default -> throw new IllegalArgumentException("'start' must be \"manual\" or \"launch\"");
        };
        int bindPort = port(table, "bind_port").orElseThrow(() -> new IllegalArgumentException("'bind_port' is required"));
        int targetPort = kind == TunnelKind.DYNAMIC ? 0
            : port(table, "target_port").orElseThrow(() -> new IllegalArgumentException("'target_port' is required"));
        Instant created = instant(table, "created").orElseGet(Instant::now);
        return new Tunnel(id, name, host, kind, text(table, "bind_address").orElse(Tunnel.LOCALHOST), bindPort,
            text(table, "target_host").orElse(""), targetPort, start, created, instant(table, "updated").orElse(created));
    }

    private static UUID derivedId(String name) {
        return UUID.nameUUIDFromBytes(("dev.jasper.remote.tunnel:" + name.strip().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Integer> port(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Long number) || number < 0 || number > 65535) throw new IllegalArgumentException("'" + key + "' must be a port number");
        return Optional.of(number.intValue());
    }

    private static Optional<String> text(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Instant> instant(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof OffsetDateTime time) return Optional.of(time.toInstant());
        if (value instanceof String text) {
            try { return Optional.of(Instant.parse(text)); }
            catch (RuntimeException bad) { throw new IllegalArgumentException("'" + key + "' is not a timestamp"); }
        }
        throw new IllegalArgumentException("'" + key + "' is not a timestamp");
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }

    /** Rejects a blank name, duplicate names (case-insensitive) and duplicate ids: every saved tunnel is named. */
    public static void validate(List<Tunnel> tunnels) {
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (Tunnel tunnel : tunnels) {
            if (tunnel.name().isBlank()) throw new IllegalArgumentException("A saved tunnel needs a name");
            if (!names.add(tunnel.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A tunnel named " + tunnel.name() + " exists");
            if (!ids.add(tunnel.id())) throw new IllegalArgumentException("Duplicate tunnel id");
        }
    }

    public static String format(List<Tunnel> tunnels) {
        var out = new StringBuilder(HEADER);
        for (Tunnel tunnel : tunnels) {
            out.append("\n[[tunnel]]\n");
            out.append("id = \"").append(tunnel.id()).append("\"\n");
            out.append("name = ").append(HostFile.tomlString(tunnel.name())).append('\n');
            out.append("host = \"").append(tunnel.hostId()).append("\"\n");
            out.append("kind = \"").append(tunnel.kind().key()).append("\"\n");
            out.append("bind_address = ").append(HostFile.tomlString(tunnel.bindAddress())).append('\n');
            out.append("bind_port = ").append(tunnel.bindPort()).append('\n');
            if (tunnel.kind() != TunnelKind.DYNAMIC) {
                out.append("target_host = ").append(HostFile.tomlString(tunnel.targetHost())).append('\n');
                out.append("target_port = ").append(tunnel.targetPort()).append('\n');
            }
            out.append("start = \"").append(tunnel.start().key()).append("\"\n");
            out.append("created = ").append(DateTimeFormatter.ISO_INSTANT.format(tunnel.created())).append('\n');
            out.append("updated = ").append(DateTimeFormatter.ISO_INSTANT.format(tunnel.updated())).append('\n');
        }
        return out.toString();
    }
}
