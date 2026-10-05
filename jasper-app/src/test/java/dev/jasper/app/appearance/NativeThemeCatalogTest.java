package dev.jasper.app.appearance;
import dev.jasper.ui.themeio.ThemeToml;
import dev.jasper.ui.theme.ThemeResolver;
import dev.jasper.ui.theme.Seed;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class NativeThemeCatalogTest {
    @TempDir Path home;
    @Test void equalMetadataParentEditsRefreshDescendantsAndKeepUnchangedIdentity() throws Exception {
        Path base = home.resolve("themes/base.toml"); Files.createDirectories(base.getParent());
        Files.writeString(base, "name='Base'\nextends='Jasper Dark'\n[colors]\nbackground='#112233'\n");
        Files.writeString(base.resolveSibling("child.toml"), "name='Child'\nextends='Base'\n");
        var catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        var before = catalog.scan().find("Child").orElseThrow();
        var stamp = Files.getLastModifiedTime(base);
        Files.writeString(base, Files.readString(base).replace("#112233", "#445566")); Files.setLastModifiedTime(base, stamp);
        var after = catalog.scan().find("Child").orElseThrow();
        assertThat(after).isNotSameAs(before);
        assertThat(after.source().document().seeds()).containsEntry(Seed.BACKGROUND, new java.awt.Color(0x445566));
        assertThat(catalog.scan().find("Child").orElseThrow()).isSameAs(after);
    }

    @Test void malformedRepairAndDirectoryRecreationRefreshProblemsAndThemes() throws Exception {
        Path file = home.resolve("themes/theme.toml"); Files.createDirectories(file.getParent());
        Files.writeString(file, "name='Child'\nextends='Missing'\n");
        var catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        assertThat(catalog.scan().problems()).anyMatch(problem -> problem.line() == 2);
        Files.writeString(file, "name='Child'\nextends='Nord'\n");
        assertThat(catalog.scan().find("Child")).isPresent(); assertThat(catalog.latest().problems()).isEmpty();
        Files.delete(file); Files.delete(file.getParent());
        assertThat(catalog.scan().find("Child")).isEmpty(); assertThat(file.getParent()).isDirectory();
        Files.writeString(file, "name='Child'\nextends='Nord'\n");
        assertThat(catalog.scan().find("Child")).isPresent();
    }

    @Test void indexedCollectionIsNativeAndReusesClassicSingletons() throws Exception {
        var snapshot = ThemeCatalog.builtIn().scan();
        assertThat(snapshot.themes()).hasSize(5).allMatch(theme -> theme.source() instanceof Theme.Bundled);
        assertThat(snapshot.names()).containsExactly("Clear Dark", "Clear Light", "Jasper Dark", "Jasper Light", "Nord");
        assertThat(snapshot.find("Jasper Dark")).containsSame(Theme.DARK);
        assertThat(snapshot.find("Jasper Light")).containsSame(Theme.JASPER_LIGHT);
        try (var jar = new java.util.jar.JarFile(dev.jasper.ui.theme.GeneratedTheme.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())) {
            String root = "dev/jasper/ui/themeimport/native/";
            assertThat(jar.stream().map(java.util.jar.JarEntry::getName)
                .filter(name -> name.startsWith(root) && name.endsWith(".toml")).toList())
                .containsExactlyInAnyOrder(root + "jasper-light.toml", root + "jasper-dark.toml",
                    root + "nord-nord.toml", root + "clear-dark.toml", root + "clear-light.toml");
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
