package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherImageTest {
    @TempDir Path root;

    private static final String CONFIG = """
        [Application]
        app.classpath=$APPDIR/jasper-app.jar
        app.mainclass=dev.jasper.app.Main
        app.classpath=$APPDIR/flatlaf-3.7.jar

        [JavaOptions]
        java-options=-Djpackage.app-version=1.0.0
        java-options=-Xmx2048m
        java-options=-Dflatlaf.nativeLibraryPath=$APPDIR/native
        java-options=-XX:AOTCache=$APPDIR/jasper.aot
        """;

    private Path image(String launcher, String appDirectory) throws IOException {
        Path app = root.resolve(appDirectory);
        Files.createDirectories(app);
        Files.writeString(app.resolve("Jasper.cfg"), CONFIG);
        Path executable = root.resolve(launcher);
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "");
        return app;
    }

    @Test void macOsWindowsAndLinuxLayoutsAreFound() throws IOException {
        Path mac = image("Jasper.app/Contents/MacOS/Jasper", "Jasper.app/Contents/app");
        assertThat(LauncherImage.locate(root.resolve("Jasper.app/Contents/MacOS/Jasper").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(mac));
        Path windows = image("win/Jasper/Jasper.exe", "win/Jasper/app");
        assertThat(LauncherImage.locate(root.resolve("win/Jasper/Jasper.exe").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(windows));
        Path linux = image("linux/Jasper/bin/Jasper", "linux/Jasper/lib/app");
        assertThat(LauncherImage.locate(root.resolve("linux/Jasper/bin/Jasper").toString()))
            .hasValueSatisfying(image -> assertThat(image.appDirectory()).isEqualTo(linux));
    }

    @Test void optionsAndClassPathHaveTheAppDirectoryResolved() throws IOException {
        Path app = image("Jasper.app/Contents/MacOS/Jasper", "Jasper.app/Contents/app");
        var image = LauncherImage.locate(root.resolve("Jasper.app/Contents/MacOS/Jasper").toString()).orElseThrow();
        assertThat(image.classpath()).containsExactly(app + "/jasper-app.jar", app + "/flatlaf-3.7.jar");
        assertThat(image.javaOptions()).contains("-Xmx2048m", "-Dflatlaf.nativeLibraryPath=" + app + "/native");
        assertThat(image.bundledMaxHeapMiB()).isEqualTo(2048);
        assertThat(image.hasAotCache()).isTrue();
    }

    @Test void noLauncherMeansNoImage() {
        assertThat(LauncherImage.locate(null)).isEmpty();
        assertThat(LauncherImage.locate(" ")).isEmpty();
        assertThat(LauncherImage.locate("bad\u0000path")).as("a path the platform cannot parse").isEmpty();
        assertThat(LauncherImage.locate(root.resolve("nowhere/Jasper").toString())).isEmpty();
        var bare = LauncherImage.parse(root, List.of("java-options=-Dx=1"));
        assertThat(bare.bundledMaxHeapMiB()).isEqualTo(LauncherImage.DEFAULT_MAX_HEAP_MIB);
        assertThat(bare.hasAotCache()).isFalse();
    }
}
