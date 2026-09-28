package dev.jasper.app.settings;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** TOML source text for keys and values. */
public final class TomlLiterals {
    private TomlLiterals() { }

    /** A basic string: quoted, with every character TOML requires escaped. */
    public static String string(String value) { return "\"" + Toml.tomlEscape(value) + "\""; }

    /** One key: bare when TOML allows it, otherwise quoted. */
    public static String key(String key) {
        boolean bare = !key.isEmpty() && key.chars().allMatch(c -> c < 128 && (Character.isLetterOrDigit(c) || c == '_' || c == '-'));
        return bare ? key : string(key);
    }

    /** A dotted key such as {@code terminal.font.size} or {@code keybindings."app.shortcuts"}. */
    public static String path(List<String> path) {
        return path.stream().map(TomlLiterals::key).collect(Collectors.joining("."));
    }

    /** Parses one value's source text; throws IllegalArgumentException when it is not a TOML value. */
    public static Object parseValue(String literal) {
        var parsed = Toml.parse("v = " + literal);
        if (parsed.hasErrors()) throw new IllegalArgumentException("Not a TOML value: " + literal);
        return parsed.get(List.of("v"));
    }

    /** Source text for a value tomlj parsed: strings, numbers, booleans, dates and times, arrays and tables. */
    public static String format(Object value) {
        return switch (value) {
            case String text -> string(text);
            case Long number -> number.toString();
            case Double number -> Double.isNaN(number) ? "nan"
                : Double.isInfinite(number) ? (number > 0 ? "inf" : "-inf") : number.toString();
            case Boolean flag -> flag.toString();
            case TomlArray array -> {
                var items = new ArrayList<String>();
                for (int index = 0; index < array.size(); index++) items.add(format(array.get(index)));
                yield "[" + String.join(", ", items) + "]";
            }
            case TomlTable table -> table.keySet().stream()
                .map(key -> key(key) + " = " + format(table.get(List.of(key))))
                .collect(Collectors.joining(", ", "{ ", " }"));
            case TemporalAccessor time -> time.toString();
            default -> throw new IllegalArgumentException("Unsupported TOML value: " + value.getClass().getName());
        };
    }
}
