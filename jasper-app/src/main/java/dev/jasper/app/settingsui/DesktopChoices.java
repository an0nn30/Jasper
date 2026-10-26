package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Desktop colors for an Appearance page. All hooks run on the EDT. Preview reads cached values; acquire retains one
 * source and polarity until its subscription closes. Status describes the applied palette, independently of previews.
 */
public record DesktopChoices(boolean available, Supplier<String> status, BiFunction<String, Boolean, Option.Palette> preview,
                             Function<Runnable, Subscription> onChanged, BiFunction<String, Boolean, Subscription> acquire) {
    public DesktopChoices {
        Objects.requireNonNull(status); Objects.requireNonNull(preview); Objects.requireNonNull(onChanged); Objects.requireNonNull(acquire);
    }

    /** Retains saved choices on other platforms and in pages built without the application. */
    public static final DesktopChoices UNAVAILABLE = new DesktopChoices(false,
        () -> "Desktop colors unavailable on this platform; using bundled colors", (source, dark) -> bundled(dark),
        ignored -> new Subscription(() -> {}), (source, dark) -> new Subscription(() -> {}));

    private static Option.Palette bundled(boolean dark) {
        var palette = JasperTheme.builtIn("slate-cobalt").orElseThrow().palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(), palette.color(SemanticColor.ACCENT).getRGB());
    }
}
