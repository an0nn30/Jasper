package dev.jasper.app.vmoptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The packaged app's launcher configuration ({@code <name>.cfg}, spec section 6.4), found from the launcher's path in
 * {@code jpackage.app-path}: {@code Contents/app} on macOS, {@code app} on Windows, {@code lib/app} on Linux. Read as
 * packaging-support.gradle's {@code launcherJavaOptions} and {@code launcherClasspath} do. Blocking file I/O.
 *
 * @param appDirectory the directory {@code $APPDIR} names
 * @param javaOptions its {@code java-options}, in order, with {@code $APPDIR} resolved
 * @param classpath its {@code app.classpath} entries, in order, with {@code $APPDIR} resolved
 */
public record LauncherImage(Path appDirectory, List<String> javaOptions, List<String> classpath) {
    /** The bundled heap limit when the configuration names none (jasper-app/build.gradle.kts). */
    public static final long DEFAULT_MAX_HEAP_MIB = 2048;

    public LauncherImage {
        javaOptions = List.copyOf(javaOptions);
        classpath = List.copyOf(classpath);
    }

    /** The image of the launcher at {@code appPath}; empty when there is none (a development run) or it cannot be read. */
    public static Optional<LauncherImage> locate(String appPath) {
        if (appPath == null || appPath.isBlank()) return Optional.empty();
        Path launcher = Path.of(appPath.strip()).toAbsolutePath().normalize();
        Path parent = launcher.getParent();
        if (parent == null || launcher.getFileName() == null) return Optional.empty();
        String name = launcher.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) name = name.substring(0, name.length() - 4);
        List<Path> candidates = List.of(parent.resolveSibling("app"), parent.resolve("app"), parent.resolveSibling("lib").resolve("app"));
        for (Path directory : candidates) {
            Path config = directory.resolve(name + ".cfg");
            if (!Files.isRegularFile(config)) continue;
            try {
                return Optional.of(parse(directory, Files.readAllLines(config, StandardCharsets.UTF_8)));
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static LauncherImage parse(Path appDirectory, List<String> lines) {
        String directory = appDirectory.toString();
        var options = new ArrayList<String>();
        var classpath = new ArrayList<String>();
        for (String line : lines) {
            if (line.startsWith("java-options=")) options.add(line.substring("java-options=".length()).replace("$APPDIR", directory));
            else if (line.startsWith("app.classpath=")) classpath.add(line.substring("app.classpath=".length()).replace("$APPDIR", directory));
        }
        return new LauncherImage(appDirectory, options, classpath);
    }

    /** The bundled heap limit: the last {@code -Xmx} in the configuration, else {@link #DEFAULT_MAX_HEAP_MIB}. */
    public long bundledMaxHeapMiB() { return VmOptionsSyntax.lastMaxHeapMiB(javaOptions).orElse(DEFAULT_MAX_HEAP_MIB); }

    /** Whether the configuration names an AOT cache (a trained image). */
    public boolean hasAotCache() { return javaOptions.stream().anyMatch(option -> option.startsWith("-XX:AOTCache=")); }
}
