package dev.moray.app;

import dev.moray.terminal.Palette;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure parser for Moray's supported custom-palette TOML subset. */
final class ThemeLoader {
    record Result(Palette palette, List<ConfigDiagnostic> diagnostics, boolean rejected) {
        Result {
            Objects.requireNonNull(palette, "palette");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private ThemeLoader() { }

    static Result parse(Path file, String text) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(text, "text");
        TomlParseResult toml = Toml.parse(text);
        var diagnostics = new ArrayList<ConfigDiagnostic>();
        Palette base = Palette.morayDark();
        if (toml.hasErrors()) {
            toml.errors().forEach(error -> diagnostics.add(new ConfigDiagnostic(
                ConfigDiagnostic.Severity.ERROR, file, error.position().line(), error.position().column(), "",
                "Invalid TOML syntax or duplicate definition; check this location.")));
            sort(diagnostics);
            return new Result(base, diagnostics, true);
        }

        var values = defaults(base);
        int[] supported = {0};
        visit(toml, toml, List.of(), values, diagnostics, file, supported);
        if (supported[0] == 0) {
            diagnostics.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.ERROR, file, 0, 0, "",
                "Theme must define at least one supported color."));
        }
        var ansi = new ArrayList<Color>();
        String[] names = names();
        for (int i = 0; i < 16; i++) {
            ansi.add(values.get(List.of("colors", i < 8 ? "normal" : "bright", names[i % 8])));
        }
        Palette candidate = new Palette(values.get(List.of("colors", "primary", "foreground")),
            values.get(List.of("colors", "primary", "background")),
            values.get(List.of("colors", "cursor", "cursor")),
            values.get(List.of("colors", "selection", "background")), ansi);
        sort(diagnostics);
        boolean rejected = diagnostics.stream().anyMatch(d -> d.severity() == ConfigDiagnostic.Severity.ERROR);
        return new Result(rejected ? base : candidate, diagnostics, rejected);
    }

    private static LinkedHashMap<List<String>, Color> defaults(Palette base) {
        var values = new LinkedHashMap<List<String>, Color>();
        values.put(List.of("colors", "primary", "foreground"), base.foreground());
        values.put(List.of("colors", "primary", "background"), base.background());
        values.put(List.of("colors", "cursor", "cursor"), base.cursor());
        values.put(List.of("colors", "selection", "background"), base.selection());
        String[] names = names();
        for (int i = 0; i < 16; i++) {
            values.put(List.of("colors", i < 8 ? "normal" : "bright", names[i % 8]), base.ansi().get(i));
        }
        return values;
    }

    private static String[] names() {
        return new String[]{"black", "red", "green", "yellow", "blue", "magenta", "cyan", "white"};
    }

    private static void visit(TomlParseResult root, TomlTable table, List<String> parent,
                              Map<List<String>, Color> values, List<ConfigDiagnostic> diagnostics,
                              Path file, int[] supported) {
        for (String key : table.keySet()) {
            var path = new ArrayList<>(parent);
            path.add(key);
            Object value = table.get(List.of(key));
            TomlPosition pos = root.inputPositionOf(path);
            String message = null;
            ConfigDiagnostic.Severity severity = ConfigDiagnostic.Severity.WARNING;
            if (values.containsKey(path)) {
                supported[0]++;
                try { values.put(List.copyOf(path), decode(value)); }
                catch (IllegalArgumentException failure) {
                    message = failure.getMessage();
                    severity = ConfigDiagnostic.Severity.ERROR;
                }
            } else if (values.keySet().stream().anyMatch(candidate -> candidate.size() > path.size()
                && candidate.subList(0, path.size()).equals(path))) {
                if (value instanceof TomlTable nested) visit(root, nested, path, values, diagnostics, file, supported);
                else {
                    message = "Expected a color table.";
                    severity = ConfigDiagnostic.Severity.ERROR;
                }
            } else {
                message = "Unsupported theme setting or table; ignored.";
            }
            if (message != null) diagnostics.add(new ConfigDiagnostic(severity, file,
                pos == null ? 0 : pos.line(), pos == null ? 0 : pos.column(), String.join(".", path), message));
        }
    }

    private static Color decode(Object value) {
        if (!(value instanceof String text) || !text.matches("(?:#|0x)[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(
                "Use a quoted #RRGGBB color; per-cell color references are not supported.");
        }
        return new Color(Integer.parseInt(text.substring(text.startsWith("#") ? 1 : 2), 16));
    }

    private static void sort(List<ConfigDiagnostic> diagnostics) {
        diagnostics.sort(Comparator.comparingInt(ConfigDiagnostic::line).thenComparingInt(ConfigDiagnostic::column));
    }
}
