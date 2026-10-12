package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.TerminalScheme;
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
 * Application-owned, EDT-confined theme selection and value subscriptions. The application shows Slate Steel: one
 * variant for chrome and workspace, and the terminal scheme's palette. The {@link Theme}-based constructors and
 * overloads show a legacy fixture theme for tests until the legacy themes are removed.
 */
public final class ThemeController implements AutoCloseable {
    private AppearanceBindings appearance;
    private boolean applying;
    private boolean animationsEnabled = true;
    public boolean animationsEnabled() { requireEdt(); return animationsEnabled; }
    public void configureAnimations(boolean enabled) { requireEdt(); animationsEnabled = enabled; }

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(GeneratedTheme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.name() + (theme.name().equals("Slate Steel") ? (theme.dark() ? " Dark" : " Light") : ""), cause);
        }
    }

    /** The Slate Steel selection: the UI variant and the terminal scheme chosen. */
    private record Slate(boolean dark, TerminalSchemeChoice scheme) { }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<GeneratedTheme> installer;
    /** The legacy fixture selection, or null while Slate Steel is shown. */
    private ThemeState state;
    /** The Slate Steel selection, or null while a legacy fixture theme is shown. */
    private Slate slate;
    private ResolvedTheme current;
    private UiFontConfig uiFont = UiFontConfig.defaults();
    private final Font platformFont;
    private final float platformLabelSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "Button.font");

    /**
     * Start-up selection: Slate Steel in {@code dark}, or Dark when that cannot be installed. A failed attempt is rolled
     * back completely, and the fallback is installed before this returns, so nothing can read the installed snapshot
     * (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure propagates.
     */
    public static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, scheme, ThemeManager::install, fallingBack);
    }
    static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(dark, scheme, installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            return new ThemeController(true, scheme, installer);
        }
    }

    /**
     * Legacy start-up for fixture themes: the saved theme, or Jasper Dark when the saved one cannot be installed. Used
     * by tests until the legacy themes are removed.
     */
    public static ThemeController startup(Theme saved, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(saved, ThemeManager::install, fallingBack);
    }
    static ThemeController startup(Theme saved, Predicate<GeneratedTheme> installer, java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(saved, installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            return new ThemeController(Theme.DARK, installer);
        }
    }

    public ThemeController() { this(Theme.DARK); }
    public ThemeController(Theme saved) { this(saved, ThemeManager::install); }
    public ThemeController(Predicate<GeneratedTheme> installer) { this(Theme.DARK, installer); }
    ThemeController(Theme saved, Predicate<GeneratedTheme> installer) {
        this(ThemeState.defaults().configure(Objects.requireNonNull(saved)), null, installer);
    }
    /** Slate Steel in {@code dark} with {@code scheme}. */
    public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeManager::install); }
    ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
        this(null, new Slate(dark, Objects.requireNonNull(scheme)), installer);
    }
    private ThemeController(ThemeState legacy, Slate slate, Predicate<GeneratedTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        this.state = legacy;
        this.slate = slate;
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("defaultFont", null);
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        current = resolve(legacy, slate);
        appearance = new AppearanceBindings(current.pair(), ThemeController::reportFailure, () -> animationsEnabled);
        new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> installOrThrow(current.chrome()), ignored -> {}).apply(current.pair());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }

    /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} the variant's own. */
    public static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice) {
        return choice == TerminalSchemeChoice.MATCH ? TerminalScheme.matching(dark) : TerminalScheme.of(choice.key()).orElseThrow();
    }
    private static ResolvedTheme resolve(ThemeState legacy, Slate slate) {
        return slate != null ? ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme())) : legacy.resolve();
    }

    public ResolvedTheme current() { requireEdt(); return current; }
    /** The configured legacy fixture theme; null while Slate Steel is shown. */
    public Theme choice() { requireEdt(); return state == null ? null : state.choice(); }
    public WorkspaceThemeChoice workspaceChoice() { requireEdt(); return state == null ? WorkspaceThemeChoice.MATCH : state.workspaceChoice(); }
    /** Legacy sources that must survive cache pruning, including an active converted workspace; none with Slate Steel. */
    public List<Theme> activeThemes() {
        requireEdt();
        if (state == null) return List.of();
        return state.workspace() == null ? List.of(state.choice()) : List.of(state.choice(), state.workspace());
    }
    /** The terminal scheme chosen; {@code MATCH} while a legacy fixture theme is shown. */
    public TerminalSchemeChoice schemeChoice() { requireEdt(); return slate == null ? TerminalSchemeChoice.MATCH : slate.scheme(); }

    /** Slate Steel in {@code dark} with {@code scheme} and {@code font}. */
    public void configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font) {
        requireEdt();
        var next = new Slate(dark, Objects.requireNonNull(scheme));
        apply(null, next, resolve(null, next), Objects.requireNonNull(font));
    }
    public void configure(boolean dark, TerminalSchemeChoice scheme) { requireEdt(); configure(dark, scheme, uiFont); }
    /** Changes only the terminal scheme; the UI variant and font stay. */
    public void configureScheme(TerminalSchemeChoice scheme) {
        requireEdt(); configure(slate != null ? slate.dark() : current.chrome().dark(), scheme, uiFont);
    }

    private ThemeState legacy() { return state == null ? ThemeState.defaults() : state; }
    /** Changes only workspace values, preserving the active chrome and interface typography. */
    public void configureWorkspace(WorkspaceThemeChoice choice, Theme workspaceOrNull) {
        requireEdt(); configure(legacy().choice(), choice, workspaceOrNull, uiFont);
    }
    public void configureTerminal(TerminalColors terminal) {
        requireEdt(); configure(legacy().choice(), terminal, uiFont);
    }
    public void configure(Theme saved) { requireEdt(); configure(saved, uiFont); }
    public void configure(Theme saved, UiFontConfig font) {
        requireEdt(); configure(saved, legacy().workspaceChoice(), legacy().workspace(), font);
    }
    /** Compatibility for built-in terminal-colour menu choices. */
    public void configure(Theme saved, TerminalColors terminal, UiFontConfig font) {
        requireEdt(); Objects.requireNonNull(terminal);
        Theme workspace = terminal.equals(TerminalColors.LIGHT) ? Theme.JASPER_LIGHT
            : terminal.equals(TerminalColors.DARK) ? Theme.DARK : null;
        configure(saved, workspace == null ? WorkspaceThemeChoice.parse(terminal.name())
            : new WorkspaceThemeChoice(workspace.name()), workspace, font);
    }
    /** Resolves both slots before installing chrome, then publishes both only after successful installation. */
    public void configure(Theme saved, WorkspaceThemeChoice choice, Theme workspaceOrNull, UiFontConfig font) {
        requireEdt();
        var next = new ThemeState(saved, choice, workspaceOrNull);
        apply(next, null, next.resolve(), Objects.requireNonNull(font));
    }
    private void apply(ThemeState nextLegacy, Slate nextSlate, ResolvedTheme next, UiFontConfig font) {
        ResolvedTheme previous = current;
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean selectionChanged = !Objects.equals(state, nextLegacy) || !Objects.equals(slate, nextSlate);
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
                state = nextLegacy;
                slate = nextSlate;
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
