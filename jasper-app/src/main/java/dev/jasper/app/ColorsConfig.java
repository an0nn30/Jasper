package dev.jasper.app;

import java.util.Objects;

record ColorsConfig(Appearance appearance, String theme) {
    ColorsConfig {
        Objects.requireNonNull(appearance);
        if (!validSelector(theme)) throw new IllegalArgumentException("Use a theme basename in Jasper's themes directory.");
    }

    static ColorsConfig defaults() { return new ColorsConfig(Appearance.SYSTEM, "jasper-dark-purple"); }

    static boolean validSelector(String value) {
        return value != null && !value.isBlank() && !value.equals(".") && !value.equals("..")
            && value.codePoints().noneMatch(c -> Character.isISOControl(c) || "/\\:*?\"<>|".indexOf(c) >= 0);
    }

    boolean custom() { return BuiltinTheme.fromId(theme) == null; }
}
