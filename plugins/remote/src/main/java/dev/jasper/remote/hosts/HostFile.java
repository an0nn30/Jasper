package dev.jasper.remote.hosts;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/** The hosts.toml format: an array of [[host]] tables. Lenient per entry, strict per file. */
public final class HostFile {
    public static final String HEADER = "# Jasper Remote hosts. Edit freely; Jasper rewrites this file when you save from the panel.\n";
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    public record Parsed(List<RemoteHost> hosts, List<String> warnings) { }

    private HostFile() { }

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("hosts.toml has TOML errors: " + toml.errors().getFirst().toString());
        Object value = toml.get("host");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'host' must be an array of tables"));
        var hosts = new ArrayList<RemoteHost>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "host " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                RemoteHost host = host(table);
                if (!names.add(host.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + host.name()); continue; }
                if (!ids.add(host.id())) { warnings.add(where + "duplicate id " + host.id()); continue; }
                hosts.add(host);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(hosts), List.copyOf(warnings));
    }

    private static RemoteHost host(TomlTable table) {
        UUID id = optional(table, "id").map(HostFile::uuid).orElseGet(UUID::randomUUID);
        String auth = optional(table, "auth").orElse("agent");
        if (table.get("credential") != null && table.get("credentials") != null) throw new IllegalArgumentException("Choose one credential form");
        Auth resolved = switch (auth) {
            case "agent" -> Auth.AGENT;
            case "vault-keys" -> new Auth.VaultKeys(keyIds(table));
            case "vault" -> new Auth.Vault(uuid(optional(table, "credential").orElseThrow(() -> new IllegalArgumentException("'credential' is required for auth = \"vault\""))));
            default -> throw new IllegalArgumentException("'auth' must be \"vault\", \"vault-keys\" or \"agent\"");
        };
        long port = table.get("port") instanceof Long value ? value : 22;
        if (port < 1 || port > 65535) throw new IllegalArgumentException("'port' must be 1 to 65535");
        Instant created = instant(table, "created").orElseGet(Instant::now);
        Object follow = table.get("follow_directory");
        if (follow != null && !(follow instanceof Boolean)) throw new IllegalArgumentException("'follow_directory' must be true or false");
        return new RemoteHost(id, optional(table, "name").orElse(""), optional(table, "hostname").orElse(""), (int) port, optional(table, "username").orElse(""),
            resolved, optional(table, "group").orElse(""), table.get("favorite") instanceof Boolean favorite && favorite,
            optional(table, "jump").map(HostFile::uuid), created, instant(table, "updated").orElse(created), !Boolean.FALSE.equals(follow));
    }

    private static List<UUID> keyIds(TomlTable table) {
        if (!(table.get("credentials") instanceof TomlArray array)) throw new IllegalArgumentException("Vault keys need a credentials array");
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IllegalArgumentException("A credential ID must be a string");
            ids.add(uuid(text));
        }
        return ids;
    }

    private static Optional<String> optional(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Instant> instant(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (value instanceof OffsetDateTime time) return Optional.of(time.toInstant());
        if (value instanceof String text) { try { return Optional.of(Instant.parse(text)); } catch (RuntimeException bad) { throw new IllegalArgumentException("'" + key + "' is not a timestamp"); } }
        throw new IllegalArgumentException("'" + key + "' is not a timestamp");
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); } catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }

    /** Rejects duplicate names (case-insensitive), dangling jump references and jump cycles. */
    public static void validate(List<RemoteHost> hosts) {
        Map<UUID, RemoteHost> byId = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (RemoteHost host : hosts) {
            if (!names.add(host.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A host named " + host.name() + " exists");
            if (byId.put(host.id(), host) != null) throw new IllegalArgumentException("Duplicate host id");
        }
        for (RemoteHost host : hosts) {
            Set<UUID> seen = new HashSet<>();
            RemoteHost current = host;
            while (current.jump().isPresent()) {
                if (!seen.add(current.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
                current = byId.get(current.jump().get());
                if (current == null) throw new IllegalArgumentException("jump host missing for " + host.name());
            }
            if (current != host && seen.contains(current.id())) throw new IllegalArgumentException("Jump hosts form a cycle at " + host.name());
        }
    }

    public static String format(List<RemoteHost> hosts) {
        var out = new StringBuilder(HEADER);
        for (RemoteHost host : hosts) {
            out.append("\n[[host]]\n");
            out.append("id = \"").append(host.id()).append("\"\n");
            out.append("name = ").append(tomlString(host.name())).append('\n');
            out.append("hostname = ").append(tomlString(host.hostname())).append('\n');
            out.append("port = ").append(host.port()).append('\n');
            out.append("username = ").append(tomlString(host.username())).append('\n');
            switch (host.auth()) {
                case Auth.VaultKeys keys -> out.append("auth = \"vault-keys\"\ncredentials = [")
                    .append(String.join(", ", keys.credentialIds().stream().map(id -> "\"" + id + "\"").toList())).append("]\n");
                case Auth.Agent agent -> out.append("auth = \"agent\"\n");
                case Auth.Vault vault -> out.append("auth = \"vault\"\ncredential = \"").append(vault.credentialId()).append("\"\n");
            }
            if (!host.group().isEmpty()) out.append("group = ").append(tomlString(host.group())).append('\n');
            out.append("favorite = ").append(host.favorite()).append('\n');
            host.jump().ifPresent(jump -> out.append("jump = \"").append(jump).append("\"\n"));
            if (!host.followDirectory()) out.append("follow_directory = false\n");
            out.append("created = ").append(DateTimeFormatter.ISO_INSTANT.format(host.created())).append('\n');
            out.append("updated = ").append(DateTimeFormatter.ISO_INSTANT.format(host.updated())).append('\n');
        }
        return out.toString();
    }

    /** A TOML basic string: backslash, quote, tab, newlines and other control characters escaped. */
    public static String tomlString(String text) {
        var out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20 || c == 0x7f) out.append(String.format(Locale.ROOT, "\\u%04X", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
