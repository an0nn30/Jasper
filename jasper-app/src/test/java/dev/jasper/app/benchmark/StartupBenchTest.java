package dev.jasper.app.benchmark;

import dev.jasper.app.layout.Group;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Layout;
import dev.jasper.app.layout.ParentTab;
import dev.jasper.app.layout.ParentTabId;
import dev.jasper.app.layout.ParentTabs;
import dev.jasper.app.layout.TabId;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.persistence.WorkspaceFile;
import dev.jasper.app.startup.StartupTraceFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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

    /**
     * The saved windows AOT training and every run in an isolated home restore: one main window with one terminal tab,
     * byte for byte what {@link WorkspaceFile#write} writes for it, so the seed cannot drift from the format.
     */
    @Test void theWorkspaceSeedIsWhatWorkspaceFileWritesForOneMainWindowWithOneTerminalTab() throws Exception {
        TabId item = TabId.parse("00000000-0000-4000-8000-000000000001");
        GroupId pane = GroupId.parse("00000000-0000-4000-9000-000000000001");
        ParentTabId tab = ParentTabId.parse("00000000-0000-4000-a000-000000000001");
        var expected = new WorkspaceFile.Snapshot(List.of(new WorkspaceFile.WindowRecord(WorkspaceFile.Role.MAIN,
            Optional.of(new UiState.Bounds(100, 100, 960, 600)), 0,
            ParentTabs.of(new ParentTab(tab, new Layout(new Group(pane, List.of(item), Optional.of(item)), pane, Optional.empty()))),
            Map.of(item, new WorkspaceFile.TabRecord.Terminal("", "", "")))));
        Path seed = temp.resolve("workspace.toml");
        Files.writeString(seed, seed(), StandardCharsets.UTF_8);
        assertThat(WorkspaceFile.read(seed)).isEqualTo(new WorkspaceFile.Loaded.Restore(expected, Optional.empty()));
        Path written = temp.resolve("written.toml");
        WorkspaceFile.write(written, expected);
        assertThat(Files.readString(seed, StandardCharsets.UTF_8))
            .as("WorkspaceFile's own text: write the snapshot above to %s again", StartupRuns.WORKSPACE_SEED)
            .isEqualTo(Files.readString(written, StandardCharsets.UTF_8));
    }

    /** Each launch in the isolated home restores the seed, whatever the run before it, priming included, saved as it quit. */
    @Test void everyColdRunInAnIsolatedHomeStartsFromTheWorkspaceSeed() throws Exception {
        var output = temp.resolve("output.json");
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", output.toString(),
            "--revision", "r", "--runs", "2", "--prime", "1", "--timeout-seconds", "5"});
        List<String> seen = new java.util.ArrayList<>();
        List<Path> homes = new java.util.ArrayList<>();
        var host = new StartupRuns.Host(command -> {
            String launch = javaToolOptions(command);
            Path home = Path.of(option(launch, "-Duser.home="));
            homes.add(home);
            seen.add(quitSaving(StartupRuns.isolatedWorkspace(home)));
            settle(Path.of(option(launch, "-Djasper.startupTrace=")), Map.of("OUTPUT_PAINTED", 500.0), List.of());
            return 0;
        }, System::currentTimeMillis, pid -> true, pid -> -1L, List::of);
        try {
            assertThat(new StartupRuns(options, host).run()).isEqualTo(0);
        } finally {
            deleteRoots(homes);
        }
        assertThat(homes).hasSize(3).containsOnly(homes.getFirst());
        assertThat(seen).hasSize(3).containsOnly(seed());
    }

    /** A resident reads the saved windows when it reopens its window: each reopen finds the seed. */
    @Test void everyWarmRunReopensOntoTheWorkspaceSeed() throws Exception {
        var output = temp.resolve("output.json");
        var options = StartupBenchOptions.parse(new String[] {"--app", "/A.app", "--output", output.toString(),
            "--revision", "r", "--mode", "warm", "--runs", "1", "--prime", "1", "--timeout-seconds", "5"});
        List<String> seen = new java.util.ArrayList<>();
        List<Path> homes = new java.util.ArrayList<>();
        Path[] trace = new Path[1];
        var host = new StartupRuns.Host(command -> {
            if (command.contains("--background")) {
                String launch = javaToolOptions(command);
                homes.add(Path.of(option(launch, "-Duser.home=")));
                trace[0] = Path.of(option(launch, "-Djasper.startupTrace="));
                settle(trace[0], Map.of(), List.of());
            } else {
                seen.add(quitSaving(StartupRuns.isolatedWorkspace(homes.getLast())));
                settle(trace[0], Map.of(), List.of(Map.of("trigger", "reopen", "requested", 10.0, "OUTPUT_PAINTED", 90.0)));
            }
            return 0;
        }, System::currentTimeMillis, pid -> true, pid -> -1L, List::of);
        try {
            assertThat(new StartupRuns(options, host).run()).isEqualTo(0);
        } finally {
            deleteRoots(homes);
        }
        assertThat(seen).hasSize(2).containsOnly(seed());
    }

    private static String seed() throws IOException {
        try (InputStream in = StartupRuns.class.getResourceAsStream(StartupRuns.WORKSPACE_SEED)) {
            assertThat(in).as("%s beside StartupRuns", StartupRuns.WORKSPACE_SEED).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** What a launch finds in {@code workspace}, which it then replaces with other windows, as a Jasper saves them when it quits. */
    private static String quitSaving(Path workspace) {
        try {
            String found = Files.readString(workspace, StandardCharsets.UTF_8);
            Files.writeString(workspace, "version = 1\n# the windows the run saved as it quit\n", StandardCharsets.UTF_8);
            return found;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The {@code JAVA_TOOL_OPTIONS} an {@code open} command passes. */
    private static String javaToolOptions(List<String> command) {
        return command.stream().filter(arg -> arg.startsWith("JAVA_TOOL_OPTIONS=")).findFirst().orElseThrow()
            .substring("JAVA_TOOL_OPTIONS=".length());
    }

    /** The value of the option starting with {@code prefix}; the benchmark's paths have no spaces. */
    private static String option(String options, String prefix) {
        return Stream.of(options.split(" ")).filter(part -> part.startsWith(prefix)).findFirst().orElseThrow()
            .substring(prefix.length());
    }

    /** A settled start-up trace with {@code milestones} and {@code windows}, as Jasper writes it. */
    private static void settle(Path trace, Map<String, Object> milestones, List<Map<String, Object>> windows) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("settled", true);
        document.put("pid", 777L);
        document.put("kind", "cold");
        document.put("jvm", "test");
        document.put("phases", Map.of());
        document.put("processStart", java.time.Instant.now().toString());
        document.put("milestones", milestones);
        document.put("windows", windows);
        try {
            StartupTraceFile.write(trace, document);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Deletes the folders under /tmp the runs' homes are in. */
    private static void deleteRoots(List<Path> homes) throws IOException {
        for (Path root : homes.stream().map(Path::getParent).distinct().toList()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static String[] join(String[] base, String... more) {
        String[] result = java.util.Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, result, base.length, more.length);
        return result;
    }
}
