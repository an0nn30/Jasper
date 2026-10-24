package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import java.awt.Font;
import javax.swing.plaf.FontUIResource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import dev.jasper.app.lifecycle.Subscription;
import java.util.function.Predicate;
import javax.swing.*;

/**
 * Application-owned, EDT-confined theme selection and value subscriptions. The shown theme's variant serves chrome and
 * workspace, and the terminal scheme's palette the terminal panes.
 */
public final class ThemeController implements AutoCloseable {
    private AppearanceBindings appearance;
    private boolean applying;
    private boolean animationsEnabled = true;
    public boolean animationsEnabled() { requireEdt(); return animationsEnabled; }
    public void configureAnimations(boolean enabled) { requireEdt(); animationsEnabled = enabled; }

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(GeneratedTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.name() + (theme.dark() ? " Dark" : " Light"), cause);
        }
    }

    /** What is shown: the appearance in effect and the selection. */
    private record Shown(boolean dark, ThemeSelection selection) { }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<GeneratedTheme> installer;
    /** The kit every install of this controller uses and every context it hands out carries. */
    private final Kit kit;
    private Shown shown;
    private ResolvedTheme current;
    private UiFontConfig uiFont = UiFontConfig.defaults();
    /** The look's standard UI font ({@code ui.font.family = "system"}): the kit's own face when registered, else FlatLaf's. */
    private final Font platformFont;
    private final float platformLabelSize;
    /** FlatLaf's desktop font size, kept apart from the look's body size for scale derivation. */
    private final float platformScaleSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "Button.font");

    /**
     * Start-up selection under {@code kit}: the controller of
     * {@link #start(boolean, ThemeSelection, Kit, java.util.function.Consumer)}, without the step that installed. A
     * failed attempt is rolled back completely, and the fallback is installed before this returns, so nothing can read
     * the installed snapshot (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure
     * propagates.
     */
    public static ThemeController startup(boolean dark, ThemeSelection selection, Kit kit,
                                          java.util.function.Consumer<InstallationFailure> fallingBack) {
        return start(dark, selection, kit, fallingBack).controller();
    }

    /** Which of spec 4.7's attempts installed. */
    public enum Step {
        /** The selection under the chosen kit. */
        CHOSEN,
        /** The selection under the default kit: the chosen kit is blamed. */
        DEFAULT_KIT,
        /** Slate Cobalt Dark under the chosen kit: the theme was at fault. */
        FALLBACK_THEME,
        /** Slate Cobalt Dark under the default kit: the chosen kit is blamed too. */
        FALLBACK_THEME_DEFAULT_KIT;

        /** Whether this step means the chosen kit could not be installed. */
        public boolean blamesKit() { return this == DEFAULT_KIT || this == FALLBACK_THEME_DEFAULT_KIT; }
    }

    /**
     * A controller made at start and the step that installed it.
     *
     * @param controller the controller
     * @param step which attempt installed
     */
    public record Started(ThemeController controller, Step step) { }

    /**
     * Start-up under {@code kit} with spec 4.7's fallback: the selection under {@code kit}, then under the default kit,
     * then Slate Cobalt Dark under {@code kit}, then under the default kit; with the default kit only the first and third.
     * Each failure but the last is given to {@code fallingBack}; the last propagates. Every later install of the controller
     * uses the kit of the step that installed.
     */
    public static Started start(boolean dark, ThemeSelection selection, Kit kit, java.util.function.Consumer<InstallationFailure> fallingBack) {
        Objects.requireNonNull(kit);
        return start(dark, selection, kit, INSTALLERS, fallingBack);
    }

    /** The production installers: a theme installed into Swing under a kit. Applied on the EDT. */
    public static final java.util.function.Function<Kit, Predicate<GeneratedTheme>> INSTALLERS = chosen -> theme -> ThemeManager.install(theme, chosen);

    /**
     * {@link #start(boolean, ThemeSelection, Kit, java.util.function.Consumer)} with {@code installers}, which tests replace
     * to fail under a chosen kit (spec 4.7's fallback tests).
     */
    public static Started start(boolean dark, ThemeSelection selection, Kit kit,
                         java.util.function.Function<Kit, Predicate<GeneratedTheme>> installers,
                         java.util.function.Consumer<InstallationFailure> fallingBack) {
        record Attempt(Shown shown, Kit kit, Step step) { }
        var cobalt = JasperTheme.bundled();
        var chosen = new Shown(dark, selection);
        var fallback = new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme()));
        boolean other = !kit.equals(Kit.DEFAULT);
        var attempts = new java.util.ArrayList<Attempt>();
        attempts.add(new Attempt(chosen, kit, Step.CHOSEN));
        if (other) attempts.add(new Attempt(chosen, Kit.DEFAULT, Step.DEFAULT_KIT));
        attempts.add(new Attempt(fallback, kit, Step.FALLBACK_THEME));
        if (other) attempts.add(new Attempt(fallback, Kit.DEFAULT, Step.FALLBACK_THEME_DEFAULT_KIT));
        for (int i = 0; ; i++) {
            var attempt = attempts.get(i);
            try { return new Started(new ThemeController(attempt.shown(), attempt.kit(), installers.apply(attempt.kit())), attempt.step()); }
            catch (InstallationFailure failure) {
                if (i == attempts.size() - 1) throw failure;
                fallingBack.accept(failure);
            }
        }
    }
    static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, ThemeSelection.slateCobalt(scheme), installer, fallingBack);
    }
    static ThemeController startup(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(new Shown(dark, selection), Kit.DEFAULT, installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), Kit.DEFAULT, installer);
        }
    }

    /** Slate Cobalt in {@code dark} with {@code scheme}, under the default kit. */
    public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeController::installDefault); }
    ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, ThemeSelection.slateCobalt(Objects.requireNonNull(scheme))), Kit.DEFAULT, installer);
    }
    /** {@code selection} in {@code dark}, under the default kit. */
    public ThemeController(boolean dark, ThemeSelection selection) {
        this(new Shown(dark, Objects.requireNonNull(selection)), Kit.DEFAULT, ThemeController::installDefault);
    }
    ThemeController(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, Objects.requireNonNull(selection)), Kit.DEFAULT, installer);
    }
    /** The installer of the controllers made without a kit. */
    private static boolean installDefault(GeneratedTheme theme) { return ThemeManager.install(theme, Kit.DEFAULT); }
    private ThemeController(Shown shown, Kit kit, Predicate<GeneratedTheme> installer) {
        requireEdt();
        this.kit = Objects.requireNonNull(kit);
        this.installer = Objects.requireNonNull(installer);
        this.shown = shown;
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        // Under a kit with a registered UI face of its own, FlatLaf's component text uses it too (spec 5.6:
        // FlatLafInstaller), while defaultFont stays FlatLaf's, so neither FlatLaf's user scale nor the kit's roles take the
        // look's face for a user font size.
        UIManager.put("defaultFont", null);
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        current = ResolvedTheme.of(shown.dark(), shown.selection());
        appearance = new AppearanceBindings(current.pair(), kit, ThemeController::reportFailure, () -> animationsEnabled);
        new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> installOrThrow(current.chrome()), ignored -> {}).apply(current.pair());
        Font look = lookFace(kit);
        Font desktop = UIManager.getFont("defaultFont");
        platformScaleSize = (desktop != null ? desktop : UIManager.getFont("Label.font")).getSize2D();
        platformFont = look != null ? new FontUIResource(look.deriveFont(com.formdev.flatlaf.util.UIScale.scale(kit.type().body())))
            : UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }

    public ResolvedTheme current() { requireEdt(); return current; }
    /** The kit this controller installs under and its contexts carry. */
    public Kit kit() { return kit; }
    /** The terminal scheme chosen. */
    public TerminalSchemeChoice schemeChoice() { requireEdt(); return shown.selection().scheme(); }
    /** The themes picked and the library they come from. */
    public ThemeSelection selection() { requireEdt(); return shown.selection(); }
    /** The installed themes the selection comes from. */
    public ThemeLibrary library() { requireEdt(); return shown.selection().library(); }

    /** {@code selection} in {@code dark} with {@code font}. */
    public void configure(boolean dark, ThemeSelection selection, UiFontConfig font) {
        requireEdt();
        var next = new Shown(dark, Objects.requireNonNull(selection));
        apply(next, ResolvedTheme.of(dark, selection), Objects.requireNonNull(font));
    }
    /** The current themes in {@code dark} with {@code scheme} and {@code font}. */
    public void configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font) {
        requireEdt(); configure(dark, shown.selection().withScheme(Objects.requireNonNull(scheme)), font);
    }
    public void configure(boolean dark, TerminalSchemeChoice scheme) { requireEdt(); configure(dark, scheme, uiFont); }
    /** Changes only the terminal scheme; the appearance, themes and font stay. */
    public void configureScheme(TerminalSchemeChoice scheme) {
        requireEdt(); configure(shown.dark(), scheme, uiFont);
    }

    private void apply(Shown nextShown, ResolvedTheme next, UiFontConfig font) {
        ResolvedTheme previous = current;
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean selectionChanged = !Objects.equals(shown, nextShown);
        if (applying) throw new IllegalStateException("Theme configuration cannot re-enter");
        applying = true;
        try {
            new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> {
                if (chromeChanged) {
                    installFontDefaults(font);
                    installOrThrow(next.chrome());
                }
                UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
                uiFont = font;
                shown = nextShown;
                current = next;
            }, ignored -> {}).apply(next.pair());
            if (chromeChanged || !previous.equals(next) || selectionChanged) {
                for (var listener : List.copyOf(listeners)) {
                    if (listeners.contains(listener)) notifyListener(listener, next, chromeChanged);
                }
            }
        } finally { applying = false; }
    }

    /** Shared module contexts for app-native views; lifetime is the controller's. */
    public AppearanceBindings appearance() { requireEdt(); return appearance; }
    @Override public void close() { requireEdt(); listeners.clear(); appearance.close(); }
    private static void reportFailure(Throwable failure) {
        java.util.logging.Logger.getLogger(ThemeController.class.getName()).log(java.util.logging.Level.WARNING,
            "Theme subscriber failed", failure);
    }
    private static void notifyListener(BiConsumer<ResolvedTheme, Boolean> listener, ResolvedTheme value, boolean chrome) {
        try { listener.accept(value, chrome); } catch (Throwable failure) { reportFailure(failure); }
    }

    /** {@code kit}'s regular UI face when the kit names one and it is registered; else null. */
    private static Font lookFace(Kit kit) {
        String face = kit.faces().uiFace(400);
        if (face.isEmpty()) return null;
        Font font = new Font(face, Font.PLAIN, 1);
        return font.getFontName(java.util.Locale.ROOT).equals(face) ? font : null;
    }

    private FontUIResource resolveFont(UiFontConfig choice) {
        Font family = choice.family().equalsIgnoreCase("system") ? platformFont : new Font(choice.family(), Font.PLAIN, 13);
        if (family.getFamily().equals(Font.DIALOG) && !choice.family().equalsIgnoreCase(Font.DIALOG)) family = platformFont;
        // Retain the platform's title/menu offset relative to ordinary form text.
        float size = choice.size() == 0 ? platformScaleSize
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
        notifyListener(listener, current(), true);
        return new Subscription(() -> { requireEdt(); listeners.remove(listener); });
    }

    private void installOrThrow(GeneratedTheme theme) {
        try {
            if (!installer.test(theme)) throw new IllegalStateException("Could not apply theme: " + theme.name());
        } catch (RuntimeException failure) {
            throw new InstallationFailure(theme, failure);
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
