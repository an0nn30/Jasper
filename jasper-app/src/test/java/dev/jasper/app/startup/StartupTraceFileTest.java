package dev.jasper.app.startup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupTraceFileTest {
    @TempDir Path temp;

    @Test void aDocumentRoundTripsWithEscapesNestingAndNumbersAsDoubles() throws Exception {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("ok", true);
        inner.put("none", null);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schema", 1);
        document.put("text", "quote \" back \\ line\n tab\t bell\u0007");
        document.put("list", List.of(1.5, "two", false));
        document.put("inner", inner);
        Path file = temp.resolve("nested/trace.json");
        StartupTraceFile.write(file, document);
        Map<String, Object> read = StartupTraceFile.read(file);
        assertThat(read).containsEntry("schema", 1.0).containsEntry("text", "quote \" back \\ line\n tab\t bell\u0007")
            .containsEntry("list", List.of(1.5, "two", false));
        assertThat(map(read.get("inner"))).containsEntry("ok", true).containsEntry("none", null);
        try (var listing = Files.list(file.getParent())) { assertThat(listing.toList()).containsExactly(file); }
    }

    @Test void readingRejectsAnythingButAWellFormedObject() throws Exception {
        Path array = temp.resolve("array.json");
        Files.writeString(array, "[1, 2]");
        assertThatThrownBy(() -> StartupTraceFile.read(array)).isInstanceOf(IOException.class);
        Path broken = temp.resolve("broken.json");
        Files.writeString(broken, "{\"a\": }");
        assertThatThrownBy(() -> StartupTraceFile.read(broken)).isInstanceOf(IOException.class);
    }

    @Test void settlingWritesTheDocumentAndEachCompletedWindowRewritesIt() throws Exception {
        var fixture = new StartupTraceTest.Fixture();
        Path file = temp.resolve("trace.json");
        StartupTrace trace = fixture.trace(file);
        fixture.at(400);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        fixture.at(500);
        WindowTrace window = trace.window("cold");
        fixture.at(520);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        Map<String, Object> settled = StartupTraceFile.read(file);
        assertThat(settled).containsEntry("schema", 1.0).containsEntry("pid", 42.0)
            .containsEntry("processStart", "2026-10-02T16:35:09.123Z").containsEntry("kind", "cold")
            .containsEntry("settled", true);
        assertThat(map(settled.get("jvm"))).containsEntry("aotCache", "used").containsEntry("cds", "on");
        assertThat(map(settled.get("milestones"))).containsEntry("PLUGINS_BOUND", 500.0)
            .containsEntry("OUTPUT_PAINTED", 620.0);
        fixture.at(530);
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        List<?> windows = (List<?>) StartupTraceFile.read(file).get("windows");
        assertThat(windows).singleElement().satisfies(entry -> assertThat(map(entry))
            .containsEntry("trigger", "cold").containsEntry("requested", 600.0).containsEntry("OUTPUT_PAINTED", 630.0));
    }

    @Test void aMilestoneMarkedAfterTheCapSettledRewritesTheDocument() throws Exception {
        var fixture = new StartupTraceTest.Fixture();
        Path file = temp.resolve("trace.json");
        StartupTrace trace = fixture.trace(file);
        fixture.at(8_000);
        trace.mark(StartupTrace.Milestone.TOOLKIT_READY);
        fixture.timers.getFirst().run();
        assertThat(map(StartupTraceFile.read(file).get("milestones"))).doesNotContainKey("PLUGINS_BOUND");
        fixture.at(11_000);
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        Map<String, Object> rewritten = StartupTraceFile.read(file);
        assertThat(rewritten).containsEntry("settled", true);
        assertThat(map(rewritten.get("milestones"))).containsEntry("PLUGINS_BOUND", 11_100.0);
    }

    @Test void anUnwritableTraceFileIsReportedOnceAndNeverThrows() throws Exception {
        var fixture = new StartupTraceTest.Fixture();
        Path blocked = temp.resolve("a-file");
        Files.writeString(blocked, "not a directory");
        StartupTrace trace = fixture.trace(blocked.resolve("trace.json"));
        WindowTrace window = trace.window("cold");
        trace.mark(StartupTrace.Milestone.PLUGINS_BOUND);
        trace.mark(StartupTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).hasSize(2);
        assertThat(fixture.log.get(1)).startsWith("Could not write the start-up trace ");
        window.mark(WindowTrace.Milestone.OUTPUT_PAINTED);
        assertThat(fixture.log).hasSize(3);
        assertThat(fixture.log.get(1)).startsWith("Could not write the start-up trace ");
        assertThat(fixture.log).noneMatch(entry -> entry.startsWith("Could not write the start-up trace ") && !entry.equals(fixture.log.get(1)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
