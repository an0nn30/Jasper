package dev.jasper.app.settingsui;

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
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface, Supplier<KitContext> kit) {
    public PageContext {
        Objects.requireNonNull(kit, "kit");
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
        return new PageContext(other, navigate, closeThen, chooseFile, changed, surface, kit);
    }

    private static KitContext noKit() {
        throw new IllegalStateException("This page context has no kit context; Settings pages build their views with the dialog's");
    }
}
