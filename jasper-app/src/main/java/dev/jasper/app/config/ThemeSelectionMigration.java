package dev.jasper.app.config;

import dev.jasper.app.persistence.TomlStateFile;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.TomlDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Narrow, comment-preserving upgrade of theme selections; does not migrate unrelated settings. */
public final class ThemeSelectionMigration {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final List<String> PATHS = List.of("ui.theme.name", "ui.theme.workspace", "ui.theme.variant", "ui.theme.terminal", "terminal.colors");
    private ThemeSelectionMigration() {}
    public record Edit(Path target, String expectedSha256, String desiredSha256, String text,
                       Map<String, String> before, Map<String, String> after) {
        public Edit { before = Map.copyOf(before); after = Map.copyOf(after); }
    }
    public static Optional<Edit> plan(Path config, boolean macOs, Map<String, String> chromeRenames,
                                      Map<String, String> workspaceRenames) throws IOException {
        var original = TomlStateFile.readBounded(config, MAX_BYTES, "Configuration");
        if (original.isEmpty()) return Optional.empty();
        String text = original.get(); var parsed = parse(config, text, macOs); var before = physical(text);
        var document = TomlDocument.parse(text);
        if (before.containsKey("ui.theme.name") || before.containsKey("ui.theme.variant"))
            document = selection(document, parsed, before, CoreSettings.UI_THEME_NAME, chromeRenames, false);
        if (before.containsKey("ui.theme.workspace") || before.containsKey("ui.theme.terminal") || before.containsKey("terminal.colors"))
            document = selection(document, parsed, before, CoreSettings.UI_THEME_WORKSPACE, workspaceRenames, true);
        for (String path : PATHS.subList(2, PATHS.size())) if (before.containsKey(path)) document = document.remove(List.of(path.split("\\.")));
        if (text.equals(document.text())) return Optional.empty();
        if (document.text().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Configuration exceeds 1 MiB");
        parse(config, document.text(), macOs);
        return Optional.of(new Edit(config.toRealPath(), hash(text), hash(document.text()), document.text(), before, physical(document.text())));
    }
    private static TomlDocument selection(TomlDocument document, ConfigLoader.Result parsed, Map<String, String> before,
                                          Setting<String> setting, Map<String, String> renames, boolean workspace) {
        String selected = parsed.values().get(setting);
        String normalized = selected.strip().toLowerCase(java.util.Locale.ROOT);
        String desired = workspace && normalized.equals("match") ? selected : renames.getOrDefault(normalized, selected);
        String path = String.join(".", setting.key());
        if (desired.equals(before.get(path))) return document;
        return SettingWriter.write(document, setting, Optional.of(desired));
    }
    /** Physical values permit recovery to distinguish an applied edit from a new user selection. */
    public static Map<String, String> values(Path config, boolean macOs) throws IOException {
        var text = TomlStateFile.readBounded(config, MAX_BYTES, "Configuration");
        if (text.isEmpty()) return Map.of();
        parse(config, text.get(), macOs); return physical(text.get());
    }
    public static void apply(Path config, boolean macOs, Edit edit) throws IOException {
        if (!config.toRealPath().equals(edit.target())) throw new IOException("Configuration location changed while migrating");
        String original = TomlStateFile.readBounded(config, MAX_BYTES, "Configuration").orElseThrow(() -> new IOException("Configuration disappeared"));
        if (!hash(original).equals(edit.expectedSha256())) throw new IOException("Configuration changed while migrating");
        if (!hash(edit.text()).equals(edit.desiredSha256()) || edit.text().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("Invalid configuration edit");
        parse(config, edit.text(), macOs);
        ConfigMigration.backUp(edit.target(), original);
        if (!config.toRealPath().equals(edit.target()) || !TomlStateFile.readBounded(config, MAX_BYTES, "Configuration").equals(Optional.of(original)))
            throw new IOException("Configuration changed while backing up");
        TomlStateFile.writeAtomically(edit.target(), ".theme-upgrade-", edit.text());
    }
    private static ConfigLoader.Result parse(Path config, String text, boolean macOs) throws IOException {
        var parsed = ConfigLoader.parse(config, text, macOs);
        if (parsed.rejected()) throw new IOException("Fix configuration errors before migrating themes");
        return parsed;
    }
    private static Map<String, String> physical(String text) throws IOException {
        var parsed = org.tomlj.Toml.parse(text); var values = new LinkedHashMap<String, String>();
        for (String path : PATHS) {
            Object value = parsed.get(path);
            if (value == null) continue;
            if (!(value instanceof String name)) throw new IOException("Invalid theme selection: " + path);
            values.put(path, name);
        }
        return Map.copyOf(values);
    }
    private static String hash(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
