package dev.moray.app;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.swing.*;

/** Application-owned, EDT-confined theme selection and window contents. */
final class ThemeController {
    // FlatLaf appends registrations, so register this package once rather than once per switch.
    static { FlatLaf.registerCustomDefaultsSource("dev.moray.app.themes"); }

    static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(BuiltinTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.label(), cause);
        }
    }

    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Predicate<BuiltinTheme> installer;
    private ThemeState state = ThemeState.defaults();
    private BuiltinTheme latestSystem = BuiltinTheme.DARK;

    ThemeController() { this(ThemeController::install); }

    /** Installation is a boundary so failure can be exercised without constructing native windows. */
    ThemeController(Predicate<BuiltinTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        installOrThrow(state.resolve().chrome());
    }

    ResolvedTheme current() { requireEdt(); return state.resolve(); }
    Appearance choice() { requireEdt(); return state.choice(); }

    void configure(ColorsConfig colors, dev.moray.terminal.Palette loaded) {
        requireEdt(); apply(state.systemChanged(latestSystem).configure(colors, loaded));
    }
    void selectAppearance(Appearance choice) {
        requireEdt(); apply(state.systemChanged(latestSystem).choose(choice));
    }
    void select(BuiltinTheme theme) {
        Objects.requireNonNull(theme);
        selectAppearance(theme == BuiltinTheme.LIGHT ? Appearance.LIGHT : Appearance.DARK);
    }
    void systemChanged(BuiltinTheme system) {
        requireEdt(); latestSystem = Objects.requireNonNull(system);
        apply(state.systemChanged(system));
    }
    private void apply(ThemeState candidate) {
        ResolvedTheme previous = state.resolve(), next = candidate.resolve();
        boolean chromeChanged = previous.chrome() != next.chrome();
        boolean choiceChanged = state.choice() != candidate.choice();
        if (chromeChanged) installOrThrow(next.chrome());
        state = candidate;
        if (!previous.equals(next) || choiceChanged)
            for (WindowContent owner : List.copyOf(owners)) owner.applyTheme(next, chromeChanged);
    }

    void register(WindowContent owner) {
        requireEdt();
        if (owners.add(Objects.requireNonNull(owner))) owner.applyTheme(current(), true);
    }

    void unregister(WindowContent owner) { requireEdt(); owners.remove(owner); }

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
            case CLASSIC_DARK -> FlatDarkLaf.setup();
            case DARK -> FlatLaf.setup(new MorayDarkPurpleLaf());
        };
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
