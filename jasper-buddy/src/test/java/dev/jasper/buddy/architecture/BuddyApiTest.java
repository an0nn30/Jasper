package dev.jasper.buddy.architecture;

import dev.jasper.buddy.config.BuddyOptions;
import dev.jasper.buddy.view.BuddyCompanion;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BuddyApiTest {
    @Test void standaloneJarLoadsWithOnlyTheJdkAndContainsTheActualSprite() throws Exception {
        Path path = Path.of(System.getProperty("jasper.buddyJar"));
        try (var jar = new JarFile(path.toFile());
             var loader = new URLClassLoader(new java.net.URL[]{path.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            var entry = jar.getJarEntry("dev/jasper/buddy/internal/presentation/jasper-buddy.png");
            assertThat(entry).isNotNull();
            try (var stream = jar.getInputStream(entry)) { assertThat(javax.imageio.ImageIO.read(stream)).isNotNull(); }
            assertThat(Class.forName("dev.jasper.buddy.view.BuddyCompanion", true, loader)).isNotNull();
            assertThatThrownBy(() -> Class.forName("dev.jasper.app.Main", true, loader)).isInstanceOf(ClassNotFoundException.class);
            assertThat(jar.stream().map(java.util.jar.JarEntry::getName).toList()).noneMatch(n -> n.contains("TestSupport"));
        }
    }
    @Test void facadeExposesOnlyItsConfiguredConstructorAndVoidMutationsExceptShow() {
        assertThat(BuddyCompanion.class.getConstructors()).hasSize(1);
        assertThat(BuddyCompanion.class.getConstructors()[0].getParameterTypes()).containsExactly(BuddyOptions.class);
        for (var method : BuddyCompanion.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertThat(method.getReturnType()).as(method.toString()).isEqualTo(method.getName().equals("show") ? boolean.class : void.class);
        }
    }
}
