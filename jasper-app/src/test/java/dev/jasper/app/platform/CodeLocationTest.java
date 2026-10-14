package dev.jasper.app.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeLocationTest {
    private static final String ENTRY = "dev/jasper/app/platform/CodeLocation.class";

    @TempDir Path dir;

    @Test void aJarResourceNamesTheJarEvenWithASpaceAndANonAsciiCharacterInItsPath() throws Exception {
        Path jar = dir.resolve("My Apps/J\u00e4sper.app/Contents/app/jasper-app.jar");
        var resource = URI.create("jar:" + jar.toUri() + "!/" + ENTRY).toURL();
        assertThat(CodeLocation.of(resource, ENTRY)).isEqualTo(jar.toAbsolutePath().normalize());
    }

    @Test void aMultiReleaseJarsVersionedSectionStillNamesTheJar() throws Exception {
        Path jar = dir.resolve("app/jasper-app.jar");
        var versioned = URI.create("jar:" + jar.toUri() + "!/META-INF/versions/9/" + ENTRY).toURL();
        assertThat(CodeLocation.of(versioned, ENTRY)).isEqualTo(jar.toAbsolutePath().normalize());
        var notAVersion = URI.create("jar:" + jar.toUri() + "!/META-INF/versions/x/" + ENTRY).toURL();
        assertThat(CodeLocation.of(notAVersion, ENTRY)).as("a prefix that is not a version number").isNull();
        var otherPrefix = URI.create("jar:" + jar.toUri() + "!/other/" + ENTRY).toURL();
        assertThat(CodeLocation.of(otherPrefix, ENTRY)).as("another prefix").isNull();
    }

    @Test void aClassesDirectoryResourceNamesTheDirectory() throws Exception {
        Path classes = dir.resolve("build dir/classes/java/main");
        var resource = classes.resolve(ENTRY).toUri().toURL();
        assertThat(CodeLocation.of(resource, ENTRY)).isEqualTo(classes.toAbsolutePath().normalize());
    }

    @Test void anythingElseHasNoLocation() throws Exception {
        assertThat(CodeLocation.of(null, ENTRY)).isNull();
        assertThat(CodeLocation.of(dir.resolve("Other.class").toUri().toURL(), ENTRY)).as("another file").isNull();
        assertThat(CodeLocation.of(URI.create("jar:" + dir.resolve("a.jar").toUri() + "!/other/Name.class").toURL(), ENTRY))
            .as("another entry").isNull();
        assertThat(CodeLocation.of(URI.create("jar:http://example.com/a.jar!/" + ENTRY).toURL(), ENTRY))
            .as("a jar that is not a file").isNull();
        assertThat(CodeLocation.of(URI.create("jrt:/java.base/java/lang/Object.class").toURL(), "java/lang/Object.class"))
            .as("the runtime image").isNull();
    }

    @Test void thisBuildIsFoundAtTheJarOrDirectoryHoldingItsClassFile() throws Exception {
        Path location = CodeLocation.of(CodeLocation.class);
        assertThat(location).as("tests run from a jar or a classes directory").isNotNull();
        assertThat(location.isAbsolute()).isTrue();
        if (Files.isDirectory(location)) {
            assertThat(location.resolve(ENTRY)).isRegularFile();
        } else {
            try (var jar = new JarFile(location.toFile())) {
                assertThat(jar.getEntry(ENTRY)).isNotNull();
            }
        }
    }
}
