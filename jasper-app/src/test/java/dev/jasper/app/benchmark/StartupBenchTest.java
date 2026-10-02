package dev.jasper.app.benchmark;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupBenchTest {
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
        String options = StartupRuns.launchOptions(Path.of("/tmp/jsb1/trace-3.json"), Path.of("/tmp/jsb1/home"));
        assertThat(options).isEqualTo("-Djasper.startupTrace=/tmp/jsb1/trace-3.json -Duser.home=/tmp/jsb1/home");
        assertThat(StartupRuns.launchOptions(Path.of("/tmp/t.json"), null)).isEqualTo("-Djasper.startupTrace=/tmp/t.json");
        assertThat(StartupRuns.openCommand(Path.of("/Apps/My Jasper.app"), options, false)).containsExactly(
            "open", "-n", "-a", "/Apps/My Jasper.app", "--env", "JAVA_TOOL_OPTIONS=" + options);
        assertThat(StartupRuns.openCommand(Path.of("/A.app"), options, true)).containsExactly(
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
        var host = new StartupRuns.Host(command -> { commands.add(command); return 0; }, () -> 0L, pid -> true, pid -> 0L);
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", "/tmp/never.json", "--revision", "r"});
        assertThat(new StartupRuns(options, host).run()).isEqualTo(3);
        assertThat(commands).containsExactly(List.of("pgrep", "-f", "/Contents/MacOS/Jasper"));
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

    private static String[] join(String[] base, String... more) {
        String[] result = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, result, base.length, more.length);
        return result;
    }
}
