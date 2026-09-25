package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.UiFontConfig;
import java.awt.Font;
import javax.swing.plaf.FontUIResource;
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
    private ThemeState state = ThemeState.defaults();
    private UiFontConfig uiFont = UiFontConfig.defaults();
    private final Font platformFont;
    private final float platformLabelSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font");

    public ThemeController() { this(Appearance.DARK); }
    public ThemeController(Appearance saved) { this(saved, ThemeController::install); }
    public ThemeController(Predicate<BuiltinTheme> installer) { this(Appearance.DARK, installer); }
    ThemeController(Appearance saved, Predicate<BuiltinTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        this.state = ThemeState.defaults().configure(Objects.requireNonNull(saved));
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        UIManager.put("defaultFont", null);
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        installOrThrow(state.resolve().chrome());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }
    public ResolvedTheme current() { requireEdt(); return state.resolve(); }
    public Appearance choice() { requireEdt(); return state.choice(); }
    /** The effective terminal colours. */
    public TerminalColors terminalColors() { requireEdt(); return state.terminalChoice(); }
    public void selectAppearance(Appearance choice) {
        requireEdt(); apply(state.choose(Objects.requireNonNull(choice)));
    }
    /** A temporary View choice for every window. */
    public void selectTerminalColors(TerminalColors choice) {
        requireEdt(); apply(state.chooseTerminal(Objects.requireNonNull(choice)));
    }
    public void configure(Appearance saved) { configure(saved, uiFont); }
    public void configure(Appearance saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
    public void configure(Appearance saved, TerminalColors terminal, UiFontConfig font) {
        requireEdt();
        apply(state.configure(Objects.requireNonNull(saved)).configureTerminal(Objects.requireNonNull(terminal)),
            Objects.requireNonNull(font));
    }
    public void select(BuiltinTheme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }
    private void apply(ThemeState candidate) { apply(candidate, uiFont); }
    private void apply(ThemeState candidate, UiFontConfig font) {
        ResolvedTheme previous = state.resolve(), next = candidate.resolve();
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean choiceChanged = state.choice() != candidate.choice() || state.terminalChoice() != candidate.terminalChoice();
        if (chromeChanged) {
            installFontDefaults(font);
            try { installOrThrow(next.chrome()); }
            catch (RuntimeException failure) { installFontDefaults(uiFont); throw failure; }
        }
        uiFont = font;
        UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
        state = candidate;
        if (chromeChanged || !previous.equals(next) || choiceChanged)
            for (var listener : List.copyOf(listeners)) listener.accept(next, chromeChanged);
    }

    private FontUIResource resolveFont(UiFontConfig choice) {
        Font family = choice.family().equalsIgnoreCase("system") ? platformFont : new Font(choice.family(), Font.PLAIN, 13);
        if (family.getFamily().equals(Font.DIALOG) && !choice.family().equalsIgnoreCase(Font.DIALOG)) family = platformFont;
        // Retain the platform's title/menu offset relative to ordinary form text.
        float size = choice.size() == 0 ? platformFont.getSize2D()
            : choice.size() + platformFont.getSize2D() - platformLabelSize;
        return new FontUIResource(family.deriveFont(Font.PLAIN, size));
    }

    private void installFontDefaults(UiFontConfig choice) {
        boolean defaults = choice.equals(UiFontConfig.defaults());
        Font family = resolveFont(choice);
        UIManager.put("defaultFont", defaults ? null : family);
        // FlatLaf scales relative offsets with the UI scale. Explicit base-control fonts preserve
        // exact configured point sizes, including fractions, without changing unset defaults.
        FontUIResource form = new FontUIResource(family.deriveFont(choice.size() == 0 ? platformLabelSize : choice.size()));
        for (String key : FORM_FONTS) UIManager.put(key, defaults ? null : form);
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

    private static boolean defaultsRegistered;
    static boolean install(BuiltinTheme theme) {
        requireEdt();
        if (!defaultsRegistered) {
            FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes");
            defaultsRegistered = true;
        }
        return theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
