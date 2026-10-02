package dev.jasper.app.appearance;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class NativeThemeCatalogTest {
    @TempDir Path home;
    @Test void indexedCollectionIsNativeAndReusesClassicSingletons() throws Exception {
        var snapshot = ThemeCatalog.builtIn().scan();
        assertThat(snapshot.themes()).hasSize(284).allMatch(theme -> theme.source() instanceof Theme.Bundled);
        assertThat(snapshot.find("Studio Dark")).isPresent(); assertThat(snapshot.find("Catppuccin Mocha")).isPresent();
        assertThat(snapshot.find("Jasper Dark")).containsSame(Theme.DARK);
        assertThat(snapshot.find("Darcula")).containsSame(Theme.DARCULA);
        try (var jar = new java.util.jar.JarFile(System.getProperty("jasper.appJar"))) {
            String root = "dev/jasper/app/themes/";
            String index = new String(jar.getInputStream(jar.getJarEntry(root + "index.txt")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String file : index.lines().toList()) {
                var entry = jar.getJarEntry(root + file); assertThat(entry).isNotNull();
                String text = new String(jar.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertThat(ThemeResolver.resolve(ThemeToml.parse(text, file))).isNotNull();
            }
        }
    }
    @Test void leftoversCannotInfluenceNativeThemesAndUnchangedFilesKeepIdentity() throws Exception {
        Files.createDirectories(home.resolve("themes"));
        Files.writeString(home.resolve("themes/example.toml"), "name='Example'\nextends='Jasper Light'\n");
        Path archive = ThemeFixtures.plugin(home.resolve("themes/leftover.jar"), "Example", true, "#123456", "010203");
        var catalog = ThemeCatalog.beside(home.resolve("config.toml")); var first = catalog.scan().find("Example").orElseThrow();
        Files.writeString(archive, "changed");
        assertThat(catalog.scan().find("Example")).containsSame(first);
        assertThat(catalog.latest().problems()).isEmpty(); assertThat(first.source()).isInstanceOf(Theme.UserFile.class);
        assertThat(first.palette()).isEqualTo(Theme.JASPER_LIGHT.palette());
    }
}
