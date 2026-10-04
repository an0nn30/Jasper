package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Writes one setting's value, or its removal, into a document. */
public final class SettingWriter {
    private SettingWriter() { }

    /** Writes {@code value} for {@code setting}, or removes it (restoring the default) when {@code value} is empty. */
    public static <T> TomlDocument write(TomlDocument document, Setting<T> setting, Optional<T> value) {
        if (!(setting.kind() instanceof SettingKind.StringMap)) {
            return value.isPresent() ? document.set(setting.key(), setting.kind().literal(value.get())) : document.remove(setting.key());
        }
        TomlDocument next = document;
        for (List<String> key : document.keysUnder(setting.key())) next = next.remove(key);
        @SuppressWarnings("unchecked")
        Map<String, String> entries = (Map<String, String>) value.orElse(null);
        // A table with no entries left goes too: an empty [table] header would read back as an empty table.
        if (entries == null) return next.removeTableIfEmpty(setting.key());
        for (var entry : new TreeMap<>(entries).entrySet()) {
            var path = new ArrayList<>(setting.key());
            path.add(entry.getKey());
            next = next.set(List.copyOf(path), TomlLiterals.string(entry.getValue()));
        }
        return next;
    }
}
