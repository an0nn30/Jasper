package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.windows.AuxiliarySurface;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What Settings › Appearance's Imported themes group needs from the application. The folder's blocking file work runs
 * on {@code worker}; every result comes back through {@code ui}, which runs it on the EDT, where the other hooks run.
 *
 * @param folder the themes folder, which checks, copies in and deletes theme files
 * @param imported the imported themes now, in the dropdowns' order
 * @param chooseFile asks for a {@code .json} file over the Settings dialog's surface (null while it is closed); empty when cancelled
 * @param confirm asks a question over the Settings dialog's surface and runs its answer when the user agrees
 * @param save writes settings at once, outside Apply
 * @param reload reads config.toml and the themes folder again
 * @param worker runs the blocking file work
 * @param ui runs a result on the EDT
 */
public record ThemeImports(ThemeFolder folder, Supplier<List<Imported>> imported,
                           Function<AuxiliarySurface, Optional<Path>> chooseFile, BiConsumer<AuxiliarySurface, Question> confirm,
                           Function<SettingsEdit, CompletableFuture<?>> save, Supplier<CompletableFuture<?>> reload,
                           Executor worker, Executor ui) {
    public ThemeImports {
        Objects.requireNonNull(folder, "folder"); Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(chooseFile, "chooseFile"); Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(save, "save"); Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(worker, "worker"); Objects.requireNonNull(ui, "ui");
    }

    /** An imported theme as the group lists it: its id, its label and what it offers, such as "UI theme · Dark". */
    public record Imported(String id, String label, String offers) {
        public Imported { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(offers, "offers"); }
    }

    /** A question with one consequence and a yes: the dialog's title, the question, what happens, and the yes button's label. */
    public record Question(String title, String question, String consequence, String yes, Runnable onYes) {
        public Question {
            Objects.requireNonNull(title, "title"); Objects.requireNonNull(question, "question");
            Objects.requireNonNull(consequence, "consequence"); Objects.requireNonNull(yes, "yes"); Objects.requireNonNull(onYes, "onYes");
        }
    }
}
