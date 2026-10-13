package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.JasperTheme;
import java.util.Optional;
import java.util.function.Supplier;

/** Test pages' appearance choices: Slate Steel's previews, an opaque workspace and, unless given, no system appearance. */
public final class AppearanceFixtures {
    private AppearanceFixtures() { }

    public static final AppearanceChoices CHOICES = choices(Optional::empty);

    public static AppearanceChoices choices(Supplier<Optional<Boolean>> systemDark) {
        var themes = InstalledThemes.slateSteel(preview(true), preview(false));
        return new AppearanceChoices(preview(true), preview(false), 100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes);
    }

    /** A Slate Steel variant's preview, as the application builds it. */
    public static Option.Palette preview(boolean dark) {
        var palette = JasperTheme.bundled().palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }
}
