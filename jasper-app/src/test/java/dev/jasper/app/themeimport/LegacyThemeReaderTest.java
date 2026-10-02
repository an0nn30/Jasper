package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class LegacyThemeReaderTest {
    @TempDir Path home;

    @Test void legacyBridgeKeepsPrecedenceAndProblems() throws Exception {
        for (String archiveName : java.util.List.of("a.jar", "z.jar")) {
            Path root = home.resolve(archiveName);
            Path themes = root.resolve("themes");
            Files.createDirectories(themes);
            Path archive = themes.resolve(archiveName);
            ThemeFixtures.jar(archive, ThemeFixtures.entries(
                "META-INF/plugin.xml", ThemeFixtures.pluginXml("Mixed", "good.theme.json", "bad.theme.json"),
                "good.theme.json", ThemeFixtures.theme("Twin", true, null, "#123456"),
                "bad.theme.json", "broken"));
            Files.writeString(themes.resolve("m.toml"), "name='Twin'\nextends='Jasper Dark'\n");
            var catalog = LegacyThemeReader.catalog(root.resolve("config.toml"));
            var first = catalog.scan();
            Theme winner = first.find("Twin").orElseThrow();
            // The transitional bridge preserves the existing alphabetical file precedence.
            assertThat(winner.source()).isInstanceOf(archiveName.equals("a.jar") ? Theme.Archive.class : Theme.UserFile.class);
            assertThat(first.problems()).anyMatch(problem -> problem.message().contains("bad.theme.json"));
            assertThat(catalog.scan().find("Twin")).containsSame(winner);
            assertThat(catalog.latest().problems()).isEqualTo(first.problems());
        }
    }
}
