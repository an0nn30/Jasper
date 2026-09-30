package dev.jasper.app.themestore;

import dev.jasper.app.settings.TomlLiterals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Where each Marketplace install came from: {@code .sources.toml} in the themes folder, one table per installed file
 * name. A file without an entry was installed from disk. Callers serialize access (ThemeInstaller is synchronized).
 */
final class Provenance {
    /** A Marketplace install: the plugin's numeric id, its plugin.xml id, its name and the installed version. */
    record Entry(int marketplaceId, String xmlId, String name, String version) { }

    static final String FILE = ".sources.toml";
    private final Path file;

    Provenance(Path themes) { file = themes.resolve(FILE); }

    /** Every entry whose file still exists, by file name; empty when the file is missing or unreadable. */
    Map<String, Entry> read() {
        if (!Files.isRegularFile(file)) return Map.of();
        TomlParseResult toml;
        try { toml = Toml.parse(file); } catch (IOException | RuntimeException unreadable) { return Map.of(); }
        if (toml.hasErrors()) return Map.of();
        var entries = new TreeMap<String, Entry>();
        for (String name : toml.keySet()) {
            TomlTable table = toml.getTable(List.of(name));
            if (table == null) continue;
            Long id = table.getLong("marketplace_id");
            String xmlId = table.getString("xml_id");
            String plugin = table.getString("name");
            String version = table.getString("version");
            if (id == null || xmlId == null || version == null) continue;
            // Only a plain file name in the themes folder: no separator of any platform and no parent step.
            if (name.startsWith(".") || name.contains("/") || name.contains("\\") || name.contains("..")
                || !Files.isRegularFile(file.resolveSibling(name))) continue;
            entries.put(name, new Entry(id.intValue(), xmlId, plugin == null ? xmlId : plugin, version));
        }
        return entries;
    }

    /** Replaces the file with {@code entries}, leaving out any whose file is gone. */
    void write(Map<String, Entry> entries) throws IOException {
        var text = new StringBuilder("# Where Jasper installed each theme plugin from. Settings > Themes keeps this file.\n");
        for (var named : new TreeMap<>(entries).entrySet()) {
            if (!Files.isRegularFile(file.resolveSibling(named.getKey()))) continue;
            Entry entry = named.getValue();
            text.append('\n').append('[').append(TomlLiterals.key(named.getKey())).append("]\n")
                .append("marketplace_id = ").append(entry.marketplaceId()).append('\n')
                .append("xml_id = ").append(TomlLiterals.string(entry.xmlId())).append('\n')
                .append("name = ").append(TomlLiterals.string(entry.name())).append('\n')
                .append("version = ").append(TomlLiterals.string(entry.version())).append('\n');
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(FILE + ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
