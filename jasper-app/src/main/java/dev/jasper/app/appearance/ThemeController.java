package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.config.UiFontConfig;
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

/** Application-owned, EDT-confined theme selection and value subscriptions. */
public final class ThemeController implements AutoCloseable {
    private AppearanceBindings appearance;
    private boolean applying;

    public static final class InstallationFailure extends IllegalStateException {
        InstallationFailure(Theme theme, RuntimeException cause) {
            super("Could not apply theme: " + theme.name(), cause);
        }
    }

    private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
    private final Predicate<Theme> installer;
    private ThemeState state = ThemeState.defaults();
    private ResolvedTheme current;
    private UiFontConfig uiFont = UiFontConfig.defaults();
    private final Font platformFont;
    private final float platformLabelSize;
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "Button.font");

    public ThemeController() { this(Theme.DARK); }
    public ThemeController(Theme saved) { this(saved, ThemeManager::install); }
    public ThemeController(Predicate<Theme> installer) { this(Theme.DARK, installer); }
    ThemeController(Theme saved, Predicate<Theme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        this.state = ThemeState.defaults().configure(Objects.requireNonNull(saved));
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("defaultFont", null);
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        current = state.resolve();
        appearance = new AppearanceBindings(current.pair(), ThemeController::reportFailure, () -> true);
        new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> installOrThrow(state.choice()), ignored -> {}).apply(current.pair());
        WorkspaceDefaults.publish(current.workspace());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }
    public ResolvedTheme current() { requireEdt(); return current; }
    /** The configured theme. */
    public Theme choice() { requireEdt(); return state.choice(); }
    public WorkspaceThemeChoice workspaceChoice() { requireEdt(); return state.workspaceChoice(); }
    /** Sources that must survive cache pruning, including an active converted workspace. */
    public List<Theme> activeThemes() {
        requireEdt();
        return state.workspace() == null ? List.of(state.choice()) : List.of(state.choice(), state.workspace());
    }
    /** Changes only workspace values, preserving the active chrome and interface typography. */
    public void configureWorkspace(WorkspaceThemeChoice choice, Theme workspaceOrNull) {
        requireEdt(); configure(state.choice(), choice, workspaceOrNull, uiFont);
    }
    public void configureTerminal(TerminalColors terminal) {
        requireEdt(); configure(state.choice(), terminal, uiFont);
    }
    public void configure(Theme saved) { requireEdt(); configure(saved, uiFont); }
    public void configure(Theme saved, UiFontConfig font) {
        requireEdt(); configure(saved, state.workspaceChoice(), state.workspace(), font);
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
        apply(new ThemeState(saved, choice, workspaceOrNull), Objects.requireNonNull(font));
    }
    private void apply(ThemeState candidate, UiFontConfig font) {
        ResolvedTheme previous = current, next = candidate.resolve();
        boolean chromeChanged = state.choice() != candidate.choice() || !uiFont.equals(font);
        boolean choiceChanged = !state.workspaceChoice().equals(candidate.workspaceChoice());
        if (applying) throw new IllegalStateException("Theme configuration cannot re-enter");
        applying = true;
        try {
            new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> {
                if (chromeChanged) {
                    installFontDefaults(font);
                    installOrThrow(candidate.choice());
                }
                UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
                WorkspaceDefaults.publish(next.workspace());
                uiFont = font;
                state = candidate;
                current = next;
            }, ignored -> {}).apply(next.pair());
            if (chromeChanged || !previous.equals(next) || choiceChanged) {
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

    private void installOrThrow(Theme theme) {
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
