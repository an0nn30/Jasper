package dev.jasper.app.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/**
 * How one setting's TOML value is read, checked and written. Messages and severities match what the
 * configuration loader has always reported, so moving a setting onto a kind changes no diagnostic.
 */
public sealed interface SettingKind<T> {
    /** What an out-of-range integer does: use the nearest bound with a warning, or the default with an error. */
    enum OutOfRange { CLAMP, DEFAULT }

    /** Reads a value tomlj parsed; {@code defaultValue} stands in for anything unusable. */
    Reading<T> read(Object value, T defaultValue);

    /** The TOML source text for {@code value}. */
    String literal(T value);

    /** Empty when {@code value} could be saved; otherwise the message a file holding it would produce. */
    default Optional<String> check(T value) {
        Object parsed;
        try {
            parsed = TomlLiterals.parseValue(literal(value));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException invalid) {
            return Optional.of("Not a valid value: " + value + ".");
        }
        return read(parsed, value).issues().stream().map(Reading.Issue::message).findFirst();
    }

    static SettingKind<Boolean> bool() { return new Bool(); }

    static SettingKind<Integer> integer(int min, int max, OutOfRange outOfRange) { return new IntegerRange(min, max, outOfRange); }

    static SettingKind<Float> number(double min, double max) { return new NumberRange(min, max); }

    static SettingKind<String> string(Predicate<String> valid, String invalidMessage) {
        return new Text(valid, invalidMessage, UnaryOperator.identity());
    }

    static SettingKind<String> string(Predicate<String> valid, String invalidMessage, UnaryOperator<String> normalize) {
        return new Text(valid, invalidMessage, normalize);
    }

    /** Tokens in the order a picker should list them. */
    static <E> SettingKind<E> choice(List<Map.Entry<String, E>> tokens) { return new Choice<>(tokens); }

    static SettingKind<List<String>> stringList(Predicate<String> validElement) { return new StringList(validElement); }

    static SettingKind<Map<String, String>> stringMap(MapRules rules) { return new StringMap(rules); }

    record Bool() implements SettingKind<Boolean> {
        @Override public Reading<Boolean> read(Object value, Boolean defaultValue) {
            return value instanceof Boolean flag ? Reading.ok(flag) : Reading.wrongType(defaultValue, "a boolean");
        }
        @Override public String literal(Boolean value) { return value.toString(); }
    }

    record IntegerRange(int min, int max, OutOfRange outOfRange) implements SettingKind<Integer> {
        public IntegerRange {
            if (min > max) throw new IllegalArgumentException("min exceeds max");
            Objects.requireNonNull(outOfRange, "outOfRange");
        }
        @Override public Reading<Integer> read(Object value, Integer defaultValue) {
            if (!(value instanceof Long number)) return Reading.wrongType(defaultValue, "an integer");
            if (number >= min && number <= max) return Reading.ok(number.intValue());
            String range = "Use an integer from " + min + "–" + max;
            if (outOfRange == OutOfRange.DEFAULT)
                return Reading.replaced(defaultValue, Reading.Severity.ERROR, range + "; using the default.");
            int bound = number < min ? min : max;
            return Reading.replaced(bound, Reading.Severity.WARNING, range + "; using " + bound + ".");
        }
        @Override public String literal(Integer value) { return value.toString(); }
    }

