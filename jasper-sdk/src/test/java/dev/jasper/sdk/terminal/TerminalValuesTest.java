package dev.jasper.sdk.terminal;

import dev.jasper.sdk.Capabilities;
import dev.jasper.sdk.MissingCapabilityException;
import dev.jasper.sdk.events.Topic;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TerminalValuesTest {
    @Test void paneInfoRejectsImpossibleValues() {
        PaneInfo unknown = PaneInfo.unknown();
        assertThat(unknown.state()).isEqualTo(SessionState.EXITED);
        assertThat(unknown.kind()).isEqualTo(SessionKind.LOCAL);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneInfo("t", Optional.empty(), Optional.empty(), -1, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty()));
        assertThatIllegalArgumentException().as("an exit status belongs to an exited session").isThrownBy(() -> new PaneInfo("t",
            Optional.empty(), Optional.empty(), 80, 24, false, SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.of(0)));
        assertThatNullPointerException().isThrownBy(() -> new PaneInfo(null, Optional.empty(), Optional.empty(), 80, 24, false,
            SessionKind.LOCAL, Optional.empty(), SessionState.RUNNING, OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new RemoteDirectory(" ", "/srv"));
        assertThat(new RemoteDirectory("", "/srv").host()).as("the program named no host").isEmpty();
    }

    @Test void openRequestsAreLocalAndTakeAnAbsoluteDirectory() {
        assertThat(OpenRequest.local()).isEqualTo(new OpenRequest.Local(new LocalSpec(Optional.empty())));
        Path home = Path.of(System.getProperty("user.home"));
        assertThat(OpenRequest.localIn(home)).isEqualTo(OpenRequest.local(new LocalSpec(Optional.of(home))));
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalSpec(Optional.of(Path.of("relative"))));
    }

    @Test void everyTerminalTopicIsNamespacedAndDistinct() throws Exception {
        List<String> ids = new ArrayList<>();
        for (var field : TerminalEvents.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != Topic.class) continue;
            Topic<?> topic = (Topic<?>) field.get(null);
            assertThat(TerminalEvents.owns(topic)).as(field.getName()).isTrue();
            assertThat(topic.id()).isEqualTo(TerminalEvents.PREFIX + field.getName().toLowerCase(java.util.Locale.ROOT));
            ids.add(topic.id());
        }
        assertThat(ids).hasSize(16).doesNotHaveDuplicates();
        assertThat(TerminalEvents.owns(Topic.of("jasper.activity", String.class))).isFalse();
    }

    @Test void aMissingCapabilityNamesThePluginAndTheCapability() {
        var failure = new MissingCapabilityException("dev.example.tool", Capabilities.TERMINAL_INJECT);
        assertThat(failure.pluginId()).isEqualTo("dev.example.tool");
        assertThat(failure.capability()).isEqualTo("terminal.inject");
        assertThat(failure).hasMessageContaining("dev.example.tool").hasMessageContaining("terminal.inject");
        assertThat(Capabilities.ALL).containsExactly("terminal.observe", "terminal.selection", "terminal.inject", "terminal.open", "session.provide", "palette.contribute");
    }
}
