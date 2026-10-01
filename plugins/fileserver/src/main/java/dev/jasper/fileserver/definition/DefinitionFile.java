package dev.jasper.fileserver.definition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
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

/** The servers.toml format: an array of [[server]] tables. Lenient per entry, strict per file. */
public final class DefinitionFile {
    public static final String HEADER = "# Jasper File Server: saved servers. Jasper rewrites this file when you save from the File Servers panel.\n";
    public static final int MAX_BYTES = 1024 * 1024;

    public record Parsed(List<ServerDefinition> definitions, List<String> warnings) { }

    private DefinitionFile() { }

    /** Throws when the text is not valid TOML; skips and reports individual bad entries. */
    public static Parsed parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("servers.toml has TOML errors: " + toml.errors().getFirst());
        Object value = toml.get("server");
        if (value == null) return new Parsed(List.of(), List.of());
        if (!(value instanceof TomlArray array)) return new Parsed(List.of(), List.of("'server' must be an array of tables"));
        var definitions = new ArrayList<ServerDefinition>();
        var warnings = new ArrayList<String>();
        var names = new HashSet<String>();
        var ids = new HashSet<UUID>();
        for (int i = 0; i < array.size(); i++) {
            String where = "server " + (i + 1) + ": ";
            if (!(array.get(i) instanceof TomlTable table)) { warnings.add(where + "not a table"); continue; }
            try {
                ServerDefinition definition = definition(table);
                if (!names.add(definition.name().toLowerCase(Locale.ROOT))) { warnings.add(where + "duplicate name " + definition.name()); continue; }
                if (!ids.add(definition.id())) { warnings.add(where + "duplicate id " + definition.id()); continue; }
                definitions.add(definition);
            } catch (IllegalArgumentException invalid) {
                warnings.add(where + invalid.getMessage());
            }
        }
        return new Parsed(List.copyOf(definitions), List.copyOf(warnings));
    }

    private static ServerDefinition definition(TomlTable table) {
        // A missing id is derived from the name (names are unique per file), so the entry keeps one identity across reads.
        UUID id = string(table, "id").map(DefinitionFile::uuid).orElseGet(() -> derivedId(string(table, "name").orElse("")));
        Protocol protocol = Protocol.parse(string(table, "protocol").orElseThrow(() -> new IllegalArgumentException("'protocol' is required")));
        String folder = string(table, "folder").orElseThrow(() -> new IllegalArgumentException("'folder' is required"));
        Path path;
        try { path = Path.of(folder); } catch (InvalidPathException bad) { throw new IllegalArgumentException("'folder' is not a path"); }
        long port = integer(table, "port").orElse((long) protocol.defaultPort());
        if (port < 1 || port > 65535) throw new IllegalArgumentException("'port' must be 1 to 65535");
        Login login = string(table, "login").map(Login::parse).orElse(protocol.allowsAnonymous() ? Login.ANONYMOUS : Login.SESSION);
        return new ServerDefinition(id, string(table, "name").orElse(""), protocol, path,
            string(table, "bind").map(Bind::parse).orElse(Bind.LOOPBACK), (int) port,
            string(table, "passive_ports").map(PortRange::parse), bool(table, "writable"), login,
            strings(table, "authorized_keys"), bool(table, "tls"), bool(table, "require_tls"), bool(table, "autostart"));
    }

    private static UUID derivedId(String name) {
        return UUID.nameUUIDFromBytes(("dev.jasper.fileserver:" + name.strip().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    public static String format(List<ServerDefinition> definitions) {
        var out = new StringBuilder(HEADER);
        for (ServerDefinition d : definitions) {
            out.append("\n[[server]]\n");
            out.append("id = \"").append(d.id()).append("\"\n");
            out.append("name = ").append(tomlString(d.name())).append('\n');
            out.append("protocol = \"").append(d.protocol().token()).append("\"\n");
            out.append("folder = ").append(tomlString(d.folder().toString())).append('\n');
            out.append("bind = ").append(tomlString(d.bind().token())).append('\n');
            out.append("port = ").append(d.port()).append('\n');
            d.passiveRange().ifPresent(range -> out.append("passive_ports = \"").append(range.token()).append("\"\n"));
            out.append("writable = ").append(d.writable()).append('\n');
            out.append("login = \"").append(d.login().token()).append("\"\n");
            if (!d.authorizedKeys().isEmpty())
                out.append("authorized_keys = [").append(String.join(", ", d.authorizedKeys().stream().map(DefinitionFile::tomlString).toList())).append("]\n");
            if (d.tls()) out.append("tls = true\n");
            if (d.requireTls()) out.append("require_tls = true\n");
            out.append("autostart = ").append(d.autostart()).append('\n');
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

    private static Optional<String> string(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a string");
        return Optional.of(text);
    }

    private static Optional<Long> integer(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return Optional.empty();
        if (!(value instanceof Long number)) throw new IllegalArgumentException("'" + key + "' must be a whole number");
        return Optional.of(number);
    }

    private static boolean bool(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return false;
        if (!(value instanceof Boolean flag)) throw new IllegalArgumentException("'" + key + "' must be true or false");
        return flag;
    }

    private static List<String> strings(TomlTable table, String key) {
        Object value = table.get(key);
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new IllegalArgumentException("'" + key + "' must be a list of strings");
        var result = new ArrayList<String>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String text)) throw new IllegalArgumentException("'" + key + "' must be a list of strings");
            result.add(text);
        }
        return result;
    }

    private static UUID uuid(String text) {
        try { return UUID.fromString(text); } catch (IllegalArgumentException bad) { throw new IllegalArgumentException("'" + text + "' is not an id"); }
    }
}
