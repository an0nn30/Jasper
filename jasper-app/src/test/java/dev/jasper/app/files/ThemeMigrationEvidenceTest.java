package dev.jasper.app.files;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.persistence.UiState;
import dev.jasper.sdk.contracts.ThemeMigrationCapture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.sdk.contracts.ThemeMigrationCapture.save;

class ThemeMigrationEvidenceTest {
    @TempDir Path directory;
    @Test void capturesActualChooserOnlyAgainstSyntheticDirectory() throws Exception {
        Files.createDirectory(directory.resolve("examples"));
        Files.writeString(directory.resolve("example.txt"), "Fixture data\n");
        ThemeMigrationCapture.themes(theme -> {
            var themes = ThemeTestSupport.controller(true);
            themes.configure(theme.equals("dark"), dev.jasper.app.config.TerminalSchemeChoice.MATCH);
            var request = new ChooserRequest("Choose fixture file", ChooserRequest.Mode.OPEN, false, Location.local(directory),
                Optional.of(Location.local(directory)), Optional.empty(), Optional.empty(), Optional.empty());
            var prefs = new ChooserPrefs(UiState.inMemory());
            var model = new ChooserModel(request, List.of(new LocalSource(new LocalBrowser(directory))), Runnable::run, Runnable::run, false);
            var panel = new FileChooserPanel(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), model, prefs, directory, files -> { }, file -> false, Optional::<String>empty);
            try {
                model.start();
                save("file-chooser", theme, "populated", panel, 920, 560);
            } finally { panel.close(); }
        });
    }
}
