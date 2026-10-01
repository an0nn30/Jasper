package dev.jasper.app.config;

import java.util.Locale;
import java.util.Objects;

/** The saved workspace theme name; {@code match} follows the chrome theme. */
public record WorkspaceThemeChoice(String name) {
    public static final WorkspaceThemeChoice MATCH = new WorkspaceThemeChoice("match");

    public WorkspaceThemeChoice {
        name = Objects.requireNonNull(name).strip();
        if (name.isEmpty()) throw new IllegalArgumentException("A workspace choice needs a name");
    }

    public static WorkspaceThemeChoice parse(String value) {
        String name = Objects.requireNonNull(value).strip();
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "match" -> MATCH;
            default -> new WorkspaceThemeChoice(name);
        };
    }

    public boolean matchesChrome() { return name.equalsIgnoreCase("match"); }
}
