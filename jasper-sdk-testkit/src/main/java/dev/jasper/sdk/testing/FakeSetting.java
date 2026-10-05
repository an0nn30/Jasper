package dev.jasper.sdk.testing;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** One declared setting of a fake plugin. */
final class FakeSetting<T> implements Setting<T> {
    private static final Object WRONG_TYPE = new Object();

    private final FakePluginSettings owner;
    private final SettingSpec<T> spec;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    FakeSetting(FakePluginSettings owner, SettingSpec<T> spec) {
        this.owner = owner; this.spec = spec; this.value = spec.defaultValue();
    }

    @Override public String key() { return spec.key(); }

    @Override public T get() { return value; }

    SettingSpec<T> spec() { return spec; }

    @Override public CompletableFuture<Void> set(T next) {
        Objects.requireNonNull(next, "value");
        Optional<String> problem = spec.kind().check(next);
        if (problem.isPresent()) return CompletableFuture.failedFuture(new IllegalArgumentException(problem.get()));
        owner.raw().put(spec.key(), toRaw(next));
        owner.reload();
        return CompletableFuture.completedFuture(null);
    }

    @Override public Subscription onChanged(Consumer<T> handler) {
        listeners.add(Objects.requireNonNull(handler, "handler"));
        return () -> listeners.remove(handler);
    }

    void close() { listeners.clear(); }

    /** Reads a raw value (absent: the default); returns whether the value changed. Listeners run when {@code notify}. */
    boolean load(Object raw, boolean notify, List<String> reports) {
        T next;
        if (raw == null) next = spec.defaultValue();
        else {
            Object converted = convert(raw);
            if (converted == WRONG_TYPE) {
                reports.add(key() + ": Expected " + expected() + "; the last good value stays in force.");
                return false;
            }
            @SuppressWarnings("unchecked") T candidate = (T) converted;
            Optional<String> problem = candidate == null ? Optional.of(unknownToken()) : spec.kind().check(candidate);
            if (problem.isPresent()) { reports.add(key() + ": " + problem.get()); next = spec.defaultValue(); }
            else next = candidate;
        }
        boolean changed = !next.equals(value);
        value = next;
        if (changed && notify) for (Consumer<T> listener : listeners) listener.accept(next);
        return changed;
    }

    /** The value a raw TOML value stands for; null for an unknown choice token; {@link #WRONG_TYPE} for a wrong type. */
    private Object convert(Object raw) {
        return switch (spec.kind()) {
            case SettingKind.Bool bool -> raw instanceof Boolean ? raw : WRONG_TYPE;
            case SettingKind.IntegerRange range -> raw instanceof Long number
                ? (Object) Math.clamp(number, Integer.MIN_VALUE, Integer.MAX_VALUE) : WRONG_TYPE;
            case SettingKind.NumberRange range -> raw instanceof Long || raw instanceof Double
                ? (Object) ((Number) raw).doubleValue() : WRONG_TYPE;
            case SettingKind.Text text -> raw instanceof String ? raw : WRONG_TYPE;
            case SettingKind.OneOf<?> oneOf -> raw instanceof String token
                ? oneOf.choices().stream().filter(choice -> choice.token().equals(token)).findFirst().<Object>map(Choice::value).orElse(null)
                : WRONG_TYPE;
            case SettingKind.StringList list -> raw instanceof List<?> items && items.stream().allMatch(String.class::isInstance)
                ? List.copyOf(items) : WRONG_TYPE;
        };
    }

    @SuppressWarnings("unchecked")
    T checked(Object candidate) {
        T next = (T) Objects.requireNonNull(candidate);
        spec.kind().check(next).ifPresent(problem -> { throw new IllegalArgumentException(problem); });
        return next;
    }

    /** Convert typed UI intent using the fake's existing TOML value conversion and validation. */
    Object edited(dev.jasper.sdk.ui.UiEvent event) {
        Object raw = switch (event) {
            case dev.jasper.sdk.ui.UiEvent.Toggled toggled -> toggled.checked();
            case dev.jasper.sdk.ui.UiEvent.Selected selected -> selected.ids().stream().findFirst().orElse("");
            case dev.jasper.sdk.ui.UiEvent.Edited edited -> {
                String text = edited.text();
                if (value instanceof Integer) yield Long.valueOf(text);
                if (value instanceof Double) yield Double.valueOf(text);
                if (value instanceof List<?>) yield text.isEmpty() ? List.of() : List.of(text.split("\n", -1));
                yield text;
            }
            default -> throw new IllegalArgumentException("Unsupported setting edit");
        };
        // Editor integers reject overflow instead of the file loader's bounded conversion.
        if (value instanceof Integer && raw instanceof Long number && (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE))
            throw new IllegalArgumentException("Enter an integer in range.");
        Object converted = convert(raw);
        if (converted == WRONG_TYPE || converted == null) throw new IllegalArgumentException("Enter " + expected() + ".");
        return checked(converted);
    }

    void writeDraft(Optional<?> edit) {
        if (edit.isEmpty()) owner.raw().remove(key());
        else owner.raw().put(key(), toRaw(checked(edit.get())));
    }

    private Object toRaw(T next) {
        return switch (spec.kind()) {
            case SettingKind.IntegerRange range -> ((Integer) next).longValue();
            case SettingKind.OneOf<?> oneOf -> oneOf.choices().stream().filter(choice -> choice.value().equals(next))
                .findFirst().orElseThrow().token();
            default -> next;
        };
    }

    private String expected() {
        return switch (spec.kind()) {
            case SettingKind.Bool bool -> "a boolean";
            case SettingKind.IntegerRange range -> "an integer";
            case SettingKind.NumberRange range -> "a number";
            case SettingKind.Text text -> "a string";
            case SettingKind.OneOf<?> oneOf -> "a string";
            case SettingKind.StringList list -> "an array of strings";
        };
    }

    private String unknownToken() {
        var oneOf = (SettingKind.OneOf<?>) spec.kind();
        return "Use one of: " + oneOf.choices().stream().map(Choice::token).collect(Collectors.joining(", ")) + ".";
    }
}
