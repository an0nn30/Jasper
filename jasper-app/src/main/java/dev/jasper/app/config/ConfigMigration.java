package dev.jasper.app.config;

import dev.jasper.app.settings.LegacyKey;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingWriter;
import dev.jasper.app.settings.TomlDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Rewrites a version 1 config.toml to the current layout: pure text in and out, plus the backup it needs. */
final class ConfigMigration {
    private ConfigMigration() { }

    /**
     * A planned rewrite.
     *
     * @param text the migrated file
     * @param changes old keys moved or dropped, plus dead tables removed
     */
    record Outcome(String text, int changes) { }

    /**
     * Plans the rewrite of {@code text}; empty when there is nothing to migrate. Refuses a rejected file.
     *
     * @param pluginSettingsExist whether {@code plugins/<id>/<id>.toml} exists, so an old plugin table may go
     */
    static Optional<Outcome> plan(String text, ConfigLoader.Result parsed, Predicate<String> pluginSettingsExist) {
        if (parsed.rejected()) throw new IllegalArgumentException("Fix config.toml's errors before migrating.");
        List<List<String>> removable = parsed.deadTables().stream()
            .filter(table -> !table.getFirst().equals("plugins") || pluginSettingsExist.test(table.get(1))).toList();
        if (parsed.legacyKeys().isEmpty() && removable.isEmpty()) return Optional.empty();
        TomlDocument document = TomlDocument.parse(text);
        var done = new HashSet<LegacyKey>();
        var byTable = new LinkedHashMap<List<String>, List<LegacyKey>>();
        for (LegacyKey key : parsed.legacyKeys())
            byTable.computeIfAbsent(key.from().subList(0, key.from().size() - 1), table -> new ArrayList<>()).add(key);
        for (Map.Entry<List<String>, List<LegacyKey>> group : byTable.entrySet()) {
            List<String> table = group.getKey();
            List<LegacyKey> keys = group.getValue();
            List<String> target = keys.getFirst().to().subList(0, keys.getFirst().to().size() - 1);
            boolean sameShape = keys.stream().allMatch(key -> key.to().subList(0, key.to().size() - 1).equals(target)
                && key.from().getLast().equals(key.to().getLast()) && !superseded(parsed, key));
            Set<List<String>> moving = new HashSet<>(keys.stream().map(LegacyKey::from).toList());
            if (sameShape && new HashSet<>(document.keysUnder(table)).equals(moving) && document.keysUnder(target).isEmpty()) {
                try {
                    document = document.renameTable(table, target);
                    done.addAll(keys);
                } catch (IllegalArgumentException notAHeader) {
                    // Written as dotted keys: move them one by one below.
                }
            }
        }
        for (LegacyKey key : parsed.legacyKeys()) {
            if (done.contains(key)) continue;
            if (superseded(parsed, key)) document = document.remove(key.from());
            else if (transformed(key)) {
                Setting<String> setting = stringSetting(key);
                document = SettingWriter.write(document.remove(key.from()), setting,
                    Optional.of(parsed.values().get(setting)));
            } else document = document.move(key.from(), key.to());
        }
        for (List<String> table : byTable.keySet()) if (!table.isEmpty()) document = document.removeTableIfEmpty(table);
        for (List<String> table : removable) document = document.removeTable(table);
        document = document.removeTableIfEmpty(List.of("plugins")).removeTableIfEmpty(List.of("palette"));
        document = document.set(List.of("version"), Integer.toString(ConfigLoader.VERSION));
        return Optional.of(new Outcome(document.text(), parsed.legacyKeys().size() + removable.size()));
    }

    private static boolean transformed(LegacyKey key) {
        return key.from().equals(List.of("ui", "theme", "variant"))
            || key.from().equals(List.of("ui", "theme", "terminal"))
            || key.from().equals(List.of("terminal", "colors"));
    }

    @SuppressWarnings("unchecked")
    private static Setting<String> stringSetting(LegacyKey key) {
        return (Setting<String>) CoreSettings.SCHEMA.find(key.to()).orElseThrow();
    }

    /** Whether the file also sets the current key, so the old one was ignored. */
    private static boolean superseded(ConfigLoader.Result parsed, LegacyKey key) {
        Setting<?> setting = CoreSettings.SCHEMA.find(key.to()).orElseThrow();
        return parsed.values().source(setting).filter(key.to()::equals).isPresent();
    }

    /** Copies the original beside {@code target} as {@code <name>.v1.bak}, numbering the name rather than overwriting. */
    static Path backUp(Path target, String original) throws IOException {
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        for (int number = 1; ; number++) {
            Path candidate = target.resolveSibling(target.getFileName() + ".v1" + (number == 1 ? "" : "." + number) + ".bak");
            try {
                Files.write(candidate, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return candidate;
            } catch (FileAlreadyExistsException taken) {
                // Every earlier backup is kept.
            }
        }
    }
}
