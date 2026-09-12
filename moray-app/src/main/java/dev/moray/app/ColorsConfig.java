package dev.moray.app;

import java.util.Objects;

record ColorsConfig(Appearance appearance, String theme) {
    ColorsConfig {
        Objects.requireNonNull(appearance);
        if (!validSelector(theme)) throw new IllegalArgumentException("Use a theme basename in Moray's themes directory.");
    }

    static ColorsConfig defaults() { return new ColorsConfig(Appearance.SYSTEM, "moray-dark"); }

    static boolean validSelector(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
            && value.codePoints().noneMatch(c -> Character.isISOControl(c) || "/\\:*?\"<>|".indexOf(c) >= 0);
    }

    boolean custom() { return !theme.equals("moray-dark") && !theme.equals("moray-light"); }
}
