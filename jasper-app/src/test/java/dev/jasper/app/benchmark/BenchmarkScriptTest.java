package dev.jasper.app.benchmark;

import java.nio.file.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Uses a fake runtime that records arguments; never launches a benchmark or a GUI. */
@DisabledOnOs(OS.WINDOWS)
class BenchmarkScriptTest {
    @TempDir Path temp;
    @Test void macScriptSelectsEntryPointFromCurrentOrPreservedJar() throws Exception {
        Path image = temp.resolve("image with spaces/Jasper.app");
        Path runtime = image.resolve("Contents/runtime/Contents/Home/bin/java");
        Files.createDirectories(runtime.getParent());
        Files.writeString(runtime, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n");
        assertThat(runtime.toFile().setExecutable(true)).isTrue();
        Path jar = image.resolve("Contents/app/jasper-app.jar"); Files.createDirectories(jar.getParent());
        for (String prefix : java.util.List.of("dev/jasper/app/", "dev/jasper/app/benchmark/")) {
            try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new JarEntry(prefix + "Bench.class")); out.closeEntry();
            }
            var process = new ProcessBuilder("/bin/sh", Path.of(System.getProperty("jasper.repoRoot"),
                "tools/benchmarks/run-macos.sh").toString(), image.toString(), "throughput", "revision",
                temp.resolve("output.json").toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.waitFor()).as(output).isZero();
            assertThat(output.lines().toList()).contains(prefix.replace('/', '.') + "Bench");
        }
    }
}
