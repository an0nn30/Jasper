package dev.jasper.app.settingsui;

import dev.jasper.app.windows.AuxiliarySurface;
import java.nio.file.Path;
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
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface) {
    /** A context for pages that open no dialogs of their own. */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed) {
        this(draft, navigate, closeThen, chooseFile, changed, () -> null);
    }
}
