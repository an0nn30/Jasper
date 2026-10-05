package dev.jasper.fileserver.ui;

import dev.jasper.fileserver.definition.Bind;
import dev.jasper.fileserver.runtime.ServerState;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @TempDir Path directory;
    @Test void capturesRealPanelEmptyBusyFailedAndConditionalServerEditor() throws Exception {
        ThemeMigrationCapture.themes(theme -> {
            var fixture = new FileServersPanelTest();
            var panel = fixture.panel();
            save("fileserver", theme, "empty", panel, 540, 420);
            fixture.view("Example server", ServerState.STARTING, false, false, Bind.LOOPBACK, List.of(), Optional.empty());
            panel.setServers(fixture.views, Optional.empty()); save("fileserver", theme, "starting", panel, 540, 420);
            fixture.views.clear(); fixture.view("Example server", ServerState.failed("Fixture port unavailable"), false, false, Bind.LOOPBACK, List.of(), Optional.empty());
            panel.setServers(fixture.views, Optional.empty()); save("fileserver", theme, "failed", panel, 540, 420);
            var editors = new ServerEditorTest(); editors.folder = directory;
            try {
                var editor = editors.editor(Optional.empty(), false, true, false, List.of());
                save("fileserver", theme, "new-anonymous", editor, 820, 610);
                editor.vault.doClick(); save("fileserver", theme, "new-vault", editor, 820, 610);
                editor.save.doClick(); save("fileserver", theme, "new-invalid", editor, 820, 610);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
}
