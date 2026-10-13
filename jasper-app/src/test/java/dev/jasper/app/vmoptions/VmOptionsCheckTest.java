package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class VmOptionsCheckTest {
    private static final LauncherImage IMAGE = new LauncherImage(Path.of("/app"), List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions"),
        List.of("/app/jasper-app.jar"));
    private static final LauncherImage TRAINED = new LauncherImage(Path.of("/app"),
        List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot"), List.of("/app/jasper-app.jar", "/app/b.jar"));
    private static final String RECOVERABLE = "Invalid maximum heap size: -Xmx3q\nError: Could not create the Java Virtual Machine.\n"
        + "Error: A fatal exception has occurred. Program will exit.\n";
    private static final String UNRECOVERABLE = "Error occurred during initialization of VM\n"
        + "Initial heap size set to a larger value than the maximum heap size\n";

    private final List<List<String>> runs = new ArrayList<>();

    private VmOptionsCheck check(LauncherImage image, Function<List<String>, VmOptionsCheck.Outcome> script) {
        return new VmOptionsCheck(Path.of("/java"), image, (command, limit) -> { runs.add(command); return script.apply(command); },
            Duration.ofSeconds(10));
    }

    private static VmOptionsSyntax.Parsed parsed(String text) { return VmOptionsSyntax.parse(text.getBytes(StandardCharsets.UTF_8)); }
    private static VmOptionsCheck.Outcome ok() { return new VmOptionsCheck.Outcome(0, "openjdk version \"25\"\n", false); }

    @Test void eachLineAloneFindsWhatTheJvmWouldIgnore() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag")
            ? new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\nError: Could not create the Java Virtual Machine.\n", false)
            : ok()).check(parsed("-XX:+NoSuchFlag\n-Xmx1g\n"));
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).isEmpty();
        assertThat(runs.getFirst()).containsExactly("/java", "-XX:-IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-version");
        assertThat(runs.get(2)).as("then the line-up as launched")
            .containsExactly("/java", "-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-Xmx1g", "-version");
    }

    @Test void aJvmThatRejectsTheOptionsIsRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false) : ok())
            .check(parsed("-Xmx3q\n"));
        assertThat(findings.unrecognized()).isEmpty();
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
    }

    @Test void hotSpotsOwnInitializationFailureIsNotRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, UNRECOVERABLE, false) : ok()).check(parsed("-Xms4g\n"));
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
        assertThat(VmOptionsCheck.fatal("Error occurred during initialization of boot layer\njava.lang.module.FindException: Module jdk.nope not found\n"))
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.module.FindException: Module jdk.nope not found", false));
        assertThat(VmOptionsCheck.fatal("[0.010s][warning][aot] cache mismatch\n" + RECOVERABLE).message()).isEqualTo("Invalid maximum heap size: -Xmx3q");
    }

    @Test void aTrainedImageAlsoChecksTheCache() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, "Unable to use AOT cache.\n", false) : ok())
            .check(parsed("-XX:+UseZGC\n"));
        assertThat(findings.cacheRejected()).isTrue();
        assertThat(runs.getLast()).containsSubsequence("-XX:+UseZGC", "-XX:AOTMode=on", "-cp", "/app/jasper-app.jar" + java.io.File.pathSeparator + "/app/b.jar", "-version");
        runs.clear();
        assertThat(check(IMAGE, command -> ok()).check(parsed("-XX:+UseZGC\n")).cacheRejected()).isFalse();
        assertThat(runs).noneMatch(command -> command.contains("-XX:AOTMode=on"));
    }

    @Test void nothingToCheckRunsNothing() throws IOException {
        assertThat(check(IMAGE, command -> ok()).check(parsed("# only\nXmx300m\n"))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(check(IMAGE, command -> ok()).check(VmOptionsSyntax.parse(new byte[] {'-', 0}))).isEqualTo(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
    }

    @Test void aTimedOutRunFindsNothing() throws IOException {
        var findings = check(IMAGE, command -> new VmOptionsCheck.Outcome(-1, "", true)).check(parsed("-Xmx3q\n"));
        assertThat(findings).isEqualTo(VmOptionsCheck.Findings.NONE);
    }

    @Test void theRealJvmAgrees() throws IOException {
        var check = new VmOptionsCheck(VmOptionsCheck.runtimeJava(), IMAGE, VmOptionsCheck::runProcess, Duration.ofSeconds(30));
        var findings = check.check(parsed("-XX:+NoSuchFlag\n-Xmx3q\n"));
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(check.check(parsed("-Xms1g\n")).fatal())
            .contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
    }

    @Test void anInterruptStopsTheCheck() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> VmOptionsCheck.runProcess(List.of(VmOptionsCheck.runtimeJava().toString(), "-version"), Duration.ofSeconds(30)))
                .isInstanceOf(InterruptedIOException.class);
            assertThat(Thread.interrupted()).as("the interrupt is restored for the caller").isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
