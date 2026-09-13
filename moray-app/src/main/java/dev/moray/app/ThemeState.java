package dev.moray.app;

import dev.moray.terminal.Palette;

import java.util.Objects;

record ThemeState(ColorsConfig saved, Palette loaded, Appearance override, BuiltinTheme system) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(loaded); Objects.requireNonNull(system); }

    static ThemeState defaults() {
        return new ThemeState(ColorsConfig.defaults(), Palette.morayDarkPurple(), null, BuiltinTheme.DARK);
    }

    ThemeState configure(ColorsConfig next, Palette palette) {
        return new ThemeState(next, palette, saved.appearance() == next.appearance() ? override : null, system);
    }

    ThemeState choose(Appearance next) { return new ThemeState(saved, loaded, Objects.requireNonNull(next), system); }

    ThemeState systemChanged(BuiltinTheme next) { return new ThemeState(saved, loaded, override, next); }

    Appearance choice() { return override == null ? saved.appearance() : override; }

    ResolvedTheme resolve() {
        boolean light = choice() == Appearance.LIGHT
            || (choice() == Appearance.SYSTEM && system == BuiltinTheme.LIGHT);
        BuiltinTheme dark = saved.theme().equals("moray-dark") ? BuiltinTheme.CLASSIC_DARK : BuiltinTheme.DARK;
        BuiltinTheme chrome = light ? BuiltinTheme.LIGHT : dark;
        return new ResolvedTheme(chrome, saved.custom() ? loaded : chrome.palette());
    }
}
