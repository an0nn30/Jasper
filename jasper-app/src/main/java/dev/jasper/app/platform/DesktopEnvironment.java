package dev.jasper.app.platform;

import java.util.Map;
import java.util.Objects;

/** Immutable process environment hints used for desktop palette source ordering. */
public record DesktopEnvironment(String currentDesktop, String kdeSessionVersion,
                                 String kdeFullSession, String qtPlatformTheme) {
    public DesktopEnvironment {
        Objects.requireNonNull(currentDesktop);
        Objects.requireNonNull(kdeSessionVersion);
        Objects.requireNonNull(kdeFullSession);
        Objects.requireNonNull(qtPlatformTheme);
    }
    public static DesktopEnvironment from(Map<String, String> environment) {
        Objects.requireNonNull(environment);
        return new DesktopEnvironment(environment.getOrDefault("XDG_CURRENT_DESKTOP", ""),
            environment.getOrDefault("KDE_SESSION_VERSION", ""),
            environment.getOrDefault("KDE_FULL_SESSION", ""),
            environment.getOrDefault("QT_QPA_PLATFORMTHEME", ""));
    }
    public static DesktopEnvironment capture() { return from(System.getenv()); }
}
