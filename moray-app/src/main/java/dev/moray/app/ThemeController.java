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

    private final Set<WindowContent> owners = new LinkedHashSet<>();
    private final Predicate<BuiltinTheme> installer;
    private BuiltinTheme current = BuiltinTheme.DARK;

    ThemeController() { this(ThemeController::install); }

    /** Installation is a boundary so failure can be exercised without constructing native windows. */
    ThemeController(Predicate<BuiltinTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        installOrThrow(current);
    }

    BuiltinTheme current() { requireEdt(); return current; }

    void select(BuiltinTheme theme) {
        requireEdt(); Objects.requireNonNull(theme);
        if (theme == current) return;
        installOrThrow(theme);
        current = theme;
        for (WindowContent owner : List.copyOf(owners)) owner.applyTheme(theme);
    }

    void register(WindowContent owner) {
        requireEdt();
        if (owners.add(Objects.requireNonNull(owner))) owner.applyTheme(current);
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
            throw new IllegalStateException("Could not apply theme: " + theme.label(), failure);
        }
    }

    static boolean install(BuiltinTheme theme) {
        requireEdt();
        return theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
