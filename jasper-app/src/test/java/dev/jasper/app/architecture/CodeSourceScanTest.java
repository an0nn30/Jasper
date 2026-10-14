package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Spec 2026-10-14 section 3.2: classes the AOT cache loads report the cache's training location, or none
 * (JDK-8376576), so the modules on the application class path find their files through {@code CodeLocation}.
 * Plugins load from their own class loaders, outside the cache, and are not scanned.
 */
class CodeSourceScanTest {
    static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot"));
    static final List<String> CLASS_PATH_MODULES = List.of("jasper-app", "jasper-ui", "jasper-terminal", "jasper-buddy", "jasper-sdk");

    @Test void noModuleOnTheApplicationClassPathReadsACodeSource() throws IOException {
        var offenders = new java.util.ArrayList<String>();
        for (String module : CLASS_PATH_MODULES) {
            try (Stream<Path> files = Files.walk(ROOT.resolve(module).resolve("src/main/java"))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.contains("getProtectionDomain(") || text.contains("getCodeSource(")) {
                        offenders.add(ROOT.relativize(file).toString().replace('\\', '/'));
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }
}
