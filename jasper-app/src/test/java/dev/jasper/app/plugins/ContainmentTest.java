package dev.jasper.app.plugins;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ContainmentTest {
    private final List<PluginRuntime.Failure> reported = new ArrayList<>();

    @Test void aContainedFailureIsCountedAndReportedWithWhatThePluginWasDoing() {
        var containment = new Containment(() -> false, reported::add);
        var failure = new IllegalStateException("plugin broke");
        assertThat(containment.attempt("dev.example.tool", "action tool.run", () -> { throw failure; })).isSameAs(failure);
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
        assertThat(reported).containsExactly(new PluginRuntime.Failure("dev.example.tool", "action tool.run", failure));
    }

    @Test void aLinkageErrorIsReportedToo() {
        var containment = new Containment(() -> false, reported::add);
        var failure = new NoClassDefFoundError("dev/example/Missing");
        assertThat(containment.run("dev.example.tool", "panel tool.files", () -> { throw failure; })).isFalse();
        assertThat(reported).extracting(PluginRuntime.Failure::cause).containsExactly(failure);
    }

    @Test void aCallThatSucceedsReportsNothing() {
        var containment = new Containment(() -> false, reported::add);
        assertThat(containment.run("dev.example.tool", "stop", () -> { })).isTrue();
        assertThat(reported).isEmpty();
    }

    @Test void aReportThatFailsIsContained() {
        var containment = new Containment(() -> false, failure -> { throw new IllegalStateException("sink broke"); });
        assertThatCode(() -> containment.run("dev.example.tool", "stop", () -> { throw new IllegalStateException("x"); }))
            .doesNotThrowAnyException();
        assertThat(containment.failures("dev.example.tool")).isEqualTo(1);
    }
}
