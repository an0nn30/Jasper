package dev.jasper.sdk.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * How a setting's values are checked. The runtime reads a value from the plugin's settings file; a value its kind
 * rejects is replaced by the setting's default and reported.
 *
 * @param <T> the value type
 */
public sealed interface SettingKind<T> {
    /**
     * Checks a value.
     *
     * @param value a value of this kind's type
     * @return empty when the value is allowed, otherwise a message for the user
     */
    Optional<String> check(T value);

    /**
     * True or false.
     *
     * @return the kind
     */
    static SettingKind<Boolean> bool() { return new Bool(); }

    /**
     * A whole number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     * @return the kind
     */
    static SettingKind<Integer> integer(int min, int max) { return new IntegerRange(min, max); }

    /**
     * A finite number in a range. Jasper keeps the value, the default included, at float precision: 0.1 reads back as
     * 0.10000000149011612.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     * @return the kind
     */
    static SettingKind<Double> number(double min, double max) { return new NumberRange(min, max); }

    /**
     * Any text.
     *
     * @return the kind
     */
    static SettingKind<String> text() { return new Text(text -> true, "Not an allowed value."); }

    /**
     * Text that a rule accepts. The rule is plugin code; the runtime treats a rule that throws as rejecting the value.
     *
     * @param valid the rule
     * @param invalidMessage the message for a value the rule rejects
     * @return the kind
     */
    static SettingKind<String> text(Predicate<String> valid, String invalidMessage) { return new Text(valid, invalidMessage); }

    /**
     * One of a fixed set of options.
     *
     * @param choices the options, in the order a picker lists them
     * @param <T> the value type
     * @return the kind
     */
    static <T> SettingKind<T> choice(List<Choice<T>> choices) { return new OneOf<>(choices); }

    /**
     * A list of strings.
     *
     * @return the kind
     */
    static SettingKind<List<String>> stringList() { return new StringList(element -> true); }

    /**
     * A list of strings that each pass a rule.
     *
     * @param validElement the rule for each element
     * @return the kind
     */
    static SettingKind<List<String>> stringList(Predicate<String> validElement) { return new StringList(validElement); }

    /** True or false. */
    record Bool() implements SettingKind<Boolean> {
        @Override public Optional<String> check(Boolean value) { return Optional.empty(); }
    }

    /**
     * A whole number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     */
    record IntegerRange(int min, int max) implements SettingKind<Integer> {
        /** Rejects an empty range. */
        public IntegerRange {
            if (min > max) throw new IllegalArgumentException("min exceeds max");
        }
        @Override public Optional<String> check(Integer value) {
            return value >= min && value <= max ? Optional.empty()
                : Optional.of("Use a whole number from " + min + "–" + max + ".");
        }
    }

    /**
     * A finite number in a range.
     *
     * @param min the lowest allowed value
     * @param max the highest allowed value
     */
    record NumberRange(double min, double max) implements SettingKind<Double> {
        /** Rejects an empty or infinite range. */
        public NumberRange {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) throw new IllegalArgumentException("Not a finite range");
        }
        @Override public Optional<String> check(Double value) {
            return Double.isFinite(value) && value >= min && value <= max ? Optional.empty()
                : Optional.of("Use a number from " + bound(min) + "–" + bound(max) + ".");
        }
        private static String bound(double value) {
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }

    /**
     * Text that a rule accepts.
     *
     * @param valid the rule
     * @param invalidMessage the message for a value the rule rejects
     */
    record Text(Predicate<String> valid, String invalidMessage) implements SettingKind<String> {
        /** Rejects nulls. */
        public Text {
            Objects.requireNonNull(valid, "valid");
            Objects.requireNonNull(invalidMessage, "invalidMessage");
        }
        @Override public Optional<String> check(String value) {
            return valid.test(value) ? Optional.empty() : Optional.of(invalidMessage);
        }
    }

    /**
     * One of a fixed set of options.
     *
     * @param choices the options, with unique tokens
     * @param <T> the value type
     */
    record OneOf<T>(List<Choice<T>> choices) implements SettingKind<T> {
        /** Copies the options and rejects an empty list or a repeated token. */
        public OneOf {
            choices = List.copyOf(choices);
            if (choices.isEmpty()) throw new IllegalArgumentException("A choice needs options");
            var tokens = new HashSet<String>();
            for (Choice<T> choice : choices)
                if (!tokens.add(choice.token())) throw new IllegalArgumentException("Repeated token: " + choice.token());
        }
        @Override public Optional<String> check(T value) {
            for (Choice<T> choice : choices) if (choice.value().equals(value)) return Optional.empty();
            return Optional.of("Use one of: " + choices.stream().map(Choice::token).collect(Collectors.joining(", ")) + ".");
        }
    }

    /**
     * A list of strings that each pass a rule.
     *
     * @param validElement the rule for each element
     */
    record StringList(Predicate<String> validElement) implements SettingKind<List<String>> {
        /** Rejects a null rule. */
        public StringList {
            Objects.requireNonNull(validElement, "validElement");
        }
        @Override public Optional<String> check(List<String> value) {
            for (String element : value)
                if (!validElement.test(element)) return Optional.of("Not an allowed list element: \"" + element + "\".");
            return Optional.empty();
        }
    }
}
