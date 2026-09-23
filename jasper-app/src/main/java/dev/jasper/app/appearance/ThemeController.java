package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
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
    private final ThemeStyle style;
    private ThemeState state = ThemeState.defaults();
    private UiFontConfig uiFont = UiFontConfig.defaults();
    private final Font platformFont;
    private final float platformLabelSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font");

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
        UIManager.put("defaultFont", null);
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        installOrThrow(resolve(state).chrome());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
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
    public void configure(Appearance saved) { configure(saved, uiFont); }
    public void configure(Appearance saved, UiFontConfig font) {
        requireEdt(); apply(state.configure(Objects.requireNonNull(saved)), Objects.requireNonNull(font));
    }
    public void select(BuiltinTheme theme) { selectAppearance(Objects.requireNonNull(theme).appearance()); }
    private void apply(ThemeState candidate) { apply(candidate, uiFont); }
    private void apply(ThemeState candidate, UiFontConfig font) {
        ResolvedTheme previous = resolve(state), next = resolve(candidate);
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean choiceChanged = style == ThemeStyle.MODERN && state.choice() != candidate.choice();
        if (chromeChanged) {
            installFontDefaults(font);
            try {
                installOrThrow(next.chrome());
                if (style == ThemeStyle.RETRO) MetalDefaults.configureFonts(resolveFont(font), platformLabelSize);
            }
            catch (RuntimeException failure) {
                installFontDefaults(uiFont);
                if (style == ThemeStyle.RETRO) MetalDefaults.configureFonts(resolveFont(uiFont), platformLabelSize);
                throw failure;
            }
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
        if (style == ThemeStyle.RETRO) return;
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
