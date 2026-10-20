package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Option;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Appearance page shows, supplied by the application: the installed themes and schemes, the workspace glass
 * (the defaults the Workspace rows return to), the operating system's appearance when it is known, and its changes.
 * EDT only.
 *
 * @param opacity the theme's workspace opacity, 10–100
 * @param blur whether the theme blurs a translucent workspace
 * @param systemDark the operating system's appearance, empty when unavailable
 * @param onChanged runs its argument whenever the operating system's appearance changes
 * @param themes the installed themes and schemes, read again whenever the page refreshes
 * @param imports the theme gears' hooks; empty shows no gears
 */
public record AppearanceChoices(float opacity, boolean blur, Supplier<Optional<Boolean>> systemDark,
                                Function<Runnable, Subscription> onChanged, Supplier<InstalledThemes> themes,
                                Optional<ThemeImports> imports) {
    public AppearanceChoices {
        Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
        Objects.requireNonNull(themes, "themes"); Objects.requireNonNull(imports, "imports");
    }

    /** Fixed previews, Slate Cobalt alone, an opaque workspace and no system appearance, for pages built without an application. */
    public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
        var themes = InstalledThemes.slateCobalt(dark, light);
        return new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes, Optional.empty());
    }
}
