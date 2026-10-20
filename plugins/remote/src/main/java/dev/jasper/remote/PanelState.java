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

/**
 * Which groups the hosts panel shows collapsed, in {@code data/panel-state.toml}; a broken file means "all expanded".
 * {@code collapsed} lists host groups by name; the WSL group has its own {@code wsl_collapsed} flag, so no host group
 * (not even one named "wsl", nor the default group renamed so) can share its state. {@code sftp_show_hidden} is whether
 * the SFTP browsers show hidden items (off when absent or broken).
 */
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

    boolean wslCollapsed() {
        try {
            if (!Files.isRegularFile(file)) return false;
            return Toml.parse(Files.readString(file, StandardCharsets.UTF_8)).get("wsl_collapsed") instanceof Boolean value && value;
        } catch (IOException | RuntimeException unreadable) { return false; }
    }

    boolean sftpShowHidden() {
        try {
            if (!Files.isRegularFile(file)) return false;
            return Toml.parse(Files.readString(file, StandardCharsets.UTF_8)).get("sftp_show_hidden") instanceof Boolean value && value;
        } catch (IOException | RuntimeException unreadable) { return false; }
    }

    /** Saves whether the SFTP browsers show hidden items, keeping the rest. */
    void saveSftpShowHidden(boolean shown) throws IOException { save(collapsed(), wslCollapsed(), defaultGroup(), shown); }

    String defaultGroup() {
        try {
            if (!Files.isRegularFile(file)) return "Other";
            String name = Toml.parse(Files.readString(file)).getString("default_group");
            return name == null || name.isBlank() ? "Other" : name;
        } catch (IOException | RuntimeException unreadable) { return "Other"; }
    }

    void renameDefaultGroup(String name) throws IOException {
        name = name.strip();
        if (name.isBlank() || name.length() > 80) throw new IllegalArgumentException("Use a group name from 1 to 80 characters");
        var groups = new LinkedHashSet<>(collapsed());
        if (groups.remove(defaultGroup())) groups.add(name);
        save(groups, wslCollapsed(), name, sftpShowHidden());
    }

    /** Saves the collapsed host groups and keeps the WSL group's flag. */
    void save(Set<String> groups) throws IOException { save(groups, wslCollapsed(), defaultGroup(), sftpShowHidden()); }

    void save(Set<String> groups, boolean wslCollapsed) throws IOException { save(groups, wslCollapsed, defaultGroup(), sftpShowHidden()); }

    private void save(Set<String> groups, boolean wslCollapsed, String defaultGroup, boolean sftpShowHidden) throws IOException {
        var out = new StringBuilder("# Jasper Remote panel state.\ndefault_group = ").append(dev.jasper.remote.hosts.HostFile.tomlString(defaultGroup))
            .append("\nwsl_collapsed = ").append(wslCollapsed).append("\nsftp_show_hidden = ").append(sftpShowHidden).append("\ncollapsed = [");
        boolean first = true;
        for (String group : groups) { if (!first) out.append(", "); first = false; out.append(dev.jasper.remote.hosts.HostFile.tomlString(group)); }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, out.append("]\n").toString(), StandardCharsets.UTF_8);
    }
}
