package dev.jasper.app.platform;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class DesktopHelperDirectoryTest {
    @TempDir Path home;
    @Test void helperNamesAreFixedAndContainNoShellFragments() {
        assertThat(DesktopColors.helperNames()).containsExactly(
            "jasper-desktop-portal", "jasper-desktop-gtk3", "jasper-desktop-gtk4");
        assertThat(DesktopColors.helperNames()).allMatch(name -> name.matches("[a-z0-9-]+"));
    }
    @Test void installedAndDevelopmentPathsPreserveSpaces() throws Exception {
        Path lib = Files.createDirectories(home.resolve("Jasper install/lib/app"));
        Path helpers = Files.createDirectory(lib.resolve("desktop-colors"));
        assertThat(DesktopColors.helperDirectory("Linux", null, lib.resolve("jasper-app.jar"))).contains(helpers);
        Path development = Files.createDirectory(home.resolve("development helpers"));
        assertThat(DesktopColors.helperDirectory("Linux", development.toString(), null)).contains(development);
    }
    @Test void missingInstalledDirectoryIsUnavailable() {
        assertThat(DesktopColors.helperDirectory("Linux", null, home.resolve("jasper-app.jar"))).isEmpty();
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.LINUX)
    void installedHelperRunsFromAPathWithSpacesWithoutDisplay() throws Exception {
        Path directory = Files.createDirectory(home.resolve("installed helpers with spaces"));
        Path helper = Files.copy(Path.of(System.getProperty("jasper.stagedDesktopHelpers")).resolve("jasper-desktop-gtk4"),
            directory.resolve("jasper-desktop-gtk4"), StandardCopyOption.COPY_ATTRIBUTES);
        var builder = new ProcessBuilder(helper.toString(), "--protocol=1");
        builder.environment().remove("DISPLAY"); builder.environment().remove("WAYLAND_DISPLAY");
        builder.environment().put("GDK_BACKEND", "x11");
        Process process = builder.start();
        try {
            var writer = process.outputWriter();
            writer.write("{\"v\":1,\"kind\":\"sample\",\"generation\":7,\"appearance\":\"dark\"}\n"); writer.flush();
            var response = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    var reader = process.inputReader();
                    for (String line; (line = reader.readLine()) != null; )
                        if (line.contains("\"generation\":7")) return line;
                    return null;
                }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
            assertThat(response.get(5, java.util.concurrent.TimeUnit.SECONDS))
                .contains("\"kind\":\"palette\"", "\"generation\":7");
            writer.close();
            assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            process.destroyForcibly();
            assertThat(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close();
        }
    }
    @Test void explicitAbsoluteDirectoryOverridesInstalledSibling() throws Exception {
        Path explicit = Files.createDirectory(home.resolve("explicit"));
        Path installed = Files.createDirectory(home.resolve("desktop-colors"));
        assertThat(DesktopColors.helperDirectory("Linux", explicit.toString(), home.resolve("jasper-app.jar"))).contains(explicit);
        assertThat(DesktopColors.helperDirectory("Linux", null, home.resolve("jasper-app.jar"))).contains(installed);
    }
    @Test void invalidExplicitPathDoesNotSearchOtherLocationsOrWorkingDirectory() throws Exception {
        Files.createDirectory(home.resolve("desktop-colors"));
        for (String explicit : new String[] { "relative/helpers", home.resolve("missing").toString(), "bad\0path" })
            assertThat(DesktopColors.helperDirectory("Linux", explicit, home.resolve("jasper-app.jar"))).isEmpty();
        assertThat(DesktopColors.helperDirectory("Linux", null, home.resolve("classes"))).isEmpty();
        assertThat(DesktopColors.helperDirectory("Linux", null, null)).isEmpty();
    }
    @Test void otherPlatformsNeverUseLinuxHelpers() throws Exception {
        Path explicit = Files.createDirectory(home.resolve("explicit"));
        assertThat(DesktopColors.helperDirectory("Mac OS X", explicit.toString(), null)).isEmpty();
        assertThat(DesktopColors.helperDirectory("Windows 11", explicit.toString(), null)).isEmpty();
    }
}
