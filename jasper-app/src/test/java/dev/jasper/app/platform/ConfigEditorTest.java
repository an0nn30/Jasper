package dev.jasper.app.platform;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigEditorTest {
    @Test void fallsBackFromEditToOpenThenRevealWithoutChangingThePath() {
        var calls = new ArrayList<String>();
        Path file = Path.of("/tmp/settings.toml");
        var editor = new ConfigEditor(path -> { calls.add("edit:" + path); throw new IllegalStateException(); },
            path -> { calls.add("open:" + path); throw new IllegalStateException(); }, path -> calls.add("reveal:" + path));
        editor.open(file);
        assertThat(calls).containsExactly("edit:" + file, "open:" + file, "reveal:" + file);
    }
    @Test void successfulEditStopsFallbackAndTotalFailureIsReported() {
        Path file = Path.of("/tmp/settings.toml");
        var opened = new ArrayList<Path>();
        new ConfigEditor(opened::add, path -> { throw new AssertionError("unexpected open"); },
            path -> { throw new AssertionError("unexpected reveal"); }).open(file);
        assertThat(opened).containsExactly(file);
        var broken = new ConfigEditor(path -> { throw new IllegalStateException("edit"); },
            path -> { throw new IllegalStateException("open"); }, path -> { throw new IllegalStateException("reveal"); });
        assertThatThrownBy(() -> broken.open(file)).isInstanceOf(IllegalStateException.class).hasMessageContaining(file.toString());
    }
    @Test void openFolderOpensTheFolderItselfAndRevealsItOnlyAsAFallback() {
        Path folder = Path.of("/tmp/logs");
        var calls = new ArrayList<String>();
        new ConfigEditor(path -> calls.add("edit:" + path), path -> calls.add("open:" + path), path -> calls.add("reveal:" + path))
            .openFolder(folder);
        assertThat(calls).as("opened, not shown inside its parent").containsExactly("open:" + folder);
        calls.clear();
        new ConfigEditor(path -> calls.add("edit:" + path), path -> { calls.add("open:" + path); throw new IllegalStateException("open"); },
            path -> calls.add("reveal:" + path)).openFolder(folder);
        assertThat(calls).containsExactly("open:" + folder, "reveal:" + folder);
        var broken = new ConfigEditor(path -> { throw new AssertionError("a folder is never edited"); },
            path -> { throw new IllegalStateException("open"); }, path -> { throw new UnsupportedOperationException("reveal"); });
        assertThatThrownBy(() -> broken.openFolder(folder)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(folder.toString())
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("open", "reveal"));
    }
    @Test void openFolderRefusesTheEdt() throws Exception {
        var calls = new ArrayList<Path>();
        var editor = new ConfigEditor(calls::add, calls::add, calls::add);
        var thrown = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { editor.openFolder(Path.of("/tmp/logs")); } catch (Throwable failure) { thrown.set(failure); }
        });
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
        assertThat(calls).isEmpty();
    }

    /** Ruling 6: a file a program printed or linked is edited or revealed, never opened, which would run a script or program. */
    @Test void editOrRevealEditsWithoutEverOpening() {
        Path file = Path.of("/tmp/run.command");
        var calls = new ArrayList<String>();
        new ConfigEditor(path -> calls.add("edit:" + path), path -> calls.add("open:" + path), path -> calls.add("reveal:" + path))
            .editOrReveal(file);
        assertThat(calls).as("the edit action alone").containsExactly("edit:" + file);
    }
    @Test void editOrRevealRevealsWhenEditFailsAndStillNeverOpens() {
        Path file = Path.of("/tmp/run.command");
        var calls = new ArrayList<String>();
        new ConfigEditor(path -> { calls.add("edit:" + path); throw new UnsupportedOperationException("edit"); },
            path -> calls.add("open:" + path), path -> calls.add("reveal:" + path)).editOrReveal(file);
        assertThat(calls).as("reveal after edit; open is skipped").containsExactly("edit:" + file, "reveal:" + file);
    }
    @Test void editOrRevealFailsWithEachAttemptsFailureSuppressedWhenNeitherWorked() {
        Path file = Path.of("/tmp/run.command");
        var calls = new ArrayList<String>();
        var broken = new ConfigEditor(path -> { calls.add("edit"); throw new IllegalStateException("edit"); },
            path -> calls.add("open"), path -> { calls.add("reveal"); throw new UnsupportedOperationException("reveal"); });
        assertThatThrownBy(() -> broken.editOrReveal(file)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(file.toString())
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("edit", "reveal"));
        assertThat(calls).as("open was never tried").containsExactly("edit", "reveal");
    }
    @Test void editOrRevealRefusesTheEdt() throws Exception {
        var calls = new ArrayList<Path>();
        var editor = new ConfigEditor(calls::add, calls::add, calls::add);
        var thrown = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { editor.editOrReveal(Path.of("/tmp/run.command")); } catch (Throwable failure) { thrown.set(failure); }
        });
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class);
        assertThat(calls).isEmpty();
    }

    /** Workspace-tabs plan 1, Ruling 26: a file another program asked Jasper to open is revealed, never edited or opened. */
    @Test void revealOnlyNeverEditsOrOpensAndRefusesTheEdt() throws Exception {
        Path file = Path.of("/tmp/notes.txt");
        var calls = new ArrayList<String>();
        new ConfigEditor(path -> calls.add("edit:" + path), path -> calls.add("open:" + path), path -> calls.add("reveal:" + path))
            .revealOnly(file);
        assertThat(calls).containsExactly("reveal:" + file);
        var broken = new ConfigEditor(path -> calls.add("edit"), path -> calls.add("open"),
            path -> { throw new UnsupportedOperationException("reveal"); });
        assertThatThrownBy(() -> broken.revealOnly(file)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(file.toString())
            .satisfies(failure -> assertThat(failure.getSuppressed()).extracting(Throwable::getMessage).containsExactly("reveal"));
        assertThat(calls).as("nothing else was tried").containsExactly("reveal:" + file);
        var thrown = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { broken.revealOnly(file); } catch (Throwable failure) { thrown.set(failure); }
        });
        assertThat(thrown.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("worker thread");
    }
}
