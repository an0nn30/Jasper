package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.settingsui.AppearanceChoices;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import java.util.Optional;

/** The Appearance page's values: Slate Steel's variants as previews, its workspace glass and the live OS appearance. */
final class SettingsAppearance {
    private SettingsAppearance() { }

    static AppearanceChoices choices(SystemAppearance system) {
        var glass = SlateSteel.bundled().generated(true).glass();
        return new AppearanceChoices(preview(true), preview(false), (float) (glass.opacity() * 100), glass.blur(),
            () -> switch (system.current().preference()) {
                case DARK -> Optional.of(true);
                case LIGHT -> Optional.of(false);
                case UNAVAILABLE -> Optional.empty();
            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())));
    }

    /** A variant's preview: its input, sidebar and app surfaces, text and accent. */
    static Option.Palette preview(boolean dark) {
        var palette = SlateSteel.bundled().palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }
}
