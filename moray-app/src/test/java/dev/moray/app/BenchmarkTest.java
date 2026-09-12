package dev.moray.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

class BenchmarkTest {
    @TempDir Path temp;

    @Test void explicitOutputAndRevisionAndBoundedArgumentsAreRequired() {
        assertThatThrownBy(() -> BenchmarkOptions.parse(false, new String[]{})).isInstanceOf(IllegalArgumentException.class);
        for (String[] extra : List.of(new String[]{"--bytes", "0"}, new String[]{"--panes", "9"},
                new String[]{"--scrollback", "1000000"}, new String[]{"--repeat", "11"},
                new String[]{"--scenario", "bogus"}, new String[]{"--sample-ms", "0"},
                new String[]{"--timeout-seconds", "3601"}, new String[]{"--output"})) {
            assertThatThrownBy(() -> options(extra)).isInstanceOf(IllegalArgumentException.class);
        }
        BenchmarkOptions options = options("--panes", "4,8", "--scrollback", "0,10000", "--scenario", "output", "--repeat", "2");
        assertThat(options.panes()).containsExactly(4, 8);
        assertThat(options.scrollbacks()).containsExactly(0, 10000);
        assertThat(options.bytes()).isEqualTo(100L * 1024 * 1024);
        assertThat(options.repeats()).isEqualTo(2);
        assertThat(options.scenarios()).containsExactly("output");
    }

    @Test void stagedUtf8WorkloadIsSeededAndCountsActualBytes() throws Exception {
        Path first = temp.resolve("one.txt"), second = temp.resolve("two.txt");
        long bytes = BenchmarkFixture.generate(first, 4097);
        BenchmarkFixture.generate(second, 4097);
        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        assertThat(bytes).isEqualTo(Files.size(first)).isBetween(4097L, 4609L);
        assertThat(bytes).isGreaterThan(Files.readString(first).length());
        assertThat(Files.readString(first)).contains("\033[", "moray");
    }

    @Test void childArgumentsKeepPathsWithSpacesAndMetacharactersSeparate() {
        Path directory = temp.resolve("space & special");
        List<String> command = BenchmarkFixture.command(directory.resolve("data.txt"), directory, 120);
        assertThat(command).containsSubsequence("dev.moray.app.BenchmarkFixture", "--data", directory.resolve("data.txt").toString(),
                "--control", directory.toString(), "--timeout-seconds", "120");
        assertThat(command).doesNotContain("cmd.exe", "/c", "/bin/sh");
    }

    @Test void summarySkipsUnavailableValuesRatherThanInventingZero() {
        Map<String, Object> summary = BenchmarkReport.summary(Arrays.asList(2.0, null, 4.0, 9.0));
        assertThat(summary).containsEntry("count", 3).containsEntry("median", 4.0).containsEntry("max", 9.0);
        assertThat(BenchmarkReport.summary(Arrays.asList(null, null))).containsEntry("median", null).containsEntry("max", null);
        assertThat(BenchmarkMetrics.parseResidentBytes("darwin", " 123\n")).isEqualTo(123L * 1024);
        assertThat(BenchmarkMetrics.parseResidentBytes("windows", "123")).isEqualTo(123L);
        assertThat(BenchmarkMetrics.parseResidentBytes("unknown", "123")).isNull();
        assertThat(BenchmarkMetrics.parseResidentBytes("darwin", "unavailable")).isNull();
    }

