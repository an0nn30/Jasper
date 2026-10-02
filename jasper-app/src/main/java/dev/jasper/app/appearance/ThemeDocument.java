package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One Jasper theme as written: only what its file sets, nothing derived. {@code ansi} maps 0-7 to
 * {@code [colors.normal]} and 8-15 to {@code [colors.bright]}. {@code ui} keeps the file's order, which matters for
 * IntelliJ's {@code *.} wildcard keys. {@code icons} is IntelliJ's icon table, preserved by the native {@code [icons]} section. {@code parentLine} is the line of {@code extends}, 0 without one.
 */
public record ThemeDocument(String origin, String name, Optional<Boolean> dark, Optional<String> parent, int parentLine,
                            Optional<Boolean> uiComplete, Map<String, String> metadata, Map<Seed, Color> seeds,
                            Map<Integer, Color> ansi, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                            Map<String, Object> ui, Map<String, Object> icons) {
    /** The TOML names of the eight ANSI colours, in index order. */
    public static final List<String> ANSI_NAMES = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");

    public ThemeDocument {
        Objects.requireNonNull(origin); Objects.requireNonNull(name); Objects.requireNonNull(dark);
        Objects.requireNonNull(parent); Objects.requireNonNull(uiComplete);
        metadata = ordered(metadata);
        seeds = Map.copyOf(seeds);
        ansi = Map.copyOf(ansi);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        ui = data(ui);
        icons = data(icons);
    }

    /**
     * This document laid over {@code base}: every section merges key by key with this document's values winning,
     * and a {@code [ui]} key this document sets replaces the base's and moves after the base's keys. The result
     * keeps this document's origin, name and metadata, and extends whatever {@code base} extends.
     */
    ThemeDocument over(ThemeDocument base) {
        var layered = new LinkedHashMap<>(base.ui);
        ui.forEach((key, value) -> { layered.remove(key); layered.put(key, value); });
        return new ThemeDocument(origin, name, dark.or(base::dark), base.parent, base.parentLine,
            uiComplete.or(base::uiComplete), metadata, merged(base.seeds, seeds), merged(base.ansi, ansi),
            merged(base.tokens, tokens), merged(base.syntax, syntax), layered, icons.isEmpty() ? base.icons : icons);
    }

    private static <K, V> Map<K, V> merged(Map<K, V> base, Map<K, V> top) {
        var result = new HashMap<>(base);
        result.putAll(top);
        return result;
    }

    private static <V> Map<String, V> ordered(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(map)));
    }
    private static Map<String, Object> data(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        int[] count = {0};
        source.forEach((key, value) -> result.put(Objects.requireNonNull(key), freeze(value, 1, count)));
        return Collections.unmodifiableMap(result);
    }

    private static Object freeze(Object value, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 100_000) throw new IllegalArgumentException("Theme data is too deeply nested or large");
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, child) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("Theme data keys must be strings");
                result.put(text, freeze(child, depth + 1, count));
            });
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(child -> freeze(child, depth + 1, count)).toList();
        return Objects.requireNonNull(value, "Theme data cannot contain null");
    }
}
