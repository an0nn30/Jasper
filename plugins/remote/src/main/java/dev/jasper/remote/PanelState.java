package dev.jasper.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

/** Which groups the hosts panel shows collapsed, in {@code data/panel-state.toml}; a broken file means "all expanded". */
final class PanelState {
    private final Path file;

    PanelState(Path file) { this.file = file; }

    Set<String> collapsed() {
        try {
            if (!Files.isRegularFile(file)) return Set.of();
            TomlParseResult toml = Toml.parse(Files.readString(file, StandardCharsets.UTF_8));
            var out = new LinkedHashSet<String>();
            if (toml.get("collapsed") instanceof TomlArray array) for (int i = 0; i < array.size(); i++) if (array.get(i) instanceof String name) out.add(name);
            return out;
        } catch (IOException | RuntimeException unreadable) { return Set.of(); }
    }

    void save(Set<String> groups) throws IOException {
        var out = new StringBuilder("# Jasper Remote panel state.\ncollapsed = [");
        boolean first = true;
        for (String group : groups) { if (!first) out.append(", "); first = false; out.append(dev.jasper.remote.hosts.HostFile.tomlString(group)); }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, out.append("]\n").toString(), StandardCharsets.UTF_8);
    }
}