    record NumberRange(double min, double max) implements SettingKind<Float> {
        @Override public Reading<Float> read(Object value, Float defaultValue) {
            if (!(value instanceof Long) && !(value instanceof Double)) return Reading.wrongType(defaultValue, "a number");
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number) && number >= min && number <= max) return Reading.ok((float) number);
            return Reading.replaced(defaultValue, Reading.Severity.ERROR,
                "Use a finite number from " + bound(min) + "–" + bound(max) + "; using the default.");
        }
        @Override public String literal(Float value) {
            float number = value;
            if (Float.isNaN(number)) return "nan";
            if (Float.isInfinite(number)) return number > 0 ? "inf" : "-inf";
            if (number == Math.rint(number) && Math.abs(number) < 1e15) return Long.toString((long) number);
            return Float.toString(number);
        }
        /** Whole bounds print without a fraction, so messages read "6–72". */
        private static String bound(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }

    record Text(Predicate<String> valid, String invalidMessage, UnaryOperator<String> normalize) implements SettingKind<String> {
        @Override public Reading<String> read(Object value, String defaultValue) {
            if (!(value instanceof String text)) return Reading.wrongType(defaultValue, "a string");
            if (!valid.test(text)) return Reading.replaced(defaultValue, Reading.Severity.ERROR, invalidMessage);
            return Reading.ok(normalize.apply(text));
        }
        @Override public String literal(String value) { return TomlLiterals.string(value); }
    }

    record Choice<E>(List<Map.Entry<String, E>> tokens) implements SettingKind<E> {
        public Choice {
            tokens = List.copyOf(tokens);
            if (tokens.isEmpty()) throw new IllegalArgumentException("A choice needs tokens");
        }
        @Override public Reading<E> read(Object value, E defaultValue) {
            if (!(value instanceof String text)) return Reading.wrongType(defaultValue, "a string");
            for (var token : tokens) if (token.getKey().equals(text)) return Reading.ok(token.getValue());
            return Reading.replaced(defaultValue, Reading.Severity.ERROR, "Use "
                + tokens.stream().map(Map.Entry::getKey).sorted().collect(Collectors.joining(", ")) + "; using the default.");
        }
        @Override public String literal(E value) { return TomlLiterals.string(token(value)); }
        /** The token that stands for {@code value}. */
        public String token(E value) {
            for (var token : tokens) if (token.getValue().equals(value)) return token.getKey();
            throw new IllegalArgumentException("Not one of this setting's choices: " + value);
        }
    }

    record StringList(Predicate<String> validElement) implements SettingKind<List<String>> {
        @Override public Reading<List<String>> read(Object value, List<String> defaultValue) {
            if (!(value instanceof TomlArray array)) return Reading.wrongType(defaultValue, "an array of strings");
            var values = new ArrayList<String>();
            var issues = new ArrayList<Reading.Issue>();
            for (int index = 0; index < array.size(); index++) {
                Object element = array.get(index);
                if (!(element instanceof String text))
                    issues.add(new Reading.Issue(List.of(), Reading.Severity.ERROR,
                        "Expected an array of strings; configuration was not applied.", true));
                else if (!validElement.test(text))
                    issues.add(new Reading.Issue(List.of(), Reading.Severity.ERROR,
                        "Invalid string array element; using the default list.", false));
                else values.add(text);
            }
            return new Reading<>(issues.isEmpty() ? List.copyOf(values) : defaultValue, issues);
        }
        @Override public String literal(List<String> value) {
            return value.stream().map(TomlLiterals::string).collect(Collectors.joining(", ", "[", "]"));
        }
    }

    /**
     * The rules a table of string values follows. Each message is the whole diagnostic text.
     *
     * @param validName which entry names are allowed
     * @param invalidName the error for any other name; the entry is omitted
     * @param reserved names the application owns
     * @param reservedName the warning for a reserved name; the entry is omitted
     * @param validValue which values are allowed
     * @param invalidValue the error for any other value; the entry is omitted
     * @param valueType what a value must be, for the wrong-type message ("a string environment value")
     */
    record MapRules(Predicate<String> validName, String invalidName, Set<String> reserved, String reservedName,
                    Predicate<String> validValue, String invalidValue, String valueType) {
        public MapRules { reserved = Set.copyOf(reserved); }
    }

    record StringMap(MapRules rules) implements SettingKind<Map<String, String>> {
        @Override public Reading<Map<String, String>> read(Object value, Map<String, String> defaultValue) {
            if (!(value instanceof TomlTable table)) return Reading.wrongType(defaultValue, "a table");
            var values = new LinkedHashMap<String, String>();
            var issues = new ArrayList<Reading.Issue>();
            for (String name : table.keySet()) {
                Object entry = table.get(List.of(name));
                List<String> at = List.of(name);
                if (!(entry instanceof String text))
                    issues.add(new Reading.Issue(at, Reading.Severity.ERROR,
                        "Expected " + rules.valueType() + "; configuration was not applied.", true));
                else if (!rules.validName().test(name)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidName(), false));
                else if (rules.reserved().contains(name)) issues.add(new Reading.Issue(at, Reading.Severity.WARNING, rules.reservedName(), false));
                else if (!rules.validValue().test(text)) issues.add(new Reading.Issue(at, Reading.Severity.ERROR, rules.invalidValue(), false));
                else values.put(name, text);
            }
            return new Reading<>(Map.copyOf(values), issues);
        }
        @Override public String literal(Map<String, String> value) {
            if (value.isEmpty()) return "{}";
            return value.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> TomlLiterals.key(entry.getKey()) + " = " + TomlLiterals.string(entry.getValue()))
                .collect(Collectors.joining(", ", "{ ", " }"));
        }
    }
}
