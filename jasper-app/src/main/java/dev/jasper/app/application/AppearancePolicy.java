package dev.jasper.app.application;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.platform.SystemAppearance;

/** One rule for start-up, saved configuration and OS observations: the UI variant for {@code ui.appearance}. */
final class AppearancePolicy {
    private static final System.Logger LOG = System.getLogger(AppearancePolicy.class.getName());
    private AppearancePolicy() { }

    /** Whether the UI is Dark: the chosen variant, or with {@code SYSTEM} the operating system's, Dark when unknown. */
    static boolean dark(AppearanceMode mode, SystemAppearance.Observation observation) {
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> switch (observation.preference()) {
                case DARK -> true;
                case LIGHT -> false;
                case UNAVAILABLE -> {
                    LOG.log(System.Logger.Level.INFO, "The system appearance is unavailable (" + observation.reason() + "); using Dark.");
                    yield true;
                }
            };
        };
    }
}
