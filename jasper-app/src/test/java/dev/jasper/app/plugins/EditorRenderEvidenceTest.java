package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static dev.jasper.app.plugins.EditorEnterBaselineTest.require;
import static dev.jasper.app.plugins.StagedRemoteHost.awaitNamed;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-host, dark and light pixel evidence of the migrated Editor dialogs (Plan 2 Task 9a; Task 7 ruling 2 and the 8a
 * precedent): the staged Editor runs in the application's plugin host ({@link StagedEditorHost}) and its question sheets,
 * Go to Line (valid and invalid), Language picker (all, filtered and empty) and Saving dialog render through the host
 * translation with synthetic text. Each capture waits for its named content to mount and must not be a uniform frame.
 * Output goes to build/theme-migration-evidence/editor-hosted; the frozen before images are untouched.
 */
class EditorRenderEvidenceTest {
    static final String FAMILY = "editor-hosted";
    @TempDir Path root;

    @Test @Timeout(value = 150, unit = TimeUnit.SECONDS)
    void capturesTheStagedEditorDialogsInTheRealHostDarkAndLight() throws Exception {
        for (String theme : List.of("dark", "light")) capture(theme, root.resolve(theme));
    }

    private void capture(String theme, Path data) throws Exception {
        var controller = onEdtValue(dev.jasper.app.appearance.ThemeController::new);
        onEdt(() -> controller.configure(theme.equals("dark") ? dev.jasper.app.appearance.Theme.DARK : dev.jasper.app.appearance.Theme.JASPER_LIGHT));
        try (var editor = new StagedEditorHost(data, theme.equals("dark"))) {
            onEdt(() -> editor.ask("Unsaved changes", "Save changes to example.txt?", List.of("Save", "Don't Save", "Cancel"), false, answer -> { }));
            var unsaved = editor.awaitSurface("Unsaved changes");
            awaitNamed(unsaved, "question.choice.0");
            save(theme, "unsaved-question", unsaved, 540, 200);
            onEdt(unsaved::requestClose);

            onEdt(() -> editor.ask("File changed", "example.txt changed on disk since you opened it.", List.of("Overwrite", "Reload", "Cancel"), true, answer -> { }));
            var changed = editor.awaitSurface("File changed");
            awaitNamed(changed, "question.choice.2");
            save(theme, "changed-question", changed, 540, 200);
            onEdt(changed::requestClose);

            onEdt(() -> editor.goToLine(1, 10, line -> { }));
            var line = editor.awaitSurface("Go to Line");
            awaitNamed(line, "go-to-line.go");
            save(theme, "go-to-line", line, 500, 220);
            onEdt(() -> {
                var field = (JTextComponent) require(line, "go-to-line.line.input");
                field.setText("20");
                StagedEditorHost.enter(field);
            });
            StagedVaultHost.await(() -> StagedRemoteHost.labels(line.holder()).contains("Enter a line from 1 to 10"), "the inline error");
            save(theme, "go-to-line-invalid", line, 500, 220);
            onEdt(line::requestClose);

            onEdt(() -> editor.language("text/python", style -> { }));
            var language = editor.awaitSurface("Language");
            awaitNamed(language, "language.list");
            save(theme, "language", language, 420, 520);
            onEdt(() -> ((JTextComponent) require(language, "language.filter.input")).setText("ja"));
            StagedVaultHost.await(() -> EditorEnterBaselineTest.rowNames(language).size() < 8, "the filtered list");
            save(theme, "language-filtered", language, 420, 520);
            onEdt(() -> ((JTextComponent) require(language, "language.filter.input")).setText("no-such-language-xyz"));
            StagedVaultHost.await(() -> EditorEnterBaselineTest.rowNames(language).isEmpty(), "the empty filtered list");
            save(theme, "language-empty", language, 420, 520);
            onEdt(language::requestClose);

            var saves = new CompletableFuture<Boolean>();
            var waited = editor.saveWait("Saving 3 files…", saves);
            var saving = editor.awaitSurface("Saving");
            awaitNamed(saving, "saving.progress");
            save(theme, "saving", saving, 460, 200);
            onEdt(() -> saves.complete(true));
            assertThat(waited.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(editor.problems).isEmpty();
        } finally {
            onEdt(() -> { controller.close(); new dev.jasper.app.appearance.ThemeController(); });
        }
    }

    /** Captures and checks the frame shows content: a blank, uniform frame fails. */
    static void save(String theme, String state, AuxiliarySurface surface, int width, int height) throws Exception {
        JComponent component = surface.holder();
        onEdt(() -> ThemeMigrationCapture.save(FAMILY, theme, state, component, width, height));
        var image = ImageIO.read(Path.of("build/theme-migration-evidence", FAMILY, theme, state + ".png").toFile());
        var colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y += 2) for (int x = 0; x < image.getWidth(); x += 2) colours.add(image.getRGB(x, y));
        assertThat(colours.size()).as("%s/%s shows content, not a uniform frame", theme, state).isGreaterThanOrEqualTo(8);
    }
}
