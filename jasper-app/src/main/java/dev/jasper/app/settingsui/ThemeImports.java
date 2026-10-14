package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeFolder;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.windows.AuxiliarySurface;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the theme gears on Settings › Appearance need from the application. The folder's blocking file work runs on
 * {@code worker}; every result comes back through {@code ui}, which runs it on the EDT, where the other hooks run.
 *
 * @param folder the themes folder, which checks, copies in and deletes theme files
 * @param chooseFile asks for a {@code .json} file over the Settings dialog's surface (null while it is closed); empty when cancelled
 * @param confirm asks a question over the Settings dialog's surface and runs its answer when the user agrees
 * @param inform shows a notice over the Settings dialog's surface, which the user acknowledges
 * @param save writes settings at once, outside Apply
 * @param reload reads config.toml and the themes folder again
 * @param worker runs the blocking file work
 * @param ui runs a result on the EDT
 */
public record ThemeImports(ThemeFolder folder, Function<AuxiliarySurface, Optional<Path>> chooseFile,
                           BiConsumer<AuxiliarySurface, Question> confirm, BiConsumer<AuxiliarySurface, Notice> inform,
                           Function<SettingsEdit, CompletableFuture<?>> save, Supplier<CompletableFuture<?>> reload,
                           Executor worker, Executor ui) {
    public ThemeImports {
        Objects.requireNonNull(folder, "folder"); Objects.requireNonNull(chooseFile, "chooseFile");
        Objects.requireNonNull(confirm, "confirm"); Objects.requireNonNull(inform, "inform");
        Objects.requireNonNull(save, "save"); Objects.requireNonNull(reload, "reload");
        Objects.requireNonNull(worker, "worker"); Objects.requireNonNull(ui, "ui");
    }

    /**
     * A question with one consequence and a yes.
     *
     * @param title the dialog's title
     * @param question the question
     * @param consequence what happens on yes
     * @param yes the yes button's label
     * @param onYes runs when the user agrees
     */
    public record Question(String title, String question, String consequence, String yes, Runnable onYes) {
        public Question {
            Objects.requireNonNull(title, "title"); Objects.requireNonNull(question, "question");
            Objects.requireNonNull(consequence, "consequence"); Objects.requireNonNull(yes, "yes"); Objects.requireNonNull(onYes, "onYes");
        }
    }

    /**
     * What happened, for the user to acknowledge.
     *
     * @param title the dialog's title and the notice's heading, such as "Could not import dracula.json"
     * @param message why, or what to do next
     */
    public record Notice(String title, String message) {
        public Notice { Objects.requireNonNull(title, "title"); Objects.requireNonNull(message, "message"); }
    }
}
