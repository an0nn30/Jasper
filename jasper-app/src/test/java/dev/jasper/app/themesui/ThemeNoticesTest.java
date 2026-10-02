package dev.jasper.app.themesui;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ThemeNoticesTest {
    @Test void readsPackagedUtf8Notices() throws Exception {
        assertThat(ThemeNotices.read()).contains("license", "IntelliJ");
        try(var jar=new java.util.jar.JarFile(System.getProperty("jasper.appJar"))){assertThat(jar.getJarEntry("dev/jasper/app/themes/THIRD-PARTY-THEMES.md")).isNotNull();}
    }
}
