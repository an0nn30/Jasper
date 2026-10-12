package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Option;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Appearance page shows, supplied by the application: Slate Steel's Dark and Light previews, the theme's
 * workspace glass (the defaults the Workspace rows return to), the operating system's appearance when it is known, and
 * its changes. EDT only.
 *
 * @param dark the Dark tile's preview
 * @param light the Light tile's preview
 * @param opacity the theme's workspace opacity, 10–100
 * @param blur whether the theme blurs a translucent workspace
 * @param systemDark the operating system's appearance, empty when unavailable
 * @param onChanged runs its argument whenever the operating system's appearance changes
 */
public record AppearanceChoices(Option.Palette dark, Option.Palette light, float opacity, boolean blur,
                                Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged) {
    public AppearanceChoices {
        Objects.requireNonNull(dark, "dark"); Objects.requireNonNull(light, "light");
        Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
    }

    /** Fixed previews, an opaque workspace and no system appearance, for pages built without an application. */
    public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
        return new AppearanceChoices(dark, light, 100f, false, Optional::empty, ignored -> new Subscription(() -> { }));
    }
}
