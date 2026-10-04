package dev.jasper.app.architecture;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The packaged launcher's native-library options (passed in by gradle/packaging-support-test.gradle), with $APPDIR
 * resolved to a temporary app folder laid out as packageApp lays out the image: every library loads from that folder
 * and nothing is extracted. Catches a FlatLaf, JNA or pty4j upgrade that moves its resources or ignores the options.
 */
@EnabledOnOs(OS.MAC)
class BundledNativesProbeTest {
    @TempDir Path temp;

    @Test void everyBundledLibraryLoadsFromTheAppFolderAndNothingIsExtracted() throws Exception {
        String options = System.getProperty("jasper.bundledNativeOptions");
        assertThat(options).as("set by gradle/packaging-support-test.gradle").isNotBlank();
        boolean arm = System.getProperty("os.arch").equals("aarch64");
        Path root = temp.toRealPath();
        Path app = root.resolve("app");
        Path natives = app.resolve("native");
        String flatLaf = "libflatlaf-macos-" + (arm ? "arm64" : "x86_64") + ".dylib";
        copy("com.formdev.flatlaf.ui.FlatNativeMacLibrary", "com/formdev/flatlaf/natives/" + flatLaf, natives.resolve(flatLaf));
        copy("com.sun.jna.Native", "com/sun/jna/darwin-" + (arm ? "aarch64" : "x86-64") + "/libjnidispatch.jnilib",
            natives.resolve("libjnidispatch.jnilib"));
        copy("com.pty4j.PtyProcessBuilder", "resources/com/pty4j/native/darwin/libpty.dylib", natives.resolve("darwin/libpty.dylib"));
        Path helper = natives.resolve("darwin/pty4j-unix-spawn-helper");
        copy("com.pty4j.PtyProcessBuilder", "resources/com/pty4j/native/darwin/pty4j-unix-spawn-helper", helper);
        assertThat(helper.toFile().setExecutable(true, false)).isTrue();
        Path scratch = root.resolve("scratch");
        Files.createDirectories(scratch.resolve("home"));
        Files.createDirectories(scratch.resolve("tmp"));

        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--enable-native-access=ALL-UNNAMED"));
        for (String option : options.split("\n")) command.add(option.replace("$APPDIR", app.toString()));
        command.addAll(List.of("-Djava.awt.headless=true", "-Duser.home=" + scratch.resolve("home"),
            "-Djava.io.tmpdir=" + scratch.resolve("tmp"), "-Djna.tmpdir=" + scratch.resolve("jna"),
            "-Dpty4j.tmpdir=" + scratch.resolve("pty4j"), "-Xlog:library=info:stdout",
            "-cp", classPath(), BundledNativesProbe.class.getName()));
        // Output goes to a file, not a pipe: reading a pipe to its end blocks for as long as the child lives, and a blocked
        // read cannot be interrupted, so a hung probe would otherwise be bounded only by the CI job's own timeout.
        Path log = root.resolve("probe-output.txt");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            throw new AssertionError("the bundled natives probe did not exit within 60 s; output so far:\n"
                + read(log));
        }
        String output = read(log);
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("Loaded library " + natives.resolve(flatLaf) + ", handle",
            "Loaded library " + natives.resolve("libjnidispatch.jnilib") + ", handle",
            "pty4j libpty.dylib " + natives.resolve("darwin/libpty.dylib"),
            "pty4j pty4j-unix-spawn-helper " + helper,
            "bundled natives probe passed");
        try (var files = Files.walk(scratch)) {
            assertThat(files.filter(Files::isRegularFile).toList()).as("files extracted while loading").isEmpty();
        }
    }

    /** The file's text, decoding bytes that are not UTF-8 leniently as the old pipe read did. */
    private static String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void copy(String owner, String entry, Path target) throws Exception {
        Files.createDirectories(target.getParent());
        try (var jar = new ZipFile(codeSource(owner).toFile())) {
            var source = jar.getEntry(entry);
            assertThat(source).as("%s in %s", entry, jar.getName()).isNotNull();
            try (var input = jar.getInputStream(source)) { Files.copy(input, target); }
        }
    }

    /** Where a class on the test runtime class path comes from, without initialising it (JNA would load itself). */
    private static Path codeSource(String className) throws Exception {
        Class<?> type = Class.forName(className, false, BundledNativesProbeTest.class.getClassLoader());
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** The probe and what the packaged class path gives it: FlatLaf, JNA, pty4j and their runtime needs. */
    private static String classPath() throws Exception {
        List<String> entries = new ArrayList<>();
        for (String type : List.of(BundledNativesProbe.class.getName(), "com.formdev.flatlaf.ui.FlatNativeMacLibrary",
                "com.sun.jna.Native", "com.sun.jna.platform.unix.LibCAPI", "com.pty4j.PtyProcessBuilder", "kotlin.Unit",
                "org.slf4j.LoggerFactory", "org.slf4j.nop.NOPServiceProvider"))
            entries.add(codeSource(type).toString());
        return String.join(File.pathSeparator, entries);
    }
}
