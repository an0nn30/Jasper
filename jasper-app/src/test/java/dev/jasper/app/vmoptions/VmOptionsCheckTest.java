package dev.jasper.app.vmoptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VmOptionsCheckTest {
    private static final LauncherImage IMAGE = new LauncherImage(Path.of("/app"), List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions"),
        List.of("/app/jasper-app.jar"));
    private static final LauncherImage TRAINED = new LauncherImage(Path.of("/app"),
        List.of("-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot"), List.of("/app/jasper-app.jar", "/app/b.jar"));
    private static final String RECOVERABLE = "Invalid maximum heap size: -Xmx3q\nError: Could not create the Java Virtual Machine.\n"
        + "Error: A fatal exception has occurred. Program will exit.\n";
    private static final String UNRECOVERABLE = "Error occurred during initialization of VM\n"
        + "Initial heap size set to a larger value than the maximum heap size\n";
    /** What the installed app printed on 2026-10-14 (start-up recovery spec section 1). */
    private static final String SEALED = "[0.142s][error][aot               ] java.lang.SecurityException: sealing violation: "
        + "can't seal package com.jetbrains: already defined\nError occurred during initialization of VM\n"
        + "Unexpected exception when loading aot-linked classes.\n";
    private static final VmOptionsCheck.Fatal SEALED_FATAL = new VmOptionsCheck.Fatal(
        "java.lang.SecurityException: sealing violation: can't seal package com.jetbrains: already defined", false);
    /** What the test JVM printed on 2026-10-14 for a missing cache with -XX:AOTMode=on. */
    private static final String MISSING = "[0.005s][error][aot] An error has occurred while processing the AOT cache. Run with -Xlog:aot for details.\n"
        + "[0.005s][error][aot] Loading static archive failed.\n[0.005s][error][aot] Unable to map shared spaces\n"
        + "Error occurred during initialization of VM\nUnable to use AOT cache.\n";
    private static final String CLASSPATH = "/app/jasper-app.jar" + File.pathSeparator + "/app/b.jar";
    /** The cache check with the bundled options alone. */
    private static final List<String> BUNDLED_CACHE = List.of("/java", "-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions",
        "-XX:AOTCache=/app/jasper.aot", "-XX:AOTMode=on", "-cp", CLASSPATH, "-version");
    private static final VmOptionsCheck.Outcome TIMED_OUT = new VmOptionsCheck.Outcome(-1, "", true);

    @TempDir Path temp;

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
            : ok()).check(parsed("-XX:+NoSuchFlag\n-Xmx1g\n")).orElseThrow();
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).isEmpty();
        assertThat(runs.getFirst()).containsExactly("/java", "-XX:-IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-version");
        assertThat(runs.get(2)).as("then the line-up as launched")
            .containsExactly("/java", "-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:+NoSuchFlag", "-Xmx1g", "-version");
    }

    @Test void aJvmThatRejectsTheOptionsIsRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false) : ok())
            .check(parsed("-Xmx3q\n")).orElseThrow();
        assertThat(findings.unrecognized()).isEmpty();
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
    }

    @Test void hotSpotsOwnInitializationFailureIsNotRecoverable() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-Xms4g") && !command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, UNRECOVERABLE, false) : ok()).check(parsed("-Xms4g\n")).orElseThrow();
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
        assertThat(VmOptionsCheck.fatal("Error occurred during initialization of boot layer\njava.lang.module.FindException: Module jdk.nope not found\n"))
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.module.FindException: Module jdk.nope not found", false));
        assertThat(VmOptionsCheck.fatal("[0.010s][warning][aot] cache mismatch\n" + RECOVERABLE).message()).isEqualTo("Invalid maximum heap size: -Xmx3q");
    }

    @Test void aTrainedImageAlsoChecksTheCacheWithTheLineUp() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") && command.contains("-XX:+UseZGC")
            ? new VmOptionsCheck.Outcome(1, "Unable to use AOT cache.\n", false) : ok()).check(parsed("-XX:+UseZGC\n")).orElseThrow();
        assertThat(findings.cache()).contains(new VmOptionsCheck.Fatal("Unable to use AOT cache.", true));
        assertThat(findings.cacheUsedOptions()).as("the cache starts with the bundled options alone, so the options are to blame").isTrue();
        assertThat(findings.cacheChecked()).isTrue();
        assertThat(runs.subList(runs.size() - 2, runs.size())).as("the cache with the line-up, then once more with the bundled options alone")
            .containsExactly(List.of("/java", "-Xmx256m", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot", "-XX:+UseZGC",
                "-XX:AOTMode=on", "-cp", CLASSPATH, "-version"), BUNDLED_CACHE);
        runs.clear();
        var untrained = check(IMAGE, command -> ok()).check(parsed("-XX:+UseZGC\n")).orElseThrow();
        assertThat(untrained.cache()).isEmpty();
        assertThat(untrained.cacheUsedOptions()).isFalse();
        assertThat(untrained.cacheChecked()).isFalse();
        assertThat(runs).noneMatch(command -> command.contains("-XX:AOTMode=on"));
    }

    @Test void optionsThatStopTheJvmAreNeverBlamedOnTheCache() throws IOException {
        Function<List<String>, VmOptionsCheck.Outcome> script = command -> command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false)
            : command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok();
        var findings = check(TRAINED, script).checkWithCache(parsed("-Xmx3q\n")).orElseThrow();
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(runs.getLast()).as("the cache check runs with the bundled options alone").isEqualTo(BUNDLED_CACHE);
        assertThat(runs).filteredOn(command -> command.contains("-XX:AOTMode=on")).hasSize(1);
        assertThat(findings.cache()).as("Java's own message").contains(SEALED_FATAL);
        assertThat(findings.cacheUsedOptions()).isFalse();
        runs.clear();
        var settings = check(TRAINED, script).check(parsed("-Xmx3q\n")).orElseThrow();
        assertThat(settings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(settings.cache()).isEmpty();
        assertThat(settings.cacheChecked()).isFalse();
        assertThat(runs).as("System Settings has no use for the cache's result then").noneMatch(command -> command.contains("-XX:AOTMode=on"));
    }

    @Test void aCacheBrokenOnItsOwnIsNeverBlamedOnTheOptions() throws IOException {
        var findings = check(TRAINED, command -> !command.contains("-XX:AOTMode=on") ? ok()
            : new VmOptionsCheck.Outcome(1, command.contains("-Xmx1g") ? MISSING : SEALED, false)).check(parsed("-Xmx1g\n")).orElseThrow();
        assertThat(findings.cache()).as("the message of the run with the bundled options alone").contains(SEALED_FATAL);
        assertThat(findings.cacheUsedOptions()).isFalse();
        assertThat(findings.cacheChecked()).isTrue();
        assertThat(runs.getLast()).isEqualTo(BUNDLED_CACHE);
        assertThat(runs).filteredOn(command -> command.contains("-XX:AOTMode=on")).hasSize(2);
    }

    @Test void aCacheCheckOverItsTimeLimitKeepsTheOtherFindings() throws IOException {
        Function<List<String>, VmOptionsCheck.Outcome> script = command -> {
            if (command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag")) {
                return new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\n", false);
            }
            if (command.contains("-XX:AOTMode=on")) return TIMED_OUT;
            return command.contains("-Xmx3q") ? new VmOptionsCheck.Outcome(1, RECOVERABLE, false) : ok();
        };
        var stopped = check(TRAINED, script).checkWithCache(parsed("-XX:+NoSuchFlag\n-Xmx3q\n")).orElseThrow();
        assertThat(stopped.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(stopped.fatal()).as("the fatal stays").contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(stopped.cache()).isEmpty();
        assertThat(stopped.cacheUsedOptions()).isFalse();
        assertThat(stopped.cacheChecked()).isFalse();
        var started = check(TRAINED, script).check(parsed("-XX:+NoSuchFlag\n")).orElseThrow();
        assertThat(started.unrecognized()).as("the cache with the line-up over its limit").containsExactly("-XX:+NoSuchFlag");
        assertThat(started.cache()).isEmpty();
        assertThat(started.cacheChecked()).isFalse();
        runs.clear();
        var rechecked = check(TRAINED, command -> command.contains("-XX:-IgnoreUnrecognizedVMOptions") && command.contains("-XX:+NoSuchFlag")
            ? new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\n", false)
            : !command.contains("-XX:AOTMode=on") ? ok()
            : command.contains("-XX:+NoSuchFlag") ? new VmOptionsCheck.Outcome(1, MISSING, false) : TIMED_OUT).check(parsed("-XX:+NoSuchFlag\n")).orElseThrow();
        assertThat(rechecked.unrecognized()).as("the run with the bundled options alone over its limit").containsExactly("-XX:+NoSuchFlag");
        assertThat(rechecked.cache()).isEmpty();
        assertThat(rechecked.cacheUsedOptions()).isFalse();
        assertThat(rechecked.cacheChecked()).isFalse();
        assertThat(runs.getLast()).isEqualTo(BUNDLED_CACHE);
    }

    @Test void aRefusedCacheIsExplainedByTheJvmsOwnAotErrorWhenItNamesTheCause() {
        assertThat(VmOptionsCheck.cacheFatal(SEALED)).isEqualTo(SEALED_FATAL);
        assertThat(VmOptionsCheck.cacheFatal(MISSING)).as("no AOT error names an exception: HotSpot's line")
            .isEqualTo(new VmOptionsCheck.Fatal("Unable to use AOT cache.", false));
        assertThat(VmOptionsCheck.cacheFatal("[0.1s][warning][aot] java.lang.IllegalStateException: only a warning\nUnable to use AOT cache.\n"))
            .as("warnings never explain").isEqualTo(new VmOptionsCheck.Fatal("Unable to use AOT cache.", true));
        assertThat(VmOptionsCheck.cacheFatal("[0.1s][error][aot,load] java.lang.OutOfMemoryError: Java heap space\n"
            + "Error occurred during initialization of VM\nUnexpected exception when loading aot-linked classes.\n"))
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.OutOfMemoryError: Java heap space", false));
    }

    @Test void aCauseWithABracketInItIsReadWhole() {
        assertThat(VmOptionsCheck.cacheFatal("[0.142s][error][aot               ] java.lang.IllegalStateException: archived module graph "
            + "mismatch: [app] differs from [boot] at runtime\nError occurred during initialization of VM\n"
            + "Unexpected exception when loading aot-linked classes.\n")).as("the text after the decorations, not after the last bracket")
            .isEqualTo(new VmOptionsCheck.Fatal("java.lang.IllegalStateException: archived module graph mismatch: [app] differs from [boot] at runtime", false));
    }

    @Test void afterASkippedCacheTheCacheIsCheckedEvenWithoutCustomOptions() throws IOException {
        var findings = check(TRAINED, command -> command.contains("-XX:AOTMode=on") ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok())
            .checkWithCache(parsed("# only a comment\n")).orElseThrow();
        assertThat(runs).singleElement().satisfies(command -> assertThat(command).containsExactly("/java", "-Xmx256m",
            "-XX:+IgnoreUnrecognizedVMOptions", "-XX:AOTCache=/app/jasper.aot", "-XX:AOTMode=on", "-cp", CLASSPATH, "-version"));
        assertThat(findings).isEqualTo(new VmOptionsCheck.Findings(List.of(), Optional.empty(), Optional.of(SEALED_FATAL), false, true));
        runs.clear();
        assertThat(check(TRAINED, command -> ok()).checkWithCache(VmOptionsSyntax.parse(new byte[] {'-', 0})))
            .as("a corrupted file: the bundled options pass")
            .contains(new VmOptionsCheck.Findings(List.of(), Optional.empty(), Optional.empty(), false, true));
        assertThat(runs).hasSize(1);
        runs.clear();
        var withOptions = check(TRAINED, command -> command.contains("-XX:AOTMode=on") && command.contains("-Xmx1g")
            ? new VmOptionsCheck.Outcome(1, SEALED, false) : ok()).checkWithCache(parsed("-Xmx1g\n")).orElseThrow();
        assertThat(withOptions.cacheUsedOptions()).as("the options start the JVM, so the cache check uses them").isTrue();
        assertThat(runs.get(runs.size() - 2)).containsSubsequence("-Xmx1g", "-XX:AOTMode=on");
        runs.clear();
        assertThat(check(IMAGE, command -> ok()).checkWithCache(parsed(""))).as("no cache to check").contains(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
        assertThat(check(TRAINED, command -> new VmOptionsCheck.Outcome(-1, "", true)).checkWithCache(parsed("")))
            .as("a cache check over its limit leaves the cache unchecked").contains(VmOptionsCheck.Findings.NONE);
    }

    @Test void nothingToCheckRunsNothing() throws IOException {
        assertThat(check(IMAGE, command -> ok()).check(parsed("# only\nXmx300m\n"))).contains(VmOptionsCheck.Findings.NONE);
        assertThat(check(IMAGE, command -> ok()).check(VmOptionsSyntax.parse(new byte[] {'-', 0}))).contains(VmOptionsCheck.Findings.NONE);
        assertThat(check(TRAINED, command -> ok()).check(parsed("# only\n"))).as("Settings never checks the cache alone")
            .contains(VmOptionsCheck.Findings.NONE);
        assertThat(runs).isEmpty();
    }

    @Test void aTimedOutRunFindsNothing() throws IOException {
        var findings = check(IMAGE, command -> new VmOptionsCheck.Outcome(-1, "", true)).check(parsed("-Xmx3q\n"));
        assertThat(findings).as("the check could not run").isEmpty();
    }

    @Test void aCheckOverItsTimeLimitShowsNoFindingsAtAll() throws IOException {
        var findings = check(IMAGE, command -> command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(1, "Unrecognized VM option 'NoSuchFlag'\n", false)
            : new VmOptionsCheck.Outcome(-1, "", true)).check(parsed("-XX:+NoSuchFlag\n"));
        assertThat(findings).as("the line alone found something, but the merged run timed out").isEmpty();
        runs.clear();
        var aloneTimedOut = check(IMAGE, command -> command.contains("-XX:-IgnoreUnrecognizedVMOptions")
            ? new VmOptionsCheck.Outcome(-1, "", true) : new VmOptionsCheck.Outcome(1, RECOVERABLE, false)).check(parsed("-Xmx3q\n"));
        assertThat(aloneTimedOut).as("any run of the options over its limit").isEmpty();
    }

    @Test void theRealJvmAgrees() throws IOException {
        var check = new VmOptionsCheck(VmOptionsCheck.runtimeJava(), IMAGE, VmOptionsCheck::runProcess, Duration.ofSeconds(30));
        var findings = check.check(parsed("-XX:+NoSuchFlag\n-Xmx3q\n")).orElseThrow();
        assertThat(findings.unrecognized()).containsExactly("-XX:+NoSuchFlag");
        assertThat(findings.fatal()).contains(new VmOptionsCheck.Fatal("Invalid maximum heap size: -Xmx3q", true));
        assertThat(check.check(parsed("-Xms1g\n")).orElseThrow().fatal())
            .contains(new VmOptionsCheck.Fatal("Initial heap size set to a larger value than the maximum heap size", false));
    }

    @Test void theRealJvmRefusesACacheItCannotUse() throws IOException {
        var image = new LauncherImage(temp, List.of("-Xmx256m", "-XX:AOTCache=" + temp.resolve("missing.aot")), List.of(temp.resolve("a.jar").toString()));
        var findings = new VmOptionsCheck(VmOptionsCheck.runtimeJava(), image, VmOptionsCheck::runProcess, Duration.ofSeconds(30))
            .checkWithCache(VmOptionsSyntax.Parsed.EMPTY).orElseThrow();
        assertThat(findings.cache()).contains(new VmOptionsCheck.Fatal("Unable to use AOT cache.", false));
        assertThat(findings.cacheUsedOptions()).isFalse();
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
