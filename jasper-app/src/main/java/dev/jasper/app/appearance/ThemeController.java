package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.jasper.app.lifecycle.Subscription;
import java.util.function.Predicate;
import javax.swing.*;

/** Application-owned, EDT-confined theme selection and value subscriptions. */
public final class ThemeController {

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(BuiltinTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.label(), cause);
        }
    }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<BuiltinTheme> installer;
    private final ThemeStyle style;
    private ThemeState state = ThemeState.defaults();

public ThemeController() { this(ThemeStyle.MODERN, Appearance.DARK); }
public ThemeController(ThemeStyle style, Appearance saved) {
    this(style, saved, ThemeController::install);
}
public ThemeController(Predicate<BuiltinTheme> installer) {
    this(ThemeStyle.MODERN, Appearance.DARK, installer);
}
ThemeController(ThemeStyle style, Appearance saved, Predicate<BuiltinTheme> installer) {
    requireEdt();
    this.style = Objects.requireNonNull(style);
    this.installer = Objects.requireNonNull(installer);
    this.state = ThemeState.defaults().configure(Objects.requireNonNull(saved));
    installOrThrow(resolve(state).chrome());
}
public ThemeStyle style() { requireEdt(); return style; }
private ResolvedTheme resolve(ThemeState candidate) {
    return style == ThemeStyle.RETRO
        ? new ResolvedTheme(BuiltinTheme.RETRO, BuiltinTheme.RETRO.palette()) : candidate.resolve();
}
public ResolvedTheme current() { requireEdt(); return resolve(state); }
public Appearance choice() {
    requireEdt(); return style == ThemeStyle.RETRO ? Appearance.LIGHT : state.choice();
}
public void selectAppearance(Appearance choice) {
    requireEdt(); Objects.requireNonNull(choice);
    if (style == ThemeStyle.MODERN) apply(state.choose(choice));
}
private void apply(ThemeState candidate) {
    if (style == ThemeStyle.RETRO) { state = candidate; return; }
    ResolvedTheme previous = resolve(state), next = resolve(candidate);
    boolean chromeChanged = previous.chrome() != next.chrome();
    boolean choiceChanged = state.choice() != candidate.choice();
    if (chromeChanged) installOrThrow(next.chrome());
    state = candidate;
    if (!previous.equals(next) || choiceChanged)
        for (var listener : List.copyOf(listeners)) listener.accept(next, chromeChanged);
}

    public void configure(Appearance saved) { requireEdt(); apply(state.configure(Objects.requireNonNull(saved))); }
    public void select(BuiltinTheme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }

    /** Replays current appearance; the subscribing owner closes its registration on disposal. */
    public Subscription subscribe(BiConsumer<ResolvedTheme, Boolean> listener) {
        requireEdt(); Objects.requireNonNull(listener);
        if (!listeners.add(listener)) return new Subscription(() -> {});
        try { listener.accept(current(), true); }
        catch (RuntimeException | Error failure) { listeners.remove(listener); throw failure; }
        return new Subscription(() -> { requireEdt(); listeners.remove(listener); });
    }

    private void installOrThrow(BuiltinTheme theme) {
        LookAndFeel previous = UIManager.getLookAndFeel();
        var previousMetalTheme = javax.swing.plaf.metal.MetalLookAndFeel.getCurrentTheme();
        try {
            if (!installer.test(theme)) throw new IllegalStateException("Could not apply theme: " + theme.label());
        } catch (RuntimeException failure) {
            javax.swing.plaf.metal.MetalLookAndFeel.setCurrentTheme(previousMetalTheme);
            // A failed setup may have installed a LAF before one of its initialization hooks failed.
            if (UIManager.getLookAndFeel() != previous) {
                try { UIManager.setLookAndFeel(previous); }
                catch (UnsupportedLookAndFeelException | RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            throw new InstallationFailure(theme, failure);
        }
    }

private static boolean modernDefaultsRegistered;
static boolean install(BuiltinTheme theme) {
    requireEdt();
    if (theme == BuiltinTheme.RETRO) return MetalDefaults.install();
    if (!modernDefaultsRegistered) {
        FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes");
        modernDefaultsRegistered = true;
    }
    return theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
}

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
