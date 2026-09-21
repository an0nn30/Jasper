package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
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
    // FlatLaf appends registrations, so register this package once rather than once per switch.
    static { FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes"); }

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(BuiltinTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.label(), cause);
        }
    }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<BuiltinTheme> installer;
    private ThemeState state = ThemeState.defaults();

    public ThemeController() { this(ThemeController::install); }

    /** Installation is a boundary so failure can be exercised without constructing native windows. */
    public ThemeController(Predicate<BuiltinTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        installOrThrow(state.resolve().chrome());
    }

    public ResolvedTheme current() { requireEdt(); return state.resolve(); }
    public Appearance choice() { requireEdt(); return state.choice(); }

    public void configure(Appearance saved) { requireEdt(); apply(state.configure(Objects.requireNonNull(saved))); }
    public void selectAppearance(Appearance choice) { requireEdt(); apply(state.choose(choice)); }
    public void select(BuiltinTheme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }
    private void apply(ThemeState candidate) {
        ResolvedTheme previous = state.resolve(), next = candidate.resolve();
        boolean chromeChanged = previous.chrome() != next.chrome();
        boolean choiceChanged = state.choice() != candidate.choice();
        if (chromeChanged) installOrThrow(next.chrome());
        state = candidate;
        if (!previous.equals(next) || choiceChanged)
            for (var listener : List.copyOf(listeners)) listener.accept(next, chromeChanged);
    }

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
        try {
            if (!installer.test(theme)) throw new IllegalStateException("Could not apply theme: " + theme.label());
        } catch (RuntimeException failure) {
            // A failed setup may have installed a LAF before one of its initialization hooks failed.
            if (UIManager.getLookAndFeel() != previous) {
                try { UIManager.setLookAndFeel(previous); }
                catch (UnsupportedLookAndFeelException | RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            }
            throw new InstallationFailure(theme, failure);
        }
    }

    static boolean install(BuiltinTheme theme) {
        requireEdt();
        return switch (theme) {
            case LIGHT -> FlatLightLaf.setup();
            case DARK -> FlatDarkLaf.setup();
        };
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
