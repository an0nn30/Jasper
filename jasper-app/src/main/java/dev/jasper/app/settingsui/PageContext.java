package dev.jasper.app.settingsui;

import dev.jasper.app.presentation.Composition;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.SettingsChrome;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.ui.swing.KitContext;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a page may ask of the dialog it is in.
 *
 * @param draft the dialog's pending core-setting edits
 * @param navigate selects another page by id
 * @param closeThen closes the dialog (asking about unsaved changes) and then runs the action
 * @param chooseFile asks for an existing file over the dialog: title and initial path in, choice out
 * @param changed tells the dialog a page's validity or modification changed outside the draft
 * @param surface the dialog's surface, for dialogs a page opens over it; null while the dialog is closed
 * @param kit the dialog's kit context (chrome route); read on the EDT when a page builds its view
 * @param chrome the look's Settings projection (spec 12.1): Reset inline or in the row's menu, the rows it hides, the font
 *     row's copy
 * @param composition how the look composes the app's pages (spec 12.2); only the composition roots read it
 * @param sheets asks a small form in a sheet over the dialog
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome,
                          Composition composition, Sheets sheets) {
    public PageContext {
        Objects.requireNonNull(sheets, "sheets");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(chrome, "chrome");
        Objects.requireNonNull(composition, "composition");
    }

    /** A context that asks no sheets ({@link Sheets#NONE}). */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                       Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit, SettingsChrome chrome, Composition composition) {
        this(draft, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition, Sheets.NONE);
    }

    /** A context under the default look on this host ({@link #standard()}). */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                       Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit) {
        this(draft, navigate, closeThen, chooseFile, changed, surface, kit, standard().settings(), standard().composition());
    }

    /** The default look's projections on this host, for pages and dialogs made without a presentation. */
    public static Projections standard() {
        var platform = Projections.Platform.of(System.getProperty("os.name", ""));
        return Projections.standard(new Projections.Host(platform,
            platform == Projections.Platform.MAC && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported));
    }

    /** A context whose pages build no kit view, such as one that only wraps another component. */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                       Supplier<AuxiliarySurface> surface) {
        this(draft, navigate, closeThen, chooseFile, changed, surface, PageContext::noKit);
    }

    /** A context for pages that open no dialogs of their own and build no kit view. */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed) {
        this(draft, navigate, closeThen, chooseFile, changed, () -> null);
    }

    /** The same dialog over another draft, as a plugin's own settings page keeps one. */
    public PageContext withDraft(SettingsDraft other) {
        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit, chrome, composition, sheets);
    }

    private static KitContext noKit() {
        throw new IllegalStateException("This page context has no kit context; Settings pages build their views with the dialog's");
    }
}
