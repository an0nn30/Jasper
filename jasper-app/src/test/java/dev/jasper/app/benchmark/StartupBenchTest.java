package dev.jasper.app.benchmark;

import dev.jasper.app.startup.StartupTraceFile;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(OS.WINDOWS)
class StartupBenchTest {
    @TempDir Path temp;

    @Test void optionsDefaultToTenColdRunsWithOnePrimingRunInAnIsolatedHome() {
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A/Jasper.app", "--output", "/r/out.json", "--revision", "r1"});
        assertThat(options).isEqualTo(new StartupBenchOptions(Path.of("/A/Jasper.app"), Path.of("/r/out.json"), "r1",
            "cold", 10, 1, false, 45));
    }

    @Test void optionsRejectUnknownModesBadCountsAndAWarmRealHome() {
        String[] base = {"--app", "/A.app", "--output", "/o.json", "--revision", "r"};
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--mode", "hot"))).hasMessageContaining("--mode");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--runs", "0"))).hasMessageContaining("--runs");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--mode", "warm", "--real-home")))
            .hasMessageContaining("isolated");
        assertThatThrownBy(() -> StartupBenchOptions.parse(new String[] {"--app", "/A.app"})).hasMessageContaining("required");
        assertThatThrownBy(() -> StartupBenchOptions.parse(join(base, "--app", "/B.app"))).hasMessageContaining("unique");
    }

    @Test void launchesGoThroughLaunchServicesWithTheTraceAndAnIsolatedHome() {
        Path home = Path.of("/tmp/jsb1/home");
        String options = StartupRuns.launchOptions(Path.of("/tmp/jsb1/trace-3.json"), home);
        assertThat(options).isEqualTo("-Djasper.startupTrace=/tmp/jsb1/trace-3.json -Duser.home=/tmp/jsb1/home");
        assertThat(StartupRuns.launchOptions(Path.of("/tmp/t.json"), null)).isEqualTo("-Djasper.startupTrace=/tmp/t.json");
        assertThat(StartupRuns.isolatedVmOptions(home)).isEqualTo(Path.of("/tmp/jsb1/home/.config/jasper/jasper.vmoptions"));
        assertThat(StartupRuns.openCommand(Path.of("/Apps/My Jasper.app"), options, home, false)).containsExactly(
            "open", "-n", "-a", "/Apps/My Jasper.app", "--env", "JAVA_TOOL_OPTIONS=" + options,
            "--env", "JASPER_VM_OPTIONS=/tmp/jsb1/home/.config/jasper/jasper.vmoptions");
        assertThat(StartupRuns.openCommand(Path.of("/A.app"), options, null, true)).containsExactly(
            "open", "-n", "-a", "/A.app", "--env", "JAVA_TOOL_OPTIONS=" + options, "--args", "--background");
        assertThat(StartupRuns.reopenCommand(Path.of("/A.app"))).containsExactly("open", "-a", "/A.app");
    }

    @Test void milestonesAreRebasedFromTheProcessStartToTheClick() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("processStart", "2026-10-02T16:35:09.100Z");
        trace.put("milestones", Map.of("MAIN_ENTERED", 70.0, "OUTPUT_PAINTED", 500.0));
        trace.put("windows", List.of(Map.of("trigger", "cold", "requested", 300.0, "OUTPUT_PAINTED", 500.0),
            Map.of("trigger", "reopen", "requested", 5_000.0, "SHOWN", 5_040.0, "OUTPUT_PAINTED", 5_090.0)));
        long click = java.time.Instant.parse("2026-10-02T16:35:09.080Z").toEpochMilli();
        assertThat(StartupRuns.fromClick(trace, click, null)).containsEntry("PROCESS_START", 20.0)
            .containsEntry("MAIN_ENTERED", 90.0).containsEntry("OUTPUT_PAINTED", 520.0);
        long reopen = java.time.Instant.parse("2026-10-02T16:35:14.090Z").toEpochMilli();
        assertThat(StartupRuns.fromClick(trace, reopen, "reopen")).containsExactly(
            Map.entry("REQUESTED", 10.0), Map.entry("SHOWN", 50.0), Map.entry("OUTPUT_PAINTED", 100.0));
    }

    @Test void refusesWhileAnyJasperIsRunningAndLaunchesNothing() throws Exception {
        List<List<String>> commands = new java.util.ArrayList<>();
        var host = new StartupRuns.Host(command -> { commands.add(command); return 0; }, () -> 0L, pid -> true, pid -> 0L,
            () -> List.of(4242L));
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", "/tmp/never.json", "--revision", "r"});
        assertThat(new StartupRuns(options, host).run()).isEqualTo(3);
        assertThat(commands).isEmpty();
    }

    @Test void residentMemoryComesFromPsOutputInKibibytes() {
        assertThat(StartupRuns.parseRss("  412345\n")).isEqualTo(412_345L);
        assertThat(StartupRuns.parseRss("")).isEqualTo(-1L);
        assertThat(StartupRuns.parseRss("rss")).isEqualTo(-1L);
    }

    @Test void theSummaryGivesCountMedianMinimumAndMaximumPerMilestone() {
        var summary = StartupRuns.summary(List.of(Map.of("A", 10.0, "B", 1.0), Map.of("A", 30.0), Map.of("A", 20.0)));
        assertThat(summary.get("A")).containsEntry("count", 3).containsEntry("median", 20.0)
            .containsEntry("min", 10.0).containsEntry("max", 30.0);
        assertThat(summary.get("B")).containsEntry("count", 1).containsEntry("median", 1.0);
    }

    @Test void aColdRunWithoutOutputPaintedIsIncomplete() throws Exception {
        var output = temp.resolve("output.json");
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", output.toString(),
            "--revision", "r", "--runs", "1", "--prime", "0", "--timeout-seconds", "5"});
        var host = new StartupRuns.Host(command -> {
            if (command.contains("open")) {
                for (String arg : command) {
                    if (arg.contains("startupTrace=")) {
                        int idx = arg.indexOf("startupTrace=") + "startupTrace=".length();
                        String rest = arg.substring(idx);
                        int spaceIdx = rest.indexOf(" ");
                        String traceArg = spaceIdx >= 0 ? rest.substring(0, spaceIdx) : rest;
                        Path path = Path.of(traceArg);
                        try {
                            Map<String, Object> document = new LinkedHashMap<>();
                            document.put("settled", true);
                            document.put("pid", 777L);
                            document.put("kind", "cold");
                            document.put("jvm", "test");
                            document.put("phases", Map.of());
                            document.put("processStart", java.time.Instant.now().toString());
                            document.put("milestones", Map.of("MAIN_ENTERED", 70.0));
                            document.put("windows", List.of());
                            StartupTraceFile.write(path, document);
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    }
                }
            }
            return 0;
        }, System::currentTimeMillis, pid -> true, pid -> -1L, () -> List.of());
        assertThat(new StartupRuns(options, host).run()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) StartupTraceFile.read(output);
        assertThat(report.get("status")).isEqualTo("incomplete");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) report.get("runs");
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).get("outcome")).isEqualTo("incomplete");
    }

    @Test void cleanupStopsEveryJasperEvenWhenOneStopFails() {
        List<Long> attemptedPids = new java.util.ArrayList<>();
        var host = new StartupRuns.Host(command -> 0, System::currentTimeMillis,
            pid -> {
                attemptedPids.add(pid);
                if (pid == 2L) throw new IllegalStateException("x");
                return true;
            },
            pid -> -1L, () -> List.of(1L, 2L, 3L));
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", "/tmp/out.json", "--revision", "r"});
        new StartupRuns(options, host).stopAll();
        assertThat(attemptedPids).containsExactly(1L, 2L, 3L);
    }

    private static String[] join(String[] base, String... more) {
        String[] result = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, result, base.length, more.length);
        return result;
    }
}
