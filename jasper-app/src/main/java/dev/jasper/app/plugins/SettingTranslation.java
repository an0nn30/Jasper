package dev.jasper.app.plugins;

import dev.jasper.app.settings.Applies;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.SettingSpec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** SDK setting declarations in the application's terms. SDK types stop here. */
final class SettingTranslation {
    private SettingTranslation() { }

    /**
     * One declaration in application terms.
     *
     * @param spec the plugin's declaration
     * @param setting the application's setting
     * @param toSdk converts an application value to the plugin's (identity except for numbers)
     * @param toApp converts a plugin value to the application's
     */
    record Translated(SettingSpec<?> spec, Setting<?> setting, Function<Object, Object> toSdk, Function<Object, Object> toApp) { }

    /**
     * Translates {@code spec}; {@code guard} wraps each plugin-supplied rule so that it runs contained. A look's default
     * (spec 5.8) is read with the translated kind, as the settings file would be: a value it accepts is the setting's
     * default, and otherwise {@code problem} hears the reader's first message and the declared default stays.
     *
     * @param lookDefault the look's raw TOML value for this key, if it gives one
     * @param problem hears why the look's value is not used, without the reader's "; using ..." tail
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Translated translate(SettingSpec<?> spec, UnaryOperator<Predicate<String>> guard, Optional<Object> lookDefault,
                                Consumer<String> problem) {
        Function<Object, Object> same = value -> value;
        Function<Object, Object> toSdk = same;
        Function<Object, Object> toApp = same;
        Object defaultValue = spec.defaultValue();
        SettingKind kind = switch (spec.kind()) {
            case dev.jasper.sdk.settings.SettingKind.Bool bool -> SettingKind.bool();
            case dev.jasper.sdk.settings.SettingKind.IntegerRange range ->
                SettingKind.integer(range.min(), range.max(), SettingKind.OutOfRange.DEFAULT);
            case dev.jasper.sdk.settings.SettingKind.NumberRange range -> {
                toSdk = value -> ((Float) value).doubleValue();
                toApp = value -> ((Double) value).floatValue();
                defaultValue = ((Double) spec.defaultValue()).floatValue();
                yield SettingKind.number(range.min(), range.max());
            }
            case dev.jasper.sdk.settings.SettingKind.Text text -> SettingKind.string(guard.apply(text.valid()), text.invalidMessage());
            case dev.jasper.sdk.settings.SettingKind.OneOf oneOf -> SettingKind.choice(((List<Choice<?>>) oneOf.choices()).stream()
                .map(choice -> Map.entry(choice.token(), (Object) choice.value())).toList());
            case dev.jasper.sdk.settings.SettingKind.StringList list -> SettingKind.stringList(guard.apply(list.validElement()));
        };
        if (lookDefault.isPresent()) {
            Reading<?> reading = kind.read(lookDefault.get(), defaultValue);
            if (reading.issues().isEmpty()) defaultValue = reading.value();
            else problem.accept(reading.issues().getFirst().message().replaceFirst("; using [^;]*\\.$", "."));
        }
        Setting<?> setting = Setting.of(spec.key(), kind, defaultValue).label(spec.label()).description(spec.description())
            .keywords(spec.keywords().toArray(String[]::new))
            .applies(spec.applies() == dev.jasper.sdk.settings.Applies.RESTART ? Applies.RESTART : Applies.LIVE).build();
        return new Translated(spec, setting, toSdk, toApp);
    }
}