    @Test void reportEscapesTextAndPreservesNullAndFailure() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "failed"); result.put("reason", "quote\"\n\\"); result.put("rss", null);
        Path report = temp.resolve("nested/result.json");
        BenchmarkReport.write(report, result);
        assertThat(Files.readString(report)).isEqualTo("{\"status\":\"failed\",\"reason\":\"quote\\\"\\n\\\\\",\"rss\":null}\n");
    }

    @Test void deadlineAlwaysClosesResourceOnTimeoutAndOnFailure() {
        AtomicBoolean closed = new AtomicBoolean();
        assertThatThrownBy(() -> {
            try (BenchmarkLifetime lifetime = new BenchmarkLifetime(20, () -> closed.set(true))) {
                lifetime.await(() -> false, "fake reader completion");
            }
        }).isInstanceOf(TimeoutException.class).hasMessageContaining("fake reader completion");
        assertThat(closed).isTrue();
        closed.set(false);
        assertThatThrownBy(() -> {
            try (BenchmarkLifetime lifetime = new BenchmarkLifetime(1000, () -> closed.set(true))) {
                lifetime.await(() -> { throw new IllegalStateException("fake failure"); }, "fake");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(closed).isTrue();
    }

    @Test void selectedIdleFixtureIsGatedAndTerminatesWithoutAShell() throws Exception {
        Path data = temp.resolve("payload with spaces.txt"), control = Files.createDirectory(temp.resolve("control"));
        BenchmarkFixture.generate(data, 64);
        Process process = new ProcessBuilder(BenchmarkFixture.command(data, control, 5)).start();
        try (BenchmarkLifetime lifetime = new BenchmarkLifetime(4000, process::destroyForcibly)) {
            lifetime.await(() -> Files.exists(control.resolve("pid")), "fixture ready");
            assertThat(process.getInputStream().available()).isZero();
            Files.createFile(control.resolve("go"));
            lifetime.await(() -> { try { return process.getInputStream().available() >= Files.size(data) + 2 * 30; }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); } }, "fixture output");
            Files.createFile(control.resolve("stop"));
            lifetime.await(() -> !process.isAlive(), "fixture stop");
            assertThat(process.exitValue()).isZero();
            assertThat(new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("\033]2;" + BenchmarkFixture.START + "\007" + Files.readString(data)
                    + "\033]2;" + BenchmarkFixture.END + "\007");
        }
    }

    @Test void reportIncludesHumanReadableCompanionWithStatusAndUnits() throws Exception {
        Path file = temp.resolve("report.json");
        BenchmarkReport.write(file, Map.of("status", "failed", "schemaVersion", 1));
        assertThat(Files.readString(temp.resolve("report.json.md"))).contains("failed", "bytes", "milliseconds");
    }

    @Test void unsuccessfulHeadlessInvocationStillWritesStructuredFailure() throws Exception {
        BenchmarkOptions settings = options("--bytes", "64", "--panes", "1", "--scrollback", "0");
        assertThatThrownBy(() -> BenchmarkRun.run(settings)).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("display");
        assertThat(settings.output()).exists();
        assertThat(Files.readString(settings.output())).contains("\"status\":\"failed\"", "\"schemaVersion\":1", "test-revision");
    }

    @Test void standaloneFixtureIdleDeadlineExitsUnsuccessfully() throws Exception {
        Path data = temp.resolve("tiny.txt"), control = Files.createDirectory(temp.resolve("timeout-control"));
        BenchmarkFixture.generate(data, 1);
        Process process = new ProcessBuilder(BenchmarkFixture.command(data, control, 1))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try (BenchmarkLifetime lifetime = new BenchmarkLifetime(4000, process::destroyForcibly)) {
            lifetime.await(() -> !process.isAlive(), "fixture deadline");
            assertThat(process.exitValue()).isNotZero();
        }
    }

    @Test void preparationModeStagesSeededBytesWithoutStartingTheFixtureWaitLoop() throws Exception {
        Path prepared = temp.resolve("preparation output.txt");
        BenchmarkFixture.main(new String[]{"--generate", prepared.toString(), "--bytes", "4097"});
        assertThat(Files.size(prepared)).isBetween(4097L, 4609L);
    }

    @Test void preparationAllocatesInASeparateExitedJvmAndHasBoundedFailureCleanup() throws Exception {
        Path prepared = temp.resolve("isolated staging.txt");
        Map<String, Object> info = BenchmarkFixture.prepare(prepared, 4097, 4000);
        long pid = ((Number) info.get("pid")).longValue();
        assertThat(pid).isNotEqualTo(ProcessHandle.current().pid());
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
        assertThat(info).containsEntry("stagedBytes", Files.size(prepared)).containsEntry("exitCode", 0);
        assertThatThrownBy(() -> BenchmarkFixture.prepare(temp.resolve("timeout.txt"), 104857600, 0))
            .isInstanceOf(TimeoutException.class).hasMessageContaining("preparation");
    }

    private BenchmarkOptions options(String... extra) {
        List<String> args = new ArrayList<>(List.of("--output", temp.resolve("results.json").toString(), "--revision", "test-revision"));
        args.addAll(List.of(extra)); return BenchmarkOptions.parse(true, args.toArray(String[]::new));
    }
}
