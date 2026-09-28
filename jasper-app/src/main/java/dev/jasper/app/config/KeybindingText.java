package dev.jasper.app.config;

import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settings.TomlLiterals;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.tomlj.Toml;

/** Patches binding assignments, leaving comments and unrelated TOML text byte-for-byte intact. */
final class KeybindingText {
    private KeybindingText() { }

    static String update(String text, Map<String, Optional<String>> changes) {
        if (Toml.parse(text).hasErrors()) throw new IllegalArgumentException("Fix the settings file's TOML errors before saving shortcuts.");
        var document = TomlDocument.parse(text);
        for (var change : new TreeMap<>(changes).entrySet()) {
            List<String> path = List.of("keybindings", change.getKey());
            document = change.getValue().isPresent()
                ? document.set(path, TomlLiterals.string(change.getValue().get()))
                : document.remove(path);
        }
        return document.text();
    }
}
